package com.dshmobile.shell.ui.theme

import androidx.compose.material3.MaterialTheme
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

/** dsh 主题：原生页面固定浅色（不跟随系统深色；WebView 内页面主题仍由桥推送系统 uiMode）。 */
@Composable
fun DshTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = LightColors, content = content)
}

/** 引导页背景渐变（固定浅色）。 */
@Composable
fun splashBrush(): Brush =
  Brush.verticalGradient(listOf(DshSplashTopLight, DshSplashBottomLight))
