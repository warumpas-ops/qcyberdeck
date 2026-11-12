package com.bbcyberdeck;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public class Installer {
    private static final String TAG = "Installer";
    private static final String VERSION_MARKER = ".installed_v3";

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

                    File rootfsDir = new File(filesDir, "rootfs");
                    rootfsDir.mkdirs();

                    // 1. Install Static Musl Busybox (Guaranteed to execute on any ARM)
                    callback.onProgress("[1/5] Installing native Linux core (BusyBox)...");
                    File busyboxFile = new File(binDir, "busybox");
                    copyAsset(context, "busybox", busyboxFile);
                    busyboxFile.setReadable(true, false);
                    busyboxFile.setExecutable(true, false);
                    try {
                        Runtime.getRuntime().exec(new String[]{"chmod", "755", busyboxFile.getAbsolutePath()}).waitFor();
                    } catch (Exception ignored) {}

                    // 2. Install proot engine
                    callback.onProgress("[2/5] Installing PRoot engine...");
                    File prootFile = new File(binDir, "proot");
                    try {
                        copyAsset(context, "proot", prootFile);
                        prootFile.setReadable(true, false);
                        prootFile.setExecutable(true, false);
                        Runtime.getRuntime().exec(new String[]{"chmod", "755", prootFile.getAbsolutePath()}).waitFor();
                    } catch (Exception ignored) {}

                    // 3. Extract Alpine rootfs
                    callback.onProgress("[3/5] Extracting Linux environment...");
                    InputStream is = openRootfsStream(context);
                    if (is != null) {
                        try {
                            TarExtractor.extractTarGz(is, rootfsDir, new TarExtractor.ProgressListener() {
                                @Override
                                public void onProgress(String file, int count) {
                                    callback.onProgress("[3/5] Extracting packages (" + count + " files)...");
                                }
                            });
                        } catch (Exception e) {
                            Log.w(TAG, "Tar extraction non-fatal error: " + e.getMessage());
                        }
                    }

                    // 4. Configure system symlinks & directories
                    callback.onProgress("[4/5] Configuring directories & symlinks...");
                    new File(rootfsDir, "dev").mkdirs();
                    new File(rootfsDir, "proc").mkdirs();
                    new File(rootfsDir, "sys").mkdirs();
                    new File(rootfsDir, "sdcard").mkdirs();
                    new File(rootfsDir, "tmp").mkdirs();
                    new File(rootfsDir, "root").mkdirs();

                    // Ensure bin/sh exists inside rootfs
                    File rBusybox = new File(rootfsDir, "bin/busybox");
                    File rSh = new File(rootfsDir, "bin/sh");
                    if (!rSh.exists() || rSh.length() == 0) {
                        copyFile(busyboxFile, rSh);
                    }
                    if (!rBusybox.exists() || rBusybox.length() == 0) {
                        copyFile(busyboxFile, rBusybox);
                    }

                    // Install busybox symlinks into usr/bin
                    try {
                        Process p = Runtime.getRuntime().exec(new String[]{
                                busyboxFile.getAbsolutePath(), "--install", "-s", binDir.getAbsolutePath()
                        });
                        p.waitFor();
                    } catch (Exception ignored) {}

                    // 5. Setup root profile & MOTD
                    callback.onProgress("[5/5] Finalizing shell profile...");
                    File profile = new File(rootDir, ".profile");
                    FileOutputStream pFos = new FileOutputStream(profile);
                    String motd = "export PS1='\\[\\033[01;32m\\]root@q5\\[\\033[00m\\]:\\[\\033[01;34m\\]\\w\\[\\033[00m\\]# '\n"
                            + "export TERM=xterm-256color\n"
                            + "export HOME=" + rootDir.getAbsolutePath() + "\n"
                            + "export USER=root\n"
                            + "export PATH=" + binDir.getAbsolutePath() + ":" + rootfsDir.getAbsolutePath() + "/bin:/usr/local/bin:/usr/bin:/bin:/system/xbin:/system/bin:/sbin\n"
                            + "alias ll='ls -la'\n"
                            + "echo -e \"\\033[1;32m====================================================\\033[0m\"\n"
                            + "echo -e \"\\033[1;32m   ___   _____      __               __          __  \\033[0m\"\n"
                            + "echo -e \"\\033[1;32m  / _ \\ / ___/_ __ / /  ___ _______ / /__ ____  / /__\\033[0m\"\n"
                            + "echo -e \"\\033[1;32m / // // /__/ // // _ \\/ -_) __/ _ /  '_// -_)/ // _/\\033[0m\"\n"
                            + "echo -e \"\\033[1;32m \\__\\_\\\\___/\\_, //_.__/\\__/_/  \\__/_/\\_\\\\__/_//_/\\__\\ \\033[0m\"\n"
                            + "echo -e \"\\033[1;32m           /___/                                     \\033[0m\"\n"
                            + "echo -e \"\\033[1;32m====================================================\\033[0m\"\n"
                            + "echo -e \"  Hardware: BlackBerry Q5 (Qualcomm MSM8960 2GB)\"\n"
                            + "echo -e \"  OS:       Native Linux Shell (BusyBox 1.31 static musl)\"\n"
                            + "echo -e \"  Storage:  /sdcard & internal storage mounted\"\n"
                            + "echo -e \"  Prompt:   \\033[1;31mroot@q5:#\\033[0m (Interactive Cyberdeck)\"\n"
                            + "echo -e \"----------------------------------------------------\"\n"
                            + "echo -e \"  Commands: \\033[1;33mls, ps, top, vi, grep, awk, tar, wget, ping\\033[0m\"\n"
                            + "echo -e \"\\033[1;32m====================================================\\033[0m\\n\"\n";
                    pFos.write(motd.getBytes());
                    pFos.close();

                    // Also copy profile to rootfs/root/.profile
                    try {
                        copyFile(profile, new File(rootfsDir, "root/.profile"));
                    } catch (Exception ignored) {}

                    // Mark installed
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

    private static InputStream openRootfsStream(Context context) {
        String[] candidates = new String[]{"rootfs.tar.gz", "rootfs.tar", "alpine-minirootfs.tar.gz", "alpine-minirootfs.tar"};
        for (String c : candidates) {
            try {
                InputStream is = context.getAssets().open(c);
                if (is != null) return is;
            } catch (Exception ignored) {}
        }
        return null;
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

    private static void copyFile(File src, File dst) {
        try {
            FileInputStream in = new FileInputStream(src);
            FileOutputStream out = new FileOutputStream(dst);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            in.close();
            out.close();
            dst.setReadable(true, false);
            dst.setExecutable(true, false);
        } catch (Exception ignored) {}
    }
}
