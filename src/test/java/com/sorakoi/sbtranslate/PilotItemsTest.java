package com.sorakoi.sbtranslate;

import com.google.gson.JsonObject;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;

class PilotItemsTest {
    TranslationManager manager;
    JsonObject fixtures;
    @BeforeEach void load() throws Exception {
        manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));
        fixtures=TranslationManager.readObject(Path.of("src/test/resources/fixtures/pilot-item-lore.json"));
    }
    static String values(String text,int level) {
        text=text.replace("{LVL}",""+level);
        var matcher=Pattern.compile("\\{[A-Za-z0-9_]+}").matcher(text);
        return matcher.replaceAll(""+level);
    }
    List<Component> source(String id,int level) {
        List<Component> lines=new ArrayList<>();
        for(var line:fixtures.getAsJsonObject(id).getAsJsonArray("lore")) lines.add(Component.literal(values(line.getAsString(),level)));
        return lines;
    }
    @Test void all100ItemsRenderChineseNamesAndEveryLoreLineWithoutMutatingSource() {
        List<String> failures=new ArrayList<>();
        for(var fixture:fixtures.entrySet()) {
            String id=fixture.getKey();var source=source(id,50);var before=source.stream().map(Component::copy).toList();
            var result=LoreTranslationEngine.translate(source,manager,id,(text,lines)->List.of(text),false);
            // Command lists intentionally retain executable English commands and their original styles.
            var missing=result.missing().stream().filter(s->!s.stripLeading().startsWith("/")).toList();
            if(!missing.isEmpty())failures.add(id+": "+missing);
            assertEquals(before,source,id+" source mutated");
            assertEquals(numbers(source),numbers(result.lines()),id+" displayed numeric values");
            assertEquals(source.stream().filter(c->c.getString().isBlank()).count(),result.lines().stream().filter(c->c.getString().isBlank()).count(),id+" blank separators");
            var entry=manager.item(id);
            Component original=Component.literal(values(fixture.getValue().getAsJsonObject().get("name").getAsString(),50));
            Component name=TextTranslationEngine.itemName(original,entry.originalName(),entry.name(),manager::reforge,entry.styleMappings());
            assertEquals(values(entry.name(),50),name.getString(),id);
        }
        assertEquals(List.of(),failures,String.join("\n",failures));
    }
    @Test void manifestContainsExactly100UniqueBundledDictionaries() throws Exception {
        var ids=Files.readAllLines(Path.of("src/main/resources/defaults/pilot-items.txt"));
        assertEquals(100,ids.size());assertEquals(100,new HashSet<>(ids).size());assertEquals(fixtures.keySet(),new HashSet<>(ids));
        for(String id:ids)try(var stream=getClass().getResourceAsStream("/defaults/translations/items/"+id+".json")){assertNotNull(stream,id);}
    }
    @Test void petValuesAndLevelsRemainLive() {
        for(String id:List.of("SHEEP;4","TIGER;4","LION;4","RABBIT;4","SILVERFISH;4","GRIFFIN;4"))for(int level:List.of(1,37,100)) {
            var source=source(id,level);
            var result=LoreTranslationEngine.translate(source,manager,id,(text,lines)->List.of(text),false);
            assertEquals(List.of(),result.missing(),id+" level "+level);
            assertTrue(result.lines().stream().anyMatch(c->c.getString().contains(""+level)),id);
            var entry=manager.item(id);
            var name=TextTranslationEngine.itemName(Component.literal(values(fixtures.getAsJsonObject(id).get("name").getAsString(),level)),entry.originalName(),entry.name(),manager::reforge,entry.styleMappings());
            assertTrue(name.getString().startsWith("[等级 "+level+"] "));
            StyledLoreTest.color(name,entry.name().substring(entry.name().indexOf("] ")+2),ChatFormatting.GOLD);
            StyledLoreTest.color(name,"等级",ChatFormatting.GRAY);
        }
    }
    @Test void changedDamageReforgeAndTransmissionDistanceKeepColors() {
        var entry=manager.item("ASPECT_OF_THE_VOID");
        var name=TextTranslationEngine.itemName(Component.literal("§5Heroic Aspect of the Void §6✪"),entry.originalName(),entry.name(),manager::reforge,entry.styleMappings());
        assertEquals("英雄的虚空之刃 ✪",name.getString());StyledLoreTest.color(name,"✪",ChatFormatting.GOLD);
        for(int distance=8;distance<=12;distance++) {
            var source=List.<Component>of(Component.literal("§7Damage: §c+173 §e(+30)"),
                Component.literal("§7Teleport §a"+distance+" blocks§7 ahead of you and"),
                Component.literal("§7gain §a+50 §f Speed§7 for §a3 seconds§7."));
            var result=LoreTranslationEngine.translate(source,manager,"ASPECT_OF_THE_VOID",(text,lines)->List.of(text),false);
            assertEquals(List.of(),result.missing());assertEquals("伤害:+173 (+30)",result.lines().getFirst().getString());
            StyledLoreTest.color(result.lines().getFirst(),"+173",ChatFormatting.RED);StyledLoreTest.color(result.lines().getFirst(),"+30",ChatFormatting.YELLOW);
            var line=result.lines().get(1);assertTrue(line.getString().contains(distance+" 格"));
            StyledLoreTest.color(line,"格",ChatFormatting.GREEN);StyledLoreTest.color(line,"速度",ChatFormatting.WHITE);StyledLoreTest.color(line,"秒",ChatFormatting.GREEN);
        }
    }
    @Test void overlappingLabelsUseLongestPhraseAndUnitsKeepTheirOriginalStyle() {
        var source=List.<Component>of(Component.literal("§7Grants §c+1  Damage §7and §a+2 §b"),
            Component.literal("§bIntelligence §7per §cCatacombs §7level."));
        var translated=LoreTranslationEngine.translate(source,manager,"HYPERION",(text,lines)->List.of(text),false).lines().getFirst();
        StyledLoreTest.color(translated,"地牢",ChatFormatting.RED);StyledLoreTest.color(translated,"智力",ChatFormatting.AQUA);
        StyledLoreTest.color(translated,"+2",ChatFormatting.GREEN);StyledLoreTest.color(translated,"伤害",ChatFormatting.RED);
        translated=TextTranslationEngine.translate(Component.literal(" §9 Crit Damage"),s->manager.match("item","DRAGON_CLAW",s));
        assertEquals("  暴击伤害",translated.getString());StyledLoreTest.color(translated,"暴击伤害",ChatFormatting.BLUE);
        translated=TextTranslationEngine.translate(Component.literal("§8Cooldown: §a2.5s"),s->manager.match("item","LIVID_DAGGER",s));
        assertEquals("冷却时间:2.5秒",translated.getString());StyledLoreTest.color(translated,"秒",ChatFormatting.GREEN);
        translated=LoreTranslationEngine.translate(source("LION;4",50),manager,"LION;4",(text,lines)->List.of(text),false)
            .lines().stream().filter(c->c.getString().contains(manager.enchantment("Combo"))).findFirst().orElseThrow();
        StyledLoreTest.color(translated,"连击",ChatFormatting.LIGHT_PURPLE);assertTrue(StyledLoreTest.styleAt(translated,"连击").isBold());
    }
    @Test void repeatedSymbolsOnlyInheritWhenTheirCompleteStylesAgree() {
        var same=Component.literal("§7Heal §c10❤ §7and §c20❤");
        var template=new TranslationManager.Template("Heal {first}❤ and {second}❤","恢复 {first}❤ 和 {second}❤");
        var result=TextTranslationEngine.apply(same,template.apply(TranslationManager.plain(same.getString())));
        assertEquals("恢复 10❤ 和 20❤",result.getString());StyledLoreTest.color(result,"❤",ChatFormatting.RED);
        var different=Component.literal("§7Heal §c10❤ §7and §b20❤");
        assertSame(different,TextTranslationEngine.apply(different,template.apply(TranslationManager.plain(different.getString()))));
        var interactive=Component.literal("Heal ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("10❤").setStyle(Style.EMPTY.withColor(ChatFormatting.RED).withInsertion("one")))
            .append(Component.literal(" and ").setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
            .append(Component.literal("20❤").setStyle(Style.EMPTY.withColor(ChatFormatting.RED).withInsertion("two")));
        assertSame(interactive,TextTranslationEngine.apply(interactive,template.apply(interactive.getString())));
    }
    static List<String> numbers(List<Component> lines) {
        var pattern=Pattern.compile("[+-]?(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?%?");
        List<String> values=new ArrayList<>();
        for(var line:lines)pattern.matcher(TranslationManager.plain(line.getString())).results().forEach(m->values.add(m.group()));
        return values.stream().sorted().toList();
    }
}
