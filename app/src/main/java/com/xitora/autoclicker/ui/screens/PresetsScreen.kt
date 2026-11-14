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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SmallTitle(text = "현재 설정 빠르게 저장")
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 18.dp,
                insideMargin = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onSaveCurrentSingle,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors()
                    ) {
                        Text("단일 설정 저장", style = MiuixTheme.textStyles.button)
                    }
                    Button(
                        onClick = onSaveCurrentMulti,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors()
                    ) {
                        Text("다중 설정 저장", style = MiuixTheme.textStyles.button)
                    }
                }
            }
        }

        item {
            SmallTitle(text = "저장된 프리셋 (${state.presets.size}개)")
        }

        if (state.presets.isEmpty()) {
            item {
                EmptyDataBanner(
                    icon = MiuixIcons.Favorites,
                    title = "저장된 프리셋이 없습니다",
                    subtitle = "자주 쓰는 간격, 지속 시간, 다중 타겟 배치를 프리셋으로 저장해 두면 원터치로 불러와 실행할 수 있습니다."
                )
            }
        } else {
            items(state.presets, key = { it.id }) { preset ->
                val isMulti = preset.mode == ClickMode.MULTI
                val accent = if (isMulti) Color(0xFF2B8A3E) else MiuixTheme.colorScheme.primary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MiuixGlassIcon(
                                icon = if (isMulti) MiuixIcons.Layers else MiuixIcons.Location,
                                contentDescription = null,
                                accentColor = accent
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = preset.name,
                                    style = MiuixTheme.textStyles.headline2.copy(fontWeight = FontWeight.SemiBold),
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                val modeText = if (isMulti) "다중 타겟 (${preset.targets.size}개)" else "단일 타겟"
                                val ivMs = ClickEngineCalculator.toMillis(preset.timing.intervalValue, preset.timing.intervalUnit)
                                Text(
                                    text = "$modeText · 간격 ${ivMs}ms · ${ClickEngineCalculator.formatRepeatSummary(preset.timing)}",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                            IconButton(onClick = { renamingPreset = preset }) {
                                Icon(
                                    imageVector = MiuixIcons.Edit,
                                    contentDescription = "이름 변경",
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                            IconButton(onClick = { onDeletePreset(preset.id) }) {
                                Icon(
                                    imageVector = MiuixIcons.Delete,
                                    contentDescription = "프리셋 삭제",
                                    tint = Color(0xFFD93025)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onApplyPreset(preset, false) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors()
                            ) {
                                Text("설정 불러오기", style = MiuixTheme.textStyles.button)
                            }
                            Button(
                                onClick = { onApplyPreset(preset, true) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) {
                                Text(
                                    text = "불러와 실행",
                                    style = MiuixTheme.textStyles.button,
                                    color = MiuixTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    renamingPreset?.let { targetPreset ->
        var text by remember(targetPreset.id) { mutableStateOf(targetPreset.name) }
        OverlayDialog(
            show = true,
            title = "프리셋 이름 변경",
            onDismissRequest = { renamingPreset = null }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = "새 프리셋 이름",
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { renamingPreset = null },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors()
                    ) {
                        Text("취소", style = MiuixTheme.textStyles.button)
                    }
                    Button(
                        onClick = {
                            onRenamePreset(targetPreset.id, text)
                            renamingPreset = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) {
                        Text("변경", style = MiuixTheme.textStyles.button, color = MiuixTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}
