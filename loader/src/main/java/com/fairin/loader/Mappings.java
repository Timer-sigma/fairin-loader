package com.fairin.loader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Official Mojang "client.txt" ProGuard mappings (available since 1.14.4).
 * Downloaded once per version, cached in fairin-cache.
 * Direction: left = official (deobfuscated) name, right = obfuscated runtime name.
 */
public final class Mappings {
    /** obfuscated internal name -> official internal name */
    public static final Map<String, String> classObfToOfficial = new HashMap<String, String>(66000);
    /** official internal name -> obfuscated internal name */
    public static final Map<String, String> classOfficialToObf = new HashMap<String, String>(66000);
    /** official class -> (obf key -> official member name); key: obfName + officialDesc (methods), obfName (fields) */
    public static final Map<String, Map<String, String>> methodsObfToOfficial = new HashMap<String, Map<String, String>>();
    public static final Map<String, Map<String, String>> fieldsObfToOfficial = new HashMap<String, Map<String, String>>();
    /** official class -> official member name(+officialDesc) -> obf name (for hook specs) */
    public static final Map<String, Map<String, String>> methodOfficialToObf = new HashMap<String, Map<String, String>>();
    public static final Map<String, Map<String, String>> fieldOfficialToObf = new HashMap<String, Map<String, String>>();

    public static boolean loaded;

    private Mappings() {
    }

    public static synchronized void load(String mcVersion, File gameDir) {
        load(mcVersion, gameDir, null, "client_mappings");
    }

    /** @param directUrl mappings URL from version JSON (nullable -> resolve via manifest) */
    public static synchronized void load(String mcVersion, File gameDir, String directUrl, String kind) {
        if (loaded) return;
        loaded = true;
        long t0 = System.currentTimeMillis();
        String prefix = kind != null && kind.startsWith("server") ? "server" : "client";
        try {
            File cache = new File(gameDir, "fairin-cache");
            cache.mkdirs();
            File f = new File(cache, prefix + "-" + mcVersion + ".txt");
            if (!f.isFile() || f.length() < 1000) {
                System.out.println("[Fairin] downloading " + kind + " for " + mcVersion + " ...");
                String mappingUrl = directUrl != null ? directUrl : resolveMappingsUrl(mcVersion, gameDir, kind);
                download(mappingUrl, f);
            }
            parse(f);
            System.out.println("[Fairin] mappings ready: " + classObfToOfficial.size() + " classes in "
                    + (System.currentTimeMillis() - t0) + " ms");
        } catch (Throwable t) {
            System.err.println("[Fairin] FAILED to load mappings (remapped mods will not work): " + t);
        }
    }

    /** Resolves mappings URL from Mojang meta (cached meta file). */
    private static String resolveMappingsUrl(String mcVersion, File gameDir, String kind) throws Exception {
        File cache = new File(gameDir, "fairin-cache");
        File meta = new File(cache, "meta-" + mcVersion + ".json");
        if (!meta.isFile()) {
            String manifest = httpGet("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
            Object root = TinyJson.parse(manifest);
            if (root instanceof Map) {
                Object versions = ((Map<?, ?>) root).get("versions");
                if (versions instanceof List<?>) {
                    for (Object v : (List<?>) versions) {
                        if (v instanceof Map && mcVersion.equals(((Map<?, ?>) v).get("id"))) {
                            download(String.valueOf(((Map<?, ?>) v).get("url")), meta);
                            break;
                        }
                    }
                }
            }
        }
        if (!meta.isFile()) throw new RuntimeException("version " + mcVersion + " not found in Mojang manifest");
        Map<String, Object> vj = TinyJson.parseObject(readFile(meta));
        Map<?, ?> downloads = (Map<?, ?>) vj.get("downloads");
        Map<?, ?> cm = (Map<?, ?>) downloads.get(kind != null ? kind : "client_mappings");
        if (cm == null) throw new RuntimeException(kind + " not available for " + mcVersion);
        return String.valueOf(cm.get("url"));
    }

    /** Parses ProGuard mapping text into lookup tables (two passes). */
    static void parse(File f) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
        String line;
        while ((line = r.readLine()) != null) {
            int arrow = line.indexOf(" -> ");
            if (arrow < 0 || line.startsWith(" ") || line.startsWith("#")) continue;
            String officialClass = line.substring(0, arrow).replace('.', '/');
            String obf = line.substring(arrow + 4).trim();
            if (obf.endsWith(":")) obf = obf.substring(0, obf.length() - 1);
            classObfToOfficial.put(obf.replace('.', '/'), officialClass);
            classOfficialToObf.put(officialClass, obf.replace('.', '/'));
        }
        r.close();

        // member pass (needs complete classMap: descs here are official-space)
        r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
        String officialClass = null;
        while ((line = r.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            int arrow = line.indexOf(" -> ");
            if (arrow < 0) continue;
            String left = line.substring(0, arrow).trim();
            String right = line.substring(arrow + 4).trim();
            if (right.endsWith(":")) { // class line
                officialClass = left.replace('.', '/');
                continue;
            }
            if (!Character.isWhitespace(line.charAt(0)) || officialClass == null) continue;
            int paren = left.indexOf('(');
            if (paren >= 0) {
                // strip optional ProGuard line-number prefix: "1317:1454:" before the type
                if (Character.isDigit(left.charAt(0))) {
                    int c1 = left.indexOf(':');
                    int c2 = c1 >= 0 ? left.indexOf(':', c1 + 1) : -1;
                    if (c1 > 0 && c2 > c1 && left.substring(0, c1).chars().allMatch(Character::isDigit)) {
                        String second = left.substring(c1 + 1, c2);
                        boolean digits = true;
                        for (int k = 0; k < second.length(); k++) {
                            if (!Character.isDigit(second.charAt(k))) { digits = false; break; }
                        }
                        if (digits) left = left.substring(c2 + 1);
                    }
                }
                paren = left.indexOf('(');
                if (paren < 0) continue;
                String params = left.substring(paren + 1, left.lastIndexOf(')'));
                String head = left.substring(0, paren).trim();
                int sp = head.lastIndexOf(' ');
                if (sp < 0) continue;
                String name = head.substring(sp + 1);
                String ret = head.substring(0, sp);
                String officialDesc = TypeDesc.methodDescriptor(ret, params);
                putInto(methodsObfToOfficial, officialClass, right + officialDesc, name);
                putInto(methodOfficialToObf, officialClass, name + officialDesc, right);
            } else {
                int sp = left.lastIndexOf(' ');
                if (sp < 0) continue;
                String name = left.substring(sp + 1);
                putInto(fieldsObfToOfficial, officialClass, right, name);
                putInto(fieldOfficialToObf, officialClass, name, right);
            }
        }
        r.close();
    }

    private static void putInto(Map<String, Map<String, String>> all, String cls, String key, String value) {
        Map<String, String> m = all.get(cls);
        if (m == null) all.put(cls, m = new HashMap<String, String>());
        m.put(key, value);
    }

    static String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Accept-Encoding", "gzip");
        java.io.InputStream in = c.getInputStream();
        if ("gzip".equals(c.getContentEncoding())) in = new GZIPInputStream(in);
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
        r.close();
        return sb.toString();
    }

    public static void download(String url, File target) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(60000);
                c.setRequestProperty("Accept-Encoding", "gzip");
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (FairinLoader/0.1)");
                java.io.InputStream in = c.getInputStream();
                if ("gzip".equals(c.getContentEncoding())) in = new GZIPInputStream(in);
                FileOutputStream out = new FileOutputStream(target);
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
                return;
            } catch (Exception e) {
                last = e;
                System.err.println("[Fairin] download attempt " + attempt + "/3 failed: " + e);
                if (attempt < 3) {
                    try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
        throw last != null ? last : new RuntimeException("download failed");
    }

    public static String readFile(File f) throws Exception {
        return readStream(new FileInputStream(f));
    }

    static String readStream(java.io.InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
        r.close();
        return sb.toString();
    }
}
