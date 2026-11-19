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
    MULTI("다중 타겟 모드"),
    SCENARIO("시나리오 모드")
}

@Serializable
enum class ScenarioStepType(val label: String, val badgeSuffix: String) {
    REPEAT_UNTIL_DETECT("반복 터치 + 이미지 감지", "🔄"),
    SINGLE_TAP("지정 위치 1회 클릭", ""),
    SCROLL("지정 위치 스크롤", "↕"),
    WAIT_FOR_IMAGE("이미지 대기 (클릭 없음)", "⏳"),
    TAP_DETECTED_IMAGE("감지된 이미지 위치 클릭", "🎯"),
    LONG_PRESS("길게 누르기 (롱프레스)", "✊"),
    DELAY_RANDOM("랜덤 시간 대기", "⏱"),
    COLOR_MATCH("픽셀 색상 감지 대기", "🎨")
}

@Serializable
enum class MultiImageLogic(val label: String) {
    ANY("하나라도 감지 (OR)"),
    ALL("모두 감지 (AND)")
}

@Serializable
enum class ScrollDirection(val label: String, val arrowSymbol: String) {
    DOWN("스크롤 내리기 (아래 내용 보기 · 위로 드래그)", "↓"),
    UP("스크롤 올리기 (위 내용 보기 · 아래로 드래그)", "↑"),
    LEFT("왼쪽으로 넘기기 (우→좌 드래그)", "←"),
    RIGHT("오른쪽으로 넘기기 (좌→우 드래그)", "→")
}

@Serializable
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

@Serializable
data class ScenarioStep(
    val id: String = UUID.randomUUID().toString(),
    val order: Int = 1,
    val title: String = "단계",
    val type: ScenarioStepType = ScenarioStepType.SINGLE_TAP,
    val xPx: Float = 540f,
    val yPx: Float = 1100f,
    val repeatIntervalMs: Long = 120L,
    val tapDurationMs: Long = 40L,
    val detectCheckIntervalMs: Long = 450L,
    val targetImagePath: String? = null,
    val extraImagePaths: List<String> = emptyList(),
    val matchLogic: MultiImageLogic = MultiImageLogic.ANY,
    val invertCondition: Boolean = false,
    val imageSimilarityPercent: Int = 78,
    val detectRoiRectNorm: NormalizedRect? = null,
    val detectTimeoutMs: Long = 0L,
    val onMatchJumpOrder: Int? = null,
    val onTimeoutJumpOrder: Int? = null,
    val delayMinMs: Long = 300L,
    val delayMaxMs: Long = 800L,
    val targetColorHex: Long? = null,
    val colorTolerance: Int = 24,
    val scrollDirection: ScrollDirection = ScrollDirection.DOWN,
    val scrollDistanceDp: Int = 340,
    val scrollDurationMs: Long = 360L,
    val postDelayMs: Long = 450L,
    val antiBotRandomEnabled: Boolean = false,
    val randomRadiusDp: Int = 18,
    val intervalJitterPercent: Int = 0,
    val enabled: Boolean = true
) {
    fun badgeText(): String = when (type) {
        ScenarioStepType.REPEAT_UNTIL_DETECT -> "${order}🔄"
        ScenarioStepType.SINGLE_TAP -> order.toString()
        ScenarioStepType.SCROLL -> "${order}${scrollDirection.arrowSymbol}"
        ScenarioStepType.WAIT_FOR_IMAGE -> "${order}⏳"
        ScenarioStepType.TAP_DETECTED_IMAGE -> "${order}🎯"
        ScenarioStepType.LONG_PRESS -> "${order}✊"
        ScenarioStepType.DELAY_RANDOM -> "${order}⏱"
        ScenarioStepType.COLOR_MATCH -> "${order}🎨"
    }
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
    val randomRadiusDp: Int = 24,
    val intervalJitterPercent: Int = 0,
    val durationJitterMs: Int = 0,
    val breakEveryMinutes: Int = 0,
    val breakDurationSec: Int = 10
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
    val randomRadiusDp: Int = 24,
    val intervalJitterPercent: Int = 0,
    val durationJitterMs: Int = 0,
    val breakEveryMinutes: Int = 0,
    val breakDurationSec: Int = 10
)

@Serializable
data class ClickPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mode: ClickMode,
    val timing: ClickTimingConfig,
    val targets: List<TargetPoint>,
    val scenarioSteps: List<ScenarioStep> = emptyList(),
    val boundPackageName: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Serializable
data class PresetShareBundle(
    val preset: ClickPreset,
    val imagesBase64: Map<String, String> = emptyMap()
)

data class RecordedTouchEvent(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val durationMs: Long,
    val delayAfterMs: Long
)

@Serializable
data class AppSettings(
    val themeOption: ThemeOption = ThemeOption.SYSTEM,
    val themeColorStyle: ThemeColorStyle = ThemeColorStyle.DEFAULT,
    val customKeyColorHex: Long? = 0xFF3482FF,
    val navBarStyle: NavBarStyle = NavBarStyle.MIUIX,
    val targetSizeDp: Int = 46,
    val targetOpacityPercent: Int = 92,
    val showAntiBotZoneGuide: Boolean = true,
    val isOverlayLocked: Boolean = false,
    val keepScreenOnWhileRunning: Boolean = false,
    val maxRunMinutes: Int = 0,
    val batteryStopPercent: Int = 0,
    val volumeKeyControlEnabled: Boolean = false,
    val autoSwitchPresetByApp: Boolean = false
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
    val scenarioConfig: ClickTimingConfig = ClickTimingConfig(),
    val scenarioSteps: List<ScenarioStep> = emptyList(),
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
    val currentScenarioStepOrder: Int = 1,
    val lastDetectScore: Int? = null,
    val controlPanelX: Int = 24,
    val controlPanelY: Int = 240
)
