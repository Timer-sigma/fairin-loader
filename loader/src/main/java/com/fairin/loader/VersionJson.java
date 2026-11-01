package com.fairin.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves a Minecraft version: follows inheritsFrom chains (profile JSONs may be
 * missing locally -> downloaded from the Mojang manifest), merges libraries, and
 * exposes the client jar + official mappings URL. Version-agnostic: works for any
 * modern version (1.14+) including future 26.x date-based releases.
 */
public final class VersionJson {
    public String id;                 // vanilla id (deepest parent)
    public String mainClass = "net.minecraft.client.main.Main";
    public File clientJar;
    public File serverJar;            // downloads.server.url (null before 1.14)
    public String mappingsUrl;        // downloads.client_mappings.url (null before 1.14.4)
    public String serverMappingsUrl;  // downloads.server_mappings.url
    public final List<File> libraries = new ArrayList<File>();

    private VersionJson() {
    }

    /** @param versionArg profile id (e.g. fairin-1.21.11) or plain vanilla id */
    public static VersionJson resolve(String versionArg, File gameDir) throws Exception {
        return resolve(versionArg, gameDir, true);
    }

    /** @param needClient false for server installs (skip client jar download) */
    public static VersionJson resolve(String versionArg, File gameDir, boolean needClient) throws Exception {
        File versionsDir = new File(gameDir, "versions");
        Map<String, Object> json = loadJson(versionArg, versionsDir);
        if (json == null) {
            throw new IllegalStateException("Version JSON not found for " + versionArg);
        }
        String vanillaId = versionArg;
        Map<String, Object> cur = json;
        List<Map<String, Object>> chain = new ArrayList<Map<String, Object>>();
        chain.add(json);
        int depth = 0;
        while (depth++ < 10) {
            String parent = str(cur.get("inheritsFrom"));
            if (parent == null) break;
            vanillaId = parent;
            Map<String, Object> parentJson = loadJson(parent, versionsDir);
            if (parentJson == null) {
                throw new IllegalStateException("Cannot resolve parent version JSON: " + parent);
            }
            chain.add(parentJson);
            cur = parentJson;
        }

        VersionJson out = new VersionJson();
        out.id = vanillaId;
        // game mainClass: vanilla parent wins unless child overrides with a FOREIGN class
        // (OptiFine/Tweaker profiles); child pointing back at our own launcher is ignored.
        String childMc = str(json.get("mainClass"));
        String vanillaMc = str(cur.get("mainClass"));
        String selfMc = "com.fairin.loader.launch.FairinLauncher";
        out.mainClass = (childMc == null || childMc.equals(selfMc))
                ? (vanillaMc != null ? vanillaMc : out.mainClass) : childMc;
        out.clientJar = needClient ? resolveClientJar(json, versionsDir, vanillaId, cur) : null;
        out.mappingsUrl = mappingUrl(cur, "client_mappings"); // vanilla JSON carries downloads section
        out.serverMappingsUrl = mappingUrl(cur, "server_mappings");
        out.serverJar = resolveServerJar(json, versionsDir, vanillaId, cur);

        List<File> seen = new ArrayList<File>();
        for (Map<String, Object> js : chain) {
            collectLibraries(js, versionsDir, out.libraries, seen);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadJson(String id, File versionsDir) throws Exception {
        File f = new File(versionsDir, id + File.separator + id + ".json");
        if (!f.isFile()) {
            String url = manifestUrl(id);
            if (url == null) return null;
            f.getParentFile().mkdirs();
            Mappings.download(url, f);
        }
        Object o = TinyJson.parse(Mappings.readFile(f));
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    private static String manifestUrl(String id) throws Exception {
        String manifest = Mappings.httpGet("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
        Object root = TinyJson.parse(manifest);
        if (!(root instanceof Map)) return null;
        Object versions = ((Map<?, ?>) root).get("versions");
        if (!(versions instanceof List)) return null;
        for (Object v : (List<?>) versions) {
            if (v instanceof Map && id.equals(((Map<?, ?>) v).get("id"))) {
                return str(((Map<?, ?>) v).get("url"));
            }
        }
        return null;
    }

    private static String mappingUrl(Map<String, Object> json, String key) {
        if (json == null) return null;
        Object dl = json.get("downloads");
        if (!(dl instanceof Map)) return null;
        Object cm = ((Map<?, ?>) dl).get(key);
        if (!(cm instanceof Map)) return null;
        return str(((Map<?, ?>) cm).get("url"));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    @SuppressWarnings("unchecked")
    private static File resolveClientJar(Map<String, Object> json, File versionsDir,
                                         String vanillaId, Map<String, Object> vanilla) throws Exception {
        String jarField = str(json.get("jar")); // OptiFine-style variants
        String id = jarField != null ? jarField : vanillaId;
        File jar = new File(versionsDir, id + File.separator + id + ".jar");
        if (!jar.isFile() && vanilla != null) {
            Object dl = vanilla.get("downloads");
            if (dl instanceof Map) {
                Object client = ((Map<?, ?>) dl).get("client");
                if (client instanceof Map) {
                    String url = str(((Map<?, ?>) client).get("url"));
                    if (url != null) {
                        jar.getParentFile().mkdirs();
                        System.out.println("[Fairin] downloading client jar " + id + " ...");
                        Mappings.download(url, jar);
                    }
                }
            }
        }
        return jar;
    }

    @SuppressWarnings("unchecked")
    private static File resolveServerJar(Map<String, Object> json, File versionsDir,
                                         String vanillaId, Map<String, Object> vanilla) throws Exception {
        String id = vanillaId;
        File jar = new File(versionsDir, id + File.separator + id + "-server.jar");
        if (!jar.isFile() && vanilla != null) {
            Object dl = vanilla.get("downloads");
            if (dl instanceof Map) {
                Object server = ((Map<?, ?>) dl).get("server");
                if (server instanceof Map) {
                    String url = str(((Map<?, ?>) server).get("url"));
                    if (url != null) {
                        jar.getParentFile().mkdirs();
                        System.out.println("[Fairin] downloading server jar " + id + " ...");
                        Mappings.download(url, jar);
                    }
                }
            }
        }
        return jar.isFile() ? jar : null;
    }

    @SuppressWarnings("unchecked")
    private static void collectLibraries(Map<String, Object> json, File versionsDir,
                                         List<File> out, List<File> seen) {
        Object libs = json.get("libraries");
        if (!(libs instanceof List)) return;
        File libRoot = new File(gameDirRoot(versionsDir), "libraries");
        for (Object o : (List<?>) libs) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> lib = (Map<String, Object>) o;
            if (!allowedByRules(lib)) continue;
            Object dl = lib.get("downloads");
            String path = null;
            String url = null;
            if (dl instanceof Map) {
                Object artifact = ((Map<?, ?>) dl).get("artifact");
                if (artifact instanceof Map) {
                    path = str(((Map<?, ?>) artifact).get("path"));
                    url = str(((Map<?, ?>) artifact).get("url"));
                }
            }
            if (path == null) path = mavenPath(str(lib.get("name")));
            if (path == null) continue;
            File f = new File(libRoot, path.replace('/', File.separatorChar));
            if (f.exists()) {
                if (!seen.contains(f)) { seen.add(f); out.add(f); }
                continue;
            }
            if (url == null || url.isEmpty()) continue;
            try {
                f.getParentFile().mkdirs();
                // artifact url may be the full file URL (Mojang) or a repo base (Fabric-style)
                String full = url.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")
                        ? url : url + (url.endsWith("/") ? "" : "/") + path;
                Mappings.download(full, f);
                if (!seen.contains(f)) { seen.add(f); out.add(f); }
            } catch (Exception e) {
                System.err.println("[Fairin] cannot fetch library " + path + ": " + e);
            }
        }
    }

    /** gameDir is the parent of versions/ */
    private static File gameDirRoot(File versionsDir) {
        File p = versionsDir.getParentFile();
        return p != null ? p : new File(".");
    }

    /** com.google.code.gson:gson:2.10.1 -> com/google/code/gson/gson/2.10.1/gson-2.10.1.jar */
    private static String mavenPath(String coords) {
        if (coords == null) return null;
        String[] parts = coords.split(":");
        if (parts.length != 3) return null;
        return parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2] + ".jar";
    }

    /** OS rules check (best effort: include unless rules explicitly exclude this OS). */
    private static boolean allowedByRules(Map<String, Object> lib) {
        Object rules = lib.get("rules");
        if (!(rules instanceof List)) return true;
        boolean allowed = false;
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String current = os.contains("win") ? "windows" : os.contains("mac") ? "osx" : "linux";
        for (Object r : (List<?>) rules) {
            if (!(r instanceof Map)) continue;
            Map<?, ?> rule = (Map<?, ?>) r;
            Object action = rule.get("action");
            Object osObj = rule.get("os");
            boolean osMatch = true;
            if (osObj instanceof Map) {
                Object name = ((Map<?, ?>) osObj).get("name");
                osMatch = name == null || current.equals(name);
            }
            if (osMatch) allowed = "allow".equals(action);
        }
        return allowed;
    }
}
