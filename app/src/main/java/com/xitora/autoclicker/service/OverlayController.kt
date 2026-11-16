package com.xitora.autoclicker.service

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.xitora.autoclicker.data.AutoClickerStateHolder
import com.xitora.autoclicker.domain.ClickEngineCalculator
import com.xitora.autoclicker.model.ClickMode
import com.xitora.autoclicker.model.ClickTimingConfig
import com.xitora.autoclicker.model.ScenarioStep
import com.xitora.autoclicker.model.ScenarioStepType
import com.xitora.autoclicker.model.ScrollDirection
import com.xitora.autoclicker.model.TargetPoint
import com.xitora.autoclicker.model.ThemeOption
import com.xitora.autoclicker.model.TimeUnitOption
import kotlin.math.roundToInt

/**
 * Manages the two screen overlays:
 * 1) Floating Control Panel ([FloatingControlPanelView])
 * 2) Generated Touch Targets ([TargetReticleView] list)
 * plus the optional modal editor displayed over apps.
 */
class OverlayController(
    private val serviceContext: Context,
    private val stateHolder: AutoClickerStateHolder,
    private val onToggleRun: () -> Unit,
    private val onPauseBeforeEdit: () -> Unit,
    private val onCloseOverlay: () -> Unit
) {
    private val windowManager = serviceContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var controlPanelView: FloatingControlPanelView? = null
    private var controlPanelParams: WindowManager.LayoutParams? = null

    private val targetHolders = mutableListOf<TargetWindowHolder>()
    private var activeModalView: View? = null
    private var panelXF = 24f
    private var panelYF = 240f

    private data class TargetWindowHolder(
        val targetId: String,
        val view: TargetReticleView,
        val params: WindowManager.LayoutParams,
        var centerXF: Float,
        var centerYF: Float
    )

    private fun overlayWindowType(): Int {
        return WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
    }

    private fun isEffectiveDark(): Boolean {
        return when (stateHolder.state.value.settings.themeOption) {
            ThemeOption.DARK -> true
            ThemeOption.LIGHT -> false
            ThemeOption.SYSTEM -> {
                val nightFlags = serviceContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightFlags == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun screenDimensions(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val dm = serviceContext.resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }

    fun showOverlays(mode: ClickMode) {
        ensureControlPanelShown()
        rebuildTargets(mode)
        updateStatusReadout(completedCycles = 0L)
    }

    fun hideAllOverlays() {
        dismissModal()
        targetHolders.forEach { holder ->
            runCatching { windowManager.removeView(holder.view) }
        }
        targetHolders.clear()
        controlPanelView?.let { panel ->
            runCatching { windowManager.removeView(panel) }
        }
        controlPanelView = null
        controlPanelParams = null
    }

    private fun ensureControlPanelShown() {
        if (controlPanelView != null) return
        val rt = stateHolder.runtime.value
        val (screenW, screenH) = screenDimensions()

        panelXF = rt.controlPanelX.coerceIn(0, (screenW - 140).coerceAtLeast(0)).toFloat()
        panelYF = rt.controlPanelY.coerceIn(40, (screenH - 380).coerceAtLeast(40)).toFloat()

        val panel = FloatingControlPanelView(
            context = serviceContext,
            onCloseClick = onCloseOverlay,
            onSettingsClick = { openModeSettingsModal() },
            onAddTargetClick = {
                val (sw, sh) = screenDimensions()
                val mode = stateHolder.runtime.value.activeMode
                if (mode == ClickMode.SCENARIO) {
                    val existingCount = stateHolder.state.value.scenarioSteps.size
                    val spawnX = (sw * 0.5f + ((existingCount % 3) - 1) * 90f).coerceIn(100f, (sw - 100).toFloat())
                    val spawnY = (sh * 0.52f + ((existingCount / 3) % 3) * 90f).coerceIn(180f, (sh - 180).toFloat())
                    stateHolder.addScenarioStep(ScenarioStepType.SINGLE_TAP, spawnX, spawnY)
                } else {
                    val existingCount = stateHolder.state.value.multiTargets.size
                    val spawnX = (sw * 0.5f + ((existingCount % 3) - 1) * 90f).coerceIn(100f, (sw - 100).toFloat())
                    val spawnY = (sh * 0.52f + ((existingCount / 3) % 3) * 90f).coerceIn(180f, (sh - 180).toFloat())
                    stateHolder.addMultiTarget(spawnX, spawnY)
                }
            },
            onRemoveTargetClick = {
                if (stateHolder.runtime.value.activeMode == ClickMode.SCENARIO) {
                    stateHolder.removeLastScenarioStep()
                } else {
                    stateHolder.removeLastMultiTarget()
                }
            },
            onToggleRunClick = onToggleRun,
            onPanelDrag = { dx, dy ->
                val lp = controlPanelParams ?: return@FloatingControlPanelView
                val (sw, sh) = screenDimensions()
                val panelW = controlPanelView?.width?.takeIf { it > 0 } ?: 140
                val panelH = controlPanelView?.height?.takeIf { it > 0 } ?: 420
                panelXF = (panelXF + dx).coerceIn(0f, (sw - panelW).coerceAtLeast(0).toFloat())
                panelYF = (panelYF + dy).coerceIn(0f, (sh - panelH).coerceAtLeast(0).toFloat())
                lp.x = panelXF.roundToInt()
                lp.y = panelYF.roundToInt()
                runCatching { windowManager.updateViewLayout(controlPanelView, lp) }
                stateHolder.updateRuntime { it.copy(controlPanelX = lp.x, controlPanelY = lp.y) }
            }
        )

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = rt.controlPanelX.coerceIn(0, (screenW - 140).coerceAtLeast(0))
            y = rt.controlPanelY.coerceIn(40, (screenH - 380).coerceAtLeast(40))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        windowManager.addView(panel, lp)
        controlPanelView = panel
        controlPanelParams = lp
    }

    private data class RenderedReticleSpec(
        val carrier: TargetPoint,
        val badgeText: String? = null,
        val scrollDirection: ScrollDirection? = null
    )

    private fun ScenarioStep.asTargetPointCarrier(): TargetPoint {
        return TargetPoint(
            id = id,
            order = order,
            xPx = xPx,
            yPx = yPx,
            intervalValue = postDelayMs,
            intervalUnit = TimeUnitOption.MS,
            durationValue = tapDurationMs,
            durationUnit = TimeUnitOption.MS,
            antiBotRandomEnabled = antiBotRandomEnabled,
            randomRadiusDp = randomRadiusDp
        )
    }

    fun rebuildTargets(mode: ClickMode) {
        val data = stateHolder.state.value
        val specs = when (mode) {
            ClickMode.SINGLE -> listOf(RenderedReticleSpec(data.singleTarget))
            ClickMode.MULTI -> data.multiTargets.map { RenderedReticleSpec(it) }
            ClickMode.SCENARIO -> data.scenarioSteps.map { step ->
                RenderedReticleSpec(
                    carrier = step.asTargetPointCarrier(),
                    badgeText = step.badgeText(),
                    scrollDirection = step.scrollDirection.takeIf { step.type == ScenarioStepType.SCROLL }
                )
            }
        }
        val isRunning = stateHolder.runtime.value.isClicking

        // Remove existing targets cleanly
        targetHolders.forEach { holder ->
            runCatching { windowManager.removeView(holder.view) }
        }
        targetHolders.clear()

        val (screenW, screenH) = screenDimensions()
        val sizeDp = data.settings.targetSizeDp
        val opacity = data.settings.targetOpacityPercent
        val showZoneGuide = data.settings.showAntiBotZoneGuide

        specs.forEach { spec ->
            val target = spec.carrier
            val reticleView = TargetReticleView(
                context = serviceContext,
                target = target,
                reticleSizeDp = sizeDp,
                opacityPercent = opacity,
                showAntiBotZoneGuide = showZoneGuide,
                customBadgeText = spec.badgeText,
                scrollDirection = spec.scrollDirection,
                onMoved = { pt, dx, dy ->
                    val holder = targetHolders.firstOrNull { it.targetId == pt.id } ?: return@TargetReticleView
                    val (sw, sh) = screenDimensions()
                    val winSize = holder.view.requiredWindowSizePx()
                    val half = winSize / 2f
                    holder.centerXF = (holder.centerXF + dx).coerceIn(24f, (sw - 24).toFloat())
                    holder.centerYF = (holder.centerYF + dy).coerceIn(24f, (sh - 24).toFloat())
                    holder.params.x = (holder.centerXF - half).roundToInt()
                    holder.params.y = (holder.centerYF - half).roundToInt()
                    runCatching { windowManager.updateViewLayout(holder.view, holder.params) }
                    when (stateHolder.runtime.value.activeMode) {
                        ClickMode.SINGLE -> stateHolder.updateSingleTargetPosition(holder.centerXF, holder.centerYF)
                        ClickMode.MULTI -> stateHolder.updateMultiTargetPosition(pt.id, holder.centerXF, holder.centerYF)
                        ClickMode.SCENARIO -> stateHolder.updateScenarioStepPosition(pt.id, holder.centerXF, holder.centerYF)
                    }
                },
                onTap = { pt ->
                    openSingleTargetModal(pt)
                }
            ).apply {
                isLockedForClicking = isRunning
            }

            val winSize = reticleView.requiredWindowSizePx()
            val half = winSize / 2f
            val clampedX = target.xPx.coerceIn(36f, (screenW - 36).toFloat())
            val clampedY = target.yPx.coerceIn(60f, (screenH - 60).toFloat())

            val flags = baseTargetFlags(isRunning)
            val lp = WindowManager.LayoutParams(
                winSize,
                winSize,
                overlayWindowType(),
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (clampedX - half).roundToInt()
                y = (clampedY - half).roundToInt()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            windowManager.addView(reticleView, lp)
            targetHolders.add(TargetWindowHolder(target.id, reticleView, lp, clampedX, clampedY))
        }
    }

    private fun baseTargetFlags(isRunning: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (isRunning) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return flags
    }

    fun setTargetsTouchThrough(touchThrough: Boolean) {
        targetHolders.forEach { holder ->
            holder.view.isLockedForClicking = touchThrough
            holder.view.alpha = 1f
            holder.params.flags = baseTargetFlags(touchThrough)
            runCatching { windowManager.updateViewLayout(holder.view, holder.params) }
        }
    }

    fun setAllOverlaysTemporarilyHidden(hidden: Boolean) {
        val alphaVal = if (hidden) 0f else 1f
        controlPanelView?.alpha = alphaVal
        targetHolders.forEach { holder ->
            holder.view.alpha = alphaVal
        }
    }

    /**
     * Reads exact physical screen coordinates of the target reticle center so [dispatchGesture]
     * lands dead-center inside the crosshair regardless of display cutouts or status bars.
     */
    fun getTargetScreenCenter(target: TargetPoint): Pair<Float, Float> {
        val holder = targetHolders.firstOrNull { it.targetId == target.id }
        if (holder != null && holder.view.isAttachedToWindow && holder.view.width > 0) {
            val loc = IntArray(2)
            holder.view.getLocationOnScreen(loc)
            return (loc[0] + holder.view.width / 2f) to (loc[1] + holder.view.height / 2f)
        }
        return target.xPx to target.yPx
    }

    fun getScenarioStepScreenCenter(step: ScenarioStep): Pair<Float, Float> {
        return getTargetScreenCenter(step.asTargetPointCarrier())
    }

    fun screenBounds(): Pair<Int, Int> = screenDimensions()

    fun updateStatusReadout(completedCycles: Long) {
        val rt = stateHolder.runtime.value
        val data = stateHolder.state.value
        val activeConfig = when (rt.activeMode) {
            ClickMode.SINGLE -> data.singleConfig
            ClickMode.MULTI -> data.multiConfig
            ClickMode.SCENARIO -> data.scenarioConfig
        }
        controlPanelView?.updateState(
            mode = rt.activeMode,
            isRunning = rt.isClicking,
            elapsedMs = rt.elapsedMs,
            clickCount = rt.clickCount,
            completedCycles = completedCycles,
            activeConfig = activeConfig,
            isDarkSurface = isEffectiveDark(),
            currentScenarioStepOrder = rt.currentScenarioStepOrder,
            totalScenarioSteps = data.scenarioSteps.size
        )
    }

    private fun openModeSettingsModal() {
        onPauseBeforeEdit()
        dismissModal()
        val rt = stateHolder.runtime.value
        val data = stateHolder.state.value
        val activeConfig = when (rt.activeMode) {
            ClickMode.SINGLE -> data.singleConfig
            ClickMode.MULTI -> data.multiConfig
            ClickMode.SCENARIO -> data.scenarioConfig
        }
        val title = when (rt.activeMode) {
            ClickMode.SINGLE -> "단일 타겟 클릭 설정"
            ClickMode.MULTI -> "다중 타겟 공통 설정"
            ClickMode.SCENARIO -> "시나리오 전체 반복 설정"
        }
        val subtitle = if (rt.activeMode == ClickMode.SCENARIO) {
            "단계별 상세 편집·이미지 업로드는 앱 내 시나리오 편집기에서 설정하세요."
        } else {
            "설정한 간격·지속시간·반복·봇 방지 옵션이 바로 적용됩니다."
        }
        val modal = OverlayModalFactory.buildTimingModal(
            context = serviceContext,
            title = title,
            subtitle = subtitle,
            initialConfig = activeConfig,
            showRepeatOptions = true,
            isDark = isEffectiveDark(),
            onCancel = { dismissModal() },
            onSave = { updated ->
                when (rt.activeMode) {
                    ClickMode.SINGLE -> stateHolder.updateSingleConfig(updated)
                    ClickMode.MULTI -> stateHolder.updateMultiConfig(updated, syncToTargets = true)
                    ClickMode.SCENARIO -> stateHolder.updateScenarioConfig(updated)
                }
                dismissModal()
            }
        )
        showModalView(modal)
    }

    private fun openSingleTargetModal(target: TargetPoint) {
        onPauseBeforeEdit()
        dismissModal()
        val rt = stateHolder.runtime.value
        if (rt.activeMode == ClickMode.SCENARIO) {
            val step = stateHolder.state.value.scenarioSteps.firstOrNull { it.id == target.id } ?: return
            val canDelete = stateHolder.state.value.scenarioSteps.size > 1
            val modal = OverlayModalFactory.buildScenarioStepModal(
                context = serviceContext,
                step = step,
                isDark = isEffectiveDark(),
                canDelete = canDelete,
                onPickOnScreen = { draftStep ->
                    stateHolder.updateScenarioStep(draftStep)
                    dismissModal()
                    AutoClickAccessibilityService.instance?.promptDetectionTargetPicker(
                        step = draftStep,
                        resumeAfterSave = false
                    )
                },
                onDelete = {
                    stateHolder.removeScenarioStepById(step.id)
                    dismissModal()
                },
                onCancel = { dismissModal() },
                onSave = { updatedStep ->
                    stateHolder.updateScenarioStep(updatedStep)
                    dismissModal()
                }
            )
            showModalView(modal)
            return
        }
        val isSingleMode = rt.activeMode == ClickMode.SINGLE
        val initial = if (isSingleMode) {
            stateHolder.state.value.singleConfig
        } else {
            ClickTimingConfig(
                intervalValue = target.intervalValue,
                intervalUnit = target.intervalUnit,
                durationValue = target.durationValue,
                durationUnit = target.durationUnit,
                antiBotRandomEnabled = target.antiBotRandomEnabled,
                randomRadiusDp = target.randomRadiusDp
            )
        }
        val canDelete = !isSingleMode && stateHolder.state.value.multiTargets.size > 1
        val modal = OverlayModalFactory.buildTimingModal(
            context = serviceContext,
            title = if (isSingleMode) "타겟 지점 설정" else "타겟 #${target.order} 개별 설정",
            subtitle = "이 지점의 클릭 간격, 지속시간 및 봇 방지 랜덤 반경을 설정합니다.",
            initialConfig = initial,
            showRepeatOptions = isSingleMode,
            isDark = isEffectiveDark(),
            showDeleteButton = canDelete,
            onDelete = {
                stateHolder.removeMultiTargetById(target.id)
                dismissModal()
            },
            onCancel = { dismissModal() },
            onSave = { updated ->
                if (isSingleMode) {
                    stateHolder.updateSingleConfig(updated)
                } else {
                    stateHolder.updateMultiTarget(
                        target.copy(
                            intervalValue = updated.intervalValue,
                            intervalUnit = updated.intervalUnit,
                            durationValue = updated.durationValue,
                            durationUnit = updated.durationUnit,
                            antiBotRandomEnabled = updated.antiBotRandomEnabled,
                            randomRadiusDp = updated.randomRadiusDp
                        )
                    )
                }
                dismissModal()
            }
        )
        showModalView(modal)
    }

    fun openDetectionTargetPicker(
        step: ScenarioStep,
        resumeAfterSave: Boolean,
        onSaveCropRect: (Rect) -> Unit
    ) {
        dismissModal()
        val pickerView = OverlayModalFactory.buildScreenDetectionPickerOverlay(
            context = serviceContext,
            step = step,
            isDark = isEffectiveDark(),
            resumeAfterSave = resumeAfterSave,
            onSaveCropRect = { rect ->
                dismissModal()
                onSaveCropRect(rect)
            },
            onCancel = { dismissModal() }
        )
        showModalView(pickerView)
    }

    private fun showModalView(view: View) {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        runCatching {
            windowManager.addView(view, lp)
            activeModalView = view
        }
    }

    fun dismissModal() {
        activeModalView?.let { v ->
            runCatching { windowManager.removeView(v) }
        }
        activeModalView = null
    }
}
