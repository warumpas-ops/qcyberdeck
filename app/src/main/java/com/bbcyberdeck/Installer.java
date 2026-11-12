package com.bbcyberdeck;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class Installer {
    private static final String TAG = "Installer";

    public interface InstallCallback {
        void onProgress(String status);
        void onComplete();
        void onError(String error);
    }

    public static boolean isInstalled(Context context) {
        File proot = new File(context.getFilesDir(), "usr/bin/proot");
        File sh = new File(context.getFilesDir(), "rootfs/bin/sh");
        return proot.exists() && proot.canExecute() && sh.exists();
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

                    // 1. Install proot
                    callback.onProgress("[1/4] Installing PRoot binary...");
                    File prootFile = new File(binDir, "proot");
                    copyAsset(context, "proot", prootFile);
                    prootFile.setExecutable(true, false);
                    try {
                        Runtime.getRuntime().exec(new String[]{"chmod", "755", prootFile.getAbsolutePath()}).waitFor();
                    } catch (Exception ignored) {}

                    // 2. Extract Alpine rootfs
                    callback.onProgress("[2/4] Extracting Alpine Linux rootfs...");
                    InputStream is = openRootfsStream(context);
                    if (is == null) {
                        throw new Exception("rootfs archive not found in assets");
                    }

                    TarExtractor.extractTarGz(is, rootfsDir, new TarExtractor.ProgressListener() {
                        @Override
                        public void onProgress(String file, int count) {
                            callback.onProgress("[2/4] Extracting rootfs (" + count + " files)...");
                        }
                    });

                    // 3. Configure DNS
                    callback.onProgress("[3/4] Configuring network & DNS...");
                    File etcDir = new File(rootfsDir, "etc");
                    etcDir.mkdirs();
                    File resolv = new File(etcDir, "resolv.conf");
                    FileOutputStream fos = new FileOutputStream(resolv);
                    fos.write("nameserver 8.8.8.8\nnameserver 1.1.1.1\n".getBytes());
                    fos.close();

                    // 4. Configure MOTD and shell profile
                    callback.onProgress("[4/4] Setting up root profile & MOTD...");
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
                            + "clear\n"
                            + "echo -e \"\\033[1;32m====================================================\\033[0m\"\n"
                            + "echo -e \"\\033[1;32m   ___   _____      __               __          __  \\033[0m\"\n"
                            + "echo -e \"\\033[1;32m  / _ \\ / ___/_ __ / /  ___ _______ / /__ ____  / /__\\033[0m\"\n"
                            + "echo -e \"\\033[1;32m / // // /__/ // // _ \\/ -_) __/ _ /  '_// -_)/ // _/\\033[0m\"\n"
                            + "echo -e \"\\033[1;32m \\__\\_\\\\___/\\_, //_.__/\\__/_/  \\__/_/\\_\\\\__/_//_/\\__\\ \\033[0m\"\n"
                            + "echo -e \"\\033[1;32m           /___/                                     \\033[0m\"\n"
                            + "echo -e \"\\033[1;32m====================================================\\033[0m\"\n"
                            + "echo -e \"  OS:       Alpine Linux (v3.19 armhf)\"\n"
                            + "echo -e \"  Hardware: BlackBerry Q5 (Qualcomm MSM8960 Krait 2GB)\"\n"
                            + "echo -e \"  User:     \\033[1;31mroot (UID 0)\\033[0m\"\n"
                            + "echo -e \"  Storage:  /sdcard mounted at /sdcard\"\n"
                            + "echo -e \"----------------------------------------------------\"\n"
                            + "echo -e \"  Try: \\033[1;33mapk update\\033[0m && \\033[1;33mapk add python3 git nano htop\\033[0m\"\n"
                            + "echo -e \"\\033[1;32m====================================================\\033[0m\\n\"\n";
                    pFos.write(motd.getBytes());
                    pFos.close();

                    // Create required virtual dirs
                    new File(rootfsDir, "tmp").mkdirs();
                    new File(rootfsDir, "dev").mkdirs();
                    new File(rootfsDir, "proc").mkdirs();
                    new File(rootfsDir, "sys").mkdirs();

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
}
