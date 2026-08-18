plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

import java.util.Properties

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
      val keyProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
      }
      val storeFileProp = keyProps.getProperty("storeFile") ?: System.getenv("KEYSTORE_PATH")
      if (!storeFileProp.isNullOrBlank()) {
        signingConfig = signingConfigs.create("release") {
          storeFile = rootProject.file(storeFileProp)
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

// 运行时快照来自 GitHub Releases（大文件不入库）；缺失时构建失败并给出获取指引。
tasks.whenTaskAdded {
  if (name == "mergeDebugAssets" || name == "mergeReleaseAssets") {
    doFirst {
      val snap = file("src/main/assets/snapshot.tar.xz")
      if (!snap.exists()) {
        throw GradleException(
          "缺少运行时快照 assets/snapshot.tar.xz —— " +
            "从 GitHub Releases 下载 snapshot-x86_64.tar.xz 后放到 app/src/main/assets/snapshot.tar.xz，" +
            "或按 scripts/make-snapshot.sh 在 Termux 设备自打后拉取（见 README.md）",
        )
      }
    }
  }
}

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
