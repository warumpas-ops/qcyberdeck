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
                    File proot = new File(filesDir, "usr/bin/proot");
                    File rootfs = new File(filesDir, "rootfs");

                    List<String> cmd = new ArrayList<String>();
                    cmd.add(proot.getAbsolutePath());
                    // Force fake root (UID 0, GID 0)
                    cmd.add("-0");
                    // Chroot to rootfs
                    cmd.add("-r");
                    cmd.add(rootfs.getAbsolutePath());
                    // Bind mount /sdcard if available
                    File sdcard = new File("/sdcard");
                    if (sdcard.exists()) {
                        cmd.add("-b");
                        cmd.add("/sdcard:/sdcard");
                    }
                    // Bind mount /dev, /proc, /sys
                    cmd.add("-b");
                    cmd.add("/dev");
                    cmd.add("-b");
                    cmd.add("/proc");
                    cmd.add("-b");
                    cmd.add("/sys");
                    // Working directory
                    cmd.add("-w");
                    cmd.add("/root");
                    // Execute login shell
                    cmd.add("/bin/sh");
                    cmd.add("-l");

                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.directory(rootfs);
                    pb.redirectErrorStream(true);

                    Map<String, String> env = pb.environment();
                    env.put("HOME", "/root");
                    env.put("USER", "root");
                    env.put("TERM", "xterm-256color");
                    env.put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
                    env.put("SHELL", "/bin/sh");
                    env.put("LANG", "C.UTF-8");
                    env.put("PROOT_NO_SECCOMP", "1"); // Critical for older Android kernels like 4.3!

                    mProcess = pb.start();
                    mStdin = mProcess.getOutputStream();
                    mStdout = mProcess.getInputStream();
                    mRunning = true;

                    byte[] buf = new byte[4096];
                    int read;
                    while (mRunning && (read = mStdout.read(buf)) != -1) {
                        if (mListener != null) {
                            mListener.onOutput(buf, 0, read);
                        }
                    }

                    int exitCode = mProcess.waitFor();
                    mRunning = false;
                    if (mListener != null) {
                        mListener.onProcessExited(exitCode);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Process failed", e);
                    mRunning = false;
                    if (mListener != null) {
                        byte[] err = ("\r\n[CRITICAL ERROR: " + e.getMessage() + "]\r\n").getBytes();
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
