package com.xitora.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.SystemClock
import android.util.TypedValue
import android.view.accessibility.AccessibilityEvent
import com.xitora.autoclicker.data.AutoClickerStateHolder
import com.xitora.autoclicker.domain.ClickEngineCalculator
import com.xitora.autoclicker.domain.ScenarioDetectGuard
import com.xitora.autoclicker.model.ClickMode
import com.xitora.autoclicker.model.ScenarioStep
import com.xitora.autoclicker.model.ScenarioStepType
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class AutoClickAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var stateHolder: AutoClickerStateHolder
    private lateinit var overlayController: OverlayController

    private var clickJob: Job? = null
    private var tickerJob: Job? = null
    private var completedCycles = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        stateHolder = AutoClickerStateHolder.getInstance(applicationContext)
        overlayController = OverlayController(
            serviceContext = this,
            stateHolder = stateHolder,
            onToggleRun = { toggleClicking() },
            onPauseBeforeEdit = { stopClicking() },
            onCloseOverlay = { hideOverlay() }
        )
        stateHolder.updateRuntime { it.copy(isAccessibilityEnabled = true) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally lightweight: we do not inspect window content or collect user data.
    }

    override fun onInterrupt() {
        stopClicking()
    }

    override fun onDestroy() {
        stopClicking()
        if (::overlayController.isInitialized) {
            overlayController.hideAllOverlays()
        }
        if (::stateHolder.isInitialized) {
            stateHolder.updateRuntime {
                it.copy(
                    isAccessibilityEnabled = false,
                    isOverlayVisible = false,
                    isClicking = false
                )
            }
        }
        serviceScope.cancel()
        instance = null
        super.onDestroy()
    }

    fun showOverlay(mode: ClickMode) {
        stopClicking()
        completedCycles = 0L
        stateHolder.updateRuntime {
            it.copy(
                isOverlayVisible = true,
                activeMode = mode,
                isClicking = false,
                elapsedMs = 0L,
                clickCount = 0L,
                currentScenarioStepOrder = 1
            )
        }
        overlayController.showOverlays(mode)
    }

    fun hideOverlay() {
        stopClicking()
        overlayController.hideAllOverlays()
        stateHolder.updateRuntime {
            it.copy(
                isOverlayVisible = false,
                isClicking = false,
                elapsedMs = 0L,
                clickCount = 0L
            )
        }
    }

    fun syncFromState() {
        val rt = stateHolder.runtime.value
        if (!rt.isOverlayVisible) return
        if (!rt.isClicking) {
            overlayController.rebuildTargets(rt.activeMode)
        }
        overlayController.updateStatusReadout(completedCycles)
    }

    fun toggleClicking() {
        if (stateHolder.runtime.value.isClicking) {
            stopClicking()
        } else {
            startClicking()
        }
    }

    fun startClicking() {
        val rt = stateHolder.runtime.value
        if (!rt.isOverlayVisible || rt.isClicking) return

        overlayController.dismissModal()
        completedCycles = 0L
        val startRealtime = SystemClock.elapsedRealtime()

        stateHolder.updateRuntime {
            it.copy(
                isClicking = true,
                elapsedMs = 0L,
                clickCount = 0L,
                currentScenarioStepOrder = 1
            )
        }
        overlayController.setTargetsTouchThrough(true)
        overlayController.updateStatusReadout(completedCycles)

        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive && stateHolder.runtime.value.isClicking) {
                val elapsed = SystemClock.elapsedRealtime() - startRealtime
                stateHolder.updateRuntime { it.copy(elapsedMs = elapsed) }
                overlayController.updateStatusReadout(completedCycles)

                val data = stateHolder.state.value
                val mode = stateHolder.runtime.value.activeMode
                val cfg = when (mode) {
                    ClickMode.SINGLE -> data.singleConfig
                    ClickMode.MULTI -> data.multiConfig
                    ClickMode.SCENARIO -> data.scenarioConfig
                }
                if (ClickEngineCalculator.shouldStopByRepeatRule(cfg, completedCycles, elapsed)) {
                    stopClicking()
                    break
                }
                delay(200L)
            }
        }

        clickJob?.cancel()
        clickJob = serviceScope.launch {
            // Brief yield so WindowManager applies FLAG_NOT_TOUCHABLE before the very first tap
            delay(50L)

            while (isActive && stateHolder.runtime.value.isClicking) {
                val data = stateHolder.state.value
                val mode = stateHolder.runtime.value.activeMode
                val activeConfig = when (mode) {
                    ClickMode.SINGLE -> data.singleConfig
                    ClickMode.MULTI -> data.multiConfig
                    ClickMode.SCENARIO -> data.scenarioConfig
                }
                val elapsed = SystemClock.elapsedRealtime() - startRealtime

                if (ClickEngineCalculator.shouldStopByRepeatRule(activeConfig, completedCycles, elapsed)) {
                    stopClicking()
                    break
                }

                when (mode) {
                    ClickMode.SINGLE -> {
                        val target = data.singleTarget
                        val (cx, cy) = overlayController.getTargetScreenCenter(target)
                        val (sw, sh) = overlayController.screenBounds()
                        val radiusPx = dpToPx(activeConfig.randomRadiusDp.toFloat())
                        val (tapX, tapY) = ClickEngineCalculator.computeClickCoordinate(
                            xPx = cx,
                            yPx = cy,
                            antiBotEnabled = activeConfig.antiBotRandomEnabled,
                            radiusPx = radiusPx,
                            screenWidth = sw,
                            screenHeight = sh
                        )
                        val durationMs = ClickEngineCalculator.resolveDurationMs(
                            activeConfig.durationValue,
                            activeConfig.durationUnit
                        )
                        val intervalMs = ClickEngineCalculator.resolveIntervalMs(
                            activeConfig.intervalValue,
                            activeConfig.intervalUnit
                        )

                        performTapGesture(tapX, tapY, durationMs)
                        if (!stateHolder.runtime.value.isClicking) break

                        completedCycles++
                        val nowElapsed = SystemClock.elapsedRealtime() - startRealtime
                        stateHolder.updateRuntime {
                            it.copy(clickCount = it.clickCount + 1L, elapsedMs = nowElapsed)
                        }
                        overlayController.updateStatusReadout(completedCycles)

                        if (ClickEngineCalculator.shouldStopByRepeatRule(activeConfig, completedCycles, nowElapsed)) {
                            stopClicking()
                            break
                        }
                        delay(intervalMs)
                    }
                    ClickMode.MULTI -> {
                        val targets = data.multiTargets
                        if (targets.isEmpty()) {
                            stopClicking()
                            break
                        }
                        for (pt in targets) {
                            if (!isActive || !stateHolder.runtime.value.isClicking) break
                            val (cx, cy) = overlayController.getTargetScreenCenter(pt)
                            val (sw, sh) = overlayController.screenBounds()
                            val radiusPx = dpToPx(pt.randomRadiusDp.toFloat())
                            val (tapX, tapY) = ClickEngineCalculator.computeClickCoordinate(
                                xPx = cx,
                                yPx = cy,
                                antiBotEnabled = pt.antiBotRandomEnabled,
                                radiusPx = radiusPx,
                                screenWidth = sw,
                                screenHeight = sh
                            )
                            val durationMs = ClickEngineCalculator.resolveDurationMs(
                                pt.durationValue,
                                pt.durationUnit
                            )
                            val intervalMs = ClickEngineCalculator.resolveIntervalMs(
                                pt.intervalValue,
                                pt.intervalUnit
                            )

                            performTapGesture(tapX, tapY, durationMs)
                            if (!stateHolder.runtime.value.isClicking) break

                            val stepElapsed = SystemClock.elapsedRealtime() - startRealtime
                            stateHolder.updateRuntime {
                                it.copy(clickCount = it.clickCount + 1L, elapsedMs = stepElapsed)
                            }
                            overlayController.updateStatusReadout(completedCycles)

                            if (ClickEngineCalculator.shouldStopByRepeatRule(activeConfig, completedCycles, stepElapsed)) {
                                break
                            }
                            delay(intervalMs)
                        }
                        if (!isActive || !stateHolder.runtime.value.isClicking) break
                        completedCycles++
                        val cycleElapsed = SystemClock.elapsedRealtime() - startRealtime
                        if (ClickEngineCalculator.shouldStopByRepeatRule(activeConfig, completedCycles, cycleElapsed)) {
                            stopClicking()
                            break
                        }
                    }
                    ClickMode.SCENARIO -> {
                        val steps = data.scenarioSteps
                        if (steps.isEmpty()) {
                            stopClicking()
                            break
                        }
                        for (step in steps) {
                            if (!isActive || !stateHolder.runtime.value.isClicking) break
                            val liveStep = stateHolder.state.value.scenarioSteps.firstOrNull { it.id == step.id } ?: step
                            stateHolder.updateRuntime { it.copy(currentScenarioStepOrder = liveStep.order) }
                            overlayController.updateStatusReadout(completedCycles)

                            val (cx, cy) = overlayController.getScenarioStepScreenCenter(liveStep)
                            val (sw, sh) = overlayController.screenBounds()
                            val radiusPx = dpToPx(liveStep.randomRadiusDp.toFloat())

                            when (liveStep.type) {
                                ScenarioStepType.REPEAT_UNTIL_DETECT -> {
                                    if (!ScreenDetector.hasValidDetectionTarget(liveStep)) {
                                        stopClicking()
                                        promptDetectionTargetPicker(
                                            step = liveStep,
                                            resumeAfterSave = true
                                        )
                                        return@launch
                                    }
                                    val tapsInStep = AtomicInteger(0)
                                    val detectGuard = ScenarioDetectGuard()
                                    val detectPollMs = liveStep.detectCheckIntervalMs.coerceAtLeast(200L)
                                    val repeatDelayMs = liveStep.repeatIntervalMs.coerceAtLeast(20L)
                                    val tapMs = liveStep.tapDurationMs.coerceIn(10L, 60_000L)

                                    val conditionMatched = AtomicBoolean(false)

                                    // Run image detection asynchronously so repeat taps never stutter or wait on screenshots.
                                    val detectJob = serviceScope.launch(Dispatchers.Default) {
                                        var lastScreenshotCheckMs = 0L
                                        while (isActive && stateHolder.runtime.value.isClicking && !conditionMatched.get()) {
                                            val currentTaps = tapsInStep.get()
                                            val nowMs = SystemClock.elapsedRealtime()
                                            var detected = false
                                            var evaluated = false

                                            if (!liveStep.targetImagePath.isNullOrBlank() &&
                                                nowMs - lastScreenshotCheckMs >= 1000L
                                            ) {
                                                val matchResult = ScreenDetector.detectImageWithScoreInService(
                                                    service = this@AutoClickAccessibilityService,
                                                    imagePath = liveStep.targetImagePath,
                                                    similarityPercent = liveStep.imageSimilarityPercent
                                                )
                                                if (matchResult != null) {
                                                    lastScreenshotCheckMs = SystemClock.elapsedRealtime()
                                                    stateHolder.updateRuntime {
                                                        it.copy(lastDetectScore = matchResult.bestScore)
                                                    }
                                                    evaluated = true
                                                    detected = matchResult.matched
                                                }
                                            }
                                            if (evaluated && detectGuard.onConditionEvaluated(detected, currentTaps)) {
                                                conditionMatched.set(true)
                                                break
                                            }
                                            delay(detectPollMs)
                                        }
                                    }

                                    try {
                                        while (isActive && stateHolder.runtime.value.isClicking && !conditionMatched.get()) {
                                            val (tapX, tapY) = ClickEngineCalculator.computeClickCoordinate(
                                                xPx = cx,
                                                yPx = cy,
                                                antiBotEnabled = liveStep.antiBotRandomEnabled,
                                                radiusPx = radiusPx,
                                                screenWidth = sw,
                                                screenHeight = sh
                                            )
                                            performTapGesture(tapX, tapY, tapMs)
                                            if (!stateHolder.runtime.value.isClicking || conditionMatched.get()) break

                                            tapsInStep.incrementAndGet()
                                            val loopElapsed = SystemClock.elapsedRealtime() - startRealtime
                                            stateHolder.updateRuntime {
                                                it.copy(clickCount = it.clickCount + 1L, elapsedMs = loopElapsed)
                                            }
                                            overlayController.updateStatusReadout(completedCycles)
                                            delay(repeatDelayMs)
                                        }
                                    } finally {
                                        detectJob.cancel()
                                    }
                                    if (conditionMatched.get() && isActive && stateHolder.runtime.value.isClicking) {
                                        delay(liveStep.postDelayMs.coerceAtLeast(20L))
                                    }
                                }
                                ScenarioStepType.SINGLE_TAP -> {
                                    val (tapX, tapY) = ClickEngineCalculator.computeClickCoordinate(
                                        xPx = cx,
                                        yPx = cy,
                                        antiBotEnabled = liveStep.antiBotRandomEnabled,
                                        radiusPx = radiusPx,
                                        screenWidth = sw,
                                        screenHeight = sh
                                    )
                                    performTapGesture(tapX, tapY, liveStep.tapDurationMs.coerceIn(10L, 60_000L))
                                    if (!stateHolder.runtime.value.isClicking) break

                                    val stepElapsed = SystemClock.elapsedRealtime() - startRealtime
                                    stateHolder.updateRuntime {
                                        it.copy(clickCount = it.clickCount + 1L, elapsedMs = stepElapsed)
                                    }
                                    overlayController.updateStatusReadout(completedCycles)
                                    delay(liveStep.postDelayMs.coerceAtLeast(20L))
                                }
                                ScenarioStepType.SCROLL -> {
                                    val distancePx = dpToPx(liveStep.scrollDistanceDp.toFloat())
                                    val (startPt, endPt) = ClickEngineCalculator.computeSwipePoints(
                                        centerX = cx,
                                        centerY = cy,
                                        direction = liveStep.scrollDirection,
                                        distancePx = distancePx,
                                        screenWidth = sw,
                                        screenHeight = sh
                                    )
                                    performSwipeGesture(
                                        startX = startPt.first,
                                        startY = startPt.second,
                                        endX = endPt.first,
                                        endY = endPt.second,
                                        durationMs = liveStep.scrollDurationMs
                                    )
                                    if (!stateHolder.runtime.value.isClicking) break

                                    val stepElapsed = SystemClock.elapsedRealtime() - startRealtime
                                    stateHolder.updateRuntime {
                                        it.copy(clickCount = it.clickCount + 1L, elapsedMs = stepElapsed)
                                    }
                                    overlayController.updateStatusReadout(completedCycles)
                                    delay(liveStep.postDelayMs.coerceAtLeast(20L))
                                }
                            }
                        }
                        if (!isActive || !stateHolder.runtime.value.isClicking) break
                        completedCycles++
                        val cycleElapsed = SystemClock.elapsedRealtime() - startRealtime
                        if (ClickEngineCalculator.shouldStopByRepeatRule(activeConfig, completedCycles, cycleElapsed)) {
                            stopClicking()
                            break
                        }
                    }
                }
            }
        }
    }

    fun stopClicking() {
        clickJob?.cancel()
        clickJob = null
        tickerJob?.cancel()
        tickerJob = null
        if (::stateHolder.isInitialized) {
            stateHolder.updateRuntime { it.copy(isClicking = false) }
        }
        if (::overlayController.isInitialized) {
            overlayController.setTargetsTouchThrough(stateHolder.state.value.settings.isOverlayLocked)
            overlayController.updateStatusReadout(completedCycles)
        }
    }

    /**
     * Displays an interactive overlay so the user can drag over the target area to detect,
     * saving it as a cropped image template ([ScenarioStep.targetImagePath]) and optionally
     * resuming scenario execution immediately.
     */
    fun promptDetectionTargetPicker(step: ScenarioStep, resumeAfterSave: Boolean) {
        if (!::overlayController.isInitialized) return
        overlayController.openDetectionTargetPicker(
            step = step,
            resumeAfterSave = resumeAfterSave,
            onSaveCropRect = { cropRect ->
                serviceScope.launch {
                    overlayController.setAllOverlaysTemporarilyHidden(true)
                    delay(180L)
                    val savedPath = try {
                        ScreenDetector.captureAndCropTemplateImage(
                            service = this@AutoClickAccessibilityService,
                            cropRect = cropRect
                        )
                    } finally {
                        overlayController.setAllOverlaysTemporarilyHidden(false)
                    }
                    if (savedPath != null) {
                        stateHolder.updateScenarioStep(
                            step.copy(targetImagePath = savedPath)
                        )
                        if (resumeAfterSave) {
                            startClicking()
                        }
                    }
                }
            }
        )
    }

    private suspend fun performTapGesture(x: Float, y: Float, durationMs: Long): Boolean {
        val safeDuration = durationMs.coerceIn(10L, 60_000L)
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, safeDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchAwaitGesture(gesture, safeDuration + 1200L)
    }

    private suspend fun performSwipeGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long
    ): Boolean {
        val safeDuration = durationMs.coerceIn(80L, 5_000L)
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, safeDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchAwaitGesture(gesture, safeDuration + 1500L)
    }

    private suspend fun dispatchAwaitGesture(gesture: GestureDescription, timeoutMs: Long): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val dispatched = dispatchGesture(
                    gesture,
                    object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                    null
                )
                if (!dispatched && cont.isActive) {
                    cont.resume(false)
                }
            }
        } ?: false
    }

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

    companion object {
        @Volatile
        var instance: AutoClickAccessibilityService? = null
            private set
    }
}
