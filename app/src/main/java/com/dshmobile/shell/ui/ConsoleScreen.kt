package com.dsharnessmobile.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsharnessmobile.shell.EngineProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 控制台（Compose 原生终端）：状态栏 + 输出区（可选中、等宽、自动滚底）+
 * 命令输入行（历史 ↑/↓、清屏、粘贴）。引擎状态 3s 轮询（桥自足，无需 JS 接口）。
 */
@Composable
fun ConsoleScreen(
  consoleStatus: String,
  output: String,
  onSubmit: (String) -> Unit,
  onClear: () -> Unit,
) {
  var cmdText by remember { mutableStateOf("") }
  val history = remember { mutableStateListOf<String>() }
  var historyIndex by remember { mutableStateOf(0) }
  var engineRunning by remember { mutableStateOf(false) }
  var engineInfo by remember { mutableStateOf("引擎检测中…") }
  val clipboard = LocalClipboardManager.current

  // 引擎状态轮询（3s），与旧 console.html 行为对齐。
  LaunchedEffect(Unit) {
    while (true) {
      val info = withContext(Dispatchers.IO) {
        try {
          EngineProbe.check()
        } catch (_: Exception) {
          org.json.JSONObject("{}")
        }
      }
      engineRunning = info.optBoolean("running", false)
      val latency = info.optLong("latencyMs", 0)
      engineInfo = if (engineRunning) {
        "引擎运行中（${latency}ms）"
      } else {
        val err = info.optString("error", "")
        "引擎未运行" + (if (err.isNotEmpty()) "：$err" else "")
      }
      delay(3000)
    }
  }

  fun submit() {
    val t = cmdText.trim()
    if (t.isEmpty()) return
    history.add(t)
    if (history.size > 200) history.removeAt(0)
    historyIndex = history.size
    onSubmit(t)
    cmdText = ""
  }

  fun paste() {
    val text = clipboard.getText()?.text?.toString()
    if (!text.isNullOrEmpty()) cmdText = text
  }

  val scroll = rememberScrollState()

  Column(
    Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .imePadding(),
  ) {
    // 状态栏（抬高 surface + 分割线，避让系统状态栏）
    Surface(
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 3.dp,
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "dsh 控制台",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.layout.Box(
          Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (engineRunning) Color(0xFF3FB950) else MaterialTheme.colorScheme.error),
        )
        Spacer(Modifier.width(6.dp))
        Text(
          text = engineInfo,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Text(
          text = consoleStatus,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

    // 输出区：等宽、可选中、自动滚底（LaunchedEffect(output)）。
    SelectionContainer(Modifier.weight(1f)) {
      Text(
        text = output,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 8.dp)
          .verticalScroll(scroll),
      )
    }

    // 输入行：命令 + 清屏 + 粘贴 + 发送（避让手势导航条；发送键空输入禁用）。
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface)
        .navigationBarsPadding()
        .padding(horizontal = 8.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = cmdText,
        onValueChange = { cmdText = it },
        modifier = Modifier
          .weight(1f)
          .onKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp) {
              when (event.key) {
                Key.DirectionUp -> {
                  if (historyIndex > 0) {
                    historyIndex--
                    cmdText = history[historyIndex]
                  }
                  true
                }
                Key.DirectionDown -> {
                  if (historyIndex < history.size - 1) {
                    historyIndex++
                    cmdText = history[historyIndex]
                  } else if (historyIndex == history.size - 1) {
                    historyIndex++
                    cmdText = ""
                  }
                  true
                }
                else -> false
              }
            } else {
              false
            }
          },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        placeholder = { Text("输入命令，回车执行") },
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { submit() }),
      )
      Spacer(Modifier.width(2.dp))
      TextButton(onClick = onClear, enabled = output.isNotEmpty()) { Text("清屏") }
      TextButton(onClick = { paste() }) { Text("粘贴") }
      Spacer(Modifier.width(4.dp))
      Button(
        onClick = { submit() },
        enabled = cmdText.isNotBlank(),
        shape = RoundedCornerShape(12.dp),
      ) { Text("发送") }
    }
  }

  // 自动滚底：输出变化后滚动到底部。
  LaunchedEffect(output) {
    if (scroll.maxValue > 0) scroll.animateScrollTo(scroll.maxValue)
  }
}
