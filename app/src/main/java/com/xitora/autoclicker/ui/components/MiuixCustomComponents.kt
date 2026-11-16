package com.xitora.autoclicker.ui.components

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xitora.autoclicker.domain.ClickEngineCalculator
import com.xitora.autoclicker.model.*
import com.xitora.autoclicker.service.ScreenDetector
import com.xitora.autoclicker.ui.theme.LocalIsDarkTheme
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun MiuixGlassIcon(
    icon: ImageVector,
    contentDescription: String?,
    accentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    @Suppress("UNUSED_PARAMETER") cornerRadius: Dp = 12.dp
) {
    val resolvedIconSize = maxOf(iconSize * 1.42f, (size.value * 0.72f).dp)
    val containerSize = maxOf(resolvedIconSize, (size.value * 0.80f).dp)

    Box(
        modifier = modifier.size(containerSize),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = accentColor,
            modifier = Modifier.size(resolvedIconSize)
        )
    }
}

@Composable
fun SquirclePillBadge(
    text: String,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val base = modifier
        .squircleBackground(backgroundColor, cornerRadius = 10.dp)
        .squircleClip(10.dp)
    val finalModifier = if (onClick != null) base.clickable(onClick = onClick) else base
    Box(
        modifier = finalModifier.padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EmptyDataBanner(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MiuixGlassIcon(
                icon = icon,
                contentDescription = null,
                accentColor = accentColor,
                size = 60.dp,
                iconSize = 28.dp,
                cornerRadius = 18.dp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.Bold),
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    text = actionLabel,
                    onClick = onAction,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun TimeUnitValueRow(
    title: String,
    subtitle: String,
    value: Long,
    unit: TimeUnitOption,
    quickPresets: List<Long>,
    onChanged: (Long, TimeUnitOption) -> Unit
) {
    val valueMs = remember(value, unit) { ClickEngineCalculator.toMillis(value, unit) }
    var textValue by remember(valueMs) { mutableStateOf(valueMs.toString()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold),
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            SquirclePillBadge(
                text = "${valueMs}ms",
                backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.13f),
                contentColor = MiuixTheme.colorScheme.primary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = textValue,
                onValueChange = { raw ->
                    val filtered = raw.filter(Char::isDigit).take(9)
                    textValue = filtered
                    val parsed = filtered.toLongOrNull()
                    if (parsed != null && parsed >= 1L) {
                        onChanged(parsed, TimeUnitOption.MS)
                    }
                },
                label = "밀리초(ms) 입력",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .width(54.dp)
                    .squircleBackground(
                        color = MiuixTheme.colorScheme.secondaryContainer,
                        cornerRadius = 12.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ms",
                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold),
                    color = MiuixTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(quickPresets) { presetMs ->
                val active = valueMs == presetMs
                SquirclePillBadge(
                    text = "${presetMs}ms",
                    backgroundColor = if (active) {
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        MiuixTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (active) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                    onClick = {
                        textValue = presetMs.toString()
                        onChanged(presetMs, TimeUnitOption.MS)
                    }
                )
            }
        }
    }
}

@Composable
fun ClickTimingEditorSection(
    config: ClickTimingConfig,
    showRepeatSection: Boolean,
    reticleSizeDp: Int = 46,
    antiBotSubtitle: String = "선택한 영역 내 무작위 좌표로 클릭해 패턴 감지를 줄입니다",
    onConfigChange: (ClickTimingConfig) -> Unit
) {
    val intervalPresets = remember { listOf(50L, 100L, 300L, 1000L, 3000L) }
    val durationPresets = remember { listOf(20L, 40L, 80L, 150L, 500L) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SmallTitle(text = "클릭 타이밍 (ms)")
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            insideMargin = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TimeUnitValueRow(
                    title = "클릭 간격",
                    subtitle = "클릭과 다음 클릭 사이의 대기 시간 (ms)",
                    value = config.intervalValue,
                    unit = config.intervalUnit,
                    quickPresets = intervalPresets,
                    onChanged = { v, u -> onConfigChange(config.copy(intervalValue = v, intervalUnit = u)) }
                )
                HorizontalDivider()
                TimeUnitValueRow(
                    title = "클릭 지속시간",
                    subtitle = "화면을 누르고 유지하는 터치 길이 (ms)",
                    value = config.durationValue,
                    unit = config.durationUnit,
                    quickPresets = durationPresets,
                    onChanged = { v, u -> onConfigChange(config.copy(durationValue = v, durationUnit = u)) }
                )
            }
        }

        if (showRepeatSection) {
            SmallTitle(text = "반복 종료 조건")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                cornerRadius = 20.dp,
                insideMargin = PaddingValues(16.dp)
            ) {
                val modes = RepeatMode.entries
                var countText by remember(config.repeatCount) { mutableStateOf(config.repeatCount.toString()) }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TabRowWithContour(
                        tabs = listOf("계속 반복", "일정 시간", "횟수 입력"),
                        selectedTabIndex = modes.indexOf(config.repeatMode),
                        onTabSelected = { idx ->
                            onConfigChange(config.copy(repeatMode = modes[idx]))
                        }
                    )

                    when (config.repeatMode) {
                        RepeatMode.INFINITE -> {
                            Text(
                                text = "오버레이의 일시정지/닫기 버튼을 누를 때까지 계속 클릭합니다.",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                        RepeatMode.DURATION -> {
                            TimeUnitValueRow(
                                title = "지속 시간 설정",
                                subtitle = "설정한 시간(ms)이 지나면 자동으로 클릭이 멈춥니다",
                                value = config.repeatDurationValue,
                                unit = config.repeatDurationUnit,
                                quickPresets = listOf(10_000L, 30_000L, 60_000L, 300_000L, 600_000L),
                                onChanged = { v, u ->
                                    onConfigChange(
                                        config.copy(
                                            repeatDurationValue = v,
                                            repeatDurationUnit = u
                                        )
                                    )
                                }
                            )
                        }
                        RepeatMode.COUNT -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextField(
                                    value = countText,
                                    onValueChange = { raw ->
                                        val digits = raw.filter(Char::isDigit).take(7)
                                        countText = digits
                                        val parsed = digits.toIntOrNull()
                                        if (parsed != null && parsed >= 1) {
                                            onConfigChange(config.copy(repeatCount = parsed))
                                        }
                                    },
                                    label = "반복 횟수 (회 / 사이클)",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(listOf(10, 50, 100, 500, 1000)) { presetCnt ->
                                        SquirclePillBadge(
                                            text = "${presetCnt}회",
                                            backgroundColor = if (config.repeatCount == presetCnt) {
                                                MiuixTheme.colorScheme.primary.copy(alpha = 0.18f)
                                            } else {
                                                MiuixTheme.colorScheme.surfaceContainerHigh
                                            },
                                            contentColor = if (config.repeatCount == presetCnt) {
                                                MiuixTheme.colorScheme.primary
                                            } else {
                                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                                            },
                                            onClick = {
                                                countText = presetCnt.toString()
                                                onConfigChange(config.copy(repeatCount = presetCnt))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SmallTitle(text = "봇 방지 랜덤 터치")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            cornerRadius = 20.dp
        ) {
            SwitchPreference(
                title = "랜덤 영역 내 클릭 (Anti-Bot)",
                summary = antiBotSubtitle,
                checked = config.antiBotRandomEnabled,
                onCheckedChange = { enabled ->
                    onConfigChange(config.copy(antiBotRandomEnabled = enabled))
                },
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                startAction = {
                    MiuixGlassIcon(
                        icon = MiuixIcons.Stopwatch,
                        contentDescription = null,
                        accentColor = Color(0xFF06B6D4)
                    )
                }
            )
            AnimatedVisibility(
                visible = config.antiBotRandomEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "선택 랜덤 영역 반경",
                                style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold),
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            SquirclePillBadge(
                                text = "반경 ${config.randomRadiusDp}dp",
                                backgroundColor = Color(0xFF06B6D4).copy(alpha = 0.15f),
                                contentColor = Color(0xFF0891B2)
                            )
                        }
                        Slider(
                            value = config.randomRadiusDp.toFloat(),
                            onValueChange = { raw ->
                                onConfigChange(config.copy(randomRadiusDp = raw.roundToInt().coerceIn(8, 80)))
                            },
                            valueRange = 8f..80f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(176.dp)
                                    .squircleBackground(MiuixTheme.colorScheme.surfaceContainerHigh, 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                TargetPreviewBadge(
                                    order = 1,
                                    antiBotEnabled = true,
                                    radiusDp = config.randomRadiusDp,
                                    reticleSizeDp = reticleSizeDp,
                                    exactScale = true,
                                    modifier = Modifier.size(172.dp)
                                )
                            }
                            Text(
                                text = "실제 화면과 동일한 1:1 비율입니다. 파란 점선 원 내부 어느 위치든 무작위로 터치됩니다.",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TargetPreviewBadge(
    order: Int,
    antiBotEnabled: Boolean,
    radiusDp: Int,
    modifier: Modifier = Modifier,
    reticleSizeDp: Int = 46,
    exactScale: Boolean = false
) {
    val isDark = LocalIsDarkTheme.current
    val ringColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF2B2D30)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            if (antiBotEnabled) {
                val previewR = if (exactScale) {
                    radiusDp.dp.toPx().coerceAtLeast(8.dp.toPx()).coerceAtMost(size.minDimension * 0.48f)
                } else {
                    val maxR = size.minDimension * 0.46f
                    ((radiusDp.coerceIn(8, 80) / 80f) * maxR).coerceAtLeast(size.minDimension * 0.22f)
                }
                drawCircle(
                    color = Color(0xFF3A9BDC).copy(alpha = 0.14f),
                    radius = previewR,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color(0xFF3A9BDC).copy(alpha = 0.72f),
                    radius = previewR,
                    center = Offset(cx, cy),
                    style = Stroke(
                        width = if (exactScale) 1.8.dp.toPx() else 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            if (exactScale) floatArrayOf(5.dp.toPx(), 4.dp.toPx()) else floatArrayOf(8f, 6f),
                            0f
                        )
                    )
                )
            }
            val ringR = if (exactScale) reticleSizeDp.dp.toPx() * 0.40f else size.minDimension * 0.27f
            val strokeW = if (exactScale) 3.0.dp.toPx() else 2.2.dp.toPx()
            drawCircle(
                color = ringColor,
                radius = ringR,
                center = Offset(cx, cy),
                style = Stroke(width = strokeW)
            )
            val tickOut = if (exactScale) 4.8.dp.toPx() else 3.5.dp.toPx()
            val tickIn = if (exactScale) 4.2.dp.toPx() else 3.dp.toPx()
            drawLine(ringColor, Offset(cx, cy - ringR - tickOut), Offset(cx, cy - ringR + tickIn), strokeW, StrokeCap.Round)
            drawLine(ringColor, Offset(cx, cy + ringR - tickIn), Offset(cx, cy + ringR + tickOut), strokeW, StrokeCap.Round)
            drawLine(ringColor, Offset(cx - ringR - tickOut, cy), Offset(cx - ringR + tickIn, cy), strokeW, StrokeCap.Round)
            drawLine(ringColor, Offset(cx + ringR - tickIn, cy), Offset(cx + ringR + tickOut, cy), strokeW, StrokeCap.Round)
        }
        Text(
            text = order.toString(),
            fontSize = if (exactScale) (reticleSizeDp * 0.38f).sp else 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD94B64)
        )
    }
}

@Composable
fun AccessibilityPermissionGuideDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppInfo: () -> Unit
) {
    OverlayDialog(
        show = show,
        title = "접근성 권한 안내",
        summary = "오토 클리커가 화면에 컨트롤 패널·타겟 지점을 띄우고 자동 터치를 실행하려면 안드로이드 접근성 서비스를 켜야 합니다.",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .squircleBackground(MiuixTheme.colorScheme.surfaceContainerHigh, 14.dp)
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "허용 방법",
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                        color = MiuixTheme.colorScheme.primary
                    )
                    Text(
                        text = "1. 아래 [접근성 설정]을 누릅니다.\n2. [설치된 앱] 또는 [다운로드한 앱]에서 '오토 클리커'를 선택합니다.\n3. 스위치를 켜고 허용을 누른 뒤 돌아오면 바로 사용할 수 있습니다.",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .squircleBackground(Color(0xFFF59E0B).copy(alpha = 0.10f), 14.dp)
                    .squircleBorder(1.dp, Color(0xFFF59E0B).copy(alpha = 0.25f), 14.dp)
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "스위치가 안 눌리는 경우 (OneUI · Android 13+)",
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFD97706)
                    )
                    Text(
                        text = "일부 기기에서는 보안상 접근성이 잠겨 있을 수 있습니다.\n[앱 정보 열기] → 우측 상단 ⋮ 메뉴 → [제한된 설정 허용]을 먼저 누른 뒤 다시 접근성 설정에서 켜주세요.",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
            TextButton(
                text = "앱 정보 열기 (제한된 설정 허용)",
                onClick = {
                    onDismiss()
                    onOpenAppInfo()
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(text = "닫기", onClick = onDismiss, modifier = Modifier.weight(1f))
                TextButton(
                    text = "접근성 설정",
                    onClick = {
                        onDismiss()
                        onOpenSettings()
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SavePresetDialog(
    show: Boolean,
    mode: ClickMode,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(show, mode) {
        mutableStateOf(
            when (mode) {
                ClickMode.SINGLE -> "내 단일 클릭 프리셋"
                ClickMode.MULTI -> "내 다중 클릭 프리셋"
                ClickMode.SCENARIO -> "내 시나리오 매크로"
            }
        )
    }

    OverlayDialog(
        show = show,
        title = "프리셋으로 저장",
        summary = "현재 ${mode.label}의 타겟 위치와 타이밍 설정을 저장합니다.",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = name,
                onValueChange = { if (it.length <= 32) name = it },
                label = "프리셋 이름",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(text = "취소", onClick = onDismiss, modifier = Modifier.weight(1f))
                TextButton(
                    text = "저장",
                    onClick = {
                        onConfirm(name.trim())
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun TargetPointEditDialog(
    target: TargetPoint?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (TargetPoint) -> Unit,
    onDelete: (String) -> Unit
) {
    if (target == null) return
    var editing by remember(target.id, target) {
        mutableStateOf(
            ClickTimingConfig(
                intervalValue = target.intervalValue,
                intervalUnit = target.intervalUnit,
                durationValue = target.durationValue,
                durationUnit = target.durationUnit,
                antiBotRandomEnabled = target.antiBotRandomEnabled,
                randomRadiusDp = target.randomRadiusDp
            )
        )
    }

    OverlayDialog(
        show = true,
        title = "타겟 #${target.order} 세부 설정",
        summary = "화면 좌표 (${target.xPx.roundToInt()}, ${target.yPx.roundToInt()})",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 310.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TimeUnitValueRow(
                    title = "이 지점 대기 간격",
                    subtitle = "클릭 완료 후 다음 타겟까지 간격 (ms)",
                    value = editing.intervalValue,
                    unit = editing.intervalUnit,
                    quickPresets = listOf(50L, 100L, 500L, 1000L),
                    onChanged = { v, u -> editing = editing.copy(intervalValue = v, intervalUnit = u) }
                )
                HorizontalDivider()
                TimeUnitValueRow(
                    title = "클릭 지속시간",
                    subtitle = "이 지점을 누르고 있는 시간 (ms)",
                    value = editing.durationValue,
                    unit = editing.durationUnit,
                    quickPresets = listOf(20L, 40L, 100L),
                    onChanged = { v, u -> editing = editing.copy(durationValue = v, durationUnit = u) }
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("봇 방지 랜덤 터치", style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            if (editing.antiBotRandomEnabled) "반경 ${editing.randomRadiusDp}dp 내 랜덤 클릭" else "정확한 중심 좌표 클릭",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                    Switch(
                        checked = editing.antiBotRandomEnabled,
                        onCheckedChange = { editing = editing.copy(antiBotRandomEnabled = it) }
                    )
                }
                AnimatedVisibility(
                    visible = editing.antiBotRandomEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Slider(
                        value = editing.randomRadiusDp.toFloat(),
                        onValueChange = { editing = editing.copy(randomRadiusDp = it.roundToInt().coerceIn(8, 80)) },
                        valueRange = 8f..80f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (canDelete) {
                    TextButton(
                        text = "이 타겟 삭제",
                        onClick = {
                            onDelete(target.id)
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            color = MiuixTheme.colorScheme.error,
                            textColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(text = "취소", onClick = onDismiss, modifier = Modifier.weight(1f))
                TextButton(
                    text = "저장",
                    onClick = {
                        onSave(
                            target.copy(
                                intervalValue = editing.intervalValue,
                                intervalUnit = editing.intervalUnit,
                                durationValue = editing.durationValue,
                                durationUnit = editing.durationUnit,
                                antiBotRandomEnabled = editing.antiBotRandomEnabled,
                                randomRadiusDp = editing.randomRadiusDp
                            )
                        )
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ScenarioStepEditDialog(
    step: ScenarioStep?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (ScenarioStep) -> Unit,
    onDelete: (String) -> Unit,
    onFeedback: (String) -> Unit
) {
    if (step == null) return
    val context = LocalContext.current
    var editing by remember(step.id, step) { mutableStateOf(step) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val savedPath = ScreenDetector.saveTemplateImageFromUri(context, uri)
            if (savedPath != null) {
                editing = editing.copy(targetImagePath = savedPath)
                onFeedback("감지용 이미지를 업로드했습니다.")
            } else {
                onFeedback("이미지를 불러올 수 없습니다. 다른 이미지를 선택해 주세요.")
            }
        }
    }

    val previewBitmap: ImageBitmap? = remember(editing.targetImagePath) {
        val path = editing.targetImagePath ?: return@remember null
        runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
    }

    OverlayDialog(
        show = true,
        title = "단계 #${step.order} 상세 설정",
        summary = "화면 좌표 (${step.xPx.roundToInt()}, ${step.yPx.roundToInt()}) · 오버레이에서 드래그로 이동",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(
                    value = editing.title,
                    onValueChange = { if (it.length <= 36) editing = editing.copy(title = it) },
                    label = "단계 이름",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "동작 유형",
                        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val typeLabels = listOf(
                            ScenarioStepType.REPEAT_UNTIL_DETECT to "반복+감지",
                            ScenarioStepType.SINGLE_TAP to "1회 클릭",
                            ScenarioStepType.SCROLL to "스크롤"
                        )
                        typeLabels.forEach { (typeOpt, shortTitle) ->
                            val selected = editing.type == typeOpt
                            SquirclePillBadge(
                                text = shortTitle,
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
                                onClick = { editing = editing.copy(type = typeOpt) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                when (editing.type) {
                    ScenarioStepType.REPEAT_UNTIL_DETECT -> {
                        HorizontalDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "반복 중단(다음 단계 전환) 감지 조건",
                                style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(DetectionConditionType.entries) { cond ->
                                    val selected = editing.conditionType == cond
                                    SquirclePillBadge(
                                        text = cond.label,
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
                                        onClick = { editing = editing.copy(conditionType = cond) }
                                    )
                                }
                            }
                        }

                        if (editing.conditionType != DetectionConditionType.IMAGE_ONLY) {
                            TextField(
                                value = editing.targetText,
                                onValueChange = { if (it.length <= 120) editing = editing.copy(targetText = it) },
                                label = "감지할 화면 텍스트 (복수 키워드는 | 구분)",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (editing.conditionType != DetectionConditionType.TEXT_ONLY) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .squircleBackground(MiuixTheme.colorScheme.surfaceContainerHigh, 14.dp)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "감지할 기준 이미지",
                                        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (editing.targetImagePath != null) {
                                            SquirclePillBadge(
                                                text = "제거",
                                                backgroundColor = Color(0xFFD93025).copy(alpha = 0.14f),
                                                contentColor = Color(0xFFD93025),
                                                onClick = { editing = editing.copy(targetImagePath = null) }
                                            )
                                        }
                                        SquirclePillBadge(
                                            text = if (editing.targetImagePath == null) "이미지 업로드" else "변경",
                                            backgroundColor = MiuixTheme.colorScheme.primary,
                                            contentColor = MiuixTheme.colorScheme.onPrimary,
                                            onClick = { imagePickerLauncher.launch("image/*") }
                                        )
                                    }
                                }

                                if (previewBitmap != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Image(
                                            bitmap = previewBitmap,
                                            contentDescription = "업로드된 감지 이미지",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .squircleClip(10.dp)
                                                .background(Color.Black.copy(alpha = 0.08f))
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "유사도 임계값: ${editing.imageSimilarityPercent}%",
                                                style = MiuixTheme.textStyles.footnote1.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontFeatureSettings = "tnum"
                                                )
                                            )
                                            Slider(
                                                value = editing.imageSimilarityPercent.toFloat(),
                                                onValueChange = {
                                                    editing = editing.copy(
                                                        imageSimilarityPercent = it.roundToInt().coerceIn(65, 98)
                                                    )
                                                },
                                                valueRange = 65f..98f,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "잘라낸 버튼·문구 스크린샷을 올리면 Android 11+에서 자동 인식합니다.",
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                            }
                        }

                        TimeUnitValueRow(
                            title = "반복 클릭 간격",
                            subtitle = "감지되기 전까지 반복 터치하는 주기 (ms)",
                            value = editing.repeatIntervalMs,
                            unit = TimeUnitOption.MS,
                            quickPresets = listOf(60L, 120L, 250L, 500L),
                            onChanged = { v, _ -> editing = editing.copy(repeatIntervalMs = v.coerceAtLeast(20L)) }
                        )
                        TimeUnitValueRow(
                            title = "화면 감지 확인 주기",
                            subtitle = "텍스트/이미지 조건을 확인하는 간격 (ms)",
                            value = editing.detectCheckIntervalMs,
                            unit = TimeUnitOption.MS,
                            quickPresets = listOf(250L, 450L, 800L, 1200L),
                            onChanged = { v, _ -> editing = editing.copy(detectCheckIntervalMs = v.coerceAtLeast(200L)) }
                        )
                    }
                    ScenarioStepType.SINGLE_TAP -> {
                        HorizontalDivider()
                        TimeUnitValueRow(
                            title = "클릭 지속시간",
                            subtitle = "이 위치를 누르고 있는 시간 (ms)",
                            value = editing.tapDurationMs,
                            unit = TimeUnitOption.MS,
                            quickPresets = listOf(20L, 40L, 100L),
                            onChanged = { v, _ -> editing = editing.copy(tapDurationMs = v.coerceIn(10L, 60_000L)) }
                        )
                    }
                    ScenarioStepType.SCROLL -> {
                        HorizontalDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "스크롤 방향",
                                style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(ScrollDirection.entries) { dir ->
                                    val selected = editing.scrollDirection == dir
                                    val shortDir = when (dir) {
                                        ScrollDirection.DOWN -> "↓ 아래로 내리기"
                                        ScrollDirection.UP -> "↑ 위로 올리기"
                                        ScrollDirection.LEFT -> "← 왼쪽"
                                        ScrollDirection.RIGHT -> "→ 오른쪽"
                                    }
                                    SquirclePillBadge(
                                        text = shortDir,
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
                                        onClick = { editing = editing.copy(scrollDirection = dir) }
                                    )
                                }
                            }
                        }
                        Text(
                            text = "스크롤 이동 거리: ${editing.scrollDistanceDp}dp",
                            style = MiuixTheme.textStyles.body2.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum"
                            )
                        )
                        Slider(
                            value = editing.scrollDistanceDp.toFloat(),
                            onValueChange = { editing = editing.copy(scrollDistanceDp = it.roundToInt().coerceIn(100, 900)) },
                            valueRange = 100f..900f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TimeUnitValueRow(
                            title = "스크롤 소요 시간",
                            subtitle = "짧을수록 빠르게 튕기듯 스크롤됩니다 (ms)",
                            value = editing.scrollDurationMs,
                            unit = TimeUnitOption.MS,
                            quickPresets = listOf(220L, 360L, 600L),
                            onChanged = { v, _ -> editing = editing.copy(scrollDurationMs = v.coerceIn(80L, 5_000L)) }
                        )
                    }
                }

                HorizontalDivider()
                TimeUnitValueRow(
                    title = "실행 후 다음 단계 대기",
                    subtitle = "화면 전환을 기다리는 여유 시간 (ms)",
                    value = editing.postDelayMs,
                    unit = TimeUnitOption.MS,
                    quickPresets = listOf(200L, 450L, 800L, 1500L),
                    onChanged = { v, _ -> editing = editing.copy(postDelayMs = v.coerceAtLeast(20L)) }
                )

                if (editing.type != ScenarioStepType.SCROLL) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("봇 방지 랜덤 터치", style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                if (editing.antiBotRandomEnabled) "반경 ${editing.randomRadiusDp}dp 내 랜덤" else "정확한 중심 좌표",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                        Switch(
                            checked = editing.antiBotRandomEnabled,
                            onCheckedChange = { editing = editing.copy(antiBotRandomEnabled = it) }
                        )
                    }
                    AnimatedVisibility(visible = editing.antiBotRandomEnabled) {
                        Slider(
                            value = editing.randomRadiusDp.toFloat(),
                            onValueChange = { editing = editing.copy(randomRadiusDp = it.roundToInt().coerceIn(8, 80)) },
                            valueRange = 8f..80f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (canDelete) {
                    TextButton(
                        text = "이 단계 삭제",
                        onClick = {
                            onDelete(step.id)
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            color = MiuixTheme.colorScheme.error,
                            textColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(text = "취소", onClick = onDismiss, modifier = Modifier.weight(1f))
                TextButton(
                    text = "저장",
                    onClick = {
                        onSave(editing.copy(title = editing.title.trim().ifEmpty { step.title }))
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
