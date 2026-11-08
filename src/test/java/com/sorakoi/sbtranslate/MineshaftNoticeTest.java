package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MineshaftNoticeTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;}
    static Component chat(String raw) {
        var source=Component.literal(raw);var copy=source.copy();var out=SystemTranslationEngine.translate(source,"chat",manager);
        if(out==source)TextTranslationEngine.apply(TextTranslationEngine.resolvedDisplay(source),SystemTranslationEngine.match(TranslationManager.plain(raw).strip(),"chat",manager),System.out::println);
        assertNotSame(source,out,raw);assertEquals(copy,source);return out;
    }
    @Test void allCorpseTitlesKeepTypeColorAndBoldHeading() {
        var names=Map.of("LAPIS","青金石","TUNGSTEN","钨","UMBER","棕土","VANGUARD","先锋");
        for(var row:names.entrySet()) {
            var color=switch(row.getKey()){case "LAPIS"->ChatFormatting.BLUE;case "TUNGSTEN"->ChatFormatting.GRAY;case "UMBER"->ChatFormatting.GOLD;default->ChatFormatting.WHITE;};
            var out=chat("  §r§b§l§r"+color+"§l"+row.getKey()+" §r§b§lCORPSE LOOT!");
            assertEquals("  "+row.getValue()+"尸体战利品!",out.getString());
            StyledLoreTest.color(out,row.getValue(),color);StyledLoreTest.color(out,"尸体战利品!",ChatFormatting.AQUA);
            assertTrue(StyledLoreTest.styleAt(out,"尸体战利品!").isBold());
        }
    }
    @Test void corpseProfitBothModsAndShortNumbers() {
        for(String type:List.of("Lapis","Tungsten","Umber","Vanguard"))for(String n:List.of("0","40,922","53k","1.25M","-5k","-40,922")) {
            var out=chat("§e[SkyHanni] Profit for §9"+type+" Corpse§e: §6"+n);
            assertTrue(out.getString().contains("尸体收益"));StyledLoreTest.color(out,n,ChatFormatting.GOLD);
            out=chat("§b[Skyblocker] §aCorpse Profit: "+n);
            assertTrue(out.getString().contains("尸体收益："+n));StyledLoreTest.color(out,n,ChatFormatting.GREEN);
        }
    }
    @Test void miningRequirementsKeepNpcAndVariablePower() {
        for(String power:List.of("9","12","100")) {
            var out=chat("§cYou need a tool with a §r§aBreaking Power §r§cof §r§6"+power+"§r§c to mine Umber§r§c! Speak to §r§dFragilis §r§cby the entrance to the Crystal Hollows to learn more!");
            assertTrue(out.getString().contains("水晶洞窟"));assertTrue(out.getString().contains("棕土"));
            StyledLoreTest.color(out,power,ChatFormatting.GOLD);StyledLoreTest.color(out,"破坏力",ChatFormatting.GREEN);
            StyledLoreTest.color(out,"Fragilis",ChatFormatting.LIGHT_PURPLE);
        }
    }
    @Test void rewardAndPetNamesComeFromTheDictionary() {
        for(String n:List.of("3","25","5,211")) {
            var out=chat("    §aGreen Goblin Egg §7x"+n);
            assertTrue(out.getString().contains(manager.knownItemName("Green Goblin Egg")));StyledLoreTest.color(out,"x"+n,ChatFormatting.GRAY);
        }
        var gem=manager.item("FINE_CITRINE_GEM").name();
        var out=chat("    §9 Fine Citrine Gemstone");assertEquals("    "+gem,out.getString());StyledLoreTest.color(out,gem,ChatFormatting.BLUE);
        out=chat("    §6 §9Fine Citrine Gemstone");StyledLoreTest.color(out,"",ChatFormatting.GOLD);StyledLoreTest.color(out,"精致黄水晶",ChatFormatting.BLUE);
        var pet=manager.knownItemName("Glacite Golem");assertNotNull(pet);
        out=chat("§aYou summoned your §6Glacite Golem§a!");assertTrue(out.getString().contains(pet));StyledLoreTest.color(out,pet,ChatFormatting.GOLD);
        var unknown=Component.literal("PlayerUnknown x13");assertEquals(unknown.getString(),SystemTranslationEngine.translate(unknown,"chat",manager).getString());
    }
}
