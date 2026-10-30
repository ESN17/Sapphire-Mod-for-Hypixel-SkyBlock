package com.sorakoi.sbtranslate.config;
import com.google.gson.GsonBuilder;
import com.sorakoi.sbtranslate.translation.TranslationManager;
import java.io.IOException;
import java.nio.file.*;
public final class SapphireConfig {
    public boolean enabled = true, items = true, lore = true, gui = true, chat = true,
        scoreboard = true, tablist = true, actionbar = true, title = true, subtitle = true, bossbar = true;
    public boolean debug = false, forceTranslation = true, collectUntranslated = false, showOriginal = false;
    public int maxRecords = 2000, maxQueue = 512, maxDiskMegabytes = 16;
    public static SapphireConfig load(Path root) throws IOException {
        Path path = root.resolve("config.json");
        SapphireConfig c = Files.exists(path) ? new GsonBuilder().create().fromJson(TranslationManager.readObject(path), SapphireConfig.class) : new SapphireConfig();
        c.maxRecords = Math.clamp(c.maxRecords, 1, 10000); c.maxQueue = Math.clamp(c.maxQueue, 16, 4096);
        c.maxDiskMegabytes = Math.clamp(c.maxDiskMegabytes, 1, 128);
        if (!Files.exists(path)) c.save(root); return c;
    }
    public void save(Path root) throws IOException {
        Files.createDirectories(root); Path temp = root.resolve("config.json.tmp");
        Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(this) + "\n");
        Files.move(temp, root.resolve("config.json"), StandardCopyOption.REPLACE_EXISTING);
    }
    public boolean translationAllowed(boolean skyBlockConfirmed) {
        return enabled && (forceTranslation || skyBlockConfirmed);
    }
    public boolean scope(String s) {
        return enabled && switch(s) {
            case "item" -> items; case "gui" -> gui; case "chat" -> chat; case "scoreboard" -> scoreboard; case "tablist" -> tablist;
            case "actionbar" -> actionbar; case "title" -> title; case "subtitle" -> subtitle; case "bossbar" -> bossbar; default -> false;
        };
    }
}
