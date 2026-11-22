package com.bttrcal.app.ui.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.bounceClick(
    enabled: Boolean = true,
    scaleTarget: Float = 0.96f,
    haptic: Boolean = false,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) scaleTarget else 1f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounce_scale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null
        ) {
            if (haptic) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            onClick()
        }
}

@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalBttrPalette.current

    Box(modifier = modifier.background(palette.background)) {
        if (palette.isGlass) {
            val transition = rememberInfiniteTransition(label = "glass_ambient")
            val drift by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 16000),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "mesh_drift"
            )

            Canvas(modifier = Modifier.matchParentSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(palette.meshStart, palette.meshMid, palette.meshEnd),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )

                val orb1Center = Offset(
                    x = size.width * (0.22f + 0.14f * drift),
                    y = size.height * (0.14f + 0.06f * drift)
                )
                val orb2Center = Offset(
                    x = size.width * (0.82f - 0.16f * drift),
                    y = size.height * (0.48f + 0.08f * drift)
                )
                val orb3Center = Offset(
                    x = size.width * (0.35f + 0.15f * drift),
                    y = size.height * 0.88f
                )

                val orb1Color = if (palette.isDark) Color(0x360A84FF) else Color(0x4270B5FF)
                val orb2Color = if (palette.isDark) Color(0x305E5CE6) else Color(0x38BA9CFF)
                val orb3Color = if (palette.isDark) Color(0x2830B0C7) else Color(0x387CE8F5)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(orb1Color, Color.Transparent),
                        center = orb1Center,
                        radius = size.width * 0.65f
                    ),
                    center = orb1Center,
                    radius = size.width * 0.65f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(orb2Color, Color.Transparent),
                        center = orb2Center,
                        radius = size.width * 0.6f
                    ),
                    center = orb2Center,
                    radius = size.width * 0.6f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(orb3Color, Color.Transparent),
                        center = orb3Center,
                        radius = size.width * 0.55f
                    ),
                    center = orb3Center,
                    radius = size.width * 0.55f
                )
            }
        }
        content()
    }
}

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    elevated: Boolean = false,
    padding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalBttrPalette.current
    val shape: Shape = RoundedCornerShape(cornerRadius)

    val fillBrush = if (palette.isGlass) {
        val topAlpha = if (elevated) {
            if (palette.isDark) 0.78f else 0.94f
        } else {
            if (palette.isDark) 0.56f else 0.84f
        }
        val bottomAlpha = if (elevated) {
            if (palette.isDark) 0.66f else 0.86f
        } else {
            if (palette.isDark) 0.42f else 0.72f
        }
        val base = if (palette.isDark) Color(0xFF1A253A) else Color(0xFFFFFFFF)
        Brush.linearGradient(
            colors = listOf(base.copy(alpha = topAlpha), base.copy(alpha = bottomAlpha)),
            start = Offset(0f, 0f),
            end = Offset(500f, 600f)
        )
    } else {
        val solid = if (elevated) palette.surfaceElevated else palette.surface
        Brush.verticalGradient(listOf(solid, solid))
    }

    val borderStroke = if (palette.isGlass) {
        BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    palette.specularHighlight.copy(alpha = if (palette.isDark) 0.34f else 0.92f),
                    palette.border.copy(alpha = if (palette.isDark) 0.12f else 0.45f)
                )
            )
        )
    } else {
        BorderStroke(0.75.dp, palette.border)
    }

    val shadowElevation = when {
        palette.isGlass && !palette.isDark -> 8.dp
        !palette.isDark && elevated -> 6.dp
        !palette.isDark -> 2.dp
        else -> 0.dp
    }

    val baseModifier = modifier
        .then(if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier)
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            ambientColor = Color(0x1F0F172A),
            spotColor = Color(0x1A0F172A)
        )
        .clip(shape)
        .background(brush = fillBrush, shape = shape)
        .border(border = borderStroke, shape = shape)
        .padding(padding)

    Box(modifier = baseModifier, content = content)
}
