package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = Color(0xFF003731),
    primaryContainer = TealSoftContainerDark,
    onPrimaryContainer = TealSoftContainer,
    secondary = SlateSecondaryDark,
    onSecondary = Color(0xFF1C3532),
    secondaryContainer = SlateContainerDark,
    onSecondaryContainer = SlateContainer,
    tertiary = AmberTertiaryDark,
    background = SurfaceDark,
    surface = SurfaceDark,
    surfaceContainerLow = SurfaceCardDark,
    surfaceContainer = SurfaceVariantDark,
    onBackground = Color(0xFFE0E5E4),
    onSurface = Color(0xFFE0E5E4),
  )

private val LightColorScheme =
  lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = TealSoftContainer,
    onPrimaryContainer = Color(0xFF00201C),
    secondary = SlateSecondary,
    onSecondary = Color.White,
    secondaryContainer = SlateContainer,
    onSecondaryContainer = Color(0xFF06201D),
    tertiary = AmberTertiary,
    background = SurfaceLight,
    surface = SurfaceLight,
    surfaceContainerLow = SurfaceCardLight,
    surfaceContainer = SurfaceVariantLight,
    onBackground = Color(0xFF161D1C),
    onSurface = Color(0xFF161D1C),
  )

@Composable
fun BanglaKeysTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  ProvideSafeTextToolbar {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  BanglaKeysTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
