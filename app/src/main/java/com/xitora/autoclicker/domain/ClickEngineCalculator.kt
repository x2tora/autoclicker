package com.xitora.autoclicker.domain

import com.xitora.autoclicker.model.ClickTimingConfig
import com.xitora.autoclicker.model.RepeatMode
import com.xitora.autoclicker.model.ScenarioStep
import com.xitora.autoclicker.model.ScrollDirection
import com.xitora.autoclicker.model.TargetPoint
import com.xitora.autoclicker.model.TimeUnitOption
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object ClickEngineCalculator {
    const val MIN_INTERVAL_MS = 10L
    const val MIN_DURATION_MS = 10L
    const val MAX_DURATION_MS = 60_000L
    const val MAX_TIME_VALUE = 86_400_000L

    /**
     * Converts a numeric value + [TimeUnitOption] into milliseconds safely without overflow.
     */
    fun toMillis(value: Long, unit: TimeUnitOption): Long {
        val safeVal = value.coerceAtLeast(1L)
        val mul = unit.toMsMultiplier
        return if (safeVal > Long.MAX_VALUE / mul) {
            Long.MAX_VALUE / 2L
        } else {
            safeVal * mul
        }
    }

    fun resolveIntervalMs(value: Long, unit: TimeUnitOption): Long {
        return toMillis(value, unit).coerceAtLeast(MIN_INTERVAL_MS)
    }

    fun resolveDurationMs(value: Long, unit: TimeUnitOption): Long {
        return toMillis(value, unit).coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
    }

    fun resolveRepeatDurationMs(config: ClickTimingConfig): Long {
        return toMillis(config.repeatDurationValue, config.repeatDurationUnit).coerceAtLeast(100L)
    }

    /**
     * Computes the actual screen coordinate to tap.
     * When [antiBotEnabled] is true, samples uniformly inside a disk of radius [radiusPx]
     * around ([xPx], [yPx]) using polar sqrt(u) sampling, then clamps inside the screen bounds.
     */
    fun computeClickCoordinate(
        xPx: Float,
        yPx: Float,
        antiBotEnabled: Boolean,
        radiusPx: Float,
        screenWidth: Int,
        screenHeight: Int,
        random: Random = Random.Default
    ): Pair<Float, Float> {
        val maxW = (screenWidth - 2).coerceAtLeast(1).toFloat()
        val maxH = (screenHeight - 2).coerceAtLeast(1).toFloat()

        if (!antiBotEnabled || radiusPx <= 0.5f) {
            return xPx.coerceIn(1f, maxW) to yPx.coerceIn(1f, maxH)
        }

        val angle = random.nextDouble(0.0, 2.0 * PI)
        val r = radiusPx * sqrt(random.nextDouble(0.0, 1.0)).toFloat()
        val targetX = (xPx + r * cos(angle).toFloat()).coerceIn(1f, maxW)
        val targetY = (yPx + r * sin(angle).toFloat()).coerceIn(1f, maxH)
        return targetX to targetY
    }

    /**
     * Returns true when a running click loop should terminate based on [config].
     * Note: in Multi-Target mode, [executedCycles] counts completed target dispatches or cycles;
     * for [RepeatMode.COUNT], we check against completed cycles (or individual clicks in single mode).
     */
    fun shouldStopByRepeatRule(
        config: ClickTimingConfig,
        completedCycles: Long,
        elapsedMs: Long
    ): Boolean {
        return when (config.repeatMode) {
            RepeatMode.INFINITE -> false
            RepeatMode.COUNT -> completedCycles >= config.repeatCount.coerceAtLeast(1).toLong()
            RepeatMode.DURATION -> elapsedMs >= resolveRepeatDurationMs(config)
        }
    }

    fun formatElapsedMmSs(elapsedMs: Long): String {
        val totalSeconds = (elapsedMs.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatRepeatStatusBadge(config: ClickTimingConfig, elapsedMs: Long, completedCycles: Long): String {
        return when (config.repeatMode) {
            RepeatMode.INFINITE -> "Unlimited"
            RepeatMode.COUNT -> {
                val target = config.repeatCount.coerceAtLeast(1).toLong()
                val remaining = (target - completedCycles).coerceAtLeast(0L)
                "${remaining}회 남음"
            }
            RepeatMode.DURATION -> {
                val limit = resolveRepeatDurationMs(config)
                val remaining = (limit - elapsedMs).coerceAtLeast(0L)
                formatElapsedMmSs(remaining)
            }
        }
    }

    fun formatTimingSummary(value: Long, unit: TimeUnitOption): String {
        return "${value.coerceAtLeast(1L)}${unit.symbol}"
    }

    fun formatRepeatSummary(config: ClickTimingConfig): String {
        return when (config.repeatMode) {
            RepeatMode.INFINITE -> "멈출 때까지 계속"
            RepeatMode.COUNT -> "${config.repeatCount.coerceAtLeast(1)}회 반복"
            RepeatMode.DURATION -> "${config.repeatDurationValue.coerceAtLeast(1L)}${config.repeatDurationUnit.symbol} 동안"
        }
    }

    fun reindexTargets(targets: List<TargetPoint>): List<TargetPoint> {
        return targets.mapIndexed { idx, point -> point.copy(order = idx + 1) }
    }

    fun reindexScenarioSteps(steps: List<ScenarioStep>): List<ScenarioStep> {
        return steps.mapIndexed { idx, step -> step.copy(order = idx + 1) }
    }

    /**
     * Computes swipe start and end coordinates centered on ([centerX], [centerY]).
     * Note: scrolling down to reveal content below requires swiping the finger upward.
     */
    fun computeSwipePoints(
        centerX: Float,
        centerY: Float,
        direction: ScrollDirection,
        distancePx: Float,
        screenWidth: Int,
        screenHeight: Int
    ): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        val safeMinX = 8f
        val safeMaxX = (screenWidth - 8).coerceAtLeast(16).toFloat()
        val safeMinY = 24f
        val safeMaxY = (screenHeight - 24).coerceAtLeast(48).toFloat()
        val half = distancePx.coerceAtLeast(40f) / 2f

        val cx = centerX.coerceIn(safeMinX, safeMaxX)
        val cy = centerY.coerceIn(safeMinY, safeMaxY)

        val (startPair, endPair) = when (direction) {
            ScrollDirection.DOWN -> (cx to (cy + half)) to (cx to (cy - half))
            ScrollDirection.UP -> (cx to (cy - half)) to (cx to (cy + half))
            ScrollDirection.LEFT -> ((cx + half) to cy) to ((cx - half) to cy)
            ScrollDirection.RIGHT -> ((cx - half) to cy) to ((cx + half) to cy)
        }
        return (startPair.first.coerceIn(safeMinX, safeMaxX) to startPair.second.coerceIn(safeMinY, safeMaxY)) to
            (endPair.first.coerceIn(safeMinX, safeMaxX) to endPair.second.coerceIn(safeMinY, safeMaxY))
    }

    fun matchesTargetText(candidateText: CharSequence?, query: String): Boolean {
        if (candidateText.isNullOrBlank() || query.isBlank()) return false
        val tokens = query.split('|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return false
        val haystack = candidateText.toString()
        return tokens.any { token -> haystack.contains(token, ignoreCase = true) }
    }
}

/**
 * Edge-triggered detection guard for [com.xitora.autoclicker.model.ScenarioStepType.REPEAT_UNTIL_DETECT].
 * Requires at least one tap and at least one absent observation before firing so stale text/images
 * lingering from the end of the previous cycle never skip the infinite-tap step.
 */
class ScenarioDetectGuard {
    var seenAbsent: Boolean = false
        private set

    fun onConditionEvaluated(isPresent: Boolean, tapsInStep: Int): Boolean {
        if (!isPresent) {
            seenAbsent = true
            return false
        }
        return (seenAbsent && tapsInStep >= 1) || tapsInStep >= 3
    }
}
