package com.fairin.loader;

import java.io.File;
import java.lang.instrument.Instrumentation;

/**
 * Shared bootstrap used both by the launcher entry (FairinLauncher) and by the
 * optional -javaagent mode. Scans mods, puts them on the classpath, instantiates
 * mods and fires @OnInit. Class transformation/remap happens offline in the
 * launcher pipeline (see RemapPipeline), never at class-load time.
 */
public final class Boot {
    public static volatile Instrumentation instrumentation;
    public static File gameDir = new File(".");
    public static String mcVersion = "unknown";

    private Boot() {
    }

    /** Full bootstrap from -javaagent (light mode: mods without baked game hooks). */
    public static void start(Instrumentation inst, String args) {
        parseArgs(args);
        System.out.println("[Fairin] agent boot: mc=" + mcVersion + ", gameDir=" + gameDir.getAbsolutePath());
        startCore(inst, null);
    }

    /** Core sequence shared by launcher and agent modes. */
    public static void startCore(Instrumentation inst, ClassLoader modClassLoader) {
        long t0 = System.currentTimeMillis();
        HookInjector.loadDefaults();
        ModScanner.scan(new File(gameDir, "mods"));
        ModScanner.init(inst, modClassLoader);
        com.fairin.api.EventBus.fireInit();
        System.out.println("[Fairin] ready in " + (System.currentTimeMillis() - t0) + " ms, "
                + ModScanner.mods.size() + " mod(s)");
    }

    /** Agent args: mcVersion=1.21.11,gameDir=C:/path (',' or ';' separators). */
    private static void parseArgs(String args) {
        if (args == null || args.isEmpty()) return;
        for (String part : args.split("[,;]")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String k = part.substring(0, eq).trim();
            String v = part.substring(eq + 1).trim();
            if (k.equals("mcVersion") || k.equals("version")) mcVersion = v;
            else if (k.equals("gameDir")) gameDir = new File(v);
        }
    }
}
