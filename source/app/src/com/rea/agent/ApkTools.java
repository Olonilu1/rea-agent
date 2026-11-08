package com.rea.agent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** On-device APK inspection: the tools a phone can actually run, fed to the AI as evidence. */
public class ApkTools {
    private File apkFile = null;
    private String apkName = null;

    public void load(File f, String name) { apkFile = f; apkName = name; }
    public boolean hasApk() { return apkFile != null && apkFile.exists(); }
    public String apkName() { return apkName == null ? "(none)" : apkName; }

    public String analyze() {
        if (!hasApk()) return "ERROR: no apk loaded. The user must press Pick APK first.";
        StringBuilder sb = new StringBuilder();
        try {
            ZipFile z = new ZipFile(apkFile);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            java.io.FileInputStream in = new java.io.FileInputStream(apkFile);
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            in.close();
            sb.append("APK: ").append(apkName).append("\n");
            sb.append("size: ").append(apkFile.length()).append(" bytes\n");
            sb.append("sha256: ").append(hex(md.digest())).append("\n");
            int dexCount = 0; long totalU = 0;
            List<String> big = new ArrayList<>();
            Map<String, Long> sizes = new LinkedHashMap<>();
            List<ZipEntry> entries = new ArrayList<>();
            for (java.util.Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) entries.add(e.nextElement());
            for (ZipEntry en : entries) { sizes.put(en.getName(), en.getSize()); totalU += en.getCompressedSize(); if (en.getName().endsWith(".dex")) dexCount++; }
            entries.sort((a, b) -> Long.compare(b.getSize(), a.getSize()));
            sb.append("entries: ").append(sizes.size()).append(", dex files: ").append(dexCount).append("\n");
            sb.append("largest entries:\n");
            for (int i = 0; i < Math.min(25, entries.size()); i++) {
                ZipEntry en = entries.get(i);
                sb.append("  ").append(en.getName()).append("  ").append(en.getSize()).append(" bytes\n");
            }
            // manifest string pool: package, version, permissions, components live here as plain strings
            ZipEntry mf = z.getEntry("AndroidManifest.xml");
            if (mf != null) {
                byte[] x = read(z, mf);
                List<String> pool = axmlStrings(x);
                List<String> perms = new ArrayList<>(); List<String> interesting = new ArrayList<>();
                for (String s : pool) {
                    if (s.startsWith("android.permission.") || s.startsWith("com.android.permission")) perms.add(s);
                    else if (s.contains(".permission.")) perms.add(s);
                }
                sb.append("manifest strings (package, version, components, actions):\n");
                int shown = 0;
                for (String s : pool) {
                    if (s.length() < 3 || s.startsWith("android.permission") || s.contains(".permission.")) continue;
                    if (s.equals("manifest") || s.startsWith("android:")) continue;
                    sb.append("  ").append(s).append("\n");
                    if (++shown >= 120) { sb.append("  ...(truncated)\n"); break; }
                }
                sb.append("permissions requested:\n");
                for (String p : perms) sb.append("  ").append(p).append("\n");
            }
            // native libs
            List<String> abis = new ArrayList<>();
            for (String k : sizes.keySet()) {
                if (k.startsWith("lib/")) { String[] p = k.split("/"); if (p.length > 1 && !abis.contains(p[1])) abis.add(p[1]); }
            }
            if (!abis.isEmpty()) sb.append("native ABIs: ").append(abis).append("\n");
            z.close();
        } catch (Exception e) {
            sb.append("error: ").append(e);
        }
        return sb.toString();
    }

    public String dexStrings(String filter, int limit) {
        if (!hasApk()) return "ERROR: no apk loaded. The user must press Pick APK first.";
        if (limit <= 0 || limit > 4000) limit = 1500;
        StringBuilder sb = new StringBuilder();
        try {
            ZipFile z = new ZipFile(apkFile);
            int total = 0, printed = 0;
            for (java.util.Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                ZipEntry en = e.nextElement();
                if (!en.getName().endsWith(".dex")) continue;
                byte[] d = read(z, en);
                sb.append("-- ").append(en.getName()).append(" --\n");
                long stringIdsSize = u32(d, 0x38), stringIdsOff = u32(d, 0x3c);
                int cap = (int) Math.min(stringIdsSize, 60000);
                for (int i = 0; i < cap && printed < limit; i++) {
                    int off = (int) u32(d, (int) stringIdsOff + i * 4);
                    if (off <= 0 || off >= d.length) continue;
                    int p = off;
                    while (p < d.length && (d[p] & 0x80) != 0) p++; // skip uleb128 size
                    p++;
                    int end = p;
                    while (end < d.length && d[end] != 0) end++;
                    if (end <= p) continue;
                    if (end - p > 300) continue;
                    String s = new String(d, p, end - p, StandardCharsets.UTF_8);
                    if (s.length() < 4) continue;
                    boolean printable = true;
                    for (int j = 0; j < s.length(); j++) { char c = s.charAt(j); if (c < 32 && c != '\n' && c != '\t') { printable = false; break; } }
                    if (!printable) continue;
                    total++;
                    if (filter != null && !filter.isEmpty() && !s.toLowerCase().contains(filter.toLowerCase())) continue;
                    sb.append(s).append("\n");
                    printed++;
                }
            }
            z.close();
            sb.append("(matched ").append(printed).append(" of ").append(total).append(" strings scanned)\n");
        } catch (Exception ex) { sb.append("error: ").append(ex); }
        return sb.toString();
    }

    public String find(String needle) {
        if (!hasApk()) return "ERROR: no apk loaded. The user must press Pick APK first.";
        StringBuilder sb = new StringBuilder();
        try {
            ZipFile z = new ZipFile(apkFile);
            byte[] needleB = needle.getBytes(StandardCharsets.UTF_8);
            byte[] needle16 = needle.getBytes(StandardCharsets.UTF_16LE);
            int hits = 0;
            for (java.util.Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements() && hits < 60; ) {
                ZipEntry en = e.nextElement();
                if (en.getSize() > 40_000_000) continue;
                byte[] d = read(z, en);
                for (int i = 0; i < d.length - needleB.length && hits < 60; i++) {
                    boolean m8 = true, m16 = i + needle16.length <= d.length;
                    for (int j = 0; j < needleB.length && m8; j++) if (d[i + j] != needleB[j]) m8 = false;
                    if (m16) for (int j = 0; j < needle16.length; j++) if (d[i + j] != needle16[j]) { m16 = false; break; }
                    if (m8 || m16) {
                        int s = Math.max(0, i - 40), e2 = Math.min(d.length, i + needleB.length + 60);
                        String ctx = new String(d, s, e2 - s, StandardCharsets.ISO_8859_1).replaceAll("[\\x00-\\x1f]", ".");
                        sb.append(en.getName()).append(" @").append(i).append(": ").append(ctx).append("\n");
                        hits++; i += needleB.length;
                    }
                }
            }
            z.close();
            if (hits == 0) sb.append("no hits for \"").append(needle).append("\"\n");
        } catch (Exception ex) { sb.append("error: ").append(ex); }
        return sb.toString();
    }

    /** Extract the string pool of a binary AXML file (AndroidManifest.xml). */
    static List<String> axmlStrings(byte[] x) {
        List<String> out = new ArrayList<>();
        try {
            int stringChunk = -1;
            int off = 8;
            while (off + 8 <= x.length) {
                int type = u16(x, off) & 0xFFFF;
                int hs = u16(x, off + 2) & 0xFFFF;
                int size = (int) u32(x, off + 4);
                if (type == 0x0001) { stringChunk = off; break; }
                if (size <= 0) break;
                off += size;
            }
            if (stringChunk < 0) return out;
            int stringCount = (int) u32(x, stringChunk + 8);
            int flags = (int) u32(x, stringChunk + 16);
            int stringsStart = (int) u32(x, stringChunk + 20);
            boolean utf8 = (flags & 0x100) != 0;
            for (int i = 0; i < stringCount; i++) {
                int soff = (int) u32(x, stringChunk + 28 + i * 4);
                int p = stringChunk + stringsStart + soff;
                if (p >= x.length) continue;
                String s;
                if (utf8) {
                    while (p < x.length && (x[p] & 0x80) != 0) p++; p++;      // u16 len
                    while (p < x.length && (x[p] & 0x80) != 0) p++; p++;      // u8 len
                    int end = p; while (end < x.length && x[end] != 0) end++;
                    s = new String(x, p, Math.max(0, end - p), StandardCharsets.UTF_8);
                } else {
                    int len = u16(x, p) & 0xFFFF;
                    if ((len & 0x8000) != 0) { len = ((len & 0x7FFF) << 16) | (u16(x, p + 2) & 0xFFFF); p += 2; }
                    p += 2;
                    int end = p + len * 2;
                    if (end > x.length) end = x.length;
                    s = new String(x, p, Math.max(0, end - p), StandardCharsets.UTF_16LE);
                }
                if (s.length() > 0 && s.length() < 400) out.add(s);
            }
        } catch (Exception ignored) { }
        return out;
    }

    static int u16(byte[] b, int o) { return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8); }
    static long u32(byte[] b, int o) {
        long v = 0; for (int i = 3; i >= 0; i--) v = (v << 8) | (b[o + i] & 0xFF); return v & 0xFFFFFFFFL;
    }
    static byte[] read(ZipFile z, ZipEntry e) throws Exception {
        InputStream in = z.getInputStream(e); ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[16384]; int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        in.close(); return o.toByteArray();
    }
    static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x)); return s.toString(); }
}
