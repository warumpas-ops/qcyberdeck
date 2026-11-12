package com.bbcyberdeck;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class Installer {
    private static final String TAG = "Installer";
    private static final String VERSION_MARKER = ".installed_v4";

    public interface InstallCallback {
        void onProgress(String status);
        void onComplete();
        void onError(String error);
    }

    public static boolean isInstalled(Context context) {
        File marker = new File(context.getFilesDir(), VERSION_MARKER);
        File busybox = new File(context.getFilesDir(), "usr/bin/busybox");
        return marker.exists() && busybox.exists() && busybox.canExecute();
    }

    public static void install(final Context context, final InstallCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File filesDir = context.getFilesDir();
                    File binDir = new File(filesDir, "usr/bin");
                    binDir.mkdirs();

                    File rootDir = new File(filesDir, "root");
                    rootDir.mkdirs();

                    File hostTmpDir = new File(filesDir, "tmp");
                    hostTmpDir.mkdirs();
                    hostTmpDir.setReadable(true, false);
                    hostTmpDir.setWritable(true, false);
                    hostTmpDir.setExecutable(true, false);

                    // 1. Install Static Musl Busybox (ARMv5TE softfp - runs on all ARM)
                    callback.onProgress("[1/3] Installing native Linux core (BusyBox)...");
                    File busyboxFile = new File(binDir, "busybox");
                    copyAsset(context, "busybox", busyboxFile);
                    busyboxFile.setReadable(true, false);
                    busyboxFile.setExecutable(true, false);
                    try {
                        Runtime.getRuntime().exec(new String[]{"chmod", "755", busyboxFile.getAbsolutePath()}).waitFor();
                    } catch (Exception ignored) {}

                    // 2. Install busybox applets into usr/bin
                    callback.onProgress("[2/3] Setting up 350+ Unix utilities...");
                    try {
                        Process p = Runtime.getRuntime().exec(new String[]{
                                busyboxFile.getAbsolutePath(), "--install", "-s", binDir.getAbsolutePath()
                        });
                        p.waitFor();
                    } catch (Exception ignored) {}

                    // 3. Finalize setup
                    callback.onProgress("[3/3] Finalizing Cyberdeck shell...");
                    File marker = new File(filesDir, VERSION_MARKER);
                    marker.createNewFile();

                    callback.onComplete();
                } catch (Exception e) {
                    Log.e(TAG, "Install failed", e);
                    callback.onError(e.getMessage());
                }
            }
        }).start();
    }

    private static void copyAsset(Context context, String assetName, File outFile) throws Exception {
        InputStream in = context.getAssets().open(assetName);
        FileOutputStream out = new FileOutputStream(outFile);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        out.close();
    }
}
