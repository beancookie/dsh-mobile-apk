package com.dshmobile.shell.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.dshmobile.shell.R
import com.dshmobile.shell.ui.theme.splashBrush

/**
 * 启动/测试引导页（Compose，渲染在独立 ComposeView 内，作为普通 View 与
 * WebView 兄弟节点由 View 可见性切换）。浏览器（WebView）完全 View 化，
 * 不进入 Compose 组合，避免 OEM WebView 在 Compose 布局下的渲染异常。
 *
 * 2026-08-18：浅色卡片式布局——渐变背景 + 居中圆角卡片（限宽，平板不拉宽），
 * 主操作「重试」全宽主按钮，次要操作等宽次按钮行。
 */
@Composable
fun GuideScreen(
  engineStatusText: String,
  progressBarVisible: Boolean,
  progressText: String,
  crashBanner: String?,
  logSummary: String?,
  onOpenConsole: () -> Unit,
  onRetry: () -> Unit,
  onUpdate: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(brush = splashBrush())
      .systemBarsPadding(),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 2.dp,
      shadowElevation = 8.dp,
      modifier = Modifier
        .padding(24.dp)
        .widthIn(max = 480.dp)
        .fillMaxWidth(),
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .padding(horizontal = 24.dp, vertical = 28.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val logo = remember {
          val sizePx = with(density) { 72.dp.toPx() }.toInt()
          context.getDrawable(R.drawable.icon)?.toBitmap(sizePx, sizePx)?.asImageBitmap()
        }
        logo?.let {
          Image(
            bitmap = it,
            contentDescription = null,
            modifier = Modifier
              .size(72.dp)
              .clip(RoundedCornerShape(18.dp)),
          )
        }
        Spacer(Modifier.height(16.dp))
        Text(
          text = "DeepSeek Harness",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        crashBanner?.let { banner ->
          Spacer(Modifier.height(12.dp))
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = banner,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }
        }
        Spacer(Modifier.height(12.dp))
        Text(
          text = engineStatusText,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
        )
        if (progressBarVisible) {
          Spacer(Modifier.height(14.dp))
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
          Spacer(Modifier.height(8.dp))
          Text(
            text = progressText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
        logSummary?.let { summary ->
          Spacer(Modifier.height(16.dp))
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = summary,
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(12.dp),
            )
          }
        }
        Spacer(Modifier.height(24.dp))
        // 主操作全宽；次要操作等宽一行，避免小屏三键挤压换行。
        Button(
          onClick = onRetry,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        ) { Text("重试") }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedButton(
            onClick = onOpenConsole,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .height(44.dp),
          ) { Text("打开控制台") }
          OutlinedButton(
            onClick = onUpdate,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .height(44.dp),
          ) { Text("检查更新", maxLines = 1) }
        }
      }
    }
  }
}
