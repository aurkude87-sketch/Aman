package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val QuantDarkColorScheme = darkColorScheme(
    primary = EmeraldBull,
    onPrimary = Color(0xFF002110),
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldOnContainer,
    secondary = CyanIndicator,
    onSecondary = Color(0xFF002026),
    secondaryContainer = CyanContainer,
    onSecondaryContainer = CyanOnContainer,
    tertiary = AmberWarning,
    onTertiary = Color(0xFF281B00),
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberOnContainer,
    error = CrimsonBear,
    onError = Color(0xFF3A0005),
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonOnContainer,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = ObsidianSurface,
    surfaceContainerHigh = ObsidianSurfaceVariant,
    surfaceContainerHighest = ObsidianElevated,
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF1E2B44)
)

private val QuantLightColorScheme = lightColorScheme(
    primary = LightEmerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F7DB),
    onPrimaryContainer = Color(0xFF002110),
    secondary = LightCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC4F1F9),
    onSecondaryContainer = Color(0xFF001F24),
    tertiary = Color(0xFF9A6700),
    onTertiary = Color.White,
    error = LightCrimson,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0xFFB8C7DC)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun PipGuardTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) QuantDarkColorScheme else QuantLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
