package com.sorakoi.sbtranslate;

import com.google.gson.*;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FirstFeedbackTest {
    static TranslationManager manager;
    static JsonObject fixtures;
    @BeforeAll static void load() throws Exception {
        manager=new TranslationManager(); manager.load(Path.of("src/main/resources/defaults"));
        fixtures=TranslationManager.readObject(Path.of("src/test/resources/fixtures/first-feedback-items.json"));
    }
    static String live(String text) { return text.replaceAll("\\{\\{[A-Za-z][A-Za-z0-9_]*}}|\\{[A-Za-z0-9_]+}","50"); }
    static List<Component> source(String id) {
        List<Component> lines=new ArrayList<>();
        for(var line:fixtures.getAsJsonObject(id).getAsJsonArray("lore"))
            lines.add(Component.literal(live(line.getAsString())).withStyle(ChatFormatting.WHITE));
        return lines;
    }
    static LoreTranslationEngine.Result render(String id) {
        return LoreTranslationEngine.translate(source(id),manager,id,(text,lines)->List.of(text),false);
    }
    @Test void allEditedNamesAndMatchingParagraphsKeepTheirRealReferenceStyles() {
        List<String> failures=new ArrayList<>();int checked=0;
        for(var fixture:fixtures.entrySet()) {
            String id=fixture.getKey(); var entry=manager.item(id);
            Component name=Component.literal(live(fixture.getValue().getAsJsonObject().get("name").getAsString())).withStyle(ChatFormatting.WHITE);
            Component output=TextTranslationEngine.itemName(name,entry.originalName(),entry.name(),manager::reforge,entry.styleMappings());
            if(!TranslationManager.plain(output.getString()).equals(live(entry.name()))) failures.add(id+" name: "+output.getString());
            var lines=source(id);
            for(var p:entry.paragraphs()) for(int start=0;start+p.source().size()<=lines.size();start++) {
                var parts=lines.subList(start,start+p.source().size());
                if(parts.stream().anyMatch(c->c.getString().isBlank()))continue;
                MutableComponent joined=Component.empty();
                for(var part:parts){if(!joined.getSiblings().isEmpty())joined.append(" ");joined.append(part.copy());}
                var match=p.template().apply(TranslationManager.plain(joined.getString()));
                if(match==null)continue;
                var before=joined.copy();List<String> diagnostics=new ArrayList<>();
                Component translated=TextTranslationEngine.apply(joined,new TranslationManager.Match(match.rule(),match.pieces(),p.styleMappings()==null?entry.styleMappings():p.styleMappings()),diagnostics::add);
                assertEquals(before,joined,id+" source mutated");checked++;
                if(!TranslationManager.plain(translated.getString()).equals(match.text()))failures.add(id+" "+p.source()+": "+diagnostics);
            }
        }
        assertTrue(checked>1000,"reference paragraphs audited");
        assertEquals(List.of(),failures,String.join("\n",failures));
    }
    @Test void magicBallKeepsFiveSeparateResponsesAndTenSeparateStats() {
        var result=render("MAGIC_8_BALL");assertEquals(List.of(),result.missing());
        var lines=result.lines();
        assertEquals(5,lines.stream().filter(c->c.getString().startsWith("✦")||c.getString().startsWith("✧")).count());
        assertEquals(10,lines.stream().filter(c->c.getString().startsWith("    ")).count());
        for(var c:lines) {
            String text=c.getString();
            if(text.startsWith("✦")||text.startsWith("✧"))assertFalse(text.contains("+"),text);
            if(text.startsWith("    "))assertEquals(1,text.chars().filter(v->v=='+').count(),text);
            if(text.contains("+25"))StyledLoreTest.color(c,"农业幸运",ChatFormatting.GOLD);
            if(text.contains("+1☯")&&text.contains("农业智慧"))StyledLoreTest.color(c,"农业智慧",ChatFormatting.DARK_AQUA);
        }
    }
    @Test void alignedAndPeaceFlavorAreTranslatedWithoutDroppingFunctionalStats() {
        var gyro=render("GYROKINETIC_WAND");assertEquals(List.of(),gyro.missing());
        var heading=gyro.lines().stream().filter(c->c.getString().equals("||| 铁索连环")).findFirst().orElseThrow();
        StyledLoreTest.color(heading,"铁索连环",ChatFormatting.GREEN);
        assertTrue(gyro.lines().stream().anyMatch(c->c.getString().contains("铁索连环")&&c.getString().replace(" ","").contains("6秒")));
        var peace=render("THE_ART_OF_PEACE");assertEquals(List.of(),peace.missing());
        assertTrue(peace.lines().stream().anyMatch(c->c.getString().equals("别特么动不动引用我哥的话了。")));
        assertTrue(peace.lines().stream().anyMatch(c->c.getString().equals(" —— 孙子弟弟")));
        assertTrue(peace.lines().stream().anyMatch(c->c.getString().contains("+40 生命值")));
    }
}
