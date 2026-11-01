package com.fairin.loader.installer;

import com.fairin.loader.Mappings;
import com.fairin.loader.TinyJson;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Fetches the Mojang version manifest and returns version ids (newest first). */
final class ManifestHelper {
    private static final Pattern DATE_BASED = Pattern.compile("^\\d{2}\\..*"); // 26.1, 27.2-snapshot-1
    private static final Pattern LEGACY = Pattern.compile("^1\\.(\\d+)");
    private static final Pattern SNAPSHOT = Pattern.compile("^\\d{2}w\\d{2}[a-z].*"); // 25w46a

    private ManifestHelper() {
    }

    /** Loader supports 1.14+ (official mappings) and future date-based versions. */
    static boolean isSupported(String id) {
        if (DATE_BASED.matcher(id).matches()) return true;
        java.util.regex.Matcher m = LEGACY.matcher(id);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1)) >= 14;
            } catch (NumberFormatException ignore) {
                return false;
            }
        }
        return false;
    }

    private static List<Map<String, Object>> loadManifest(File gameDir) throws Exception {
        File cache = new File(gameDir, "fairin-cache");
        cache.mkdirs();
        File f = new File(cache, "manifest.json");
        // refresh at most once per hour
        boolean stale = !f.isFile() || System.currentTimeMillis() - f.lastModified() > 3600_000L;
        if (stale) {
            Mappings.download("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json", f);
        }
        Object root = TinyJson.parse(Mappings.readFile(f));
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        if (root instanceof Map) {
            Object versions = ((Map<?, ?>) root).get("versions");
            if (versions instanceof List) {
                for (Object v : (List<?>) versions) {
                    if (v instanceof Map) out.add((Map<String, Object>) v);
                }
            }
        }
        return out;
    }

    static List<String> releaseVersions(File gameDir) throws Exception {
        return filter(gameDir, "release");
    }

    static List<String> snapshotVersions(File gameDir) throws Exception {
        return filter(gameDir, "snapshot");
    }

    @SuppressWarnings("unchecked")
    private static List<String> filter(File gameDir, String type) throws Exception {
        List<String> out = new ArrayList<String>();
        for (Map<String, Object> v : loadManifest(gameDir)) {
            if (type.equals(v.get("type"))) {
                Object id = v.get("id");
                if (id != null) out.add(String.valueOf(id));
            }
        }
        return out;
    }
}

