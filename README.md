# 📟 QCyberdeck

> **Turn your BlackBerry into a dedicated, physical-keyboard Cyberdeck.**  
> A self-contained, offline Linux terminal environment built specifically for the **BlackBerry Q5, Q10, Classic (Q20), and Passport** running BlackBerry 10 (Android 4.3 runtime).

[![Latest Release](https://img.shields.io/github/v/release/warumpas-ops/qcyberdeck?style=for-the-badge&color=00FF66&labelColor=050B07)](https://github.com/warumpas-ops/qcyberdeck/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-BlackBerry%2010%20%2F%20Android%204.3+-00D2FF?style=for-the-badge&labelColor=050B07)](https://github.com/warumpas-ops/qcyberdeck)
[![Display](https://img.shields.io/badge/Display-720x720%20Square%20Optimized-FFB000?style=for-the-badge&labelColor=050B07)](https://github.com/warumpas-ops/qcyberdeck)
[![Architecture](https://img.shields.io/badge/Arch-ARMv7%20%2F%20Qualcomm%20MSM8960-white?style=for-the-badge&labelColor=050B07)](https://github.com/warumpas-ops/qcyberdeck)

---

## 📦 Installation

1. Download **[`QCyberdeck-v3.8.0.apk`](https://github.com/warumpas-ops/qcyberdeck/releases/latest)** from GitHub Releases.
2. Transfer the `.apk` to your BlackBerry (via Micro-USB, SD Card, or the BB10 Browser).
3. Open the native **File Manager** and tap the `.apk`.
4. Tap **Install** in the top right corner.
5. Launch **QCyberdeck** from your home screen.
6. Once inside the terminal, type **`tutorial`** to read the full handbook.

*No root, no Google Play Services, no internet connection required.*

---

## 📖 Built-in Handbook

QCyberdeck ships with a full tutorial handbook baked directly into the terminal.  
Once installed, type any of the following to read it:

```
tutorial
manual
cat TUTORIAL.txt
```

The handbook is also automatically saved to your Micro-SD card at `/sdcard/TUTORIAL.txt` on first launch, so you can open it in any file viewer.

> **[📄 Read the Handbook online below ↓](#-handbook--command-reference)**

---

## ⚡ Key Features

- **⌨️ 100% Physical Keyboard Native**:
  - Full BlackBerry 35-key physical keyboard support.
  - Custom `BaseInputConnection` pipeline routing the native BB10 **`SYM` panel** directly into the terminal line editor for all extended programming punctuation (`|`, `\`, `~`, `^`, `{`, `}`, `[`, `]`).
- **📖 Built-in Tutorial Handbook**:
  - `tutorial` / `manual` — Full 7-section handbook available offline inside the terminal.
  - `TUTORIAL.txt` auto-saved to `/sdcard/` on first boot.
- **📦 Native Package Manager (`pkg`)**:
  - Install additional static ARMv7 tools over Wi-Fi without `apt` or root:
    - `pkg list` — View installed and available packages
    - `pkg install <name>` — Live download with progress bar
    - `pkg remove <name>` — Remove a package
  - **Online Repository**: `curl`, `htop`, `vim`, `ncdu`, `tcpdump`, `socat`, `strace`, `iperf`
- **🔐 Dropbear SSH Suite & Git (Pre-installed)**:
  - `ssh` — Dropbear SSH client, connect to remote servers from your physical keyboard.
  - `scp` — Secure file transfer over SSH.
  - `dropbearkey` — Generate RSA/ECDSA/ED25519 key pairs locally.
  - `git` — Full static ARMv7 Git suite (clone, commit, diff, push).
  - `nano` — GNU Nano editor tuned for 720×720 square LCD.
- **📟 Direct Native Shell (Zero-PRoot Architecture)**:
  - Bypasses QNX microkernel `ptrace()` limitation (no SIGSEGV on PRoot).
  - Runs directly via `/system/bin/sh` + static Musl BusyBox.
- **📺 Authentic Vintage CRT Phosphor Themes**:
  - Double-tap the screen to cycle:
    - 🟢 **Matrix Green** — P1 Phosphor `#00FF66`
    - 🟠 **Amber CRT** — Vintage P3 `#FFB000`
    - 🔵 **Cyber Blue** — Cyan `#00D2FF`
    - ⚪ **Classic White** — `#E6EDF3`
- **🚂 Canonical Linux ASCII Art & Animations**:
  - `dragon`, `cbonsai`, `neofetch`, `sl`, `asciiquarium`, `cmatrix`, `aafire`, `pipes`, `nyancat`, `fortune | cowsay` — and more.
  - **Any key on physical keyboard** instantly exits any animation.
- **📜 2,000-Line Scrollback Buffer** with hard-stop at line 1 (no black void).

---

## 🎮 Controls & Shortcuts

| Action | Function | Details |
| :--- | :--- | :--- |
| **`SYM` Key** | **Symbol Grid** | Opens BB10 symbol picker: `\|`  `\`  `~`  `^`  `{`  `}`  `[`  `]` |
| **`Alt` + Key** | **Number / Symbol** | Types numbers `1`–`9`, `0` and alt punctuation |
| **Any Key** | **Stop Animation** | Instantly stops `sl`, `fish`, `matrix`, `bonsai`, etc. |
| **`Back` Key** | **Cancel / Clear** | Clears the input line or cancels a running process |
| **`Enter`** | **Execute** | Submits command to the shell |
| **`Del` / Backspace** | **Backspace** | Deletes preceding character |
| **Trackpad / D-Pad ↑** | **History Up** | Recalls previous command |
| **Trackpad / D-Pad ↓** | **History Down** | Recalls next command |
| **`Tab`** | **Tab** | Inserts tab spacing |
| **Double Tap Screen** | **Cycle CRT Theme** | Green ➔ Amber ➔ Blue ➔ White |
| **Drag Up / Down** | **Scroll Buffer** | Scrolls up to 2,000 lines of history |
| **Tap Screen** | **Snap to Bottom** | Jumps back to live prompt |
| **Long Press Screen** | **Restart Session** | Restarts the terminal session |

---

## 📋 Handbook & Command Reference

```
================================================================================
             Q C Y B E R D E C K   H A N D B O O K   &   M A N U A L
================================================================================
  Hardware : BlackBerry Q5 / Q10 / Classic / Passport (720x720 Square LCD)
  Platform : BlackBerry 10 (Android 4.3 runtime + QNX Neutrino microkernel)
  Identity : root@q5 [UID 0] - Full Offline Linux Environment
================================================================================

1. KEYBOARD & INPUT SHORTCUTS
--------------------------------------------------------------------------------
  'SYM' Key         : Opens native BlackBerry on-screen symbol picker for all
                      code punctuation: |  \  ~  ^  {  }  [  ]  <  >  `  _
  'Alt' + Key       : Types numbers (1-9, 0) and symbols printed on keyboard.
  'Back' Key        : Cancels currently running command or clears input line.
  'Enter'           : Submits and executes command line.
  'Trackpad / D-Pad': Up / Down recalls command history.
  'Tab'             : Inserts tab indentation.
  Any Key           : Instantly stops any playing ASCII animation or demo.

2. TOUCHSCREEN GESTURES
--------------------------------------------------------------------------------
  Double-Tap Screen : Cycles vintage CRT phosphor themes:
                      - [Green] Matrix P1 Phosphor (#00FF66)
                      - [Amber] Vintage Amber CRT (#FFB000)
                      - [Blue]  Cyber Cyan (#00D2FF)
                      - [White] Clean Terminal White (#E6EDF3)
  Drag Up / Down    : Smoothly scroll through 2,000 lines of scrollback buffer.
                      Stops cleanly at line 1 (no empty black void).
  Single Tap Screen : Snaps view immediately back down to live prompt.
  Long-Press Screen : Emergency restart of the terminal session.

3. BUILT-IN PACKAGE MANAGER ('pkg')
--------------------------------------------------------------------------------
Connect your BlackBerry to Wi-Fi to install additional static ARMv7 tools:

  pkg list             - View installed and available repository packages
  pkg install <name>   - Download and install tool to /system/usr/bin/
  pkg remove <name>    - Uninstall an installed package

Available tools in repository:
  - curl     : Full cURL with HTTPS/TLS network transfer support
  - htop     : Interactive visual process monitor
  - vim      : Vi IMproved modal text editor
  - ncdu     : Visual NCurses disk usage analyzer
  - tcpdump  : Network packet capture and protocol analyzer
  - socat    : Multipurpose bidirectional socket relay
  - strace   : System call tracer
  - iperf    : Network bandwidth throughput testing tool

Example:
  pkg install curl
  pkg install htop

4. REMOTE ACCESS & CODE CONTROL (PRE-INSTALLED)
--------------------------------------------------------------------------------
  ssh user@host      : Dropbear SSH client. Remote into Linux servers,
                       VPS, or Raspberry Pis from physical keys!
                       Example: ssh pi@192.168.1.50
  scp file user@host : Secure copy files to/from remote machines.
  dropbearkey        : Generate local SSH keypairs in ~/.ssh/
  git                : Full static ARMv7 Git suite!
                       - git clone https://github.com/user/repo.git
                       - git status
                       - git add .
                       - git commit -m "my commit"
                       - git push origin main
  nano <filename>    : User-friendly terminal text editor for BlackBerry LCD.
  vi <filename>      : Classic vi modal editor.

5. CANONICAL LINUX ASCII ART & ANIMATIONS
--------------------------------------------------------------------------------
Tap ANY key on the physical keyboard to exit an animation at any time.

  dragon <message>   : Giant fire-breathing dragon speaking your custom message!
                       Color adapts dynamically to your active CRT phosphor.
  cbonsai / bonsai   : Live animated growing bonsai tree with custom branches.
  neofetch / fastfetch: Cyberdeck hardware spec card & ANSI architecture art.
  sl / train         : Steam locomotive rolling across your screen.
  asciiquarium / fish: Underwater aquarium with fish, castle & swimming ducks.
  cmatrix / matrix   : Falling digital Matrix rain.
  aafire / fire      : Heat diffusion ASCII fire simulation.
  pipes              : Multi-color animated pipe maze.
  nyancat            : Animated Nyan Cat with rainbow star trail.
  fortune | cowsay   : Talking cow speaking witty Unix fortune quotes.
  skull              : Cyberpunk root skull graphic.
  deck               : ASCII BlackBerry cyberdeck handheld rendering.
  hack               : Simulated cyberdeck network infiltration sequence.

6. SYSTEM TELEMETRY & HARDWARE GAUGES
--------------------------------------------------------------------------------
  battery   : Real-time battery %, health, temperature (C & F), voltage,
              and charging status via direct BatteryManager intent.
  sysinfo   : CPU clock speed, hardware architecture, and kernel status.
  df -h     : Disk space across internal storage and Micro-SD card (/sdcard).
  free      : Available and used RAM (2GB LPDDR2).
  top       : Live CPU usage and task list.
  uname -a  : System kernel and architecture information.
  ifconfig  : Active Wi-Fi and mobile data IP addresses.
  netstat   : Active TCP/UDP listening ports and network connections.
  ping      : Test internet connectivity (e.g. ping 8.8.8.8).

7. FILESYSTEM & STORAGE
--------------------------------------------------------------------------------
  /sdcard   : Your Micro-SD card and user storage. Mounts cleanly.
  ~/        : App private home (/data/data/com.bbcyberdeck/files/root)
  tree      : Visual folder directory hierarchy tree.
  ls -la    : List all files with sizes, dates, and permissions.
  tar / gzip: Create or extract archives (.tar.gz).

================================================================================
Type 'help' for a quick cheat sheet. Type 'tutorial' to re-read this handbook.
================================================================================
```

---

## 🛠️ Architecture & Under the Hood

### The QNX Neutrino Constraint
BlackBerry 10 runs on the **QNX Neutrino RTOS**, implementing a strict subset of Linux syscalls inside its Android 4.3 sandbox.

- **Why PRoot Fails**: PRoot requires `ptrace(PTRACE_SYSCALL)`. QNX doesn't implement it → immediate **Signal 11 (SIGSEGV)**.
- **The QCyberdeck Solution**: Spawns `/system/bin/sh` directly via Java `ProcessBuilder`, with paths pre-populated (`/system/bin:/system/xbin:/data/data/com.bbcyberdeck/files/usr/bin`) and a standalone static Musl BusyBox binary.
- **Java Terminal Driver**: Terminal emulation, ANSI SGR color parsing, local line editing, and ASCII animations are all in-process — zero native crashes, sub-millisecond input latency.

---

## 📄 License

Open-source under the [MIT License](LICENSE).
