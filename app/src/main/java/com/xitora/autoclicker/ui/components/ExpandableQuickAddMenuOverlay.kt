package com.xitora.autoclicker.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.xitora.autoclicker.model.NavBarStyle
import com.xitora.autoclicker.ui.theme.LocalIsDarkTheme
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class QuickMenuAction(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val onClick: () -> Unit
)

@Composable
fun QuickAddMenuScrim(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 210f),
        label = "quickMenuScrim"
    )
    if (!visible && progress <= 0.01f) return

    BackHandler(enabled = visible) { onDismiss() }

    val isDark = LocalIsDarkTheme.current
    val scrimAlpha = (if (isDark) 0.56f else 0.34f) * progress
    val dismissInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = dismissInteraction,
                indication = null,
                onClick = onDismiss
            )
    )
}

@Composable
fun BoxScope.ExpandableQuickAddMenuOverlay(
    visible: Boolean,
    navBarStyle: NavBarStyle,
    actions: List<QuickMenuAction>,
    onDismiss: () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = if (visible) 0.72f else 0.84f,
            stiffness = if (visible) 185f else 210f
        ),
        label = "quickMenuMorph"
    )
    if (!visible && progress <= 0.01f) return

    val isDark = LocalIsDarkTheme.current
    val expandedSurface = if (isDark) MiuixTheme.colorScheme.surfaceContainerHigh else Color.White

    val expandedWidth = 304.dp
    val rowHeight = 58.dp
    val expandedHeight = (rowHeight * actions.size) + 16.dp

    val startWidth: Dp
    val startHeight: Dp
    val startBottomOffset: Dp
    val endBottomOffset: Dp
    val startCorner: Dp
    val cardBg: Color

    when (navBarStyle) {
        NavBarStyle.LIQUID_GLASS -> {
            startWidth = 50.dp
            startHeight = 44.dp
            startBottomOffset = 22.dp
            endBottomOffset = 88.dp
            startCorner = 22.dp
            val colorBlend = ((progress - 0.05f) / 0.55f).coerceIn(0f, 1f)
            cardBg = lerp(MiuixTheme.colorScheme.primary, expandedSurface, colorBlend)
        }
        NavBarStyle.MIUIX -> {
            startWidth = 84.dp
            startHeight = 36.dp
            startBottomOffset = 36.dp
            endBottomOffset = 80.dp
            startCorner = 18.dp
            cardBg = expandedSurface
        }
    }

    val currentWidth = lerp(startWidth, expandedWidth, progress)
    val currentHeight = lerp(startHeight, expandedHeight, progress)
    val currentBottomOffset = lerp(startBottomOffset, endBottomOffset, progress)
    val currentCorner = lerp(startCorner, 24.dp, progress)
    val contentAlpha = ((progress - 0.30f) / 0.70f).coerceIn(0f, 1f)
    val borderColor = MiuixTheme.colorScheme.onSurface.copy(alpha = (if (isDark) 0.14f else 0.08f) * contentAlpha)

    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = currentBottomOffset)
            .width(currentWidth)
            .height(currentHeight)
            .dropShadow(
                shape = RoundedCornerShape(currentCorner),
                shadow = Shadow(
                    radius = 22.dp * progress,
                    color = Color.Black,
                    alpha = (if (isDark) 0.42f else 0.16f) * progress
                )
            )
            .squircleClip(currentCorner)
            .squircleBackground(cardBg, currentCorner)
            .squircleBorder(0.75.dp, borderColor, currentCorner)
    ) {
        if (contentAlpha > 0.01f) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(contentAlpha)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                actions.forEachIndexed { index, item ->
                    QuickMenuActionRow(
                        action = item,
                        onClick = {
                            onDismiss()
                            item.onClick()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                    )
                    if (index < actions.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickMenuActionRow(
    action: QuickMenuAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiuixGlassIcon(
            icon = action.icon,
            contentDescription = null,
            accentColor = action.accentColor,
            size = 38.dp,
            iconSize = 19.dp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = action.title,
                style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold),
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = action.subtitle,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp)
        )
    }
}
