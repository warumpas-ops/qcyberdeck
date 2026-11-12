package com.bbcyberdeck;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class Installer {
    private static final String TAG = "Installer";
    // Version v6: Minimalist UI + Full BusyBox symlinks + Library path fix
    private static final String VERSION_MARKER = ".installed_v6";

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

                    // 1. Copy Static BusyBox Binary
                    callback.onProgress("installing core...");
                    File busyboxFile = new File(binDir, "busybox");
                    copyAsset(context, "busybox", busyboxFile);
                    busyboxFile.setReadable(true, false);
                    busyboxFile.setExecutable(true, false);
                    try {
                        Runtime.getRuntime().exec(
                            new String[]{"chmod", "755", busyboxFile.getAbsolutePath()}).waitFor();
                    } catch (Exception ignored) {}

                    // 2. Set up symlinks for all primary Unix utilities
                    callback.onProgress("linking tools...");
                    String[] applets = new String[]{
                        "whoami", "id", "ls", "cat", "ps", "top", "vi", "grep", "pwd",
                        "mkdir", "rm", "cp", "mv", "chmod", "chown", "touch", "echo",
                        "clear", "find", "sed", "awk", "tar", "gzip", "wget", "curl",
                        "df", "free", "uname", "uptime", "head", "tail", "more", "less",
                        "which", "kill", "sleep", "date", "env", "true", "false", "test",
                        "wc", "sort", "uniq", "cut", "tr", "tee", "xargs", "strings",
                        "du", "netstat", "ifconfig", "ping", "stat", "basename", "dirname"
                    };

                    for (String app : applets) {
                        File link = new File(binDir, app);
                        if (!link.exists()) {
                            try {
                                Runtime.getRuntime().exec(new String[]{
                                    "ln", "-s", busyboxFile.getAbsolutePath(), link.getAbsolutePath()
                                }).waitFor();
                            } catch (Exception ignored) {}
                        }
                    }

                    // Best effort --install -s
                    try {
                        Process p = Runtime.getRuntime().exec(new String[]{
                            busyboxFile.getAbsolutePath(), "--install", "-s",
                            binDir.getAbsolutePath()
                        });
                        p.waitFor();
                    } catch (Exception ignored) {}

                    // 3. Mark as installed
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
}
