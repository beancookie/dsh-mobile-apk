package com.dshmobile.shell.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
  primary = DshAccentLight,
  onPrimary = Color.White,
  background = DshBgLight,
  onBackground = DshTextLight,
  surface = DshSurfaceLight,
  onSurface = DshTextLight,
  surfaceVariant = DshSurfaceVariantLight,
  onSurfaceVariant = DshTextDimLight,
  outline = DshBorderLight,
  error = DshErrorLight,
)

private val DarkColors = darkColorScheme(
  primary = DshAccentDark,
  onPrimary = Color(0xFF002E63),
  background = DshBgDark,
  onBackground = DshTextDark,
  surface = DshSurfaceDark,
  onSurface = DshTextDark,
  surfaceVariant = DshSurfaceVariantDark,
  onSurfaceVariant = DshTextDimDark,
  outline = DshBorderDark,
  error = DshErrorDark,
)

/** dsh 主题：深色跟随系统（uiMode 变更时 Compose 自动重组合）。 */
@Composable
fun DshTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColors else LightColors,
    content = content,
  )
}

/** 引导页背景渐变（不透明，深浅色随 uiMode）。 */
@Composable
fun splashBrush(): Brush =
  if (isSystemInDarkTheme()) {
    Brush.verticalGradient(listOf(DshSplashTopDark, DshSplashBottomDark))
  } else {
    Brush.verticalGradient(listOf(DshSplashTopLight, DshSplashBottomLight))
  }
