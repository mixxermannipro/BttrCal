package com.bttrcal.app.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class BttrHaptics(private val view: View) {
    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm() {
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
        view.performHapticFeedback(code)
    }

    fun heavy() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

@Composable
fun rememberBttrHaptics(): BttrHaptics {
    val view = LocalView.current
    return remember(view) { BttrHaptics(view) }
}

fun Modifier.bounceClick(
    enabled: Boolean = true,
    scaleTarget: Float = 0.94f,
    haptic: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) scaleTarget else 1f,
        animationSpec = spring(
            dampingRatio = 0.56f,
            stiffness = 440f
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
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            onClick()
        }
}

@Composable
fun BouncyEntrance(
    modifier: Modifier = Modifier,
    delayIndex: Int = 0,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(delayIndex == 0) }
    LaunchedEffect(delayIndex) {
        if (delayIndex > 0) {
            kotlinx.coroutines.delay((delayIndex.coerceAtMost(6) * 28L))
        }
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
        ) + slideInVertically(
            initialOffsetY = { (it * 0.14f).toInt().coerceAtLeast(18) },
            animationSpec = spring(dampingRatio = 0.65f, stiffness = 360f)
        ) + scaleIn(
            initialScale = 0.95f,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = 360f)
        )
    ) {
        content()
    }
}

@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalBttrPalette.current
    Box(
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(palette.meshStart, palette.meshMid, palette.meshEnd)
            )
        ),
        content = content
    )
}

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    elevated: Boolean = false,
    padding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalBttrPalette.current
    val shape: Shape = RoundedCornerShape(cornerRadius)

    val fillBrush = if (palette.isDark) {
        val topColor = if (elevated) Color(0xFF212227) else Color(0xFF17181B)
        val bottomColor = if (elevated) Color(0xFF17181C) else Color(0xFF121316)
        Brush.linearGradient(
            colors = listOf(topColor.copy(alpha = 0.94f), bottomColor.copy(alpha = 0.90f)),
            start = Offset(0f, 0f),
            end = Offset(450f, 650f)
        )
    } else {
        val topColor = if (elevated) Color(0xFAFFFFFF) else Color(0xF5FFFFFF)
        val bottomColor = if (elevated) Color(0xEEF7F8FC) else Color(0xF0FFFFFF)
        Brush.linearGradient(
            colors = listOf(topColor, bottomColor),
            start = Offset(0f, 0f),
            end = Offset(450f, 650f)
        )
    }

    val borderStroke = if (palette.isDark) {
        BorderStroke(
            width = 0.85.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0x36FFFFFF),
                    Color(0x10FFFFFF)
                )
            )
        )
    } else {
        BorderStroke(
            width = 0.85.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFFFFF),
                    palette.border
                )
            )
        )
    }

    val shadowElevation = when {
        !palette.isDark && elevated -> 6.dp
        !palette.isDark -> 2.dp
        else -> 0.dp
    }

    val baseModifier = modifier
        .then(if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier)
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            ambientColor = Color(0x18000000),
            spotColor = Color(0x14000000)
        )
        .clip(shape)
        .background(brush = fillBrush, shape = shape)
        .border(border = borderStroke, shape = shape)
        .padding(padding)

    Box(modifier = baseModifier, content = content)
}
