package com.fairin.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Discovers mods in the mods/ folder by scanning @FairinMod annotations with ASM (no classloading). */
public final class ModScanner {
    public static final List<ModInstance> mods = new ArrayList<ModInstance>();
    public static final List<File> modJars = new ArrayList<File>();

    private ModScanner() {
    }

    static boolean scanned = false;

    public static void scan(File modsDir) {
        if (scanned) return; // scan once per JVM (launcher scans before building classpath)
        scanned = true;
        mods.clear();
        modJars.clear();
        File[] files = modsDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.isFile() || !f.getName().endsWith(".jar")) continue;
            try {
                scanJar(f);
            } catch (Throwable t) {
                System.err.println("[Fairin] cannot scan " + f.getName() + ": " + t);
            }
        }
        if (!mods.isEmpty()) {
            System.out.println("[Fairin] found " + mods.size() + " mod(s) in " + modsDir.getAbsolutePath());
        }
    }

    private static void scanJar(File jar) throws Exception {
        JarFile jf = new JarFile(jar);
        try {
            java.util.Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                if (!e.getName().endsWith(".class") || e.getName().contains("module-info")) continue;
                InputStream in = jf.getInputStream(e);
                try {
                    byte[] all = readAll(in);
                    ClassReader cr = new ClassReader(all);
                    DetectVisitor dv = new DetectVisitor();
                    cr.accept(dv, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    if (dv.found) {
                        mods.add(new ModInstance(dv.id, dv.name, dv.version, dv.className, jar.getName()));
                        if (!modJars.contains(jar)) modJars.add(jar);
                    }
                } finally {
                    in.close();
                }
            }
        } finally {
            jf.close();
        }
    }

    /**
     * Puts mod jars on the classpath, creates instances, registers event handlers.
     * @param inst instrumentation (agent mode) or null (launcher mode: jars are added
     *             to the launcher classloader instead)
     * @param modClassLoader classloader used to load mod classes (nullable -> system)
     */
    public static void init(Instrumentation inst, ClassLoader modClassLoader) {
        for (File jar : modJars) {
            if (inst == null) break;
            try {
                inst.appendToSystemClassLoaderSearch(new JarFile(jar));
            } catch (Throwable t) {
                System.err.println("[Fairin] cannot add to classpath: " + jar + " (" + t + ")");
            }
        }
        ClassLoader cl = modClassLoader != null ? modClassLoader : ClassLoader.getSystemClassLoader();
        for (ModInstance mod : mods) {
            try {
                Class<?> c = Class.forName(mod.className, true, cl);
                mod.instance = c.getDeclaredConstructor().newInstance();
                com.fairin.api.EventBus.register(mod.instance);
                System.out.println("[Fairin] loaded " + mod);
            } catch (Throwable t) {
                System.err.println("[Fairin] failed to load mod " + mod.id + ": " + t);
            }
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static final class DetectVisitor extends ClassVisitor {
        boolean found;
        String id, name, version, className;

        DetectVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name.replace('/', '.');
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (!"Lcom/fairin/api/FairinMod;".equals(desc)) return null;
            found = true;
            id = className;
            name = "";
            version = "1.0";
            return new org.objectweb.asm.AnnotationVisitor(Opcodes.ASM9) {
                @Override
                public void visit(String key, Object value) {
                    if ("id".equals(key)) id = String.valueOf(value);
                    else if ("name".equals(key)) name = String.valueOf(value);
                    else if ("version".equals(key)) version = String.valueOf(value);
                }
            };
        }
    }
}
