package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class VoltLabExtendedColors(
    val isDark: Boolean,
    val canvasBackground: Color,
    val gridMinor: Color,
    val gridMajor: Color,
    val wireIdle: Color,
    val componentStroke: Color,
    val selectionGlow: Color,
    val terminalDot: Color,
    val terminalHighlight: Color,
    val probeCh1: Color,
    val probeCh2: Color,
    val probeCh3: Color,
    val probeCh4: Color
)

private val DarkExtended = VoltLabExtendedColors(
    isDark = true,
    canvasBackground = LabBgDeep,
    gridMinor = LabGridMinor,
    gridMajor = LabGridMajor,
    wireIdle = Color(0xFF7B8CA3),
    componentStroke = Color(0xFFE6EDF3),
    selectionGlow = LabCyan,
    terminalDot = Color(0xFF94A3B8),
    terminalHighlight = LabAmber,
    probeCh1 = ScopeCh1Color,
    probeCh2 = ScopeCh2Color,
    probeCh3 = ScopeCh3Color,
    probeCh4 = ScopeCh4Color
)

private val LightExtended = VoltLabExtendedColors(
    isDark = false,
    canvasBackground = BlueprintBg,
    gridMinor = BlueprintGridMinor,
    gridMajor = BlueprintGridMajor,
    wireIdle = Color(0xFF475569),
    componentStroke = Color(0xFF0F172A),
    selectionGlow = BlueprintAccent,
    terminalDot = Color(0xFF334155),
    terminalHighlight = LabAmber,
    probeCh1 = Color(0xFF0284C7),
    probeCh2 = Color(0xFFD97706),
    probeCh3 = Color(0xFF059669),
    probeCh4 = Color(0xFFE11D48)
)

val LocalVoltLabColors = staticCompositionLocalOf { DarkExtended }

private val DarkLabScheme = darkColorScheme(
    primary = LabCyan,
    onPrimary = Color(0xFF00242B),
    primaryContainer = Color(0xFF0C3542),
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = LabAmber,
    onSecondary = Color(0xFF2B1900),
    secondaryContainer = Color(0xFF3F2805),
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = LabEmerald,
    onTertiary = Color(0xFF00291B),
    tertiaryContainer = Color(0xFF083828),
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = LabBgDeep,
    onBackground = LabTextPrimary,
    surface = LabSurface,
    onSurface = LabTextPrimary,
    surfaceVariant = LabSurfaceElevated,
    onSurfaceVariant = LabTextSecondary,
    surfaceContainerHigh = LabSurfaceHighest,
    outline = LabBorder,
    outlineVariant = LabGridMajor,
    error = LabRose,
    onError = Color.White
)

private val LightBlueprintScheme = lightColorScheme(
    primary = BlueprintAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF075985),
    secondary = Color(0xFFD97706),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF92400E),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = BlueprintBg,
    onBackground = BlueprintTextPrimary,
    surface = BlueprintSurface,
    onSurface = BlueprintTextPrimary,
    surfaceVariant = BlueprintSurfaceElevated,
    onSurfaceVariant = BlueprintTextSecondary,
    outline = BlueprintBorder,
    error = Color(0xFFE11D48),
    onError = Color.White
)

@Composable
fun VoltLabTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkLabScheme else LightBlueprintScheme
    val extended = if (darkTheme) DarkExtended else LightExtended

    CompositionLocalProvider(LocalVoltLabColors provides extended) {
        MaterialTheme(
            colorScheme = scheme,
            typography = VoltLabTypography,
            content = content
        )
    }
}
