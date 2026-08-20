package com.dsharnessmobile.shell

import android.content.Context
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dsharnessmobile.shell.ui.ConsoleScreen
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/**
 * 内置控制台：Compose 终端外壳（红黄绿灯标题栏 + 额外按键行 + 状态行）内嵌
 * Termux TerminalView（真 PTY + ANSI/真彩色 + 手势 + 软键盘直通）。ConsoleSession
 * 构建快照 bash（env 与引擎一致）→ TerminalView 渲染交互；引擎未运行时也可用。
 * 终端恒深色（经典终端观感，不随应用主题）；语言随 AppPrefs，onResume 版本
 * 检测 settings 变更后 recreate。
 */
class ConsoleActivity : ComponentActivity() {

  private val handler = android.os.Handler(android.os.Looper.getMainLooper())
  private var seenConfigVersion = -1

  /** 状态文案（启动/退出），Compose 状态。 */
  private var consoleStatus by mutableStateOf("")

  private lateinit var terminalView: TerminalView
  private lateinit var consoleSession: ConsoleSession

  /** 终端字体大小（dp，额外按键 A-/A+ 动态调整，持久化）。 */
  private var terminalFontSize by mutableStateOf(FONT_SIZE_DEFAULT)

  /** 额外按键行 CTRL/ALT 粘滞态（经 TerminalViewClient.read*Key() 让软键盘输入也生效）。 */
  private var ctrlHeld = false
  private var altHeld = false

  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(AppPrefs.localeContext(newBase))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // 沉浸式：内容延伸到系统栏（键盘弹出仍由 manifest adjustResize 处理）。
    WindowCompat.setDecorFitsSystemWindows(window, false)
    // 终端恒深色：窗口底色 + 系统栏浅色图标（不随应用主题）。
    applySystemBarsAppearance()
    consoleStatus = getString(R.string.console_starting)

    terminalView = TerminalView(this, null).apply {
      isFocusable = true
      isFocusableInTouchMode = true
      terminalFontSize = prefsFontSize()
      setTextSize(terminalFontSize)
    }
    consoleSession = ConsoleSession(this)
    val sessionClient = buildSessionClient()
    if (consoleSession.create(sessionClient, sessionListener)) {
      consoleStatus = getString(R.string.console_bash_started)
      terminalView.attachSession(consoleSession.session())
    }
    terminalView.setTerminalViewClient(buildViewClient())

    setContent {
      ConsoleScreen(
        terminalView = terminalView,
        consoleStatus = consoleStatus,
        ctrlHeld = ctrlHeld,
        altHeld = altHeld,
        onToggleCtrl = {
          ctrlHeld = !ctrlHeld
          terminalView.invalidate()
        },
        onToggleAlt = {
          altHeld = !altHeld
          terminalView.invalidate()
        },
        onSendText = { text ->
          val session = consoleSession.session() ?: return@ConsoleScreen
          val bytes = text.toByteArray(Charsets.UTF_8)
          session.write(bytes, 0, bytes.size)
        },
        fontSize = terminalFontSize,
        onFontSizeChange = { delta -> adjustFontSize(delta) },
      )
    }
  }

  /** 终端字体大小持久化读取（默认 24，范围 12–40）。 */
  private fun prefsFontSize(): Int {
    return try {
      getSharedPreferences("dsh_settings", Context.MODE_PRIVATE)
        .getInt(KEY_FONT_SIZE, FONT_SIZE_DEFAULT).coerceIn(FONT_SIZE_MIN, FONT_SIZE_MAX)
    } catch (_: Exception) {
      FONT_SIZE_DEFAULT
    }
  }

  /** 动态调整终端字体大小（A-/A+）并持久化。 */
  private fun adjustFontSize(delta: Int) {
    val next = (terminalFontSize + delta).coerceIn(FONT_SIZE_MIN, FONT_SIZE_MAX)
    if (next == terminalFontSize) return
    terminalFontSize = next
    terminalView.setTextSize(next)
    try {
      getSharedPreferences("dsh_settings", Context.MODE_PRIVATE)
        .edit().putInt(KEY_FONT_SIZE, next).apply()
    } catch (t: Throwable) {
      Log.w("dsh-console", "font size persist failed: " + (t.message ?: t.javaClass.simpleName))
    }
  }

  private val sessionListener = object : ConsoleSession.Listener {
    override fun onStatus(text: String) {
      handler.post { consoleStatus = text }
    }

    override fun onExit(code: Int) {
      handler.post { consoleStatus = getString(R.string.console_bash_exited, code) }
    }

    override fun onCopyText(text: String) {
      handler.post { copyToClipboard(text) }
    }

    override fun onPasteText(): String? {
      return (getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
        .primaryClip?.getItemAt(0)?.coerceToText(this@ConsoleActivity)?.toString()?.takeIf { it.isNotEmpty() }
    }
  }

  private fun copyToClipboard(text: String) {
    try {
      val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
      cm.setPrimaryClip(android.content.ClipData.newPlainText("dsh-terminal", text))
    } catch (t: Throwable) {
      Log.w("dsh-console", "clipboard write failed: " + (t.message ?: t.javaClass.simpleName))
    }
  }

  /** TerminalSession callbacks：输出到达刷新视图、会话结束状态、剪贴板。 */
  private fun buildSessionClient(): TerminalSessionClient {
    return object : TerminalSessionClient {
      override fun onTextChanged(session: TerminalSession) { terminalView.onScreenUpdated() }
      override fun onTitleChanged(session: TerminalSession) {}
      override fun onSessionFinished(session: TerminalSession) {
        handler.post { consoleStatus = getString(R.string.console_bash_exited, session.exitStatus) }
      }
      override fun onCopyTextToClipboard(session: TerminalSession, text: String) { copyToClipboard(text) }
      override fun onPasteTextFromClipboard(session: TerminalSession) {
        sessionListener.onPasteText()?.let {
          val bytes = it.toByteArray(Charsets.UTF_8)
          session.write(bytes, 0, bytes.size)
        }
      }
      override fun onBell(session: TerminalSession) {}
      override fun onColorsChanged(session: TerminalSession) {}
      override fun onTerminalCursorStateChange(visible: Boolean) {}
      override fun getTerminalCursorStyle(): Int? = null
      override fun logError(tag: String, msg: String) { Log.e(tag, msg) }
      override fun logWarn(tag: String, msg: String) { Log.w(tag, msg) }
      override fun logInfo(tag: String, msg: String) { Log.i(tag, msg) }
      override fun logDebug(tag: String, msg: String) { Log.d(tag, msg) }
      override fun logVerbose(tag: String, msg: String) { Log.v(tag, msg) }
      override fun logStackTraceWithMessage(tag: String, msg: String, e: Exception) { Log.e(tag, msg, e) }
      override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "stack", e) }
    }
  }

  /** TerminalView 交互回调：点击呼出键盘、粘滞 CTRL/ALT、长按保留默认（选词）。 */
  private fun buildViewClient(): TerminalViewClient {
    return object : TerminalViewClient {
      override fun onScale(scale: Float): Float = scale.coerceIn(0.5f, 2.0f)
      override fun onSingleTapUp(event: android.view.MotionEvent) {
        terminalView.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
          ?.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
      }
      override fun shouldBackButtonBeMappedToEscape(): Boolean = false
      override fun shouldEnforceCharBasedInput(): Boolean = true
      override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
      override fun isTerminalViewSelected(): Boolean = true
      override fun copyModeChanged(enabled: Boolean) {}
      override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent, session: TerminalSession): Boolean = false
      override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean = false
      override fun onLongPress(event: android.view.MotionEvent): Boolean = false
      override fun readControlKey(): Boolean = ctrlHeld
      override fun readAltKey(): Boolean = altHeld
      override fun readShiftKey(): Boolean = false
      override fun readFnKey(): Boolean = false
      override fun onCodePoint(codepoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
      override fun onEmulatorSet() {}
      override fun logError(tag: String, msg: String) { Log.e(tag, msg) }
      override fun logWarn(tag: String, msg: String) { Log.w(tag, msg) }
      override fun logInfo(tag: String, msg: String) { Log.i(tag, msg) }
      override fun logDebug(tag: String, msg: String) { Log.d(tag, msg) }
      override fun logVerbose(tag: String, msg: String) { Log.v(tag, msg) }
      override fun logStackTraceWithMessage(tag: String, msg: String, e: Exception) { Log.e(tag, msg, e) }
      override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "stack", e) }
    }
  }

  override fun onResume() {
    super.onResume()
    // 设置变更（语言）→ recreate 应用（语言需重建走 attachBaseContext）。
    val v = AppPrefs.configVersion(this)
    when {
      seenConfigVersion == -1 -> seenConfigVersion = v
      v != seenConfigVersion -> {
        seenConfigVersion = v
        recreate()
      }
    }
  }

  override fun onDestroy() {
    consoleSession.finish()
    super.onDestroy()
  }

  override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
    super.onConfigurationChanged(newConfig)
    // 终端恒深色：系统栏图标保持浅色（语言/主题重建由 onResume 处理）。
    applySystemBarsAppearance()
  }

  /** 终端恒深色外观：窗口底色深色 + 系统栏浅色图标（与 ConsoleScreen 配色一致）。 */
  private fun applySystemBarsAppearance() {
    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFF0D1117.toInt()))
    WindowInsetsControllerCompat(window, window.decorView).apply {
      isAppearanceLightStatusBars = false
      isAppearanceLightNavigationBars = false
    }
  }

  companion object {
    private const val KEY_FONT_SIZE = "console_font_size"
    private const val FONT_SIZE_DEFAULT = 36
    private const val FONT_SIZE_MIN = 12
    private const val FONT_SIZE_MAX = 60
  }
}
