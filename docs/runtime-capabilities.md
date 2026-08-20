# 虚拟系统能力（dsh-mobile-apk 内嵌运行时）

> 版本 v1.0 ｜ 2026-08-20 ｜ 依据：快照内容核对（`assets/usr`，Termux 0.118.3 运行时）

## 1. 概览

APK 内嵌 Termux 运行时快照（`snapshot-<abi>.tar.xz`，约 105MB xz），首启解压到应用私有目录：

| 路径 | 含义 |
|---|---|
| `<filesDir>/usr` | Termux rootfs（PREFIX），含 bin/lib/etc/node_modules |
| `<filesDir>/home` | `$HOME`（用户数据、`.dsh` 配置） |
| `<filesDir>/dshdata` | `$DSH_HOME`（运行时用户数据；对外的 `Documents/dshdata` 仅存放导出包） |
| `<filesDir>/engine.log` | 引擎 stdout 重定向日志 |

bash/引擎看到的就是这套文件系统；控制台终端直接在此环境中执行命令。

## 2. 可直接运行的语言

| 语言 | 可执行 | 说明 |
|---|---|---|
| **JavaScript / Node.js** | `node`、`npm`、`npx`、`corepack` | 引擎本身是 Node 应用；`usr/lib/node_modules/@deepseek-ai/dsh` 为 dsh 主包 |
| **Shell 脚本** | `bash`、`sh`、`dash` | 控制台默认交互 shell（PS1=`dsh:\w$ `） |
| **C / C++** | `clang`/`clang++`（21）、`gcc`/`g++`（aarch64 工具链）、`ld.lld`/`ld64.lld`/`lld-link`、`make`、`ar`/`nm`/`objdump`/`readelf`/`strip` | LLVM 全家（`llc`/`lli`/`opt`/`scan-build` 等）亦在 |
| **AWK** | `gawk` / `awk` | 文本处理脚本 |

**示例**（控制台内）：

```sh
node -e "console.log('hello dsh')"        # JS
clang -O2 -o /tmp/a /tmp/a.c && /tmp/a    # C
echo 'int main(){return 0;}' > a.cpp && g++ -o /tmp/b a.cpp && /tmp/b  # C++
awk 'BEGIN{print "hi"}'                    # AWK
```

## 3. 配套工具

- **版本控制**：`git`（含 git-shell/upload-pack 等）
- **网络**：`curl`、`wget`、`ssh`/`scp`/`sftp`/`sshd`/`telnet`/`ftp`
- **数据处理**：`jq`、`sed`/`grep`/`awk`、`sort`/`uniq`/`cut` 等 coreutils 全套
- **压缩**：`tar`、`xz`/`zstd`/`bzip2`/`gzip`/`unzip`
- **编辑器**：`nano`、`ed`、`less`/`more`（`vim` 未装）
- **系统/调试**：`top`/`ps`/`lsof`/`lscpu`/`free`、`dmesg`、`strace` 类（`peekfd`/`pmap`）
- **包管理**：`apt`/`apt-get`/`dpkg`、`pkg`（Termux 风格封装）

## 4. 未安装

- **Python**：`usr/bin` 仅残留 `python-config`/`pydoc*` 脚本，**无解释器与标准库**（`usr/lib` 无 `python3.*`）→ 不可运行
- Perl / Ruby / PHP / Lua / Java / Go / Rust：均未装
- CMake / Meson / Ninja：未装（`make` + clang 可直接编 C/C++）
- vim / tmux：未装

## 5. 可扩展

`apt` 基础设施在位（Termux 仓库），联网后即可安装新运行时：

```sh
pkg install python perl golang rust
pkg install cmake ninja vim tmux
```

> 注意：包安装需联网（Termux 官方源），并会占用 `filesDir` 空间；安装的包随应用数据保留。

## 6. 与壳能力的联动

- **控制台**：内置真终端（Termux `terminal-view`），PTY + ANSI + 手势 + 额外按键行（ESC/TAB/CTRL/ALT/方向键）——可直接 `node`/`clang`/`bash` 交互式运行代码
- **文件管理**：设置页 → 文件管理，只读浏览上述 `filesDir` 目录树，文本文件可预览
- **引擎**：Node 进程常驻（`usr/lib/node_modules/@deepseek-ai/dsh/lib/bin.js`），WebView 消费 `127.0.0.1:3080`
