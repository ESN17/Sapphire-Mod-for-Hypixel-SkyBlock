package com.sorakoi.sbtranslate;

import com.google.gson.*;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DungeonF7M7FeedbackTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;com.sorakoi.sbtranslate.client.EntityNameDisplay.clear();}
    static Component translate(Component source,String scope){return SystemTranslationEngine.translate(source,scope,manager);}
    @Test void terminalCountersAndPlayerStylesAreVariable() {
        var click=new ClickEvent.SuggestCommand("/msg ESN17_OwO ");
        var hover=new HoverEvent.ShowText(Component.literal("original detail"));
        for(String player:List.of("ESN17_OwO","Another_Player"))for(String count:List.of("1/7","8/8","12/19"))
        for(String action:List.of("activated a terminal","activated a lever","completed a device"))for(String scope:List.of("chat","subtitle")) {
            var source=Component.literal("§b"+player+"§a "+action+"! (§e"+count+"§a)").withStyle(s->s.withClickEvent(click).withHoverEvent(hover));
            var copy=source.copy();var out=translate(source,scope);
            assertFalse(out.getString().contains(action),out.getString());assertTrue(out.getString().contains(count));
            StyledLoreTest.color(out,player,ChatFormatting.AQUA);StyledLoreTest.color(out,count,ChatFormatting.YELLOW);
            assertEquals(click,StyledLoreTest.styleAt(out,player).getClickEvent());assertEquals(hover,StyledLoreTest.styleAt(out,player).getHoverEvent());
            assertEquals(copy,source);
        }
    }
    @Test void numbersTimesAndCombatDetailsAreNotHardcoded() {
        for(String n:List.of("0","17","999,999,999","32,123.5")) {
            for(String message:List.of("Your Implosion hit 17 enemies for "+n+" damage.",
                "Storm's Giga Lightning hit you for "+n+" true damage.","The Wither King's Dimensional Slash hit you for "+n+" damage.",
                "OtherPlayer's Wish healed you for "+n+" health and granted you an absorption shield with 98,765 health!",
                "+"+n+" Mage Experience (Team Bonus)")) {
                var out=translate(Component.literal(message),"chat").getString();assertNotEquals(message,out);assertTrue(out.contains(n),out);
            }
        }
        for(String time:List.of("5s","30s","127s")) {
            assertEquals("你将在 "+time+" 后复活",translate(Component.literal("You will be revived in "+time),"subtitle").getString());
            assertEquals("T9!     将在 "+time+" 后施放",translate(Component.literal("T9!     CASTING IN "+time),"actionbar").getString());
        }
        for(String time:List.of("06s","01m 27s","1h 02m 03s")) {
            var source=Component.literal("§6§lDUNGEON BUFF! §bOtherPlayer §ffound a §dBlessing of Stone IX§f! §7("+time+")");
            var out=translate(source,"chat");assertTrue(out.getString().contains("磐石祝福 IX"),out.getString());assertTrue(out.getString().contains(time));
            StyledLoreTest.color(out,"OtherPlayer",ChatFormatting.AQUA);StyledLoreTest.color(out,"IX",ChatFormatting.LIGHT_PURPLE);
        }
    }
    @Test void terminalLettersAndDeadPlayersRetainOriginalIdentifiers() {
        for(char c='A';c<='Z';c++)assertEquals("选择英文名称以 '"+c+"' 开头的物品",translate(Component.literal("What starts with: '"+c+"'?"),"gui").getString());
        var input=Component.literal("§b[410] §aESN17_OwO §dα §7(DEAD)");var copy=input.copy();
        var out=HudTranslationEngine.translate(input,"tablist",manager);assertEquals("[410] ESN17_OwO α（死亡）",out.getString());
        StyledLoreTest.color(out,"[410]",ChatFormatting.AQUA);StyledLoreTest.color(out,"ESN17_OwO",ChatFormatting.GREEN);StyledLoreTest.color(out,"α",ChatFormatting.LIGHT_PURPLE);
        assertEquals(copy,input);
        for(String source:List.of("[410] [MVP+] ESN17_OwO: Come closer!","Party > Player: You... again?","[SkyHanni] My soul is disposable.","[Skyblocker] The gate has been destroyed!"))
            assertEquals(source,translate(Component.literal(source),"chat").getString());
    }
    @Test void reviewedCaptureReplaysIncludingActualFormatting() throws Exception {
        Path path=Path.of("src/test/resources/fixtures/dungeon-f7-m7-20261104.json");
        assertTrue(Files.exists(path));
        var previous=DungeonFeedbackTest.manager;
        try {
            DungeonFeedbackTest.manager=manager;
            for(var value:JsonParser.parseString(Files.readString(path)).getAsJsonArray()) {
                var row=value.getAsJsonObject();var source=Component.literal(row.get("source").getAsString());var copy=source.copy();
                var result=DungeonFeedbackTest.translate(source,row.get("scope").getAsString());
                assertEquals(row.get("translation").getAsString(),result.getString(),source.getString());assertEquals(copy,source);
            }
        }finally {DungeonFeedbackTest.manager=previous;com.sorakoi.sbtranslate.client.EntityNameDisplay.clear();}
    }
    @Test void weirdosPossessivesAndWrappedLootInstructions() {
        for(String raw:List.of("[NPC] Carver: At least one of them is lying, and the reward is not in §c§cVictoria's §rchest!",
            "[NPC] Marshall: They are both telling the truth. The reward isn't in §c§cLuverne's §rchest.")) {
            var source=Component.literal(raw);var out=translate(source,"chat");
            if(out==source)TextTranslationEngine.apply(TextTranslationEngine.resolvedDisplay(source),SystemTranslationEngine.match(TranslationManager.plain(raw),"chat",manager),System.out::println);
            assertFalse(out.getString().contains("chest"),out.getString());
            StyledLoreTest.color(out,raw.contains("Victoria")?"Victoria":"Luverne",ChatFormatting.RED);
        }
        var lines=List.<Component>of(Component.literal("§7Consume a §5Kismet Feather §7to reroll"),
            Component.literal("§7the loot in this chest."),Component.literal("§7You may only use a feather once"),Component.literal("§7per run/chest where applicable."));
        var translated=LoreTranslationEngine.translate(lines,manager,null,(s,l)->List.of(s),false).lines();
        var out=Component.empty();translated.forEach(out::append);
        assertFalse(out.getString().contains("reroll"),out.getString());assertTrue(out.getString().contains(manager.knownItemName("Kismet Feather")),out.getString());
    }
    @Test void abilityIconsAndArrowPreviewAreTranslatedWithVariableNumbers() {
        for(String ability:List.of("Wither Impact","Spirit Leap","Big Heal","Howl","Pickobulus")) {
            var raw=Component.literal("§6⦾ Ability: "+ability+"  §e§lRIGHT CLICK");
            var out=translate(raw,"item-tooltip");assertTrue(out.getString().startsWith("⦾ 技能："),out.getString());
            assertFalse(out.getString().contains(ability));StyledLoreTest.color(out,"右键点击",ChatFormatting.YELLOW);
        }
        var lines=List.<Component>of(Component.literal("§7Arrows have a §a37.5% §7chance to"),Component.literal("§7bounce to another target."),
            Component.literal("§7Bounces up to §a19 §7targets, each"),Component.literal("§7bounce dealing §c23% §7less damage."));
        var result=LoreTranslationEngine.translate(lines,manager,null,(s,l)->List.of(s),false).lines();
        var out=Component.empty();result.forEach(out::append);
        assertFalse(out.getString().contains("bounce"),out.getString());
        for(String n:List.of("37.5%","19","23%"))assertTrue(out.getString().contains(n));
        StyledLoreTest.color(out,"37.5%",ChatFormatting.GREEN);StyledLoreTest.color(out,"23%",ChatFormatting.RED);
        assertEquals("已选择："+manager.knownItemName("Bouncy Arrow"),translate(Component.literal("Selected: Bouncy Arrow"),"item-tooltip").getString());
    }
}
