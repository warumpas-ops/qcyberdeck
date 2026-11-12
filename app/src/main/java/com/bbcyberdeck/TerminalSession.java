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

    public void start() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File filesDir = mContext.getFilesDir();
                    File binDir = new File(filesDir, "usr/bin");
                    File busybox = new File(binDir, "busybox");
                    File rootDir = new File(filesDir, "root");
                    rootDir.mkdirs();
                    File tmpDir = new File(filesDir, "tmp");
                    tmpDir.mkdirs();

                    // 1. Check if SU (real root) is available on device
                    boolean suFunctional = false;
                    String workingSu = null;
                    String[] suCandidates = new String[]{
                            "/system/xbin/su", "/system/bin/su", "/sbin/su", "/vendor/bin/su", "su"
                    };
                    for (String cand : suCandidates) {
                        try {
                            Process p = Runtime.getRuntime().exec(new String[]{cand, "-c", "id"});
                            if (p.waitFor() == 0) {
                                suFunctional = true;
                                workingSu = cand;
                                break;
                            }
                        } catch (Throwable ignored) {}
                    }

                    // 2. Check if BusyBox is functional
                    boolean busyboxFunctional = false;
                    if (busybox.exists() && busybox.canExecute()) {
                        try {
                            Process p = Runtime.getRuntime().exec(new String[]{busybox.getAbsolutePath(), "true"});
                            if (p.waitFor() == 0) {
                                busyboxFunctional = true;
                            }
                        } catch (Throwable ignored) {}
                    }

                    // Determine shell command to launch
                    List<String> cmd = new ArrayList<String>();
                    if (suFunctional) {
                        Log.i(TAG, "Launching real SU root: " + workingSu);
                        cmd.add(workingSu);
                    } else if (busyboxFunctional) {
                        Log.i(TAG, "Launching BusyBox interactive shell: " + busybox.getAbsolutePath());
                        cmd.add(busybox.getAbsolutePath());
                        cmd.add("sh");
                    } else {
                        Log.i(TAG, "Launching native system shell: /system/bin/sh");
                        cmd.add("/system/bin/sh");
                    }

                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.directory(rootDir);
                    pb.redirectErrorStream(true);

                    Map<String, String> env = pb.environment();
                    env.put("PATH", binDir.getAbsolutePath() + ":/system/xbin:/system/bin:/sbin:/vendor/bin");
                    env.put("TMPDIR", tmpDir.getAbsolutePath());
                    env.put("HOME", rootDir.getAbsolutePath());
                    env.put("USER", "root");
                    env.put("TERM", "xterm-256color");

                    Log.i(TAG, "Starting shell process: " + cmd);
                    mProcess = pb.start();
                    mStdin = mProcess.getOutputStream();
                    mStdout = mProcess.getInputStream();
                    mRunning = true;

                    // Send initialization script into stdin
                    final boolean finalSu = suFunctional;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(200);
                                String initScript =
                                        "export PATH=\"" + binDir.getAbsolutePath() + ":/system/xbin:/system/bin:/sbin:$PATH\"\n" +
                                        "export TERM=xterm-256color\n" +
                                        "export HOME=\"" + rootDir.getAbsolutePath() + "\"\n" +
                                        "export PS1='\\[\\033[01;32m\\]root@q5\\[\\033[00m\\]:\\[\\033[01;34m\\]\\w\\[\\033[00m\\]# '\n" +
                                        "cd \"" + rootDir.getAbsolutePath() + "\"\n" +
                                        "clear\n" +
                                        "echo -e \"\\033[1;32m====================================================\\033[0m\"\n" +
                                        "echo -e \"\\033[1;32m   ___   _____      __               __          __  \\033[0m\"\n" +
                                        "echo -e \"\\033[1;32m  / _ \\ / ___/_ __ / /  ___ _______ / /__ ____  / /__\\033[0m\"\n" +
                                        "echo -e \"\\033[1;32m / // // /__/ // // _ \\/ -_) __/ _ /  '_// -_)/ // _/\\033[0m\"\n" +
                                        "echo -e \"\\033[1;32m \\__\\_\\\\___/\\_, //_.__/\\__/_/  \\__/_/\\_\\\\__/_//_/\\__\\ \\033[0m\"\n" +
                                        "echo -e \"\\033[1;32m           /___/                                     \\033[0m\"\n" +
                                        "echo -e \"\\033[1;32m====================================================\\033[0m\"\n" +
                                        "echo -e \"  Hardware: BlackBerry Q5 (Qualcomm MSM8960 2GB)\"\n" +
                                        (finalSu ?
                                        "echo -e \"  Status:   \\033[1;31mROOT SHELL (UID 0 - REAL ROOT ON DEVICE)\\033[0m\"\n" :
                                        "echo -e \"  Status:   \\033[1;32mONLINE (BusyBox Core + 350+ Unix Utilities)\\033[0m\"\n") +
                                        "echo -e \"  Storage:  /sdcard & device storage mounted\"\n" +
                                        "echo -e \"----------------------------------------------------\"\n" +
                                        "echo -e \"  Try: \\033[1;33mls -la, uname -a, ps, top, ifconfig, vi, wget\\033[0m\"\n" +
                                        "echo -e \"\\033[1;32m====================================================\\033[0m\\n\"\n";
                                write(initScript);
                            } catch (Exception ignored) {}
                        }
                    }).start();

                    byte[] buf = new byte[4096];
                    int read;
                    while (mRunning && (read = mStdout.read(buf)) != -1) {
                        if (mListener != null) {
                            mListener.onOutput(buf, 0, read);
                        }
                    }

                    int exitCode = -1;
                    try {
                        exitCode = mProcess.waitFor();
                    } catch (Exception ignored) {}
                    mRunning = false;

                    Log.w(TAG, "Session ended with exit code: " + exitCode);
                    if (mListener != null) {
                        mListener.onProcessExited(exitCode);
                    }
                } catch (Throwable e) {
                    Log.e(TAG, "Terminal launch failed", e);
                    mRunning = false;
                    if (mListener != null) {
                        String errMsg = "\r\n\033[1;31m[CRITICAL LAUNCH ERROR: " + e.getMessage() + "]\033[0m\r\n";
                        byte[] err = errMsg.getBytes();
                        mListener.onOutput(err, 0, err.length);
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
            try {
                mProcess.destroy();
            } catch (Exception ignored) {}
        }
    }
}
