package com.sorakoi.sbtranslate;

import com.google.gson.*;
import com.sorakoi.sbtranslate.client.EntityNameDisplay;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DungeonFeedbackTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;EntityNameDisplay.clear();}
    static Component translate(Component source,String scope) {
        if(scope.equals("scoreboard") || scope.equals("tablist"))return HudTranslationEngine.translate(source,scope,manager);
        if(scope.equals("entity"))return EntityNameDisplay.translate(source,manager);
        if(scope.equals("tooltip-name"))return SystemTranslationEngine.translate(source,"item-name",manager);
        if(scope.equals("tooltip")) {
            var lines=LoreTranslationEngine.translate(List.of(source),manager,null,(s,l)->List.of(s),false).lines();
            return lines.getFirst();
        }
        String actual=scope.startsWith("tooltip")?"gui-tooltip":scope;
        var result=SystemTranslationEngine.translate(source,actual,manager);
        if(result!=source)return result;
        if(scope.equals("chat"))return CommunityTranslationEngine.translate(source,"chat",manager);
        return TextTranslationEngine.translate(source,t->manager.match(scope,null,t));
    }
    @Test void auditCapture() throws Exception {
        Path input=Path.of("build/dungeon-review.json");
        if(!Files.exists(input))return;
        var rows=JsonParser.parseString(Files.readString(input)).getAsJsonArray();var results=new JsonArray();
        for(var value:rows) {
            var row=value.getAsJsonObject();String scope=row.get("scope").getAsString();
            var source=Component.literal(row.get("original").getAsString());
            var result=translate(source,scope);var out=row.deepCopy();
            out.addProperty("current",result.getString());out.addProperty("changed",result!=source);results.add(out);
            assertEquals(row.get("original").getAsString(),source.getString());
        }
        Files.writeString(Path.of("build/dungeon-coverage.json"),new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(results));
    }
    @Test void replayReviewedF1F2F3Messages() throws Exception {
        var fixture=JsonParser.parseString(Files.readString(Path.of("src/test/resources/fixtures/dungeon-f1-f3-20261103.json"))).getAsJsonArray();
        for(var element:fixture) {
            var row=element.getAsJsonObject();var source=Component.literal(row.get("source").getAsString());var copy=source.copy();
            assertEquals(row.get("translation").getAsString(),translate(source,row.get("scope").getAsString()).getString(),source.getString());
            assertEquals(copy,source);
        }
    }
    @Test void dynamicSkillsAndMilestones() {
        for(String value:List.of("0","17","1,234,567","7237552.5"))for(String time:List.of("01s","30s","09m 47s","1h 02m 03s")) {
            for(String circle:List.of("❶","❷","❸","❹","❺","❻","❼","❽","❾"))for(String job:List.of("Archer","Healer","Mage","Tank","Berserk")) {
                String part=job.equals("Healer")?"You have done a total of "+value+" healing and damage so far!":"You have dealt "+value+(job.equals("Archer")?" Ranged":" Total")+" Damage so far!";
                if(job.equals("Tank"))part="You have tanked and dealt "+value+" Total Damage so far!";
                String raw=job+" Milestone "+circle+": "+part+" "+time;
                var result=translate(Component.literal(raw),"chat").getString();
                assertTrue(result.contains("里程碑"),result);assertTrue(result.contains(value));assertTrue(result.contains(time));assertTrue(result.contains(circle));
            }
            for(String raw:List.of("Your Thunderstorm hit 37 enemies for "+value+" damage.","Your Wish healed you for "+value+" health and granted you an absorption shield with 98,765 health!","Warning! The instance will close in "+time+".")) {
                assertNotEquals(raw,translate(Component.literal(raw),"chat").getString());
            }
        }
    }
    @Test void tabNameAndAlphaAreUntouchedIncludingEveryStyle() {
        var click=new ClickEvent.SuggestCommand("/msg ESN17_OwO ");
        var hover=new HoverEvent.ShowText(Component.literal("player information"));
        for(String job:List.of("Archer","Healer","Mage","Tank","Berserk")) {
            var source=Component.literal("§b[410] §aESN17_OwO §dα §7("+job+" §6XXXVII§7)")
                .withStyle(s->s.withClickEvent(click).withHoverEvent(hover));
            var original=source.copy();var out=translate(source,"tablist");
            for(String part:List.of("[410]","ESN17_OwO","α","XXXVII")) {
                assertTrue(out.getString().contains(part),out.getString());
                var expected=renderedStyles(source);var actual=renderedStyles(out);
                int before=TranslationManager.plain(source.getString()).indexOf(part),after=out.getString().indexOf(part);
                assertEquals(expected.subList(before,before+part.length()),actual.subList(after,after+part.length()),part);
            }
            assertFalse(out.getString().contains(job));assertEquals(original,source);
        }
    }
    static List<Style> renderedStyles(Component source) {
        var result=new ArrayList<Style>();
        net.minecraft.util.StringDecomposer.iterateFormatted(source,Style.EMPTY,(index,style,point)->{for(int n=0;n<Character.charCount(point);n++)result.add(style);return true;});
        return result;
    }
    @Test void observedColoredNotifications() {
        for(String raw:List.of("§6Castle of Stone§r§a is ready to use! Press §r§6§lDROP§r§a to activate it!",
            "§6Ragnarok§r§a is ready to use! Press §r§6§lDROP§r§a to activate it!",
            "§b§lCHARM! §7You charmed the §cTank Zombie §7and received §a2 §fTank Zombie Shards§7!",
            "§d§lRNG METER! §r§aReselected the §6Recombobulator 3000 §afor §cCatacombs - Floor 3§a! §e§lCLICK HERE §r§ato select a new drop!",
            "§6§lDUNGEON BUFF! §fYou found a §dBlessing of Power V§f! §7(03m 25s)",
            "     §7Granted you §a+20§7 & §a+1.1x §c Strength§7.",
            "     §7Also granted you §a+30§7 & §a+1.25x §9 Crit Damage§7.",
            "     §7Granted you §a+1.15x HP §7and §a+1.15x §c Health Regen§7.",
            "     §7Granted you §a+4§7 & §a+1.02x §a Defense §7and §a+6 §c Damage§7.",
            "§e[410] §aESN17_OwO §ehas obtained §dBlessing of Power!")) {
            // Obtained player's prefix in this fixture uses a standard Hypixel rank.
            raw=raw.replace("§e[410] §aESN17_OwO","§a[MVP+] ESN17_OwO");
            var source=Component.literal(raw);var out=translate(source,"chat");
            String plain=TranslationManager.plain(raw).strip();
            var match=SystemTranslationEngine.match(plain,"chat",manager);
            if(out==source && match!=null)TextTranslationEngine.apply(TextTranslationEngine.resolvedDisplay(Component.literal(raw.strip())),match,System.out::println);
            assertNotSame(source,out,raw);
        }
    }
    @Test void orbParagraphsAndRewardNamesUseExistingCatalog() {
        var source=List.<Component>of(Component.literal("§7Heals everyone in your group to full"),Component.literal("§7health and grants them a shield for"),
            Component.literal("§a37% §7of their maximum health. The"),Component.literal("§7cooldown is reduced by §a17 §7seconds"),Component.literal("§7for every player below §a48% §7health."));
        var result=LoreTranslationEngine.translate(source,manager,"DUNGEON_STONE",(s,l)->List.of(s),false).lines();
        var joined=Component.empty();result.forEach(joined::append);
        assertFalse(joined.getString().contains("maximum health"),joined.getString());
        for(String number:List.of("37%","17","48%")) {assertTrue(joined.getString().contains(number));StyledLoreTest.color(joined,number,net.minecraft.ChatFormatting.GREEN);}
        for(String name:List.of("Bank","Ultimate Wise","Rejuvenate","No Pain No Gain","Feather Falling")) {
            String out=translate(Component.literal("Enchanted Book ("+name+" IX)"),"chat").getString();
            assertTrue(out.contains(manager.enchantment(name)),out);assertTrue(out.contains("IX"));
        }
        for(String name:List.of("Adaptive Belt","Recombobulator 3000"))assertEquals(manager.knownItemName(name),translate(Component.literal(name),"chat").getString());
        for(String name:List.of("Tank Zombie","Zombie Soldier","Skeletor"))for(String suffix:List.of("Shard","Shards")) {
            var raw=Component.literal("§b§lCHARM! §7You charmed the §c"+name+" §7and received §a37 §f"+name+" "+suffix+"§7!");
            var out=translate(raw,"chat");assertTrue(out.getString().contains(manager.knownItemName(name+" Shard")),out.getString());
            StyledLoreTest.color(out,"37",net.minecraft.ChatFormatting.GREEN);
        }
        for(String ability:List.of("Wish","Healing Circle","Rapid Fire","Explosive Shot","Guided Sheep","Thunderstorm","Seismic Wave","Castle of Stone","Throwing Axe","Ragnarok")) {
            var line=Component.literal("§6Ability: "+ability+"  §e§lRIGHT CLICK");
            var translated=LoreTranslationEngine.translate(List.of(line),manager,"DUNGEON_STONE",(s,l)->List.of(s),false).lines().getFirst();
            assertFalse(translated.getString().contains(ability),translated.getString());assertTrue(translated.getString().contains("右键"));
        }
        for(String name:List.of("Undead Essence","Wither Essence"))for(String count:List.of("1","37","127")) {
            var line=Component.literal("§d"+name+" §8x"+count);
            var out=SystemTranslationEngine.translate(line,"item-name",manager);
            assertNotSame(line,out);assertTrue(out.getString().contains(count));
            StyledLoreTest.color(out,count,net.minecraft.ChatFormatting.DARK_GRAY);
        }
    }
    @Test void formattingAndInteractionAndPlayerChat() {
        var click=new ClickEvent.RunCommand("/unchanged");
        var source=Component.literal("§eArcher Milestone §6❷§e: You have dealt §a9,876,543 §eRanged Damage so far! §b09m 47s")
            .withStyle(s->s.withClickEvent(click).withHoverEvent(new HoverEvent.ShowText(Component.literal("original hover"))));
        var copy=source.copy();var result=translate(source,"chat");
        assertTrue(result.getString().contains("里程碑"),result.getString());
        StyledLoreTest.color(result,"9,876,543",net.minecraft.ChatFormatting.GREEN);
        StyledLoreTest.color(result,"09m 47s",net.minecraft.ChatFormatting.AQUA);
        assertEquals(click,StyledLoreTest.styleAt(result,"9,876,543").getClickEvent());assertEquals(copy,source);
        for(String raw:List.of("[Party] Tester: Used Thunderstorm!","Tester: Good luck.","[NPC] Unknown: Good luck."))assertEquals(raw,translate(Component.literal(raw),"chat").getString());
    }
    @Test void scoreboardInvisibleCodesAndEntityAmounts() {
        assertTrue(translate(Component.literal(" §7 §cThe Catac§u§combs §7(F3)"),"scoreboard").getString().contains("地下墓穴"));
        assertTrue(translate(Component.literal("Time Elapsed: §a§q§a09m 47s"),"scoreboard").getString().contains("已用时间"));
        for(String amount:List.of("1","25","123,456"))assertTrue(translate(Component.literal("Undead Essence x"+amount),"entity").getString().contains(amount));
        assertEquals("技能伤害 7",translate(Component.literal("ABILITY DAMAGE 7"),"entity").getString());
        assertEquals("Mort",translate(Component.literal("Mort"),"entity").getString());
    }
}
