package com.xitora.autoclicker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.xitora.autoclicker.domain.ClickEngineCalculator
import com.xitora.autoclicker.model.*
import com.xitora.autoclicker.ui.components.ClickTimingEditorSection
import com.xitora.autoclicker.ui.components.ModeLaunchHeroCard
import com.xitora.autoclicker.ui.components.SquirclePillBadge
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import kotlin.math.roundToInt

@Composable
fun SingleModeScreen(
    state: AutoClickerDataState,
    runtime: OverlayRuntimeState,
    scrollBehavior: ScrollBehavior,
    onToggleOverlay: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onUpdateSingleConfig: (ClickTimingConfig) -> Unit,
    onSavePresetClick: () -> Unit
) {
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isOverlayActive = runtime.isOverlayVisible && runtime.activeMode == ClickMode.SINGLE
    val isOtherActive = runtime.isOverlayVisible && runtime.activeMode != ClickMode.SINGLE

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
                mode = ClickMode.SINGLE,
                isAccessibilityEnabled = runtime.isAccessibilityEnabled,
                isOverlayRunningForThisMode = isOverlayActive,
                isOtherModeOverlayActive = isOtherActive,
                runtime = runtime,
                subtitle = "한 지점에 집중 클릭 · 간격 ${
                    ClickEngineCalculator.formatTimingSummary(state.singleConfig.intervalValue, state.singleConfig.intervalUnit)
                } · 지속 ${
                    ClickEngineCalculator.formatTimingSummary(state.singleConfig.durationValue, state.singleConfig.durationUnit)
                }",
                onToggleOverlay = onToggleOverlay,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onSavePresetClick = onSavePresetClick
            )
        }

        item {
            SmallTitle(text = "타겟 위치 요약")
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 18.dp,
                insideMargin = PaddingValues(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "타겟 #1 좌표 (${state.singleTarget.xPx.roundToInt()}, ${state.singleTarget.yPx.roundToInt()})",
                            style = MiuixTheme.textStyles.body1
                        )
                        Text(
                            text = "오버레이를 띄운 뒤 과녁을 원하는 위치로 드래그해 배치하세요.",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                    SquirclePillBadge(
                        text = ClickEngineCalculator.formatRepeatSummary(state.singleConfig),
                        backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                        contentColor = MiuixTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        item {
            ClickTimingEditorSection(
                config = state.singleConfig,
                showRepeatSection = true,
                onConfigChange = onUpdateSingleConfig
            )
        }
    }
}
