package com.xitora.autoclicker

import com.xitora.autoclicker.data.AutoClickerRepository
import com.xitora.autoclicker.domain.ClickEngineCalculator
import com.xitora.autoclicker.domain.ScenarioDetectGuard
import com.xitora.autoclicker.model.*
import com.xitora.autoclicker.service.ScreenDetector
import com.xitora.autoclicker.update.AppUpdater
import com.xitora.autoclicker.update.ReleaseApkAsset
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class ClickEngineCalculatorTest {

    @Test
    fun `time unit conversions handle ms s m h accurately`() {
        assertEquals(250L, ClickEngineCalculator.toMillis(250L, TimeUnitOption.MS))
        assertEquals(3_000L, ClickEngineCalculator.toMillis(3L, TimeUnitOption.SEC))
        assertEquals(120_000L, ClickEngineCalculator.toMillis(2L, TimeUnitOption.MIN))
        assertEquals(7_200_000L, ClickEngineCalculator.toMillis(2L, TimeUnitOption.HOUR))

        // Minimum safe clamping
        assertEquals(ClickEngineCalculator.MIN_INTERVAL_MS, ClickEngineCalculator.resolveIntervalMs(0L, TimeUnitOption.MS))
        assertEquals(ClickEngineCalculator.MIN_DURATION_MS, ClickEngineCalculator.resolveDurationMs(1L, TimeUnitOption.MS))
        assertEquals(ClickEngineCalculator.MAX_DURATION_MS, ClickEngineCalculator.resolveDurationMs(2L, TimeUnitOption.MIN))
    }

    @Test
    fun `repeat termination rules obey infinite count and duration`() {
        val inf = ClickTimingConfig(repeatMode = RepeatMode.INFINITE)
        assertFalse(ClickEngineCalculator.shouldStopByRepeatRule(inf, 999_999L, 999_999L))

        val cnt = ClickTimingConfig(repeatMode = RepeatMode.COUNT, repeatCount = 15)
        assertFalse(ClickEngineCalculator.shouldStopByRepeatRule(cnt, 14L, 1_000L))
        assertTrue(ClickEngineCalculator.shouldStopByRepeatRule(cnt, 15L, 1_000L))

        val dur = ClickTimingConfig(
            repeatMode = RepeatMode.DURATION,
            repeatDurationValue = 5L,
            repeatDurationUnit = TimeUnitOption.SEC
        )
        assertFalse(ClickEngineCalculator.shouldStopByRepeatRule(dur, 100L, 4_999L))
        assertTrue(ClickEngineCalculator.shouldStopByRepeatRule(dur, 100L, 5_000L))
    }

    @Test
    fun `anti-bot random sampling stays inside radius and screen bounds`() {
        val rng = Random(42)
        val cx = 500f
        val cy = 900f
        val radius = 60f
        repeat(250) {
            val (tx, ty) = ClickEngineCalculator.computeClickCoordinate(
                xPx = cx,
                yPx = cy,
                antiBotEnabled = true,
                radiusPx = radius,
                screenWidth = 1080,
                screenHeight = 2400,
                random = rng
            )
            assertTrue(hypot((tx - cx).toDouble(), (ty - cy).toDouble()) <= radius + 0.01)
            assertTrue(tx in 1f..1078f)
            assertTrue(ty in 1f..2398f)
        }

        // Disabled mode returns exact coordinate clamped
        val (exactX, exactY) = ClickEngineCalculator.computeClickCoordinate(
            xPx = cx,
            yPx = cy,
            antiBotEnabled = false,
            radiusPx = radius,
            screenWidth = 1080,
            screenHeight = 2400,
            random = rng
        )
        assertEquals(cx, exactX, 0.001f)
        assertEquals(cy, exactY, 0.001f)
    }

    @Test
    fun `elapsed time and repeat badge formatting match overlay conventions`() {
        assertEquals("00:14", ClickEngineCalculator.formatElapsedMmSs(14_000L))
        assertEquals("05:03", ClickEngineCalculator.formatElapsedMmSs(303_000L))
        assertEquals("01:02:03", ClickEngineCalculator.formatElapsedMmSs(3_723_000L))

        val inf = ClickTimingConfig(repeatMode = RepeatMode.INFINITE)
        assertEquals("Unlimited", ClickEngineCalculator.formatRepeatStatusBadge(inf, 14_000L, 16L))

        val cnt = ClickTimingConfig(repeatMode = RepeatMode.COUNT, repeatCount = 50)
        assertEquals("34회 남음", ClickEngineCalculator.formatRepeatStatusBadge(cnt, 5_000L, 16L))
    }

    @Test
    fun `state serialization round trip preserves single multi presets and settings`() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val original = AutoClickerDataState(
            singleConfig = ClickTimingConfig(
                intervalValue = 2L,
                intervalUnit = TimeUnitOption.SEC,
                durationValue = 80L,
                durationUnit = TimeUnitOption.MS,
                repeatMode = RepeatMode.COUNT,
                repeatCount = 42,
                antiBotRandomEnabled = true,
                randomRadiusDp = 36
            ),
            presets = listOf(
                ClickPreset(
                    id = "p1",
                    name = "테스트 프리셋",
                    mode = ClickMode.MULTI,
                    timing = ClickTimingConfig(),
                    targets = defaultMultiTargets()
                )
            ),
            settings = AppSettings(
                themeOption = ThemeOption.DARK,
                themeColorStyle = ThemeColorStyle.CUSTOM,
                customKeyColorHex = 0xFF10B981,
                navBarStyle = NavBarStyle.LIQUID_GLASS
            )
        )
        val encoded = json.encodeToString(AutoClickerDataState.serializer(), original)
        val decoded = json.decodeFromString(AutoClickerDataState.serializer(), encoded)
        assertEquals(original, decoded)
        assertEquals(NavBarStyle.LIQUID_GLASS, decoded.settings.resolvedNavBarStyle())
    }

    @Test
    fun `legacy json without navBarStyle decodes cleanly to MIUIX default`() {
        val json = Json { ignoreUnknownKeys = true }
        val legacyJson = """{"themeOption":"SYSTEM","themeColorStyle":"DEFAULT"}"""
        val decoded = json.decodeFromString(AppSettings.serializer(), legacyJson)
        assertEquals(NavBarStyle.MIUIX, decoded.resolvedNavBarStyle())
    }

    @Test
    fun `app updater version comparison and abi asset matching`() {
        assertTrue(AppUpdater.isNewerVersion("1.0.1", "1.0.0"))
        assertTrue(AppUpdater.isNewerVersion("v1.1.0", "1.0.5"))
        assertFalse(AppUpdater.isNewerVersion("1.0.0", "1.0.0"))

        val assets = listOf(
            ReleaseApkAsset("autoclicker-v1.0.0-armeabi-v7a.apk", "https://example.com/v7a.apk", 100L),
            ReleaseApkAsset("autoclicker-v1.0.0-arm64-v8a.apk", "https://example.com/arm64.apk", 100L),
            ReleaseApkAsset("autoclicker-v1.0.0-universal.apk", "https://example.com/univ.apk", 100L)
        )
        val (pickedArm64, abiLabel) = AppUpdater.selectBestApkAsset(assets, listOf("arm64-v8a", "armeabi-v7a"))!!
        assertEquals("autoclicker-v1.0.0-arm64-v8a.apk", pickedArm64.name)
        assertEquals("arm64-v8a", abiLabel)

        val (pickedFallback, fallbackLabel) = AppUpdater.selectBestApkAsset(assets, listOf("mips64"))!!
        assertEquals("autoclicker-v1.0.0-universal.apk", pickedFallback.name)
        assertEquals("universal", fallbackLabel)
    }

    @Test
    fun `default 8 step scenario workflow and serialization round trip behave cleanly`() {
        val steps = defaultScenarioSteps()
        assertEquals(8, steps.size)
        assertEquals(ScenarioStepType.REPEAT_UNTIL_DETECT, steps[0].type)
        assertEquals("1🔄", steps[0].badgeText())
        assertEquals(ScenarioStepType.SINGLE_TAP, steps[1].type)
        assertEquals(ScenarioStepType.SINGLE_TAP, steps[2].type)
        assertEquals(ScenarioStepType.SINGLE_TAP, steps[3].type)
        assertEquals(ScenarioStepType.SCROLL, steps[4].type)
        assertEquals("5↓", steps[4].badgeText())
        assertEquals(ScenarioStepType.SINGLE_TAP, steps[5].type)
        assertEquals(ScenarioStepType.SINGLE_TAP, steps[6].type)
        assertEquals(ScenarioStepType.SINGLE_TAP, steps[7].type)

        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val updatedSteps = steps.mapIndexed { idx, s ->
            if (idx == 0) s.copy(targetText = "완료|확인", targetImagePath = "/tmp/tpl.png") else s
        }
        val state = AutoClickerDataState(
            scenarioSteps = updatedSteps,
            presets = listOf(
                ClickPreset(
                    id = "sc_p1",
                    name = "시나리오 프리셋",
                    mode = ClickMode.SCENARIO,
                    timing = ClickTimingConfig(repeatMode = RepeatMode.COUNT, repeatCount = 10),
                    targets = emptyList(),
                    scenarioSteps = updatedSteps
                )
            )
        )
        val encoded = json.encodeToString(AutoClickerDataState.serializer(), state)
        val decoded = json.decodeFromString(AutoClickerDataState.serializer(), encoded)
        assertEquals(state, decoded)
    }

    @Test
    fun `scroll swipe coordinates and text keyword matching stay accurate`() {
        val (downStart, downEnd) = ClickEngineCalculator.computeSwipePoints(
            centerX = 500f,
            centerY = 1000f,
            direction = ScrollDirection.DOWN,
            distancePx = 400f,
            screenWidth = 1080,
            screenHeight = 2400
        )
        assertEquals(500f, downStart.first, 0.01f)
        assertEquals(1200f, downStart.second, 0.01f)
        assertEquals(500f, downEnd.first, 0.01f)
        assertEquals(800f, downEnd.second, 0.01f)

        val (upStart, upEnd) = ClickEngineCalculator.computeSwipePoints(
            centerX = 500f,
            centerY = 1000f,
            direction = ScrollDirection.UP,
            distancePx = 400f,
            screenWidth = 1080,
            screenHeight = 2400
        )
        assertTrue(upStart.second < upEnd.second)

        assertTrue(ClickEngineCalculator.matchesTargetText("보상 획득 완료!", "완료 | 수령"))
        assertFalse(ClickEngineCalculator.matchesTargetText("진행 중", "완료 | 수령"))

        val unconfiguredStep = defaultScenarioSteps().first()
        assertFalse(ScreenDetector.hasValidDetectionTarget(unconfiguredStep))
        assertTrue(ScreenDetector.hasValidDetectionTarget(unconfiguredStep.copy(targetText = "보상 받기")))
        assertTrue(ScreenDetector.hasValidDetectionTarget(unconfiguredStep.copy(targetImagePath = "/tmp/tpl.png")))
    }

    @Test
    fun `template matcher detects exact patch and rejects mismatch`() {
        val screenW = 40
        val screenH = 40
        val screen = IntArray(screenW * screenH) { 0xFF102030.toInt() }
        val tplW = 10
        val tplH = 10
        val tpl = IntArray(tplW * tplH)
        for (y in 0 until tplH) {
            for (x in 0 until tplW) {
                val color = (0xFF000000 or ((x * 25).toLong() shl 16) or ((y * 25).toLong() shl 8) or 180L).toInt()
                tpl[y * tplW + x] = color
                screen[(13 + y) * screenW + (17 + x)] = color
            }
        }
        val exactScore = ScreenDetector.computeTemplateMatchScore(screen, screenW, screenH, tpl, tplW, tplH)
        assertEquals(100, exactScore)

        val darkScreen = IntArray(screenW * screenH) { 0xFF000000.toInt() }
        val whiteTpl = IntArray(tplW * tplH) { 0xFFFFFFFF.toInt() }
        val mismatchScore = ScreenDetector.computeTemplateMatchScore(darkScreen, screenW, screenH, whiteTpl, tplW, tplH)
        assertTrue("Expected low mismatch score but got $mismatchScore", mismatchScore < 20)
    }

    @Test
    fun `scenario detect guard ignores stale condition until absent then triggers on reappearance`() {
        val guard = ScenarioDetectGuard()
        // Even if condition is still present right as cycle 2 starts, do not fire before seeing absent
        assertFalse(guard.onConditionEvaluated(isPresent = true, tapsInStep = 0))
        assertFalse(guard.onConditionEvaluated(isPresent = true, tapsInStep = 1))
        assertFalse(guard.seenAbsent)

        // Old popup/text disappears
        assertFalse(guard.onConditionEvaluated(isPresent = false, tapsInStep = 2))
        assertTrue(guard.seenAbsent)

        // New text appears after tapping -> triggers stop of repeat-tap step
        assertTrue(guard.onConditionEvaluated(isPresent = true, tapsInStep = 3))
    }

    @Test
    fun `single preset export and import creates fresh id and rejects full backup json`() {
        val original = ClickPreset(
            id = "preset-orig-id",
            name = "공유용 시나리오",
            mode = ClickMode.SCENARIO,
            timing = ClickTimingConfig(repeatMode = RepeatMode.COUNT, repeatCount = 5),
            targets = emptyList(),
            scenarioSteps = defaultScenarioSteps()
        )
        val json = AutoClickerRepository.encodeSinglePresetJson(original)
        val imported = AutoClickerRepository.parseSinglePresetJson(json).getOrThrow()

        assertNotEquals(original.id, imported.id)
        assertEquals(original.name, imported.name)
        assertEquals(ClickMode.SCENARIO, imported.mode)
        assertEquals(8, imported.scenarioSteps.size)
        assertEquals(5, imported.timing.repeatCount)

        // Full backup JSON should be rejected cleanly by single preset importer
        val invalidResult = AutoClickerRepository.parseSinglePresetJson("""{"singleConfig":{},"presets":[]}""")
        assertTrue(invalidResult.isFailure)
    }
}
