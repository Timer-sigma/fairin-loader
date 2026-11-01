package com.fairin.loader;

import org.objectweb.asm.commons.Remapper;

import java.util.Map;

/** ASM remapper: obfuscated Minecraft bytecode -> official Mojang names. */
public final class FairinRemapper extends Remapper {
    public static final FairinRemapper INSTANCE = new FairinRemapper();

    private FairinRemapper() {
    }

    @Override
    public String map(String internalName) {
        String official = Mappings.classObfToOfficial.get(internalName);
        return official != null ? official : internalName;
    }

    @Override
    public String mapMethodName(String owner, String name, String desc) {
        if (name.startsWith("<")) return name;
        // resolve via the class that DECLARES the method (walk up the hierarchy)
        String declarer = Hierarchy.declaringClass(owner, name, desc, true);
        String search = declarer != null ? declarer : owner;
        String ownerOfficial = Mappings.classObfToOfficial.get(search);
        if (ownerOfficial == null) return name;
        Map<String, String> m = Mappings.methodsObfToOfficial.get(ownerOfficial);
        if (m == null) return name;
        // desc is obf-space here; translate it to official space for the lookup key
        String officialDesc = mapMethodDesc(desc);
        String official = m.get(name + officialDesc);
        return official != null ? official : name;
    }

    @Override
    public String mapFieldName(String owner, String name, String desc) {
        String declarer = Hierarchy.declaringClass(owner, name, desc, false);
        String search = declarer != null ? declarer : owner;
        String ownerOfficial = Mappings.classObfToOfficial.get(search);
        if (ownerOfficial == null) return name;
        Map<String, String> m = Mappings.fieldsObfToOfficial.get(ownerOfficial);
        if (m == null) return name;
        String official = m.get(name);
        return official != null ? official : name;
    }

    /** invokedynamic MethodHandles carry (owner, name, desc) - map them properly. */
    @Override
    public Object mapValue(Object value) {
        if (value instanceof org.objectweb.asm.Handle) {
            org.objectweb.asm.Handle h = (org.objectweb.asm.Handle) value;
            if (h.getTag() <= org.objectweb.asm.Opcodes.H_PUTSTATIC) {
                // field handle
                return new org.objectweb.asm.Handle(h.getTag(), mapType(h.getOwner()),
                        mapFieldName(h.getOwner(), h.getName(), h.getDesc()),
                        mapDesc(h.getDesc()), h.isInterface());
            }
            return new org.objectweb.asm.Handle(h.getTag(), mapType(h.getOwner()),
                    mapMethodName(h.getOwner(), h.getName(), h.getDesc()),
                    mapMethodDesc(h.getDesc()), h.isInterface());
        }
        return super.mapValue(value);
    }
}
