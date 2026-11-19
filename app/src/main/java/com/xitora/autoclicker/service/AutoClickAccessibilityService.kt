package com.xitora.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.BatteryManager
import android.os.SystemClock
import android.util.TypedValue
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.xitora.autoclicker.data.AutoClickerStateHolder
import com.xitora.autoclicker.domain.ClickEngineCalculator
import com.xitora.autoclicker.domain.ScenarioDetectGuard
import com.xitora.autoclicker.model.ClickMode
import com.xitora.autoclicker.model.NormalizedRect
import com.xitora.autoclicker.model.ScenarioStep
import com.xitora.autoclicker.model.ScenarioStepType
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.random.Random

class AutoClickAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var stateHolder: AutoClickerStateHolder
    private lateinit var overlayController: OverlayController

    private var clickJob: Job? = null
    private var tickerJob: Job? = null
    private var completedCycles = 0L
    private var lastVolDownPressMs = 0L
    private var lastVolUpPressMs = 0L
    private var lastAutoSwitchedPkg: String? = null

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
        syncKeyEventFilterFlag()
    }

    private fun syncKeyEventFilterFlag() {
        if (!::stateHolder.isInitialized) return
        val info = serviceInfo ?: return
        val wantFilter = stateHolder.state.value.settings.volumeKeyControlEnabled
        val hasFlag = (info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0
        if (wantFilter != hasFlag) {
            info.flags = if (wantFilter) {
                info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            } else {
                info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
            }
            serviceInfo = info
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null || event.action != KeyEvent.ACTION_DOWN) return false
        if (!::stateHolder.isInitialized) return false
        val settings = stateHolder.state.value.settings
        val rt = stateHolder.runtime.value
        if (!settings.volumeKeyControlEnabled || !rt.isOverlayVisible) return false

        val now = SystemClock.elapsedRealtime()
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (now - lastVolDownPressMs <= 450L) {
                    lastVolDownPressMs = 0L
                    toggleClicking()
                    true
                } else {
                    lastVolDownPressMs = now
                    false
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (now - lastVolUpPressMs <= 450L) {
                    lastVolUpPressMs = 0L
                    stateHolder.updateSettings { it.copy(isOverlayLocked = !it.isOverlayLocked) }
                    true
                } else {
                    lastVolUpPressMs = now
                    false
                }
            }
            else -> false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!::stateHolder.isInitialized) return
        val data = stateHolder.state.value
        if (!data.settings.autoSwitchPresetByApp) return

        val pkg = event.packageName?.toString()?.trim() ?: return
        if (pkg.isEmpty() || pkg == packageName || pkg == "com.android.systemui") return

        val matchedPreset = data.presets.firstOrNull { it.boundPackageName == pkg }
        if (matchedPreset != null) {
            if (lastAutoSwitchedPkg != pkg) {
                lastAutoSwitchedPkg = pkg
                stateHolder.applyPreset(matchedPreset)
                showOverlay(matchedPreset.mode)
            }
        } else if (lastAutoSwitchedPkg != null) {
            lastAutoSwitchedPkg = null
            if (stateHolder.runtime.value.isClicking) {
                stopClicking()
            }
        }
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
        syncKeyEventFilterFlag()
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
                if (data.settings.maxRunMinutes > 0 && elapsed >= data.settings.maxRunMinutes * 60_000L) {
                    stopClicking()
                    break
                }
                if (data.settings.batteryStopPercent > 0) {
                    val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                    val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
                    if (pct in 1..data.settings.batteryStopPercent) {
                        stopClicking()
                        break
                    }
                }
                delay(200L)
            }
        }

        clickJob?.cancel()
        clickJob = serviceScope.launch {
            // Brief yield so WindowManager applies FLAG_NOT_TOUCHABLE before the very first tap
            delay(50L)
            var lastBreakRealtime = SystemClock.elapsedRealtime()

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
                        val baseDurationMs = ClickEngineCalculator.resolveDurationMs(
                            activeConfig.durationValue,
                            activeConfig.durationUnit
                        )
                        val durationMs = ClickEngineCalculator.applyDurationJitterMs(
                            baseDurationMs,
                            activeConfig.durationJitterMs
                        )
                        val baseIntervalMs = ClickEngineCalculator.resolveIntervalMs(
                            activeConfig.intervalValue,
                            activeConfig.intervalUnit
                        )
                        val intervalMs = ClickEngineCalculator.applyJitterMs(
                            baseIntervalMs,
                            activeConfig.intervalJitterPercent
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
                        if (activeConfig.breakEveryMinutes > 0 &&
                            SystemClock.elapsedRealtime() - lastBreakRealtime >= activeConfig.breakEveryMinutes * 60_000L
                        ) {
                            lastBreakRealtime = SystemClock.elapsedRealtime()
                            delay(activeConfig.breakDurationSec.coerceIn(2, 120) * 1000L)
                        } else {
                            delay(intervalMs)
                        }
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
                            val baseDurationMs = ClickEngineCalculator.resolveDurationMs(
                                pt.durationValue,
                                pt.durationUnit
                            )
                            val durationMs = ClickEngineCalculator.applyDurationJitterMs(
                                baseDurationMs,
                                activeConfig.durationJitterMs
                            )
                            val baseIntervalMs = ClickEngineCalculator.resolveIntervalMs(
                                pt.intervalValue,
                                pt.intervalUnit
                            )
                            val intervalMs = ClickEngineCalculator.applyJitterMs(
                                baseIntervalMs,
                                activeConfig.intervalJitterPercent
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
                        var stepIdx = 0
                        var cycleDispatches = 0
                        val maxDispatchesPerCycle = (steps.size * 6).coerceAtLeast(12)
                        while (stepIdx in steps.indices && isActive && stateHolder.runtime.value.isClicking && cycleDispatches < maxDispatchesPerCycle) {
                            cycleDispatches++
                            val step = steps[stepIdx]
                            val liveStep = stateHolder.state.value.scenarioSteps.firstOrNull { it.id == step.id } ?: step
                            if (!liveStep.enabled) {
                                stepIdx++
                                continue
                            }
                            stateHolder.updateRuntime { it.copy(currentScenarioStepOrder = liveStep.order) }
                            overlayController.updateStatusReadout(completedCycles)

                            val (cx, cy) = overlayController.getScenarioStepScreenCenter(liveStep)
                            val (sw, sh) = overlayController.screenBounds()
                            val radiusPx = dpToPx(liveStep.randomRadiusDp.toFloat())
                            var jumpOrder: Int? = null

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
                                    val stepStartMs = SystemClock.elapsedRealtime()
                                    val conditionMatched = AtomicBoolean(false)
                                    val timedOut = AtomicBoolean(false)

                                    val detectJob = serviceScope.launch(Dispatchers.Default) {
                                        var lastScreenshotCheckMs = 0L
                                        while (isActive && stateHolder.runtime.value.isClicking && !conditionMatched.get() && !timedOut.get()) {
                                            val currentTaps = tapsInStep.get()
                                            val nowMs = SystemClock.elapsedRealtime()
                                            if (liveStep.detectTimeoutMs > 0L && nowMs - stepStartMs >= liveStep.detectTimeoutMs) {
                                                timedOut.set(true)
                                                break
                                            }
                                            var detected = false
                                            var evaluated = false
                                            if (nowMs - lastScreenshotCheckMs >= 1000L) {
                                                val matchResult = ScreenDetector.detectMultiImageInService(
                                                    service = this@AutoClickAccessibilityService,
                                                    primaryPath = liveStep.targetImagePath,
                                                    extraPaths = liveStep.extraImagePaths,
                                                    matchLogic = liveStep.matchLogic,
                                                    similarityPercent = liveStep.imageSimilarityPercent,
                                                    roiNorm = liveStep.detectRoiRectNorm
                                                )
                                                if (matchResult != null) {
                                                    lastScreenshotCheckMs = SystemClock.elapsedRealtime()
                                                    stateHolder.updateRuntime { it.copy(lastDetectScore = matchResult.bestScore) }
                                                    evaluated = true
                                                    detected = if (liveStep.invertCondition) !matchResult.matched else matchResult.matched
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
                                        while (isActive && stateHolder.runtime.value.isClicking && !conditionMatched.get() && !timedOut.get()) {
                                            if (liveStep.detectTimeoutMs > 0L && SystemClock.elapsedRealtime() - stepStartMs >= liveStep.detectTimeoutMs) {
                                                timedOut.set(true)
                                                break
                                            }
                                            val (tapX, tapY) = ClickEngineCalculator.computeClickCoordinate(
                                                xPx = cx,
                                                yPx = cy,
                                                antiBotEnabled = liveStep.antiBotRandomEnabled,
                                                radiusPx = radiusPx,
                                                screenWidth = sw,
                                                screenHeight = sh
                                            )
                                            performTapGesture(
                                                tapX,
                                                tapY,
                                                ClickEngineCalculator.applyDurationJitterMs(tapMs, activeConfig.durationJitterMs)
                                            )
                                            if (!stateHolder.runtime.value.isClicking || conditionMatched.get() || timedOut.get()) break

                                            tapsInStep.incrementAndGet()
                                            val loopElapsed = SystemClock.elapsedRealtime() - startRealtime
                                            stateHolder.updateRuntime {
                                                it.copy(clickCount = it.clickCount + 1L, elapsedMs = loopElapsed)
                                            }
                                            overlayController.updateStatusReadout(completedCycles)
                                            val jitterPct = maxOf(liveStep.intervalJitterPercent, activeConfig.intervalJitterPercent)
                                            delay(ClickEngineCalculator.applyJitterMs(repeatDelayMs, jitterPct, minMs = 20L))
                                        }
                                    } finally {
                                        detectJob.cancel()
                                    }
                                    if (isActive && stateHolder.runtime.value.isClicking) {
                                        jumpOrder = if (timedOut.get()) liveStep.onTimeoutJumpOrder else liveStep.onMatchJumpOrder
                                        if (conditionMatched.get()) {
                                            delay(liveStep.postDelayMs.coerceAtLeast(20L))
                                        }
                                    }
                                }
                                ScenarioStepType.WAIT_FOR_IMAGE,
                                ScenarioStepType.TAP_DETECTED_IMAGE,
                                ScenarioStepType.COLOR_MATCH -> {
                                    if (!ScreenDetector.hasValidDetectionTarget(liveStep)) {
                                        stopClicking()
                                        if (liveStep.type != ScenarioStepType.COLOR_MATCH) {
                                            promptDetectionTargetPicker(step = liveStep, resumeAfterSave = true)
                                        }
                                        return@launch
                                    }
                                    val pollMs = liveStep.detectCheckIntervalMs.coerceAtLeast(250L)
                                    val stepStartMs = SystemClock.elapsedRealtime()
                                    var matched = false
                                    var timedOut = false
                                    val matchedRectRef = AtomicReference<android.graphics.Rect?>(null)

                                    while (isActive && stateHolder.runtime.value.isClicking) {
                                        val nowMs = SystemClock.elapsedRealtime()
                                        if (liveStep.detectTimeoutMs > 0L && nowMs - stepStartMs >= liveStep.detectTimeoutMs) {
                                            timedOut = true
                                            break
                                        }
                                        val res = if (liveStep.type == ScenarioStepType.COLOR_MATCH) {
                                            ScreenDetector.detectPixelColorInService(
                                                service = this@AutoClickAccessibilityService,
                                                xPx = cx,
                                                yPx = cy,
                                                targetColorHex = liveStep.targetColorHex,
                                                tolerance = liveStep.colorTolerance
                                            )
                                        } else {
                                            ScreenDetector.detectMultiImageInService(
                                                service = this@AutoClickAccessibilityService,
                                                primaryPath = liveStep.targetImagePath,
                                                extraPaths = liveStep.extraImagePaths,
                                                matchLogic = liveStep.matchLogic,
                                                similarityPercent = liveStep.imageSimilarityPercent,
                                                roiNorm = liveStep.detectRoiRectNorm
                                            )
                                        }
                                        if (res != null) {
                                            stateHolder.updateRuntime { it.copy(lastDetectScore = res.bestScore) }
                                            val effectiveMatch = if (liveStep.invertCondition) !res.matched else res.matched
                                            if (effectiveMatch) {
                                                matched = true
                                                matchedRectRef.set(res.matchedRect)
                                                break
                                            }
                                        }
                                        delay(pollMs.coerceAtLeast(900L))
                                    }
                                    if (!isActive || !stateHolder.runtime.value.isClicking) break
                                    if (matched && liveStep.type == ScenarioStepType.TAP_DETECTED_IMAGE) {
                                        val rect = matchedRectRef.get()
                                        val tx = rect?.exactCenterX() ?: cx
                                        val ty = rect?.exactCenterY() ?: cy
                                        val (tapX, tapY) = ClickEngineCalculator.computeClickCoordinate(
                                            xPx = tx,
                                            yPx = ty,
                                            antiBotEnabled = liveStep.antiBotRandomEnabled,
                                            radiusPx = radiusPx,
                                            screenWidth = sw,
                                            screenHeight = sh
                                        )
                                        performTapGesture(
                                            tapX,
                                            tapY,
                                            ClickEngineCalculator.applyDurationJitterMs(liveStep.tapDurationMs, activeConfig.durationJitterMs)
                                        )
                                        val stepElapsed = SystemClock.elapsedRealtime() - startRealtime
                                        stateHolder.updateRuntime {
                                            it.copy(clickCount = it.clickCount + 1L, elapsedMs = stepElapsed)
                                        }
                                        overlayController.updateStatusReadout(completedCycles)
                                    }
                                    jumpOrder = if (timedOut) liveStep.onTimeoutJumpOrder else liveStep.onMatchJumpOrder
                                    if (matched) {
                                        delay(liveStep.postDelayMs.coerceAtLeast(20L))
                                    }
                                }
                                ScenarioStepType.SINGLE_TAP,
                                ScenarioStepType.LONG_PRESS -> {
                                    val (tapX, tapY) = ClickEngineCalculator.computeClickCoordinate(
                                        xPx = cx,
                                        yPx = cy,
                                        antiBotEnabled = liveStep.antiBotRandomEnabled,
                                        radiusPx = radiusPx,
                                        screenWidth = sw,
                                        screenHeight = sh
                                    )
                                    val baseTapDur = if (liveStep.type == ScenarioStepType.LONG_PRESS) {
                                        liveStep.tapDurationMs.coerceAtLeast(300L)
                                    } else {
                                        liveStep.tapDurationMs.coerceIn(10L, 60_000L)
                                    }
                                    performTapGesture(
                                        tapX,
                                        tapY,
                                        ClickEngineCalculator.applyDurationJitterMs(baseTapDur, activeConfig.durationJitterMs)
                                    )
                                    if (!stateHolder.runtime.value.isClicking) break

                                    val stepElapsed = SystemClock.elapsedRealtime() - startRealtime
                                    stateHolder.updateRuntime {
                                        it.copy(clickCount = it.clickCount + 1L, elapsedMs = stepElapsed)
                                    }
                                    overlayController.updateStatusReadout(completedCycles)
                                    delay(liveStep.postDelayMs.coerceAtLeast(20L))
                                }
                                ScenarioStepType.DELAY_RANDOM -> {
                                    val low = liveStep.delayMinMs.coerceAtLeast(50L)
                                    val high = liveStep.delayMaxMs.coerceAtLeast(low)
                                    val waitMs = if (low == high) low else Random.nextLong(low, high + 1L)
                                    delay(waitMs)
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
                            stepIdx = ClickEngineCalculator.resolveNextStepIndex(stepIdx, steps.size, jumpOrder)
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
            onSaveCropRect = { cropRect, useRoi ->
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
                        val roiNorm = if (useRoi) {
                            val (sw, sh) = overlayController.screenBounds()
                            val w = sw.coerceAtLeast(1).toFloat()
                            val h = sh.coerceAtLeast(1).toFloat()
                            NormalizedRect(
                                left = (cropRect.left / w).coerceIn(0f, 1f),
                                top = (cropRect.top / h).coerceIn(0f, 1f),
                                right = (cropRect.right / w).coerceIn(0f, 1f),
                                bottom = (cropRect.bottom / h).coerceIn(0f, 1f)
                            )
                        } else {
                            null
                        }
                        stateHolder.updateScenarioStep(
                            step.copy(targetImagePath = savedPath, detectRoiRectNorm = roiNorm)
                        )
                        if (resumeAfterSave) {
                            startClicking()
                        }
                    }
                }
            }
        )
    }

    fun testScenarioStepDetection(step: ScenarioStep) {
        if (!::overlayController.isInitialized) return
        serviceScope.launch {
            overlayController.setAllOverlaysTemporarilyHidden(true)
            delay(180L)
            val result = try {
                ScreenDetector.detectImageWithScoreInService(
                    service = this@AutoClickAccessibilityService,
                    imagePath = step.targetImagePath,
                    similarityPercent = step.imageSimilarityPercent,
                    roiNorm = step.detectRoiRectNorm
                )
            } finally {
                overlayController.setAllOverlaysTemporarilyHidden(false)
            }
            overlayController.showImageMatchTestOverlay(step, result)
        }
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
