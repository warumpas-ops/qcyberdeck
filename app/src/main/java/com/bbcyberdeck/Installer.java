package com.bbcyberdeck;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public class Installer {
    private static final String TAG = "Installer";
    private static final String VERSION_MARKER = ".installed_v2";

    public interface InstallCallback {
        void onProgress(String status);
        void onComplete();
        void onError(String error);
    }

    public static boolean isInstalled(Context context) {
        File marker = new File(context.getFilesDir(), VERSION_MARKER);
        File proot = new File(context.getFilesDir(), "usr/bin/proot");
        File busybox = new File(context.getFilesDir(), "rootfs/bin/busybox");
        return marker.exists() && proot.exists() && busybox.exists();
    }

    public static void install(final Context context, final InstallCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File filesDir = context.getFilesDir();
                    File binDir = new File(filesDir, "usr/bin");
                    binDir.mkdirs();

                    File rootfsDir = new File(filesDir, "rootfs");
                    rootfsDir.mkdirs();

                    File hostTmpDir = new File(filesDir, "tmp");
                    hostTmpDir.mkdirs();
                    hostTmpDir.setReadable(true, false);
                    hostTmpDir.setWritable(true, false);
                    hostTmpDir.setExecutable(true, false);

                    // 1. Install proot binary
                    callback.onProgress("[1/5] Installing PRoot engine...");
                    File prootFile = new File(binDir, "proot");
                    copyAsset(context, "proot", prootFile);
                    prootFile.setReadable(true, false);
                    prootFile.setExecutable(true, false);
                    try {
                        Runtime.getRuntime().exec(new String[]{"chmod", "755", prootFile.getAbsolutePath()}).waitFor();
                    } catch (Exception ignored) {}

                    // 2. Extract Alpine rootfs
                    callback.onProgress("[2/5] Extracting Alpine Linux rootfs...");
                    InputStream is = openRootfsStream(context);
                    if (is == null) {
                        throw new Exception("rootfs archive not found in APK assets");
                    }

                    TarExtractor.extractTarGz(is, rootfsDir, new TarExtractor.ProgressListener() {
                        @Override
                        public void onProgress(String file, int count) {
                            callback.onProgress("[2/5] Extracting files (" + count + " unpacked)...");
                        }
                    });

                    // 3. Fix critical binaries & symlinks
                    callback.onProgress("[3/5] Setting up system binaries & permissions...");
                    File busyboxFile = new File(rootfsDir, "bin/busybox");
                    File shFile = new File(rootfsDir, "bin/sh");
                    File ashFile = new File(rootfsDir, "bin/ash");

                    // Ensure bin/sh exists as a real executable if symlink failed
                    if (!shFile.exists() || shFile.length() == 0) {
                        if (busyboxFile.exists()) {
                            copyFile(busyboxFile, shFile);
                        }
                    }
                    if (!ashFile.exists() || ashFile.length() == 0) {
                        if (busyboxFile.exists()) {
                            copyFile(busyboxFile, ashFile);
                        }
                    }

                    // Recursively chmod rootfs to ensure all binaries and .so libraries are executable
                    try {
                        Runtime.getRuntime().exec(new String[]{"chmod", "-R", "755", rootfsDir.getAbsolutePath()}).waitFor();
                    } catch (Exception ignored) {}

                    // 4. Create required virtual directories
                    callback.onProgress("[4/5] Configuring file system & DNS...");
                    new File(rootfsDir, "dev").mkdirs();
                    new File(rootfsDir, "proc").mkdirs();
                    new File(rootfsDir, "sys").mkdirs();
                    new File(rootfsDir, "sdcard").mkdirs();
                    File rootfsTmp = new File(rootfsDir, "tmp");
                    rootfsTmp.mkdirs();
                    rootfsTmp.setReadable(true, false);
                    rootfsTmp.setWritable(true, false);
                    rootfsTmp.setExecutable(true, false);

                    File etcDir = new File(rootfsDir, "etc");
                    etcDir.mkdirs();
                    File resolv = new File(etcDir, "resolv.conf");
                    FileOutputStream fos = new FileOutputStream(resolv);
                    fos.write("nameserver 8.8.8.8\nnameserver 1.1.1.1\n".getBytes());
                    fos.close();

                    // 5. Configure MOTD and shell profile
                    callback.onProgress("[5/5] Finalizing root environment...");
                    File rootDir = new File(rootfsDir, "root");
                    rootDir.mkdirs();
                    File profile = new File(rootDir, ".profile");
                    FileOutputStream pFos = new FileOutputStream(profile);
                    String motd = "export PS1='\\[\\033[01;32m\\]root@q5\\[\\033[00m\\]:\\[\\033[01;34m\\]\\w\\[\\033[00m\\]# '\n"
                            + "export TERM=xterm-256color\n"
                            + "export HOME=/root\n"
                            + "export USER=root\n"
                            + "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\n"
                            + "alias ll='ls -la'\n"
                            + "echo -e \"\\033[1;32m====================================================\\033[0m\"\n"
                            + "echo -e \"\\033[1;32m   ___   _____      __               __          __  \\033[0m\"\n"
                            + "echo -e \"\\033[1;32m  / _ \\ / ___/_ __ / /  ___ _______ / /__ ____  / /__\\033[0m\"\n"
                            + "echo -e \"\\033[1;32m / // // /__/ // // _ \\/ -_) __/ _ /  '_// -_)/ // _/\\033[0m\"\n"
                            + "echo -e \"\\033[1;32m \\__\\_\\\\___/\\_, //_.__/\\__/_/  \\__/_/\\_\\\\__/_//_/\\__\\ \\033[0m\"\n"
                            + "echo -e \"\\033[1;32m           /___/                                     \\033[0m\"\n"
                            + "echo -e \"\\033[1;32m====================================================\\033[0m\"\n"
                            + "echo -e \"  OS:       Alpine Linux (v3.19 armhf)\"\n"
                            + "echo -e \"  Hardware: BlackBerry Q5 (Qualcomm MSM8960 2GB)\"\n"
                            + "echo -e \"  User:     \\033[1;31mroot (UID 0)\\033[0m (PRoot sandbox)\"\n"
                            + "echo -e \"  Storage:  /sdcard mounted at /sdcard\"\n"
                            + "echo -e \"----------------------------------------------------\"\n"
                            + "echo -e \"  Package manager: \\033[1;33mapk update && apk add python3\\033[0m\"\n"
                            + "echo -e \"\\033[1;32m====================================================\\033[0m\\n\"\n";
                    pFos.write(motd.getBytes());
                    pFos.close();

                    // Touch version marker
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
        try {
            for (String a : context.getAssets().list("")) {
                if (a.contains("rootfs") || a.contains("alpine")) {
                    return context.getAssets().open(a);
                }
            }
        } catch (Exception ignored) {}
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
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            in.close();
            out.close();
            dst.setReadable(true, false);
            dst.setExecutable(true, false);
        } catch (Exception ignored) {}
    }
}
