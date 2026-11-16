package com.xitora.autoclicker.data

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.xitora.autoclicker.domain.ClickEngineCalculator
import com.xitora.autoclicker.model.*
import com.xitora.autoclicker.service.AutoClickAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Process-wide singleton holder shared cleanly between UI ViewModel and [AutoClickAccessibilityService].
 */
class AutoClickerStateHolder private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val repository = AutoClickerRepository(appContext)

    private val _state = MutableStateFlow(repository.loadState())
    val state: StateFlow<AutoClickerDataState> = _state.asStateFlow()

    private val _runtime = MutableStateFlow(
        OverlayRuntimeState(isAccessibilityEnabled = checkAccessibilityServiceEnabled())
    )
    val runtime: StateFlow<OverlayRuntimeState> = _runtime.asStateFlow()

    fun refreshAccessibilityStatus(): Boolean {
        val enabled = AutoClickAccessibilityService.instance != null || checkAccessibilityServiceEnabled()
        _runtime.update { it.copy(isAccessibilityEnabled = enabled) }
        return enabled
    }

    fun checkAccessibilityServiceEnabled(): Boolean {
        if (AutoClickAccessibilityService.instance != null) return true
        val expectedComponent = "${appContext.packageName}/${AutoClickAccessibilityService::class.java.name}"
        val enabledFlag = runCatching {
            Settings.Secure.getInt(appContext.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        }.getOrDefault(0)
        if (enabledFlag != 1) return false

        val settingValue = runCatching {
            Settings.Secure.getString(
                appContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        }.getOrNull() ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(settingValue)
        while (splitter.hasNext()) {
            val serviceStr = splitter.next()
            if (serviceStr.equals(expectedComponent, ignoreCase = true) ||
                serviceStr.equals("${appContext.packageName}/.service.AutoClickAccessibilityService", ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    private fun commit(transform: (AutoClickerDataState) -> AutoClickerDataState) {
        _state.update { current ->
            val next = transform(current)
            repository.saveState(next)
            next
        }
    }

    fun updateSingleConfig(config: ClickTimingConfig) {
        commit { s ->
            s.copy(
                singleConfig = config,
                singleTarget = s.singleTarget.copy(
                    intervalValue = config.intervalValue,
                    intervalUnit = config.intervalUnit,
                    durationValue = config.durationValue,
                    durationUnit = config.durationUnit,
                    antiBotRandomEnabled = config.antiBotRandomEnabled,
                    randomRadiusDp = config.randomRadiusDp
                )
            )
        }
        AutoClickAccessibilityService.instance?.syncFromState()
    }

    fun updateSingleTargetPosition(xPx: Float, yPx: Float) {
        commit { s ->
            s.copy(singleTarget = s.singleTarget.copy(xPx = xPx, yPx = yPx))
        }
    }

    fun updateMultiConfig(config: ClickTimingConfig, syncToTargets: Boolean = true) {
        commit { s ->
            val updatedTargets = if (syncToTargets) {
                s.multiTargets.map { pt ->
                    pt.copy(
                        intervalValue = config.intervalValue,
                        intervalUnit = config.intervalUnit,
                        durationValue = config.durationValue,
                        durationUnit = config.durationUnit,
                        antiBotRandomEnabled = config.antiBotRandomEnabled,
                        randomRadiusDp = config.randomRadiusDp
                    )
                }
            } else {
                s.multiTargets
            }
            s.copy(multiConfig = config, multiTargets = updatedTargets)
        }
        AutoClickAccessibilityService.instance?.syncFromState()
    }

    fun addMultiTarget(xPx: Float? = null, yPx: Float? = null): TargetPoint {
        var added: TargetPoint? = null
        commit { s ->
            val cfg = s.multiConfig
            val prev = s.multiTargets.lastOrNull()
            val defaultX = xPx ?: ((prev?.xPx ?: 420f) + 90f).coerceAtMost(960f)
            val defaultY = yPx ?: ((prev?.yPx ?: 980f) + 110f).coerceAtMost(1850f)
            val newPt = TargetPoint(
                id = UUID.randomUUID().toString(),
                order = s.multiTargets.size + 1,
                xPx = defaultX,
                yPx = defaultY,
                intervalValue = cfg.intervalValue,
                intervalUnit = cfg.intervalUnit,
                durationValue = cfg.durationValue,
                durationUnit = cfg.durationUnit,
                antiBotRandomEnabled = cfg.antiBotRandomEnabled,
                randomRadiusDp = cfg.randomRadiusDp
            )
            added = newPt
            s.copy(multiTargets = ClickEngineCalculator.reindexTargets(s.multiTargets + newPt))
        }
        AutoClickAccessibilityService.instance?.syncFromState()
        return added!!
    }

    fun removeLastMultiTarget(): Boolean {
        var removed = false
        commit { s ->
            if (s.multiTargets.size <= 1) return@commit s
            removed = true
            s.copy(multiTargets = ClickEngineCalculator.reindexTargets(s.multiTargets.dropLast(1)))
        }
        if (removed) AutoClickAccessibilityService.instance?.syncFromState()
        return removed
    }

    fun removeMultiTargetById(targetId: String): Boolean {
        var removed = false
        commit { s ->
            if (s.multiTargets.size <= 1) return@commit s
            val filtered = s.multiTargets.filterNot { it.id == targetId }
            if (filtered.size == s.multiTargets.size) return@commit s
            removed = true
            s.copy(multiTargets = ClickEngineCalculator.reindexTargets(filtered))
        }
        if (removed) AutoClickAccessibilityService.instance?.syncFromState()
        return removed
    }

    fun updateMultiTarget(target: TargetPoint) {
        commit { s ->
            val updated = s.multiTargets.map { if (it.id == target.id) target else it }
            s.copy(multiTargets = ClickEngineCalculator.reindexTargets(updated))
        }
        AutoClickAccessibilityService.instance?.syncFromState()
    }

    fun updateMultiTargetPosition(targetId: String, xPx: Float, yPx: Float) {
        commit { s ->
            val updated = s.multiTargets.map {
                if (it.id == targetId) it.copy(xPx = xPx, yPx = yPx) else it
            }
            s.copy(multiTargets = updated)
        }
    }

    fun savePreset(name: String, mode: ClickMode): ClickPreset {
        val current = _state.value
        val preset = when (mode) {
            ClickMode.SINGLE -> ClickPreset(
                name = name.trim().ifBlank { "단일 타겟 프리셋" },
                mode = ClickMode.SINGLE,
                timing = current.singleConfig,
                targets = listOf(current.singleTarget)
            )
            ClickMode.MULTI -> ClickPreset(
                name = name.trim().ifBlank { "다중 타겟 프리셋 (${current.multiTargets.size}개)" },
                mode = ClickMode.MULTI,
                timing = current.multiConfig,
                targets = current.multiTargets
            )
            ClickMode.SCENARIO -> ClickPreset(
                name = name.trim().ifBlank { "시나리오 프리셋 (${current.scenarioSteps.size}단계)" },
                mode = ClickMode.SCENARIO,
                timing = current.scenarioConfig,
                targets = emptyList(),
                scenarioSteps = current.scenarioSteps
            )
        }
        commit { s ->
            s.copy(presets = listOf(preset) + s.presets)
        }
        return preset
    }

    fun renamePreset(presetId: String, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        commit { s ->
            s.copy(presets = s.presets.map { if (it.id == presetId) it.copy(name = clean) else it })
        }
    }

    fun deletePreset(presetId: String) {
        commit { s ->
            s.copy(presets = s.presets.filterNot { it.id == presetId })
        }
    }

    fun applyPreset(preset: ClickPreset) {
        commit { s ->
            when (preset.mode) {
                ClickMode.SINGLE -> {
                    val target = preset.targets.firstOrNull() ?: s.singleTarget
                    s.copy(singleConfig = preset.timing, singleTarget = target)
                }
                ClickMode.MULTI -> {
                    val targets = preset.targets.ifEmpty { defaultMultiTargets() }
                    s.copy(
                        multiConfig = preset.timing,
                        multiTargets = ClickEngineCalculator.reindexTargets(targets)
                    )
                }
                ClickMode.SCENARIO -> {
                    val steps = preset.scenarioSteps.ifEmpty { defaultScenarioSteps() }
                    s.copy(
                        scenarioConfig = preset.timing,
                        scenarioSteps = ClickEngineCalculator.reindexScenarioSteps(steps)
                    )
                }
            }
        }
        AutoClickAccessibilityService.instance?.syncFromState()
    }

    fun updateScenarioConfig(config: ClickTimingConfig) {
        commit { s -> s.copy(scenarioConfig = config) }
        AutoClickAccessibilityService.instance?.syncFromState()
    }

    fun addScenarioStep(
        type: ScenarioStepType = ScenarioStepType.SINGLE_TAP,
        xPx: Float? = null,
        yPx: Float? = null
    ): ScenarioStep {
        var added: ScenarioStep? = null
        commit { s ->
            val order = s.scenarioSteps.size + 1
            val prev = s.scenarioSteps.lastOrNull()
            val defaultX = xPx ?: ((prev?.xPx ?: 420f) + 60f).coerceAtMost(960f)
            val defaultY = yPx ?: ((prev?.yPx ?: 980f) + 80f).coerceAtMost(1850f)
            val step = ScenarioStep(
                id = UUID.randomUUID().toString(),
                order = order,
                title = when (type) {
                    ScenarioStepType.REPEAT_UNTIL_DETECT -> "반복 터치 · 감지 대기"
                    ScenarioStepType.SINGLE_TAP -> "${order}번째 위치 클릭"
                    ScenarioStepType.SCROLL -> "정해진 위치 스크롤"
                },
                type = type,
                xPx = defaultX,
                yPx = defaultY,
                postDelayMs = if (type == ScenarioStepType.SCROLL) 600L else 450L
            )
            added = step
            s.copy(scenarioSteps = ClickEngineCalculator.reindexScenarioSteps(s.scenarioSteps + step))
        }
        AutoClickAccessibilityService.instance?.syncFromState()
        return added!!
    }

    fun updateScenarioStep(step: ScenarioStep) {
        commit { s ->
            val updated = s.scenarioSteps.map { if (it.id == step.id) step else it }
            s.copy(scenarioSteps = ClickEngineCalculator.reindexScenarioSteps(updated))
        }
        AutoClickAccessibilityService.instance?.syncFromState()
    }

    fun updateScenarioStepPosition(stepId: String, xPx: Float, yPx: Float) {
        commit { s ->
            val updated = s.scenarioSteps.map {
                if (it.id == stepId) it.copy(xPx = xPx, yPx = yPx) else it
            }
            s.copy(scenarioSteps = updated)
        }
    }

    fun removeScenarioStepById(stepId: String): Boolean {
        var removed = false
        commit { s ->
            if (s.scenarioSteps.size <= 1) return@commit s
            val filtered = s.scenarioSteps.filterNot { it.id == stepId }
            if (filtered.size == s.scenarioSteps.size) return@commit s
            removed = true
            s.copy(scenarioSteps = ClickEngineCalculator.reindexScenarioSteps(filtered))
        }
        if (removed) AutoClickAccessibilityService.instance?.syncFromState()
        return removed
    }

    fun removeLastScenarioStep(): Boolean {
        var removed = false
        commit { s ->
            if (s.scenarioSteps.size <= 1) return@commit s
            removed = true
            s.copy(scenarioSteps = ClickEngineCalculator.reindexScenarioSteps(s.scenarioSteps.dropLast(1)))
        }
        if (removed) AutoClickAccessibilityService.instance?.syncFromState()
        return removed
    }

    fun moveScenarioStep(stepId: String, moveUp: Boolean) {
        commit { s ->
            val list = s.scenarioSteps.toMutableList()
            val idx = list.indexOfFirst { it.id == stepId }
            if (idx < 0) return@commit s
            val swapIdx = if (moveUp) idx - 1 else idx + 1
            if (swapIdx !in list.indices) return@commit s
            val item = list.removeAt(idx)
            list.add(swapIdx, item)
            s.copy(scenarioSteps = ClickEngineCalculator.reindexScenarioSteps(list))
        }
        AutoClickAccessibilityService.instance?.syncFromState()
    }

    fun resetScenarioToDefault8Steps() {
        commit { s ->
            s.copy(
                scenarioConfig = ClickTimingConfig(),
                scenarioSteps = defaultScenarioSteps()
            )
        }
        AutoClickAccessibilityService.instance?.syncFromState()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        commit { s -> s.copy(settings = transform(s.settings)) }
        AutoClickAccessibilityService.instance?.syncFromState()
    }

    fun exportBackupJson(): String = repository.exportJson(_state.value)

    fun importBackupJson(rawJson: String): Result<Unit> {
        return repository.importJson(rawJson).map { restored ->
            _state.update {
                val safeMulti = if (restored.multiTargets.isEmpty()) defaultMultiTargets()
                else ClickEngineCalculator.reindexTargets(restored.multiTargets)
                val safeScenario = if (restored.scenarioSteps.isEmpty()) defaultScenarioSteps()
                else ClickEngineCalculator.reindexScenarioSteps(restored.scenarioSteps)
                val normalized = restored.copy(multiTargets = safeMulti, scenarioSteps = safeScenario)
                repository.saveState(normalized)
                normalized
            }
            AutoClickAccessibilityService.instance?.syncFromState()
        }
    }

    fun resetAllData() {
        val preservedSettings = _state.value.settings
        val fresh = AutoClickerDataState(settings = preservedSettings)
        _state.value = fresh
        repository.saveState(fresh)
        AutoClickAccessibilityService.instance?.syncFromState()
    }

    fun updateRuntime(transform: (OverlayRuntimeState) -> OverlayRuntimeState) {
        _runtime.update(transform)
    }

    companion object {
        @Volatile
        private var INSTANCE: AutoClickerStateHolder? = null

        fun getInstance(context: Context): AutoClickerStateHolder {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AutoClickerStateHolder(context).also { INSTANCE = it }
            }
        }
    }
}
