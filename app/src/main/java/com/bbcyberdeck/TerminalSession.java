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

                    // --- Determine if BusyBox binary executes cleanly ---
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
                        Log.i(TAG, "Launching: busybox sh -i");
                        cmd.add(busybox.getAbsolutePath());
                        cmd.add("sh");
                        cmd.add("-i");
                    } else {
                        Log.i(TAG, "Launching: /system/bin/sh -i");
                        cmd.add("/system/bin/sh");
                        cmd.add("-i");
                    }

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
                    env.put("PS1",     "root@q5:# ");
                    env.remove("LD_PRELOAD");
                    env.remove("LD_LIBRARY_PATH");

                    Log.i(TAG, "Starting process: " + cmd);
                    mProcess = pb.start();
                    mStdin   = mProcess.getOutputStream();
                    mStdout  = mProcess.getInputStream();
                    mRunning = true;

                    // Emit clean, formatted banner directly to screen (zero shell printf calls)
                    final boolean bb = usingBusybox;
                    String banner =
                        "\u001B[1;32m================================================\u001B[0m\r\n" +
                        "\u001B[1;32m  QCyberdeck - BlackBerry Q5 Linux Terminal\u001B[0m\r\n" +
                        "\u001B[1;32m================================================\u001B[0m\r\n" +
                        "  Hardware : BlackBerry Q5 (Qualcomm MSM8960 2GB)\r\n" +
                        "  Core     : " + (bb ? "BusyBox Linux (350+ Unix Utilities)" : "Native System Shell") + "\r\n" +
                        "  Storage  : /sdcard & app storage mounted\r\n" +
                        "  Try      : ls, uname -a, ps, top, ifconfig, vi\r\n" +
                        "\u001B[1;32m------------------------------------------------\u001B[0m\r\n\r\n";
                    emit(banner);

                    // Switch working directory to /sdcard or app root without noisy prints
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(250);
                                write("cd /sdcard 2>/dev/null || cd \"" + mContext.getFilesDir().getAbsolutePath() + "/root\"\n");
                            } catch (Exception ignored) {}
                        }
                    }).start();

                    // Read loop: streams shell output directly to the terminal view
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
                        String msg = "\r\n\u001B[1;31m[LAUNCH ERROR: " + e.getMessage() + "]\u001B[0m\r\n";
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
