package com.example.dueordie.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PrimaryForestLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondarySlateLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    tertiary = WarningLight,
    onTertiary = OnWarningLight,
    tertiaryContainer = WarningContainerLight,
    onTertiaryContainer = OnWarningContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimarySageDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondarySlateDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    tertiary = WarningDark,
    onTertiary = OnWarningDark,
    tertiaryContainer = WarningContainerDark,
    onTertiaryContainer = OnWarningContainerDark
)

private val AmoledColorScheme = darkColorScheme(
    primary = PrimarySageDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = Color(0xFF1B2C23),
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondarySlateDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = Color(0xFF131D17),
    onSecondaryContainer = OnSecondaryContainerDark,
    background = AmoledBackground,
    onBackground = OnBackgroundDark,
    surface = AmoledSurface,
    onSurface = OnSurfaceDark,
    surfaceVariant = Color(0xFF121A15),
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = AmoledOutline,
    outlineVariant = Color(0xFF1A261E),
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    tertiary = WarningDark,
    onTertiary = OnWarningDark,
    tertiaryContainer = WarningContainerDark,
    onTertiaryContainer = OnWarningContainerDark
)

@Composable
fun DueOrDieTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoledBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        amoledBlack -> AmoledColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
