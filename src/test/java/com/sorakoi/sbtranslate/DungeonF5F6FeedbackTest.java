package com.sorakoi.sbtranslate;

import com.google.gson.*;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DungeonF5F6FeedbackTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;com.sorakoi.sbtranslate.client.EntityNameDisplay.clear();}
    static Component chat(Component source){return SystemTranslationEngine.translate(source,"chat",manager);}
    static Component lore(List<Component> lines) {
        var result=LoreTranslationEngine.translate(lines,manager,"DUNGEON_STONE",(s,l)->List.of(s),false).lines();
        var joined=Component.empty();result.forEach(joined::append);return joined;
    }
    @Test void replayObservedF5F6WithoutMutatingInputs() throws Exception {
        var previous=DungeonFeedbackTest.manager;
        try {
            DungeonFeedbackTest.manager=manager;
            var fixture=JsonParser.parseString(Files.readString(Path.of("src/test/resources/fixtures/dungeon-f5-f6-20261103.json"))).getAsJsonArray();
            assertTrue(fixture.size()>150);
            for(var value:fixture){
                var row=value.getAsJsonObject();var source=Component.literal(row.get("source").getAsString());var copy=source.copy();
                var result=DungeonFeedbackTest.translate(source,row.get("scope").getAsString());
                assertEquals(row.get("translation").getAsString(),result.getString(),source.getString());assertEquals(copy,source);
            }
        } finally {DungeonFeedbackTest.manager=previous;com.sorakoi.sbtranslate.client.EntityNameDisplay.clear();}
    }
    @Test void sadanPlayerSpansAndLividDisguisesRetainStylesAndActions() {
        var click=new ClickEvent.SuggestCommand("/msg ESN17_OwO ");
        var hover=new HoverEvent.ShowText(Component.literal("Original detail"));
        for(String player:List.of("ESN17_OwO","Another_Player")) {
            var input=Component.literal("§c[BOSS] Sadan§f: Those Golems will be your undoing, §b"+player+"§f!")
                .withStyle(s->s.withClickEvent(click).withHoverEvent(hover));var copy=input.copy();
            var out=chat(input);assertEquals("[BOSS] Sadan: 那些傀儡会要了你的命，"+player+"！",out.getString());
            StyledLoreTest.color(out,player,ChatFormatting.AQUA);
            assertEquals(click,StyledLoreTest.styleAt(out,player).getClickEvent());
            assertEquals(hover,StyledLoreTest.styleAt(out,player).getHoverEvent());assertEquals(copy,input);
        }
        for(String npc:List.of("Livid","Doctor Livid","Purple Enderman","Smile Livid","Frog Enderman")) {
            var input=Component.literal("§c[BOSS] §d"+npc+"§f: I am but a shadow.");var out=chat(input);
            assertEquals("[BOSS] "+npc+": 我不过是一道暗影。",out.getString());StyledLoreTest.color(out,npc,ChatFormatting.LIGHT_PURPLE);
        }
        for(String line:List.of("Player: I am but a shadow.","[Party] [BOSS] Doctor Livid: So foolish!","[NPC] Unknown: Keep looking!"))
            assertEquals(line,chat(Component.literal(line)).getString());
    }
    @Test void tankSkillWrappedLoreUsesVariableDamageDefenseAndDuration() {
        for(String damage:List.of("20,000","98,765.5"))for(String percent:List.of("10%","23.5%")) {
            var lines=List.<Component>of(Component.literal("§7A Seismic Wave emerges from"),
                Component.literal("§7underneath you and travels in a"),Component.literal("§7straight line. Deals §c"+damage+" §a+"+percent),
                Component.literal("§7every §a+73 Defense §7in damage to"),Component.literal("§7any enemy in its path."));
            var out=lore(lines);assertFalse(out.getString().contains("Deals"),out.getString());assertTrue(out.getString().contains("地震波"));
            for(String token:List.of(damage,percent,"73"))assertTrue(out.getString().contains(token));
            StyledLoreTest.color(out,damage,ChatFormatting.RED);StyledLoreTest.color(out,percent,ChatFormatting.GREEN);
            StyledLoreTest.color(out,"73",ChatFormatting.GREEN);
        }
        var castle=lore(List.of(Component.literal("§7Fortifies your defence, reducing the"),
            Component.literal("§7damage you take by §a61% §7for §a37"),Component.literal("§7seconds. Also aggros all enemies in"),Component.literal("§7a §a14 §7block radius.")));
        assertFalse(castle.getString().contains("Fortifies"),castle.getString());
        for(String token:List.of("61%","37","14")){assertTrue(castle.getString().contains(token));StyledLoreTest.color(castle,token,ChatFormatting.GREEN);}
    }
    @Test void dungeonPotionsAndRecordTimesAreDynamic() {
        for(String duration:List.of("40:00","07:25","1:02:03")) {
            var input=Component.literal("§cRegeneration §eVII §7(§b"+duration+"§7)");
            var out=SystemTranslationEngine.translate(input,"item-tooltip",manager);
            assertEquals("生命恢复 VII ("+duration+")",out.getString());StyledLoreTest.color(out,duration,ChatFormatting.AQUA);
        }
        for(String time:List.of("03m 14s","58s","1h 02m 03s")) {
            var input=Component.literal("           §7☠ Defeated §cSadan §7in §a"+time+" §d(NEW RECORD!)");
            var out=chat(input);assertTrue(out.getString().contains("新纪录"),out.getString());assertTrue(out.getString().contains(time));
            StyledLoreTest.color(out,time,ChatFormatting.GREEN);StyledLoreTest.color(out,"Sadan",ChatFormatting.RED);
        }
        var critical=lore(List.of(Component.literal("§7Increases §9 Crit Chance §7by §a27% §7and"),Component.literal("§9 Crit Damage §7by §a42%.")));
        assertFalse(critical.getString().contains("Crit"),critical.getString());
        StyledLoreTest.color(critical,"27%",ChatFormatting.GREEN);StyledLoreTest.color(critical,"暴击几率",ChatFormatting.BLUE);
        var regen=SystemTranslationEngine.translate(Component.literal("§7Grants §a+17 Health Regen§7."),"item-tooltip",manager);
        assertTrue(regen.getString().contains("生命恢复"));StyledLoreTest.color(regen,"17",ChatFormatting.GREEN);
    }
    @Test void newlyObservedNamesAndDamageUseCatalog() {
        var original=Component.literal("§aYou bought §5General's Medallion§a!");
        var result=chat(original);assertTrue(result.getString().contains(manager.knownItemName("General's Medallion")),result.getString());
        StyledLoreTest.color(result,manager.knownItemName("General's Medallion"),ChatFormatting.DARK_PURPLE);
        var beating=com.sorakoi.sbtranslate.client.EntityNameDisplay.translate(Component.literal("§5Beating Heart"),manager);
        assertEquals(manager.knownItemName("Beating Heart"),beating.getString());
        for(String damage:List.of("1,319.8","2,435","0")) {
            var out=chat(Component.literal("§7The Stormy §cSuper Tank Zombie §7struck you for §a"+damage+" §7damage!"));
            assertTrue(out.getString().contains(damage));assertFalse(out.getString().contains("struck"),out.getString());
            assertTrue(out.getString().contains(manager.scope("entity-names").get("Super Tank Zombie")));StyledLoreTest.color(out,damage,ChatFormatting.GREEN);
        }
    }
}
