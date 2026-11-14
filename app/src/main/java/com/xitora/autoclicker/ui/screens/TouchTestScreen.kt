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
        tapTimestamps.count { now - it <= 1000L }.toFloat()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp + navBottomInset),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                    .height(380.dp)
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
                            tapMarkers.add(TapMarker(id = now + totalTaps, offset = offset, timestampMs = now))
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
