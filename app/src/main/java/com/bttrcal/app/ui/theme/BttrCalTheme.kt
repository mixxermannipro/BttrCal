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
    buildPalette(ThemeMode.LIQUID_GLASS, GlassVariant.LIGHT_GLASS)
}

fun buildPalette(mode: ThemeMode, glassVariant: GlassVariant): BttrPalette {
    return when (mode) {
        ThemeMode.LIGHT -> BttrPalette(
            themeMode = mode,
            glassVariant = glassVariant,
            isDark = false,
            isGlass = false,
            background = Color(0xFFF5F6FA),
            surface = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFFFFFFF),
            surfaceSubtle = Color(0xFFECEFF5),
            border = Color(0xFFE2E6EE),
            specularHighlight = Color(0xFFFFFFFF),
            textPrimary = Color(0xFF0F172A),
            textSecondary = Color(0xFF526075),
            textTertiary = Color(0xFF8C97A8),
            accent = Color(0xFF0A84FF),
            accentSoft = Color(0x180A84FF),
            danger = Color(0xFFFF3B30),
            success = Color(0xFF34C759),
            warning = Color(0xFFFF9500),
            gridLine = Color(0xFFE9EDF4),
            meshStart = Color(0xFFF5F6FA),
            meshMid = Color(0xFFF5F6FA),
            meshEnd = Color(0xFFF5F6FA)
        )
        ThemeMode.DARK -> BttrPalette(
            themeMode = mode,
            glassVariant = glassVariant,
            isDark = true,
            isGlass = false,
            background = Color(0xFF090B10),
            surface = Color(0xFF141720),
            surfaceElevated = Color(0xFF1C202C),
            surfaceSubtle = Color(0xFF1F2433),
            border = Color(0xFF282E40),
            specularHighlight = Color(0x28FFFFFF),
            textPrimary = Color(0xFFF4F6FA),
            textSecondary = Color(0xFFA0ABBE),
            textTertiary = Color(0xFF697489),
            accent = Color(0xFF0A84FF),
            accentSoft = Color(0x260A84FF),
            danger = Color(0xFFFF453A),
            success = Color(0xFF30D158),
            warning = Color(0xFFFF9F0A),
            gridLine = Color(0xFF1E2331),
            meshStart = Color(0xFF090B10),
            meshMid = Color(0xFF090B10),
            meshEnd = Color(0xFF090B10)
        )
        ThemeMode.LIQUID_GLASS -> if (glassVariant == GlassVariant.DARK_GLASS) {
            BttrPalette(
                themeMode = mode,
                glassVariant = glassVariant,
                isDark = true,
                isGlass = true,
                background = Color(0xFF0B111E),
                surface = Color(0x8C172033),
                surfaceElevated = Color(0xB31D2840),
                surfaceSubtle = Color(0x66222F4A),
                border = Color(0x38FFFFFF),
                specularHighlight = Color(0x58FFFFFF),
                textPrimary = Color(0xFFF8FAFF),
                textSecondary = Color(0xFFB5C2D9),
                textTertiary = Color(0xFF7B8BA8),
                accent = Color(0xFF389BFF),
                accentSoft = Color(0x30389BFF),
                danger = Color(0xFFFF5247),
                success = Color(0xFF32D760),
                warning = Color(0xFFFFA726),
                gridLine = Color(0x22FFFFFF),
                meshStart = Color(0xFF0C182E),
                meshMid = Color(0xFF151936),
                meshEnd = Color(0xFF0A2230)
            )
        } else {
            BttrPalette(
                themeMode = mode,
                glassVariant = glassVariant,
                isDark = false,
                isGlass = true,
                background = Color(0xFFEDF3FC),
                surface = Color(0xB8FFFFFF),
                surfaceElevated = Color(0xDEFFFFFF),
                surfaceSubtle = Color(0x88EBF2FF),
                border = Color(0xBFFFFFFF),
                specularHighlight = Color(0xFFFFFFFF),
                textPrimary = Color(0xFF0D1526),
                textSecondary = Color(0xFF475670),
                textTertiary = Color(0xFF7F8EA6),
                accent = Color(0xFF0A84FF),
                accentSoft = Color(0x1E0A84FF),
                danger = Color(0xFFFF3B30),
                success = Color(0xFF28CD41),
                warning = Color(0xFFFF9500),
                gridLine = Color(0x336C85A8),
                meshStart = Color(0xFFE4EEFF),
                meshMid = Color(0xFFF3ECFF),
                meshEnd = Color(0xFFE0F7FA)
            )
        }
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
        fontSize = 16.sp,
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
            surface = animatedPalette.surfaceElevated,
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
            surface = animatedPalette.surfaceElevated,
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
