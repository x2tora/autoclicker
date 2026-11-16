package com.xitora.autoclicker.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xitora.autoclicker.domain.ClickEngineCalculator
import com.xitora.autoclicker.model.*
import com.xitora.autoclicker.ui.components.ClickTimingEditorSection
import com.xitora.autoclicker.ui.components.MiuixGlassIcon
import com.xitora.autoclicker.ui.components.ScenarioStepEditDialog
import com.xitora.autoclicker.ui.components.SquirclePillBadge
import com.xitora.autoclicker.ui.components.TargetPointEditDialog
import com.xitora.autoclicker.ui.components.TargetPreviewBadge
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    listState: LazyListState,
    state: AutoClickerDataState,
    runtime: OverlayRuntimeState,
    scrollBehavior: ScrollBehavior,
    bottomBarExtraPadding: Dp,
    onLaunchMode: (ClickMode) -> Unit,
    onToggleClicking: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppInfoSettings: () -> Unit,
    onUpdateSingleConfig: (ClickTimingConfig) -> Unit,
    onUpdateMultiConfig: (ClickTimingConfig) -> Unit,
    onAddTarget: () -> Unit,
    onUpdateTarget: (TargetPoint) -> Unit,
    onDeleteTarget: (String) -> Unit,
    onUpdateScenarioConfig: (ClickTimingConfig) -> Unit,
    onAddScenarioStep: (ScenarioStepType) -> Unit,
    onUpdateScenarioStep: (ScenarioStep) -> Unit,
    onDeleteScenarioStep: (String) -> Unit,
    onMoveScenarioStep: (String, Boolean) -> Unit,
    onResetScenarioSteps: () -> Unit,
    onSavePresetClick: (ClickMode) -> Unit,
    onLaunchPreset: (ClickPreset) -> Unit,
    onNavigateTab: (Int) -> Unit,
    onNotify: (String) -> Unit
) {
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var settingsSheetMode by remember { mutableStateOf<ClickMode?>(null) }
    var editingTarget by remember { mutableStateOf<TargetPoint?>(null) }
    var editingScenarioStep by remember { mutableStateOf<ScenarioStep?>(null) }

    val isSingleActive = runtime.isOverlayVisible && runtime.activeMode == ClickMode.SINGLE
    val isMultiActive = runtime.isOverlayVisible && runtime.activeMode == ClickMode.MULTI
    val isScenarioActive = runtime.isOverlayVisible && runtime.activeMode == ClickMode.SCENARIO

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = bottomBarExtraPadding + navBottomInset
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "hero_status") {
            FixedHomeStatusCard(
                state = state,
                runtime = runtime,
                onToggleClicking = onToggleClicking,
                onCloseOverlay = { onLaunchMode(runtime.activeMode) },
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenAppInfoSettings = onOpenAppInfoSettings,
                onSavePresetClick = { onSavePresetClick(runtime.activeMode) },
                onOpenTouchTest = { onNavigateTab(2) }
            )
        }

        item(key = "mode_launch_cards") {
            SmallTitle(text = "클릭 모드 바로 실행")
            val singleIntervalMs = ClickEngineCalculator.toMillis(
                state.singleConfig.intervalValue,
                state.singleConfig.intervalUnit
            )
            val singleDurationMs = ClickEngineCalculator.toMillis(
                state.singleConfig.durationValue,
                state.singleConfig.durationUnit
            )
            val multiIntervalMs = ClickEngineCalculator.toMillis(
                state.multiConfig.intervalValue,
                state.multiConfig.intervalUnit
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModeTileCard(
                    title = "단일 타겟",
                    summaryLine1 = "간격 ${singleIntervalMs}ms · 지속 ${singleDurationMs}ms",
                    summaryLine2 = ClickEngineCalculator.formatRepeatSummary(state.singleConfig),
                    icon = MiuixIcons.Location,
                    accentColor = MiuixTheme.colorScheme.primary,
                    isActive = isSingleActive,
                    isAccessibilityEnabled = runtime.isAccessibilityEnabled,
                    onLaunchClick = { onLaunchMode(ClickMode.SINGLE) },
                    onSettingsClick = { settingsSheetMode = ClickMode.SINGLE },
                    modifier = Modifier.weight(1f)
                )
                ModeTileCard(
                    title = "다중 타겟",
                    summaryLine1 = "${state.multiTargets.size}개 지점 · 간격 ${multiIntervalMs}ms",
                    summaryLine2 = ClickEngineCalculator.formatRepeatSummary(state.multiConfig),
                    icon = MiuixIcons.Layers,
                    accentColor = Color(0xFF2B8A3E),
                    isActive = isMultiActive,
                    isAccessibilityEnabled = runtime.isAccessibilityEnabled,
                    onLaunchClick = { onLaunchMode(ClickMode.MULTI) },
                    onSettingsClick = { settingsSheetMode = ClickMode.MULTI },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item(key = "scenario_mode_card") {
            ScenarioModeLaunchCard(
                steps = state.scenarioSteps,
                config = state.scenarioConfig,
                isActive = isScenarioActive,
                isClicking = runtime.isClicking && isScenarioActive,
                currentStepOrder = runtime.currentScenarioStepOrder,
                isAccessibilityEnabled = runtime.isAccessibilityEnabled,
                onLaunchClick = { onLaunchMode(ClickMode.SCENARIO) },
                onSettingsClick = { settingsSheetMode = ClickMode.SCENARIO }
            )
        }

        item(key = "recent_presets") {
            RecentPresetsHomeSection(
                presets = state.presets,
                activeMode = runtime.activeMode,
                onLaunchPreset = onLaunchPreset,
                onSaveCurrent = { onSavePresetClick(runtime.activeMode) },
                onOpenAllPresets = { onNavigateTab(1) }
            )
        }

        item(key = "quick_actions_grid") {
            SmallTitle(text = "빠른 도구")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionGridCard(
                        icon = MiuixIcons.Favorites,
                        accentColor = Color(0xFFF59E0B),
                        title = "프리셋 저장",
                        subtitle = if (isMultiActive) "다중 타겟 구성" else "단일 타겟 구성",
                        onClick = {
                            onSavePresetClick(if (isMultiActive) ClickMode.MULTI else ClickMode.SINGLE)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionGridCard(
                        icon = MiuixIcons.Playlist,
                        accentColor = MiuixTheme.colorScheme.primary,
                        title = "프리셋 보관함",
                        subtitle = "저장된 구성 ${state.presets.size}개",
                        onClick = { onNavigateTab(1) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionGridCard(
                        icon = MiuixIcons.Tune,
                        accentColor = Color(0xFF06B6D4),
                        title = "터치 테스트",
                        subtitle = "실제 CPS · 분산 측정",
                        onClick = { onNavigateTab(2) },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionGridCard(
                        icon = MiuixIcons.Help,
                        accentColor = Color(0xFF10B981),
                        title = "권한 · 가이드",
                        subtitle = "OneUI 제한 해제 안내",
                        onClick = { onNavigateTab(3) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    settingsSheetMode?.let { mode ->
        val sheetTitle = when (mode) {
            ClickMode.SINGLE -> "단일 타겟 설정"
            ClickMode.MULTI -> "다중 타겟 설정"
            ClickMode.SCENARIO -> "시나리오 · 조건부 매크로 편집"
        }
        OverlayBottomSheet(
            show = true,
            title = sheetTitle,
            onDismissRequest = { settingsSheetMode = null }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                when (mode) {
                    ClickMode.SINGLE -> {
                        item(key = "single_target_info") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                insideMargin = PaddingValues(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "타겟 #1 (${state.singleTarget.xPx.roundToInt()}, ${state.singleTarget.yPx.roundToInt()})",
                                            style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = "오버레이에서 과녁을 드래그해 위치를 조절합니다.",
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        )
                                    }
                                    SquirclePillBadge(
                                        text = "프리셋 저장",
                                        backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                                        contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                                        onClick = { onSavePresetClick(ClickMode.SINGLE) }
                                    )
                                }
                            }
                        }
                        item(key = "single_timing_editor") {
                            ClickTimingEditorSection(
                                config = state.singleConfig,
                                showRepeatSection = true,
                                reticleSizeDp = state.settings.targetSizeDp,
                                onConfigChange = onUpdateSingleConfig
                            )
                        }
                    }
                    ClickMode.MULTI -> {
                        item(key = "multi_targets_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SmallTitle(text = "타겟 지점 (${state.multiTargets.size}개)")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SquirclePillBadge(
                                        text = "프리셋 저장",
                                        backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                                        contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                                        onClick = { onSavePresetClick(ClickMode.MULTI) }
                                    )
                                    SquirclePillBadge(
                                        text = "+ 지점 추가",
                                        backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        contentColor = MiuixTheme.colorScheme.primary,
                                        onClick = {
                                            onAddTarget()
                                            onNotify("새 다중 타겟 지점이 추가되었습니다.")
                                        }
                                    )
                                }
                            }
                        }

                        items(state.multiTargets, key = { it.id }) { target ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                                    .squircleClip(16.dp)
                                    .clickable { editingTarget = target },
                                cornerRadius = 16.dp,
                                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TargetPreviewBadge(
                                        order = target.order,
                                        antiBotEnabled = target.antiBotRandomEnabled,
                                        radiusDp = target.randomRadiusDp,
                                        modifier = Modifier.size(42.dp)
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "타겟 #${target.order} (${target.xPx.roundToInt()}, ${target.yPx.roundToInt()})",
                                            style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        val ivMs = ClickEngineCalculator.toMillis(target.intervalValue, target.intervalUnit)
                                        val duMs = ClickEngineCalculator.toMillis(target.durationValue, target.durationUnit)
                                        val randTxt = if (target.antiBotRandomEnabled) " · 랜덤 ${target.randomRadiusDp}dp" else ""
                                        Text(
                                            text = "간격 ${ivMs}ms · 지속 ${duMs}ms$randTxt",
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        )
                                    }
                                    IconButton(onClick = { editingTarget = target }) {
                                        Icon(
                                            imageVector = MiuixIcons.Edit,
                                            contentDescription = "지점 수정",
                                            tint = MiuixTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (state.multiTargets.size <= 1) {
                                                onNotify("최소 1개의 타겟 지점이 필요합니다.")
                                            } else {
                                                onDeleteTarget(target.id)
                                                onNotify("타겟 #${target.order}이 제거되었습니다.")
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Delete,
                                            contentDescription = "지점 삭제",
                                            tint = Color(0xFFD93025)
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "multi_timing_editor") {
                            ClickTimingEditorSection(
                                config = state.multiConfig,
                                showRepeatSection = true,
                                reticleSizeDp = state.settings.targetSizeDp,
                                antiBotSubtitle = "모든 다중 타겟에 랜덤 영역 클릭을 일괄 적용합니다 (개별 수정 가능)",
                                onConfigChange = onUpdateMultiConfig
                            )
                        }
                    }
                    ClickMode.SCENARIO -> {
                        item(key = "scenario_header_actions") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                insideMargin = PaddingValues(14.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "총 ${state.scenarioSteps.size}단계 시나리오",
                                                style = MiuixTheme.textStyles.headline2.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "마지막 단계 완료 후 다시 1단계부터 반복됩니다.",
                                                style = MiuixTheme.textStyles.footnote2,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                            )
                                        }
                                        SquirclePillBadge(
                                            text = "프리셋 저장",
                                            backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                                            contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                                            onClick = { onSavePresetClick(ClickMode.SCENARIO) }
                                        )
                                    }

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        item {
                                            SquirclePillBadge(
                                                text = "+ 반복·감지",
                                                backgroundColor = Color(0xFF7C5CFF).copy(alpha = 0.14f),
                                                contentColor = Color(0xFF7C5CFF),
                                                onClick = {
                                                    onAddScenarioStep(ScenarioStepType.REPEAT_UNTIL_DETECT)
                                                    onNotify("반복·감지 단계를 추가했습니다.")
                                                }
                                            )
                                        }
                                        item {
                                            SquirclePillBadge(
                                                text = "+ 1회 클릭",
                                                backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                                                contentColor = MiuixTheme.colorScheme.primary,
                                                onClick = {
                                                    onAddScenarioStep(ScenarioStepType.SINGLE_TAP)
                                                    onNotify("1회 클릭 단계를 추가했습니다.")
                                                }
                                            )
                                        }
                                        item {
                                            SquirclePillBadge(
                                                text = "+ 스크롤",
                                                backgroundColor = Color(0xFF06B6D4).copy(alpha = 0.14f),
                                                contentColor = Color(0xFF06B6D4),
                                                onClick = {
                                                    onAddScenarioStep(ScenarioStepType.SCROLL)
                                                    onNotify("스크롤 단계를 추가했습니다.")
                                                }
                                            )
                                        }
                                        item {
                                            SquirclePillBadge(
                                                text = "8단계 기본 초기화",
                                                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                                                contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                                                onClick = {
                                                    onResetScenarioSteps()
                                                    onNotify("기본 8단계(반복→감지→3클릭→스크롤→3클릭)로 복원했습니다.")
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        items(state.scenarioSteps, key = { it.id }) { step ->
                            ScenarioStepRowCard(
                                step = step,
                                canMoveUp = step.order > 1,
                                canMoveDown = step.order < state.scenarioSteps.size,
                                canDelete = state.scenarioSteps.size > 1,
                                onEdit = { editingScenarioStep = step },
                                onMoveUp = { onMoveScenarioStep(step.id, true) },
                                onMoveDown = { onMoveScenarioStep(step.id, false) },
                                onDelete = {
                                    onDeleteScenarioStep(step.id)
                                    onNotify("단계 #${step.order}을 삭제했습니다.")
                                },
                                modifier = Modifier.animateItem()
                            )
                        }

                        item(key = "scenario_repeat_rule") {
                            ScenarioRepeatRuleCard(
                                config = state.scenarioConfig,
                                onConfigChange = onUpdateScenarioConfig
                            )
                        }
                    }
                }
            }
        }
    }

    TargetPointEditDialog(
        target = editingTarget,
        canDelete = state.multiTargets.size > 1,
        onDismiss = { editingTarget = null },
        onSave = { updated ->
            onUpdateTarget(updated)
            editingTarget = null
            onNotify("타겟 #${updated.order} 설정을 저장했습니다.")
        },
        onDelete = { id ->
            onDeleteTarget(id)
            editingTarget = null
            onNotify("타겟 지점을 삭제했습니다.")
        }
    )

    ScenarioStepEditDialog(
        step = editingScenarioStep,
        canDelete = state.scenarioSteps.size > 1,
        onDismiss = { editingScenarioStep = null },
        onSave = { updated ->
            onUpdateScenarioStep(updated)
            editingScenarioStep = null
            onNotify("단계 #${updated.order} 설정을 저장했습니다.")
        },
        onDelete = { id ->
            onDeleteScenarioStep(id)
            editingScenarioStep = null
            onNotify("시나리오 단계를 삭제했습니다.")
        },
        onFeedback = onNotify
    )
}

@Composable
private fun FixedHomeStatusCard(
    state: AutoClickerDataState,
    runtime: OverlayRuntimeState,
    onToggleClicking: () -> Unit,
    onCloseOverlay: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppInfoSettings: () -> Unit,
    onSavePresetClick: () -> Unit,
    onOpenTouchTest: () -> Unit
) {
    val active = runtime.isOverlayVisible
    val needsPermission = !runtime.isAccessibilityEnabled
    val statusDotColor by animateColorAsState(
        targetValue = when {
            active && runtime.isClicking -> Color(0xFF10B981)
            active -> MiuixTheme.colorScheme.primary
            needsPermission -> Color(0xFFF59E0B)
            else -> Color(0xFF10B981)
        },
        animationSpec = tween(220),
        label = "heroStatusDotColor"
    )
    val badgeBg by animateColorAsState(
        targetValue = statusDotColor.copy(alpha = 0.14f),
        animationSpec = tween(220),
        label = "heroBadgeBg"
    )

    val singleMs = ClickEngineCalculator.toMillis(state.singleConfig.intervalValue, state.singleConfig.intervalUnit)
    val telemetryText = when {
        active -> "${ClickEngineCalculator.formatElapsedMmSs(runtime.elapsedMs)} · ${runtime.clickCount}회"
        needsPermission -> "접근성 권한 필요"
        else -> "단일 ${singleMs}ms · 다중 ${state.multiTargets.size}개"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        insideMargin = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .squircleBackground(badgeBg, 10.dp)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .squircleBackground(statusDotColor, 4.dp)
                    )
                    Text(
                        text = when {
                            active && runtime.isClicking -> "${runtime.activeMode.label} 클릭 중"
                            active -> "${runtime.activeMode.label} 대기 중"
                            needsPermission -> "접근성 권한 꺼짐"
                            else -> "실행 준비 완료"
                        },
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                        color = statusDotColor,
                        maxLines = 1
                    )
                }

                Text(
                    text = telemetryText,
                    style = MiuixTheme.textStyles.body2.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when {
                    needsPermission -> {
                        FixedStatusActionPill(
                            text = "앱 정보 (제한 해제)",
                            backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                            contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                            onClick = onOpenAppInfoSettings,
                            modifier = Modifier.weight(1f)
                        )
                        FixedStatusActionPill(
                            text = "접근성 설정 열기",
                            backgroundColor = MiuixTheme.colorScheme.primary,
                            contentColor = MiuixTheme.colorScheme.onPrimary,
                            onClick = onOpenAccessibilitySettings,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    active -> {
                        FixedStatusActionPill(
                            text = if (runtime.isClicking) "일시정지" else "클릭 시작",
                            backgroundColor = if (runtime.isClicking) {
                                MiuixTheme.colorScheme.secondaryContainer
                            } else {
                                MiuixTheme.colorScheme.primary
                            },
                            contentColor = if (runtime.isClicking) {
                                MiuixTheme.colorScheme.onSecondaryContainer
                            } else {
                                MiuixTheme.colorScheme.onPrimary
                            },
                            onClick = onToggleClicking,
                            modifier = Modifier.weight(1f)
                        )
                        FixedStatusActionPill(
                            text = "오버레이 닫기",
                            backgroundColor = Color(0xFFD93025).copy(alpha = 0.14f),
                            contentColor = Color(0xFFD93025),
                            onClick = onCloseOverlay,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    else -> {
                        FixedStatusActionPill(
                            text = "현재 설정 저장",
                            backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                            contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                            onClick = onSavePresetClick,
                            modifier = Modifier.weight(1f)
                        )
                        FixedStatusActionPill(
                            text = "터치 테스트",
                            backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                            contentColor = MiuixTheme.colorScheme.primary,
                            onClick = onOpenTouchTest,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FixedStatusActionPill(
    text: String,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .squircleBackground(color = backgroundColor, cornerRadius = 13.dp)
            .squircleClip(13.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ModeTileCard(
    title: String,
    summaryLine1: String,
    summaryLine2: String,
    icon: ImageVector,
    accentColor: Color,
    isActive: Boolean,
    isAccessibilityEnabled: Boolean,
    onLaunchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actionBg by animateColorAsState(
        targetValue = if (isActive) Color(0xFFD93025).copy(alpha = 0.14f) else accentColor,
        animationSpec = tween(220),
        label = "modeTileBg"
    )
    val actionFg by animateColorAsState(
        targetValue = if (isActive) Color(0xFFD93025) else Color.White,
        animationSpec = tween(220),
        label = "modeTileFg"
    )
    val borderCol by animateColorAsState(
        targetValue = if (isActive) accentColor.copy(alpha = 0.55f) else Color.Transparent,
        animationSpec = tween(220),
        label = "modeTileBorder"
    )

    Card(
        modifier = modifier
            .height(176.dp)
            .squircleBorder(width = 1.5.dp, color = borderCol, cornerRadius = 22.dp)
            .squircleClip(22.dp)
            .clickable(onClick = onLaunchClick),
        cornerRadius = 22.dp,
        insideMargin = PaddingValues(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                MiuixGlassIcon(
                    icon = icon,
                    contentDescription = title,
                    accentColor = accentColor,
                    size = 40.dp,
                    iconSize = 20.dp
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .squircleBackground(
                            color = MiuixTheme.colorScheme.secondaryContainer,
                            cornerRadius = 11.dp
                        )
                        .squircleClip(11.dp)
                        .clickable(onClick = onSettingsClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Settings,
                        contentDescription = "$title 설정",
                        tint = MiuixTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.headline2.copy(fontWeight = FontWeight.Bold),
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$summaryLine1\n$summaryLine2",
                    style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = "tnum"),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .squircleBackground(color = actionBg, cornerRadius = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isActive) MiuixIcons.Pause else MiuixIcons.Play,
                        contentDescription = null,
                        tint = actionFg,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = when {
                            isActive -> "실행 중 · 닫기"
                            !isAccessibilityEnabled -> "권한 필요"
                            else -> "실행하기"
                        },
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                        color = actionFg,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentPresetsHomeSection(
    presets: List<ClickPreset>,
    activeMode: ClickMode,
    onLaunchPreset: (ClickPreset) -> Unit,
    onSaveCurrent: () -> Unit,
    onOpenAllPresets: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallTitle(text = "최근 프리셋 바로 실행")
            SquirclePillBadge(
                text = "전체 ${presets.size}개",
                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                onClick = onOpenAllPresets
            )
        }

        if (presets.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .squircleClip(18.dp)
                    .clickable(onClick = onSaveCurrent),
                cornerRadius = 18.dp,
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "저장된 프리셋이 없습니다",
                            style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold),
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = "탭하여 현재 ${activeMode.label} 설정을 저장하고 원탭으로 실행하세요",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    SquirclePillBadge(
                        text = "저장",
                        backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                        contentColor = MiuixTheme.colorScheme.primary
                    )
                }
            }
        } else {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(presets.take(6), key = { it.id }) { preset ->
                    val modeAccent = when (preset.mode) {
                        ClickMode.SINGLE -> MiuixTheme.colorScheme.primary
                        ClickMode.MULTI -> Color(0xFF2B8A3E)
                        ClickMode.SCENARIO -> Color(0xFF7C5CFF)
                    }
                    val intervalMs = ClickEngineCalculator.toMillis(
                        preset.timing.intervalValue,
                        preset.timing.intervalUnit
                    )
                    Card(
                        modifier = Modifier
                            .width(172.dp)
                            .fillMaxHeight()
                            .squircleClip(18.dp)
                            .clickable { onLaunchPreset(preset) },
                        cornerRadius = 18.dp,
                        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (preset.mode) {
                                        ClickMode.SINGLE -> "단일"
                                        ClickMode.MULTI -> "다중 ${preset.targets.size}점"
                                        ClickMode.SCENARIO -> "시나리오 ${preset.scenarioSteps.size}단계"
                                    },
                                    style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.Bold),
                                    color = modeAccent,
                                    maxLines = 1
                                )
                                Icon(
                                    imageVector = MiuixIcons.Play,
                                    contentDescription = "실행",
                                    tint = modeAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = preset.name,
                                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold),
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (preset.mode == ClickMode.SCENARIO) {
                                        "${preset.scenarioSteps.size}단계 · ${ClickEngineCalculator.formatRepeatSummary(preset.timing)}"
                                    } else {
                                        "${intervalMs}ms · ${ClickEngineCalculator.formatRepeatSummary(preset.timing)}"
                                    },
                                    style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = "tnum"),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenarioModeLaunchCard(
    steps: List<ScenarioStep>,
    config: ClickTimingConfig,
    isActive: Boolean,
    isClicking: Boolean,
    currentStepOrder: Int,
    isAccessibilityEnabled: Boolean,
    onLaunchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val accent = Color(0xFF7C5CFF)
    val actionBg by animateColorAsState(
        targetValue = if (isActive) Color(0xFFD93025).copy(alpha = 0.14f) else accent,
        animationSpec = tween(220),
        label = "scenarioLaunchBg"
    )
    val actionFg by animateColorAsState(
        targetValue = if (isActive) Color(0xFFD93025) else Color.White,
        animationSpec = tween(220),
        label = "scenarioLaunchFg"
    )
    val borderCol by animateColorAsState(
        targetValue = if (isActive) accent.copy(alpha = 0.55f) else Color.Transparent,
        animationSpec = tween(220),
        label = "scenarioBorder"
    )
    val firstDetect = steps.firstOrNull { it.type == ScenarioStepType.REPEAT_UNTIL_DETECT }
    val detectNote = when {
        firstDetect == null -> "${steps.size}단계 구성"
        firstDetect.targetText.isNotBlank() -> "텍스트 '${firstDetect.targetText}'"
        !firstDetect.targetImagePath.isNullOrBlank() -> "업로드 이미지"
        else -> "텍스트/이미지 미지정 (⚙ 설정 필요)"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .squircleBorder(width = 1.5.dp, color = borderCol, cornerRadius = 22.dp)
            .squircleClip(22.dp)
            .clickable(onClick = onLaunchClick),
        cornerRadius = 22.dp,
        insideMargin = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiuixGlassIcon(
                icon = MiuixIcons.Favorites,
                contentDescription = "시나리오 매크로",
                accentColor = accent,
                size = 42.dp,
                iconSize = 21.dp
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "시나리오 · 조건부 매크로",
                        style = MiuixTheme.textStyles.headline2.copy(fontWeight = FontWeight.Bold),
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${steps.size}단계 · $detectNote · ${ClickEngineCalculator.formatRepeatSummary(config)}",
                        style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = "tnum"),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .squircleBackground(color = actionBg, cornerRadius = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isActive) MiuixIcons.Pause else MiuixIcons.Play,
                            contentDescription = null,
                            tint = actionFg,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = when {
                                isClicking -> "실행 중 (#$currentStepOrder/${steps.size}) · 닫기"
                                isActive -> "오버레이 표시 중 · 닫기"
                                !isAccessibilityEnabled -> "권한 필요"
                                else -> "시나리오 오버레이 실행"
                            },
                            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                            color = actionFg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .align(Alignment.Top)
                    .squircleBackground(
                        color = MiuixTheme.colorScheme.secondaryContainer,
                        cornerRadius = 11.dp
                    )
                    .squircleClip(11.dp)
                    .clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.Settings,
                    contentDescription = "시나리오 단계 편집",
                    tint = MiuixTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun ScenarioStepRowCard(
    step: ScenarioStep,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeColor = when (step.type) {
        ScenarioStepType.REPEAT_UNTIL_DETECT -> Color(0xFF7C5CFF)
        ScenarioStepType.SINGLE_TAP -> MiuixTheme.colorScheme.primary
        ScenarioStepType.SCROLL -> Color(0xFF06B6D4)
    }
    val detailText = when (step.type) {
        ScenarioStepType.REPEAT_UNTIL_DETECT -> {
            val condLabel = buildList {
                if (step.conditionType != DetectionConditionType.IMAGE_ONLY && step.targetText.isNotBlank()) {
                    add("글자 '${step.targetText}'")
                }
                if (step.conditionType != DetectionConditionType.TEXT_ONLY && !step.targetImagePath.isNullOrBlank()) {
                    add("이미지 ${step.imageSimilarityPercent}%")
                }
            }.joinToString(" 또는 ").ifEmpty { "감지 조건 미설정" }
            "${step.repeatIntervalMs}ms 반복 · $condLabel · 후대기 ${step.postDelayMs}ms"
        }
        ScenarioStepType.SINGLE_TAP ->
            "1회 클릭 (${step.xPx.roundToInt()}, ${step.yPx.roundToInt()}) · 후대기 ${step.postDelayMs}ms"
        ScenarioStepType.SCROLL ->
            "${step.scrollDirection.arrowSymbol} ${step.scrollDistanceDp}dp 스크롤 (${step.xPx.roundToInt()}, ${step.yPx.roundToInt()}) · 후대기 ${step.postDelayMs}ms"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .squircleClip(16.dp)
            .clickable(onClick = onEdit),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .squircleBackground(badgeColor.copy(alpha = 0.14f), 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = step.badgeText(),
                    style = MiuixTheme.textStyles.body2.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = badgeColor,
                    maxLines = 1
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = step.title,
                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold),
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = detailText,
                    style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = "tnum"),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .squircleBackground(MiuixTheme.colorScheme.secondaryContainer, 8.dp)
                        .squircleClip(8.dp)
                        .clickable(enabled = canMoveUp, onClick = onMoveUp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↑",
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                        color = MiuixTheme.colorScheme.onSecondaryContainer.copy(alpha = if (canMoveUp) 1f else 0.3f)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .squircleBackground(MiuixTheme.colorScheme.secondaryContainer, 8.dp)
                        .squircleClip(8.dp)
                        .clickable(enabled = canMoveDown, onClick = onMoveDown),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↓",
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                        color = MiuixTheme.colorScheme.onSecondaryContainer.copy(alpha = if (canMoveDown) 1f else 0.3f)
                    )
                }
            }

            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = "단계 삭제",
                        tint = Color(0xFFD93025)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScenarioRepeatRuleCard(
    config: ClickTimingConfig,
    onConfigChange: (ClickTimingConfig) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "시나리오 전체 반복 조건",
                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(RepeatMode.entries) { mode ->
                    val selected = config.repeatMode == mode
                    SquirclePillBadge(
                        text = mode.label,
                        backgroundColor = if (selected) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.secondaryContainer
                        },
                        contentColor = if (selected) {
                            MiuixTheme.colorScheme.onPrimary
                        } else {
                            MiuixTheme.colorScheme.onSecondaryContainer
                        },
                        onClick = { onConfigChange(config.copy(repeatMode = mode)) }
                    )
                }
            }
            if (config.repeatMode == RepeatMode.COUNT) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(5, 10, 30, 100).forEach { cnt ->
                        val selected = config.repeatCount == cnt
                        SquirclePillBadge(
                            text = "${cnt}사이클",
                            backgroundColor = if (selected) {
                                Color(0xFF7C5CFF)
                            } else {
                                MiuixTheme.colorScheme.secondaryContainer
                            },
                            contentColor = if (selected) Color.White else MiuixTheme.colorScheme.onSecondaryContainer,
                            onClick = { onConfigChange(config.copy(repeatCount = cnt)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else if (config.repeatMode == RepeatMode.DURATION) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(1L to "1분", 5L to "5분", 15L to "15분", 30L to "30분").forEach { (mins, label) ->
                        val curMs = ClickEngineCalculator.toMillis(config.repeatDurationValue, config.repeatDurationUnit)
                        val selected = curMs == mins * 60_000L
                        SquirclePillBadge(
                            text = label,
                            backgroundColor = if (selected) {
                                Color(0xFF7C5CFF)
                            } else {
                                MiuixTheme.colorScheme.secondaryContainer
                            },
                            contentColor = if (selected) Color.White else MiuixTheme.colorScheme.onSecondaryContainer,
                            onClick = {
                                onConfigChange(
                                    config.copy(
                                        repeatDurationValue = mins,
                                        repeatDurationUnit = TimeUnitOption.MIN
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionGridCard(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .squircleClip(18.dp)
            .clickable(onClick = onClick),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MiuixGlassIcon(
                icon = icon,
                contentDescription = title,
                accentColor = accentColor,
                size = 36.dp,
                iconSize = 18.dp
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold),
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


