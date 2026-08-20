package com.dsharnessmobile.shell

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dsharnessmobile.shell.ui.SettingsScreen
import com.dsharnessmobile.shell.ui.theme.DshTheme

/**
 * 设置页：语言（跟随系统/中文/English）+ 主题（跟随系统/浅色/深色）+
 * 操作（检查更新/打开控制台）+ 运行日志（engine.log 尾部摘要）。
 * 语言经 attachBaseContext 套 override；变更后 bump configVersion + recreate
 * 让本页立即生效（底层 Activity 由各自 onResume 的版本检测 recreate）。
 */
class SettingsActivity : ComponentActivity() {

  private var seenConfigVersion = -1

  /** 运行日志摘要（engine.log 尾部），onResume 刷新。 */
  private var logSummary by mutableStateOf("")

  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(AppPrefs.localeContext(newBase))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // 沉浸式：内容延伸到系统栏。
    WindowCompat.setDecorFitsSystemWindows(window, false)
    applySystemBarsAppearance()
    logSummary = LogCollector.engineLogTail(this, LOG_TAIL_LINES)
    var language by mutableStateOf(AppPrefs.language(this))
    var theme by mutableStateOf(AppPrefs.theme(this))
    var updateStatus by mutableStateOf<String?>(null)
    setContent {
      DshTheme(darkTheme = AppPrefs.isDark(this)) {
        SettingsScreen(
          language = language,
          themeMode = theme,
          updateStatus = updateStatus,
          logSummary = logSummary.takeIf { it.isNotEmpty() },
          onLanguageChange = { value ->
            if (value != language) {
              language = value
              AppPrefs.setLanguage(this, value)
              AppPrefs.bumpConfigVersion(this)
              seenConfigVersion = AppPrefs.configVersion(this)
              recreate()
            }
          },
          onThemeChange = { value ->
            if (value != theme) {
              theme = value
              AppPrefs.setTheme(this, value)
              AppPrefs.bumpConfigVersion(this)
              seenConfigVersion = AppPrefs.configVersion(this)
              recreate()
            }
          },
          onCheckUpdate = {
            updateStatus = getString(R.string.settings_check_update) + "…"
            UpdateManager(this).checkAndApply { status ->
              runOnUiThread { updateStatus = status }
            }
          },
          onOpenConsole = {
            startActivity(Intent(this, ConsoleActivity::class.java))
          },
          onOpenFileManager = {
            startActivity(Intent(this, FileManagerActivity::class.java))
          },
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    // 底层 Activity 设置变更（非本页）→ 版本不一致则 recreate 应用新语言/主题。
    val v = AppPrefs.configVersion(this)
    when {
      seenConfigVersion == -1 -> seenConfigVersion = v
      v != seenConfigVersion -> {
        seenConfigVersion = v
        recreate()
      }
    }
    // 运行日志摘要刷新（从控制台/更新返回时拿到最新尾部）。
    logSummary = LogCollector.engineLogTail(this, LOG_TAIL_LINES)
  }

  override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
    super.onConfigurationChanged(newConfig)
    // uiMode 切换：系统栏图标跟随深浅（Compose 侧自动重组合）。
    applySystemBarsAppearance()
  }

  /** 系统栏图标颜色跟随应用主题深浅（浅色背景深图标，深色背景浅图标）。 */
  private fun applySystemBarsAppearance() {
    val dark = AppPrefs.isDark(this)
    AppPrefs.applyWindowBackground(this)
    WindowInsetsControllerCompat(window, window.decorView).apply {
      isAppearanceLightStatusBars = !dark
      isAppearanceLightNavigationBars = !dark
    }
  }

  companion object {
    private const val LOG_TAIL_LINES = 12
  }
}
