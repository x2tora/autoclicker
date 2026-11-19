package com.xitora.autoclicker.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xitora.autoclicker.domain.ClickEngineCalculator
import com.xitora.autoclicker.model.ClickMode
import com.xitora.autoclicker.model.NavBarStyle
import com.xitora.autoclicker.model.OverlayRuntimeState
import com.xitora.autoclicker.ui.components.AccessibilityPermissionGuideDialog
import com.xitora.autoclicker.ui.components.AppUpdateDialog
import com.xitora.autoclicker.ui.components.ExpandableQuickAddMenuOverlay
import com.xitora.autoclicker.ui.components.FloatingLiquidGlassNavBar
import com.xitora.autoclicker.ui.components.QuickAddMenuScrim
import com.xitora.autoclicker.ui.components.QuickMenuAction
import com.xitora.autoclicker.ui.components.SavePresetDialog
import com.xitora.autoclicker.ui.components.StackedToastHost
import com.xitora.autoclicker.ui.components.StackedToastState
import com.xitora.autoclicker.ui.components.StandardMiuixNavBar
import com.xitora.autoclicker.ui.components.rememberStackedToastState
import com.xitora.autoclicker.ui.theme.AutoClickerMotion
import com.xitora.autoclicker.ui.theme.LocalAppBackdrop
import com.xitora.autoclicker.ui.theme.LocalLiquidGlassEnabled
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
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
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

    var selectedTab by remember { mutableIntStateOf(0) } // 0=홈, 1=프리셋, 2=테스트, 3=가이드
    var showQuickMenu by remember { mutableStateOf(false) }
    var showAccessibilityGuide by remember { mutableStateOf(false) }
    var presetSaveMode by remember { mutableStateOf<ClickMode?>(null) }
    var lastBackPressMs by remember { mutableLongStateOf(0L) }
    val homeListState = rememberLazyListState()
    val context = LocalContext.current

    val notify: (String) -> Unit = { msg -> toastState.show(msg) }
    val selectTab: (Int) -> Unit = { index ->
        showQuickMenu = false
        selectedTab = index.coerceIn(0, 3)
    }

    BackHandler {
        when {
            showQuickMenu -> showQuickMenu = false
            selectedTab != 0 -> selectedTab = 0
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressMs <= 2000L) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPressMs = now
                    notify("한 번 더 누르면 앱이 종료됩니다")
                }
            }
        }
    }

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

    val handleToggleClicking: () -> Unit = {
        val ok = viewModel.toggleOverlayClicking()
        if (!ok) {
            showAccessibilityGuide = true
        }
    }

    val liveTickerProgress by remember {
        derivedStateOf {
            if (selectedTab != 0) {
                0f
            } else {
                val heroItem = homeListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
                when {
                    heroItem == null -> if (homeListState.firstVisibleItemIndex > 0) 1f else 0f
                    heroItem.size <= 0 -> 0f
                    else -> {
                        val hiddenRatio = (-heroItem.offset.toFloat()) / heroItem.size.toFloat()
                        ((hiddenRatio - 0.35f) / 0.45f).coerceIn(0f, 1f)
                    }
                }
            }
        }
    }
    val tickerAlpha by animateFloatAsState(
        targetValue = liveTickerProgress,
        animationSpec = tween(160),
        label = "liveTickerAlpha"
    )

    val navBarStyle = state.settings.resolvedNavBarStyle()
    val liquidGlassEnabled = navBarStyle == NavBarStyle.LIQUID_GLASS
    val bottomBarExtraPadding = if (navBarStyle == NavBarStyle.LIQUID_GLASS) 104.dp else 90.dp
    val scrollBehavior = MiuixScrollBehavior(state = rememberTopAppBarState())
    val useShader = isRuntimeShaderSupported()
    val barOverlayColor = MiuixTheme.colorScheme.surface.copy(alpha = if (useShader) 0.48f else 0.90f)
    val tabTitles = listOf("오토 클리커", "프리셋 보관함", "터치 테스트", "권한 · 사용 가이드")
    val focusManager = LocalFocusManager.current

    CompositionLocalProvider(LocalLiquidGlassEnabled provides liquidGlassEnabled) {
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
            Box(
                modifier = topBlurMod.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth()) {
                    SmallTopAppBar(
                        title = tabTitles[selectedTab],
                        titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = (1f - tickerAlpha).coerceIn(0f, 1f)),
                        color = Color.Transparent,
                        scrollBehavior = scrollBehavior,
                        actions = {
                            IconButton(onClick = {
                                showQuickMenu = false
                                onOpenSettings()
                            }) {
                                Icon(
                                    imageVector = MiuixIcons.Regular.Settings,
                                    contentDescription = "설정 및 백업",
                                    tint = MiuixTheme.colorScheme.onSurface
                                )
                            }
                        }
                    )
                    if (tickerAlpha > 0.01f) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .statusBarsPadding()
                                .padding(start = 20.dp, end = 64.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            PinnedHeaderLiveTicker(
                                runtime = runtime,
                                onAction = {
                                    if (runtime.isOverlayVisible) {
                                        handleToggleClicking()
                                    } else {
                                        handleLaunchOrToggleOverlay(runtime.activeMode)
                                    }
                                },
                                modifier = Modifier.graphicsLayer {
                                    alpha = tickerAlpha
                                    translationY = (1f - tickerAlpha) * 14f
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            // Capture layer ONLY: surface background + scrolling pages
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(barBackdrop)
                    .background(surfaceBg),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 620.dp)
                        .fillMaxSize()
                        .padding(top = paddingValues.calculateTopPadding())
                        .imePadding()
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            AutoClickerMotion.tabTransition(
                                forward = targetState > initialState
                            ).using(SizeTransform(clip = false))
                        },
                        label = "mainTabTransition"
                    ) { tab ->
                        when (tab) {
                            0 -> HomeScreen(
                                listState = homeListState,
                                state = state,
                                runtime = runtime,
                                scrollBehavior = scrollBehavior,
                                bottomBarExtraPadding = bottomBarExtraPadding,
                                onLaunchMode = { mode -> handleLaunchOrToggleOverlay(mode) },
                                onToggleClicking = handleToggleClicking,
                                onOpenAccessibilitySettings = { showAccessibilityGuide = true },
                                onOpenAppInfoSettings = { viewModel.openAppInfoSettings() },
                                onUpdateSingleConfig = viewModel::updateSingleConfig,
                                onUpdateMultiConfig = { viewModel.updateMultiConfig(it, syncToTargets = true) },
                                onAddTarget = { viewModel.addMultiTarget() },
                                onUpdateTarget = viewModel::updateMultiTarget,
                                onDeleteTarget = { id -> viewModel.removeMultiTargetById(id) },
                                onUpdateScenarioConfig = viewModel::updateScenarioConfig,
                                onAddScenarioStep = { type -> viewModel.addScenarioStep(type) },
                                onUpdateScenarioStep = viewModel::updateScenarioStep,
                                onDeleteScenarioStep = { id -> viewModel.removeScenarioStepById(id) },
                                onMoveScenarioStep = viewModel::moveScenarioStep,
                                onDuplicateScenarioStep = viewModel::duplicateScenarioStep,
                                onToggleScenarioStepEnabled = viewModel::toggleScenarioStepEnabled,
                                onResetScenarioSteps = viewModel::clearScenarioSteps,
                                onSavePresetClick = { mode -> presetSaveMode = mode },
                                onLaunchPreset = { preset ->
                                    viewModel.applyPreset(preset)
                                    val ok = viewModel.startOverlay(preset.mode)
                                    if (!ok) {
                                        showAccessibilityGuide = true
                                    } else {
                                        notify("'${preset.name}' 프리셋으로 오버레이를 실행했습니다.")
                                    }
                                },
                                onNavigateTab = selectTab,
                                onNotify = notify
                            )
                            1 -> PresetsScreen(
                                state = state,
                                scrollBehavior = scrollBehavior,
                                bottomBarExtraPadding = bottomBarExtraPadding,
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
                                        selectedTab = 0
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
                                onSaveCurrentMulti = { presetSaveMode = ClickMode.MULTI },
                                onSaveCurrentScenario = { presetSaveMode = ClickMode.SCENARIO },
                                onExportPreset = viewModel::exportSinglePresetJson,
                                onImportPreset = viewModel::importSinglePresetJson,
                                onUpdatePresetBoundPackage = viewModel::updatePresetBoundPackage,
                                onNotify = notify
                            )
                            2 -> TouchTestScreen(
                                scrollBehavior = scrollBehavior,
                                bottomBarExtraPadding = bottomBarExtraPadding
                            )
                            else -> GuideScreen(
                                isAccessibilityEnabled = runtime.isAccessibilityEnabled,
                                scrollBehavior = scrollBehavior,
                                bottomBarExtraPadding = bottomBarExtraPadding,
                                onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                                onOpenAppInfoSettings = { viewModel.openAppInfoSettings() }
                            )
                        }
                    }
                }
            }

            // Scrim placed BELOW bottom nav bar so active bar & "+"/"×" button remain sharp.
            QuickAddMenuScrim(
                visible = showQuickMenu,
                onDismiss = { showQuickMenu = false },
                modifier = Modifier.fillMaxSize()
            )

            val isAccessibilityMissing = !runtime.isAccessibilityEnabled
            when (navBarStyle) {
                NavBarStyle.MIUIX -> StandardMiuixNavBar(
                    selectedTab = selectedTab,
                    presetCount = state.presets.size,
                    showAccessibilityDot = isAccessibilityMissing,
                    isCenterAddSelected = showQuickMenu,
                    onSelectTab = selectTab,
                    onToggleCenterAdd = { showQuickMenu = !showQuickMenu },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
                NavBarStyle.LIQUID_GLASS -> FloatingLiquidGlassNavBar(
                    backdrop = barBackdrop,
                    selectedTab = selectedTab,
                    presetCount = state.presets.size,
                    showAccessibilityDot = isAccessibilityMissing,
                    isCenterAddSelected = showQuickMenu,
                    onSelectTab = selectTab,
                    onToggleCenterAdd = { showQuickMenu = !showQuickMenu },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }

            val isSingleVisible = runtime.isOverlayVisible && runtime.activeMode == ClickMode.SINGLE
            val isMultiVisible = runtime.isOverlayVisible && runtime.activeMode == ClickMode.MULTI
            val isScenarioVisible = runtime.isOverlayVisible && runtime.activeMode == ClickMode.SCENARIO
            val quickMenuActions = remember(
                isSingleVisible,
                isMultiVisible,
                isScenarioVisible,
                runtime.isOverlayVisible,
                runtime.isClicking,
                runtime.activeMode
            ) {
                buildList {
                    add(
                        QuickMenuAction(
                            id = "launch_single",
                            title = if (isSingleVisible) "단일 타겟 오버레이 닫기" else "단일 타겟 오버레이 실행",
                            subtitle = if (isSingleVisible) "화면의 단일 타겟 패널을 종료합니다" else "1개 포인트 반복 터치 패널 띄우기",
                            icon = MiuixIcons.Location,
                            accentColor = Color(0xFF3482FF),
                            onClick = { handleLaunchOrToggleOverlay(ClickMode.SINGLE) }
                        )
                    )
                    add(
                        QuickMenuAction(
                            id = "launch_multi",
                            title = if (isMultiVisible) "다중 타겟 오버레이 닫기" else "다중 타겟 오버레이 실행",
                            subtitle = if (isMultiVisible) "화면의 다중 타겟 패널을 종료합니다" else "순서대로 여러 지점 자동 터치 실행",
                            icon = MiuixIcons.Layers,
                            accentColor = Color(0xFF2B8A3E),
                            onClick = { handleLaunchOrToggleOverlay(ClickMode.MULTI) }
                        )
                    )
                    add(
                        QuickMenuAction(
                            id = "launch_scenario",
                            title = if (isScenarioVisible) "시나리오 오버레이 닫기" else "시나리오 매크로 실행",
                            subtitle = if (isScenarioVisible) "조건 감지 매크로 패널을 종료합니다" else "이미지 감지 + 클릭/스크롤 루프",
                            icon = MiuixIcons.Favorites,
                            accentColor = Color(0xFF7C5CFF),
                            onClick = { handleLaunchOrToggleOverlay(ClickMode.SCENARIO) }
                        )
                    )
                    if (runtime.isOverlayVisible) {
                        add(
                            QuickMenuAction(
                                id = "toggle_clicking",
                                title = if (runtime.isClicking) "자동 터치 일시정지" else "자동 터치 바로 시작",
                                subtitle = "${runtime.activeMode.label} 모드 실시간 제어",
                                icon = if (runtime.isClicking) MiuixIcons.Pause else MiuixIcons.Play,
                                accentColor = if (runtime.isClicking) Color(0xFFF59E0B) else Color(0xFF10B981),
                                onClick = handleToggleClicking
                            )
                        )
                    } else {
                        add(
                            QuickMenuAction(
                                id = "save_preset",
                                title = "현재 설정 프리셋 저장",
                                subtitle = "${runtime.activeMode.label} 설정을 보관함에 기록",
                                icon = MiuixIcons.Favorites,
                                accentColor = Color(0xFFF59E0B),
                                onClick = { presetSaveMode = runtime.activeMode }
                            )
                        )
                    }
                }
            }

            ExpandableQuickAddMenuOverlay(
                visible = showQuickMenu,
                navBarStyle = navBarStyle,
                actions = quickMenuActions,
                onDismiss = { showQuickMenu = false }
            )

            // Keep floating overlays outside layerBackdrop and above quick-add menu in Z-order.
            StackedToastHost(
                state = toastState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = if (navBarStyle == NavBarStyle.LIQUID_GLASS) 92.dp else 86.dp
                    )
            )


            AccessibilityPermissionGuideDialog(
                show = showAccessibilityGuide,
                onDismiss = { showAccessibilityGuide = false },
                onOpenSettings = { viewModel.openAccessibilitySettings() },
                onOpenAppInfo = { viewModel.openAppInfoSettings() }
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
    val focusManager = LocalFocusManager.current
    val liquidGlassEnabled = state.settings.resolvedNavBarStyle() == NavBarStyle.LIQUID_GLASS

    CompositionLocalProvider(LocalLiquidGlassEnabled provides liquidGlassEnabled) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                SmallTopAppBar(
                    modifier = Modifier.widthIn(max = 620.dp),
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
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxSize()
                    .imePadding()
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
                onUpdateNavBarStyle = viewModel::updateNavBarStyle,
                onUpdateTargetSizeDp = viewModel::updateTargetSizeDp,
                onUpdateTargetOpacity = viewModel::updateTargetOpacity,
                onUpdateShowAntiBotZoneGuide = viewModel::updateShowAntiBotZoneGuide,
                onUpdateOverlayLocked = viewModel::updateOverlayLocked,
                onUpdateKeepScreenOn = viewModel::updateKeepScreenOn,
                onUpdateMaxRunMinutes = viewModel::updateMaxRunMinutes,
                onUpdateBatteryStopPercent = viewModel::updateBatteryStopPercent,
                onUpdateVolumeKeyControl = viewModel::updateVolumeKeyControlEnabled,
                onUpdateAutoSwitchPresetByApp = viewModel::updateAutoSwitchPresetByApp,
                onExportJson = viewModel::exportBackupJson,
                onImportJson = viewModel::importBackupJson,
                onResetAllData = viewModel::resetAllData,
                onCheckForUpdate = { viewModel.checkForUpdate(manual = true, onFeedback = notify) },
                onNotify = notify
            )
            }

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
}

@Composable
private fun PinnedHeaderLiveTicker(
    runtime: OverlayRuntimeState,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when {
        runtime.isClicking -> Color(0xFF10B981)
        runtime.isOverlayVisible -> MiuixTheme.colorScheme.primary
        runtime.isAccessibilityEnabled -> MiuixTheme.colorScheme.primary
        else -> Color(0xFFF59E0B)
    }
    val statusLabel = when {
        runtime.isClicking && runtime.activeMode == ClickMode.SCENARIO ->
            "시나리오 #${runtime.currentScenarioStepOrder} · ${ClickEngineCalculator.formatElapsedMmSs(runtime.elapsedMs)} · ${runtime.clickCount}회"
        runtime.isClicking ->
            "실행 중 · ${ClickEngineCalculator.formatElapsedMmSs(runtime.elapsedMs)} · ${runtime.clickCount}회"
        runtime.isOverlayVisible ->
            "${runtime.activeMode.label} 오버레이 표시 중"
        runtime.isAccessibilityEnabled ->
            "오토 클리커 · 준비됨 (${runtime.activeMode.label})"
        else ->
            "권한 필요 · 탭하여 확인"
    }

    Row(
        modifier = modifier
            .squircleClip(20.dp)
            .squircleBackground(
                color = accent.copy(alpha = 0.12f),
                cornerRadius = 20.dp
            )
            .squircleBorder(
                width = 1.dp,
                color = accent.copy(alpha = 0.28f),
                cornerRadius = 20.dp
            )
            .clickable(onClick = onAction)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Text(
            text = statusLabel,
            style = MiuixTheme.textStyles.footnote1.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            ),
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
