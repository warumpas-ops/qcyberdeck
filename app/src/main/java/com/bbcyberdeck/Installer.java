package com.bbcyberdeck;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class Installer {
    private static final String TAG = "Installer";
    // Bump version marker → forces re-install on user's device
    private static final String VERSION_MARKER = ".installed_v5";

    public interface InstallCallback {
        void onProgress(String status);
        void onComplete();
        void onError(String error);
    }

    public static boolean isInstalled(Context context) {
        File marker  = new File(context.getFilesDir(), VERSION_MARKER);
        File busybox = new File(context.getFilesDir(), "usr/bin/busybox");
        return marker.exists() && busybox.exists() && busybox.canExecute();
    }

    public static void install(final Context context, final InstallCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File filesDir = context.getFilesDir();

                    // Create directory structure
                    File binDir     = new File(filesDir, "usr/bin");
                    File rootDir    = new File(filesDir, "root");
                    File tmpDir     = new File(filesDir, "tmp");
                    File etcDir     = new File(filesDir, "etc");
                    binDir.mkdirs();
                    rootDir.mkdirs();
                    tmpDir.mkdirs();
                    etcDir.mkdirs();
                    tmpDir.setReadable(true, false);
                    tmpDir.setWritable(true, false);
                    tmpDir.setExecutable(true, false);

                    // 1. Copy BusyBox binary
                    callback.onProgress("[1/3] Installing BusyBox...");
                    File busyboxFile = new File(binDir, "busybox");
                    copyAsset(context, "busybox", busyboxFile);
                    // chmod 755 via multiple methods for maximum compatibility
                    busyboxFile.setReadable(true, false);
                    busyboxFile.setExecutable(true, false);
                    busyboxFile.setWritable(false, false);
                    // Also try runtime chmod in case setExecutable fails on this Android version
                    try {
                        Process p = Runtime.getRuntime().exec(
                            new String[]{"chmod", "755", busyboxFile.getAbsolutePath()});
                        p.waitFor();
                    } catch (Exception ignored) {}

                    // 2. Try busybox --install -s, but DON'T fail if it doesn't work.
                    // On some BB10 Android runtimes this silently fails — that's OK because
                    // TerminalSession will still use busybox directly via full path.
                    callback.onProgress("[2/3] Setting up utilities...");
                    try {
                        Process p = Runtime.getRuntime().exec(new String[]{
                            busyboxFile.getAbsolutePath(), "--install", "-s",
                            binDir.getAbsolutePath()
                        });
                        p.waitFor(); // ignore exit code — it's best-effort
                    } catch (Exception ignored) {}

                    // 3. Write a minimal /etc/profile so interactive shells get PATH
                    callback.onProgress("[3/3] Finalizing...");
                    File profile = new File(etcDir, "profile");
                    writeTextFile(profile,
                        "export PATH=\"" + binDir.getAbsolutePath()
                        + ":/system/xbin:/system/bin:/sbin:/vendor/bin\"\n"
                        + "export HOME=\"" + rootDir.getAbsolutePath() + "\"\n"
                        + "export TERM=xterm-256color\n"
                        + "export USER=root\n"
                    );

                    // Mark as installed
                    File marker = new File(filesDir, VERSION_MARKER);
                    marker.createNewFile();

                    callback.onComplete();

                } catch (Exception e) {
                    Log.e(TAG, "Install failed", e);
                    callback.onError(e.getMessage() != null ? e.getMessage() : "Unknown error");
                }
            }
        }).start();
    }

    private static void copyAsset(Context context, String assetName, File outFile) throws Exception {
        InputStream in = context.getAssets().open(assetName);
        FileOutputStream out = new FileOutputStream(outFile);
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        out.close();
    }

    private static void writeTextFile(File f, String content) {
        try {
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(content.getBytes());
            fos.close();
        } catch (Exception ignored) {}
    }
}
