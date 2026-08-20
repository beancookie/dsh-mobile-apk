package com.dsharnessmobile.shell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dsharnessmobile.shell.ui.ConsoleScreen
import com.dsharnessmobile.shell.ui.theme.DshTheme

/**
 * 内置控制台：Compose 原生终端界面（状态栏 + 输出区 + 命令输入行），
 * ConsoleSession spawn 快照 bash（env 与引擎一致）→ stdin 管道写命令、
 * 输出经 Listener 回调追加到 Compose 状态。引擎未运行时也可用（排查场景）。
 */
class ConsoleActivity : ComponentActivity() {

  private val session = ConsoleSession(this)
  private val handler = android.os.Handler(android.os.Looper.getMainLooper())
  private var sessionStarted = false

  /** 状态文案（启动/退出），Compose 状态。 */
  private var consoleStatus by mutableStateOf("启动中…")
  /** 终端输出缓冲（capped），Compose 状态。 */
  private var output by mutableStateOf("")

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // 沉浸式：内容延伸到系统栏（键盘弹出仍由 manifest adjustResize 处理）。
    WindowCompat.setDecorFitsSystemWindows(window, false)
    // 系统栏图标颜色跟随系统深浅。
    applySystemBarsAppearance()
    setContent {
      DshTheme {
        ConsoleScreen(
          consoleStatus = consoleStatus,
          output = output,
          onSubmit = { session.writeCommand(it) },
          onClear = { output = "" },
        )
      }
    }
  }

  override fun onStart() {
    super.onStart()
    if (sessionStarted) return
    sessionStarted = session.start(object : ConsoleSession.Listener {
      override fun onOutput(text: String) {
        handler.post { output = capOutput(output + text) }
      }

      override fun onStatus(text: String) {
        handler.post { consoleStatus = text }
      }

      override fun onExit(code: Int) {
        handler.post { consoleStatus = "bash 已退出（code $code）" }
      }
    })
  }

  override fun onDestroy() {
    session.destroy()
    super.onDestroy()
  }

  override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
    super.onConfigurationChanged(newConfig)
    // uiMode 切换：系统栏图标跟随深浅（Compose 侧自动重组合）。
    applySystemBarsAppearance()
  }

  /** 系统栏图标颜色跟随系统深浅（浅色背景深图标，深色背景浅图标）。 */
  private fun applySystemBarsAppearance() {
    val night = (resources.configuration.uiMode and
      android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
      android.content.res.Configuration.UI_MODE_NIGHT_YES
    WindowInsetsControllerCompat(window, window.decorView).apply {
      isAppearanceLightStatusBars = !night
      isAppearanceLightNavigationBars = !night
    }
  }

  /** 输出上限（字符数）：超出时从最近换行处裁掉旧内容，防无界增长卡顿。 */
  private fun capOutput(text: String): String {
    if (text.length <= OUTPUT_CAP) return text
    val cut = text.substring(text.length - OUTPUT_KEEP)
    val nl = cut.indexOf('\n')
    return if (nl >= 0) cut.substring(nl + 1) else cut
  }

  companion object {
    private const val OUTPUT_CAP = 200_000
    private const val OUTPUT_KEEP = 150_000
  }
}
