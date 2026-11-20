package com.xitora.autoclicker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.xitora.autoclicker.ui.theme.LocalIsDarkTheme
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

data class QuickMenuAction(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val onClick: () -> Unit
)

@Composable
fun ExpandableQuickAddMenuOverlay(
    morphProgress: Float,
    interactive: Boolean,
    isLiquidGlass: Boolean,
    actions: List<QuickMenuAction>,
    onDismiss: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val density = LocalDensity.current
    val raw = morphProgress.coerceAtLeast(0f)
    val clamped = raw.coerceIn(0f, 1f)
    val liftProgress = smoothStep(0f, 0.72f, clamped) + (raw - clamped) * 0.35f
    val sizeProgress = smoothStep(0.02f, 0.85f, clamped) + (raw - clamped) * 0.18f
    val colorBlend = smoothStep(0.06f, 0.58f, clamped)
    val contentFade = smoothStep(0.32f, 0.84f, clamped)

    val surfaceCardColor = if (isDark) MiuixTheme.colorScheme.surfaceContainerHigh else Color.White
    val cardColor = if (isLiquidGlass) {
        lerp(
            MiuixTheme.colorScheme.primary,
            surfaceCardColor,
            colorBlend
        )
    } else {
        surfaceCardColor.copy(alpha = smoothStep(0f, 0.30f, clamped))
    }
    val borderColor = MiuixTheme.colorScheme.onSurface.copy(
        alpha = lerpFloat(0f, if (isDark) 0.12f else 0.08f, clamped)
    )
    val bottomOffset = lerp(
        if (isLiquidGlass) 22.dp else 36.dp,
        if (isLiquidGlass) 88.dp else 80.dp,
        liftProgress.coerceAtLeast(0f)
    )
    val cornerRadius = lerp(
        if (isLiquidGlass) 22.dp else 18.dp,
        26.dp,
        clamped
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        val targetWidthDp = (maxWidth - 40.dp).coerceIn(264.dp, 340.dp)
        val targetWidthPx = with(density) { targetWidthDp.roundToPx() }
        val minWidthPx = with(density) { (if (isLiquidGlass) 50.dp else 84.dp).roundToPx() }
        val minHeightPx = with(density) { (if (isLiquidGlass) 44.dp else 36.dp).roundToPx() }
        val cardShape = RoundedCornerShape(cornerRadius)

        Box(
            modifier = Modifier
                .padding(bottom = bottomOffset)
                .shadow(
                    elevation = lerpFloat(2f, 22f, clamped).dp,
                    shape = cardShape,
                    ambientColor = Color.Black.copy(alpha = lerpFloat(0.08f, 0.28f, clamped)),
                    spotColor = Color.Black.copy(alpha = lerpFloat(0.10f, 0.32f, clamped))
                )
                .clip(cardShape)
                .background(cardColor)
                .squircleBorder(1.dp, borderColor, cornerRadius)
                .pointerInput(interactive) {
                    if (interactive) {
                        detectTapGestures { }
                    }
                }
                .layout { measurable, constraints ->
                    val childConstraints = constraints.copy(
                        minWidth = targetWidthPx,
                        maxWidth = targetWidthPx,
                        minHeight = 0
                    )
                    val placeable = measurable.measure(childConstraints)
                    val curW = lerpFloat(minWidthPx.toFloat(), targetWidthPx.toFloat(), sizeProgress)
                        .roundToInt()
                        .coerceAtLeast(1)
                    val curH = lerpFloat(minHeightPx.toFloat(), placeable.height.toFloat(), sizeProgress)
                        .roundToInt()
                        .coerceAtLeast(1)
                    layout(curW, curH) {
                        placeable.placeRelative(
                            x = (curW - placeable.width) / 2,
                            y = (curH - placeable.height) / 2
                        )
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .width(targetWidthDp)
                    .padding(horizontal = 8.dp, vertical = 10.dp)
                    .graphicsLayer {
                        alpha = contentFade
                    }
            ) {
                Text(
                    text = "빠른 실행",
                    style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 6.dp)
                )
                actions.forEachIndexed { index, action ->
                    val revealStart = 0.28f + index * 0.06f
                    QuickAddMorphActionRow(
                        icon = action.icon,
                        iconTint = action.accentColor,
                        title = action.title,
                        subtitle = action.subtitle,
                        revealProgress = smoothStep(revealStart, (revealStart + 0.50f).coerceAtMost(0.96f), clamped),
                        enabled = interactive,
                        onClick = {
                            onDismiss()
                            action.onClick()
                        }
                    )
                    if (index < actions.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAddMorphActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    revealProgress: Float,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .graphicsLayer {
                alpha = revealProgress
                translationY = (1f - revealProgress) * with(density) { 12.dp.toPx() }
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(26.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.headline2.copy(fontWeight = FontWeight.SemiBold),
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.size(16.dp)
        )
    }
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
    if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
