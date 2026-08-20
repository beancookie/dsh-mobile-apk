package com.dsharnessmobile.shell

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.dsharnessmobile.shell.ui.FileManagerScreen
import com.dsharnessmobile.shell.ui.theme.DshTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 文件管理：只读浏览应用数据目录（虚拟系统文件，根 = filesDir：usr/home/dshdata 等）。
 * 目录导航（导航栈）、后台 IO 列目录、目录优先 + 名称字母序；点击文件弹元信息
 * + 文本预览对话框。主题/语言随 AppPrefs，onResume 版本检测 settings 变更后 recreate。
 */
class FileManagerActivity : ComponentActivity() {

  private var seenConfigVersion = -1

  /** 浏览根目录 = 应用数据目录（虚拟系统根），不允许越出。 */
  private val rootDir: File get() = filesDir

  private val history = mutableStateListOf<File>()
  // 注意：不能在属性初始化器里读 filesDir——属性初始化在构造函数阶段执行，
  // 此时 Activity 的 base Context 尚未 attach（getFilesDir() 返回 null，实测崩溃）。
  private var currentDir by mutableStateOf<File?>(null)
  private var entries by mutableStateOf<List<File>>(emptyList())
  private var isLoading by mutableStateOf(false)
  private var loadError by mutableStateOf<String?>(null)

  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(AppPrefs.localeContext(newBase))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // 沉浸式：内容延伸到系统栏。
    WindowCompat.setDecorFitsSystemWindows(window, false)
    applySystemBarsAppearance()
    currentDir = filesDir
    setContent {
      DshTheme(darkTheme = AppPrefs.isDark(this)) {
        FileManagerScreen(
          currentDir = currentDir ?: filesDir,
          entries = entries,
          isLoading = isLoading,
          loadError = loadError,
          canGoUp = (currentDir ?: filesDir) != rootDir,
          onNavigate = { dir -> navigate(dir) },
          onGoUp = { goUp() },
        )
      }
    }
    load(filesDir)
  }

  private fun navigate(dir: File) {
    val from = currentDir ?: return
    history.add(from)
    currentDir = dir
    load(dir)
  }

  private fun goUp() {
    if (history.isEmpty()) return
    currentDir = history.removeAt(history.size - 1)
    load(currentDir ?: return)
  }

  /** 后台列目录（try/catch 权限），目录优先 + 名称字母序。 */
  private fun load(dir: File) {
    isLoading = true
    loadError = null
    lifecycleScope.launch {
      val list = withContext(Dispatchers.IO) {
        try {
          dir.listFiles()
            ?.filter { it.isFile || it.isDirectory }
            ?.sortedWith(
              compareBy<File> { !it.isDirectory }
                .thenBy { it.name.lowercase(Locale.ROOT) },
            )
        } catch (_: SecurityException) {
          null
        } catch (_: Exception) {
          null
        }
      }
      isLoading = false
      if (list == null) {
        loadError = getString(R.string.file_manager_error)
        entries = emptyList()
      } else {
        entries = list
      }
    }
  }

  override fun onResume() {
    super.onResume()
    // 设置变更（语言/主题）→ recreate 应用。
    val v = AppPrefs.configVersion(this)
    when {
      seenConfigVersion == -1 -> seenConfigVersion = v
      v != seenConfigVersion -> {
        seenConfigVersion = v
        recreate()
      }
    }
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
}
