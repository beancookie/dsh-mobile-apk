import java.util.Properties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

// 快照 ABI（构建属性/环境变量，默认 x86_64）：决定打进 APK 的快照。
// 用法：./gradlew assembleRelease -PsnapshotAbi=x86_64|arm64
val snapshotAbi: String = providers.gradleProperty("snapshotAbi")
  .orElse(providers.environmentVariable("SNAPSHOT_ABI"))
  .orElse("x86_64")
  .get()

android {
  namespace = "com.dshmobile.shell"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.dshmobile.shell"
    minSdk = 26
    // targetSdk 34: Android 15+ forbids exec of app-data ELF for targetSdk 35+
    // (the embedded engine, bash, and every child command would need linker64
    // wrappers); 34 keeps native exec working on Android 15/16 devices.
    targetSdk = 34
    versionCode = 16
    versionName = "0.12.2"
  }

  buildFeatures {
    compose = true
  }

  androidResources {
    // snapshot.tar.xz is already xz-compressed; double-compressing it breaks openFd.
    noCompress += "xz"
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
  }

  lint {
    // 离线环境无 lint-gradle 依赖缓存（国内网络）；lint 非发布关键路径。
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
val stageSnapshot = tasks.register("stageSnapshot") {
  doFirst {
    when {
      snapshotAbiFile != null -> {
        snapshotAbiFile.copyTo(snapshotPlainFile, overwrite = true)
        logger.lifecycle("> snapshot ABI: ${snapshotAbi}（${snapshotAbiFile.length()} bytes）")
      }
      snapshotPlainFile.exists() -> logger.warn(
        "缺少 snapshot/snapshot-${snapshotAbi}.tar.xz，使用现有 snapshot.tar.xz（ABI 未校验，可能错标！）",
      )
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
