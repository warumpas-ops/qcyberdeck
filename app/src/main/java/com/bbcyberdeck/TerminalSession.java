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
                    File tmpDir = new File(filesDir, "tmp");
                    tmpDir.mkdirs();
                    tmpDir.setReadable(true, false);
                    tmpDir.setWritable(true, false);
                    tmpDir.setExecutable(true, false);

                    // Ensure proot is executable
                    proot.setReadable(true, false);
                    proot.setExecutable(true, false);

                    List<String> cmd = new ArrayList<String>();
                    cmd.add(proot.getAbsolutePath());
                    cmd.add("--kill-on-exit");
                    // Force fake root (UID 0, GID 0)
                    cmd.add("-0");
                    // Chroot to rootfs
                    cmd.add("-r");
                    cmd.add(rootfs.getAbsolutePath());

                    // Bind mount /dev, /proc, /sys
                    cmd.add("-b");
                    cmd.add("/dev");
                    cmd.add("-b");
                    cmd.add("/proc");
                    cmd.add("-b");
                    cmd.add("/sys");

                    // Bind mount /sdcard only if both host and guest directories exist
                    File hostSdcard = new File("/sdcard");
                    File guestSdcard = new File(rootfs, "sdcard");
                    if (hostSdcard.exists() && guestSdcard.exists()) {
                        cmd.add("-b");
                        cmd.add("/sdcard:/sdcard");
                    }

                    // Working directory inside container
                    cmd.add("-w");
                    cmd.add("/root");

                    // Select shell: prefer /bin/sh, fallback to /bin/busybox sh
                    File shFile = new File(rootfs, "bin/sh");
                    if (shFile.exists() && shFile.canExecute()) {
                        cmd.add("/bin/sh");
                        cmd.add("-l");
                    } else {
                        cmd.add("/bin/busybox");
                        cmd.add("sh");
                        cmd.add("-l");
                    }

                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.directory(rootfs);
                    pb.redirectErrorStream(true);

                    Map<String, String> env = pb.environment();
                    env.put("PROOT_TMP_DIR", tmpDir.getAbsolutePath());
                    env.put("TMPDIR", tmpDir.getAbsolutePath());
                    env.put("HOME", "/root");
                    env.put("USER", "root");
                    env.put("TERM", "xterm-256color");
                    env.put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
                    env.put("SHELL", "/bin/sh");
                    env.put("LANG", "C.UTF-8");
                    env.put("PROOT_NO_SECCOMP", "1"); // Critical for older Android kernels like 4.3!

                    Log.i(TAG, "Launching PRoot: " + cmd);
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

                    int exitCode = -1;
                    try {
                        exitCode = mProcess.waitFor();
                    } catch (Exception ignored) {}
                    mRunning = false;

                    Log.w(TAG, "Process exited with code: " + exitCode);
                    if (mListener != null) {
                        mListener.onProcessExited(exitCode);
                    }
                } catch (Throwable e) {
                    Log.e(TAG, "Process start failed", e);
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
