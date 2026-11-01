package com.fairin.loader.server;

import com.fairin.loader.Boot;
import com.fairin.loader.Mappings;
import com.fairin.loader.ModScanner;
import com.fairin.loader.RemapPipeline;
import com.fairin.loader.TinyJson;
import com.fairin.loader.VersionJson;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fabric-style dedicated server entry. Run via the generated fairin-server-launch.jar:
 * java -jar fairin-server-launch.jar nogui
 *
 * Handles both server jar formats:
 *  - bundler (1.18+): extracts inner version jar + META-INF/libraries.list libs
 *  - fat jar (1.14 - 1.17.1): remaps it directly, libraries from version JSON
 * The server jar is remapped with official server_mappings (cached), mods from
 * ./mods are loaded, then the server main class is invoked.
 */
public final class ServerLauncher {
    public static void main(String[] rawArgs) {
        try {
            run(rawArgs);
        } catch (Throwable t) {
            System.err.println("[Fairin] server launch failed:");
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(String[] rawArgs) throws Throwable {
        long t0 = System.currentTimeMillis();
        File gameDir = new File(".").getAbsoluteFile();
        String mc = null;
        // config file next to the launch jar (or --mc override)
        File cfg = new File(gameDir, "fairin-server.json");
        if (cfg.isFile()) {
            Object o = TinyJson.parse(Mappings.readFile(cfg));
            if (o instanceof Map) mc = str(((Map<?, ?>) o).get("mcVersion"));
        }
        List<String> pass = new ArrayList<String>();
        for (int i = 0; i < rawArgs.length; i++) {
            if (rawArgs[i].equals("--mc") && i + 1 < rawArgs.length) mc = rawArgs[++i];
            else if (rawArgs[i].equals("--gameDir") && i + 1 < rawArgs.length) gameDir = new File(rawArgs[++i]).getAbsoluteFile();
            else pass.add(rawArgs[i]);
        }
        if (mc == null) throw new IllegalStateException("fairin-server.json missing: set \"mcVersion\"");
        Boot.gameDir = gameDir;
        Boot.mcVersion = mc;

        System.out.println("[Fairin] server boot: mc=" + mc);
        VersionJson vj = VersionJson.resolve(mc, gameDir, false);
        File serverJar = vj.serverJar;
        if (serverJar == null || !serverJar.isFile()) {
            throw new IllegalStateException("server jar unavailable for " + mc);
        }

        // bundler (1.18+) vs fat jar (1.14 - 1.17)
        Bundler b = Bundler.detect(serverJar, gameDir);
        File remapTarget = b != null ? b.innerJar : serverJar;
        File gameJar = RemapPipeline.ensureRemappedJar(vj.id, gameDir, remapTarget, vj.serverMappingsUrl, "server_mappings");

        List<URL> cp = new ArrayList<URL>();
        cp.add(gameJar.toURI().toURL());
        if (b != null) {
            for (File lib : b.libs) cp.add(lib.toURI().toURL());
        } else {
            for (File lib : vj.libraries) cp.add(lib.toURI().toURL());
        }
        ModScanner.scan(new File(gameDir, "mods"));
        for (File modJar : ModScanner.modJars) cp.add(modJar.toURI().toURL());

        URLClassLoader cl = new URLClassLoader(cp.toArray(new URL[0]), ServerLauncher.class.getClassLoader());
        Thread.currentThread().setContextClassLoader(cl);
        Boot.startCore(null, cl);

        String mainClass = b != null ? b.mainClass : "net.minecraft.server.MinecraftServer";
        System.out.println("[Fairin] starting server " + mc + " (" + mainClass + ") in "
                + (System.currentTimeMillis() - t0) + " ms");
        Class<?> main = Class.forName(mainClass, true, cl);
        Method m = main.getMethod("main", String[].class);
        m.invoke(null, (Object) pass.toArray(new String[0]));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
