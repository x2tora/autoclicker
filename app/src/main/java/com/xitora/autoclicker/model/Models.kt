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
    REPEAT_UNTIL_DETECT("반복 터치 + 조건 감지 대기", "🔄"),
    SINGLE_TAP("지정 위치 1회 클릭", ""),
    SCROLL("지정 위치 스크롤", "↕")
}

@Serializable
enum class DetectionConditionType(val label: String) {
    TEXT_OR_IMAGE("텍스트 또는 이미지 감지"),
    TEXT_ONLY("화면 텍스트만 감지"),
    IMAGE_ONLY("업로드 이미지만 감지")
}

@Serializable
enum class ScrollDirection(val label: String, val arrowSymbol: String) {
    DOWN("스크롤 내리기 (아래 내용 보기 · 위로 드래그)", "↓"),
    UP("스크롤 올리기 (위 내용 보기 · 아래로 드래그)", "↑"),
    LEFT("왼쪽으로 넘기기 (우→좌 드래그)", "←"),
    RIGHT("오른쪽으로 넘기기 (좌→우 드래그)", "→")
}

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
    val conditionType: DetectionConditionType = DetectionConditionType.TEXT_OR_IMAGE,
    val targetText: String = "",
    val targetImagePath: String? = null,
    val imageSimilarityPercent: Int = 85,
    val scrollDirection: ScrollDirection = ScrollDirection.DOWN,
    val scrollDistanceDp: Int = 340,
    val scrollDurationMs: Long = 360L,
    val postDelayMs: Long = 450L,
    val antiBotRandomEnabled: Boolean = false,
    val randomRadiusDp: Int = 18
) {
    fun badgeText(): String = when (type) {
        ScenarioStepType.REPEAT_UNTIL_DETECT -> "${order}🔄"
        ScenarioStepType.SINGLE_TAP -> order.toString()
        ScenarioStepType.SCROLL -> "${order}${scrollDirection.arrowSymbol}"
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
    val scenarioSteps: List<ScenarioStep> = emptyList(),
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
    val scenarioConfig: ClickTimingConfig = ClickTimingConfig(),
    val scenarioSteps: List<ScenarioStep> = defaultScenarioSteps(),
    val presets: List<ClickPreset> = emptyList(),
    val settings: AppSettings = AppSettings()
)

fun defaultMultiTargets(): List<TargetPoint> = listOf(
    TargetPoint(id = "multi_target_1", order = 1, xPx = 360f, yPx = 980f),
    TargetPoint(id = "multi_target_2", order = 2, xPx = 700f, yPx = 1220f)
)

fun defaultScenarioSteps(): List<ScenarioStep> = listOf(
    ScenarioStep(
        id = "scenario_step_1",
        order = 1,
        title = "반복 터치 · 텍스트/이미지 감지 대기",
        type = ScenarioStepType.REPEAT_UNTIL_DETECT,
        xPx = 320f,
        yPx = 920f,
        repeatIntervalMs = 120L,
        postDelayMs = 400L
    ),
    ScenarioStep(
        id = "scenario_step_2",
        order = 2,
        title = "감지 후 1번째 위치 클릭",
        type = ScenarioStepType.SINGLE_TAP,
        xPx = 540f,
        yPx = 760f,
        postDelayMs = 450L
    ),
    ScenarioStep(
        id = "scenario_step_3",
        order = 3,
        title = "2번째 위치 클릭",
        type = ScenarioStepType.SINGLE_TAP,
        xPx = 540f,
        yPx = 920f,
        postDelayMs = 450L
    ),
    ScenarioStep(
        id = "scenario_step_4",
        order = 4,
        title = "3번째 위치 클릭",
        type = ScenarioStepType.SINGLE_TAP,
        xPx = 540f,
        yPx = 1080f,
        postDelayMs = 500L
    ),
    ScenarioStep(
        id = "scenario_step_5",
        order = 5,
        title = "정해진 위치에서 스크롤 내리기",
        type = ScenarioStepType.SCROLL,
        xPx = 540f,
        yPx = 1240f,
        scrollDirection = ScrollDirection.DOWN,
        scrollDistanceDp = 340,
        scrollDurationMs = 360L,
        postDelayMs = 600L
    ),
    ScenarioStep(
        id = "scenario_step_6",
        order = 6,
        title = "스크롤 후 1번째 위치 클릭",
        type = ScenarioStepType.SINGLE_TAP,
        xPx = 540f,
        yPx = 980f,
        postDelayMs = 450L
    ),
    ScenarioStep(
        id = "scenario_step_7",
        order = 7,
        title = "스크롤 후 2번째 위치 클릭",
        type = ScenarioStepType.SINGLE_TAP,
        xPx = 540f,
        yPx = 1140f,
        postDelayMs = 450L
    ),
    ScenarioStep(
        id = "scenario_step_8",
        order = 8,
        title = "스크롤 후 3번째 위치 클릭",
        type = ScenarioStepType.SINGLE_TAP,
        xPx = 540f,
        yPx = 1300f,
        postDelayMs = 500L
    )
)

data class OverlayRuntimeState(
    val isAccessibilityEnabled: Boolean = false,
    val isOverlayVisible: Boolean = false,
    val activeMode: ClickMode = ClickMode.SINGLE,
    val isClicking: Boolean = false,
    val elapsedMs: Long = 0L,
    val clickCount: Long = 0L,
    val currentScenarioStepOrder: Int = 1,
    val controlPanelX: Int = 24,
    val controlPanelY: Int = 240
)
