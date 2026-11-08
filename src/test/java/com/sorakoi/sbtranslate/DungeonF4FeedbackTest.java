package com.sorakoi.sbtranslate;

import com.google.gson.*;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DungeonF4FeedbackTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;}
    static Component chat(String raw){return SystemTranslationEngine.translate(Component.literal(raw),"chat",manager);}
    @Test void replayF4CaptureWithoutChangingOriginals() throws Exception {
        var previous=DungeonFeedbackTest.manager;
        try {
            DungeonFeedbackTest.manager=manager;
            var rows=JsonParser.parseString(Files.readString(Path.of("src/test/resources/fixtures/dungeon-f4-20261103.json"))).getAsJsonArray();
            for(var value:rows) {
                var row=value.getAsJsonObject();var original=Component.literal(row.get("source").getAsString());var copy=original.copy();
                var result=DungeonFeedbackTest.translate(original,row.get("scope").getAsString());
                assertEquals(row.get("translation").getAsString(),result.getString(),original.getString());assertEquals(copy,original);
            }
        } finally {DungeonFeedbackTest.manager=previous;com.sorakoi.sbtranslate.client.EntityNameDisplay.clear();}
    }
    @Test void crowdAndStatueKeepPlayerNamesAndVariableNumbers() {
        for(String player:List.of("ESN17_OwO","Other_Player"))for(String count:List.of("1","3","42","1,234")) {
            for(String text:List.of("[CROWD] Zombie Soldier: "+player+" has no thumbs!",
                "[CROWD] Sniper: My goodness, "+player+" really can't aim!!",
                "[STATUE] Oruo the Omniscient: "+player+" answered Question #"+count+" correctly!")) {
                String out=chat(text).getString();assertNotEquals(text,out);assertTrue(out.contains(player));
                if(text.contains("Question #"))assertTrue(out.contains(count));
            }
            String source="[CROWD] Super Tank Zombie: Make it a best of "+count+", best of "+count+"!!!!";
            assertTrue(chat(source).getString().contains(count));assertFalse(chat(source).getString().contains("best of"));
        }
        for(String raw:List.of("Player: You picked up the Spirit Bow! Use it to attack Thorn!","[Party] [CROWD] Sniper: BOOOOO!!!"))assertEquals(raw,chat(raw).getString());
    }
    @Test void coloredSpiritBowAndQuizOptionsKeepInteractions() {
        var click=new ClickEvent.RunCommand("/unchanged");
        for(String raw:List.of("§6 ⓒ §a61 Minions","§6 ⓐ §a80 Fairy Souls","§6 ⓒ §aStonk Dragon")) {
            var source=Component.literal(raw).withStyle(s->s.withClickEvent(click));var copy=source.copy();
            var out=SystemTranslationEngine.translate(source,"chat",manager);
            assertNotSame(source,out,raw);assertEquals(copy,source);
            String marker=raw.contains("ⓒ")?"ⓒ":"ⓐ";
            StyledLoreTest.color(out,marker,ChatFormatting.GOLD);
            assertEquals(click,StyledLoreTest.styleAt(out,marker).getClickEvent());
        }
        var source=Component.literal("§bOther_Player§a picked up the §5Spirit Bow§a!");
        var out=SystemTranslationEngine.translate(source,"subtitle",manager);
        assertFalse(out.getString().contains("picked up"));StyledLoreTest.color(out,"Other_Player",ChatFormatting.AQUA);
        assertEquals(source.getString(),"§bOther_Player§a picked up the §5Spirit Bow§a!");
    }
    @Test void timeBlessingHandlesRepeatedAndDistinctNumbersAndStyles() {
        for(String value:List.of("24","37.5","1,234"))for(String multiplier:List.of("1.12","2.05")) {
            var raw=Component.literal("     §7Granted you §a+"+value+"§7 & §a+"+multiplier+"x HP§7, §a+"+value+"§7 & §a+"+multiplier+"x §a Defense§7, §a+"+value+"§7 & §a+"+multiplier+"x §b Intelligence§7, and §a+"+value+"§7 & §a+"+multiplier+"x §c Strength§7.");
            var out=SystemTranslationEngine.translate(raw,"chat",manager);
            assertNotSame(raw,out,raw.getString());assertTrue(out.getString().contains("生命值"));
            assertTrue(out.getString().contains(value));assertTrue(out.getString().contains(multiplier));
            StyledLoreTest.color(out,"智力",ChatFormatting.AQUA);StyledLoreTest.color(out,"力量",ChatFormatting.RED);
        }
    }
    @Test void observedRagnarokTooltipJoinsWrapsAndUsesLiveStats() {
        for(String multiplier:List.of("1.5","2.75"))for(String duration:List.of("15","37")) {
            var lines=List.<Component>of(Component.literal("§7Grants §e137 Attack Speed§7, §f503"),
                Component.literal("§fSpeed §7and §a"+multiplier+"x §7melee damage"),
                Component.literal("§7increase for §a"+duration+" §7seconds. Also"),
                Component.literal("§7summons §a7 §7Zombie minions to aid you"),Component.literal("§7in battle!"));
            var result=LoreTranslationEngine.translate(lines,manager,"DUNGEON_STONE",(s,l)->List.of(s),false).lines();
            var combined=Component.empty();result.forEach(combined::append);
            assertFalse(combined.getString().contains("summons"),combined.getString());
            for(String value:List.of("137","503",multiplier,duration,"7"))assertTrue(combined.getString().contains(value));
            StyledLoreTest.color(combined,"137",ChatFormatting.YELLOW);StyledLoreTest.color(combined,"503",ChatFormatting.WHITE);
            StyledLoreTest.color(combined,multiplier,ChatFormatting.GREEN);
        }
    }
}
