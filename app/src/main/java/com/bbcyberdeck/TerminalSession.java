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
                    File proot = new File(binDir, "proot");
                    File rootfs = new File(filesDir, "rootfs");
                    File rootDir = new File(filesDir, "root");
                    File tmpDir = new File(filesDir, "tmp");

                    // 1. Check if device has SU (real root)
                    boolean hasSu = false;
                    String suPath = null;
                    String[] suCandidates = new String[]{
                            "/system/xbin/su", "/system/bin/su", "/sbin/su", "/vendor/bin/su"
                    };
                    for (String candidate : suCandidates) {
                        File f = new File(candidate);
                        if (f.exists() && f.canExecute()) {
                            hasSu = true;
                            suPath = candidate;
                            break;
                        }
                    }

                    // 2. Check if PRoot works or segfaults (test --version)
                    boolean prootWorks = false;
                    if (proot.exists() && proot.canExecute()) {
                        try {
                            ProcessBuilder testPb = new ProcessBuilder(proot.getAbsolutePath(), "--version");
                            testPb.redirectErrorStream(true);
                            Process testP = testPb.start();
                            int exit = testP.waitFor();
                            if (exit == 0) {
                                prootWorks = true;
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "PRoot test failed: " + t.getMessage());
                        }
                    }

                    // Determine command to run
                    List<String> cmd = new ArrayList<String>();
                    File workDir = rootDir;

                    if (prootWorks) {
                        Log.i(TAG, "Using PRoot container mode");
                        cmd.add(proot.getAbsolutePath());
                        cmd.add("--kill-on-exit");
                        cmd.add("-0");
                        cmd.add("-r");
                        cmd.add(rootfs.getAbsolutePath());
                        cmd.add("-b");
                        cmd.add("/dev");
                        cmd.add("-b");
                        cmd.add("/proc");
                        cmd.add("-b");
                        cmd.add("/sys");

                        File hostSdcard = new File("/sdcard");
                        File guestSdcard = new File(rootfs, "sdcard");
                        if (hostSdcard.exists() && guestSdcard.exists()) {
                            cmd.add("-b");
                            cmd.add("/sdcard:/sdcard");
                        }

                        cmd.add("-w");
                        cmd.add("/root");
                        cmd.add("/bin/sh");
                        cmd.add("-l");
                        workDir = rootfs;
                    } else if (hasSu) {
                        Log.i(TAG, "Using real SU root shell: " + suPath);
                        cmd.add(suPath);
                        workDir = rootDir;
                    } else {
                        // Use static Musl BusyBox shell directly
                        Log.i(TAG, "Using native static BusyBox shell: " + busybox.getAbsolutePath());
                        cmd.add(busybox.getAbsolutePath());
                        cmd.add("sh");
                        cmd.add("-l");
                        workDir = rootDir;
                    }

                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.directory(workDir);
                    pb.redirectErrorStream(true);

                    Map<String, String> env = pb.environment();
                    env.put("PROOT_TMP_DIR", tmpDir.getAbsolutePath());
                    env.put("TMPDIR", tmpDir.getAbsolutePath());
                    env.put("HOME", rootDir.getAbsolutePath());
                    env.put("USER", "root");
                    env.put("TERM", "xterm-256color");
                    env.put("PATH", binDir.getAbsolutePath() + ":" + rootfs.getAbsolutePath() + "/bin:/usr/local/bin:/usr/bin:/bin:/system/xbin:/system/bin:/sbin");
                    env.put("SHELL", busybox.getAbsolutePath());
                    env.put("LANG", "C.UTF-8");
                    env.put("PROOT_NO_SECCOMP", "1");

                    Log.i(TAG, "Starting shell: " + cmd);
                    mProcess = pb.start();
                    mStdin = mProcess.getOutputStream();
                    mStdout = mProcess.getInputStream();
                    mRunning = true;

                    // If running Busybox directly, read .profile manually if not loaded
                    if (!prootWorks && !hasSu) {
                        File prof = new File(rootDir, ".profile");
                        if (prof.exists()) {
                            write(". " + prof.getAbsolutePath() + "\n");
                        }
                    }

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
