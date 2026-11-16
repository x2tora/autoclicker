package com.xitora.autoclicker.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xitora.autoclicker.domain.ClickEngineCalculator
import com.xitora.autoclicker.model.AutoClickerDataState
import com.xitora.autoclicker.model.ClickMode
import com.xitora.autoclicker.model.ClickPreset
import com.xitora.autoclicker.ui.components.ConfirmActionDialog
import com.xitora.autoclicker.ui.components.EmptyDataBanner
import com.xitora.autoclicker.ui.components.MiuixGlassIcon
import com.xitora.autoclicker.ui.components.SquirclePillBadge
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PresetFilter(val label: String, val mode: ClickMode?) {
    ALL("전체", null),
    SINGLE("단일", ClickMode.SINGLE),
    MULTI("다중", ClickMode.MULTI),
    SCENARIO("시나리오", ClickMode.SCENARIO)
}

@Composable
fun PresetsScreen(
    state: AutoClickerDataState,
    scrollBehavior: ScrollBehavior,
    bottomBarExtraPadding: Dp,
    onApplyPreset: (ClickPreset, Boolean) -> Unit,
    onRenamePreset: (String, String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onSaveCurrentSingle: () -> Unit,
    onSaveCurrentMulti: () -> Unit,
    onSaveCurrentScenario: () -> Unit,
    onExportPreset: (String) -> String?,
    onImportPreset: (String) -> Result<ClickPreset>,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var selectedFilter by remember { mutableStateOf(PresetFilter.ALL) }
    var deletingPreset by remember { mutableStateOf<ClickPreset?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var renamingPreset by remember { mutableStateOf<ClickPreset?>(null) }
    var exportingPreset by remember { mutableStateOf<ClickPreset?>(null) }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

    val exportPresetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val jsonText = pendingExportJson
        pendingExportJson = null
        if (uri != null && jsonText != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(jsonText.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                onNotify("개별 프리셋 JSON 파일로 내보냈습니다.")
            }.onFailure {
                onNotify("내보내기 실패: ${it.message}")
            }
        }
    }

    val importPresetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                }.orEmpty()
                onImportPreset(raw).getOrThrow()
            }.onSuccess { imported ->
                showImportDialog = false
                onNotify("'${imported.name}' 프리셋을 가져왔습니다.")
            }.onFailure {
                onNotify("가져오기 실패: ${it.message ?: "올바른 프리셋 파일이 아닙니다."}")
            }
        }
    }

    val filteredPresets = remember(state.presets, selectedFilter, searchQuery) {
        state.presets.filter { preset ->
            val modeOk = selectedFilter.mode == null || preset.mode == selectedFilter.mode
            val queryOk = searchQuery.isBlank() || preset.name.contains(searchQuery.trim(), ignoreCase = true)
            modeOk && queryOk
        }
    }

    LazyColumn(
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
        item(key = "preset_quick_actions") {
            SmallTitle(text = "새 프리셋 저장 · 개별 가져오기")
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                insideMargin = PaddingValues(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PresetQuickSaveTile(
                            title = "단일 저장",
                            icon = MiuixIcons.Location,
                            accentColor = MiuixTheme.colorScheme.primary,
                            onClick = onSaveCurrentSingle,
                            modifier = Modifier.weight(1f)
                        )
                        PresetQuickSaveTile(
                            title = "다중 저장",
                            icon = MiuixIcons.Layers,
                            accentColor = Color(0xFF2B8A3E),
                            onClick = onSaveCurrentMulti,
                            modifier = Modifier.weight(1f)
                        )
                        PresetQuickSaveTile(
                            title = "시나리오 저장",
                            icon = MiuixIcons.Favorites,
                            accentColor = Color(0xFF7C5CFF),
                            onClick = onSaveCurrentScenario,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .squircleBackground(MiuixTheme.colorScheme.secondaryContainer, 13.dp)
                            .squircleClip(13.dp)
                            .clickable { showImportDialog = true }
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Import,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "개별 프리셋 가져오기 (JSON 파일 · 클립보드)",
                            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                            color = MiuixTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (state.presets.isNotEmpty()) {
            item(key = "preset_filter_search") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallTitle(text = "보관함 (${filteredPresets.size}/${state.presets.size})")
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(PresetFilter.entries) { filter ->
                            val count = if (filter.mode == null) {
                                state.presets.size
                            } else {
                                state.presets.count { it.mode == filter.mode }
                            }
                            val selected = selectedFilter == filter
                            SquirclePillBadge(
                                text = "${filter.label} $count",
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
                                onClick = { selectedFilter = filter }
                            )
                        }
                    }
                    if (state.presets.size >= 3) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { if (it.length <= 32) searchQuery = it },
                            label = "프리셋 이름 검색",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        } else {
            item(key = "preset_empty_title") {
                SmallTitle(text = "저장된 프리셋 (0개)")
            }
        }

        if (state.presets.isEmpty()) {
            item(key = "preset_empty") {
                EmptyDataBanner(
                    icon = MiuixIcons.Favorites,
                    title = "저장된 프리셋이 없습니다",
                    subtitle = "위에서 현재 단일·다중·시나리오 설정을 저장하거나, 공유받은 개별 프리셋 JSON 파일을 바로 가져올 수 있습니다."
                )
            }
        } else if (filteredPresets.isEmpty()) {
            item(key = "preset_filter_empty") {
                EmptyDataBanner(
                    icon = MiuixIcons.Favorites,
                    title = "조건에 맞는 프리셋이 없습니다",
                    subtitle = "선택한 모드 필터나 검색어를 초기화하면 저장된 프리셋 목록이 표시됩니다.",
                    actionLabel = "필터 초기화",
                    onAction = {
                        selectedFilter = PresetFilter.ALL
                        searchQuery = ""
                    }
                )
            }
        } else {
            items(filteredPresets, key = { it.id }) { preset ->
                val accent = when (preset.mode) {
                    ClickMode.SINGLE -> MiuixTheme.colorScheme.primary
                    ClickMode.MULTI -> Color(0xFF2B8A3E)
                    ClickMode.SCENARIO -> Color(0xFF7C5CFF)
                }
                val icon = when (preset.mode) {
                    ClickMode.SINGLE -> MiuixIcons.Location
                    ClickMode.MULTI -> MiuixIcons.Layers
                    ClickMode.SCENARIO -> MiuixIcons.Favorites
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MiuixGlassIcon(
                                icon = icon,
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
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val modeText = when (preset.mode) {
                                    ClickMode.SINGLE -> "단일 타겟"
                                    ClickMode.MULTI -> "다중 타겟 (${preset.targets.size}개)"
                                    ClickMode.SCENARIO -> "시나리오 (${preset.scenarioSteps.size}단계)"
                                }
                                val ivMs = ClickEngineCalculator.toMillis(preset.timing.intervalValue, preset.timing.intervalUnit)
                                val summaryText = if (preset.mode == ClickMode.SCENARIO) {
                                    "$modeText · ${ClickEngineCalculator.formatRepeatSummary(preset.timing)}"
                                } else {
                                    "$modeText · 간격 ${ivMs}ms · ${ClickEngineCalculator.formatRepeatSummary(preset.timing)}"
                                }
                                Text(
                                    text = summaryText,
                                    style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = "tnum"),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { exportingPreset = preset }) {
                                Icon(
                                    imageVector = MiuixIcons.Backup,
                                    contentDescription = "개별 내보내기",
                                    tint = accent
                                )
                            }
                            IconButton(onClick = { renamingPreset = preset }) {
                                Icon(
                                    imageVector = MiuixIcons.Edit,
                                    contentDescription = "이름 변경",
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                            IconButton(onClick = { deletingPreset = preset }) {
                                Icon(
                                    imageVector = MiuixIcons.Delete,
                                    contentDescription = "프리셋 삭제",
                                    tint = MiuixTheme.colorScheme.error
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

    exportingPreset?.let { preset ->
        OverlayDialog(
            show = true,
            title = "개별 프리셋 내보내기",
            summary = "'${preset.name}' 설정만 별도의 JSON으로 저장하거나 공유합니다.",
            onDismissRequest = { exportingPreset = null }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val json = onExportPreset(preset.id)
                        if (json != null) {
                            pendingExportJson = json
                            val safeName = preset.name
                                .replace(Regex("[^a-zA-Z0-9가-힣_-]"), "_")
                                .take(24)
                                .ifBlank { "preset" }
                            val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                            exportingPreset = null
                            exportPresetLauncher.launch("autoclicker-preset-$safeName-$today.json")
                        } else {
                            exportingPreset = null
                            onNotify("프리셋 데이터를 찾을 수 없습니다.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text(
                        text = "JSON 파일로 저장",
                        style = MiuixTheme.textStyles.button,
                        color = MiuixTheme.colorScheme.onPrimary
                    )
                }
                Button(
                    onClick = {
                        val json = onExportPreset(preset.id)
                        if (json != null) {
                            clipboard.setText(AnnotatedString(json))
                            onNotify("'${preset.name}' 프리셋 JSON이 클립보드에 복사되었습니다.")
                        } else {
                            onNotify("프리셋 데이터를 찾을 수 없습니다.")
                        }
                        exportingPreset = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("클립보드에 JSON 복사", style = MiuixTheme.textStyles.button)
                }
                TextButton(
                    text = "닫기",
                    onClick = { exportingPreset = null },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    var pastePresetJson by remember(showImportDialog) { mutableStateOf("") }
    OverlayDialog(
        show = showImportDialog,
        title = "개별 프리셋 가져오기",
        summary = "내보낸 개별 프리셋 JSON 파일을 불러오거나 텍스트를 붙여넣어 추가합니다.",
        onDismissRequest = { showImportDialog = false }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    importPresetLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors()
            ) {
                Text("JSON 파일 선택하기", style = MiuixTheme.textStyles.button)
            }
            TextField(
                value = pastePresetJson,
                onValueChange = { pastePresetJson = it },
                label = "개별 프리셋 JSON 텍스트 붙여넣기",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 180.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    text = "클립보드 붙여넣기",
                    onClick = {
                        pastePresetJson = clipboard.getText()?.text.orEmpty()
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "가져오기",
                    onClick = {
                        onImportPreset(pastePresetJson).onSuccess { imported ->
                            showImportDialog = false
                            onNotify("'${imported.name}' 프리셋을 가져왔습니다.")
                        }.onFailure {
                            onNotify("가져오기 실패: ${it.message ?: "올바른 프리셋 JSON이 아닙니다."}")
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
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
                        Text(
                            text = "저장",
                            style = MiuixTheme.textStyles.button,
                            color = MiuixTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }

    deletingPreset?.let { targetPreset ->
        ConfirmActionDialog(
            show = true,
            title = "프리셋 삭제",
            summary = "'${targetPreset.name}' 프리셋을 삭제합니다. 계속할까요?",
            confirmText = "삭제",
            isDestructive = true,
            onDismiss = { deletingPreset = null },
            onConfirm = { onDeletePreset(targetPreset.id) }
        )
    }
}

@Composable
private fun PresetQuickSaveTile(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .squircleBackground(accentColor.copy(alpha = 0.11f), 14.dp)
            .squircleClip(14.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MiuixGlassIcon(
            icon = icon,
            contentDescription = title,
            accentColor = accentColor,
            size = 34.dp,
            iconSize = 17.dp
        )
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
