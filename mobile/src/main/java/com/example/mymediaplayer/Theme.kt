package com.example.mymediaplayer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color


private val LcarsBlack = Color(0xFF0D0D0D)
private val LcarsDarkPurple = Color(0xFF1A1025)
private val LcarsOrange = Color(0xFFFF9900)
private val LcarsCyan = Color(0xFF00CCCC)
private val LcarsPurple = Color(0xFF9966CC)
private val LcarsHotPink = Color(0xFFFF4488)
private val LcarsWarmWhite = Color(0xFFFFEECC)
private val LcarsDimWhite = Color(0xFFBBAA99)
private val LcarsSurface = Color(0xFF221133)
private val LcarsSurfaceVariant = Color(0xFF332244)
private val LcarsLightBg = Color(0xFFFFF8F0)
private val LcarsLightOrange = Color(0xFFCC6600)

fun lcarsDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = LcarsOrange,
    onPrimary = LcarsBlack,
    primaryContainer = LcarsSurfaceVariant,
    onPrimaryContainer = LcarsOrange,
    secondary = LcarsCyan,
    onSecondary = LcarsBlack,
    secondaryContainer = LcarsSurface,
    onSecondaryContainer = LcarsCyan,
    tertiary = LcarsPurple,
    onTertiary = Color.White,
    tertiaryContainer = LcarsSurfaceVariant,
    onTertiaryContainer = LcarsPurple,
    error = LcarsHotPink,
    onError = Color.White,
    errorContainer = LcarsHotPink,
    onErrorContainer = Color.White,
    background = LcarsBlack,
    onBackground = LcarsWarmWhite,
    surface = LcarsDarkPurple,
    onSurface = LcarsWarmWhite,
    surfaceVariant = LcarsSurfaceVariant,
    onSurfaceVariant = LcarsDimWhite,
    outline = LcarsDimWhite,
)

fun lcarsLightColorScheme(): ColorScheme = lightColorScheme(
    primary = LcarsLightOrange,
    onPrimary = Color.White,
    primaryContainer = LcarsOrange,
    onPrimaryContainer = Color.White,
    secondary = LcarsCyan,
    onSecondary = LcarsBlack,
    secondaryContainer = LcarsSurfaceVariant,
    onSecondaryContainer = LcarsWarmWhite,
    tertiary = LcarsPurple,
    onTertiary = Color.White,
    tertiaryContainer = LcarsSurface,
    onTertiaryContainer = LcarsWarmWhite,
    error = LcarsHotPink,
    onError = Color.White,
    errorContainer = LcarsHotPink,
    onErrorContainer = Color.White,
    background = LcarsLightBg,
    onBackground = LcarsBlack,
    surface = LcarsLightBg,
    onSurface = LcarsBlack,
    surfaceVariant = LcarsSurfaceVariant,
    onSurfaceVariant = LcarsDimWhite,
    outline = LcarsDimWhite,
)

@Composable
fun LcarsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) lcarsDarkColorScheme() else lcarsLightColorScheme()

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
