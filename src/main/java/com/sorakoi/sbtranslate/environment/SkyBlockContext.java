package com.sorakoi.sbtranslate.environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.*;
import com.sorakoi.sbtranslate.translation.TranslationManager;
import java.util.*;

public final class SkyBlockContext {
    private Object level;
    private int stableTicks;
    private boolean confirmed;
    public static boolean evidence(String host, String brand, String title, List<String> lines) {
        host = host.toLowerCase(Locale.ROOT).replaceFirst(":\\d+$", "");
        boolean hypixel = (host.equals("hypixel.net") || host.endsWith(".hypixel.net")) && brand != null && brand.toLowerCase(Locale.ROOT).contains("hypixel");
        return hypixel && title.startsWith("SKYBLOCK") && lines.stream().anyMatch(s -> s.contains("www.hypixel.net"))
            && lines.stream().anyMatch(s -> s.startsWith("Purse:") || s.startsWith("Piggy:") || s.contains("⏣"));
    }
    public void tick(Minecraft client) {
        if (client.level != level) { reset(); level = client.level; }
        var connection = client.getConnection();
        if (client.level == null || client.player == null || connection == null || client.hasSingleplayerServer() || connection.getServerData() == null) { reset(); return; }
        // Match Gui.extractScoreboardSidebar: a team-color sidebar takes precedence.
        Scoreboard board = client.level.getScoreboard();
        Objective objective = null;
        PlayerTeam team = board.getPlayersTeam(client.player.getScoreboardName());
        DisplaySlot slot = team == null ? null : DisplaySlot.teamColorToSlot(team.getColor());
        if (slot != null) objective = board.getDisplayObjective(slot);
        if (objective == null) objective = board.getDisplayObjective(DisplaySlot.SIDEBAR);
        List<String> lines = new ArrayList<>();
        if (objective != null) for (var entry : board.listPlayerScores(objective)) {
            lines.add(TranslationManager.plain(PlayerTeam.formatNameForTeam(board.getPlayersTeam(entry.owner()), entry.ownerName()).getString()).trim());
        }
        boolean valid = objective != null && evidence(connection.getServerData().ip,connection.serverBrand(),
            TranslationManager.plain(objective.getDisplayName().getString()),lines);
        stableTicks = valid ? Math.min(stableTicks + 1, 20) : 0; confirmed = stableTicks >= 10;
    }
    public boolean confirmed() { return confirmed; }
    public void reset() { level = null; stableTicks = 0; confirmed = false; }
}
