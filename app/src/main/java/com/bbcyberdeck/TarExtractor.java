package com.bbcyberdeck;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.zip.GZIPInputStream;

public class TarExtractor {
    private static final String TAG = "TarExtractor";

    public interface ProgressListener {
        void onProgress(String file, int count);
    }

    public static void extractTarGz(InputStream rawStream, File targetDir, ProgressListener listener) throws IOException {
        PushbackInputStream pbis = new PushbackInputStream(rawStream, 2);
        byte[] magic = new byte[2];
        int r = pbis.read(magic);
        pbis.unread(magic, 0, r);

        InputStream is;
        if (r == 2 && magic[0] == (byte) 0x1f && magic[1] == (byte) 0x8b) {
            is = new GZIPInputStream(pbis);
        } else {
            is = pbis;
        }

        byte[] header = new byte[512];
        int count = 0;

        while (true) {
            int read = readFully(is, header, 0, 512);
            if (read < 512) break;

            // Check for empty block (end of archive)
            boolean allZero = true;
            for (byte b : header) {
                if (b != 0) { allZero = false; break; }
            }
            if (allZero) break;

            String name = parseString(header, 0, 100);
            if (name.startsWith("./")) name = name.substring(2);
            if (name.isEmpty() || name.equals(".")) continue;

            long size = parseOctal(header, 124, 12);
            byte type = header[156];
            String linkName = parseString(header, 157, 100);

            File targetFile = new File(targetDir, name);

            if (type == '5' || name.endsWith("/")) {
                targetFile.mkdirs();
            } else if (type == '2' || type == '1') {
                targetFile.getParentFile().mkdirs();
                try {
                    createSymlink(linkName, targetFile.getAbsolutePath());
                } catch (Throwable t) {
                    Log.w(TAG, "Symlink error for " + name + " -> " + linkName);
                }
            } else {
                targetFile.getParentFile().mkdirs();
                if (targetFile.exists() || !targetFile.canWrite()) {
                    targetFile.delete();
                }

                FileOutputStream fos = new FileOutputStream(targetFile);
                byte[] buf = new byte[8192];
                long remaining = size;
                while (remaining > 0) {
                    int toRead = (int) Math.min(buf.length, remaining);
                    int n = is.read(buf, 0, toRead);
                    if (n < 0) break;
                    fos.write(buf, 0, n);
                    remaining -= n;
                }
                fos.close();

                if (name.startsWith("bin/") || name.startsWith("sbin/") || name.startsWith("usr/bin/") || name.startsWith("usr/sbin/")) {
                    targetFile.setExecutable(true, false);
                }

                long pad = (512 - (size % 512)) % 512;
                if (pad > 0) {
                    byte[] skip = new byte[(int) pad];
                    readFully(is, skip, 0, (int) pad);
                }
            }

            count++;
            if (listener != null && count % 50 == 0) {
                listener.onProgress(name, count);
            }
        }
        is.close();
    }

    private static void createSymlink(String target, String linkpath) {
        try {
            File link = new File(linkpath);
            link.delete();
            Process p = Runtime.getRuntime().exec(new String[]{"/system/bin/ln", "-s", target, linkpath});
            p.waitFor();
        } catch (Exception ignored) {}
    }

    private static int readFully(InputStream is, byte[] b, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int r = is.read(b, off + total, len - total);
            if (r < 0) break;
            total += r;
        }
        return total;
    }

    private static String parseString(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) end++;
        return new String(b, off, end - off).trim();
    }

    private static long parseOctal(byte[] b, int off, int len) {
        long result = 0;
        for (int i = off; i < off + len; i++) {
            byte val = b[i];
            if (val == 0 || val == ' ') break;
            if (val >= '0' && val <= '7') {
                result = (result << 3) + (val - '0');
            }
        }
        return result;
    }
}
