package com.bttrcal.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bttrcal.app.domain.model.GlassVariant
import com.bttrcal.app.domain.model.ThemeMode

@Immutable
data class BttrPalette(
    val themeMode: ThemeMode,
    val glassVariant: GlassVariant,
    val isDark: Boolean,
    val isGlass: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceSubtle: Color,
    val border: Color,
    val specularHighlight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentSoft: Color,
    val danger: Color,
    val success: Color,
    val warning: Color,
    val gridLine: Color,
    val meshStart: Color,
    val meshMid: Color,
    val meshEnd: Color
)

val LocalBttrPalette = staticCompositionLocalOf {
    buildPalette(ThemeMode.DARK, GlassVariant.DARK_GLASS)
}

fun buildPalette(mode: ThemeMode, glassVariant: GlassVariant): BttrPalette {
    val effectiveDark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.LIQUID_GLASS -> glassVariant == GlassVariant.DARK_GLASS
    }

    return if (effectiveDark) {
        BttrPalette(
            themeMode = ThemeMode.DARK,
            glassVariant = glassVariant,
            isDark = true,
            isGlass = true,
            background = Color(0xFF000000),
            surface = Color(0xFF141416),
            surfaceElevated = Color(0xEC1B1C20),
            surfaceSubtle = Color(0xFF25262B),
            border = Color(0x26FFFFFF),
            specularHighlight = Color(0x42FFFFFF),
            textPrimary = Color(0xFFF5F6FA),
            textSecondary = Color(0xFF9EA2AD),
            textTertiary = Color(0xFF6B6F7B),
            accent = Color(0xFFFF4F38),
            accentSoft = Color(0x26FF4F38),
            danger = Color(0xFFFF453A),
            success = Color(0xFF30D158),
            warning = Color(0xFFFF9F0A),
            gridLine = Color(0xFF222328),
            meshStart = Color(0xFF000000),
            meshMid = Color(0xFF040508),
            meshEnd = Color(0xFF000000)
        )
    } else {
        BttrPalette(
            themeMode = ThemeMode.LIGHT,
            glassVariant = glassVariant,
            isDark = false,
            isGlass = true,
            background = Color(0xFFF4F5F8),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xF5FFFFFF),
            surfaceSubtle = Color(0xFFEBEDF2),
            border = Color(0xFFE0E4EC),
            specularHighlight = Color(0xFFFFFFFF),
            textPrimary = Color(0xFF0E131D),
            textSecondary = Color(0xFF555D6E),
            textTertiary = Color(0xFF8C93A4),
            accent = Color(0xFFFF3B30),
            accentSoft = Color(0x1AFF3B30),
            danger = Color(0xFFFF3B30),
            success = Color(0xFF34C759),
            warning = Color(0xFFFF9500),
            gridLine = Color(0xFFE7EAF0),
            meshStart = Color(0xFFF4F5F8),
            meshMid = Color(0xFFF7F8FB),
            meshEnd = Color(0xFFF2F4F8)
        )
    }
}

private val BttrTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontSize = 15.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.15).sp
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Normal
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium
    )
)

@Composable
fun BttrCalTheme(
    themeMode: ThemeMode,
    glassVariant: GlassVariant,
    content: @Composable () -> Unit
) {
    val target = buildPalette(themeMode, glassVariant)
    val animSpec = spring<Color>(stiffness = Spring.StiffnessMediumLow)

    val bg by animateColorAsState(target.background, animSpec, label = "bg")
    val surface by animateColorAsState(target.surface, animSpec, label = "surface")
    val surfaceElev by animateColorAsState(target.surfaceElevated, animSpec, label = "surfaceElev")
    val textPrimary by animateColorAsState(target.textPrimary, animSpec, label = "textPrimary")
    val accent by animateColorAsState(target.accent, animSpec, label = "accent")

    val animatedPalette = target.copy(
        background = bg,
        surface = surface,
        surfaceElevated = surfaceElev,
        textPrimary = textPrimary,
        accent = accent
    )

    val m3Scheme = if (animatedPalette.isDark) {
        darkColorScheme(
            primary = animatedPalette.accent,
            onPrimary = Color.White,
            background = animatedPalette.background,
            onBackground = animatedPalette.textPrimary,
            surface = animatedPalette.surface,
            onSurface = animatedPalette.textPrimary,
            surfaceVariant = animatedPalette.surfaceSubtle,
            onSurfaceVariant = animatedPalette.textSecondary,
            outline = animatedPalette.border,
            error = animatedPalette.danger
        )
    } else {
        lightColorScheme(
            primary = animatedPalette.accent,
            onPrimary = Color.White,
            background = animatedPalette.background,
            onBackground = animatedPalette.textPrimary,
            surface = animatedPalette.surface,
            onSurface = animatedPalette.textPrimary,
            surfaceVariant = animatedPalette.surfaceSubtle,
            onSurfaceVariant = animatedPalette.textSecondary,
            outline = animatedPalette.border,
            error = animatedPalette.danger
        )
    }

    CompositionLocalProvider(LocalBttrPalette provides animatedPalette) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = BttrTypography,
            content = content
        )
    }
}
