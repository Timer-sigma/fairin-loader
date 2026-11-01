package com.fairin.loader;

/** Converts a plain Java type string (as used in ProGuard mappings) to a JVM descriptor. */
final class TypeDesc {
    private TypeDesc() {
    }

    /** "int", "java.lang.String", "net.minecraft.X$Y", "java.lang.String[][]" -> descriptor. */
    static String toDescriptor(String type) {
        int dims = 0;
        while (type.endsWith("[]")) {
            dims++;
            type = type.substring(0, type.length() - 2);
        }
        String base = primitive(type);
        if (base == null) {
            base = "L" + type.replace('.', '/') + ";";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dims; i++) sb.append('[');
        sb.append(base);
        return sb.toString();
    }

    /** Method signature parts -> "(paramdescs)returntypedesc". */
    static String methodDescriptor(String returnType, String params) {
        StringBuilder sb = new StringBuilder("(");
        params = params.trim();
        if (!params.isEmpty()) {
            for (String p : params.split(",")) {
                sb.append(toDescriptor(p.trim()));
            }
        }
        sb.append(')').append(toDescriptor(returnType));
        return sb.toString();
    }

    private static String primitive(String t) {
        if (t.equals("int")) return "I";
        if (t.equals("boolean")) return "Z";
        if (t.equals("byte")) return "B";
        if (t.equals("char")) return "C";
        if (t.equals("short")) return "S";
        if (t.equals("long")) return "J";
        if (t.equals("float")) return "F";
        if (t.equals("double")) return "D";
        if (t.equals("void")) return "V";
        return null;
    }
}
