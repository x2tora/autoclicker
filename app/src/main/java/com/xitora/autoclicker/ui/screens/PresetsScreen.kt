package com.xitora.autoclicker.ui.screens

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
import com.xitora.autoclicker.model.AutoClickerDataState
import com.xitora.autoclicker.model.ClickMode
import com.xitora.autoclicker.model.ClickPreset
import com.xitora.autoclicker.ui.components.EmptyDataBanner
import com.xitora.autoclicker.ui.components.MiuixGlassIcon
import com.xitora.autoclicker.ui.components.SquirclePillBadge
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun PresetsScreen(
    state: AutoClickerDataState,
    scrollBehavior: ScrollBehavior,
    onApplyPreset: (ClickPreset, Boolean) -> Unit,
    onRenamePreset: (String, String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onSaveCurrentSingle: () -> Unit,
    onSaveCurrentMulti: () -> Unit
) {
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var renamingPreset by remember { mutableStateOf<ClickPreset?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp + navBottomInset),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 18.dp,
                insideMargin = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "빠른 프리셋 저장",
                        style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "현재 설정된 단일 또는 다중 타겟 좌표·간격·반복 규칙을 저장해 두고 필요할 때 바로 불러오세요.",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            text = "단일 모드 저장",
                            onClick = onSaveCurrentSingle,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            text = "다중 모드 저장",
                            onClick = onSaveCurrentMulti,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (state.presets.isEmpty()) {
            item {
                EmptyDataBanner(
                    icon = MiuixIcons.Favorites,
                    title = "저장된 프리셋이 없습니다",
                    subtitle = "자주 쓰는 자동 클릭 조합을 저장하면 탭 한 번으로 오버레이를 띄울 수 있습니다."
                )
            }
        } else {
            item {
                SmallTitle(text = "저장된 프리셋 (${state.presets.size}개)")
            }
            items(state.presets, key = { it.id }) { preset ->
                val isSingle = preset.mode == ClickMode.SINGLE
                val accent = if (isSingle) MiuixTheme.colorScheme.primary else Color(0xFF10B981)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MiuixGlassIcon(
                                icon = if (isSingle) MiuixIcons.Location else MiuixIcons.Layers,
                                contentDescription = null,
                                accentColor = accent
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = preset.name,
                                        style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Bold),
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                    SquirclePillBadge(
                                        text = if (isSingle) "단일" else "다중 ${preset.targets.size}점",
                                        backgroundColor = accent.copy(alpha = 0.14f),
                                        contentColor = accent
                                    )
                                }
                                Text(
                                    text = "간격 ${
                                        ClickEngineCalculator.formatTimingSummary(preset.timing.intervalValue, preset.timing.intervalUnit)
                                    } · 지속 ${
                                        ClickEngineCalculator.formatTimingSummary(preset.timing.durationValue, preset.timing.durationUnit)
                                    } · ${ClickEngineCalculator.formatRepeatSummary(preset.timing)}",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                            IconButton(onClick = { renamingPreset = preset }) {
                                Icon(MiuixIcons.Edit, contentDescription = "이름 변경")
                            }
                            IconButton(onClick = { onDeletePreset(preset.id) }) {
                                Icon(MiuixIcons.Delete, contentDescription = "삭제", tint = MiuixTheme.colorScheme.error)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                text = "불러오기",
                                onClick = { onApplyPreset(preset, false) },
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                text = "불러와 오버레이 실행",
                                onClick = { onApplyPreset(preset, true) },
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                modifier = Modifier.weight(1.4f)
                            )
                        }
                    }
                }
            }
        }
    }

    renamingPreset?.let { targetPreset ->
        var editedName by remember(targetPreset.id) { mutableStateOf(targetPreset.name) }
        OverlayDialog(
            show = true,
            title = "프리셋 이름 변경",
            onDismissRequest = { renamingPreset = null }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = editedName,
                    onValueChange = { if (it.length <= 32) editedName = it },
                    label = "새 이름",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(text = "취소", onClick = { renamingPreset = null }, modifier = Modifier.weight(1f))
                    TextButton(
                        text = "변경",
                        onClick = {
                            onRenamePreset(targetPreset.id, editedName)
                            renamingPreset = null
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
