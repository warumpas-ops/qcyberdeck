package com.bbcyberdeck;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TerminalSession {
    private static final String TAG = "TerminalSession";

    public interface OutputListener {
        void onOutput(byte[] buffer, int offset, int length);
        void onProcessExited(int exitCode);
    }

    private final Context mContext;
    private Process mProcess;
    private OutputStream mStdin;
    private InputStream mStdout;
    private OutputListener mListener;
    private volatile boolean mRunning = false;

    public TerminalSession(Context context) {
        mContext = context;
    }

    public void setOutputListener(OutputListener listener) {
        mListener = listener;
    }

    public boolean isRunning() {
        return mRunning;
    }

    private void emit(String msg) {
        if (mListener != null && msg != null) {
            byte[] b = msg.getBytes();
            mListener.onOutput(b, 0, b.length);
        }
    }

    public void start() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File filesDir = mContext.getFilesDir();
                    File binDir   = new File(filesDir, "usr/bin");
                    File busybox  = new File(binDir, "busybox");
                    File rootDir  = new File(filesDir, "root");
                    File tmpDir   = new File(filesDir, "tmp");
                    rootDir.mkdirs();
                    tmpDir.mkdirs();

                    boolean usingBusybox = false;
                    if (busybox.exists() && busybox.canExecute()) {
                        try {
                            Process t = Runtime.getRuntime().exec(
                                new String[]{busybox.getAbsolutePath(), "true"});
                            if (t.waitFor() == 0) {
                                usingBusybox = true;
                            }
                        } catch (Throwable ignored) {}
                    }

                    List<String> cmd = new ArrayList<String>();
                    if (usingBusybox) {
                        Log.i(TAG, "Shell: busybox sh");
                        cmd.add(busybox.getAbsolutePath());
                        cmd.add("sh");
                    } else {
                        Log.i(TAG, "Shell: /system/bin/sh");
                        cmd.add("/system/bin/sh");
                    }

                    String pathVal = binDir.getAbsolutePath()
                            + ":/system/xbin:/system/bin:/sbin:/vendor/bin";

                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.directory(rootDir);
                    pb.redirectErrorStream(true);

                    Map<String, String> env = pb.environment();
                    env.put("PATH",     pathVal);
                    env.put("HOME",     rootDir.getAbsolutePath());
                    env.put("TMPDIR",   tmpDir.getAbsolutePath());
                    env.put("USER",     "root");
                    env.put("LOGNAME",  "root");
                    env.put("TERM",     "xterm-256color");
                    env.put("PS1",      "root@q5:~# ");

                    String existingLd = System.getenv("LD_LIBRARY_PATH");
                    if (existingLd == null || existingLd.isEmpty()) {
                        existingLd = "/system/lib:/vendor/lib";
                    }
                    env.put("LD_LIBRARY_PATH", existingLd);
                    env.remove("LD_PRELOAD");

                    Log.i(TAG, "Starting: " + cmd);
                    mProcess = pb.start();
                    mStdin   = mProcess.getOutputStream();
                    mStdout  = mProcess.getInputStream();
                    mRunning = true;

                    // Clean startup header with full device specs and initial prompt
                    String banner =
                        "\u001B[1;32m================================================\u001B[0m\r\n" +
                        "\u001B[1;32m  QCyberdeck - BlackBerry Q5 Linux Terminal\u001B[0m\r\n" +
                        "\u001B[1;32m================================================\u001B[0m\r\n" +
                        "  Device   : BlackBerry Q5 (Qualcomm MSM8960 2GB)\r\n" +
                        "  Core     : BusyBox Linux (350+ Unix Utilities)\r\n" +
                        "  Storage  : /sdcard & app storage mounted\r\n" +
                        "  Status   : \u001B[1;32mONLINE [ROOT: UID 0]\u001B[0m\r\n" +
                        "  Commands : type 'help' for quick reference\r\n" +
                        "\u001B[1;32m------------------------------------------------\u001B[0m\r\n\r\n" +
                        "\u001B[1;32mroot@q5\u001B[0m:\u001B[1;34m~#\u001B[0m ";
                    emit(banner);

                    // Load built-in functions directly into memory inside the shell
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(150);
                                StringBuilder defs = new StringBuilder();
                                defs.append("whoami() { echo 'root'; }\n");
                                defs.append("id() { if [ \"$1\" = \"-u\" ]; then echo 0; elif [ \"$1\" = \"-g\" ]; then echo 0; elif [ \"$1\" = \"-un\" ] || [ \"$1\" = \"-gn\" ]; then echo root; else echo 'uid=0(root) gid=0(root) groups=0(root)'; fi; }\n");
                                defs.append("who() { echo \"root pts/0 $(date +'%Y-%m-%d %H:%M')\"; }\n");
                                defs.append("su() { echo 'Already running as root (UID 0)'; }\n");
                                defs.append("cowsay() { MSG=\"${*:-QCyberdeck online.}\"; echo ' -------------------------------------'; echo \"< $MSG >\"; echo ' -------------------------------------'; echo '        \\   ^__^'; echo '         \\  (oo)\\_______'; echo '            (__)\\       )\\/\\'; echo '                ||----w |'; echo '                ||     ||'; }\n");
                                defs.append("skull() { echo ''; echo '          .---.'; echo '         /     \\'; echo '        | () () |'; echo '         \\  ^  /'; echo '          |||||'; echo '      .---'\"'\"'---'\"'\"'---.'; echo '     /  CYBERDECK  \\'; echo '    |  ROOT ACCESS  |'; echo '     \\_____________/ '; echo ''; }\n");
                                defs.append("deck() { echo ''; echo '   .------------------------.'; echo '   | [=] QCYBERDECK    100% |'; echo '   |------------------------|'; echo '   | root@q5:~# _           |'; echo '   | Linux 3.4 MSM8960 2GB  |'; echo '   |========================|'; echo '   | [Q][W][E][R][T][Y] ... |'; echo '   | [A][S][D][F][G][H] ... |'; echo '   | [ALT][Z][X][C][V] [ENT]|'; echo '   '\"'\"'------------------------'\"'\"''; echo ''; }\n");
                                defs.append("clock() { echo \"  Date   : $(date +'%A, %B %d, %Y')\"; echo \"  Time   : $(date +'%H:%M:%S %Z')\"; echo \"  Uptime : $(uptime | sed 's/.*up \\([^,]*\\), .*/\\1/' 2>/dev/null)\"; }\n");
                                defs.append("fetch() { echo ''; echo '       ____  _____'; echo '      / __ \\/ ___/    OS     : QCyberdeck v2.7'; echo '     / / / /\\__ \\     Host   : BlackBerry Q5 (Qualcomm MSM8960)'; echo \"    / /_/ /___/ /     Kernel : $(uname -r 2>/dev/null || echo '3.4.0-QNX')\"; echo '    \\___\\_\\____/      Memory : 2048 MB LPDDR2'; echo \"                      Storage: $(df -h /sdcard 2>/dev/null | tail -n 1 | awk '{print $4}') free on /sdcard\"; echo '                      User   : root@q5 (UID 0)'; echo ''; }\n");
                                defs.append("matrix() { echo '=== CYBER STREAM START ==='; for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25; do echo '01000010 01000010 00110001 00110000 01010001 00110101'; sleep 0.04; done; echo '=== CYBER STREAM COMPLETE ==='; }\n");
                                defs.append("tree() { if busybox tree \"$@\" 2>/dev/null; then return; fi; T=\"${1:-.}\"; echo \"$T\"; find \"$T\" -maxdepth 3 2>/dev/null | sed -e 's/[^-][^\\/]*\\// |-- /g' -e 's/|-- \\([^ ]\\)/+-- \\1/'; }\n");
                                defs.append("sysinfo() { echo '=== BlackBerry Q5 Telemetry ==='; echo 'CPU     : Qualcomm MSM8960 Krait Dual-Core'; [ -f /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq ] && echo \"Clock   : $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq) kHz\"; [ -f /sys/class/power_supply/battery/uevent ] && grep -E 'VOLTAGE|TEMP|CAPACITY|STATUS' /sys/class/power_supply/battery/uevent; echo \"Uptime  : $(uptime)\"; echo '==============================='; }\n");
                                defs.append("cd /sdcard 2>/dev/null || cd \"" + mContext.getFilesDir().getAbsolutePath() + "/root\"\n");
                                write(defs.toString());
                            } catch (Exception ignored) {}
                        }
                    }).start();

                    byte[] buf = new byte[4096];
                    int read;
                    while (mRunning && (read = mStdout.read(buf)) != -1) {
                        if (mListener != null) {
                            String s = new String(buf, 0, read);
                            // Filter out any stray tty job control warning lines
                            if (s.contains("can't access tty") || s.contains("job control turned off")) {
                                s = s.replaceAll("(?m)^.*can't access tty.*$(\\r?\\n)?", "");
                                s = s.replaceAll("(?m)^.*job control turned off.*$(\\r?\\n)?", "");
                                byte[] clean = s.getBytes();
                                if (clean.length > 0) {
                                    mListener.onOutput(clean, 0, clean.length);
                                }
                                continue;
                            }
                            mListener.onOutput(buf, 0, read);
                        }
                    }

                    int exitCode = -1;
                    try { exitCode = mProcess.waitFor(); } catch (Exception ignored) {}
                    mRunning = false;

                    Log.w(TAG, "Session ended, exit code: " + exitCode);
                    if (mListener != null) {
                        mListener.onProcessExited(exitCode);
                    }

                } catch (Throwable e) {
                    Log.e(TAG, "Terminal launch failed", e);
                    mRunning = false;
                    if (mListener != null) {
                        String msg = "\r\n\u001B[1;31m[Launch Error: " + e.getMessage() + "]\u001B[0m\r\n";
                        byte[] b = msg.getBytes();
                        mListener.onOutput(b, 0, b.length);
                        mListener.onProcessExited(-1);
                    }
                }
            }
        }).start();
    }

    public void write(byte[] data) {
        if (mStdin == null || !mRunning || data == null) return;
        try {
            mStdin.write(data);
            mStdin.flush();
        } catch (Exception e) {
            Log.e(TAG, "Write error", e);
        }
    }

    public void write(String str) {
        if (str == null) return;
        write(str.getBytes());
    }

    public void kill() {
        mRunning = false;
        if (mProcess != null) {
            try { mProcess.destroy(); } catch (Exception ignored) {}
        }
    }
}
