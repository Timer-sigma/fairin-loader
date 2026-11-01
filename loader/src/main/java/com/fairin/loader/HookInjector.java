package com.fairin.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Weaves static handler calls into game methods (OFFICIAL name space, applied offline
 * during jar remap). Specs are data-driven from fairin-hooks.json in the loader jar.
 * Injection only at stack-neutral points: method HEAD, or before RETURN of void methods.
 */
public final class HookInjector {
    /** One planned injection. */
    public static final class Hook {
        public final String targetClass;   // internal, official space
        public final String targetMethod;  // official method name
        public final String targetDesc;    // exact descriptor or null (any)
        public final boolean atHead;       // true = HEAD, false = TAIL (void methods only)
        public final String handlerClass;  // internal
        public final String handlerMethod;
        public Hook(String tc, String tm, String td, boolean atHead, String hc, String hm) {
            this.targetClass = tc; this.targetMethod = tm; this.targetDesc = td;
            this.atHead = atHead; this.handlerClass = hc; this.handlerMethod = hm;
        }
    }

    static final List<Hook> hooks = new ArrayList<Hook>();
    static boolean loaded = false;

    private HookInjector() {
    }

    /** Loads default hook specs from /fairin-hooks.json in the loader jar. */
    public static synchronized void loadDefaults() {
        if (loaded) return;
        loaded = true;
        try {
            java.io.InputStream in = HookInjector.class.getResourceAsStream("/fairin-hooks.json");
            if (in == null) {
                System.out.println("[Fairin] no fairin-hooks.json resource, no hooks");
                return;
            }
            String json = Mappings.readStream(in);
            Object root = TinyJson.parse(json);
            if (!(root instanceof List)) return;
            for (Object o : (List<?>) root) {
                if (!(o instanceof java.util.Map)) continue;
                java.util.Map<?, ?> m = (java.util.Map<?, ?>) o;
                hooks.add(new Hook(
                        str(m.get("class")), str(m.get("method")), str(m.get("desc")),
                        Boolean.TRUE.equals(m.get("head")),
                        str(m.get("handlerClass")), str(m.get("handlerMethod"))));
            }
            System.out.println("[Fairin] hooks: " + hooks.size());
        } catch (Throwable t) {
            System.err.println("[Fairin] failed to load hooks: " + t);
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** Returns woven bytes (or original if nothing matched). */
    public static byte[] inject(String officialInternalName, byte[] bytes) {
        List<Hook> forClass = new ArrayList<Hook>();
        for (Hook h : hooks) if (h.targetClass.equals(officialInternalName)) forClass.add(h);
        if (forClass.isEmpty()) return bytes;
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(0);
        cr.accept(new WeaveVisitor(cw, forClass), 0);
        byte[] out = cw.toByteArray();
        for (Hook h : forClass) {
            System.out.println("[Fairin] hook applied: " + officialInternalName + "." + h.targetMethod
                    + " (" + (h.atHead ? "HEAD" : "TAIL") + ")");
        }
        return out;
    }

    private static final class WeaveVisitor extends ClassVisitor {
        final List<Hook> forClass;

        WeaveVisitor(ClassVisitor cv, List<Hook> forClass) {
            super(Opcodes.ASM9, cv);
            this.forClass = forClass;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
            if (mv == null) return null;
            List<Hook> forMethod = new ArrayList<Hook>();
            for (Hook h : forClass) {
                if (h.targetMethod.equals(name) && (h.targetDesc == null || h.targetDesc.equals(desc))) {
                    if (!h.atHead && Type.getReturnType(desc).getSort() != Type.VOID) continue; // TAIL only for void
                    forMethod.add(h);
                }
            }
            return forMethod.isEmpty() ? mv : new WeaveMethodVisitor(mv, forMethod);
        }
    }

    private static final class WeaveMethodVisitor extends MethodVisitor {
        final List<Hook> forMethod;

        WeaveMethodVisitor(MethodVisitor mv, List<Hook> forMethod) {
            super(Opcodes.ASM9, mv);
            this.forMethod = forMethod;
        }

        @Override
        public void visitCode() {
            mv.visitCode();
            for (Hook h : forMethod) { // HEAD: stack empty, locals untouched -> frames stay valid
                if (h.atHead) call(h);
            }
        }

        private void call(Hook h) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, h.handlerClass, h.handlerMethod, "()V", false);
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.RETURN) { // TAIL of void methods
                for (Hook h : forMethod) {
                    if (!h.atHead) call(h);
                }
            }
            mv.visitInsn(opcode);
        }
    }
}
