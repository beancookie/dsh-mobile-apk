package com.dsharnessmobile.shell.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dsharnessmobile.shell.AppPrefs
import com.dsharnessmobile.shell.R

/**
 * 设置页（Compose 原生）：语言 / 主题 / 操作 / 运行日志 四区，分区标题 +
 * 圆角卡片分组（组内分割线）的现代设置页布局。语言与主题为单选行，
 * 操作区为「打开控制台」与「检查更新」（下方实时状态文案），
 * 运行日志为 engine.log 尾部摘要（默认折叠）。
 */
@Composable
fun SettingsScreen(
  language: String,
  themeMode: String,
  updateStatus: String?,
  logSummary: String?,
  onLanguageChange: (String) -> Unit,
  onThemeChange: (String) -> Unit,
  onCheckUpdate: () -> Unit,
  onOpenConsole: () -> Unit,
) {
  Column(
    Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .navigationBarsPadding(),
  ) {
    // 顶栏（抬高 surface + 分割线，避让系统状态栏）。
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.settings_title),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
    ) {
      // —— 语言 ——
      SectionTitle(text = stringResource(R.string.settings_section_language))
      SettingsCard {
        RadioRow(
          label = stringResource(R.string.settings_language_system),
          selected = language == AppPrefs.LANG_SYSTEM,
          onClick = { onLanguageChange(AppPrefs.LANG_SYSTEM) },
        )
        CardDivider()
        RadioRow(
          label = stringResource(R.string.settings_language_zh),
          selected = language == AppPrefs.LANG_ZH,
          onClick = { onLanguageChange(AppPrefs.LANG_ZH) },
        )
        CardDivider()
        RadioRow(
          label = stringResource(R.string.settings_language_en),
          selected = language == AppPrefs.LANG_EN,
          onClick = { onLanguageChange(AppPrefs.LANG_EN) },
        )
      }

      // —— 主题 ——
      SectionTitle(text = stringResource(R.string.settings_section_theme))
      SettingsCard {
        RadioRow(
          label = stringResource(R.string.settings_theme_system),
          selected = themeMode == AppPrefs.THEME_SYSTEM,
          onClick = { onThemeChange(AppPrefs.THEME_SYSTEM) },
        )
        CardDivider()
        RadioRow(
          label = stringResource(R.string.settings_theme_light),
          selected = themeMode == AppPrefs.THEME_LIGHT,
          onClick = { onThemeChange(AppPrefs.THEME_LIGHT) },
        )
        CardDivider()
        RadioRow(
          label = stringResource(R.string.settings_theme_dark),
          selected = themeMode == AppPrefs.THEME_DARK,
          onClick = { onThemeChange(AppPrefs.THEME_DARK) },
        )
      }

      // —— 操作 ——
      SectionTitle(text = stringResource(R.string.settings_section_actions))
      SettingsCard {
        ActionRow(label = stringResource(R.string.settings_open_console), onClick = onOpenConsole)
        CardDivider(indent = 16.dp)
        ActionRow(label = stringResource(R.string.settings_check_update), onClick = onCheckUpdate)
      }
      updateStatus?.let { status ->
        Text(
          text = status,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Start,
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 6.dp, bottom = 4.dp),
        )
      }

      // —— 运行日志 ——
      SectionTitle(text = stringResource(R.string.settings_log_title))
      if (logSummary.isNullOrEmpty()) {
        Text(
          text = stringResource(R.string.settings_log_empty),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
        )
      } else {
        var logExpanded by remember(logSummary) { mutableStateOf(false) }
        Surface(
          onClick = { logExpanded = !logExpanded },
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 1.dp,
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        ) {
          Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = stringResource(R.string.settings_log_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
              )
              Text(
                text = stringResource(
                  if (logExpanded) R.string.settings_log_collapse else R.string.settings_log_expand,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            AnimatedVisibility(visible = logExpanded) {
              Text(
                text = logSummary.orEmpty(),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
              )
            }
          }
        }
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}

/** 分区标题（primary 色小标签）。 */
@Composable
private fun SectionTitle(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelLarge,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
  )
}

/** 设置分组卡片（圆角 + 轻微 tonal 提升），组内行由 Column 组合。 */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 1.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(content = content)
  }
}

/** 卡片内行分割线（indent 避开 RadioButton 图标区，对齐文字）。 */
@Composable
private fun CardDivider(indent: androidx.compose.ui.unit.Dp = 56.dp) {
  HorizontalDivider(
    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    modifier = Modifier.padding(start = indent, end = 16.dp),
  )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 52.dp)
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = onClick)
    Spacer(Modifier.width(8.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 52.dp)
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = "›",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
