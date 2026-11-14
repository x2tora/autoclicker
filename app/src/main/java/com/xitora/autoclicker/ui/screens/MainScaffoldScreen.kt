package com.xitora.autoclicker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xitora.autoclicker.model.ClickMode
import com.xitora.autoclicker.ui.components.AccessibilityPermissionGuideDialog
import com.xitora.autoclicker.ui.components.AppUpdateDialog
import com.xitora.autoclicker.ui.components.FloatingLiquidGlassNavBar
import com.xitora.autoclicker.ui.components.SavePresetDialog
import com.xitora.autoclicker.ui.components.StackedToastHost
import com.xitora.autoclicker.ui.components.StackedToastState
import com.xitora.autoclicker.ui.components.rememberStackedToastState
import com.xitora.autoclicker.ui.theme.LocalAppBackdrop
import com.xitora.autoclicker.viewmodel.AutoClickerViewModel
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object Main : AppRoute

    @Serializable
    data object Settings : AppRoute
}

@Composable
fun MainScaffoldScreen(
    viewModel: AutoClickerViewModel
) {
    val backStack = rememberNavBackStack<AppRoute>(AppRoute.Main)
    val toastState = rememberStackedToastState()
    val surfaceBg = MiuixTheme.colorScheme.surface

    // Strictly separate card/icon glass blur from scrollable content barBackdrop to avoid cyclic RenderNode graphs.
    val ambientBackdrop = rememberLayerBackdrop {
        drawRect(surfaceBg)
        drawContent()
    }
    val barBackdrop = rememberLayerBackdrop {
        drawRect(surfaceBg)
        drawContent()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(ambientBackdrop)
                .background(surfaceBg)
        )

        CompositionLocalProvider(LocalAppBackdrop provides ambientBackdrop) {
            NavDisplay(
                backStack = backStack,
                onBack = {
                    if (backStack.size > 1) {
                        backStack.removeAt(backStack.lastIndex)
                    }
                },
                transition = NavTransitions.MiuixDefault
            ) {
                entry<AppRoute.Main> {
                    MainTabsHost(
                        viewModel = viewModel,
                        barBackdrop = barBackdrop,
                        toastState = toastState,
                        onOpenSettings = {
                            backStack.add(AppRoute.Settings)
                        }
                    )
                }

                entry<AppRoute.Settings> {
                    SettingsScreenHost(
                        viewModel = viewModel,
                        toastState = toastState,
                        onBack = { backStack.removeAt(backStack.lastIndex) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MainTabsHost(
    viewModel: AutoClickerViewModel,
    barBackdrop: LayerBackdrop,
    toastState: StackedToastState,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val runtime by viewModel.runtime.collectAsStateWithLifecycle()
    val updateUiState by viewModel.updateUiState.collectAsStateWithLifecycle()
    val surfaceBg = MiuixTheme.colorScheme.surface

    var selectedTab by remember { mutableIntStateOf(0) } // 0=단일, 1=다중, 2=프리셋, 3=테스트
    var showQuickActionMenu by remember { mutableStateOf(false) }
    var showAccessibilityGuide by remember { mutableStateOf(false) }
    var presetSaveMode by remember { mutableStateOf<ClickMode?>(null) }

    val notify: (String) -> Unit = { msg -> toastState.show(msg) }

    val handleLaunchOrToggleOverlay: (ClickMode) -> Unit = { mode ->
        val ok = viewModel.toggleOverlay(mode)
        if (!ok) {
            showAccessibilityGuide = true
        } else {
            val nowVisible = viewModel.runtime.value.isOverlayVisible
            if (nowVisible) {
                notify("${mode.label} 오버레이가 화면에 표시되었습니다.")
            } else {
                notify("오버레이를 종료했습니다.")
            }
        }
    }

    val scrollBehavior = MiuixScrollBehavior(state = rememberTopAppBarState())
    val useShader = isRuntimeShaderSupported()
    val barOverlayColor = MiuixTheme.colorScheme.surface.copy(alpha = if (useShader) 0.72f else 0.96f)
    val tabTitles = listOf("오토 클리커 · 단일", "오토 클리커 · 다중", "프리셋 보관함", "터치 테스트")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            val topBlurMod = if (useShader) {
                Modifier.progressiveTextureBlur(
                    backdrop = barBackdrop,
                    shape = RectangleShape,
                    blurRadius = 16f,
                    gradient = ProgressiveBlur.Top,
                    colors = BlurDefaults.blurColors(
                        blendColors = listOf(BlendColorEntry(barOverlayColor, BlurBlendMode.SrcOver))
                    )
                )
            } else {
                Modifier.background(barOverlayColor)
            }
            Box(modifier = topBlurMod) {
                SmallTopAppBar(
                    title = tabTitles[selectedTab],
                    color = Color.Transparent,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Settings,
                                contentDescription = "설정 및 백업",
                                tint = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // ① Capture layer ONLY: surface background + scrolling pages
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(barBackdrop)
                    .background(surfaceBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = paddingValues.calculateTopPadding())
                ) {
                    when (selectedTab) {
                        0 -> SingleModeScreen(
                            state = state,
                            runtime = runtime,
                            scrollBehavior = scrollBehavior,
                            onToggleOverlay = { handleLaunchOrToggleOverlay(ClickMode.SINGLE) },
                            onOpenAccessibilitySettings = { showAccessibilityGuide = true },
                            onUpdateSingleConfig = viewModel::updateSingleConfig,
                            onSavePresetClick = { presetSaveMode = ClickMode.SINGLE }
                        )
                        1 -> MultiModeScreen(
                            state = state,
                            runtime = runtime,
                            scrollBehavior = scrollBehavior,
                            onToggleOverlay = { handleLaunchOrToggleOverlay(ClickMode.MULTI) },
                            onOpenAccessibilitySettings = { showAccessibilityGuide = true },
                            onUpdateMultiConfig = { viewModel.updateMultiConfig(it, syncToTargets = true) },
                            onAddTarget = { viewModel.addMultiTarget() },
                            onUpdateTarget = viewModel::updateMultiTarget,
                            onDeleteTarget = { id -> viewModel.removeMultiTargetById(id) },
                            onSavePresetClick = { presetSaveMode = ClickMode.MULTI },
                            onNotify = notify
                        )
                        2 -> PresetsScreen(
                            state = state,
                            scrollBehavior = scrollBehavior,
                            onApplyPreset = { preset, launchImmediately ->
                                viewModel.applyPreset(preset)
                                if (launchImmediately) {
                                    val ok = viewModel.startOverlay(preset.mode)
                                    if (!ok) {
                                        showAccessibilityGuide = true
                                    } else {
                                        notify("'${preset.name}' 프리셋으로 오버레이를 실행했습니다.")
                                    }
                                } else {
                                    selectedTab = if (preset.mode == ClickMode.SINGLE) 0 else 1
                                    notify("'${preset.name}' 프리셋을 불러왔습니다.")
                                }
                            },
                            onRenamePreset = { id, newName ->
                                viewModel.renamePreset(id, newName)
                                notify("프리셋 이름을 변경했습니다.")
                            },
                            onDeletePreset = { id ->
                                viewModel.deletePreset(id)
                                notify("프리셋을 삭제했습니다.")
                            },
                            onSaveCurrentSingle = { presetSaveMode = ClickMode.SINGLE },
                            onSaveCurrentMulti = { presetSaveMode = ClickMode.MULTI }
                        )
                        else -> TouchTestScreen(scrollBehavior = scrollBehavior)
                    }
                }
            }

            // ② Floating overlay layer (strictly OUTSIDE layerBackdrop capture box)
            StackedToastHost(
                state = toastState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 92.dp)
            )

            FloatingLiquidGlassNavBar(
                backdrop = barBackdrop,
                selectedTab = selectedTab,
                isOverlayActive = runtime.isOverlayVisible,
                presetCount = state.presets.size,
                onSelectTab = { selectedTab = it },
                onCenterAddClick = { showQuickActionMenu = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )

            OverlayDialog(
                show = showQuickActionMenu,
                title = "빠른 오버레이 실행 · 프리셋",
                onDismissRequest = { showQuickActionMenu = false }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = if (runtime.isOverlayVisible && runtime.activeMode == ClickMode.SINGLE) {
                            "단일 타겟 오버레이 닫기"
                        } else {
                            "단일 타겟 오버레이 시작"
                        },
                        onClick = {
                            showQuickActionMenu = false
                            handleLaunchOrToggleOverlay(ClickMode.SINGLE)
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        text = if (runtime.isOverlayVisible && runtime.activeMode == ClickMode.MULTI) {
                            "다중 타겟 오버레이 닫기"
                        } else {
                            "다중 타겟 오버레이 시작 (${state.multiTargets.size}개 지점)"
                        },
                        onClick = {
                            showQuickActionMenu = false
                            handleLaunchOrToggleOverlay(ClickMode.MULTI)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        text = "현재 모드 설정을 프리셋으로 저장",
                        onClick = {
                            showQuickActionMenu = false
                            presetSaveMode = if (selectedTab == 1) ClickMode.MULTI else ClickMode.SINGLE
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (runtime.isOverlayVisible) {
                        TextButton(
                            text = "실행 중인 오버레이 모두 닫기",
                            onClick = {
                                showQuickActionMenu = false
                                viewModel.hideOverlay()
                                notify("모든 오버레이를 닫았습니다.")
                            },
                            colors = ButtonDefaults.textButtonColors(
                                color = MiuixTheme.colorScheme.error,
                                textColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            AccessibilityPermissionGuideDialog(
                show = showAccessibilityGuide,
                onDismiss = { showAccessibilityGuide = false },
                onOpenSettings = { viewModel.openAccessibilitySettings() }
            )

            presetSaveMode?.let { mode ->
                SavePresetDialog(
                    show = true,
                    mode = mode,
                    onDismiss = { presetSaveMode = null },
                    onConfirm = { name ->
                        val saved = viewModel.savePreset(name, mode)
                        notify("'${saved.name}' 프리셋을 저장했습니다.")
                    }
                )
            }

            AppUpdateDialog(
                uiState = updateUiState,
                onDismiss = viewModel::dismissUpdateDialog,
                onStartDownload = viewModel::startUpdateDownloadAndInstall,
                onRetryInstall = viewModel::retryApkInstall,
                onOpenPermissionSettings = viewModel::openInstallUnknownSourcesSettings
            )
        }
    }
}

@Composable
private fun SettingsScreenHost(
    viewModel: AutoClickerViewModel,
    toastState: StackedToastState,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val runtime by viewModel.runtime.collectAsStateWithLifecycle()
    val updateUiState by viewModel.updateUiState.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior(state = rememberTopAppBarState())
    val notify: (String) -> Unit = { msg -> toastState.show(msg) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "설정 · 테마 · 백업",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "뒤로가기"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            MoreSettingsScreen(
                state = state,
                isAccessibilityEnabled = runtime.isAccessibilityEnabled,
                scrollBehavior = scrollBehavior,
                updateUiState = updateUiState,
                onOpenAccessibilitySettings = viewModel::openAccessibilitySettings,
                onUpdateTheme = viewModel::updateTheme,
                onUpdateThemeColorStyle = viewModel::updateThemeColorStyle,
                onUpdateCustomKeyColor = viewModel::updateCustomKeyColor,
                onUpdateTargetSizeDp = viewModel::updateTargetSizeDp,
                onUpdateTargetOpacity = viewModel::updateTargetOpacity,
                onUpdateShowAntiBotZoneGuide = viewModel::updateShowAntiBotZoneGuide,
                onExportJson = viewModel::exportBackupJson,
                onImportJson = viewModel::importBackupJson,
                onResetAllData = viewModel::resetAllData,
                onCheckForUpdate = { viewModel.checkForUpdate(manual = true, onFeedback = notify) },
                onNotify = notify
            )

            StackedToastHost(
                state = toastState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            )

            AppUpdateDialog(
                uiState = updateUiState,
                onDismiss = viewModel::dismissUpdateDialog,
                onStartDownload = viewModel::startUpdateDownloadAndInstall,
                onRetryInstall = viewModel::retryApkInstall,
                onOpenPermissionSettings = viewModel::openInstallUnknownSourcesSettings
            )
        }
    }
}
