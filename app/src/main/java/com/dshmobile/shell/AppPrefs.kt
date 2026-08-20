package com.dsharnessmobile.shell

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 壳设置持久化 + 语言/主题生效辅助（与 text_zoom / immersive_mode 同文件 dsh_settings）。
 *
 * 语言值：system（跟随系统）| zh | en；主题值：system | light | dark。
 * 项目用 ComponentActivity（非 AppCompat），语言经 attachBaseContext 给 base context
 * 套 createConfigurationContext override；configVersion 供各 Activity 在 onResume
 * 比对实例字段 seenVersion，不一致则 recreate() 以应用新语言/主题。
 */
object AppPrefs {
  private const val PREFS = "dsh_settings"
  private const val KEY_LANGUAGE = "app_language"
  private const val KEY_THEME = "app_theme"
  private const val KEY_CONFIG_VERSION = "cfg_version"

  const val LANG_SYSTEM = "system"
  const val LANG_ZH = "zh"
  const val LANG_EN = "en"

  const val THEME_SYSTEM = "system"
  const val THEME_LIGHT = "light"
  const val THEME_DARK = "dark"

  fun language(context: Context): String =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, LANG_SYSTEM)
      ?: LANG_SYSTEM

  fun setLanguage(context: Context, value: String) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .edit().putString(KEY_LANGUAGE, value).apply()
  }

  fun theme(context: Context): String =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_THEME, THEME_SYSTEM)
      ?: THEME_SYSTEM

  fun setTheme(context: Context, value: String) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .edit().putString(KEY_THEME, value).apply()
  }

  /** 是否深色：light→false、dark→true、system→跟随 uiMode。 */
  fun isDark(context: Context): Boolean = when (theme(context)) {
    THEME_LIGHT -> false
    THEME_DARK -> true
    else -> (context.resources.configuration.uiMode and
      android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
      android.content.res.Configuration.UI_MODE_NIGHT_YES
  }

  /** 应用语言对应的 Locale；system 返回 Locale.getDefault()。 */
  fun locale(context: Context): Locale = when (language(context)) {
    LANG_ZH -> Locale.SIMPLIFIED_CHINESE
    LANG_EN -> Locale.ENGLISH
    else -> Locale.getDefault()
  }

  /** 给 base context 套上应用语言 override（attachBaseContext 用）。 */
  fun localeContext(base: Context): Context {
    if (language(base) == LANG_SYSTEM) return base
    val target = locale(base)
    val current = base.resources.configuration.locales[0]
    if (current == target) return base
    val config = Configuration(base.resources.configuration)
    config.setLocale(target)
    return base.createConfigurationContext(config)
  }

  /** 原生窗口背景跟随应用主题（与 Color.kt 的 background 一致）：防止强制
   *  反色时（系统深色 + 强制浅色等）Compose 未覆盖的透明区/启动瞬间透出
   *  系统窗口底色。各 Activity 在 applySystemBarsAppearance 内调用。 */
  fun applyWindowBackground(activity: android.app.Activity) {
    val dark = isDark(activity)
    activity.window.setBackgroundDrawable(
      android.graphics.drawable.ColorDrawable(if (dark) 0xFF111318.toInt() else 0xFFF7F8FA.toInt()),
    )
  }

  // —— 设置变更版本号：语言/主题每次变更 +1；各 Activity 在 onResume 比对
  //    实例字段 seenConfigVersion，不一致则 recreate()（语言需重建才走
  //    attachBaseContext，主题需重建才让 Compose DshTheme / WebView 生效）。 ——
  fun configVersion(context: Context): Int =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_CONFIG_VERSION, 0)

  fun bumpConfigVersion(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .edit().putInt(KEY_CONFIG_VERSION, configVersion(context) + 1).apply()
  }
}
