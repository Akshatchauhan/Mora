package com.example.dueordie.ui.theme

import androidx.compose.ui.graphics.Color

// Figma "Productivity" Mindful Sage Palette
// Light scheme
val PrimaryForestLight = Color(0xFF315C47)         // Deep forest sage/jade
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFEAF0E7)    // Soft mint/sage mist
val OnPrimaryContainerLight = Color(0xFF21362C)

val SecondarySlateLight = Color(0xFF667269)      // Mindful slate sage
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFDEE4DA)  // Subtle container
val OnSecondaryContainerLight = Color(0xFF21362C)

val BackgroundLight = Color(0xFFF7F7F1)          // Warm soothing cream / matcha stone
val OnBackgroundLight = Color(0xFF21362C)        // Deep dark forest ink
val SurfaceLight = Color(0xFFFFFFFF)             // Pure crisp white card surface
val OnSurfaceLight = Color(0xFF21362C)
val SurfaceVariantLight = Color(0xFFEAF0E7)
val OnSurfaceVariantLight = Color(0xFF667269)    // Muted helper text
val OutlineLight = Color(0xFFDEE4DA)             // Soft border divider
val OutlineVariantLight = Color(0xFFE5ECE2)

val ErrorLight = Color(0xFFB91C1C)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFEE2E2)
val OnErrorContainerLight = Color(0xFF7F1D1D)
val WarningLight = Color(0xFF9A4D00)
val OnWarningLight = Color(0xFFFFFFFF)
val WarningContainerLight = Color(0xFFFFF0D6)
val OnWarningContainerLight = Color(0xFF422006)

// Dark scheme (Figma Frame 23:117 "Tasks - active, dark")
val PrimarySageDark = Color(0xFF9CD2B0)          // Luminous pale sage
val OnPrimaryDark = Color(0xFF121A15)
val PrimaryContainerDark = Color(0xFF2A4132)     // Deep moss container
val OnPrimaryContainerDark = Color(0xFFECF3ED)

val SecondarySlateDark = Color(0xFFB0C2B5)       // Pale sage slate
val OnSecondaryDark = Color(0xFF121A15)
val SecondaryContainerDark = Color(0xFF223126)
val OnSecondaryContainerDark = Color(0xFFECF3ED)

val BackgroundDark = Color(0xFF121A15)           // Deep pine black
val OnBackgroundDark = Color(0xFFECF3ED)         // Soft white
val SurfaceDark = Color(0xFF1B261F)              // Deep spruce card surface
val OnSurfaceDark = Color(0xFFECF3ED)
val SurfaceVariantDark = Color(0xFF243329)
val OnSurfaceVariantDark = Color(0xFFB0C2B5)
val OutlineDark = Color(0xFF3A5040)              // Subtle forest border
val OutlineVariantDark = Color(0xFF26362C)

val ErrorDark = Color(0xFFFCA5A5)
val OnErrorDark = Color(0xFF450A0A)
val ErrorContainerDark = Color(0xFF7F1D1D)
val OnErrorContainerDark = Color(0xFFFEE2E2)
val WarningDark = Color(0xFFF2C078)
val OnWarningDark = Color(0xFF2F1C06)
val WarningContainerDark = Color(0xFF4A3517)
val OnWarningContainerDark = Color(0xFFFFE8B5)

// AMOLED scheme (Figma Frame 23:154 "Tasks - active, AMOLED")
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF0D1310)
val AmoledOutline = Color(0xFF233227)

// Compatibility aliases for older UI code.
val PrimaryBlueLight = PrimaryForestLight
val PrimaryBlueDark = PrimarySageDark

// Semantic status accents
val SemanticSuccessGreen = Color(0xFF315C47)
val SemanticWarningAmber = Color(0xFFB45309)

// Legacy compatibility aliases
val GlassBackground = BackgroundDark
val GlassSurface = SurfaceDark
val GlassSurfaceVariant = SurfaceVariantDark
val GlassBorder = OutlineVariantDark
val LiquidCyan = PrimaryBlueLight
val LiquidPurple = SecondarySlateLight
val LiquidPink = PrimaryBlueLight
val LiquidRed = ErrorLight
val LiquidGreen = SemanticSuccessGreen
val TextPrimary = OnBackgroundLight
val TextSecondary = OnSurfaceVariantLight
