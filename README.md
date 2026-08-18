# dsh-mobile-apk — DeepSeek Harness 安卓壳 APK

[English README](README.en.md)

![DeepSeek Harness](https://img.shields.io/badge/DeepSeek_Harness-blue?style=flat&logo=DeepSeek&logoSize=auto&color=%232D5F9E)
![Android](https://img.shields.io/badge/Android-blue?style=flat&logo=Android&logoSize=auto&color=%2397CA00)


> **dsh-mobile 生态** · [dsh-shell-termux](https://github.com/kelai141/dsh-shell-termux)（shell）· [dsh-client-ui-responsive](https://github.com/kelai141/dsh-client-ui-responsive)（移动 UI）· [dsh-host-web-compat](https://github.com/kelai141/dsh-host-web-compat)（浏览器兼容）· [dsh-mobile](https://github.com/kelai141/dsh-mobile)（协调仓库，private）

[DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness) 的安卓壳：WebView UI 覆盖
**内嵌 Termux 运行时快照**（解压即跑，无需 Termux app）、SAF 目录桥、保活前台服务、引擎看门狗、
运行时在线更新。一个 APK 装完即用：完整的 dsh web agent，且能真实执行 bash。

## 功能

- **内嵌运行时**：随包 ~110MB xz 快照（node + bash + coreutils + dsh + 插件）；首启约 10 秒解压、
  从应用自身目录启动引擎；完全离线；
- **移动 UI**：系统 WebView 加载 `http://127.0.0.1:3080`，配响应式插件（手机端抽屉/sheet）；
- **原生壳 UI（Jetpack Compose）**：启动/引导页（引擎状态、进度、可折叠 engine.log、检查更新）
  与内置控制台（bash 终端）均为原生 Compose 界面，主题随系统深浅色自适应；**不自动进浏览器**——
  引擎就绪后停引导页，点击「进入」再打开 Web UI；
- **全面屏（沉浸式）**：WebView 边到边（系统栏透明、刘海屏适配），状态栏 + 导航条默认收起，
  边缘滑动临时呼出；
- **保活**：前台服务（"dsh 引擎运行中"）+ 5 秒看门狗（引擎崩溃自动重启）；
- **在线更新**：manifest 驱动的快照热替换（下载 → sha256 → 原子切换 → 自动重启），
  运行时可自更新而无需更新 APK；
- **SAF 桥**：`pickDirectory` 把所选目录映射为真实路径（`/storage/emulated/0/…`）。

## 构建

要求：JDK 17+、Android SDK（compileSdk 36）；Gradle 8.11.1 由 wrapper 提供。

```sh
# 1. 准备运行时快照（必须，约 110MB，作为 Release 资产分发）
#    ABI 命名放入项目根 snapshot/ 目录（不入库）：
#    方式 A：从 GitHub Releases 下载 snapshot-x86_64.tar.xz / snapshot-arm64.tar.xz
#    方式 B：在 Termux 设备上自打（scripts/make-snapshot.sh）后拉取
mkdir -p snapshot
cp snapshot-x86_64.tar.xz snapshot/
cp snapshot-arm64.tar.xz snapshot/

# 2. 构建（按 ABI 显式指定，缺快照会构建失败并提示）
./gradlew assembleDebug -PsnapshotAbi=x86_64      # 或 arm64
./gradlew assembleRelease -PsnapshotAbi=arm64
# 产物: app/build/outputs/apk/{debug,release}/app-{debug,release}.apk
```

> `-PsnapshotAbi` 决定打进 APK 的快照（默认 `x86_64`）。构建时自动把
> `snapshot/snapshot-<abi>.tar.xz` 复制为打包用的 `snapshot.tar.xz`，ABI 切换时自动重打包，
> 防止错标。Release 签名：本地 `keystore.properties`（不入库）或 CI 环境变量注入；密钥缺失时
> 产出未签名 APK 并跳过签名，不阻断构建。

## 发布（GitHub Actions 自动）

推送 `v*` tag（或手动 `workflow_dispatch`）即触发 `.github/workflows/release.yml`：

1. 从本仓库最新 Release 取 `snapshot-x86_64.tar.xz` → 构建签名 APK；
2. 生成 `MANIFEST.txt`（sha256 + 分类路径 + 字节数）与基于 git log 的发布说明；
3. 创建/更新 GitHub Release，附 APK、快照、MANIFEST、notes。

手动发布亦可（如本仓库 v0.12.2 双 ABI）：构建对应 ABI 的 `assembleRelease -PsnapshotAbi=<abi>`，
用 `gh release create <tag>` 上传 APK + `snapshot-<abi>.tar.xz` + 插件 + MANIFEST + notes。

CI 签名密钥通过仓库 secrets 注入：`ANDROID_KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` /
`KEY_ALIAS` / `KEY_PASSWORD`。

## 桥协议 v1（`window.androidBridge`）

| 方法 | 签名 | 说明 |
|---|---|---|
| `version` | getter → string | 桥协议版本（`"1.0"`），页面 feature-detect 用 |
| `checkEngine` | () → string | 探测 127.0.0.1:3080；JSON `{running, latencyMs}` |
| `keepScreenOn` | (enable: boolean) | 屏幕常亮 |
| `showNotification` | (title, text) | 通知测试通道（POST_NOTIFICATIONS） |
| `pickDirectory` | (callbackId: string) | SAF 目录选择；结果经 `window.__dshBridge.onDirectoryPicked(callbackId, path)` 异步回传 |

桥协议让 APK 与 dsh 版本解耦：页面按 `androidBridge.version` 做特性检测。

## 在线更新协议

1. App 拉取 `manifest.json`：`{url, sha256, size}`（默认 `http://10.0.2.2:8899/manifest.json`
   供模拟器测试；生产指向发布服务器）；
2. 下载快照 → 校验 SHA-256 → 解压到 staging（不碰线上目录）→ 原子切换 `usr` → 杀掉旧引擎 →
   看门狗用新运行时重启。

测试触发：`adb shell am start -n com.dshmobile.shell/.MainActivity -a com.dshmobile.shell.action.UPDATE`；
状态写入 `files/update-status.txt`。测试服务器：`node scripts/snapshot-server.mjs`。

## 权限

`INTERNET`（WebView + 引擎探测）、`POST_NOTIFICATIONS`（通知通道）、
`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`（保活）。SAF 选择无需权限。

## ABI 与页大小

Release 按 ABI 双端分发：`x86_64` 快照已端到端验证（MuMu/真机）；`arm64-v8a` 快照由官方
Termux aarch64 仓库组装（见 docs/design.md §ABI）。构建时用 `-PsnapshotAbi=<abi>` 选定；
16KB 页构建需在 16KB 设备上产出。APK 内含快照，与架构绑定。

## License

MIT。第三方组件按各自许可（见依赖声明）。设计文档：`docs/design.md`。
