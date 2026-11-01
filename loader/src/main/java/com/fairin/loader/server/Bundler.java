package com.fairin.loader.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Minecraft server "bundler" jar (1.18+): META-INF/versions.list + META-INF/libraries.list.
 * Extracts the inner (obfuscated) version jar and the bundled libraries so the
 * remap pipeline can process the real game code.
 */
final class Bundler {
    public File innerJar;         // extracted obfuscated server inner jar
    public String mainClass = "net.minecraft.server.Main";
    public final List<File> libs = new ArrayList<File>();

    private Bundler() {
    }

    /** @return bundler info, or null if serverJar is a plain fat jar (pre-1.18). */
    static Bundler detect(File serverJar, File gameDir) throws Exception {
        JarFile jf = new JarFile(serverJar);
        try {
            JarEntry versionsList = jf.getJarEntry("META-INF/versions.list");
            if (versionsList == null) return null;

            File cache = new File(gameDir, "fairin-cache");
            File libsDir = new File(cache, "server-libs");
            libsDir.mkdirs();
            Bundler b = new Bundler();

            // inner version jar: "version\tkind\tpath\tsha1\tsize"
            String vlist = read(jf.getInputStream(versionsList));
            String innerPath = null;
            for (String line : vlist.split("\r?\n")) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.trim().split("\t");
                if (parts.length >= 3) { innerPath = parts[2]; break; }
            }
            if (innerPath == null) throw new IllegalStateException("versions.list has no jar path");
            File inner = new File(cache, "server-inner.jar");
            extract(jf, "META-INF/versions/" + innerPath, inner);
            b.innerJar = inner;

            // bundled libraries: "sha1\tsize\tpath" (maven path)
            JarEntry libsList = jf.getJarEntry("META-INF/libraries.list");
            if (libsList != null) {
                String txt = read(jf.getInputStream(libsList));
                for (String line : txt.split("\r?\n")) {
                    if (line.trim().isEmpty()) continue;
                    String[] parts = line.trim().split("\t");
                    String path = parts.length >= 3 ? parts[2] : parts[0];
                    File target = new File(libsDir, path.replace('/', File.separatorChar));
                    if (!target.isFile()) {
                        extract(jf, "META-INF/libraries/" + path, target);
                    }
                    if (target.isFile()) b.libs.add(target);
                }
            }

            // main class from the inner jar manifest
            try {
                JarFile innerJf = new JarFile(b.innerJar);
                Manifest mf = innerJf.getManifest();
                if (mf != null) {
                    String mc = mf.getMainAttributes().getValue("Main-Class");
                    if (mc != null && !mc.isEmpty()) b.mainClass = mc.trim();
                }
                innerJf.close();
            } catch (Throwable ignore) {
            }
            System.out.println("[Fairin] bundler detected: inner=" + innerPath + ", libs=" + b.libs.size());
            return b;
        } finally {
            jf.close();
        }
    }

    private static void extract(JarFile jf, String entry, File target) throws Exception {
        JarEntry e = jf.getJarEntry(entry);
        if (e == null) {
            System.err.println("[Fairin] bundler: missing entry " + entry);
            return;
        }
        target.getParentFile().mkdirs();
        InputStream in = jf.getInputStream(e);
        FileOutputStream out = new FileOutputStream(target);
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.close();
        in.close();
    }

    private static String read(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return new String(bo.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
