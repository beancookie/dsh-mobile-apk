package com.dsharnessmobile.shell

import android.content.Context
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File
import java.io.IOException

/**
 * Console session: builds a Termux [TerminalSession] running the snapshot bash with the engine
 * environment (PATH/LD_LIBRARY_PATH/HOME/DSH_HOME/TERMUX_* + PS1) over a real PTY. The PTY is
 * created by the bundled terminal-emulator JNI (fork + openpty + execvp); the session owns the
 * reader/writer/waiter threads and feeds the [TerminalView]'s emulator via its [TerminalSessionClient].
 *
 * Works even when the engine is not running (diagnostics). On devices that forbid exec of app-data
 * ELF (Android 15/16, some OEMs), the shell is launched via /system/bin/linker64 (probe-first,
 * mirrors the old pipe-session behavior).
 */
class ConsoleSession(private val context: Context) {

  interface Listener {
    /** Status text (startup/exit); callback on any thread. */
    fun onStatus(text: String)
    /** bash process exit code (positive code or negated signal). */
    fun onExit(code: Int)
    /** Copy the given terminal text to the system clipboard. */
    fun onCopyText(text: String)
    /** Paste text from the system clipboard (null when empty). */
    fun onPasteText(): String?
  }

  private var terminalSession: TerminalSession? = null

  /** The running Termux terminal session, or null until [create] succeeds. */
  fun session(): TerminalSession? = terminalSession

  /**
   * Build the shell session. Must be called before attaching to the view.
   * @return false if the snapshot bash is missing (status reported via listener).
   */
  fun create(client: TerminalSessionClient, listener: Listener): Boolean {
    val engineManager = EngineManager(context, EngineManager.ensurePickToken())
    val bash = File(engineManager.usrDir, "bin/bash")
    if (!bash.exists()) {
      listener.onStatus(context.getString(R.string.console_bash_missing))
      return false
    }
    // Exec-bit fallback: some devices/filesystems lose the exec bit after extraction.
    try {
      bash.setExecutable(true, false)
    } catch (t: Throwable) {
      Log.w(TAG, "bash setExecutable failed: " + (t.message ?: t.javaClass.simpleName))
    }
    val envMap = engineManager.shellEnv() + ("PS1" to "dsh:\\w$ ")
    val env = envMap.map { "${it.key}=${it.value}" }.toTypedArray()
    val cwd = engineManager.homeDir.absolutePath
    val shellPath = if (canExecDirect(bash, envMap)) bash.absolutePath else "/system/bin/linker64"
    val args = if (shellPath == bash.absolutePath) {
      arrayOf(shellPath, "-i")
    } else {
      arrayOf(shellPath, bash.absolutePath, "-i")
    }
    terminalSession = TerminalSession(shellPath, cwd, args, env, TRANSCRIPT_ROWS, client)
    Log.i(TAG, "session created: shell=$shellPath argv=" + args.joinToString(" "))
    return true
  }

  /** True when the snapshot bash can be exec'd directly (probe one-shot). */
  private fun canExecDirect(bash: File, env: Map<String, String>): Boolean {
    return try {
      val pb = ProcessBuilder(bash.absolutePath, "-c", "exit 0")
      pb.environment().putAll(env)
      pb.redirectErrorStream(true).start().waitFor()
      true
    } catch (_: IOException) {
      Log.w(TAG, "direct exec denied, using linker64")
      false
    } catch (t: Throwable) {
      Log.w(TAG, "exec probe failed, using linker64: " + (t.message ?: t.javaClass.simpleName))
      false
    }
  }

  /** Terminate the session (Activity destroyed). */
  fun finish() {
    terminalSession?.finishIfRunning()
    terminalSession = null
  }

  companion object {
    private const val TAG = "dsh-console"
    private const val TRANSCRIPT_ROWS = 2000
  }
}
