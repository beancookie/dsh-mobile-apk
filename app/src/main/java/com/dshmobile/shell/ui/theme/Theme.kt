package com.dsharnessmobile.shell.ui.theme

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

/** dsh 主题：深浅色由调用方传入（跟随系统/浅色/深色，见 AppPrefs.isDark）。 */
@Composable
fun DshTheme(
  darkTheme: Boolean,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColors else LightColors,
    content = content,
  )
}

/** 引导页背景渐变（不透明，深浅色随 dark 参数）。 */
@Composable
fun splashBrush(dark: Boolean): Brush =
  if (dark) {
    Brush.verticalGradient(listOf(DshSplashTopDark, DshSplashBottomDark))
  } else {
    Brush.verticalGradient(listOf(DshSplashTopLight, DshSplashBottomLight))
  }
