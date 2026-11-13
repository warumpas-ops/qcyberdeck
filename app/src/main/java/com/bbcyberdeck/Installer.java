package com.bbcyberdeck;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class Installer {
    private static final String TAG = "Installer";
    // Version v12: Added pre-installed SSH, Git, Nano, and pkg package manager
    private static final String VERSION_MARKER = ".installed_v12";

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

                    // Install Static Nano Editor
                    callback.onProgress("installing nano...");
                    File nanoFile = new File(binDir, "nano");
                    copyAsset(context, "nano", nanoFile);
                    nanoFile.setReadable(true, false);
                    nanoFile.setExecutable(true, false);
                    try {
                        Runtime.getRuntime().exec(
                            new String[]{"chmod", "755", nanoFile.getAbsolutePath()}).waitFor();
                    } catch (Exception ignored) {}

                    // Install Static Dropbear SSH multi-binary
                    callback.onProgress("installing ssh & scp...");
                    File dropbearFile = new File(binDir, "dropbearmulti");
                    copyAsset(context, "dropbearmulti", dropbearFile);
                    dropbearFile.setReadable(true, false);
                    dropbearFile.setExecutable(true, false);
                    try {
                        Runtime.getRuntime().exec(
                            new String[]{"chmod", "755", dropbearFile.getAbsolutePath()}).waitFor();
                    } catch (Exception ignored) {}

                    // Install Static Git
                    callback.onProgress("installing git...");
                    File gitFile = new File(binDir, "git");
                    copyAsset(context, "git", gitFile);
                    gitFile.setReadable(true, false);
                    gitFile.setExecutable(true, false);
                    try {
                        Runtime.getRuntime().exec(
                            new String[]{"chmod", "755", gitFile.getAbsolutePath()}).waitFor();
                    } catch (Exception ignored) {}

                    // SSH and SCP wrapper scripts
                    writeExecutableScript(new File(binDir, "ssh"),
                        "#!/system/bin/sh\n" +
                        "BIN=$(cd \"$(dirname \"$0\")\" && pwd)\n" +
                        "exec \"$BIN/dropbearmulti\" dbclient \"$@\"\n"
                    );
                    writeExecutableScript(new File(binDir, "scp"),
                        "#!/system/bin/sh\n" +
                        "BIN=$(cd \"$(dirname \"$0\")\" && pwd)\n" +
                        "exec \"$BIN/dropbearmulti\" scp \"$@\"\n"
                    );
                    writeExecutableScript(new File(binDir, "dropbearkey"),
                        "#!/system/bin/sh\n" +
                        "BIN=$(cd \"$(dirname \"$0\")\" && pwd)\n" +
                        "exec \"$BIN/dropbearmulti\" dropbearkey \"$@\"\n"
                    );

                    File sshDir = new File(rootDir, ".ssh");
                    sshDir.mkdirs();

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
                        "      / __ \\/ ___/    OS     : QCyberdeck v2.5\n" +
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

                    // cowsay (Unix classic speech bubble)
                    writeExecutableScript(new File(binDir, "cowsay"),
                        "#!/system/bin/sh\n" +
                        "MSG=\"${*:-QCyberdeck online.}\"\n" +
                        "echo \" -------------------------------------\"\n" +
                        "echo \"< $MSG >\"\n" +
                        "echo \" -------------------------------------\"\n" +
                        "echo \"        \\   ^__^\"\n" +
                        "echo \"         \\  (oo)\\_______\"\n" +
                        "echo \"            (__)\\       )\\/\\\"\n" +
                        "echo \"                ||----w |\"\n" +
                        "echo \"                ||     ||\"\n"
                    );

                    // skull (Retro cyber skull ASCII art)
                    writeExecutableScript(new File(binDir, "skull"),
                        "#!/system/bin/sh\n" +
                        "echo \"\"\n" +
                        "echo \"          .---.\"\n" +
                        "echo \"         /     \\\"\n" +
                        "echo \"        | () () |\"\n" +
                        "echo \"         \\  ^  /\"\n" +
                        "echo \"          |||||\"\n" +
                        "echo \"      .---'---'---.\"\n" +
                        "echo \"     /  CYBERDECK  \\\"\n" +
                        "echo \"    |  ROOT ACCESS  |\"\n" +
                        "echo \"     \\_____________//\"\n" +
                        "echo \"\"\n"
                    );

                    // deck (Handheld BlackBerry Cyberdeck ASCII art)
                    writeExecutableScript(new File(binDir, "deck"),
                        "#!/system/bin/sh\n" +
                        "echo \"\"\n" +
                        "echo \"   .------------------------.\"\n" +
                        "echo \"   | [=] QCYBERDECK    100% |\"\n" +
                        "echo \"   |------------------------|\"\n" +
                        "echo \"   | root@q5:~# _           |\"\n" +
                        "echo \"   | Linux 3.4 MSM8960 2GB  |\"\n" +
                        "echo \"   |========================|\"\n" +
                        "echo \"   | [Q][W][E][R][T][Y] ... |\"\n" +
                        "echo \"   | [A][S][D][F][G][H] ... |\"\n" +
                        "echo \"   | [ALT][Z][X][C][V] [ENT]|\"\n" +
                        "echo \"   '------------------------'\"\n" +
                        "echo \"\"\n"
                    );

                    // hack (Simulated cyberdeck infiltration sequence)
                    writeExecutableScript(new File(binDir, "hack"),
                        "#!/system/bin/sh\n" +
                        "echo \"[*] Scanning local RF bands... OK\"\n" +
                        "sleep 0.2\n" +
                        "echo \"[*] Intercepting subsystem buses... OK\"\n" +
                        "sleep 0.2\n" +
                        "echo \"[+] Bypassing sandbox policy... SUCCESS\"\n" +
                        "sleep 0.2\n" +
                        "echo \"[+] Gaining ring 0 execution... UID 0 [ROOT]\"\n" +
                        "sleep 0.2\n" +
                        "echo \"[!] Cyberdeck node operational.\"\n"
                    );

                    // clock (ASCII live timestamp)
                    writeExecutableScript(new File(binDir, "clock"),
                        "#!/system/bin/sh\n" +
                        "echo \"  Date   : $(date +'%A, %B %d, %Y')\"\n" +
                        "echo \"  Time   : $(date +'%H:%M:%S %Z')\"\n" +
                        "echo \"  Uptime : $(uptime | sed 's/.*up \\([^,]*\\), .*/\\1/' 2>/dev/null)\"\n"
                    );

                    // tree (Directory hierarchy visualizer)
                    writeExecutableScript(new File(binDir, "tree"),
                        "#!/system/bin/sh\n" +
                        "if [ -x \"" + busyboxFile.getAbsolutePath() + "\" ] && \"" + busyboxFile.getAbsolutePath() + "\" tree \"$@\" 2>/dev/null; then\n" +
                        "    exit 0\n" +
                        "fi\n" +
                        "TARGET=\"${1:-.}\"\n" +
                        "echo \"$TARGET\"\n" +
                        "find \"$TARGET\" -maxdepth 3 2>/dev/null | sed -e 's/[^-][^\\/]*\\// |-- /g' -e 's/|-- \\([^ ]\\)/+-- \\1/'\n"
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
                        "echo \"  QCyberdeck Commands Reference\"\n" +
                        "echo \"================================================\"\n" +
                        "echo \"  ASCII    : fetch, cowsay <msg>, skull, deck\"\n" +
                        "echo \"  Visuals  : matrix, hack, clock\"\n" +
                        "echo \"  Hardware : sysinfo, uname -a, uptime, free, df -h\"\n" +
                        "echo \"  Identity : whoami, id\"\n" +
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
