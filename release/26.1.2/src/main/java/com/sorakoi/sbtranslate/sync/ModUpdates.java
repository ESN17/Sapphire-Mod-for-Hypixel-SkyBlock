package com.sorakoi.sbtranslate.sync;

import com.google.gson.JsonParser;
import com.sorakoi.sbtranslate.Sapphire;
import com.sorakoi.sbtranslate.client.SapphireNotifications;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

public final class ModUpdates {
    private static final String SITE = "https://sapphire.esn17cabin.club/mod/";
    private static final long LIMIT = 64L * 1024 * 1024;
    private static long nextCheck;
    private static long generation;
    private static boolean enabled, running;
    private static Pending ready;
    private record Pending(Path stage, Path target, String digest, String previous) {}

    public static synchronized void configure(boolean value) {
        if (value == enabled) return;
        enabled = value;
        generation++;
        ready = null;
        nextCheck = 0;
    }

    public static synchronized void tick() {
        if (!enabled || running || ready != null || System.nanoTime() < nextCheck) return;
        long request = generation;
        running = true;
        Thread.ofPlatform().daemon().name("Sapphire mod update").start(() -> {
            boolean failed = false;
            try { check(request); }
            catch (Exception e) {
                failed = true;
                Sapphire.LOGGER.warn("Sapphire mod update: {}", e.toString());
            } finally {
                synchronized (ModUpdates.class) {
                    running = false;
                    if (request == generation)
                        nextCheck = System.nanoTime() + TimeUnit.MINUTES.toNanos(failed ? 10 : 60);
                }
            }
        });
    }

    private static synchronized boolean current(long request) { return enabled && generation == request; }

    private static void notice(long request, String message) {
        Minecraft.getInstance().execute(() -> {
            if (!current(request)) return;
            var client = Minecraft.getInstance();
            Sapphire.LOGGER.info("{}", message);
            if (client.gui != null) client.gui.getChat().addClientSystemMessage(SapphireNotifications.status(message));
        });
    }

    private static InputStream open(String relative) throws IOException {
        var connection = (java.net.HttpURLConnection) URI.create(SITE + relative).toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(30000);
        if (connection.getResponseCode() != 200) {
            int code = connection.getResponseCode();
            connection.disconnect();
            throw new IOException("HTTP " + code);
        }
        return connection.getInputStream();
    }

    static boolean newer(String candidate, String installed) throws Exception {
        return candidate.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                && SemanticVersion.parse(candidate).compareTo(SemanticVersion.parse(installed)) > 0;
    }

    private static void check(long request) throws Exception {
        var loader = FabricLoader.getInstance();
        var mod = loader.getModContainer("sapphire").orElseThrow();
        String installed = mod.getMetadata().getVersion().getFriendlyString();
        String game = loader.getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
        if (!game.matches("[0-9.]+") || loader.isDevelopmentEnvironment()) return;
        var paths = mod.getOrigin().getPaths();
        if (paths.size() != 1) return;
        Path target = paths.getFirst().toAbsolutePath().normalize();
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || !target.getParent().equals(loader.getGameDir().resolve("mods").toAbsolutePath().normalize())) return;
        String json;
        try (var in = open("latest.json")) {
            byte[] bytes = in.readNBytes(32769);
            if (bytes.length > 32768) throw new IOException("Update manifest too large");
            json = new String(bytes, StandardCharsets.UTF_8);
        }
        var manifest = JsonParser.parseString(json).getAsJsonObject();
        String version = manifest.get("version").getAsString();
        if (!newer(version, installed)) return;
        var files = manifest.getAsJsonObject("files");
        if (!files.has(game)) return;
        var entry = files.getAsJsonObject(game);
        String name = "sapphire-" + version + "-mc" + game + ".jar";
        String sha = entry.get("sha256").getAsString();
        long size = entry.get("size").getAsLong();
        if (!name.equals(entry.get("name").getAsString()) || !sha.matches("[a-f0-9]{64}") || size <= 0 || size > LIMIT)
            throw new IOException("Invalid mod release metadata");
        if (!current(request)) return;
        notice(request, "检测到 Sapphire " + version + "，正在下载更新……");
        Path folder = Sapphire.ROOT.resolve("mod-update");
        Files.createDirectories(folder);
        Path tmp = Files.createTempFile(folder, "sapphire-", ".pending");
        try {
            try (var in = open(sha + "/" + name); var out = Files.newOutputStream(tmp)) {
                byte[] buffer = new byte[65536];
                int count;
                long total = 0;
                while ((count = in.read(buffer)) != -1) {
                    if (!current(request)) return;
                    total += count;
                    if (total > size) throw new IOException("Update exceeds expected size");
                    out.write(buffer, 0, count);
                }
                if (total != size) throw new IOException("Incomplete mod update");
            }
            if (!InstallModUpdate.digest(tmp).equals(sha)) throw new IOException("Incorrect update digest");
            try (var jar = new JarFile(tmp.toFile()); var in = jar.getInputStream(jar.getJarEntry("fabric.mod.json"))) {
                var metadata = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                var supported = net.fabricmc.loader.api.metadata.version.VersionPredicate.parse(metadata.getAsJsonObject("depends").get("minecraft").getAsString());
                if (!metadata.get("id").getAsString().equals("sapphire") || !metadata.get("version").getAsString().equals(version)
                        || !supported.test(net.fabricmc.loader.api.Version.parse(game))) throw new IOException("Incompatible mod update");
            }
            String oldHash = InstallModUpdate.digest(target);
            synchronized (ModUpdates.class) {
                if (!current(request)) return;
                Path stage = folder.resolve(sha + ".pending");
                Files.move(tmp, stage, StandardCopyOption.REPLACE_EXISTING);
                ready = new Pending(stage, target, sha, oldHash);
            }
            notice(request, "Sapphire " + version + " 已下载，正常退出游戏后安装，下次启动生效。");
        } finally { Files.deleteIfExists(tmp); }
    }

    public static synchronized void stopping() {
        if (!enabled || ready == null) return;
        try {
            Path folder = ready.stage.getParent();
            String resource = InstallModUpdate.class.getName().replace('.', '/') + ".class";
            Path helper = folder.resolve(resource);
            Files.createDirectories(helper.getParent());
            try (var in = InstallModUpdate.class.getResourceAsStream("/" + resource)) {
                Files.copy(in, helper, StandardCopyOption.REPLACE_EXISTING);
            }
            Path java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "javaw.exe" : "java");
            new ProcessBuilder(java.toString(), "-cp", folder.toString(), InstallModUpdate.class.getName(),
                    Long.toString(ProcessHandle.current().pid()), ready.stage.toString(), ready.target.toString(), ready.digest, ready.previous)
                    .redirectErrorStream(true).redirectOutput(folder.resolve("install.log").toFile()).start();
        } catch (IOException e) { Sapphire.LOGGER.warn("Unable to schedule Sapphire update", e); }
    }
}
