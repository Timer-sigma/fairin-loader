package com.fairin.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

/** Remaps one class (obf -> official Mojang names) and injects event hooks. Offline use. */
public final class ClassProcessor {
    private ClassProcessor() {
    }

    /**
     * @param obfName internal (obfuscated) name of the class being processed
     * @param bytes   original obfuscated class bytes
     * @return official-named, hook-woven bytes, or original bytes on failure
     */
    public static byte[] process(String obfName, byte[] bytes) {
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(0);
            cr.accept(new ClassRemapper(cw, FairinRemapper.INSTANCE), ClassReader.EXPAND_FRAMES);
            byte[] out = cw.toByteArray();
            String officialName = Mappings.classObfToOfficial.containsKey(obfName)
                    ? Mappings.classObfToOfficial.get(obfName) : obfName;
            return HookInjector.inject(officialName, out);
        } catch (Throwable t) {
            System.err.println("[Fairin] remap failed for " + obfName + ": " + t);
            return bytes;
        }
    }
}

