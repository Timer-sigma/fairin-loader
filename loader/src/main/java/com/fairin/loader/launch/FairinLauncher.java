package com.fairin.loader.launch;

import com.fairin.loader.Boot;
import com.fairin.loader.ModScanner;
import com.fairin.loader.RemapPipeline;
import com.fairin.loader.VersionJson;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry installed as the version profile mainClass (see installer).
 * Flow: resolve version -> ensure client jar/libraries -> offline remap+weave
 * (cached) -> mods on classpath -> bootstrap mods -> run the game main class.
 */
public final class FairinLauncher {
    public static void main(String[] rawArgs) {
        try {
            run(rawArgs);
        } catch (Throwable t) {
            System.err.println("[Fairin] launch failed:");
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(String[] rawArgs) throws Throwable {
        long t0 = System.currentTimeMillis();
        String gameDirStr = ".";
        String version = null;
        List<String> pass = new ArrayList<String>();
        for (int i = 0; i < rawArgs.length; i++) {
            String a = rawArgs[i];
            if (a.equals("--gameDir") && i + 1 < rawArgs.length) {
                gameDirStr = rawArgs[++i];
                pass.add(a);
                pass.add(rawArgs[i]);
            } else if (a.equals("--version") && i + 1 < rawArgs.length) {
                version = rawArgs[++i];
                pass.add(a);
                pass.add(rawArgs[i]);
            } else {
                pass.add(a);
            }
        }
        File gameDir = new File(gameDirStr).getAbsoluteFile();
        Boot.gameDir = gameDir;
        if (version != null) Boot.mcVersion = version;
        // NOTE: nothing is removed from pass - the game needs --version/--gameDir too

        VersionJson vj = VersionJson.resolve(version != null ? version : "latest", gameDir);
        Boot.mcVersion = vj.id;
        System.out.println("[Fairin] launching " + vj.id + " (main=" + vj.mainClass + ")");

        File gameJar = RemapPipeline.ensureRemappedJar(vj.id, gameDir, vj.clientJar, vj.mappingsUrl, "client_mappings");

        List<URL> cp = new ArrayList<URL>();
        cp.add(gameJar.toURI().toURL());
        for (File lib : vj.libraries) cp.add(lib.toURI().toURL());
        ModScanner.scan(new File(gameDir, "mods"));
        for (File modJar : ModScanner.modJars) cp.add(modJar.toURI().toURL());

        URLClassLoader cl = new URLClassLoader(cp.toArray(new URL[0]), FairinLauncher.class.getClassLoader());
        Thread.currentThread().setContextClassLoader(cl);
        Boot.startCore(null, cl);
        System.out.println("[Fairin] game classpath ready in " + (System.currentTimeMillis() - t0) + " ms");

        Class<?> main = Class.forName(vj.mainClass, true, cl);
        Method m = main.getMethod("main", String[].class);
        m.invoke(null, (Object) pass.toArray(new String[0]));
    }
}
