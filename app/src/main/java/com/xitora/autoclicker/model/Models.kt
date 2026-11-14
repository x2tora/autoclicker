package com.xitora.autoclicker.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ThemeOption(val label: String) {
    SYSTEM("시스템 설정 따름"),
    LIGHT("라이트 모드"),
    DARK("다크 모드")
}

@Serializable
enum class ThemeColorStyle(val label: String, val description: String) {
    DEFAULT("기본 블루", "Miuix 2025 블루 스펙"),
    MATERIAL_YOU("Material You", "배경화면 다이나믹 팔레트"),
    CUSTOM("커스텀 색상", "직접 고른 포인트 색상")
}

@Serializable
enum class NavBarStyle(val label: String, val description: String) {
    MIUIX("Miuix 기본", "표준 Miuix 하단 내비게이션 바"),
    LIQUID_GLASS("리퀴드 글래스", "AGSL 굴절 효과가 적용된 플로팅 바")
}

@Serializable
enum class ClickMode(val label: String) {
    SINGLE("단일 타겟 모드"),
    MULTI("다중 타겟 모드")
}

@Serializable
enum class TimeUnitOption(val symbol: String, val label: String, val toMsMultiplier: Long) {
    MS("ms", "밀리초 (ms)", 1L),
    SEC("s", "초 (s)", 1_000L),
    MIN("m", "분 (m)", 60_000L),
    HOUR("h", "시간 (h)", 3_600_000L)
}

@Serializable
enum class RepeatMode(val label: String, val shortLabel: String) {
    INFINITE("멈출 때까지 계속 클릭", "Unlimited"),
    DURATION("일정 시간 동안 지속", "Timer"),
    COUNT("반복 횟수 직접 입력", "Count")
}

@Serializable
data class ClickTimingConfig(
    val intervalValue: Long = 100L,
    val intervalUnit: TimeUnitOption = TimeUnitOption.MS,
    val durationValue: Long = 40L,
    val durationUnit: TimeUnitOption = TimeUnitOption.MS,
    val repeatMode: RepeatMode = RepeatMode.INFINITE,
    val repeatCount: Int = 100,
    val repeatDurationValue: Long = 5L,
    val repeatDurationUnit: TimeUnitOption = TimeUnitOption.MIN,
    val antiBotRandomEnabled: Boolean = false,
    val randomRadiusDp: Int = 24
)

@Serializable
data class TargetPoint(
    val id: String = UUID.randomUUID().toString(),
    val order: Int = 1,
    val xPx: Float = 540f,
    val yPx: Float = 1100f,
    val intervalValue: Long = 100L,
    val intervalUnit: TimeUnitOption = TimeUnitOption.MS,
    val durationValue: Long = 40L,
    val durationUnit: TimeUnitOption = TimeUnitOption.MS,
    val antiBotRandomEnabled: Boolean = false,
    val randomRadiusDp: Int = 24
)

@Serializable
data class ClickPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mode: ClickMode,
    val timing: ClickTimingConfig,
    val targets: List<TargetPoint>,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Serializable
data class AppSettings(
    val themeOption: ThemeOption = ThemeOption.SYSTEM,
    val themeColorStyle: ThemeColorStyle = ThemeColorStyle.DEFAULT,
    val customKeyColorHex: Long? = 0xFF3482FF,
    val navBarStyle: NavBarStyle = NavBarStyle.MIUIX,
    val targetSizeDp: Int = 46,
    val targetOpacityPercent: Int = 92,
    val showAntiBotZoneGuide: Boolean = true
) {
    fun resolvedColorStyle(): ThemeColorStyle = themeColorStyle
    fun resolvedNavBarStyle(): NavBarStyle = navBarStyle
}

@Serializable
data class AutoClickerDataState(
    val singleConfig: ClickTimingConfig = ClickTimingConfig(),
    val singleTarget: TargetPoint = TargetPoint(
        id = "single_target_1",
        order = 1,
        xPx = 540f,
        yPx = 1100f
    ),
    val multiConfig: ClickTimingConfig = ClickTimingConfig(),
    val multiTargets: List<TargetPoint> = defaultMultiTargets(),
    val presets: List<ClickPreset> = emptyList(),
    val settings: AppSettings = AppSettings()
)

fun defaultMultiTargets(): List<TargetPoint> = listOf(
    TargetPoint(id = "multi_target_1", order = 1, xPx = 360f, yPx = 980f),
    TargetPoint(id = "multi_target_2", order = 2, xPx = 700f, yPx = 1220f)
)

data class OverlayRuntimeState(
    val isAccessibilityEnabled: Boolean = false,
    val isOverlayVisible: Boolean = false,
    val activeMode: ClickMode = ClickMode.SINGLE,
    val isClicking: Boolean = false,
    val elapsedMs: Long = 0L,
    val clickCount: Long = 0L,
    val controlPanelX: Int = 24,
    val controlPanelY: Int = 240
)
