package com.fairin.loader.installer;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Console installer: creates a launcher profile "fairin-<mc>" that inherits from the
 * vanilla version and runs FairinLauncher. Copies the loader jar into libraries/.
 * Usage: java -cp <loader+asm> com.fairin.loader.installer.Installer --mc 1.21.11 [--gameDir <dir>]
 */
public final class Installer {
    private Installer() {
    }

    public static void main(String[] args) throws Exception {
        String mc = null;
        File gameDir = new File(System.getenv("APPDATA"), ".minecraft");
        boolean server = false;
        File serverDir = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--mc") && i + 1 < args.length) mc = args[++i];
            else if (args[i].equals("--gameDir") && i + 1 < args.length) gameDir = new File(args[++i]);
            else if (args[i].equals("--server")) server = true;
            else if (args[i].equals("--dir") && i + 1 < args.length) serverDir = new File(args[++i]);
        }
        if (mc == null) {
            if (args.length == 0 && !java.awt.GraphicsEnvironment.isHeadless()) {
                GuiInstaller.run();
                return;
            }
            System.err.println("Usage: Installer --mc <version> [--gameDir <dir>]          (client)");
            System.err.println("       Installer --mc <version> --server --dir <path>      (server)");
            System.err.println("Installed versions:");
            File v = new File(gameDir, "versions");
            if (v.isDirectory()) {
                for (File f : v.listFiles()) if (new File(f, f.getName() + ".json").isFile()) {
                    System.err.println("  " + f.getName());
                }
            }
            return;
        }
        if (server) installServer(mc, serverDir != null ? serverDir : new File("fairin-server-" + mc));
        else install(mc, gameDir);
    }

    /** Creates a ready-to-run server folder (Fabric-style launch jar + start scripts). */
    static void installServer(String mc, File dir) throws Exception {
        dir.mkdirs();
        File fairinDir = new File(dir, "fairin");
        fairinDir.mkdirs();

        // runtime jar (self-contained: ASM embedded)
        copy(findSelfJar(), new File(fairinDir, "fairin-loader-0.1.0.jar"));
        String cp = "fairin/fairin-loader-0.1.0.jar";

        // launch jar: thin Main-Class facade; classpath via manifest
        File launch = new File(dir, "fairin-server-launch.jar");
        writeLaunchJar(launch, cp);

        // config
        String cfg = "{\n  \"mcVersion\": \"" + mc + "\"\n}\n";
        writeText(new File(dir, "fairin-server.json"), cfg);

        // scripts
        writeText(new File(dir, "start.bat"),
                "@echo off\r\ncd /d %~dp0\r\njava -Djava.net.preferIPv4Stack=true -jar fairin-server-launch.jar nogui\r\npause\r\n");
        writeText(new File(dir, "start.sh"),
                "#!/bin/sh\ncd \"$(dirname \"$0\")\"\nexec java -Djava.net.preferIPv4Stack=true -jar fairin-server-launch.jar nogui\n");

        // eula + mods folder
        File eula = new File(dir, "eula.txt");
        if (!eula.isFile()) {
            writeText(eula, "# By changing the setting below to TRUE you are indicating your agreement to our EULA.\n"
                    + "# https://aka.ms/MinecraftEULA\neula=false\n");
        }
        new File(dir, "mods").mkdirs();

        System.out.println("[Fairin] server installed: " + dir.getAbsolutePath());
        System.out.println("[Fairin] 1) accept EULA (eula.txt -> eula=true)");
        System.out.println("[Fairin] 2) run start.bat (Java " + javaHint(mc) + " required)");
    }

    private static String javaHint(String mc) {
        return "21+ (1.20.5+) / 17 (1.18-1.20.4)";
    }

    private static void writeLaunchJar(File target, String classPath) throws Exception {
        String mf = "Manifest-Version: 1.0\r\n"
                + "Main-Class: com.fairin.loader.server.ServerLauncher\r\n"
                + (classPath.isEmpty() ? "" : "Class-Path: " + classPath + "\r\n")
                + "\r\n";
        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.read(new java.io.ByteArrayInputStream(mf.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(new FileOutputStream(target), manifest);
        jos.close();
    }

    /** asm jars are expected next to the loader jar (same tools layout / same folder). */
    private static File findSiblingJar(String name) {
        File self = findSelfJar();
        File p = self.getParentFile();
        if (p == null) return null;
        File f = new File(p, name + ".jar");
        return f.isFile() ? f : null;
    }

    private static void writeText(File f, String s) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        out.write(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.close();
    }

    static void install(String mc, File gameDir) throws Exception {
        String profileId = "fairin-" + mc;
        File dir = new File(gameDir, "versions" + File.separator + profileId);
        dir.mkdirs();

        // 1. copy loader jar into libraries/
        String loaderPath = "com/fairin/fairin-loader/0.1.0/fairin-loader-0.1.0.jar";
        File loaderTarget = new File(gameDir, "libraries" + File.separator + loaderPath.replace('/', File.separatorChar));
        loaderTarget.getParentFile().mkdirs();
        File self = findSelfJar();
        copy(self, loaderTarget);
        System.out.println("[Fairin] loader jar -> " + loaderTarget);

        // 2. write profile JSON
        String json = "{\n"
                + "  \"id\": \"" + profileId + "\",\n"
                + "  \"inheritsFrom\": \"" + mc + "\",\n"
                + "  \"releaseTime\": \"" + isoNow() + "\",\n"
                + "  \"time\": \"" + isoNow() + "\",\n"
                + "  \"type\": \"release\",\n"
                + "  \"mainClass\": \"com.fairin.loader.launch.FairinLauncher\",\n"
                + "  \"arguments\": {\n"
                + "    \"game\": [],\n"
                + "    \"jvm\": [ \"-Djava.net.preferIPv4Stack=true\" ]\n"
                + "  },\n"
                + "  \"libraries\": [\n"
                + "    { \"name\": \"com.fairin:fairin-loader:0.1.0\" },\n"
                + "    { \"name\": \"org.ow2.asm:asm:9.7\", \"url\": \"https://repo1.maven.org/maven2/\" },\n"
                + "    { \"name\": \"org.ow2.asm:asm-tree:9.7\", \"url\": \"https://repo1.maven.org/maven2/\" },\n"
                + "    { \"name\": \"org.ow2.asm:asm-commons:9.7\", \"url\": \"https://repo1.maven.org/maven2/\" },\n"
                + "    { \"name\": \"org.ow2.asm:asm-analysis:9.7\", \"url\": \"https://repo1.maven.org/maven2/\" }\n"
                + "  ]\n"
                + "}\n";
        File jsonFile = new File(dir, profileId + ".json");
        FileOutputStream out = new FileOutputStream(jsonFile);
        out.write(json.getBytes(StandardCharsets.UTF_8));
        out.close();
        System.out.println("[Fairin] profile written: " + jsonFile);
        System.out.println("[Fairin] DONE - pick profile '" + profileId + "' in the vanilla launcher");
    }

    private static File findSelfJar() {
        try {
            File f = new File(Installer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (f.isFile()) return f;
        } catch (Exception ignore) {
        }
        throw new IllegalStateException("Cannot locate fairin-loader jar (run installer from the jar)");
    }

    /** Public wrappers used by the GUI installer. */
    public static File findSelfJarPublic() {
        return findSelfJar();
    }

    public static void copyPublic(File src, File dst) throws Exception {
        dst.getParentFile().mkdirs();
        copy(src, dst);
    }

    private static void copy(File src, File dst) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(src);
        byte[] all = new byte[(int) src.length()];
        int off = 0;
        while (off < all.length) {
            int n = in.read(all, off, all.length - off);
            if (n < 0) break;
            off += n;
        }
        in.close();
        FileOutputStream out = new FileOutputStream(dst);
        out.write(all);
        out.close();
    }

    private static String isoNow() {
        return java.time.Instant.now().toString().substring(0, 20) + "Z";
    }
}
