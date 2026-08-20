package com.dsharnessmobile.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsharnessmobile.shell.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 文本预览上限（字节）：超出只读开头并提示。 */
private const val PREVIEW_CAP_BYTES = 32 * 1024

/**
 * 文件管理（Compose 原生）：只读浏览应用数据目录（虚拟系统文件，根 = filesDir）。
 * 顶栏返回/上级 + 当前路径；LazyColumn 文件列表（目录优先、字母序）；
 * 点击文件弹出元信息对话框（名称/类型/大小/修改时间 + 文本预览，二进制不可预览）。
 */
@Composable
fun FileManagerScreen(
  currentDir: File,
  entries: List<File>,
  isLoading: Boolean,
  loadError: String?,
  canGoUp: Boolean,
  onNavigate: (File) -> Unit,
  onGoUp: () -> Unit,
) {
  var selectedFile by remember { mutableStateOf<File?>(null) }
  var previewText by remember { mutableStateOf<String?>(null) }
  var previewBinary by remember { mutableStateOf(false) }
  var previewTruncated by remember { mutableStateOf(false) }
  val context = LocalContext.current

  // 选中文件时后台读取文本预览（上限保护）。
  LaunchedEffect(selectedFile) {
    val file = selectedFile ?: return@LaunchedEffect
    previewText = null
    previewBinary = false
    previewTruncated = false
    val (text, binary, truncated) = withContext(Dispatchers.IO) { readTextPreview(file) }
    previewText = text
    previewBinary = binary
    previewTruncated = truncated
  }

  Column(
    Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .navigationBarsPadding(),
  ) {
    // 顶栏：返回/上级 + 标题 + 当前路径（避让系统状态栏）。
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(
          onClick = onGoUp,
          enabled = canGoUp,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
          )
        }
        Column(Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.file_manager_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = currentDir.absolutePath,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Text(
          text = stringResource(R.string.file_manager_items, entries.size),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 12.dp),
        )
      }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    if (isLoading) {
      LinearProgressIndicator(
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
          .fillMaxWidth()
          .height(3.dp),
      )
    }

    when {
      loadError != null -> CenteredHint(
        text = stringResource(R.string.file_manager_error),
        color = MaterialTheme.colorScheme.error,
      )
      entries.isEmpty() && !isLoading -> CenteredHint(
        text = stringResource(R.string.file_manager_empty),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      else -> LazyColumn(Modifier.fillMaxSize()) {
        items(entries, key = { it.absolutePath }) { file ->
          FileRow(
            file = file,
            onClick = {
              if (file.isDirectory) onNavigate(file) else selectedFile = file
            },
          )
          HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            modifier = Modifier.padding(start = 16.dp),
          )
        }
      }
    }
  }

  // 元信息 + 文本预览对话框。
  selectedFile?.let { file ->
    AlertDialog(
      onDismissRequest = { selectedFile = null },
      title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
      text = {
        Column {
          MetaRow(label = stringResource(R.string.file_manager_type)) {
            Text(
              text = stringResource(if (file.isDirectory) R.string.file_manager_folder else R.string.file_manager_file),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          if (file.isFile) {
            MetaRow(label = stringResource(R.string.file_manager_size)) {
              Text(
                text = formatSize(file.length()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
          MetaRow(label = stringResource(R.string.file_manager_modified)) {
            Text(
              text = formatTime(file.lastModified()),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          if (file.isFile) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Spacer(Modifier.height(10.dp))
            when {
              previewBinary -> Text(
                text = stringResource(R.string.file_manager_binary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              previewText == null -> Text(
                text = "…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              else -> Column {
                Text(
                  text = previewText.orEmpty(),
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                  color = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(10.dp),
                )
                if (previewTruncated) {
                  Spacer(Modifier.height(6.dp))
                  Text(
                    text = stringResource(R.string.file_manager_preview_truncated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { selectedFile = null }) {
          Text(stringResource(R.string.file_manager_close))
        }
      },
    )
  }
}

@Composable
private fun FileRow(file: File, onClick: () -> Unit) {
  val isDir = file.isDirectory
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.width(22.dp)) {
      if (isDir) {
        Text(
          text = "▸",
          fontSize = 16.sp,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
    Text(
      text = file.name,
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = if (isDir) FontWeight.SemiBold else FontWeight.Normal,
      color = if (isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    if (isDir) {
      Text(
        text = "›",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      Text(
        text = formatSize(file.length()),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp),
      )
    }
  }
}

@Composable
private fun MetaRow(label: String, content: @Composable () -> Unit) {
  Row(Modifier.padding(vertical = 3.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.width(72.dp),
    )
    content()
  }
}

@Composable
private fun CenteredHint(text: String, color: androidx.compose.ui.graphics.Color) {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = color,
      textAlign = TextAlign.Center,
    )
  }
}

/** 只读文本预览：二进制（含 NUL / 解码失败）返回 binary；超长截断返回 truncated。 */
private fun readTextPreview(file: File): Triple<String?, Boolean, Boolean> {
  return try {
    val length = file.length()
    if (length <= 0) return Triple("", false, false)
    val cap = minOf(length, PREVIEW_CAP_BYTES.toLong()).toInt()
    val bytes = file.inputStream().use { input ->
      val buf = ByteArray(cap)
      val n = input.read(buf)
      if (n < cap) buf.copyOf(n) else buf
    }
    if (bytes.any { it.toInt() == 0 }) return Triple(null, true, false)
    val text = String(bytes, Charsets.UTF_8)
    Triple(text, false, length > PREVIEW_CAP_BYTES)
  } catch (_: Exception) {
    Triple(null, true, false)
  }
}

/** 人类可读文件大小。 */
private fun formatSize(bytes: Long): String = when {
  bytes < 1024 -> "$bytes B"
  bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
  bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
  else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

/** 修改时间格式化。 */
private fun formatTime(millis: Long): String {
  if (millis <= 0) return "-"
  return try {
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))
  } catch (_: Exception) {
    "-"
  }
}
