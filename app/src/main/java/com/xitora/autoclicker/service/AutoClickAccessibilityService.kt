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
import com.xitora.autoclicker.model.DetectionConditionType
import com.xitora.autoclicker.model.ScenarioStep
import com.xitora.autoclicker.model.ScenarioStepType
import kotlinx.coroutines.*
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
                                    var lastCondCheckMs = SystemClock.elapsedRealtime()
                                    var lastScreenshotCheckMs = 0L
                                    var tapsInStep = 0
                                    val detectGuard = ScenarioDetectGuard()
                                    val detectPollMs = liveStep.detectCheckIntervalMs.coerceAtLeast(200L)
                                    val repeatDelayMs = liveStep.repeatIntervalMs.coerceAtLeast(20L)
                                    val tapMs = liveStep.tapDurationMs.coerceIn(10L, 60_000L)

                                    while (isActive && stateHolder.runtime.value.isClicking) {
                                        val nowMs = SystemClock.elapsedRealtime()
                                        if (tapsInStep >= 1 && nowMs - lastCondCheckMs >= detectPollMs) {
                                            lastCondCheckMs = nowMs
                                            var detected = false
                                            var evaluated = false
                                            if (liveStep.conditionType != DetectionConditionType.IMAGE_ONLY &&
                                                liveStep.targetText.isNotBlank()
                                            ) {
                                                evaluated = true
                                                detected = ScreenDetector.detectTextInService(
                                                    service = this@AutoClickAccessibilityService,
                                                    query = liveStep.targetText
                                                )
                                            }
                                            if (!detected &&
                                                liveStep.conditionType != DetectionConditionType.TEXT_ONLY &&
                                                !liveStep.targetImagePath.isNullOrBlank() &&
                                                nowMs - lastScreenshotCheckMs >= 1100L
                                            ) {
                                                lastScreenshotCheckMs = nowMs
                                                val imgMatched = ScreenDetector.detectImageInService(
                                                    service = this@AutoClickAccessibilityService,
                                                    imagePath = liveStep.targetImagePath,
                                                    similarityPercent = liveStep.imageSimilarityPercent
                                                )
                                                if (imgMatched != null) {
                                                    evaluated = true
                                                    detected = imgMatched
                                                }
                                            }
                                            if (!isActive || !stateHolder.runtime.value.isClicking) break
                                            if (evaluated && detectGuard.onConditionEvaluated(detected, tapsInStep)) {
                                                delay(liveStep.postDelayMs.coerceAtLeast(20L))
                                                break
                                            }
                                        }

                                        val (tapX, tapY) = ClickEngineCalculator.computeClickCoordinate(
                                            xPx = cx,
                                            yPx = cy,
                                            antiBotEnabled = liveStep.antiBotRandomEnabled,
                                            radiusPx = radiusPx,
                                            screenWidth = sw,
                                            screenHeight = sh
                                        )
                                        performTapGesture(tapX, tapY, tapMs)
                                        if (!stateHolder.runtime.value.isClicking) break

                                        tapsInStep++
                                        val loopElapsed = SystemClock.elapsedRealtime() - startRealtime
                                        stateHolder.updateRuntime {
                                            it.copy(clickCount = it.clickCount + 1L, elapsedMs = loopElapsed)
                                        }
                                        overlayController.updateStatusReadout(completedCycles)
                                        delay(repeatDelayMs)
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
            overlayController.setTargetsTouchThrough(false)
            overlayController.updateStatusReadout(completedCycles)
        }
    }

    /**
     * Collects visible text regions on the active screen and displays an interactive overlay
     * so the user can tap or drag over the area to detect, saving it as [ScenarioStep.targetText]
     * (or cropped image template) and optionally resuming scenario execution immediately.
     */
    fun promptDetectionTargetPicker(step: ScenarioStep, resumeAfterSave: Boolean) {
        if (!::overlayController.isInitialized) return
        val candidates = ScreenDetector.collectScreenTextCandidates(this)
        overlayController.openDetectionTargetPicker(
            step = step,
            candidates = candidates,
            resumeAfterSave = resumeAfterSave,
            onSaveText = { chosenText ->
                stateHolder.updateScenarioStep(
                    step.copy(
                        targetText = chosenText,
                        conditionType = if (step.conditionType == DetectionConditionType.IMAGE_ONLY) {
                            DetectionConditionType.TEXT_OR_IMAGE
                        } else {
                            step.conditionType
                        }
                    )
                )
                if (resumeAfterSave) {
                    startClicking()
                }
            },
            onSaveCropRect = { cropRect ->
                serviceScope.launch {
                    delay(120L)
                    val savedPath = ScreenDetector.captureAndCropTemplateImage(
                        service = this@AutoClickAccessibilityService,
                        cropRect = cropRect
                    )
                    if (savedPath != null) {
                        stateHolder.updateScenarioStep(
                            step.copy(
                                targetImagePath = savedPath,
                                conditionType = if (step.conditionType == DetectionConditionType.TEXT_ONLY) {
                                    DetectionConditionType.TEXT_OR_IMAGE
                                } else {
                                    step.conditionType
                                }
                            )
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
