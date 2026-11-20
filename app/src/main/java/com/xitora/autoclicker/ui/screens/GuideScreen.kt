package com.xitora.autoclicker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xitora.autoclicker.ui.components.MiuixGlassIcon
import com.xitora.autoclicker.ui.theme.LocalWideDualLayoutEnabled
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun GuideScreen(
    isAccessibilityEnabled: Boolean,
    scrollBehavior: ScrollBehavior,
    topInset: Dp = 0.dp,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppInfoSettings: () -> Unit
) {
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPad = 116.dp + navBottomInset
    val wideDualEnabled = LocalWideDualLayoutEnabled.current

    val permissionsSection: @Composable () -> Unit = {
        Column {
            SmallTitle(text = "접근성 서비스 · 제한된 설정 허용")
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp
            ) {
                ArrowPreference(
                    title = if (isAccessibilityEnabled) "접근성 권한 허용됨" else "접근성 권한 꺼짐",
                    summary = if (isAccessibilityEnabled) {
                        "모든 오버레이와 자동 터치 기능을 사용할 수 있습니다"
                    } else {
                        "탭하여 안드로이드 접근성 → 설치된 앱 → 오토 클리커를 켜주세요"
                    },
                    onClick = onOpenAccessibilitySettings,
                    startAction = {
                        MiuixGlassIcon(
                            icon = MiuixIcons.Settings,
                            contentDescription = null,
                            accentColor = if (isAccessibilityEnabled) Color(0xFF10B981) else Color(0xFFD93025),
                            modifier = Modifier.padding(end = 6.dp),
                            size = 38.dp,
                            iconSize = 18.dp
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ArrowPreference(
                    title = "제한된 설정 허용 (OneUI · Android 13+)",
                    summary = "접근성 스위치가 잠겨 눌리지 않을 때 먼저 앱 정보에서 해제하세요",
                    onClick = onOpenAppInfoSettings,
                    startAction = {
                        MiuixGlassIcon(
                            icon = MiuixIcons.Lock,
                            contentDescription = null,
                            accentColor = Color(0xFFF59E0B),
                            modifier = Modifier.padding(end = 6.dp),
                            size = 38.dp,
                            iconSize = 18.dp
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "삼성 OneUI · 안드로이드 13 이상 해결 순서",
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                        color = MiuixTheme.colorScheme.primary
                    )
                    Text(
                        text = "1. 위 [제한된 설정 허용]을 눌러 앱 정보 화면으로 이동합니다.\n" +
                            "2. 화면 우측 상단 ⋮ 메뉴에서 [제한된 설정 허용]을 선택합니다.\n" +
                            "3. 돌아와 [접근성 권한]에서 '오토 클리커' 스위치를 켭니다.",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }

    val controlsSection: @Composable () -> Unit = {
        Column {
            SmallTitle(text = "플로팅 컨트롤 패널 사용법")
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp
            ) {
                GuideRow(
                    icon = MiuixIcons.Play,
                    accent = Color(0xFF2B8A3E),
                    title = "시작 / 일시정지",
                    subtitle = "설정된 간격(ms)과 종료 조건에 따라 자동 클릭을 시작하거나 멈춥니다."
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                GuideRow(
                    icon = MiuixIcons.Add,
                    accent = MiuixTheme.colorScheme.primary,
                    title = "타겟 추가 / 제거",
                    subtitle = "다중 타겟 모드에서 화면에 클릭 지점(1, 2, 3...)을 즉시 늘리거나 줄입니다."
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                GuideRow(
                    icon = MiuixIcons.Settings,
                    accent = Color(0xFF06B6D4),
                    title = "패널 설정 · 타겟 직접 탭",
                    subtitle = "컨트롤 패널의 설정 버튼 또는 화면 위 과녁 번호를 짧게 눌러 타이밍을 바로 바꿀 수 있습니다."
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                GuideRow(
                    icon = MiuixIcons.Close,
                    accent = MiuixTheme.colorScheme.error,
                    title = "오버레이 닫기",
                    subtitle = "자동 클릭을 중단하고 떠 있는 컨트롤 패널과 모든 타겟 과녁을 닫습니다."
                )
            }
        }
    }

    val antibotSection: @Composable () -> Unit = {
        Column {
            SmallTitle(text = "봇 방지 랜덤 터치 · 다중 타겟 팁")
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                insideMargin = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiuixGlassIcon(
                            icon = MiuixIcons.Stopwatch,
                            contentDescription = null,
                            accentColor = Color(0xFF06B6D4)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "1:1 비율 랜덤 영역 미리보기",
                                style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "설정 화면의 파란 점선 원 크기 그대로 실제 오버레이와 터치 반경이 적용됩니다.",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .squircleBackground(MiuixTheme.colorScheme.surfaceContainerHigh, 12.dp)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Layers,
                            contentDescription = null,
                            tint = Color(0xFF2B8A3E),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "다중 타겟 지점은 순번(1→2→3)대로 실행되며, 각 지점을 개별 설정할 수 있습니다.",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideDual = wideDualEnabled && maxWidth >= 600.dp
        val singleSidePad = ((maxWidth - 600.dp) / 2f).coerceAtLeast(20.dp)

        if (isWideDual) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(top = topInset + 12.dp, bottom = bottomPad),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "guide_permissions") { permissionsSection() }
                }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(top = topInset + 12.dp, bottom = bottomPad),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "guide_overlay_controls") { controlsSection() }
                    item(key = "guide_antibot") { antibotSection() }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = singleSidePad,
                    end = singleSidePad,
                    top = topInset + 12.dp,
                    bottom = bottomPad
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "guide_permissions") { permissionsSection() }
                item(key = "guide_overlay_controls") { controlsSection() }
                item(key = "guide_antibot") { antibotSection() }
            }
        }
    }
}

@Composable
private fun GuideRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiuixGlassIcon(
            icon = icon,
            contentDescription = null,
            accentColor = accent,
            size = 38.dp,
            iconSize = 18.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold),
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}
