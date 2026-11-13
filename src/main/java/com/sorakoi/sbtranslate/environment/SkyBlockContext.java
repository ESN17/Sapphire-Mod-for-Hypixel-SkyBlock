package com.sorakoi.sbtranslate.environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.*;
import com.sorakoi.sbtranslate.translation.TranslationManager;
import java.util.*;

public final class SkyBlockContext {
    private Object level;
    private int stableTicks;
    private boolean confirmed;
    private boolean dungeon;
    private boolean captureAllowed;
    private boolean publicLocation;
    public boolean captureAllowed(){return captureAllowed;}
    public boolean captureAllowed(Minecraft client){return captureAllowed && client.level == level;}
    public boolean publicLocation(){return publicLocation;}
    public static boolean publicLocation(List<String> lines){
        boolean located=false;
        for(String line:lines){
            String label=line.toLowerCase(Locale.ROOT);
            if(label.contains("your island") || label.contains("private island") || label.contains("你的岛屿") || label.contains("私人岛屿"))return false;
            String trimmed=label.stripLeading();
            if(label.indexOf('⏣')>=0 || label.indexOf('ф')>=0 || !trimmed.isEmpty() && "⧖⌛⏳\uE020".indexOf(trimmed.charAt(0))>=0)located=true;
        }
        return located;
    }
    public boolean dungeon(){return dungeon;}
    public static boolean evidence(String host, String brand, String title, List<String> lines) {
        host = host.toLowerCase(Locale.ROOT).replaceFirst(":\\d+$", "");
        boolean hypixel = host.equals("hypixel.net") || host.endsWith(".hypixel.net");
        if (!hypixel) return false;
        // The sidebar always carries the server address; the SKYBLOCK objective is the second signal.
        boolean address = lines.stream().anyMatch(s -> s.contains("www.hypixel.net") || s.contains("hypixel.net"));
        boolean titleBlock = title != null && title.toUpperCase(Locale.ROOT).contains("SKYBLOCK");
        boolean wallet = lines.stream().anyMatch(s -> s.contains("Purse:") || s.contains("Piggy:")
                || s.contains("Bits:") || s.contains("钱袋") || s.contains("比特"));
        return address || titleBlock;
    }
    public void tick(Minecraft client) {
        if (client.level != level) { reset(); level = client.level; }
        var connection = client.getConnection();
        if (client.level == null || client.player == null || connection == null || client.hasSingleplayerServer() || connection.getServerData() == null) { reset(); return; }
        // Match Hud.extractScoreboardSidebar: a team-color sidebar takes precedence.
        Scoreboard board = client.level.getScoreboard();
        Objective objective = null;
        PlayerTeam team = board.getPlayersTeam(client.player.getScoreboardName());
        DisplaySlot slot = team == null ? null : team.getColor().map(net.minecraft.world.scores.TeamColor::displaySlot).orElse(null);
        if (slot != null) objective = board.getDisplayObjective(slot);
        if (objective == null) objective = board.getDisplayObjective(DisplaySlot.SIDEBAR);
        List<String> lines = new ArrayList<>();
        if (objective != null) for (var entry : board.listPlayerScores(objective)) {
            lines.add(TranslationManager.plain(PlayerTeam.formatNameForTeam(board.getPlayersTeam(entry.owner()), entry.ownerName()).getString()).trim());
        }
        boolean valid = objective != null && evidence(connection.getServerData().ip,connection.serverBrand(),
            TranslationManager.plain(objective.getDisplayName().getString()),lines);
        if (com.sorakoi.sbtranslate.Sapphire.CONFIG.debug && (valid != confirmed))
            com.sorakoi.sbtranslate.Sapphire.LOGGER.info("SkyBlock probe: host={} brand={} title={} addressLine={} lines={}",
                connection.getServerData().ip, connection.serverBrand(),
                objective == null ? "-" : TranslationManager.plain(objective.getDisplayName().getString()),
                lines.stream().anyMatch(s -> s.contains("www.hypixel.net")), lines.size());
        String host=connection.getServerData().ip.toLowerCase(Locale.ROOT).replaceFirst(":\\d+$", "");
        dungeon=(host.equals("hypixel.net") || host.endsWith(".hypixel.net")) && objective!=null &&
            TranslationManager.plain(objective.getDisplayName().getString()).startsWith("SKYBLOCK") &&
            com.sorakoi.sbtranslate.collection.DungeonCapture.dungeonEvidence(lines);
        stableTicks = valid ? Math.min(stableTicks + 1, 20) : 0; confirmed = stableTicks >= 10;
        captureAllowed=confirmed && objective!=null && TranslationManager.plain(objective.getDisplayName().getString()).toUpperCase(Locale.ROOT).contains("SKYBLOCK");
        publicLocation=captureAllowed && publicLocation(lines);
    }
    public boolean confirmed() { return confirmed; }
    public void reset() { level = null; stableTicks = 0; confirmed = false; dungeon=false; captureAllowed=false; publicLocation=false; }
}
