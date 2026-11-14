package com.xitora.autoclicker.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.xitora.autoclicker.BuildConfig
import com.xitora.autoclicker.model.*
import com.xitora.autoclicker.ui.components.AppUpdateUiState
import com.xitora.autoclicker.ui.components.MiuixColorPickerDialog
import com.xitora.autoclicker.ui.components.MiuixGlassIcon
import com.xitora.autoclicker.ui.components.formatRgbHex
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MoreSettingsScreen(
    state: AutoClickerDataState,
    isAccessibilityEnabled: Boolean,
    scrollBehavior: ScrollBehavior,
    updateUiState: AppUpdateUiState,
    onOpenAccessibilitySettings: () -> Unit,
    onUpdateTheme: (ThemeOption) -> Unit,
    onUpdateThemeColorStyle: (ThemeColorStyle) -> Unit,
    onUpdateCustomKeyColor: (Long?) -> Unit,
    onUpdateNavBarStyle: (NavBarStyle) -> Unit,
    onUpdateTargetSizeDp: (Int) -> Unit,
    onUpdateTargetOpacity: (Int) -> Unit,
    onUpdateShowAntiBotZoneGuide: (Boolean) -> Unit,
    onExportJson: () -> String,
    onImportJson: (String) -> Result<Unit>,
    onResetAllData: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    var showAppColorDialog by remember { mutableStateOf(false) }
    var showOverlayStyleDialog by remember { mutableStateOf(false) }
    var showPasteRestoreDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(onExportJson().toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                onNotify("JSON 백업 파일을 저장했습니다.")
            }.onFailure {
                onNotify("백업 파일 저장에 실패했습니다.")
            }
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                } ?: error("파일을 읽을 수 없습니다.")
            }.fold(
                onSuccess = { jsonText ->
                    onImportJson(jsonText).onSuccess {
                        onNotify("백업 데이터를 복원했습니다.")
                    }.onFailure {
                        onNotify("올바른 오토 클리커 백업 파일이 아닙니다.")
                    }
                },
                onFailure = {
                    onNotify("백업 파일 읽기에 실패했습니다.")
                }
            )
        }
    }

    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val preferenceMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp + navBottomInset),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SmallTitle(text = "화면 · 테마 설정")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                val options = ThemeOption.entries
                OverlayDropdownPreference(
                    title = "테마 모드",
                    summary = state.settings.themeOption.label,
                    items = options.map { it.label },
                    selectedIndex = options.indexOf(state.settings.themeOption),
                    onSelectedIndexChange = { onUpdateTheme(options[it]) },
                    insideMargin = preferenceMargin,
                    startAction = {
                        MiuixGlassIcon(
                            icon = MiuixIcons.Theme,
                            contentDescription = null,
                            accentColor = MiuixTheme.colorScheme.primary
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                val colorStyles = ThemeColorStyle.entries
                val colorStyle = state.settings.resolvedColorStyle()
                OverlayDropdownPreference(
                    title = "테마 컬러 스타일",
                    summary = "${colorStyle.label} · ${colorStyle.description}",
                    items = colorStyles.map { it.label },
                    selectedIndex = colorStyles.indexOf(colorStyle),
                    onSelectedIndexChange = { index ->
                        val chosen = colorStyles[index]
                        onUpdateThemeColorStyle(chosen)
                        onNotify("테마 컬러가 '${chosen.label}'으로 적용되었습니다.")
                    },
                    insideMargin = preferenceMargin,
                    startAction = {
                        MiuixGlassIcon(
                            icon = MiuixIcons.Favorites,
                            contentDescription = null,
                            accentColor = Color(0xFF7C5CFF)
                        )
                    }
                )
                AnimatedVisibility(
                    visible = colorStyle == ThemeColorStyle.CUSTOM,
                    enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                    exit = shrinkVertically(tween(180)) + fadeOut(tween(180))
                ) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                        val previewHex = state.settings.customKeyColorHex ?: 0xFF3482FF
                        ArrowPreference(
                            title = "커스텀 포인트 색상 지정",
                            summary = "현재 ${formatRgbHex(previewHex)} 적용 중 · 탭하여 변경",
                            onClick = { showAppColorDialog = true },
                            insideMargin = preferenceMargin,
                            startAction = {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(previewHex))
                                )
                            }
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                val navStyles = NavBarStyle.entries
                val navStyle = state.settings.resolvedNavBarStyle()
                OverlayDropdownPreference(
                    title = "내비게이션 바 스타일",
                    summary = "${navStyle.label} · ${navStyle.description}",
                    items = navStyles.map { it.label },
                    selectedIndex = navStyles.indexOf(navStyle),
                    onSelectedIndexChange = { index ->
                        val chosen = navStyles[index]
                        onUpdateNavBarStyle(chosen)
                        onNotify("내비게이션 바가 '${chosen.label}' 스타일로 변경되었습니다.")
                    },
                    insideMargin = preferenceMargin,
                    startAction = {
                        MiuixGlassIcon(
                            icon = MiuixIcons.Tune,
                            contentDescription = null,
                            accentColor = Color(0xFF06B6D4)
                        )
                    }
                )
            }
        }

        item {
            SmallTitle(text = "접근성 서비스 · 오버레이 외형")
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "접근성 권한 상태",
                    summary = if (isAccessibilityEnabled) {
                        "활성화됨 · 오버레이 및 자동 터치 사용 가능"
                    } else {
                        "비활성화됨 · 탭하여 안드로이드 접근성 설정 열기"
                    },
                    onClick = onOpenAccessibilitySettings,
                    insideMargin = preferenceMargin,
                    startAction = {
                        MiuixGlassIcon(
                            icon = MiuixIcons.Settings,
                            contentDescription = null,
                            accentColor = if (isAccessibilityEnabled) Color(0xFF10B981) else Color(0xFFF59E0B)
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                ArrowPreference(
                    title = "타겟 과녁 크기 · 투명도",
                    summary = "크기 ${state.settings.targetSizeDp}dp · 불투명도 ${state.settings.targetOpacityPercent}%",
                    onClick = { showOverlayStyleDialog = true },
                    insideMargin = preferenceMargin,
                    startAction = {
                        MiuixGlassIcon(
                            icon = MiuixIcons.Location,
                            contentDescription = null,
                            accentColor = Color(0xFF3482FF)
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                SwitchPreference(
                    title = "봇 방지 랜덤 영역 점선 원 표시",
                    summary = "랜덤 클릭 기능이 켜져 있을 때 타겟 주위에 점선 가이드를 그립니다",
                    checked = state.settings.showAntiBotZoneGuide,
                    onCheckedChange = onUpdateShowAntiBotZoneGuide,
                    insideMargin = preferenceMargin,
                    startAction = {
                        MiuixGlassIcon(
                            icon = MiuixIcons.Stopwatch,
                            contentDescription = null,
                            accentColor = Color(0xFF06B6D4)
                        )
                    }
                )
            }
        }

        item {
            SmallTitle(text = "로컬 데이터 백업 및 복원")
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "JSON 파일로 내보내기 (백업)",
                    summary = "모든 단일/다중 타겟 설정과 프리셋을 JSON 파일로 저장",
                    onClick = {
                        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                        exportFileLauncher.launch("autoclicker-backup-$today.json")
                    },
                    insideMargin = preferenceMargin,
                    startAction = {
                        MiuixGlassIcon(
                            icon = MiuixIcons.Backup,
                            contentDescription = null,
                            accentColor = Color(0xFF3482FF)
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                ArrowPreference(
                    title = "JSON 파일에서 가져오기 (복원)",
                    summary = "저장해 둔 JSON 백업 파일을 불러와 설정과 프리셋을 복원합니다",
                    onClick = {
                        importFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    insideMargin = preferenceMargin,
                    startAction = {
                        MiuixGlassIcon(
                            icon = MiuixIcons.Import,
                            contentDescription = null,
                            accentColor = Color(0xFF10B981)
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                ArrowPreference(
                    title = "클립보드 백업 텍스트 복사 / 붙여넣기 복원",
                    summary = "파일 없이 JSON 텍스트로 바로 백업하거나 복원합니다",
                    onClick = { showPasteRestoreDialog = true },
                    insideMargin = preferenceMargin,
                    startAction = {
                        MiuixGlassIcon(
                            icon = MiuixIcons.Copy,
                            contentDescription = null,
                            accentColor = Color(0xFF06B6D4)
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                ArrowPreference(
                    title = "모든 데이터 초기화",
                    summary = "등록된 타겟 구성과 프리셋을 초기 상태로 되돌립니다 (테마 설정 유지)",
                    onClick = { showResetConfirm = true },
                    insideMargin = preferenceMargin,
                    startAction = {
                        MiuixGlassIcon(
                            icon = MiuixIcons.Reset,
                            contentDescription = null,
                            accentColor = MiuixTheme.colorScheme.error
                        )
                    }
                )
            }
        }

        item {
            SmallTitle(text = "앱 정보 및 업데이트")
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "앱 업데이트 확인",
                    summary = when {
                        updateUiState.isChecking -> "GitHub 릴리즈에서 최신 버전을 확인하는 중..."
                        !updateUiState.lastStatusNote.isNullOrBlank() -> "현재 v${BuildConfig.VERSION_NAME} · ${updateUiState.lastStatusNote}"
                        else -> "현재 v${BuildConfig.VERSION_NAME} · 앱 실행 시 자동 확인 (탭하여 지금 확인)"
                    },
                    onClick = onCheckForUpdate,
                    insideMargin = preferenceMargin,
                    startAction = {
                        MiuixGlassIcon(
                            icon = MiuixIcons.Store,
                            contentDescription = null,
                            accentColor = MiuixTheme.colorScheme.primary
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "오토 클리커 (AutoClicker) v${BuildConfig.VERSION_NAME}",
                        style = MiuixTheme.textStyles.body1
                    )
                    Text(
                        "단일·다중 타겟 플로팅 오버레이 및 봇 방지 랜덤 터치 지원",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }

    MiuixColorPickerDialog(
        show = showAppColorDialog,
        title = "커스텀 테마 포인트 색상 선택",
        initialColorHex = state.settings.customKeyColorHex ?: 0xFF3482FF,
        onDismiss = { showAppColorDialog = false },
        onConfirm = { chosenHex ->
            onUpdateCustomKeyColor(chosenHex)
            onNotify("커스텀 포인트 색상이 ${formatRgbHex(chosenHex)}로 적용되었습니다.")
        }
    )

    OverlayDialog(
        show = showOverlayStyleDialog,
        title = "타겟 과녁 크기 및 투명도",
        onDismissRequest = { showOverlayStyleDialog = false }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "과녁 크기: ${state.settings.targetSizeDp}dp",
                style = MiuixTheme.textStyles.body2
            )
            Slider(
                value = state.settings.targetSizeDp.toFloat(),
                onValueChange = { onUpdateTargetSizeDp(it.roundToInt()) },
                valueRange = 34f..72f,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "과녁 불투명도: ${state.settings.targetOpacityPercent}%",
                style = MiuixTheme.textStyles.body2
            )
            Slider(
                value = state.settings.targetOpacityPercent.toFloat(),
                onValueChange = { onUpdateTargetOpacity(it.roundToInt()) },
                valueRange = 30f..100f,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                text = "완료",
                onClick = { showOverlayStyleDialog = false },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    var pasteJson by remember(showPasteRestoreDialog) { mutableStateOf("") }
    OverlayDialog(
        show = showPasteRestoreDialog,
        title = "클립보드 JSON 백업 / 복원",
        onDismissRequest = { showPasteRestoreDialog = false }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(
                text = "현재 데이터 JSON 클립보드로 복사",
                onClick = {
                    clipboardManager.setText(AnnotatedString(onExportJson()))
                    onNotify("백업 JSON을 클립보드에 복사했습니다.")
                },
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider()
            TextField(
                value = pasteJson,
                onValueChange = { pasteJson = it },
                label = "복원할 JSON 텍스트 붙여넣기",
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(text = "취소", onClick = { showPasteRestoreDialog = false }, modifier = Modifier.weight(1f))
                TextButton(
                    text = "복원 실행",
                    onClick = {
                        onImportJson(pasteJson).onSuccess {
                            showPasteRestoreDialog = false
                            onNotify("클립보드 데이터로 복원을 완료했습니다.")
                        }.onFailure {
                            onNotify("유효한 JSON 형식이 아닙니다.")
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    OverlayDialog(
        show = showResetConfirm,
        title = "전체 데이터 초기화",
        summary = "저장된 모든 타겟 설정과 프리셋이 초기화됩니다. 계속할까요?",
        onDismissRequest = { showResetConfirm = false }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(text = "취소", onClick = { showResetConfirm = false }, modifier = Modifier.weight(1f))
            TextButton(
                text = "초기화",
                onClick = {
                    onResetAllData()
                    showResetConfirm = false
                    onNotify("모든 데이터가 초기화되었습니다.")
                },
                colors = ButtonDefaults.textButtonColors(
                    color = MiuixTheme.colorScheme.error,
                    textColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
