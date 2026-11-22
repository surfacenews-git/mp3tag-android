package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = SignalAmber,
    onPrimary = OnSignalAmber,
    primaryContainer = SignalAmberContainer,
    onPrimaryContainer = SignalAmberLight,
    secondary = StudioCobalt,
    onSecondary = Color(0xFF071933),
    secondaryContainer = StudioCobaltContainer,
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = StudioEmerald,
    onTertiary = Color(0xFF022C22),
    tertiaryContainer = StudioEmeraldContainer,
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = StudioObsidian,
    onBackground = StudioTextPrimary,
    surface = StudioSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioSurfaceHigh,
    onSurfaceVariant = StudioTextSecondary,
    surfaceContainerLowest = StudioObsidian,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceHigh,
    surfaceContainerHigh = StudioSurfaceHighest,
    outline = StudioBorder,
    outlineVariant = Color(0xFF222938),
    error = StudioCoral,
    onError = Color(0xFF3F0B10),
    errorContainer = StudioCoralContainer,
    onErrorContainer = Color(0xFFFEE2E2)
)

private val StudioLightColorScheme = lightColorScheme(
    primary = ArchivalAmber,
    onPrimary = Color.White,
    primaryContainer = ArchivalAmberContainer,
    onPrimaryContainer = Color(0xFF78350F),
    secondary = ArchivalCobalt,
    onSecondary = Color.White,
    secondaryContainer = ArchivalCobaltContainer,
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),
    background = ArchivalPaper,
    onBackground = ArchivalTextPrimary,
    surface = ArchivalCard,
    onSurface = ArchivalTextPrimary,
    surfaceVariant = ArchivalSurfaceLow,
    onSurfaceVariant = ArchivalTextSecondary,
    surfaceContainerLowest = ArchivalCard,
    surfaceContainerLow = ArchivalPaper,
    surfaceContainer = ArchivalSurfaceLow,
    surfaceContainerHigh = ArchivalSurfaceHigh,
    outline = ArchivalBorder,
    outlineVariant = Color(0xFFE5E1D8),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

val Mp3tagShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

@Composable
fun Mp3tagTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> StudioDarkColorScheme
        else -> StudioLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Mp3tagTypography,
        shapes = Mp3tagShapes,
        content = content
    )
}
