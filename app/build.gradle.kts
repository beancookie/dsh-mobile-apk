import java.io.BufferedInputStream
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Properties
import org.tukaani.xz.XZInputStream

buildscript {
  dependencies {
    // 快照 ELF 架构校验需要解压 tar.xz 流（与 app 运行时同版本 xz 库）。
    classpath("org.tukaani:xz:1.10")
  }
}

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

// 快照 ABI（构建属性/环境变量，默认 arm64）：决定打进 APK 的快照。
// 用法：./gradlew assembleRelease -PsnapshotAbi=x86_64|arm64
// 真机均为 arm64，默认 arm64 可防「不带 flag 打出 x86_64 APK、真机直接崩」；模拟器请显式 -PsnapshotAbi=x86_64。
val snapshotAbiArg: String? = providers.gradleProperty("snapshotAbi").orNull
  ?: providers.environmentVariable("SNAPSHOT_ABI").orNull
val snapshotAbi: String = (snapshotAbiArg ?: "arm64").also {
  if (snapshotAbiArg == null) {
    logger.warn("⚠ 未指定 -PsnapshotAbi，默认使用 arm64（真机架构）；x86_64 模拟器请显式传 -PsnapshotAbi=x86_64")
  }
}

android {
  namespace = "com.dsharnessmobile.shell"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.dsharnessmobile.shell"
    minSdk = 26
    // targetSdk 34: Android 15+ forbids exec of app-data ELF for targetSdk 35+
    // (the embedded engine, bash, and every child command would need linker64
    // wrappers); 34 keeps native exec working on Android 15/16 devices.
    targetSdk = 34
    versionCode = 18
    // Snapshot builds append a suffix (e.g. -SN-1-RC8) via -PversionNameSuffix; release builds pass none.
    val snapshotSuffix = providers.gradleProperty("versionNameSuffix").getOrElse("")
    versionName = "0.12.4" + snapshotSuffix
    buildConfigField("String", "TERMUX_VERSION", "\"0.118.3\"")
  }

  buildFeatures {
    buildConfig = true
  }

  buildFeatures {
    compose = true
  }

  androidResources {
    // snapshot.tar.xz is already xz-compressed; double-compressing it breaks openFd.
    noCompress += "xz"
  }

  signingConfigs {
    // Fixed debug signing from the repo keystore: CI and local builds must produce
    // byte-compatible signatures, otherwise users cannot install over previous
    // releases (INSTALL_FAILED_UPDATE_INCOMPATIBLE). AGP's default debug keystore
    // lookup (~/.android/debug.keystore) is unreliable on CI runners, so pin it.
    create("repoDebug") {
      storeFile = rootProject.file("keystore/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      // 签名：优先读 keystore.properties（本地）；CI 通过环境变量注入。
      // 密钥/文件任一缺失则跳过签名（产出未签名 APK），不因缺配置失败。
      val keyProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
      }
      val storeFileProp = keyProps.getProperty("storeFile") ?: System.getenv("KEYSTORE_PATH")
      val ksFile = storeFileProp?.takeIf { it.isNotBlank() }?.let { rootProject.file(it) }
      if (ksFile != null && ksFile.exists()) {
        val releaseSigning = signingConfigs.findByName("release") ?: signingConfigs.create("release")
        signingConfig = releaseSigning.apply {
          storeFile = ksFile
          storePassword = keyProps.getProperty("storePassword") ?: System.getenv("KEYSTORE_PASSWORD")
          keyAlias = keyProps.getProperty("keyAlias") ?: System.getenv("KEY_ALIAS")
          keyPassword = keyProps.getProperty("keyPassword") ?: System.getenv("KEY_PASSWORD")
        }
      }
    }
    debug {
      signingConfig = signingConfigs.getByName("repoDebug")
    }
  }

  lint {
    // Offline environments lack the lint-gradle dependency cache (CN networks); lint is not on the release-critical path.
    checkReleaseBuilds = false
    abortOnError = false
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
}

// 运行时快照来自 GitHub Releases（大文件不入库）。按 snapshotAbi 选择
// src/main/assets/snapshot-<abi>.tar.xz，在 merge 之前复制为 snapshot.tar.xz
// （App 运行时固定读取 assets/snapshot.tar.xz，见 EngineManager.openFd）。
// 该任务无输出故每次必执行；内容一致时 merge 保持 up-to-date，仅 ABI 切换时
// snapshot.tar.xz 变化 → merge 自动重建，杜绝错标快照打进 APK。
val snapshotAbiFile = listOf(
  rootProject.file("snapshot/snapshot-${snapshotAbi}.tar.xz"),
  file("src/main/assets/snapshot-${snapshotAbi}.tar.xz"),
).firstOrNull { it.exists() }
val snapshotPlainFile = file("src/main/assets/snapshot.tar.xz")

// —— 快照 ELF 架构校验（防「文件名对、内容错」的错标快照进 APK）——
// e_machine 值：EM_X86_64=62，EM_AARCH64=183。流式解压 xz，滚动 64B 窗口扫
// ELF 头（magic + e_machine），统计每种架构的 ELF 数，不做整包解压。
val expectedElfMachine = when (snapshotAbi) {
  "arm64" -> 183 // EM_AARCH64
  "x86_64" -> 62 // EM_X86_64
  else -> null // 未知 ABI：跳过内容校验
}

fun elfMachineCounts(file: File): Map<Int, Int> {
  val machines = HashMap<Int, Int>()
  val chunk = ByteArray(1 shl 20)
  val win = ByteArray(64)
  var winLen = 0L
  XZInputStream(BufferedInputStream(FileInputStream(file), 1 shl 16)).use { xz ->
    while (true) {
      val n = xz.read(chunk)
      if (n < 0) break
      var i = 0
      while (i < n) {
        val pos = winLen
        win[(pos % 64).toInt()] = chunk[i]
        winLen++
        // 触发点 = ELF 头 e_machine 高字节（偏移 19）：magic 位于 pos-19..pos-16，
        // machine 低/高字节位于 pos-1/pos。
        if (pos >= 19 &&
          win[((pos - 19) % 64).toInt()].toInt() == 0x7f &&
          win[((pos - 18) % 64).toInt()].toInt() == 0x45 &&
          win[((pos - 17) % 64).toInt()].toInt() == 0x4c &&
          win[((pos - 16) % 64).toInt()].toInt() == 0x46
        ) {
          val m = (win[((pos - 1) % 64).toInt()].toInt() and 0xff) or
            ((win[(pos % 64).toInt()].toInt() and 0xff) shl 8)
          machines.merge(m, 1, Int::plus)
        }
        i++
      }
    }
  }
  return machines
}

fun machineName(m: Int): String = when (m) {
  183 -> "EM_AARCH64"
  62 -> "EM_X86_64"
  else -> "EM_0x%04X".format(m)
}

fun verifySnapshotAbi(file: File) {
  val expected = expectedElfMachine ?: return
  val counts = elfMachineCounts(file)
  val expectedCount = counts[expected] ?: 0
  val total = counts.values.sum()
  val summary = counts.entries.sortedByDescending { it.value }
    .joinToString(", ") { "${machineName(it.key)}=${it.value}" }
  if (total > 0 && expectedCount == 0) {
    throw GradleException(
      "快照 ABI 校验失败：${file.name} 内未发现任何 ${machineName(expected)} ELF（实际：$summary）——" +
        "内容与 -PsnapshotAbi=$snapshotAbi 不符，疑似错标！请核对 snapshot/snapshot-$snapshotAbi.tar.xz 的来源。",
    )
  }
  if (expectedCount > 0) {
    logger.lifecycle("> snapshot ELF 校验：${machineName(expected)}=$expectedCount / 总 ELF=$total")
  } else {
    logger.warn("> snapshot ELF 校验：${file.name} 中未发现 ELF（total=$total），跳过架构断言")
  }
}

/**
 * 把实际打进 APK 的快照指纹写入 assets/snapshot.sha256（EngineManager.snapshotFresh 的重解压依据）。
 * 关键修复：此前指纹是仓库里固定的旧常量，arm64/x86_64 APK 指纹相同 → 设备端永远判定
 * 「快照未变」→ 不重解压 → 换 ABI 的 APK 装上去也保留旧 ABI 的 usr/bin/bash（EM_X86_64 一直报）。
 * 现在指纹 = 内嵌快照的真实 SHA-256，ABI 切换必然触发设备重解压（与 CI 的 sha256sum > snapshot.sha256 对齐）。
 */
fun writeSnapshotFingerprint(file: File) {
  val md = MessageDigest.getInstance("SHA-256")
  BufferedInputStream(FileInputStream(file), 1 shl 16).use { input ->
    val buf = ByteArray(1 shl 16)
    while (true) {
      val n = input.read(buf)
      if (n < 0) break
      md.update(buf, 0, n)
    }
  }
  val hex = md.digest().joinToString("") { "%02x".format(it) }
  file("src/main/assets/snapshot.sha256").writeText(hex)
  logger.lifecycle("> snapshot 指纹: $hex")
}

val stageSnapshot = tasks.register("stageSnapshot") {
  doFirst {
    when {
      snapshotAbiFile != null -> {
        snapshotAbiFile.copyTo(snapshotPlainFile, overwrite = true)
        logger.lifecycle("> snapshot ABI: ${snapshotAbi}（${snapshotAbiFile.length()} bytes）")
        verifySnapshotAbi(snapshotPlainFile)
        writeSnapshotFingerprint(snapshotPlainFile)
      }
      snapshotPlainFile.exists() -> {
        logger.warn(
          "缺少 snapshot/snapshot-${snapshotAbi}.tar.xz，使用现有 snapshot.tar.xz（按 -PsnapshotAbi=$snapshotAbi 校验内容）",
        )
        verifySnapshotAbi(snapshotPlainFile)
        writeSnapshotFingerprint(snapshotPlainFile)
      }
      else -> throw GradleException(
        "缺少运行时快照 snapshot/snapshot-${snapshotAbi}.tar.xz —— " +
          "从 GitHub Releases 下载 snapshot-${snapshotAbi}.tar.xz 后放到项目根 snapshot/ 目录，" +
          "或按 scripts/make-snapshot.sh 在 Termux 设备自打后拉取（见 README.md）",
      )
    }
  }
}
tasks.matching { it.name == "mergeDebugAssets" || it.name == "mergeReleaseAssets" }
  .configureEach { dependsOn(stageSnapshot) }

dependencies {
  implementation("androidx.activity:activity-ktx:1.10.1")
  implementation("org.apache.commons:commons-compress:1.28.0")
  implementation("org.tukaani:xz:1.10")
  implementation("dev.rikka.shizuku:api:13.1.5")
  implementation("dev.rikka.shizuku:provider:13.1.5")

  // Jetpack Compose UI（Kotlin 2.0 compose 编译器插件；BOM 统管版本）。
  implementation(platform("androidx.compose:compose-bom:2024.12.01"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.activity:activity-compose:1.10.1")
  debugImplementation("androidx.compose.ui:ui-tooling")
}
