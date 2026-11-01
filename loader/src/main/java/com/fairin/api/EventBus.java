package com.fairin.api;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central event bus. Methods annotated with {@link OnInit} / {@link OnTick}
 * are resolved once to MethodHandles (fast direct dispatch, no reflection per event).
 */
public final class EventBus {
    private static final List<MethodHandle> initHandlers = new CopyOnWriteArrayList<MethodHandle>();
    private static final List<MethodHandle> tickHandlers = new CopyOnWriteArrayList<MethodHandle>();

    private EventBus() {
    }

    /** Scans the object's class for annotated methods and registers them. */
    public static void register(Object mod) {
        Class<?> c = mod.getClass();
        for (Method m : c.getDeclaredMethods()) {
            try {
                m.setAccessible(true);
                MethodHandle h = MethodHandles.lookup().unreflect(m);
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    h = h.bindTo(mod);
                }
                if (m.isAnnotationPresent(OnInit.class) && m.getParameterCount() == 0) {
                    initHandlers.add(h);
                }
                if (m.isAnnotationPresent(OnTick.class) && m.getParameterCount() == 0) {
                    tickHandlers.add(h);
                }
            } catch (Throwable t) {
                System.err.println("[Fairin] cannot register method " + m + ": " + t);
            }
        }
    }

    /** Fires the mod init phase. */
    public static void fireInit() {
        for (MethodHandle h : initHandlers) {
            try {
                h.invokeExact();
            } catch (Throwable t) {
                System.err.println("[Fairin] error in @OnInit handler: " + t);
            }
        }
    }

    /** Fires the client tick phase. Called from the injected Minecraft hook. */
    public static void fireTick() {
        for (MethodHandle h : tickHandlers) {
            try {
                h.invokeExact();
            } catch (Throwable t) {
                System.err.println("[Fairin] error in @OnTick handler: " + t);
            }
        }
    }
}
