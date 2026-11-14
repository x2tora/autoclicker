package com.xitora.autoclicker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xitora.autoclicker.domain.ClickEngineCalculator
import com.xitora.autoclicker.model.*
import com.xitora.autoclicker.ui.components.ClickTimingEditorSection
import com.xitora.autoclicker.ui.components.EmptyDataBanner
import com.xitora.autoclicker.ui.components.MiuixGlassIcon
import com.xitora.autoclicker.ui.components.SquirclePillBadge
import com.xitora.autoclicker.ui.components.TargetPointEditDialog
import com.xitora.autoclicker.ui.components.TargetPreviewBadge
import com.xitora.autoclicker.ui.theme.LocalIsDarkTheme
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.util.Locale
import kotlin.math.roundToInt

private data class StartTapMarker(
    val id: Long,
    val offset: Offset,
    val timestampMs: Long
)

@Composable
fun StartScreen(
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
    onApplyPreset: (ClickPreset, Boolean) -> Unit,
    onRenamePreset: (String, String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onNotify: (String) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var selectedSection by remember { mutableIntStateOf(0) } // 0=단일, 1=다중, 2=프리셋, 3=테스트
    var editingTarget by remember { mutableStateOf<TargetPoint?>(null) }
    var renamingPreset by remember { mutableStateOf<ClickPreset?>(null) }

    // Touch test state
    var totalTaps by remember { mutableIntStateOf(0) }
    var lastIntervalMs by remember { mutableStateOf<Long?>(null) }
    val tapMarkers = remember { mutableStateListOf<StartTapMarker>() }
    val tapTimestamps = remember { mutableStateListOf<Long>() }

    val cps = remember(tapTimestamps.size, totalTaps) {
        val now = System.currentTimeMillis()
        val recent = tapTimestamps.count { now - it <= 1000L }
        recent.toFloat()
    }

    val isSingleActive = runtime.isOverlayVisible && runtime.activeMode == ClickMode.SINGLE
    val isMultiActive = runtime.isOverlayVisible && runtime.activeMode == ClickMode.MULTI

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp + navBottomInset),
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

        // Quick-launch dual cards: Single & Multi directly from the start screen
        item {
            SmallTitle(text = "바로 실행")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickLaunchModeCard(
                    title = "단일 타겟",
                    subtitle = "간격 ${
                        ClickEngineCalculator.formatTimingSummary(
                            state.singleConfig.intervalValue,
                            state.singleConfig.intervalUnit
                        )
                    }\n${ClickEngineCalculator.formatRepeatSummary(state.singleConfig)}",
                    icon = MiuixIcons.Location,
                    accentColor = MiuixTheme.colorScheme.primary,
                    isActive = isSingleActive,
                    isAccessibilityEnabled = runtime.isAccessibilityEnabled,
                    statusBadge = if (isSingleActive) "실행 중" else "1개 지점",
                    onLaunchClick = { onLaunchMode(ClickMode.SINGLE) },
                    onEditClick = { selectedSection = 0 },
                    modifier = Modifier.weight(1f)
                )

                QuickLaunchModeCard(
                    title = "다중 타겟",
                    subtitle = "간격 ${
                        ClickEngineCalculator.formatTimingSummary(
                            state.multiConfig.intervalValue,
                            state.multiConfig.intervalUnit
                        )
                    }\n${ClickEngineCalculator.formatRepeatSummary(state.multiConfig)}",
                    icon = MiuixIcons.Layers,
                    accentColor = Color(0xFF2B8A3E),
                    isActive = isMultiActive,
                    isAccessibilityEnabled = runtime.isAccessibilityEnabled,
                    statusBadge = if (isMultiActive) "실행 중" else "${state.multiTargets.size}개 지점",
                    onLaunchClick = { onLaunchMode(ClickMode.MULTI) },
                    onEditClick = { selectedSection = 1 },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (runtime.isOverlayVisible) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
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

        // Inline section switcher (no bottom navigation bar)
        item {
            TabRowWithContour(
                tabs = listOf("단일 설정", "다중 설정", "프리셋", "터치 테스트"),
                selectedTabIndex = selectedSection,
                onTabSelected = { selectedSection = it },
                modifier = Modifier.fillMaxWidth()
            )
        }

        when (selectedSection) {
            0 -> {
                item {
                    SmallTitle(text = "단일 타겟 위치 및 저장")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        insideMargin = PaddingValues(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "타겟 #1 좌표 (${state.singleTarget.xPx.roundToInt()}, ${state.singleTarget.yPx.roundToInt()})",
                                        style = MiuixTheme.textStyles.body1
                                    )
                                    Text(
                                        text = "바로 실행을 누르면 화면에 과녁이 표시되며 드래그로 위치를 옮길 수 있습니다.",
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
                }

                item {
                    ClickTimingEditorSection(
                        config = state.singleConfig,
                        showRepeatSection = true,
                        onConfigChange = onUpdateSingleConfig
                    )
                }
            }

            1 -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallTitle(text = "타겟 지점 목록 (${state.multiTargets.size}개)")
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
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .squircleClip(18.dp)
                            .clickable {
                                onAddTarget()
                                onNotify("새 다중 타겟 지점이 추가되었습니다.")
                            },
                        cornerRadius = 18.dp,
                        insideMargin = PaddingValues(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Add,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "새 클릭 지점 추가",
                                style = MiuixTheme.textStyles.button,
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                }

                item {
                    ClickTimingEditorSection(
                        config = state.multiConfig,
                        showRepeatSection = true,
                        onConfigChange = onUpdateMultiConfig
                    )
                }
            }

            2 -> {
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
                                onClick = { onSavePresetClick(ClickMode.SINGLE) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors()
                            ) {
                                Text("단일 설정 저장", style = MiuixTheme.textStyles.button)
                            }
                            Button(
                                onClick = { onSavePresetClick(ClickMode.MULTI) },
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
                                        Text(
                                            text = "$modeText · 간격 ${
                                                ClickEngineCalculator.formatTimingSummary(
                                                    preset.timing.intervalValue,
                                                    preset.timing.intervalUnit
                                                )
                                            } · ${ClickEngineCalculator.formatRepeatSummary(preset.timing)}",
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

            else -> {
                item {
                    SmallTitle(text = "실시간 터치 반응 측정")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        insideMargin = PaddingValues(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "총 $totalTaps 회 터치",
                                    style = MiuixTheme.textStyles.headline1.copy(fontWeight = FontWeight.Bold),
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "직전 간격: ${lastIntervalMs?.let { "${it} ms" } ?: "측정 대기"} · 속도 ${
                                        String.format(Locale.US, "%.1f", cps)
                                    } CPS",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                            SquirclePillBadge(
                                text = "초기화",
                                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                                contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                                onClick = {
                                    totalTaps = 0
                                    lastIntervalMs = null
                                    tapMarkers.clear()
                                    tapTimestamps.clear()
                                }
                            )
                        }
                    }
                }

                item {
                    val primaryColor = MiuixTheme.colorScheme.primary
                    val boardBg = if (isDark) Color(0xFF1A1E28) else Color(0xFFF1F4FA)
                    val borderCol = if (isDark) Color(0xFF2D3548) else Color(0xFFD5DCE8)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .squircleBackground(boardBg, 22.dp)
                            .squircleBorder(1.dp, borderCol, 22.dp)
                            .squircleClip(22.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val now = System.currentTimeMillis()
                                    val prev = tapTimestamps.lastOrNull()
                                    if (prev != null) {
                                        lastIntervalMs = now - prev
                                    }
                                    tapTimestamps.add(now)
                                    if (tapTimestamps.size > 40) {
                                        tapTimestamps.removeAt(0)
                                    }
                                    totalTaps += 1
                                    tapMarkers.add(StartTapMarker(id = now + totalTaps, offset = offset, timestampMs = now))
                                    if (tapMarkers.size > 24) {
                                        tapMarkers.removeAt(0)
                                    }
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val totalMarkers = tapMarkers.size
                            tapMarkers.forEachIndexed { index, marker ->
                                val alpha = ((index + 1).toFloat() / totalMarkers.coerceAtLeast(1)).coerceIn(0.25f, 1f)
                                drawCircle(
                                    color = primaryColor.copy(alpha = alpha * 0.22f),
                                    radius = 34f,
                                    center = marker.offset
                                )
                                drawCircle(
                                    color = primaryColor.copy(alpha = alpha),
                                    radius = 22f,
                                    center = marker.offset,
                                    style = Stroke(width = 4f)
                                )
                                drawCircle(
                                    color = primaryColor.copy(alpha = alpha),
                                    radius = 5f,
                                    center = marker.offset
                                )
                            }
                        }

                        if (totalTaps == 0) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "터치 테스트 보드",
                                    style = MiuixTheme.textStyles.headline2.copy(fontWeight = FontWeight.SemiBold),
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "여기를 직접 탭하거나 오토 클리커 오버레이를 띄워\n랜덤 터치 분산과 클릭 간격을 눈으로 확인해 보세요.",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
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
            onNotify("타겟 #${updated.order} 설정이 저장되었습니다.")
        },
        onDelete = { id ->
            onDeleteTarget(id)
            editingTarget = null
            onNotify("타겟 지점을 삭제했습니다.")
        }
    )

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

@Composable
private fun QuickLaunchModeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    isActive: Boolean,
    isAccessibilityEnabled: Boolean,
    statusBadge: String,
    onLaunchClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .squircleClip(20.dp)
            .clickable(onClick = onEditClick),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiuixGlassIcon(
                    icon = icon,
                    contentDescription = title,
                    accentColor = accentColor,
                    size = 36.dp,
                    iconSize = 18.dp,
                    cornerRadius = 11.dp
                )
                SquirclePillBadge(
                    text = statusBadge,
                    backgroundColor = if (isActive) {
                        Color(0xFF2B8A3E).copy(alpha = 0.16f)
                    } else {
                        accentColor.copy(alpha = 0.12f)
                    },
                    contentColor = if (isActive) Color(0xFF2B8A3E) else accentColor
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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

            Button(
                onClick = onLaunchClick,
                modifier = Modifier.fillMaxWidth(),
                colors = if (isActive) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColorsPrimary()
            ) {
                Icon(
                    imageVector = if (isActive) MiuixIcons.Pause else MiuixIcons.Play,
                    contentDescription = null,
                    tint = if (isActive) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        isActive -> "오버레이 닫기"
                        !isAccessibilityEnabled -> "권한 설정"
                        else -> "바로 실행"
                    },
                    style = MiuixTheme.textStyles.button,
                    color = if (isActive) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onPrimary,
                    maxLines = 1
                )
            }
        }
    }
}
