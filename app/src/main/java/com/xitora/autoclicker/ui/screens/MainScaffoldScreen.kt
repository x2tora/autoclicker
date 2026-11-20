package com.xitora.autoclicker.ui.screens

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xitora.autoclicker.model.ClickMode
import com.xitora.autoclicker.model.NavBarStyle
import com.xitora.autoclicker.model.ThemeColorStyle
import com.xitora.autoclicker.ui.components.AccessibilityPermissionGuideDialog
import com.xitora.autoclicker.ui.components.AppUpdateDialog
import com.xitora.autoclicker.ui.components.ExpandableQuickAddMenuOverlay
import com.xitora.autoclicker.ui.components.FloatingLiquidGlassNavBar
import com.xitora.autoclicker.ui.components.MiuixBottomEdgeBlur
import com.xitora.autoclicker.ui.components.MiuixFloatingScrollTopBar
import com.xitora.autoclicker.ui.components.QuickMenuAction
import com.xitora.autoclicker.ui.components.SavePresetDialog
import com.xitora.autoclicker.ui.components.StackedToastHost
import com.xitora.autoclicker.ui.components.StackedToastState
import com.xitora.autoclicker.ui.components.StandardMiuixNavBar
import com.xitora.autoclicker.ui.components.rememberStackedToastState
import com.xitora.autoclicker.ui.theme.AutoClickerMotion
import com.xitora.autoclicker.ui.theme.LocalAppBackdrop
import com.xitora.autoclicker.ui.theme.LocalLiquidGlassEnabled
import com.xitora.autoclicker.ui.theme.LocalWideDualLayoutEnabled
import com.xitora.autoclicker.viewmodel.AutoClickerViewModel
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
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
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack<AppRoute>(AppRoute.Main)
    val toastState = rememberStackedToastState()
    val surfaceBg = MiuixTheme.colorScheme.surface

    // Keep card/icon glass blur strictly separate from scrollable content barBackdrop to avoid cyclic RenderNode graphs.
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

        CompositionLocalProvider(
            LocalAppBackdrop provides ambientBackdrop,
            LocalLiquidGlassEnabled provides (state.settings.resolvedNavBarStyle() == NavBarStyle.LIQUID_GLASS),
            LocalWideDualLayoutEnabled provides state.settings.wideDualLayoutEnabled
        ) {
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
    var measuredTopBarHeightDp by remember { mutableStateOf(0.dp) }
    val homeListState = rememberLazyListState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current

    val notify: (String) -> Unit = { msg -> toastState.show(msg) }
    val selectTab: (Int) -> Unit = { index ->
        showQuickMenu = false
        selectedTab = index.coerceIn(0, 3)
    }

    BackHandler {
        when {
            showQuickMenu -> showQuickMenu = false
            selectedTab != 0 -> {
                selectedTab = 0
                lastBackPressMs = 0L
            }
            else -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastBackPressMs <= 2_000L) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPressMs = now
                    notify("뒤로가기를 한 번 더 누르면 앱이 종료됩니다.")
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

    val navBarStyle = state.settings.resolvedNavBarStyle()
    val liquidGlassEnabled = navBarStyle == NavBarStyle.LIQUID_GLASS
    val tabScrollBehaviors = List(4) { MiuixScrollBehavior(state = rememberTopAppBarState()) }
    val activeScrollBehavior = tabScrollBehaviors[selectedTab]
    val defaultTopBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 66.dp
    val resolvedTopBarInset = if (measuredTopBarHeightDp > 0.dp) measuredTopBarHeightDp else defaultTopBarHeight

    val quickMenuMorphProgress = remember { Animatable(0f) }
    var isQuickMenuMounted by remember { mutableStateOf(false) }
    LaunchedEffect(showQuickMenu) {
        if (showQuickMenu) {
            isQuickMenuMounted = true
            quickMenuMorphProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = 185f
                )
            )
        } else {
            quickMenuMorphProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.84f,
                    stiffness = 210f
                )
            )
            isQuickMenuMounted = false
        }
    }

    val tabTitles = listOf("오토 클리커", "프리셋 보관함", "터치 테스트", "권한 · 사용 가이드")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            // ① Capture layer ONLY: surface background + scrolling pages (no glass dock / top bar inside!)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(barBackdrop)
                    .background(surfaceBg)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        AutoClickerMotion.tabTransition(
                            forward = targetState >= initialState,
                            slideDivisor = 12
                        )
                    },
                    label = "mainTabTransition"
                ) { tab ->
                    when (tab) {
                        0 -> HomeScreen(
                            listState = homeListState,
                            state = state,
                            runtime = runtime,
                            scrollBehavior = tabScrollBehaviors[0],
                            topInset = resolvedTopBarInset,
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
                            scrollBehavior = tabScrollBehaviors[1],
                            topInset = resolvedTopBarInset,
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
                            scrollBehavior = tabScrollBehaviors[2],
                            topInset = resolvedTopBarInset
                        )
                        else -> GuideScreen(
                            isAccessibilityEnabled = runtime.isAccessibilityEnabled,
                            scrollBehavior = tabScrollBehaviors[3],
                            topInset = resolvedTopBarInset,
                            onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                            onOpenAppInfoSettings = { viewModel.openAppInfoSettings() }
                        )
                    }
                }
            }

            // ② Floating island scroll-responsive top bar
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    animationSpec = AutoClickerMotion.OffsetSpring,
                    initialOffsetY = { -it }
                ) + fadeIn(tween(320)),
                exit = slideOutVertically(
                    animationSpec = AutoClickerMotion.ExitOffsetSpring,
                    targetOffsetY = { -it }
                ) + fadeOut(tween(240)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged {
                        if (it.height > 0) {
                            val measured = with(density) { it.height.toDp() }
                            if (measured > 0.dp) measuredTopBarHeightDp = measured
                        }
                    }
            ) {
                MiuixFloatingScrollTopBar(
                    title = tabTitles[selectedTab],
                    scrollBehavior = activeScrollBehavior,
                    backdrop = barBackdrop,
                    maxContentWidth = if (state.settings.wideDualLayoutEnabled) Dp.Unspecified else 620.dp,
                    actions = {
                        IconButton(
                            onClick = {
                                showQuickMenu = false
                                onOpenSettings()
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Settings,
                                contentDescription = "더보기 및 설정",
                                tint = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            }

            // ③ Quick-add dim scrim positioned BELOW the navigation dock
            if (isQuickMenuMounted) {
                val scrimAlpha = 0.44f * quickMenuMorphProgress.value.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrimAlpha))
                        .pointerInput(showQuickMenu) {
                            if (showQuickMenu) {
                                detectTapGestures { showQuickMenu = false }
                            }
                        }
                )
            }

            // ④ Bottom edge blur + navigation bar (strictly OUTSIDE layerBackdrop capture box)
            val isAccessibilityMissing = !runtime.isAccessibilityEnabled
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                MiuixBottomEdgeBlur(
                    backdrop = barBackdrop,
                    extraHeight = 72.dp,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                if (liquidGlassEnabled) {
                    FloatingLiquidGlassNavBar(
                        backdrop = barBackdrop,
                        selectedTab = selectedTab,
                        presetCount = state.presets.size,
                        showAccessibilityDot = isAccessibilityMissing,
                        isCenterAddSelected = showQuickMenu,
                        onSelectTab = selectTab,
                        onToggleCenterAdd = { showQuickMenu = !showQuickMenu },
                        useLensTint = state.settings.resolvedColorStyle() != ThemeColorStyle.DEFAULT,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = 384.dp)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                } else {
                    StandardMiuixNavBar(
                        selectedTab = selectedTab,
                        presetCount = state.presets.size,
                        showAccessibilityDot = isAccessibilityMissing,
                        isCenterAddSelected = showQuickMenu,
                        onSelectTab = selectTab,
                        onToggleCenterAdd = { showQuickMenu = !showQuickMenu },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
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

            // ⑤ Morphing Quick-Add menu expanding from the center '+' button
            if (isQuickMenuMounted) {
                ExpandableQuickAddMenuOverlay(
                    morphProgress = quickMenuMorphProgress.value,
                    interactive = showQuickMenu,
                    isLiquidGlass = liquidGlassEnabled,
                    actions = quickMenuActions,
                    onDismiss = { showQuickMenu = false }
                )
            }

            // ⑥ Cascading StackedToastHost above quick-add & nav bar
            val toastBottomPadding by animateDpAsState(
                targetValue = if (liquidGlassEnabled) 104.dp else 94.dp,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow),
                label = "toast_bottom_offset"
            )
            StackedToastHost(
                state = toastState,
                backdrop = barBackdrop,
                isLiquidGlass = liquidGlassEnabled,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = toastBottomPadding)
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
    val settingsBackdrop = rememberLayerBackdrop()
    val surfaceBg = MiuixTheme.colorScheme.surface
    val notify: (String) -> Unit = { msg -> toastState.show(msg) }
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            MiuixFloatingScrollTopBar(
                title = "더보기 · 설정",
                scrollBehavior = scrollBehavior,
                backdrop = settingsBackdrop,
                maxContentWidth = 620.dp,
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
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
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(settingsBackdrop)
                    .background(surfaceBg)
            ) {
                MoreSettingsScreen(
                    state = state,
                    isAccessibilityEnabled = runtime.isAccessibilityEnabled,
                    scrollBehavior = scrollBehavior,
                    topInset = paddingValues.calculateTopPadding(),
                    updateUiState = updateUiState,
                    onOpenAccessibilitySettings = viewModel::openAccessibilitySettings,
                    onUpdateTheme = viewModel::updateTheme,
                    onUpdateThemeColorStyle = viewModel::updateThemeColorStyle,
                    onUpdateCustomKeyColor = viewModel::updateCustomKeyColor,
                    onUpdateNavBarStyle = viewModel::updateNavBarStyle,
                    onUpdateWideDualLayout = viewModel::updateWideDualLayout,
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

            MiuixBottomEdgeBlur(
                backdrop = settingsBackdrop,
                extraHeight = 36.dp,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            StackedToastHost(
                state = toastState,
                backdrop = settingsBackdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
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
