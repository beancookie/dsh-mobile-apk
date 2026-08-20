package com.dsharnessmobile.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dsharnessmobile.shell.EngineProbe
import com.dsharnessmobile.shell.R
import com.termux.view.TerminalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// —— 终端配色（恒深色，不随应用主题）：经典终端观感，与壳的浅/深主题解耦。 ——
private val TermBg = Color(0xFF0D1117)
private val TermHeader = Color(0xFF161B22)
private val TermHeader2 = Color(0xFF1F242E)
private val TermBorder = Color(0xFF30363D)
private val TermText = Color(0xFFE6EDF3)
private val TermDim = Color(0xFF8B949E)
private val TermGreen = Color(0xFF3FB950)
private val TermRed = Color(0xFFF85149)
private val DotRed = Color(0xFFFF5F57)
private val DotYellow = Color(0xFFFEBC2E)
private val DotGreen = Color(0xFF28C840)

/**
 * 控制台（Compose 终端外壳 + Termux TerminalView）：终端标题栏（红黄绿灯 +
 * 引擎状态）+ 真终端渲染区（PTY、ANSI、手势、软键盘直通）+ 额外按键行
 * （ESC/TAB/CTRL/ALT/方向键）+ 底部状态行。引擎状态 3s 轮询（桥自足）。
 */
@Composable
fun ConsoleScreen(
  terminalView: TerminalView,
  consoleStatus: String,
  ctrlHeld: Boolean,
  altHeld: Boolean,
  onToggleCtrl: () -> Unit,
  onToggleAlt: () -> Unit,
  onSendText: (String) -> Unit,
  fontSize: Int,
  onFontSizeChange: (Int) -> Unit,
) {
  var engineRunning by remember { mutableStateOf(false) }
  var engineInfo by remember { mutableStateOf("") }
  val context = LocalContext.current
  val strChecking = stringResource(R.string.console_engine_checking)

  // 引擎状态轮询（3s），与旧 console.html 行为对齐。
  LaunchedEffect(Unit) {
    engineInfo = strChecking
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
        context.getString(R.string.console_engine_running, latency)
      } else {
        val err = info.optString("error", "")
        context.getString(R.string.console_engine_down) + (if (err.isNotEmpty()) "：$err" else "")
      }
      delay(3000)
    }
  }

  Column(
    Modifier
      .fillMaxSize()
      .background(TermBg)
      .imePadding(),
  ) {
    // —— 终端标题栏：红黄绿灯 + 标题 + 引擎状态（避让系统状态栏）——
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(TermHeader)
        .statusBarsPadding()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TrafficLight(color = DotRed)
      Spacer(Modifier.width(6.dp))
      TrafficLight(color = DotYellow)
      Spacer(Modifier.width(6.dp))
      TrafficLight(color = DotGreen)
      Spacer(Modifier.width(14.dp))
      Text(
        text = stringResource(R.string.console_title),
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        color = TermDim,
      )
      Spacer(Modifier.weight(1f))
      Box(
        Modifier
          .size(8.dp)
          .clip(CircleShape)
          .background(if (engineRunning) TermGreen else TermRed),
      )
      Spacer(Modifier.width(6.dp))
      Text(
        text = engineInfo,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        color = TermDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    HorizontalDivider(color = TermBorder)

    // —— 真终端渲染区（Termux TerminalView：PTY + ANSI + 手势 + 软键盘直通）——
    AndroidView(
      factory = { terminalView },
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
    )
    HorizontalDivider(color = TermBorder)

    // —— 额外按键区：A-/A+ 字体 + 功能键（第一行）+ 方向键（第二行），
    //    每键均分宽度，杜绝窄屏溢出。 ——
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(TermHeader)
        .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ExtraKey("A-", modifier = Modifier.weight(1f)) { onFontSizeChange(-1) }
        Text(
          text = fontSize.toString(),
          fontSize = 12.sp,
          fontFamily = FontFamily.Monospace,
          color = TermDim,
          textAlign = TextAlign.Center,
          modifier = Modifier.width(30.dp),
        )
        ExtraKey("A+", modifier = Modifier.weight(1f)) { onFontSizeChange(1) }
        Spacer(Modifier.width(6.dp))
        ExtraKey("ESC", modifier = Modifier.weight(1f)) { onSendText("\u001b") }
        Spacer(Modifier.width(4.dp))
        ExtraKey("TAB", modifier = Modifier.weight(1f)) { onSendText("\t") }
        Spacer(Modifier.width(4.dp))
        ExtraKey("CTRL", modifier = Modifier.weight(1f), active = ctrlHeld, onClick = onToggleCtrl)
        Spacer(Modifier.width(4.dp))
        ExtraKey("ALT", modifier = Modifier.weight(1f), active = altHeld, onClick = onToggleAlt)
      }
      Spacer(Modifier.height(5.dp))
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ExtraKey("←", modifier = Modifier.weight(1f)) { onSendText("\u001b[D") }
        Spacer(Modifier.width(4.dp))
        ExtraKey("↑", modifier = Modifier.weight(1f)) { onSendText("\u001b[A") }
        Spacer(Modifier.width(4.dp))
        ExtraKey("↓", modifier = Modifier.weight(1f)) { onSendText("\u001b[B") }
        Spacer(Modifier.width(4.dp))
        ExtraKey("→", modifier = Modifier.weight(1f)) { onSendText("\u001b[C") }
      }
    }
    HorizontalDivider(color = TermBorder)

    // —— 底部状态行：bash 会话状态（避让手势导航条）——
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(TermHeader)
        .navigationBarsPadding()
        .padding(horizontal = 12.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = consoleStatus,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = TermDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun ExtraKey(
  label: String,
  active: Boolean = false,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(8.dp),
    color = if (active) TermGreen.copy(alpha = 0.22f) else TermHeader2,
    modifier = modifier.heightIn(min = 40.dp),
  ) {
    Text(
      text = label,
      fontSize = 12.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      color = if (active) TermGreen else TermText,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 10.dp),
    )
  }
}

@Composable
private fun TrafficLight(color: Color) {
  Box(
    Modifier
      .size(12.dp)
      .clip(CircleShape)
      .background(color),
  )
}
