package com.fairin.loader;

import java.lang.instrument.Instrumentation;

/** Entry point of the Fairin loader. Attached as -javaagent to the Minecraft JVM. */
public final class FairinAgent {
    public static volatile Instrumentation instrumentation;

    public static void premain(String args, Instrumentation inst) {
        instrumentation = inst;
        try {
            Boot.start(inst, args);
        } catch (Throwable t) {
            System.err.println("[Fairin] bootstrap failed:");
            t.printStackTrace();
        }
    }

    public static void agentmain(String args, Instrumentation inst) {
        premain(args, inst);
    }

    public static void main(String[] args) {
        System.out.println("[Fairin] fairin-loader jar (run via -javaagent, see README)");
    }
}
