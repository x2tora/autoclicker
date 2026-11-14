package com.xitora.autoclicker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xitora.autoclicker.domain.ClickEngineCalculator
import com.xitora.autoclicker.model.*
import com.xitora.autoclicker.ui.components.ClickTimingEditorSection
import com.xitora.autoclicker.ui.components.ModeLaunchHeroCard
import com.xitora.autoclicker.ui.components.SquirclePillBadge
import com.xitora.autoclicker.ui.components.TargetPointEditDialog
import com.xitora.autoclicker.ui.components.TargetPreviewBadge
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import kotlin.math.roundToInt

@Composable
fun MultiModeScreen(
    state: AutoClickerDataState,
    runtime: OverlayRuntimeState,
    scrollBehavior: ScrollBehavior,
    onToggleOverlay: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onUpdateMultiConfig: (ClickTimingConfig) -> Unit,
    onAddTarget: () -> Unit,
    onUpdateTarget: (TargetPoint) -> Unit,
    onDeleteTarget: (String) -> Unit,
    onSavePresetClick: () -> Unit,
    onNotify: (String) -> Unit
) {
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isOverlayActive = runtime.isOverlayVisible && runtime.activeMode == ClickMode.MULTI
    val isOtherActive = runtime.isOverlayVisible && runtime.activeMode != ClickMode.MULTI

    var editingTarget by remember { mutableStateOf<TargetPoint?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp + navBottomInset),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ModeLaunchHeroCard(
                mode = ClickMode.MULTI,
                isAccessibilityEnabled = runtime.isAccessibilityEnabled,
                isOverlayRunningForThisMode = isOverlayActive,
                isOtherModeOverlayActive = isOtherActive,
                runtime = runtime,
                subtitle = "순차 타겟 ${state.multiTargets.size}개 · 기본 간격 ${
                    ClickEngineCalculator.formatTimingSummary(state.multiConfig.intervalValue, state.multiConfig.intervalUnit)
                } · ${ClickEngineCalculator.formatRepeatSummary(state.multiConfig)}",
                onToggleOverlay = onToggleOverlay,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onSavePresetClick = onSavePresetClick
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallTitle(text = "타겟 지점 목록 (${state.multiTargets.size}개)")
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

        items(state.multiTargets, key = { it.id }) { target ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .squircleClip(18.dp)
                    .clickable { editingTarget = target },
                cornerRadius = 18.dp,
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TargetPreviewBadge(
                        order = target.order,
                        antiBotEnabled = target.antiBotRandomEnabled,
                        radiusDp = target.randomRadiusDp,
                        modifier = Modifier.size(46.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "타겟 #${target.order} (${target.xPx.roundToInt()}, ${target.yPx.roundToInt()})",
                            style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold),
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        val antiBotText = if (target.antiBotRandomEnabled) {
                            " · 랜덤 ${target.randomRadiusDp}dp"
                        } else {
                            ""
                        }
                        Text(
                            text = "간격 ${
                                ClickEngineCalculator.formatTimingSummary(target.intervalValue, target.intervalUnit)
                            } · 지속 ${
                                ClickEngineCalculator.formatTimingSummary(target.durationValue, target.durationUnit)
                            }$antiBotText",
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
                    if (state.multiTargets.size > 1) {
                        IconButton(
                            onClick = {
                                onDeleteTarget(target.id)
                                onNotify("타겟 #${target.order} 지점을 삭제했습니다.")
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Delete,
                                contentDescription = "지점 삭제",
                                tint = MiuixTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    text = "타겟 지점 추가 (+)",
                    onClick = {
                        onAddTarget()
                        onNotify("타겟 지점이 추가되었습니다.")
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            ClickTimingEditorSection(
                config = state.multiConfig,
                showRepeatSection = true,
                antiBotSubtitle = "모든 다중 타겟에 랜덤 영역 클릭을 일괄 적용합니다 (개별 수정 가능)",
                onConfigChange = onUpdateMultiConfig
            )
        }
    }

    TargetPointEditDialog(
        target = editingTarget,
        canDelete = state.multiTargets.size > 1,
        onDismiss = { editingTarget = null },
        onSave = { updated ->
            onUpdateTarget(updated)
            onNotify("타겟 #${updated.order} 설정을 저장했습니다.")
        },
        onDelete = { id ->
            onDeleteTarget(id)
            onNotify("타겟 지점을 삭제했습니다.")
        }
    )
}
