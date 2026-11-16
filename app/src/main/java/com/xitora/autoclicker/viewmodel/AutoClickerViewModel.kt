package com.xitora.autoclicker.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xitora.autoclicker.BuildConfig
import com.xitora.autoclicker.data.AutoClickerStateHolder
import com.xitora.autoclicker.model.*
import com.xitora.autoclicker.service.AutoClickAccessibilityService
import com.xitora.autoclicker.ui.components.AppUpdatePhase
import com.xitora.autoclicker.ui.components.AppUpdateUiState
import com.xitora.autoclicker.update.AppUpdater
import com.xitora.autoclicker.update.InstallLaunchOutcome
import com.xitora.autoclicker.update.UpdateCheckOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class AutoClickerViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    val stateHolder = AutoClickerStateHolder.getInstance(appContext)

    val state: StateFlow<AutoClickerDataState> = stateHolder.state
    val runtime: StateFlow<OverlayRuntimeState> = stateHolder.runtime

    private val _updateUiState = MutableStateFlow(
        AppUpdateUiState(currentVersionName = BuildConfig.VERSION_NAME)
    )
    val updateUiState: StateFlow<AppUpdateUiState> = _updateUiState.asStateFlow()

    private var downloadedApkFile: File? = null
    private var autoCheckTriggered = false

    init {
        AppUpdater.cleanupCache(appContext)
        stateHolder.refreshAccessibilityStatus()
        triggerStartupUpdateCheck()
    }

    fun refreshPermissions() {
        stateHolder.refreshAccessibilityStatus()
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { appContext.startActivity(intent) }
    }

    /**
     * Shows or hides the floating overlay for [mode].
     * Returns false when Accessibility Service is not yet enabled so UI can show guidance dialog.
     */
    fun toggleOverlay(mode: ClickMode): Boolean {
        val service = AutoClickAccessibilityService.instance
        if (service == null) {
            stateHolder.refreshAccessibilityStatus()
            return false
        }
        val rt = runtime.value
        if (rt.isOverlayVisible && rt.activeMode == mode) {
            service.hideOverlay()
        } else {
            service.showOverlay(mode)
        }
        return true
    }

    fun startOverlay(mode: ClickMode): Boolean {
        val service = AutoClickAccessibilityService.instance
        if (service == null) {
            stateHolder.refreshAccessibilityStatus()
            return false
        }
        service.showOverlay(mode)
        return true
    }

    fun hideOverlay() {
        AutoClickAccessibilityService.instance?.hideOverlay()
    }

    fun toggleOverlayClicking(): Boolean {
        val service = AutoClickAccessibilityService.instance
        if (service == null) {
            stateHolder.refreshAccessibilityStatus()
            return false
        }
        service.toggleClicking()
        return true
    }

    fun updateSingleConfig(config: ClickTimingConfig) = stateHolder.updateSingleConfig(config)

    fun updateMultiConfig(config: ClickTimingConfig, syncToTargets: Boolean = true) =
        stateHolder.updateMultiConfig(config, syncToTargets)

    fun addMultiTarget() = stateHolder.addMultiTarget()

    fun removeLastMultiTarget(): Boolean = stateHolder.removeLastMultiTarget()

    fun removeMultiTargetById(targetId: String): Boolean = stateHolder.removeMultiTargetById(targetId)

    fun updateMultiTarget(target: TargetPoint) = stateHolder.updateMultiTarget(target)

    fun updateScenarioConfig(config: ClickTimingConfig) = stateHolder.updateScenarioConfig(config)

    fun addScenarioStep(type: ScenarioStepType = ScenarioStepType.SINGLE_TAP) =
        stateHolder.addScenarioStep(type)

    fun updateScenarioStep(step: ScenarioStep) = stateHolder.updateScenarioStep(step)

    fun removeScenarioStepById(stepId: String): Boolean = stateHolder.removeScenarioStepById(stepId)

    fun moveScenarioStep(stepId: String, moveUp: Boolean) = stateHolder.moveScenarioStep(stepId, moveUp)

    fun resetScenarioToDefault8Steps() = stateHolder.resetScenarioToDefault8Steps()

    fun savePreset(name: String, mode: ClickMode): ClickPreset = stateHolder.savePreset(name, mode)

    fun renamePreset(presetId: String, newName: String) = stateHolder.renamePreset(presetId, newName)

    fun deletePreset(presetId: String) = stateHolder.deletePreset(presetId)

    fun applyPreset(preset: ClickPreset) = stateHolder.applyPreset(preset)

    fun updateTheme(option: ThemeOption) = stateHolder.updateSettings { it.copy(themeOption = option) }

    fun updateThemeColorStyle(style: ThemeColorStyle) =
        stateHolder.updateSettings { it.copy(themeColorStyle = style) }

    fun updateNavBarStyle(style: NavBarStyle) =
        stateHolder.updateSettings { it.copy(navBarStyle = style) }

    fun updateCustomKeyColor(colorHex: Long?) =
        stateHolder.updateSettings {
            it.copy(
                themeColorStyle = ThemeColorStyle.CUSTOM,
                customKeyColorHex = colorHex ?: 0xFF3482FF
            )
        }

    fun updateTargetSizeDp(sizeDp: Int) =
        stateHolder.updateSettings { it.copy(targetSizeDp = sizeDp.coerceIn(34, 72)) }

    fun updateTargetOpacity(percent: Int) =
        stateHolder.updateSettings { it.copy(targetOpacityPercent = percent.coerceIn(30, 100)) }

    fun updateShowAntiBotZoneGuide(show: Boolean) =
        stateHolder.updateSettings { it.copy(showAntiBotZoneGuide = show) }

    fun exportBackupJson(): String = stateHolder.exportBackupJson()

    fun importBackupJson(rawJson: String): Result<Unit> = stateHolder.importBackupJson(rawJson)

    fun resetAllData() = stateHolder.resetAllData()

    private fun triggerStartupUpdateCheck() {
        if (autoCheckTriggered) return
        autoCheckTriggered = true
        checkForUpdate(manual = false)
    }

    fun checkForUpdate(manual: Boolean, onFeedback: ((String) -> Unit)? = null) {
        if (_updateUiState.value.isChecking) return
        viewModelScope.launch {
            _updateUiState.update { it.copy(isChecking = true) }
            when (val outcome = AppUpdater.checkLatestRelease(BuildConfig.VERSION_NAME)) {
                is UpdateCheckOutcome.UpdateAvailable -> {
                    _updateUiState.update {
                        it.copy(
                            isChecking = false,
                            showDialog = true,
                            phase = AppUpdatePhase.Available(outcome.release),
                            lastStatusNote = "새 버전 v${outcome.release.versionName} 업데이트 가능"
                        )
                    }
                }
                is UpdateCheckOutcome.UpToDate -> {
                    _updateUiState.update {
                        it.copy(
                            isChecking = false,
                            lastStatusNote = "최신 버전입니다 (v${BuildConfig.VERSION_NAME})"
                        )
                    }
                    if (manual) onFeedback?.invoke("현재 최신 버전(v${BuildConfig.VERSION_NAME})을 사용 중입니다.")
                }
                is UpdateCheckOutcome.Failed -> {
                    _updateUiState.update {
                        it.copy(
                            isChecking = false,
                            lastStatusNote = outcome.message
                        )
                    }
                    if (manual) onFeedback?.invoke("업데이트 확인 실패: ${outcome.message}")
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        if (_updateUiState.value.phase is AppUpdatePhase.Downloading) return
        _updateUiState.update { it.copy(showDialog = false) }
    }

    fun startUpdateDownloadAndInstall() {
        val release = when (val phase = _updateUiState.value.phase) {
            is AppUpdatePhase.Available -> phase.release
            is AppUpdatePhase.Error -> phase.release
            else -> null
        } ?: return

        viewModelScope.launch {
            _updateUiState.update {
                it.copy(
                    phase = AppUpdatePhase.Downloading(
                        release = release,
                        progress = 0f,
                        downloadedBytes = 0L,
                        totalBytes = release.selectedAsset.sizeBytes
                    )
                )
            }
            val result = AppUpdater.downloadApk(
                context = appContext,
                asset = release.selectedAsset,
                onProgress = { progress, downloaded, total ->
                    _updateUiState.update { cur ->
                        cur.copy(
                            phase = AppUpdatePhase.Downloading(
                                release = release,
                                progress = progress,
                                downloadedBytes = downloaded,
                                totalBytes = total
                            )
                        )
                    }
                }
            )
            result.fold(
                onSuccess = { apkFile ->
                    downloadedApkFile = apkFile
                    val installOutcome = AppUpdater.launchApkInstaller(appContext, apkFile)
                    when (installOutcome) {
                        InstallLaunchOutcome.LAUNCHED_INSTALLER -> {
                            _updateUiState.update {
                                it.copy(
                                    phase = AppUpdatePhase.ReadyToInstall(
                                        release = release,
                                        needsPermission = false
                                    )
                                )
                            }
                        }
                        InstallLaunchOutcome.NEEDS_UNKNOWN_SOURCE_PERMISSION -> {
                            _updateUiState.update {
                                it.copy(
                                    phase = AppUpdatePhase.ReadyToInstall(
                                        release = release,
                                        needsPermission = true
                                    )
                                )
                            }
                        }
                        InstallLaunchOutcome.FAILED -> {
                            _updateUiState.update {
                                it.copy(
                                    phase = AppUpdatePhase.Error(
                                        release = release,
                                        message = "안드로이드 설치 화면을 열지 못했습니다."
                                    )
                                )
                            }
                        }
                    }
                },
                onFailure = { err ->
                    _updateUiState.update {
                        it.copy(
                            phase = AppUpdatePhase.Error(
                                release = release,
                                message = err.localizedMessage ?: "APK 다운로드 중 오류가 발생했습니다."
                            )
                        )
                    }
                }
            )
        }
    }

    fun retryApkInstall() {
        val file = downloadedApkFile ?: return
        val release = (_updateUiState.value.phase as? AppUpdatePhase.ReadyToInstall)?.release ?: return
        val outcome = AppUpdater.launchApkInstaller(appContext, file)
        _updateUiState.update {
            it.copy(
                phase = AppUpdatePhase.ReadyToInstall(
                    release = release,
                    needsPermission = outcome == InstallLaunchOutcome.NEEDS_UNKNOWN_SOURCE_PERMISSION
                )
            )
        }
    }

    fun openInstallUnknownSourcesSettings() {
        AppUpdater.openInstallPermissionSettings(appContext)
    }

    fun openAppInfoSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", appContext.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { appContext.startActivity(intent) }
    }
}
