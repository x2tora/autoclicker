package com.xitora.autoclicker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.xitora.autoclicker.ui.theme.AccentPresetPalette
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

@Composable
fun MiuixColorPickerDialog(
    show: Boolean,
    title: String = "색상 직접 지정",
    initialColorHex: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    onResetDynamic: (() -> Unit)? = null,
    resetDynamicLabel: String = "시스템 배경화면 다이나믹으로 되돌리기"
) {
    val initHsv = remember(show, initialColorHex) {
        FloatArray(3).also {
            android.graphics.Color.colorToHSV(Color(initialColorHex).toArgb(), it)
        }
    }
    var hue by remember(show, initialColorHex) { mutableFloatStateOf(initHsv[0]) }
    var sat by remember(show, initialColorHex) { mutableFloatStateOf(initHsv[1]) }
    var value by remember(show, initialColorHex) { mutableFloatStateOf(initHsv[2]) }
    var hexInput by remember(show, initialColorHex) {
        mutableStateOf(formatRgbHex(initialColorHex))
    }

    fun updateFromHsv(h: Float, s: Float, v: Float) {
        hue = h.coerceIn(0f, 360f)
        sat = s.coerceIn(0f, 1f)
        value = v.coerceIn(0f, 1f)
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
        val packed = (argb.toLong() and 0x00FFFFFFL) or 0xFF000000L
        hexInput = formatRgbHex(packed)
    }

    val previewColor = remember(hue, sat, value) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
    }
    val previewHexLong = remember(hue, sat, value) {
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
        (argb.toLong() and 0x00FFFFFFL) or 0xFF000000L
    }

    OverlayDialog(
        show = show,
        title = title,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .squircleBackground(previewColor, 12.dp)
                        .squircleBorder(1.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = 0.14f), 12.dp)
                )
                TextField(
                    value = hexInput,
                    onValueChange = { raw ->
                        val cleaned = raw.uppercase(Locale.US).filter { it in "0123456789ABCDEF#" }.take(7)
                        hexInput = cleaned
                        val digits = cleaned.removePrefix("#")
                        if (digits.length == 6) {
                            digits.toLongOrNull(16)?.let { rgb ->
                                val parsedArgb = (rgb or 0xFF000000L).toInt()
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(parsedArgb, hsv)
                                hue = hsv[0]
                                sat = hsv[1]
                                value = hsv[2]
                            }
                        }
                    },
                    label = "HEX 색상 코드 (#RRGGBB)",
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            val hueColor = remember(hue) {
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .squircleClip(12.dp)
                    .pointerInput(hue) {
                        detectTapGestures { pos ->
                            val s = (pos.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val v = 1f - (pos.y / size.height.toFloat()).coerceIn(0f, 1f)
                            updateFromHsv(hue, s, v)
                        }
                    }
                    .pointerInput(hue) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val s = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val v = 1f - (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                            updateFromHsv(hue, s, v)
                        }
                    }
            ) {
                drawRect(brush = Brush.horizontalGradient(colors = listOf(Color.White, hueColor)))
                drawRect(brush = Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black)))
                val pad = 11.dp.toPx()
                val cx = (sat * size.width).coerceIn(pad, (size.width - pad).coerceAtLeast(pad))
                val cy = ((1f - value) * size.height).coerceIn(pad, (size.height - pad).coerceAtLeast(pad))
                drawCircle(
                    color = Color.White,
                    radius = 9.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.5.dp.toPx())
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.55f),
                    radius = 10.5.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            val spectrumColors = remember {
                listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .squircleClip(8.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { pos ->
                            val nextHue = ((pos.x / size.width.toFloat()) * 360f).coerceIn(0f, 360f)
                            updateFromHsv(nextHue, sat, value)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val nextHue = ((change.position.x / size.width.toFloat()) * 360f).coerceIn(0f, 360f)
                            updateFromHsv(nextHue, sat, value)
                        }
                    }
            ) {
                drawRect(brush = Brush.horizontalGradient(colors = spectrumColors))
                val hxPad = 11.dp.toPx()
                val hx = ((hue / 360f) * size.width).coerceIn(hxPad, (size.width - hxPad).coerceAtLeast(hxPad))
                drawCircle(
                    color = Color.White,
                    radius = 9.dp.toPx(),
                    center = Offset(hx, size.height / 2f),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AccentPresetPalette) { (_, presetHex) ->
                    val active = (presetHex and 0x00FFFFFFL) == (previewHexLong and 0x00FFFFFFL)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(presetHex))
                            .then(
                                if (active) Modifier.border(2.dp, MiuixTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .clickable {
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(Color(presetHex).toArgb(), hsv)
                                updateFromHsv(hsv[0], hsv[1], hsv[2])
                            }
                    )
                }
            }

            if (onResetDynamic != null) {
                TextButton(
                    text = resetDynamicLabel,
                    onClick = {
                        onResetDynamic()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    text = "취소",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "색상 적용",
                    onClick = {
                        onConfirm(previewHexLong)
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

fun formatRgbHex(colorHex: Long): String {
    val rgb = colorHex and 0x00FFFFFFL
    return String.format(Locale.US, "#%06X", rgb)
}
