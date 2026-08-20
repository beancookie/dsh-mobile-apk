package com.dsharnessmobile.shell.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.dsharnessmobile.shell.AppPrefs
import com.dsharnessmobile.shell.R
import com.dsharnessmobile.shell.ui.theme.splashBrush

/**
 * 启动/测试引导页（Compose，渲染在独立 ComposeView 内，作为普通 View 与
 * WebView 兄弟节点由 View 可见性切换）。浏览器（WebView）完全 View 化，
 * 不进入 Compose 组合，避免 OEM WebView 在 Compose 布局下的渲染异常。
 *
 * 浅色卡片式布局：渐变背景 + 居中圆角卡片（限宽，平板不拉宽）。
 * 引擎就绪后不再自动跳进 WebView——主按钮变为「进入」由用户主动进入；
 * 失败时主按钮为「重试」。运行日志已移至设置页。
 * 文案随应用语言（AppPrefs）切换，背景深浅随应用主题。
 */
@Composable
fun GuideScreen(
  engineStatusText: String,
  progressBarVisible: Boolean,
  progressText: String,
  crashBanner: String?,
  engineReady: Boolean,
  onEnterWeb: () -> Unit,
  onRetry: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(brush = splashBrush(dark = AppPrefs.isDark(LocalContext.current)))
      .systemBarsPadding(),
    contentAlignment = Alignment.Center,
  ) {
    // 左上角设置入口：无边框软底 pill（与渐变背景和谐融合）。
    Surface(
      onClick = onOpenSettings,
      shape = RoundedCornerShape(50),
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(start = 16.dp, top = 12.dp),
    ) {
      Text(
        text = stringResource(R.string.guide_open_settings),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
      )
    }
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 1.dp,
      modifier = Modifier
        .padding(24.dp)
        .widthIn(max = 480.dp)
        .fillMaxWidth(),
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .padding(horizontal = 28.dp, vertical = 32.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val logo = remember {
          val sizePx = with(density) { 84.dp.toPx() }.toInt()
          context.getDrawable(R.drawable.icon)?.toBitmap(sizePx, sizePx)?.asImageBitmap()
        }
        logo?.let {
          Image(
            bitmap = it,
            contentDescription = null,
            modifier = Modifier
              .size(84.dp)
              .clip(RoundedCornerShape(22.dp)),
          )
        }
        Spacer(Modifier.height(20.dp))
        Text(
          text = stringResource(R.string.guide_title),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        crashBanner?.let { banner ->
          Spacer(Modifier.height(14.dp))
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = banner,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
          }
        }
        Spacer(Modifier.height(14.dp))
        Text(
          text = engineStatusText,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        if (progressBarVisible) {
          Spacer(Modifier.height(16.dp))
          LinearProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
              .fillMaxWidth()
              .height(6.dp)
              .clip(RoundedCornerShape(3.dp)),
          )
        }
        if (progressText.isNotEmpty()) {
          Spacer(Modifier.height(10.dp))
          Text(
            text = progressText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
        Spacer(Modifier.height(28.dp))
        // 主操作全宽：引擎就绪 → 进入 Web UI；未就绪/失败 → 重试。
        if (engineReady) {
          Button(
            onClick = onEnterWeb,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp),
          ) {
            Text(
              text = stringResource(R.string.guide_enter),
              style = MaterialTheme.typography.titleSmall,
            )
          }
        } else {
          Button(
            onClick = onRetry,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp),
          ) {
            Text(
              text = stringResource(R.string.guide_retry),
              style = MaterialTheme.typography.titleSmall,
            )
          }
        }
      }
    }
  }
}
