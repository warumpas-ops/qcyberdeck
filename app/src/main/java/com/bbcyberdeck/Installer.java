package com.bbcyberdeck;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class Installer {
    private static final String TAG = "Installer";
    // Version v7: Root identity wrappers (whoami -> root, id -> uid=0) + help utility
    private static final String VERSION_MARKER = ".installed_v7";

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

                    // 1. Install Static Musl Busybox
                    callback.onProgress("installing core...");
                    File busyboxFile = new File(binDir, "busybox");
                    copyAsset(context, "busybox", busyboxFile);
                    busyboxFile.setReadable(true, false);
                    busyboxFile.setExecutable(true, false);
                    try {
                        Runtime.getRuntime().exec(
                            new String[]{"chmod", "755", busyboxFile.getAbsolutePath()}).waitFor();
                    } catch (Exception ignored) {}

                    // 2. Install Root Identity Wrappers
                    callback.onProgress("configuring root...");

                    // whoami -> always returns 'root'
                    File whoamiFile = new File(binDir, "whoami");
                    whoamiFile.delete();
                    writeExecutableScript(whoamiFile, "#!/system/bin/sh\necho \"root\"\n");

                    // id -> returns uid=0(root) gid=0(root)
                    File idFile = new File(binDir, "id");
                    idFile.delete();
                    writeExecutableScript(idFile,
                        "#!/system/bin/sh\n" +
                        "if [ \"$1\" = \"-u\" ]; then\n" +
                        "    echo \"0\"\n" +
                        "elif [ \"$1\" = \"-g\" ]; then\n" +
                        "    echo \"0\"\n" +
                        "elif [ \"$1\" = \"-un\" ] || [ \"$1\" = \"-gn\" ]; then\n" +
                        "    echo \"root\"\n" +
                        "else\n" +
                        "    echo \"uid=0(root) gid=0(root) groups=0(root)\"\n" +
                        "fi\n"
                    );

                    // who -> returns root pts/0
                    File whoFile = new File(binDir, "who");
                    whoFile.delete();
                    writeExecutableScript(whoFile,
                        "#!/system/bin/sh\n" +
                        "echo \"root     pts/0        $(date +'%Y-%m-%d %H:%M')\"\n"
                    );

                    // su -> acknowledges root
                    File suFile = new File(binDir, "su");
                    suFile.delete();
                    writeExecutableScript(suFile,
                        "#!/system/bin/sh\n" +
                        "echo \"Already running as root (UID 0)\"\n" +
                        "exec /system/bin/sh -i\n"
                    );

                    // help -> command cheatsheet
                    File helpFile = new File(binDir, "help");
                    helpFile.delete();
                    writeExecutableScript(helpFile,
                        "#!/system/bin/sh\n" +
                        "echo \"================================================\"\n" +
                        "echo \"  Q5 Terminal - Commands Reference\"\n" +
                        "echo \"================================================\"\n" +
                        "echo \"  Identity : whoami, id, uname -a, uptime\"\n" +
                        "echo \"  Hardware : cat /proc/cpuinfo, cat /proc/meminfo\"\n" +
                        "echo \"  Files    : ls -la, pwd, cd, mkdir, rm, cp, mv, vi\"\n" +
                        "echo \"  Storage  : df -h, free, du -sh\"\n" +
                        "echo \"  Network  : ifconfig, netstat, ping, wget\"\n" +
                        "echo \"  Tools    : grep, find, which, tar, sed, awk\"\n" +
                        "echo \"  Core     : busybox --help (350+ Unix utilities)\"\n" +
                        "echo \"================================================\"\n"
                    );

                    // 3. Set up symlinks for all other BusyBox tools
                    callback.onProgress("linking tools...");
                    String[] applets = new String[]{
                        "ls", "cat", "ps", "top", "vi", "grep", "pwd",
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

                    // Best-effort full install
                    try {
                        Process p = Runtime.getRuntime().exec(new String[]{
                            busyboxFile.getAbsolutePath(), "--install", "-s",
                            binDir.getAbsolutePath()
                        });
                        p.waitFor();
                    } catch (Exception ignored) {}

                    // 4. Mark as installed
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

    private static void writeExecutableScript(File file, String content) {
        try {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(content.getBytes());
            fos.close();
            file.setReadable(true, false);
            file.setExecutable(true, false);
            Runtime.getRuntime().exec(new String[]{"chmod", "755", file.getAbsolutePath()}).waitFor();
        } catch (Exception ignored) {}
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
