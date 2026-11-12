package com.bbcyberdeck;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class Installer {
    private static final String TAG = "Installer";
    // Version v8: Prompt inline cursor fix + built-in fetch, matrix, sysinfo utilities
    private static final String VERSION_MARKER = ".installed_v8";

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
                    callback.onProgress("configuring tools...");

                    // whoami
                    writeExecutableScript(new File(binDir, "whoami"), "#!/system/bin/sh\necho \"root\"\n");

                    // id
                    writeExecutableScript(new File(binDir, "id"),
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

                    // who
                    writeExecutableScript(new File(binDir, "who"),
                        "#!/system/bin/sh\n" +
                        "echo \"root     pts/0        $(date +'%Y-%m-%d %H:%M')\"\n"
                    );

                    // su
                    writeExecutableScript(new File(binDir, "su"),
                        "#!/system/bin/sh\n" +
                        "echo \"Already running as root (UID 0)\"\n" +
                        "exec /system/bin/sh -i\n"
                    );

                    // fetch (Neo-fetch style hardware summary)
                    writeExecutableScript(new File(binDir, "fetch"),
                        "#!/system/bin/sh\n" +
                        "echo \"\"\n" +
                        "echo \"       ____  _____\n" +
                        "      / __ \\/ ___/    OS     : QCyberdeck v2.4\n" +
                        "     / / / /\\__ \\     Host   : BlackBerry Q5 (Qualcomm MSM8960)\n" +
                        "    / /_/ /___/ /     Kernel : $(uname -r 2>/dev/null || echo '3.4.0-QNX')\n" +
                        "    \\___\\_\\____/      Memory : 2048 MB LPDDR2\n" +
                        "                      Storage: $(df -h /sdcard 2>/dev/null | tail -n 1 | awk '{print $4}') free on /sdcard\n" +
                        "                      User   : root@q5 (UID 0)\"\n" +
                        "echo \"\"\n"
                    );

                    // matrix (Streaming retro cyber rain)
                    writeExecutableScript(new File(binDir, "matrix"),
                        "#!/system/bin/sh\n" +
                        "echo \"=== CYBER STREAM START ===\"\n" +
                        "for i in $(seq 1 35); do\n" +
                        "  echo \"01000010 01000010 00110001 00110000 01010001 00110101\"\n" +
                        "  sleep 0.04\n" +
                        "done\n" +
                        "echo \"=== CYBER STREAM COMPLETE ===\"\n"
                    );

                    // sysinfo (Hardware telemetry)
                    writeExecutableScript(new File(binDir, "sysinfo"),
                        "#!/system/bin/sh\n" +
                        "echo \"=== BlackBerry Q5 Telemetry ===\"\n" +
                        "echo \"CPU     : Qualcomm MSM8960 Krait Dual-Core\"\n" +
                        "[ -f /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq ] && echo \"Clock   : $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq) kHz\"\n" +
                        "[ -f /sys/class/power_supply/battery/uevent ] && grep -E \"VOLTAGE|TEMP|CAPACITY|STATUS\" /sys/class/power_supply/battery/uevent\n" +
                        "echo \"Uptime  : $(uptime)\"\n" +
                        "echo \"===============================\"\n"
                    );

                    // help
                    writeExecutableScript(new File(binDir, "help"),
                        "#!/system/bin/sh\n" +
                        "echo \"================================================\"\n" +
                        "echo \"  QCyberdeck Quick Reference\"\n" +
                        "echo \"================================================\"\n" +
                        "echo \"  Specs    : fetch, sysinfo, uname -a, uptime\"\n" +
                        "echo \"  Identity : whoami, id\"\n" +
                        "echo \"  Visuals  : matrix\"\n" +
                        "echo \"  Files    : ls -la, pwd, cd, mkdir, rm, cp, mv, vi\"\n" +
                        "echo \"  Archives : tar -czvf, gzip, bzip2\"\n" +
                        "echo \"  Network  : ifconfig, netstat, ping, nc, wget\"\n" +
                        "echo \"  Tools    : grep, awk, sed, find, strings, hexdump\"\n" +
                        "echo \"  All 350+ : busybox --help\"\n" +
                        "echo \"================================================\"\n"
                    );

                    // 3. Set up symlinks for all other BusyBox tools
                    callback.onProgress("linking utilities...");
                    String[] applets = new String[]{
                        "ls", "cat", "ps", "top", "vi", "grep", "pwd",
                        "mkdir", "rm", "cp", "mv", "chmod", "chown", "touch", "echo",
                        "clear", "find", "sed", "awk", "tar", "gzip", "wget", "curl",
                        "df", "free", "uname", "uptime", "head", "tail", "more", "less",
                        "which", "kill", "sleep", "date", "env", "true", "false", "test",
                        "wc", "sort", "uniq", "cut", "tr", "tee", "xargs", "strings",
                        "du", "netstat", "ifconfig", "ping", "stat", "basename", "dirname",
                        "hexdump", "od", "nc", "traceroute", "base64", "seq"
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
            if (file.exists()) file.delete();
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
