# 📟 QCyberdeck

> **Turn your BlackBerry into a dedicated, physical-keyboard Cyberdeck.**  
> A self-contained, offline Linux terminal environment built specifically for the **BlackBerry Q5, Q10, Classic (Q20), and Passport** running BlackBerry 10 (Android 4.3 runtime).

[![Latest Release](https://img.shields.io/github/v/release/warumpas-ops/qcyberdeck?style=for-the-badge&color=00FF66&labelColor=050B07)](https://github.com/warumpas-ops/qcyberdeck/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-BlackBerry%2010%20%2F%20Android%204.3+-00D2FF?style=for-the-badge&labelColor=050B07)](https://github.com/warumpas-ops/qcyberdeck)
[![Display](https://img.shields.io/badge/Display-720x720%20Square%20Optimized-FFB000?style=for-the-badge&labelColor=050B07)](https://github.com/warumpas-ops/qcyberdeck)
[![Architecture](https://img.shields.io/badge/Arch-ARMv7%20%2F%20Qualcomm%20MSM8960-white?style=for-the-badge&labelColor=050B07)](https://github.com/warumpas-ops/qcyberdeck)

---

## ⚡ Key Features

- **⌨️ 100% Physical Keyboard Native**:
  - Full BlackBerry 35-key physical keyboard support.
  - Custom `BaseInputConnection` pipeline routing the native BB10 **`SYM` panel** directly into the terminal line editor for all extended programming punctuation (`|`, `\`, `~`, `^`, `{`, `}`, `[`, `]`).
- **📟 Direct Native Shell (Zero-PRoot Architecture)**:
  - Bypasses QNX microkernel `ptrace()` limitations (which trigger signal 11 SIGSEGV on PRoot).
  - Direct execution via `/system/bin/sh` paired with a static Musl BusyBox suite.
- **📺 Authentic Vintage CRT Phosphor Themes**:
  - Double-tap the touchscreen anytime to switch themes:
    - 🟢 **Matrix Green** (P1 Phosphor `#00FF66`)
    - 🟠 **Amber CRT** (P3 Phosphor `#FFB000`)
    - 🔵 **Cyber Blue** (Cyan `#00D2FF`)
    - ⚪ **Classic White** (`#E6EDF3`)
- **🚂 Canonical Ubuntu ASCII Animation Suite**:
  - **`sl` / `train`**: Authentic Toyoda Masashi D51 steam locomotive with 6-phase wheel rotation, smoke physics, and coal tender car.
  - **`asciiquarium` / `fish` / `aqua`**: Kirk Baucom & Joan Stark underwater ASCII world with animated fish, bubbles, Atlantis castle, swimming ducks, and seaweed.
  - **`cmatrix` / `matrix`**: Chris Allegretta digital falling green rain.
  - **`aafire` / `fire`**: Jan Hubicka heat diffusion ASCII fire simulation.
  - **`pipes` / `pipes.sh`**: Matthew Simpson multi-color box-drawing pipe network.
  - **`nyancat`**: K. Lange animated Nyan Cat with scrolling rainbow trail.
  - **`fortune` | `cowsay`**: Tony Monroe talking cow with built-in fortune quotes database.
  - **One-Tap Exit**: Tap **any key** on the physical keyboard to immediately exit animations and return to the prompt.
- **🔋 Hardware & System Tooling**:
  - `battery`: Direct BatteryManager sticky intent readout (battery %, health, temperature, charging state) without Android permission denials.
  - `whoami` & `id`: Root identity emulation (`root@q5:~# `).
  - Complete BusyBox Unix suite: `ls`, `cat`, `grep`, `vi`, `ps`, `top`, `df`, `free`, `ping`, `tar`, `gzip`, etc.
- **📜 2,000-Line Scrollback Buffer**:
  - Touch-drag up and down anywhere on the screen to review terminal history.
  - Single tap snaps immediately back to the active prompt.

---

## ⌨️ Keyboard & Gesture Controls

| Key / Gesture | Action | Description |
|---|---|---|
| **`SYM`** | **Symbol Sheet** | Opens native BlackBerry symbol picker with instant character insertion |
| **Any Key** | **Stop Animation** | Immediately stops running animations (`sl`, `fish`, `matrix`, etc.) |
| **`Back` Key** | **Cancel / Clear** | Clears input line or cancels active running process |
| **`Enter`** | **Execute** | Submits command line to shell |
| **`Del` / Backspace** | **Backspace** | Deletes preceding character |
| **`Trackpad / D-Pad Up`** | **History Up** | Recalls previous command from history |
| **`Trackpad / D-Pad Down`** | **History Down** | Recalls next command from history |
| **`Tab`** | **Tab** | Inserts horizontal tab spacing |
| **Double Tap Screen** | **Cycle Theme** | Cycles Green ➔ Amber ➔ Blue ➔ White |
| **Drag Up / Down** | **Scroll History** | Smooth scrolling through up to 2,000 lines of buffer |
| **Tap Screen** | **Snap to Bottom** | Snaps view back to live shell prompt |
| **Long Press Screen** | **Restart Session** | Resets and restarts fresh shell environment |

---

## 📦 Installation

1. Download the latest **`QCyberdeck-v3.4.0.apk`** from [GitHub Releases](https://github.com/warumpas-ops/qcyberdeck/releases/latest).
2. Transfer the `.apk` file to your BlackBerry device (via Micro-USB, SD Card, or BlackBerry 10 Browser).
3. Open the native **File Manager** app on your BlackBerry and tap the `.apk`.
4. Tap **Install** in the top right corner.
5. Launch **QCyberdeck** from your home screen.

*No root, no Google Play Services, and no internet connection required.*

---

## 🛠️ Architecture & Under the Hood

### The QNX Neutrino Constraint
BlackBerry 10 runs on the **QNX Neutrino RTOS**, which implements a strict subset of Linux system calls inside its Android 4.3 runtime sandbox (`runtime.qnx`). 

- **Why PRoot Fails**: Standard Linux rootfs wrappers (PRoot, Termux PRoot) rely on `ptrace(PTRACE_SYSCALL)`. The QNX kernel does not implement ptrace syscall traps, immediately terminating processes with **Signal 11 (SIGSEGV)**.
- **The QCyberdeck Solution**: QCyberdeck bypasses ptrace entirely by spawning `/system/bin/sh` directly via Java `ProcessBuilder`, pre-populating environment paths (`/system/bin:/system/xbin`), and bundling a standalone Musl-libc static BusyBox binary.
- **Java Terminal Driver**: Terminal emulation, local line editing, ANSI SGR color parsing (30–37, foreground, background), cursor positioning, and ASCII animations are executed within an in-memory Java pipeline, guaranteeing zero native crashes and sub-millisecond input response.

---

## 📄 License

Open-source under the [MIT License](LICENSE).
