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
    private val MULTI_SCALES = floatArrayOf(1.0f, 0.85f, 1.15f, 0.72f, 1.32f)

    data class TemplatePatch(
        val width: Int,
        val height: Int,
        val pixels: IntArray
    )

    private data class CachedTemplateSet(
        val key: String,
        val variants: List<TemplatePatch>
    )

    @Volatile
    private var cachedTemplateSet: CachedTemplateSet? = null

    fun hasValidDetectionTarget(step: ScenarioStep): Boolean {
        return !step.targetImagePath.isNullOrBlank()
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

                val variants = loadScaledTemplateVariants(file, sw, sh)
                if (variants.isEmpty()) return@withContext false

                var bestScore = 0
                for (tpl in variants) {
                    val adaptiveStride = if (min(tpl.width, tpl.height) >= 28) 2 else 1
                    val score = computeTemplateMatchScore(
                        screenPixels = screenPixels,
                        screenW = sw,
                        screenH = sh,
                        tplPixels = tpl.pixels,
                        tplW = tpl.width,
                        tplH = tpl.height,
                        stride = adaptiveStride
                    )
                    if (score > bestScore) {
                        bestScore = score
                    }
                    if (bestScore >= threshold) break
                }
                bestScore >= threshold
            } finally {
                screenBmp.recycle()
            }
        }
    }

    /**
     * Crops out the outer margin of a rectangular user screenshot (where animated backgrounds
     * around circular buttons or pill banners live) so matching focuses on the invariant inner icon/text core.
     */
    fun extractInnerCorePatch(pixels: IntArray, width: Int, height: Int): TemplatePatch {
        if (width < 20 || height < 20) {
            return TemplatePatch(width, height, pixels)
        }
        val trimX = (width * 0.17f).roundToInt().coerceAtLeast(2)
        val trimY = (height * 0.20f).roundToInt().coerceAtLeast(2)
        val newW = (width - trimX * 2).coerceAtLeast(8)
        val newH = (height - trimY * 2).coerceAtLeast(8)
        val out = IntArray(newW * newH)
        for (y in 0 until newH) {
            System.arraycopy(pixels, (y + trimY) * width + trimX, out, y * newW, newW)
        }
        return TemplatePatch(newW, newH, out)
    }

    private fun loadScaledTemplateVariants(file: File, maxW: Int, maxH: Int): List<TemplatePatch> {
        val key = "${file.absolutePath}:${file.lastModified()}:${maxW}x$maxH"
        cachedTemplateSet?.takeIf { it.key == key }?.let { return it.variants }

        val raw = BitmapFactory.decodeFile(file.absolutePath) ?: return emptyList()
        val variants = mutableListOf<TemplatePatch>()
        try {
            for (scaleFactor in MULTI_SCALES) {
                val desiredW = (raw.width * MATCH_SCALE * scaleFactor).roundToInt().coerceAtLeast(8)
                val desiredH = (raw.height * MATCH_SCALE * scaleFactor).roundToInt().coerceAtLeast(8)
                if (desiredW > maxW || desiredH > maxH) continue

                val scaled = Bitmap.createScaledBitmap(raw, desiredW, desiredH, true)
                val pixels = IntArray(desiredW * desiredH)
                scaled.getPixels(pixels, 0, desiredW, 0, 0, desiredW, desiredH)
                if (scaled !== raw) scaled.recycle()

                // Add inner core patch (ignores outer background corners around circles/pills)
                val core = extractInnerCorePatch(pixels, desiredW, desiredH)
                variants.add(core)
            }
        } finally {
            raw.recycle()
        }

        cachedTemplateSet = CachedTemplateSet(key = key, variants = variants)
        return variants
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
        stride: Int = 1
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

        // Precompute template luminance mean & variance to reject flat screen patches early
        var tplSum = 0.0
        val tplLums = DoubleArray(sampleCount)
        for (i in 0 until sampleCount) {
            val t = sampleRgb[i]
            val lum = ((t shr 16) and 0xFF) * 0.299 + ((t shr 8) and 0xFF) * 0.587 + (t and 0xFF) * 0.114
            tplLums[i] = lum
            tplSum += lum
        }
        val tplMean = tplSum / sampleCount
        var tplVar = 0.0
        for (i in 0 until sampleCount) {
            val d = tplLums[i] - tplMean
            tplVar += d * d
        }
        val tplHasStructure = (tplVar / sampleCount) >= 12.0

        // Keep top 4 spatially distinct coarse candidates so a single flat patch never hides the real match
        val topK = 4
        val topX = IntArray(topK)
        val topY = IntArray(topK)
        val topDiff = LongArray(topK) { Long.MAX_VALUE }
        val minSep = max(coarseStep * 2, min(tplW, tplH) / 2)

        var y = 0
        while (y <= maxY) {
            var x = 0
            while (x <= maxX) {
                var diff = 0L
                var minLum = 255.0
                var maxLum = 0.0
                val worstTopDiff = topDiff[topK - 1]
                for (i in 0 until sampleCount) {
                    val s = screenPixels[(y + sampleY[i]) * screenW + (x + sampleX[i])]
                    val sr = (s shr 16) and 0xFF
                    val sg = (s shr 8) and 0xFF
                    val sb = s and 0xFF
                    if (tplHasStructure) {
                        val sLum = sr * 0.299 + sg * 0.587 + sb * 0.114
                        if (sLum < minLum) minLum = sLum
                        if (sLum > maxLum) maxLum = sLum
                    }
                    val t = sampleRgb[i]
                    diff += abs(sr - ((t shr 16) and 0xFF)) +
                        abs(sg - ((t shr 8) and 0xFF)) +
                        abs(sb - (t and 0xFF))
                    if (diff >= worstTopDiff) break
                }

                // Skip completely flat screen regions when the template contains text/icon contrast
                if (tplHasStructure && (maxLum - minLum) < 10.0) {
                    x += coarseStep
                    continue
                }

                if (diff < topDiff[topK - 1]) {
                    var replaced = false
                    for (k in 0 until topK) {
                        if (topDiff[k] < Long.MAX_VALUE &&
                            abs(topX[k] - x) < minSep &&
                            abs(topY[k] - y) < minSep
                        ) {
                            if (diff < topDiff[k]) {
                                topDiff[k] = diff
                                topX[k] = x
                                topY[k] = y
                            }
                            replaced = true
                            break
                        }
                    }
                    if (!replaced) {
                        topDiff[topK - 1] = diff
                        topX[topK - 1] = x
                        topY[topK - 1] = y
                    }
                    // Keep topDiff sorted ascending
                    for (i in 0 until topK - 1) {
                        for (j in i + 1 until topK) {
                            if (topDiff[j] < topDiff[i]) {
                                val td = topDiff[i]; topDiff[i] = topDiff[j]; topDiff[j] = td
                                val tx = topX[i]; topX[i] = topX[j]; topX[j] = tx
                                val ty2 = topY[i]; topY[i] = topY[j]; topY[j] = ty2
                            }
                        }
                    }
                }
                x += coarseStep
            }
            y += coarseStep
        }

        // Local 1px refinement across all top coarse candidates using denser sampling
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
        val maxPossibleDiff = fineCount.toLong() * 3L * 255L
        if (maxPossibleDiff <= 0L) return 0

        var bestOverallScore = 0
        for (k in 0 until topK) {
            if (topDiff[k] == Long.MAX_VALUE) continue
            var bestFineX = topX[k]
            var bestFineY = topY[k]
            var bestFineDiff = Long.MAX_VALUE
            val minRefineX = (topX[k] - coarseStep).coerceAtLeast(0)
            val maxRefineX = (topX[k] + coarseStep).coerceAtMost(maxX)
            val minRefineY = (topY[k] - coarseStep).coerceAtLeast(0)
            val maxRefineY = (topY[k] + coarseStep).coerceAtMost(maxY)

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

            if (bestFineDiff == Long.MAX_VALUE) continue
            val errorPct = (bestFineDiff * 100L) / maxPossibleDiff
            val l1Score = (100L - errorPct).toInt().coerceIn(0, 100)

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
            val candidateScore = if (varT < 1.0) {
                l1Score
            } else if (varS < 1.0) {
                0
            } else {
                val zncc = (num / kotlin.math.sqrt(varT * varS)).coerceIn(0.0, 1.0)
                val znccPct = (zncc * 100.0).roundToInt().coerceIn(0, 100)
                if (znccPct < 45) 0 else ((l1Score * 0.35) + (znccPct * 0.65)).roundToInt().coerceIn(0, 100)
            }
            if (candidateScore > bestOverallScore) {
                bestOverallScore = candidateScore
            }
        }
        return bestOverallScore
    }
}
