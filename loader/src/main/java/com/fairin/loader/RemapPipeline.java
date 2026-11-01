package com.fairin.loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * Offline pipeline: original obfuscated client jar -> remapped (official names)
 * + hook-woven jar, cached in fairin-cache/. Cache is invalidated when the
 * source jar changes (size/mtime marker) or hook specs change.
 */
public final class RemapPipeline {
    private RemapPipeline() {
    }

    public static File ensureRemappedJar(String mcVersion, File gameDir, File originalJar,
                                         String mappingsUrl, String kind) throws Exception {
        HookInjector.loadDefaults(); // must be loaded before weaving (idempotent)
        String flavor = kind != null && kind.startsWith("server") ? "server" : "";
        File cacheDir = new File(gameDir, "fairin-cache");
        cacheDir.mkdirs();
        File out = new File(cacheDir, "remapped-" + flavor + (flavor.isEmpty() ? "" : "-") + mcVersion + ".jar");
        File meta = new File(out.getPath() + ".meta");

        String marker = originalJar.getAbsolutePath() + "|" + originalJar.length() + "|"
                + originalJar.lastModified() + "|hooks=" + HookInjector.hooks.size();
        if (out.isFile() && out.length() > 1000 && meta.isFile() && marker.equals(Mappings.readFile(meta).trim())) {
            System.out.println("[Fairin] remapped jar cache hit: " + out.getName());
            return out;
        }

        if (mappingsUrl == null) {
            System.out.println("[Fairin] no official mappings for " + mcVersion + " - using original jar (no hooks/remap)");
            return originalJar;
        }

        long t0 = System.currentTimeMillis();
        System.out.println("[Fairin] remapping " + originalJar.getName() + " -> " + out.getName() + " ...");
        Mappings.load(mcVersion, gameDir, mappingsUrl, kind);
        if (!Mappings.loaded || Mappings.classObfToOfficial.isEmpty()) {
            System.err.println("[Fairin] mappings unavailable - using original jar");
            return originalJar;
        }
        buildHierarchy(originalJar); // pre-pass: declarations for inherited-member resolution

        JarFile src = new JarFile(originalJar);
        JarOutputStream dst = new JarOutputStream(new FileOutputStream(out));
        try {
            java.util.Enumeration<JarEntry> en = src.entries();
            int count = 0;
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String name = e.getName();
                if (name.endsWith(".SF") || name.endsWith(".DSA") || name.endsWith(".RSA")) continue;
                InputStream in = src.getInputStream(e);
                byte[] data = readAll(in);
                in.close();
                String newName = name;
                if (name.endsWith(".class")) {
                    String internal = name.substring(0, name.length() - 6);
                    data = ClassProcessor.process(internal, data);
                    String official = Mappings.classObfToOfficial.get(internal);
                    if (official != null) newName = official + ".class";
                    if (++count % 1500 == 0) {
                        System.out.println("[Fairin] remapped " + count + " classes...");
                    }
                }
                dst.putNextEntry(new ZipEntry(newName));
                dst.write(data);
                dst.closeEntry();
            }
            System.out.println("[Fairin] remapped " + count + " classes in " + (System.currentTimeMillis() - t0) + " ms");
        } finally {
            try { src.close(); } catch (Exception ignore) { }
            dst.close();
        }
        java.io.FileOutputStream mo = new FileOutputStream(meta);
        mo.write(marker.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mo.close();
        return out;
    }

    /** Pre-pass: read class headers to learn the obfuscated class hierarchy. */
    private static void buildHierarchy(File jar) throws Exception {
        java.util.jar.JarFile jf = new java.util.jar.JarFile(jar);
        try {
            java.util.Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String name = e.getName();
                if (!name.endsWith(".class")) continue;
                InputStream in = jf.getInputStream(e);
                byte[] data = readAll(in);
                in.close();
                org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(data);
                cr.accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                    String cls;
                    final java.util.Set<String> m = new java.util.HashSet<String>();
                    final java.util.Set<String> f = new java.util.HashSet<String>();

                    @Override
                    public void visit(int version, int access, String n, String signature, String superName, String[] interfaces) {
                        this.cls = n;
                        Hierarchy.learn(n, superName, interfaces, m, f);
                    }

                    @Override
                    public org.objectweb.asm.MethodVisitor visitMethod(int access, String n, String desc, String signature, String[] exceptions) {
                        m.add(n + desc);
                        return null;
                    }

                    @Override
                    public org.objectweb.asm.FieldVisitor visitField(int access, String n, String desc, String signature, Object value) {
                        f.add(n + desc);
                        return null;
                    }
                }, org.objectweb.asm.ClassReader.SKIP_CODE | org.objectweb.asm.ClassReader.SKIP_DEBUG
                        | org.objectweb.asm.ClassReader.SKIP_FRAMES);
            }
        } finally {
            jf.close();
        }
        System.out.println("[Fairin] hierarchy learned: " + Hierarchy.superMap.size() + " classes");
    }

    /** Test hook: run hierarchy pre-pass from outside the pipeline. */
    static void buildHierarchyForTest(File jar) throws Exception {
        buildHierarchy(jar);
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }
}
