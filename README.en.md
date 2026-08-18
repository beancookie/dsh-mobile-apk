<div align="center">

# dsh-mobile-apk

**DeepSeek Harness Android Shell APK**

[English](README.en.md) · [中文](README.md)

![DeepSeek Harness](https://img.shields.io/badge/DeepSeek_Harness-blue?style=flat&logo=DeepSeek&logoSize=auto&color=%232D5F9E)
![Android](https://img.shields.io/badge/Android-blue?style=flat&logo=Android&logoSize=auto&color=%2397CA00)
![minSdk](https://img.shields.io/badge/minSdk-26-green)
![License](https://img.shields.io/badge/License-MIT-orange)

> One APK to install: it boots a full dsh web agent that can really execute bash.

</div>

---

**dsh-mobile ecosystem**: [dsh-shell-termux](https://github.com/kelai141/dsh-shell-termux) (shell) · [dsh-client-ui-responsive](https://github.com/kelai141/dsh-client-ui-responsive) (mobile UI) · [dsh-host-web-compat](https://github.com/kelai141/dsh-host-web-compat) (browser compat)

Android shell for [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness): WebView UI
over an **embedded Termux runtime snapshot** (extract-and-run, no Termux app needed), SAF directory
bridge, keep-alive foreground service, engine watchdog, and online runtime updates.

## 📋 Table of Contents

- [✨ Features](#-features)
- [🚀 Quick Start](#-quick-start)
- [📦 Releases (GitHub Actions)](#-releases-github-actions)
- [🔌 Bridge protocol v1](#-bridge-protocol-v1windowandroidbridge)
- [🔄 Online update protocol](#-online-update-protocol)
- [🔐 Permissions](#-permissions)
- [🧩 ABI & pagesize](#-abi--pagesize)
- [📄 License](#-license)

## ✨ Features

| | |
|---|---|
| 🧬 **Embedded runtime** | ~110MB xz snapshot (node + bash + coreutils + dsh + plugins); first launch extracts in ~10s and starts the engine from the app's own files; **fully offline** |
| 🌐 **Mobile UI** | system WebView over `http://127.0.0.1:3080` with the responsive plugin (drawer/sheet on phones) |
| 🎨 **Native shell UI** | guide/startup screen and a built-in console are **Jetpack Compose** screens; theme follows the system (light/dark) |
| 🖥️ **Built-in console** | native bash terminal: monospace selectable output, command history ↑/↓, paste, clear; usable even when the engine is down |
| 📺 **Full-screen (immersive)** | edge-to-edge WebView (transparent system bars, notch-safe); status + navigation bars tucked away by default, revealed on edge swipe |
| 🚀 **Smart startup** | **no auto-enter** — after the engine is ready the guide stays put and you tap **进入** to open the Web UI |
| 💾 **Keep-alive** | foreground service ("dsh 引擎运行中") + 5s watchdog that restarts a dead engine |
| ♻️ **Online updates** | manifest-driven snapshot swap (download → sha256 → atomic switch → auto-restart); self-updating runtime, no APK update needed |
| 📁 **SAF bridge** | `pickDirectory` maps the picked tree to a real path (`/storage/emulated/0/…`) |

## 🚀 Quick Start

**Requirements**: JDK 17+, Android SDK (compileSdk 36); Gradle 8.11.1 via wrapper.

```sh
# 1. Prepare the runtime snapshot (required, ~110MB, distributed as a Release asset)
#    Drop ABI-named files into the project-root snapshot/ dir (gitignored):
#    Option A: download snapshot-x86_64.tar.xz / snapshot-arm64.tar.xz from GitHub Releases
#    Option B: build on a Termux device (scripts/make-snapshot.sh) and pull it
mkdir -p snapshot
cp snapshot-x86_64.tar.xz snapshot/
cp snapshot-arm64.tar.xz snapshot/

# 2. Build (ABI is explicit; fails loudly when the snapshot is missing)
./gradlew assembleDebug -PsnapshotAbi=x86_64      # or arm64
./gradlew assembleRelease -PsnapshotAbi=arm64
# output: app/build/outputs/apk/{debug,release}/app-{debug,release}.apk
```

> **💡 Notes**
>
> - `-PsnapshotAbi` selects which snapshot is embedded (default `x86_64`); the build copies
>   `snapshot/snapshot-<abi>.tar.xz` to the packaged `snapshot.tar.xz` and rebuilds on ABI switches,
>   preventing mislabeled releases.
> - Release signing: local `keystore.properties` (gitignored) or CI environment variables; if the key
>   is absent the APK is built unsigned without failing.

## 📦 Releases (GitHub Actions)

Pushing a `v*` tag (or a manual `workflow_dispatch`) triggers [`.github/workflows/release.yml`](.github/workflows/release.yml):

1. Pulls `snapshot-x86_64.tar.xz` from the latest existing Release, then builds the signed APK;
2. Generates `MANIFEST.txt` (sha256 + categorized path + size) and release notes from git log;
3. Creates/updates a GitHub Release with APK, snapshot, MANIFEST and notes.

Manual publishing also works (e.g. the dual-ABI v0.12.2):

```sh
./gradlew assembleRelease -PsnapshotAbi=<abi>
gh release create <tag> \
  apk snapshot-<abi>.tar.xz plugins MANIFEST.txt notes.md \
  --notes-file notes.md
```

> CI signing keys are injected via repository secrets: `ANDROID_KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`.

## 🔌 Bridge protocol v1 (`window.androidBridge`)

| method | signature | description |
|---|---|---|
| `version` | getter → string | bridge protocol version (`"1.0"`) for feature detection |
| `checkEngine` | () → string | probes 127.0.0.1:3080; JSON `{running, latencyMs}` |
| `keepScreenOn` | (enable: boolean) | screen-on wake lock |
| `showNotification` | (title, text) | test notification channel (POST_NOTIFICATIONS) |
| `pickDirectory` | (callbackId: string) | SAF tree picker; result async via `window.__dshBridge.onDirectoryPicked(callbackId, path)` |

The bridge decouples the APK from the dsh version: pages feature-detect on `androidBridge.version`.

## 🔄 Online update protocol

1. App fetches `manifest.json`: `{url, sha256, size}` (default `http://10.0.2.2:8899/manifest.json`
   for emulator testing; production points at a release server);
2. Downloads the snapshot, verifies SHA-256, extracts to a staging dir (never touching the live tree),
   atomically swaps `usr` → `usr-old` → new `usr`, then kills the old engine — the watchdog
   restarts it from the new runtime.

> **Test trigger**: `adb shell am start -n com.dshmobile.shell/.MainActivity -a com.dshmobile.shell.action.UPDATE`
> status is written to `files/update-status.txt`. Test server: `node scripts/snapshot-server.mjs`.

## 🔐 Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | WebView + engine probe |
| `POST_NOTIFICATIONS` | notification channel |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | keep-alive |

SAF picking needs no permission.

## 🧩 ABI & pagesize

- **Dual-ABI releases**: `x86_64` is verified end-to-end (MuMu/real device); `arm64-v8a` is assembled
  from the official Termux aarch64 repo (see [docs/design.md](docs/design.md) §ABI).
- Pick the ABI at build time with `-PsnapshotAbi=<abi>`; each APK embeds its snapshot, so APKs are
  arch-specific.
- A 16KB-page build must be produced on a 16KB device.

## 📄 License

MIT. Contains third-party components under their own licenses (see dependency declarations).
Design rationale: [docs/design.md](docs/design.md).
