package com.xitora.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.xitora.autoclicker.domain.ClickEngineCalculator
import com.xitora.autoclicker.model.DetectionConditionType
import com.xitora.autoclicker.model.ScenarioStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Provides Accessibility UI text detection and Android 11+ screenshot template matching
 * for conditional Scenario steps.
 */
object ScreenDetector {
    private const val MAX_TRAVERSED_NODES = 1500
    private const val MAX_SAVED_IMAGE_DIM = 1080
    private const val MATCH_SCALE = 0.35f

    private data class CachedTemplate(
        val key: String,
        val width: Int,
        val height: Int,
        val pixels: IntArray
    )

    data class ScreenTextCandidate(
        val text: String,
        val bounds: Rect
    )

    @Volatile
    private var cachedTemplate: CachedTemplate? = null

    fun hasValidDetectionTarget(step: ScenarioStep): Boolean {
        return when (step.conditionType) {
            DetectionConditionType.TEXT_ONLY -> step.targetText.isNotBlank()
            DetectionConditionType.IMAGE_ONLY -> !step.targetImagePath.isNullOrBlank()
            DetectionConditionType.TEXT_OR_IMAGE ->
                step.targetText.isNotBlank() || !step.targetImagePath.isNullOrBlank()
        }
    }

    private fun collectTargetAppRoots(service: AccessibilityService): List<AccessibilityNodeInfo> {
        val ownPackage = service.packageName
        val activeRoots = mutableListOf<AccessibilityNodeInfo>()
        val fallbackRoots = mutableListOf<AccessibilityNodeInfo>()

        runCatching {
            service.windows?.forEach { window ->
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) return@forEach
                val root = window.root ?: return@forEach
                if (root.packageName?.toString() != ownPackage) {
                    if (window.isActive || window.isFocused) {
                        activeRoots.add(root)
                    } else {
                        fallbackRoots.add(root)
                    }
                }
            }
        }

        val roots = if (activeRoots.isNotEmpty()) activeRoots else fallbackRoots
        if (roots.isEmpty()) {
            runCatching {
                service.rootInActiveWindow?.let { activeRoot ->
                    if (activeRoot.packageName?.toString() != ownPackage) {
                        roots.add(activeRoot)
                    }
                }
            }
        }
        return roots
    }

    /**
     * Collects visible leaf/text nodes and their screen bounding rectangles from the target app window
     * (excluding full-window containers and system bars) so the user can tap or drag to select text.
     */
    fun collectScreenTextCandidates(service: AccessibilityService): List<ScreenTextCandidate> {
        val roots = collectTargetAppRoots(service)
        val dm = service.resources.displayMetrics
        val screenArea = (dm.widthPixels.toLong() * dm.heightPixels.toLong()).coerceAtLeast(1L)
        val maxCandidateArea = (screenArea * 0.28).toLong()

        val queue = ArrayDeque<AccessibilityNodeInfo>(roots)
        val results = mutableListOf<ScreenTextCandidate>()
        val seenKeys = HashSet<String>()
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_TRAVERSED_NODES) {
            val node = queue.removeFirst()
            visited++

            val childCount = runCatching { node.childCount }.getOrDefault(0)
            runCatching {
                if (node.isVisibleToUser) {
                    val directText = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    val leafDesc = if (childCount == 0) {
                        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    } else {
                        null
                    }
                    val rawText = directText ?: leafDesc
                    if (rawText != null && rawText.length <= 80) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        val area = rect.width().toLong() * rect.height().toLong()
                        if (rect.width() > 10 && rect.height() > 10 && area in 101L..maxCandidateArea) {
                            val key = "$rawText@${rect.left},${rect.top},${rect.right},${rect.bottom}"
                            if (seenKeys.add(key)) {
                                results.add(ScreenTextCandidate(text = rawText, bounds = rect))
                            }
                        }
                    }
                }
            }

            for (i in 0 until childCount) {
                val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                queue.addLast(child)
            }
        }

        // Sort smaller, more specific UI elements first so tapping inside a button hits the button text
        return results.sortedBy { it.bounds.width().toLong() * it.bounds.height().toLong() }
    }

    /**
     * Captures the current screen and crops [cropRect] into a saved template image file (Android 11+).
     */
    suspend fun captureAndCropTemplateImage(
        service: AccessibilityService,
        cropRect: Rect
    ): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val screenBmp = captureSoftwareScreenBitmap(service) ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val left = cropRect.left.coerceIn(0, (screenBmp.width - 16).coerceAtLeast(0))
                val top = cropRect.top.coerceIn(0, (screenBmp.height - 16).coerceAtLeast(0))
                val right = cropRect.right.coerceIn(left + 16, screenBmp.width)
                val bottom = cropRect.bottom.coerceIn(top + 16, screenBmp.height)
                val w = (right - left).coerceAtLeast(16)
                val h = (bottom - top).coerceAtLeast(16)
                if (left + w > screenBmp.width || top + h > screenBmp.height) return@withContext null

                val cropped = Bitmap.createBitmap(screenBmp, left, top, w, h)
                val dir = File(service.filesDir, "scenario_templates").apply { mkdirs() }
                val outFile = File(dir, "tpl_${System.currentTimeMillis()}.png")
                FileOutputStream(outFile).use { fos ->
                    cropped.compress(Bitmap.CompressFormat.PNG, 92, fos)
                }
                if (cropped !== screenBmp) cropped.recycle()
                outFile.absolutePath
            } catch (_: Throwable) {
                null
            } finally {
                screenBmp.recycle()
            }
        }
    }

    fun detectTextInService(service: AccessibilityService, query: String): Boolean {
        if (query.isBlank()) return false
        val roots = collectTargetAppRoots(service)

        val queue = ArrayDeque<AccessibilityNodeInfo>(roots)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_TRAVERSED_NODES) {
            val node = queue.removeFirst()
            visited++
            val childCount = runCatching { node.childCount }.getOrDefault(0)
            val matched = runCatching {
                if (!node.isVisibleToUser) return@runCatching false
                ClickEngineCalculator.matchesTargetText(node.text, query) ||
                    (childCount == 0 && ClickEngineCalculator.matchesTargetText(node.contentDescription, query))
            }.getOrDefault(false)
            if (matched) return true

            for (i in 0 until childCount) {
                val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                queue.addLast(child)
            }
        }
        return false
    }

    fun saveTemplateImageFromUri(context: Context, uri: Uri): String? {
        return runCatching {
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return null

            val maxDim = max(decoded.width, decoded.height)
            val toSave = if (maxDim > MAX_SAVED_IMAGE_DIM && maxDim > 0) {
                val ratio = MAX_SAVED_IMAGE_DIM.toFloat() / maxDim.toFloat()
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * ratio).roundToInt().coerceAtLeast(1),
                    (decoded.height * ratio).roundToInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }

            val dir = File(context.filesDir, "scenario_templates").apply { mkdirs() }
            val outFile = File(dir, "tpl_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { fos ->
                toSave.compress(Bitmap.CompressFormat.PNG, 92, fos)
            }
            if (toSave !== decoded) decoded.recycle()
            toSave.recycle()
            outFile.absolutePath
        }.getOrNull()
    }

    /**
     * Returns true/false when a screenshot was captured and compared, or null if screenshot capture failed.
     */
    suspend fun detectImageInService(
        service: AccessibilityService,
        imagePath: String?,
        similarityPercent: Int
    ): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || imagePath.isNullOrBlank()) return false
        val file = File(imagePath)
        if (!file.exists()) return false

        val threshold = similarityPercent.coerceIn(50, 99)
        val screenBmp = captureSoftwareScreenBitmap(service) ?: return null
        return withContext(Dispatchers.Default) {
            try {
                val sw = (screenBmp.width * MATCH_SCALE).roundToInt().coerceAtLeast(16)
                val sh = (screenBmp.height * MATCH_SCALE).roundToInt().coerceAtLeast(16)
                val scaledScreen = Bitmap.createScaledBitmap(screenBmp, sw, sh, true)
                val screenPixels = IntArray(sw * sh)
                scaledScreen.getPixels(screenPixels, 0, sw, 0, 0, sw, sh)
                if (scaledScreen !== screenBmp) scaledScreen.recycle()

                val tpl = loadScaledTemplate(file, sw, sh) ?: return@withContext false
                val score = computeTemplateMatchScore(
                    screenPixels = screenPixels,
                    screenW = sw,
                    screenH = sh,
                    tplPixels = tpl.pixels,
                    tplW = tpl.width,
                    tplH = tpl.height,
                    stride = 3
                )
                score >= threshold
            } finally {
                screenBmp.recycle()
            }
        }
    }

    private fun loadScaledTemplate(file: File, maxW: Int, maxH: Int): CachedTemplate? {
        val key = "${file.absolutePath}:${file.lastModified()}:${maxW}x$maxH"
        cachedTemplate?.takeIf { it.key == key }?.let { return it }

        val raw = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val desiredW = (raw.width * MATCH_SCALE).roundToInt().coerceAtLeast(4)
        val desiredH = (raw.height * MATCH_SCALE).roundToInt().coerceAtLeast(4)
        val fitScale = min(
            1f,
            min(maxW.toFloat() / desiredW.toFloat(), maxH.toFloat() / desiredH.toFloat())
        )
        val finalW = (desiredW * fitScale).roundToInt().coerceAtLeast(4).coerceAtMost(maxW)
        val finalH = (desiredH * fitScale).roundToInt().coerceAtLeast(4).coerceAtMost(maxH)

        val scaled = Bitmap.createScaledBitmap(raw, finalW, finalH, true)
        val pixels = IntArray(finalW * finalH)
        scaled.getPixels(pixels, 0, finalW, 0, 0, finalW, finalH)
        if (scaled !== raw) scaled.recycle()
        raw.recycle()

        return CachedTemplate(key = key, width = finalW, height = finalH, pixels = pixels).also {
            cachedTemplate = it
        }
    }

    private suspend fun captureSoftwareScreenBitmap(service: AccessibilityService): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return withTimeoutOrNull(1500L) {
            suspendCancellableCoroutine { cont ->
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    service.mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            val bmp = try {
                                val buffer = screenshot.hardwareBuffer
                                val wrapped = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                                val copy = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                                wrapped?.recycle()
                                copy
                            } finally {
                                runCatching { screenshot.hardwareBuffer.close() }
                            }
                            if (cont.isActive) cont.resume(bmp)
                        }

                        override fun onFailure(errorCode: Int) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                )
            }
        }
    }

    /**
     * Pure CPU two-stage template matcher returning best similarity percentage in [0..100].
     */
    fun computeTemplateMatchScore(
        screenPixels: IntArray,
        screenW: Int,
        screenH: Int,
        tplPixels: IntArray,
        tplW: Int,
        tplH: Int,
        stride: Int = 3
    ): Int {
        if (screenW <= 0 || screenH <= 0 || tplW <= 0 || tplH <= 0) return 0
        if (tplW > screenW || tplH > screenH) return 0
        if (screenPixels.size < screenW * screenH || tplPixels.size < tplW * tplH) return 0

        val sampleStep = max(1, min(tplW, tplH) / 14)
        val coarseXList = mutableListOf<Int>()
        val coarseYList = mutableListOf<Int>()
        val coarseColors = mutableListOf<Int>()
        var ty = 0
        while (ty < tplH) {
            var tx = 0
            while (tx < tplW) {
                val px = tplPixels[ty * tplW + tx]
                if ((px ushr 24) >= 32) {
                    coarseXList.add(tx)
                    coarseYList.add(ty)
                    coarseColors.add(px)
                }
                tx += sampleStep
            }
            ty += sampleStep
        }
        if (coarseColors.isEmpty()) return 0

        val sampleCount = coarseColors.size
        val sampleX = coarseXList.toIntArray()
        val sampleY = coarseYList.toIntArray()
        val sampleRgb = coarseColors.toIntArray()
        val maxX = screenW - tplW
        val maxY = screenH - tplH
        val coarseStep = stride.coerceAtLeast(1)

        var bestCoarseX = 0
        var bestCoarseY = 0
        var bestCoarseDiff = Long.MAX_VALUE

        var y = 0
        while (y <= maxY) {
            var x = 0
            while (x <= maxX) {
                var diff = 0L
                for (i in 0 until sampleCount) {
                    val s = screenPixels[(y + sampleY[i]) * screenW + (x + sampleX[i])]
                    val t = sampleRgb[i]
                    diff += abs(((s shr 16) and 0xFF) - ((t shr 16) and 0xFF)) +
                        abs(((s shr 8) and 0xFF) - ((t shr 8) and 0xFF)) +
                        abs((s and 0xFF) - (t and 0xFF))
                    if (diff >= bestCoarseDiff) break
                }
                if (diff < bestCoarseDiff) {
                    bestCoarseDiff = diff
                    bestCoarseX = x
                    bestCoarseY = y
                }
                x += coarseStep
            }
            y += coarseStep
        }

        // Local 1px refinement around the coarse winner using denser sampling
        val fineStep = if (tplW * tplH <= 2400) 1 else 2
        val fineXList = mutableListOf<Int>()
        val fineYList = mutableListOf<Int>()
        val fineColors = mutableListOf<Int>()
        var fty = 0
        while (fty < tplH) {
            var ftx = 0
            while (ftx < tplW) {
                val px = tplPixels[fty * tplW + ftx]
                if ((px ushr 24) >= 32) {
                    fineXList.add(ftx)
                    fineYList.add(fty)
                    fineColors.add(px)
                }
                ftx += fineStep
            }
            fty += fineStep
        }
        val fineCount = fineColors.size
        val fineX = fineXList.toIntArray()
        val fineY = fineYList.toIntArray()
        val fineRgb = fineColors.toIntArray()

        var bestFineX = bestCoarseX
        var bestFineY = bestCoarseY
        var bestFineDiff = Long.MAX_VALUE
        val minRefineX = (bestCoarseX - coarseStep).coerceAtLeast(0)
        val maxRefineX = (bestCoarseX + coarseStep).coerceAtMost(maxX)
        val minRefineY = (bestCoarseY - coarseStep).coerceAtLeast(0)
        val maxRefineY = (bestCoarseY + coarseStep).coerceAtMost(maxY)

        for (ry in minRefineY..maxRefineY) {
            for (rx in minRefineX..maxRefineX) {
                var diff = 0L
                for (i in 0 until fineCount) {
                    val s = screenPixels[(ry + fineY[i]) * screenW + (rx + fineX[i])]
                    val t = fineRgb[i]
                    diff += abs(((s shr 16) and 0xFF) - ((t shr 16) and 0xFF)) +
                        abs(((s shr 8) and 0xFF) - ((t shr 8) and 0xFF)) +
                        abs((s and 0xFF) - (t and 0xFF))
                    if (diff >= bestFineDiff) break
                }
                if (diff < bestFineDiff) {
                    bestFineDiff = diff
                    bestFineX = rx
                    bestFineY = ry
                }
            }
        }

        val maxPossibleDiff = fineCount.toLong() * 3L * 255L
        if (maxPossibleDiff <= 0L || bestFineDiff == Long.MAX_VALUE) return 0
        val errorPct = (bestFineDiff * 100L) / maxPossibleDiff
        val l1Score = (100L - errorPct).toInt().coerceIn(0, 100)

        // Structural ZNCC check prevents plain/solid backgrounds from falsely scoring 85%+ against UI buttons with text.
        var sumT = 0.0
        var sumS = 0.0
        for (i in 0 until fineCount) {
            val t = fineRgb[i]
            val s = screenPixels[(bestFineY + fineY[i]) * screenW + (bestFineX + fineX[i])]
            sumT += ((t shr 16) and 0xFF) * 0.299 + ((t shr 8) and 0xFF) * 0.587 + (t and 0xFF) * 0.114
            sumS += ((s shr 16) and 0xFF) * 0.299 + ((s shr 8) and 0xFF) * 0.587 + (s and 0xFF) * 0.114
        }
        val meanT = sumT / fineCount
        val meanS = sumS / fineCount
        var num = 0.0
        var varT = 0.0
        var varS = 0.0
        for (i in 0 until fineCount) {
            val t = fineRgb[i]
            val s = screenPixels[(bestFineY + fineY[i]) * screenW + (bestFineX + fineX[i])]
            val lumT = ((t shr 16) and 0xFF) * 0.299 + ((t shr 8) and 0xFF) * 0.587 + (t and 0xFF) * 0.114 - meanT
            val lumS = ((s shr 16) and 0xFF) * 0.299 + ((s shr 8) and 0xFF) * 0.587 + (s and 0xFF) * 0.114 - meanS
            num += lumT * lumS
            varT += lumT * lumT
            varS += lumS * lumS
        }
        if (varT < 1.0) {
            return l1Score
        }
        if (varS < 1.0) {
            return 0
        }
        val zncc = (num / kotlin.math.sqrt(varT * varS)).coerceIn(0.0, 1.0)
        val znccPct = (zncc * 100.0).roundToInt().coerceIn(0, 100)
        return min(l1Score, znccPct)
    }
}
