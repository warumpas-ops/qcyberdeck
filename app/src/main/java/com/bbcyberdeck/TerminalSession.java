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
        if (mListener != null) {
            byte[] b = (msg + "\r\n").getBytes();
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

                    // --- Choose the shell ---
                    // Priority: busybox sh (if binary runs) → /system/bin/sh (always works)
                    List<String> cmd = new ArrayList<String>();
                    boolean usingBusybox = false;

                    if (busybox.exists() && busybox.canExecute()) {
                        // Quick smoke-test: busybox true (exit 0 means it works)
                        try {
                            Process t = Runtime.getRuntime().exec(
                                new String[]{busybox.getAbsolutePath(), "true"});
                            int code = t.waitFor();
                            if (code == 0) {
                                usingBusybox = true;
                            }
                        } catch (Throwable ignored) {}
                    }

                    if (usingBusybox) {
                        Log.i(TAG, "Shell: busybox sh");
                        cmd.add(busybox.getAbsolutePath());
                        cmd.add("sh");
                    } else {
                        Log.i(TAG, "Shell: /system/bin/sh");
                        cmd.add("/system/bin/sh");
                    }

                    // --- Build PATH: busybox dir first so its applets shadow system ones ---
                    // Even if busybox --install didn't create symlinks, we can still call
                    // `busybox <applet>` manually; but most things we need are in /system/bin anyway.
                    String pathVal = binDir.getAbsolutePath()
                            + ":/system/xbin:/system/bin:/sbin:/vendor/bin";

                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.directory(rootDir);
                    pb.redirectErrorStream(true);

                    Map<String, String> env = pb.environment();
                    env.put("PATH",    pathVal);
                    env.put("HOME",    rootDir.getAbsolutePath());
                    env.put("TMPDIR",  tmpDir.getAbsolutePath());
                    env.put("USER",    "root");
                    env.put("LOGNAME", "root");
                    env.put("TERM",    "xterm-256color");
                    // Remove vars that could confuse a minimal shell
                    env.remove("LD_PRELOAD");
                    env.remove("LD_LIBRARY_PATH");

                    Log.i(TAG, "Starting: " + cmd + " PATH=" + pathVal);
                    mProcess = pb.start();
                    mStdin   = mProcess.getOutputStream();
                    mStdout  = mProcess.getInputStream();
                    mRunning = true;

                    // --- Send init script after tiny delay so shell stdin is ready ---
                    // RULES for the init script:
                    //   1. Use ONLY shell built-ins or commands guaranteed by /system/bin/sh
                    //      (echo, printf, cd, export, alias — all built-in in ash/dash/sh)
                    //   2. NO `clear` — it needs terminfo / /usr/bin/clear which may not exist
                    //      Instead print a few blank lines to visually clear the screen
                    //   3. NO `echo -e` — use printf instead (POSIX, works in all Android shells)
                    //   4. Set PS1 LAST so the prompt appears after the banner
                    //   5. Everything uses only single-byte characters — no UTF-8 art that
                    //      might confuse the Q5 font renderer
                    final boolean bb = usingBusybox;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(300);

                                // Blank lines act as a soft clear
                                StringBuilder sb = new StringBuilder();
                                sb.append("\n\n\n\n\n");

                                // Banner — uses only printf which is a shell built-in
                                sb.append("printf '\\033[1;32m================================================\\033[0m\\n'\n");
                                sb.append("printf '\\033[1;32m  QCyberdeck - BlackBerry Q5 Linux Shell\\033[0m\\n'\n");
                                sb.append("printf '\\033[1;32m================================================\\033[0m\\n'\n");
                                sb.append("printf '  Device : BlackBerry Q5 (MSM8960 2GB)\\n'\n");
                                if (bb) {
                                    sb.append("printf '  Shell  : BusyBox ash (350+ utilities)\\n'\n");
                                } else {
                                    sb.append("printf '  Shell  : Android system sh\\n'\n");
                                }
                                sb.append("printf '  Try    : ls  ps  top  ifconfig  vi  wget\\n'\n");
                                sb.append("printf '\\033[1;32m------------------------------------------------\\033[0m\\n'\n");
                                sb.append("printf '\\n'\n");

                                // Set prompt AFTER the banner
                                sb.append("export PS1='\\033[1;32mroot@q5\\033[0m:\\033[1;34m\\w\\033[0m# '\n");

                                // cd to home
                                sb.append("cd \"" + mContext.getFilesDir().getAbsolutePath() + "/root\" 2>/dev/null\n");

                                write(sb.toString());
                            } catch (Exception ignored) {}
                        }
                    }).start();

                    // --- Read loop ---
                    byte[] buf = new byte[4096];
                    int read;
                    while (mRunning && (read = mStdout.read(buf)) != -1) {
                        if (mListener != null) {
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
                        String msg = "\r\n\033[1;31m[LAUNCH ERROR: " + e.getMessage() + "]\033[0m\r\n";
                        byte[] b = msg.getBytes();
                        mListener.onOutput(b, 0, b.length);
                        mListener.onProcessExited(-1);
                    }
                }
            }
        }).start();
    }

    public void write(byte[] data) {
        if (mStdin == null || !mRunning) return;
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
