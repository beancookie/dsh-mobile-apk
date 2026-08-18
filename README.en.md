# dsh-mobile-apk — DeepSeek Harness Android Shell APK

[中文说明](README.md)

![DeepSeek Harness](https://img.shields.io/badge/DeepSeek_Harness-blue?style=flat&logo=DeepSeek&logoSize=auto&color=%232D5F9E)
![Android](https://img.shields.io/badge/Android-blue?style=flat&logo=Android&logoSize=auto&color=%2397CA00)


> **dsh-mobile 生态** · [dsh-shell-termux](https://github.com/kelai141/dsh-shell-termux)（shell）· [dsh-client-ui-responsive](https://github.com/kelai141/dsh-client-ui-responsive)（移动 UI）· [dsh-host-web-compat](https://github.com/kelai141/dsh-host-web-compat)（浏览器兼容）

Android shell for [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness): WebView UI
over an **embedded Termux runtime snapshot** (extract-and-run, no Termux app needed), SAF directory
bridge, keep-alive foreground service, engine watchdog, and online runtime updates. One APK to
install: it boots a full dsh web agent that can really execute bash.

## Features

- **Embedded runtime** — ships a ~110MB xz snapshot (node + bash + coreutils + dsh + plugins);
  first launch extracts in ~10s and starts the engine from the app's own files; fully offline.
- **Mobile UI** — system WebView over `http://127.0.0.1:3080` with the responsive plugin
  (drawer/sheet on phones).
- **Native shell UI (Jetpack Compose)** — guide/startup screen (engine status, progress, collapsible
  engine.log, update check) and a built-in console (bash terminal) are native Compose screens;
  theme follows the system (light/dark). No auto-enter: after the engine is ready the guide stays
  put and you tap **进入** to open the Web UI.
- **Full-screen (immersive)** — edge-to-edge WebView (transparent system bars, notch-safe) with
  status + navigation bars tucked away by default and revealed on edge swipe.
- **Keep-alive** — foreground service ("dsh 引擎运行中") + 5s watchdog that restarts a dead engine.
- **Online runtime updates** — manifest-driven snapshot swap (download → sha256 → atomic switch →
  auto-restart); the running runtime can update itself without an APK update.
- **SAF bridge** — `pickDirectory` maps the picked tree to a real path (`/storage/emulated/0/…`).

## Build

Requirements: JDK 17+, Android SDK (compileSdk 36); Gradle 8.11.1 via wrapper.

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

> `-PsnapshotAbi` selects which snapshot is embedded (default `x86_64`). The build copies
> `snapshot/snapshot-<abi>.tar.xz` to the packaged `snapshot.tar.xz` and rebuilds on ABI switches,
> preventing mislabeled releases. Release signing: local `keystore.properties` (gitignored) or CI
> environment variables; if the key is absent the APK is built unsigned without failing.

## Releases (GitHub Actions)

Pushing a `v*` tag (or a manual `workflow_dispatch`) triggers `.github/workflows/release.yml`:

1. Pulls `snapshot-x86_64.tar.xz` from the latest existing Release, then builds the signed APK;
2. Generates `MANIFEST.txt` (sha256 + categorized path + size) and release notes from git log;
3. Creates/updates a GitHub Release with APK, snapshot, MANIFEST and notes.

Manual publishing is also possible (e.g. the dual-ABI v0.12.2): build each ABI with
`assembleRelease -PsnapshotAbi=<abi>`, then `gh release create <tag>` and upload the APKs,
`snapshot-<abi>.tar.xz`, plugins, MANIFEST and notes.

CI signing keys are injected via repository secrets: `ANDROID_KEYSTORE_BASE64` /
`KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`.

## Bridge protocol v1 (`window.androidBridge`)

| method | signature | description |
|---|---|---|
| `version` | getter → string | bridge protocol version (`"1.0"`) for feature detection |
| `checkEngine` | () → string | probes 127.0.0.1:3080; JSON `{running, latencyMs}` |
| `keepScreenOn` | (enable: boolean) | screen-on wake lock |
| `showNotification` | (title, text) | test notification channel (POST_NOTIFICATIONS) |
| `pickDirectory` | (callbackId: string) | SAF tree picker; result async via `window.__dshBridge.onDirectoryPicked(callbackId, path)` |

The bridge decouples the APK from the dsh version: pages feature-detect on `androidBridge.version`.

## Online update protocol

1. App fetches `manifest.json`: `{url, sha256, size}` (default `http://10.0.2.2:8899/manifest.json`
   for emulator testing; production points at a release server);
2. Downloads the snapshot, verifies SHA-256, extracts to a staging dir (never touching the live tree),
   atomically swaps `usr` → `usr-old` → new `usr`, then kills the old engine — the watchdog
   restarts it from the new runtime.

Test trigger: `adb shell am start -n com.dshmobile.shell/.MainActivity -a com.dshmobile.shell.action.UPDATE`;
status is written to `files/update-status.txt`. Test server: `node scripts/snapshot-server.mjs`.

## Permissions

`INTERNET` (WebView + engine probe), `POST_NOTIFICATIONS` (notification channel),
`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` (keep-alive). SAF picking needs no permission.

## ABI & pagesize

Releases ship both ABIs: `x86_64` is verified end-to-end (MuMu/real device); `arm64-v8a` is assembled
from the official Termux aarch64 repo (see docs/design.md §ABI). Pick the ABI at build time with
`-PsnapshotAbi=<abi>`. A 16KB-page build must be produced on a 16KB device. Each APK embeds its
snapshot, so APKs are arch-specific.

## License

MIT. Contains third-party components under their own licenses (see dependency declarations).
Design rationale: `docs/design.md`.
