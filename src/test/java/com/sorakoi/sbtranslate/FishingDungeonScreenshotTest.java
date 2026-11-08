package com.sorakoi.sbtranslate;

import com.google.gson.*;
import com.sorakoi.sbtranslate.compat.*;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FishingDungeonScreenshotTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;}
    static Component chat(Component source){return SystemTranslationEngine.translate(source,"chat",manager);}
    @Test void coloredDoorAndEnergyCrystalScreenshots() {
        for(String raw:List.of("§cYou do not currently have an §bEnergy Crystal§c! Find one and bring it here!",
            "§e§lRIGHT CLICK §7on a §8WITHER §7door to open it. This key can only be used to open §a1 §7door!",
            "§c[SAPPDEV] ESN17_OwO §aopened a §8§lWITHER §adoor!")) {
            var source=Component.literal(raw);var copy=source.copy();var result=chat(source);
            if(source==result)TextTranslationEngine.apply(TextTranslationEngine.resolvedDisplay(source),SystemTranslationEngine.match(TranslationManager.plain(raw),"chat",manager),System.out::println);
            assertNotSame(source,result,raw);assertEquals(copy,source);
            if(raw.contains("Energy Crystal"))StyledLoreTest.color(result,"能量水晶",ChatFormatting.AQUA);
            else StyledLoreTest.color(result,"凋灵之门",ChatFormatting.DARK_GRAY);
        }
    }
    @Test void singularPluralTimersAndAllClassBonuses() {
        for(String n:List.of("1","2","30","127"))for(String unit:List.of("second","seconds")) {
            var out=chat(Component.literal("§aStarting in "+n+" "+unit+"."));assertEquals("将在 "+n+" 秒后开始。",out.getString());
            for(String ability:List.of("Regular Ability","Ultimate"))assertFalse(chat(Component.literal("Your "+ability+" is currently on cooldown for "+n+" more "+unit+".")).getString().contains("cooldown"));
        }
        for(String job:List.of("Archer","Mage","Healer","Tank","Berserk")) {
            var raw=Component.literal("§6Your §a"+job+" §6stats are doubled because you are the only player using this class!");
            assertTrue(chat(raw).getString().contains("翻倍"),chat(raw).getString());
        }
        for(String stat:List.of("Extra Arrow Chance","Arrow Damage"))for(String n:List.of("87%","209.2%","1234.75%")) {
            var raw=Component.literal("§a[Archer] §f"+stat+" §c"+n+" §7-> §a259.2%");
            var out=chat(raw);assertFalse(out.getString().contains(stat),out.getString());
            StyledLoreTest.color(out,n,ChatFormatting.RED);StyledLoreTest.color(out,"259.2%",ChatFormatting.GREEN);
        }
    }
    @Test void sixChestKindsAndCatalogQuantityAreCovered() {
        for(String type:List.of("WOOD","GOLD","DIAMOND","EMERALD","OBSIDIAN","BEDROCK")) {
            assertFalse(chat(Component.literal("§f§l"+type+" CHEST REWARDS")).getString().contains("CHEST"));
            for(String name:List.of(type.charAt(0)+type.substring(1).toLowerCase(),type.charAt(0)+type.substring(1).toLowerCase()+" Chest"))
                assertTrue(SystemTranslationEngine.translate(Component.literal(name),"gui",manager).getString().endsWith("宝箱"));
        }
        var click=new ClickEvent.SuggestCommand("/msg ESN17_OwO ");
        for(String n:List.of("1","2","37","1,234")) {
            var source=Component.literal("§c[SAPPDEV] ESN17_OwO §ehas obtained §9Superboom TNT §7x"+n+"§e!").withStyle(s->s.withClickEvent(click));
            var copy=source.copy();var result=chat(source);
            assertTrue(result.getString().contains(manager.knownItemName("Superboom TNT")),result.getString());
            assertTrue(result.getString().contains("x"+n));StyledLoreTest.color(result,manager.knownItemName("Superboom TNT"),ChatFormatting.BLUE);
            StyledLoreTest.color(result,"x"+n,ChatFormatting.GRAY);assertEquals(click,StyledLoreTest.styleAt(result,"ESN17_OwO").getClickEvent());assertEquals(copy,source);
        }
    }
    @Test void allWikiSeaCreatureNoticesAndDoubleHookVariants() throws Exception {
        var fixture=JsonParser.parseString(Files.readString(Path.of("src/test/resources/fixtures/fishing-wiki-20261104.json"))).getAsJsonArray();
        assertTrue(fixture.size()>=100);var failures=new ArrayList<String>();
        for(var value:fixture) {
            var row=value.getAsJsonObject();String raw=row.get("sampleRaw").getAsString().replace("[player]","ESN17_OwO");
            var variants=new ArrayList<String>();variants.add(raw);
            if(row.get("catch").getAsBoolean()){variants.add("§e§lDOUBLE HOOK! §r"+raw);variants.add(raw+" §e§lDOUBLE HOOK!");}
            for(String text:variants)for(String scope:List.of("chat","skyhanni")) {
                var source=Component.literal(text);var copy=source.copy();var out=SystemTranslationEngine.translate(source,scope,manager);
                if(out==source || out.getString().contains("DOUBLE HOOK")) {
                    failures.add(scope+" "+row.get("page").getAsString()+": "+text);
                    var match=SystemTranslationEngine.match(TranslationManager.plain(text),scope,manager);
                    if(match!=null)TextTranslationEngine.apply(TextTranslationEngine.resolvedDisplay(source),match,System.out::println);
                }
                assertEquals(copy,source);
            }
        }
        assertTrue(failures.isEmpty(),String.join("\n",failures));
        for(String text:List.of("§eIt's a §aDouble Hook§e!","§eIt's a §aDouble Hook§e! Woot woot!"))
            assertTrue(chat(Component.literal(text)).getString().contains("一竿双钓"));
        for(String mob:List.of("Frozen Steve","Yeti","Water Hydra"))for(String article:List.of("a","an"))for(boolean before:List.of(true,false)) {
            String raw="§9You caught "+article+" §b"+mob+"§9!";raw=before?"§e§lDOUBLE HOOK! "+raw:raw+" §e§lDOUBLE HOOK!";
            var out=chat(Component.literal(raw));assertFalse(out.getString().contains(mob),out.getString());assertTrue(out.getString().contains("一竿双钓"));
            StyledLoreTest.color(out,"一竿双钓",ChatFormatting.YELLOW);
        }
        var player=Component.literal("[MVP+] Player: DOUBLE HOOK! You caught a Frozen Steve!");assertSame(player,chat(player));
    }
    @Test void skyHanniPaddedTrackerCellsAndNotifications() {
        var live=new SkyHanniLiveTranslation();var dict=new SkyHanniDictionary(Map.of("Custom Tracker","自定义统计"));
        for(String name:List.of("Summoning Eye","Judgement Core","Ench Ender Pearl","Custom Tracker")) {
            var click=new ClickEvent.RunCommand("/sh");var hover=new HoverEvent.ShowText(Component.literal("source tooltip"));
            var source=Component.literal(" §6"+name+" ").withStyle(s->s.withClickEvent(click).withHoverEvent(hover));var copy=source.copy();
            var out=live.translate(source,manager,dict);assertFalse(out.getString().contains(name),out.getString());
            assertTrue(out.getString().startsWith(" ") && out.getString().endsWith(" "));
            String target=out.getString().strip();StyledLoreTest.color(out,target,ChatFormatting.GOLD);
            assertEquals(click,StyledLoreTest.styleAt(out,target).getClickEvent());assertEquals(hover,StyledLoreTest.styleAt(out,target).getHoverEvent());assertEquals(copy,source);
            assertEquals(out.getString(),TranslationManager.plain(live.translate(" §6"+name+" ",manager,dict)));
        }
        for(String raw:List.of("§eTotal Uptime: §b17m 28s §c(Paused!)","§7Sea Creature Tracker:"," §7- §e1,234 §7Total Sea Creatures",
            "§eDisplay Mode §a[Today]","§eYou obtained this item recently.","§e[SkyHanni] You are not in SkyBlock!")) {
            var out=live.translate(Component.literal(raw),manager,dict);assertNotEquals(TranslationManager.plain(raw),out.getString(),raw);
        }
        for(String raw:List.of("ESN17_OwO","  UnknownFutureItem  ","[Party] ESN17_OwO: Starting in 1 second."))assertEquals(raw,live.translate(Component.literal(raw),manager,dict).getString());
    }
}
