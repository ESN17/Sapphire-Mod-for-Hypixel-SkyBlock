package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RiftFeedbackTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;}
    static Component hud(String raw,String expected) {
        var source=Component.literal(raw);var before=source.copy();
        var out=HudTranslationEngine.translate(source,"tablist",manager);
        assertEquals(expected,out.getString());assertEquals(before,source);return out;
    }
    @Test void screenshotRiftFieldsTranslateAndPreserveValues() {
        hud("§bArea: §7The Rift","区域: 裂隙维度");
        var visited=hud("§fVisited Rift: §e676 times","到访裂隙: 676 次");
        StyledLoreTest.color(visited,"676",ChatFormatting.YELLOW);
        var motes=hud("§fLifetime Motes: §d18,025,490","累计裂隙尘埃: 18,025,490");
        StyledLoreTest.color(motes,"18,025,490",ChatFormatting.LIGHT_PURPLE);
        hud("§fRift Time Left: §a40m","裂隙剩余时间: 40分钟");
        hud("§fTimecharms: §c8/8","时光护符: 8/8");
        hud("§fEnigma Souls: §548/52","恩尼格玛之魂: 48/52");
        hud("§fRift Damage: §5❁28","裂隙伤害: ❁28");
        hud("§fMana Regen: §b☷131","魔力回复: ☷131");
        hud("§bEssence:","精华:");hud("§fSun Gecko: §d0","太阳壁虎: 0");
        hud("§dShen: §f(31h)","Shen: (31小时)");
    }
    @Test void wrappedAdvertisementAndFooterTranslateWithoutTouchingNpcNames() {
        var ad=hud("§bBarry §fis the best\n§fmayor that SkyBlock\n§fhas ever had.","Barry 是空岛历来\n最出色的\n市长.");
        StyledLoreTest.color(ad,"Barry",ChatFormatting.AQUA);
        hud("§f1 day, 19 hours","1 天 19 小时");
        hud("§eGarden Farming Fortune VII §f6 Days","花园耕作时运 VII 6 天");
        hud("§fMontezuma: §b9/9","§fMontezuma: §b9/9");
        var player=Component.literal("§aMotes_Player");assertSame(player,HudTranslationEngine.translate(player,"tablist",manager));
        var clock=Component.literal("6:30am");assertSame(clock,HudTranslationEngine.translate(clock,"scoreboard",manager));
    }
    @Test void currencySingularPluralAndPricesWorkAcrossScopes() {
        for(String word:List.of("Mote","Motes","mote","motes")) {
            for(String scope:List.of("scoreboard","tablist","actionbar","bossbar","title","subtitle","gui-tooltip")) {
                var source=Component.literal("§fCost: §d1,250 "+word);
                var out=SystemTranslationEngine.translate(source,scope,manager);
                assertEquals("花费: 1,250 裂隙尘埃",out.getString());StyledLoreTest.color(out,"裂隙尘埃",ChatFormatting.LIGHT_PURPLE);
            }
            var out=SystemTranslationEngine.translate(Component.literal("§aYou gained §d20 "+word+"§a!"),"chat",manager);
            assertEquals("你获得了 20 裂隙尘埃!",out.getString());
        }
        var source=Component.literal("§fMotes: §d17,000,056");
        assertEquals("裂隙尘埃: 17,000,056",HudTranslationEngine.translate(source,"scoreboard",manager).getString());
        var chat=Component.literal("[MVP+] Player: You gained 20 Motes!");
        assertSame(chat,SystemTranslationEngine.translate(chat,"chat",manager));
    }
    @Test void currencyItemLoreAndOrbNotificationUseOneTerm() {
        assertEquals("裂隙尘埃",manager.item("SKYBLOCK_MOTE").name());
        for(String id:List.of("BUBBA_BLISTER","CHOCOLATE_CHIP","DISCRITE","ENCHANTED_BOOK_BUNDLE_QUANTUM","ENCHANTED_VAMPIRIC_MELON","GUARDIAN_LUCKY_BLOCK","HIGHLITE","MCGRUBBER_BURGER","SPOTLITE")) {
            var item=manager.item(id);assertTrue(item.paragraphs().stream().filter(p->String.join(" ",p.source()).matches("(?is).*\\bmotes?\\b.*")).allMatch(p->p.translation().contains("裂隙尘埃")&&!p.translation().matches(".*微[粒尘].*")),id);
        }
        var orb=Component.literal("§5§lORB! §dPicked up §5+25 Motes§d!");
        var out=CommunityTranslationEngine.translate(orb,"chat",manager);
        assertEquals("光球！获得 +25 裂隙尘埃！",out.getString());StyledLoreTest.color(out,"裂隙尘埃",ChatFormatting.DARK_PURPLE);
        var lore=LoreTranslationEngine.translate(List.of(Component.literal("§7Cost: §d123 Motes")),manager,null,(s,o)->List.of(s),false);
        assertEquals("花费: 123 裂隙尘埃",lore.lines().getFirst().getString());
    }
    @Test void enigmaTermsAgreeAcrossItemsDialogueAndMenus() {
        assertEquals("恩尼格玛的斗篷",manager.item("ENIGMA_CLOAK").name());
        assertEquals("恩尼格玛之魂 (裂隙)",manager.item("SKYBLOCK_ENIGMA_SOUL").name());
        assertEquals("恩尼格玛居所",manager.scope("locations").get("Enigma's Crib"));
        for(var p:manager.item("ENIGMA_CLOAK").paragraphs())
            if(String.join(" ",p.source()).contains("Enigma"))assertTrue(p.translation().contains("恩尼格玛"));
        var out=CommunityTranslationEngine.translate(Component.literal("§cYou have already found that Enigma Soul!"),"chat",manager);
        assertEquals("你已经收集过这个恩尼格玛之魂了！",out.getString());
        assertEquals("穿戴恩尼格玛的斗篷",CommunityTranslationEngine.translate(Component.literal("Wear Enigma's Cloak"),"tooltip",manager).getString());
        // The ordinary English adjective is not the character's name.
        assertTrue(manager.item("POTION_SECRETS;1").paragraphs().stream().anyMatch(p->p.translation().contains("谜团")));
    }
}
