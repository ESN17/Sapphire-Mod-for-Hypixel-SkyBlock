package com.sorakoi.sbtranslate.client;

import com.sorakoi.sbtranslate.Sapphire;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import java.util.*;

/** Verify real mixed-in display methods without rewriting parser-facing tab/boss objects. */
final class HudOverlaySmokeTest {
    static void run() {
        var mc=Minecraft.getInstance();
        var tab=new PlayerTabOverlay(mc,mc.gui);
        var boss=new BossHealthOverlay(mc);
        boolean tabEnabled=Sapphire.CONFIG.tablist,bossEnabled=Sapphire.CONFIG.bossbar,original=Sapphire.CONFIG.showOriginal;
        try {
            Sapphire.CONFIG.tablist=true;Sapphire.CONFIG.bossbar=true;Sapphire.CONFIG.showOriginal=false;
            var header=Component.literal("§aPlayers §f(21)\n§bArea: §7The Rift");
            var footer=Component.literal("§eStats:\n§fBank: §649.9M");
            tab.setHeader(header);tab.setFooter(footer);
            require(hook(tab,"sapphire$header",header).getString().contains("裂隙维度"),"translated Tab header");
            require(hook(tab,"sapphire$footer",footer).getString().contains("属性"),"translated Tab footer");
            for(String fieldName:List.of("header","footer")) {
                var field=PlayerTabOverlay.class.getDeclaredField(fieldName);field.setAccessible(true);
                require(field.get(tab)==(fieldName.equals("header")?header:footer),"original Tab field retained");
            }
            for(String time:List.of("01:03","00:42")) {
                var name=Component.literal("§e§lEVENT §6§lRAFFLE §e§lACTIVE IN §b§lFAR RESERVE §e§lfor §a§l"+time);
                var event=new LerpingBossEvent(UUID.randomUUID(),name,0.65f,BossEvent.BossBarColor.YELLOW,BossEvent.BossBarOverlay.PROGRESS,false,false,false);
                var translated=hook(boss,"sapphire$name",event.getName());
                require(translated.getString().contains("抽奖") && translated.getString().contains(time),"each boss display and latest countdown");
                require(event.getName()==name && event.getProgress()==0.65f,"boss source and progress unchanged");
                Sapphire.CONFIG.bossbar=false;
                require(hook(boss,"sapphire$name",name)==name,"boss disabled fallback");
                Sapphire.CONFIG.bossbar=true;
            }
            Sapphire.CONFIG.tablist=false;
            require(hook(tab,"sapphire$header",header)==header && hook(tab,"sapphire$footer",footer)==footer,"Tab disabled fallback");
            Sapphire.CONFIG.tablist=true;Sapphire.CONFIG.showOriginal=true;
            var bilingual=hook(tab,"sapphire$header",header).getString();
            require(bilingual.contains("裂隙维度") && bilingual.contains("The Rift"),"original display option retained");
            Sapphire.LOGGER.info("SAPPHIRE_HUD_OVERLAY_SMOKE_OK: Tab header/footer, multiple boss titles and live values translated; parser data/progress, switches and original mode preserved");
        } catch(ReflectiveOperationException e){throw new IllegalStateException("HUD overlay smoke failed",e);}
        finally {Sapphire.CONFIG.tablist=tabEnabled;Sapphire.CONFIG.bossbar=bossEnabled;Sapphire.CONFIG.showOriginal=original;}
    }
    private static Component hook(Object owner,String suffix,Component source) throws ReflectiveOperationException {
        var method=Arrays.stream(owner.getClass().getDeclaredMethods()).filter(m->m.getName().endsWith(suffix)).findFirst().orElseThrow();
        method.setAccessible(true);return (Component)method.invoke(owner,source);
    }
    private static void require(boolean success,String check){if(!success)throw new IllegalStateException("HUD overlay smoke failed: "+check);}
}
