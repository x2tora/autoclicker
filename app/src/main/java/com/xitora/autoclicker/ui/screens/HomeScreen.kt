package com.xitora.autoclicker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xitora.autoclicker.domain.ClickEngineCalculator
import com.xitora.autoclicker.model.*
import com.xitora.autoclicker.ui.components.ClickTimingEditorSection
import com.xitora.autoclicker.ui.components.MiuixGlassIcon
import com.xitora.autoclicker.ui.components.SquirclePillBadge
import com.xitora.autoclicker.ui.components.TargetPointEditDialog
import com.xitora.autoclicker.ui.components.TargetPreviewBadge
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    state: AutoClickerDataState,
    runtime: OverlayRuntimeState,
    scrollBehavior: ScrollBehavior,
    onLaunchMode: (ClickMode) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onUpdateSingleConfig: (ClickTimingConfig) -> Unit,
    onUpdateMultiConfig: (ClickTimingConfig) -> Unit,
    onAddTarget: () -> Unit,
    onUpdateTarget: (TargetPoint) -> Unit,
    onDeleteTarget: (String) -> Unit,
    onSavePresetClick: (ClickMode) -> Unit,
    onNotify: (String) -> Unit
) {
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var settingsSheetMode by remember { mutableStateOf<ClickMode?>(null) }
    var editingTarget by remember { mutableStateOf<TargetPoint?>(null) }

    val isSingleActive = runtime.isOverlayVisible && runtime.activeMode == ClickMode.SINGLE
    val isMultiActive = runtime.isOverlayVisible && runtime.activeMode == ClickMode.MULTI

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp + navBottomInset),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!runtime.isAccessibilityEnabled) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "접근성 서비스 권한이 필요합니다",
                                    style = MiuixTheme.textStyles.headline2.copy(fontWeight = FontWeight.SemiBold),
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "오토 클리커가 화면 터치를 대신 수행하려면 접근성 권한을 켜주세요.",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                            SquirclePillBadge(
                                text = "권한 꺼짐",
                                backgroundColor = Color(0xFFD93025).copy(alpha = 0.14f),
                                contentColor = Color(0xFFD93025)
                            )
                        }
                        Button(
                            onClick = onOpenAccessibilitySettings,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColorsPrimary()
                        ) {
                            Text(
                                text = "접근성 설정 열기",
                                style = MiuixTheme.textStyles.button,
                                color = MiuixTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }

        if (runtime.isOverlayVisible) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val modeName = if (runtime.activeMode == ClickMode.SINGLE) "단일 타겟 모드" else "다중 타겟 모드"
                            Text(
                                text = "$modeName 오버레이 활성",
                                style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold),
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${if (runtime.isClicking) "자동 클릭 진행 중" else "대기 중"} · 누적 ${runtime.clickCount}회",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                        SquirclePillBadge(
                            text = "오버레이 닫기",
                            backgroundColor = Color(0xFFD93025).copy(alpha = 0.14f),
                            contentColor = Color(0xFFD93025),
                            onClick = { onLaunchMode(runtime.activeMode) }
                        )
                    }
                }
            }
        }

        item {
            SmallTitle(text = "클릭 모드 바로 실행")
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StackedModeLaunchCard(
                    title = "단일 타겟 모드",
                    subtitle = "간격 ${ClickEngineCalculator.toMillis(state.singleConfig.intervalValue, state.singleConfig.intervalUnit)}ms · 지속 ${
                        ClickEngineCalculator.toMillis(state.singleConfig.durationValue, state.singleConfig.durationUnit)
                    }ms · ${ClickEngineCalculator.formatRepeatSummary(state.singleConfig)}",
                    icon = MiuixIcons.Location,
                    accentColor = MiuixTheme.colorScheme.primary,
                    isActive = isSingleActive,
                    isAccessibilityEnabled = runtime.isAccessibilityEnabled,
                    onLaunchClick = { onLaunchMode(ClickMode.SINGLE) },
                    onSettingsClick = { settingsSheetMode = ClickMode.SINGLE }
                )

                StackedModeLaunchCard(
                    title = "다중 타겟 모드",
                    subtitle = "${state.multiTargets.size}개 지점 · 간격 ${
                        ClickEngineCalculator.toMillis(state.multiConfig.intervalValue, state.multiConfig.intervalUnit)
                    }ms · ${ClickEngineCalculator.formatRepeatSummary(state.multiConfig)}",
                    icon = MiuixIcons.Layers,
                    accentColor = Color(0xFF2B8A3E),
                    isActive = isMultiActive,
                    isAccessibilityEnabled = runtime.isAccessibilityEnabled,
                    onLaunchClick = { onLaunchMode(ClickMode.MULTI) },
                    onSettingsClick = { settingsSheetMode = ClickMode.MULTI }
                )
            }
        }
    }

    settingsSheetMode?.let { mode ->
        OverlayBottomSheet(
            show = true,
            title = if (mode == ClickMode.SINGLE) "단일 타겟 설정" else "다중 타겟 설정",
            onDismissRequest = { settingsSheetMode = null }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (mode == ClickMode.SINGLE) {
                    item {
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
                    item {
                        ClickTimingEditorSection(
                            config = state.singleConfig,
                            showRepeatSection = true,
                            reticleSizeDp = state.settings.targetSizeDp,
                            onConfigChange = onUpdateSingleConfig
                        )
                    }
                } else {
                    item {
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

                    item {
                        ClickTimingEditorSection(
                            config = state.multiConfig,
                            showRepeatSection = true,
                            reticleSizeDp = state.settings.targetSizeDp,
                            antiBotSubtitle = "모든 다중 타겟에 랜덤 영역 클릭을 일괄 적용합니다 (개별 수정 가능)",
                            onConfigChange = onUpdateMultiConfig
                        )
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
}

@Composable
private fun StackedModeLaunchCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isActive: Boolean,
    isAccessibilityEnabled: Boolean,
    onLaunchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .squircleClip(20.dp)
            .clickable(onClick = onLaunchClick),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiuixGlassIcon(
                icon = icon,
                contentDescription = title,
                accentColor = accentColor
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.headline2.copy(fontWeight = FontWeight.Bold),
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right-side Launch/Close action + Mode Settings button
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .squircleBackground(
                            color = if (isActive) {
                                Color(0xFFD93025).copy(alpha = 0.14f)
                            } else {
                                MiuixTheme.colorScheme.primary
                            },
                            cornerRadius = 12.dp
                        )
                        .squircleClip(12.dp)
                        .clickable(onClick = onLaunchClick)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isActive) MiuixIcons.Pause else MiuixIcons.Play,
                            contentDescription = null,
                            tint = if (isActive) Color(0xFFD93025) else MiuixTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = when {
                                isActive -> "닫기"
                                !isAccessibilityEnabled -> "권한"
                                else -> "실행"
                            },
                            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                            color = if (isActive) Color(0xFFD93025) else MiuixTheme.colorScheme.onPrimary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .squircleBackground(
                            color = MiuixTheme.colorScheme.secondaryContainer,
                            cornerRadius = 12.dp
                        )
                        .squircleClip(12.dp)
                        .clickable(onClick = onSettingsClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Settings,
                        contentDescription = "$title 설정",
                        tint = MiuixTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
