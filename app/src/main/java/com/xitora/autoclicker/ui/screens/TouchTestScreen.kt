package com.xitora.autoclicker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xitora.autoclicker.ui.components.SquirclePillBadge
import com.xitora.autoclicker.ui.theme.LocalIsDarkTheme
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.util.Locale

private data class TapMarker(
    val id: Long,
    val offset: Offset,
    val timestampMs: Long
)

@Composable
fun TouchTestScreen(
    scrollBehavior: ScrollBehavior
) {
    val isDark = LocalIsDarkTheme.current
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var totalTaps by remember { mutableIntStateOf(0) }
    var lastIntervalMs by remember { mutableStateOf<Long?>(null) }
    val tapMarkers = remember { mutableStateListOf<TapMarker>() }
    val tapTimestamps = remember { mutableStateListOf<Long>() }

    val cps = remember(tapTimestamps.size, totalTaps) {
        val now = System.currentTimeMillis()
        val recent = tapTimestamps.count { now - it <= 1000L }
        recent
    }

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "실시간 클릭 · 봇 방지 탄착군 테스트",
                                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "오버레이를 켜고 아래 영역 위에 타겟을 올려 실제 클릭과 랜덤 분산을 확인하세요.",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SquirclePillBadge(
                            text = "누적 ${totalTaps}회",
                            backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                            contentColor = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        SquirclePillBadge(
                            text = "초당 ${cps}회 (CPS)",
                            backgroundColor = Color(0xFF10B981).copy(alpha = 0.14f),
                            contentColor = Color(0xFF059669),
                            modifier = Modifier.weight(1f)
                        )
                        SquirclePillBadge(
                            text = lastIntervalMs?.let { "${it}ms 간격" } ?: "측정 대기",
                            backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                            contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            val primaryColor = MiuixTheme.colorScheme.primary
            val ringColor = MiuixTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.14f else 0.10f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .squircleBackground(MiuixTheme.colorScheme.surfaceContainer, 24.dp)
                    .squircleBorder(1.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f), 24.dp)
                    .squircleClip(24.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { pos ->
                            val now = System.currentTimeMillis()
                            val prev = tapTimestamps.lastOrNull()
                            if (prev != null) {
                                lastIntervalMs = (now - prev).coerceAtLeast(0L)
                            }
                            tapTimestamps.add(now)
                            if (tapTimestamps.size > 40) {
                                tapTimestamps.removeAt(0)
                            }
                            totalTaps++
                            tapMarkers.add(TapMarker(id = now + totalTaps, offset = pos, timestampMs = now))
                            if (tapMarkers.size > 36) {
                                tapMarkers.removeAt(0)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxR = size.minDimension * 0.40f
                    listOf(0.35f, 0.65f, 1.0f).forEach { factor ->
                        drawCircle(
                            color = ringColor,
                            radius = maxR * factor,
                            center = center,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                    drawLine(ringColor, Offset(center.x, 24f), Offset(center.x, size.height - 24f), 1.dp.toPx())
                    drawLine(ringColor, Offset(24f, center.y), Offset(size.width - 24f, center.y), 1.dp.toPx())

                    tapMarkers.forEachIndexed { idx, marker ->
                        val ageFraction = (idx + 1).toFloat() / tapMarkers.size.coerceAtLeast(1)
                        val isLatest = idx == tapMarkers.lastIndex
                        drawCircle(
                            color = (if (isLatest) Color(0xFFE05263) else primaryColor).copy(alpha = 0.25f + 0.65f * ageFraction),
                            radius = if (isLatest) 10.dp.toPx() else 5.5.dp.toPx(),
                            center = marker.offset
                        )
                        if (isLatest) {
                            drawCircle(
                                color = Color(0xFFE05263),
                                radius = 17.dp.toPx(),
                                center = marker.offset,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }

                if (totalTaps == 0) {
                    Text(
                        text = "여기를 터치하거나 타겟 과녁을 올려 실행해 보세요",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }

        item {
            TextButton(
                text = "테스트 기록 초기화",
                onClick = {
                    totalTaps = 0
                    lastIntervalMs = null
                    tapMarkers.clear()
                    tapTimestamps.clear()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
