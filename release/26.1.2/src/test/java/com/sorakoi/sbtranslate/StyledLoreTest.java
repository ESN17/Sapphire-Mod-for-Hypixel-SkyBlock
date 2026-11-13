package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StyledLoreTest {
    @TempDir Path root;
    TranslationManager manager;
    @BeforeEach void load() throws Exception {
        Path translations=root.resolve("translations"); Files.createDirectories(translations.resolve("items"));
        Files.copy(Path.of("src/main/resources/defaults/translations/items/ASPECT_OF_THE_END.json"),translations.resolve("items/ASPECT_OF_THE_END.json"));
        Files.copy(Path.of("src/main/resources/defaults/translations/reforges.json"),translations.resolve("reforges.json"));
        manager=new TranslationManager();manager.load(root);
    }
    Component line(String text) {
        return TextTranslationEngine.translate(Component.literal(text),s -> manager.match("item","ASPECT_OF_THE_END",s));
    }
    static Style styleAt(Component component,String word) {
        int position=component.getString().indexOf(word); assertTrue(position>=0,component.getString()+" missing "+word);
        int[] offset={0}; List<Style> styles=new ArrayList<>();
        component.visit((style,text)->{for(int i=0;i<text.length();i++)styles.add(style);offset[0]+=text.length();return Optional.empty();},Style.EMPTY);
        return styles.get(position);
    }
    static void color(Component component,String word,ChatFormatting color) { assertEquals(color.getColor(),styleAt(component,word).getColor().getValue(),word); }
    @Test void abilityManaAndGemstonesKeepDistinctColorsAndBold() {
        Component ability=line("§6Ability: Instant Transmission  §e§lRIGHT CLICK");
        assertEquals("技能:瞬间传送  右键点击",ability.getString());
        color(ability,"技能",ChatFormatting.GOLD);color(ability,"右键",ChatFormatting.YELLOW);
        assertTrue(styleAt(ability,"右键").isBold());assertFalse(styleAt(ability,"技能").isBold());
        Component mana=line("§8Mana Cost: §b25");
        assertEquals("魔力消耗:25",mana.getString());color(mana,"魔力",ChatFormatting.DARK_GRAY);
        color(mana,"25",ChatFormatting.AQUA);color(mana,"",ChatFormatting.AQUA);
        Component gems=line("§7Gemstones: §8[§7§8]");
        assertEquals("宝石槽:[]",gems.getString());color(gems,"宝石槽",ChatFormatting.GRAY);
        color(gems,"[",ChatFormatting.DARK_GRAY);color(gems,"",ChatFormatting.GRAY);color(gems,"]",ChatFormatting.DARK_GRAY);
    }
    @Test void everyTransmissionTunerDistanceUsesServerValueAndSemanticColors() {
        for (int distance=8;distance<=12;distance++) {
            var source=List.<Component>of(Component.literal("§7Teleport §a"+distance+" blocks§7 ahead of you and"),Component.literal("§7gain §a+50 §f Speed§7 for §a3 seconds§7."));
            var originals=source.stream().map(Component::copy).toList();
            var result=LoreTranslationEngine.translate(source,manager,"ASPECT_OF_THE_END",(text,lines)->List.of(text),false);
            assertTrue(result.changed());assertTrue(result.missing().isEmpty());assertEquals(1,result.lines().size());
            Component translated=result.lines().getFirst();
            assertEquals("向前传送 "+distance+" 格,并获得 +50  速度,持续 3 秒.",translated.getString());
            for (String green : List.of(""+distance,"格","+50","3","秒")) color(translated,green,ChatFormatting.GREEN);
            color(translated,"向前",ChatFormatting.GRAY);color(translated,"速度",ChatFormatting.WHITE);color(translated,"",ChatFormatting.WHITE);color(translated,".",ChatFormatting.GRAY);
            assertEquals(originals,source);
        }
    }
    @Test void reforgeBonusesKeepBlueValuesAndParentheses() {
        Component damage=line("§7Damage: §c+125 §9(+25)");
        assertEquals("伤害:+125 (+25)",damage.getString());
        color(damage,"伤害",ChatFormatting.GRAY);color(damage,"+125",ChatFormatting.RED);
        color(damage,"+25",ChatFormatting.BLUE);color(damage,"(",ChatFormatting.BLUE);
    }
    @Test void reforgePrefixAndStarsKeepStylesAndUnknownPrefixStaysEnglish() {
        Component source=Component.literal("§9Heroic Aspect of the End §6✪✪");
        Component translated=TextTranslationEngine.itemName(source,"Aspect of the End","末影之刃",manager::reforge);
        assertEquals("英雄的末影之刃 ✪✪",translated.getString());color(translated,"英雄",ChatFormatting.BLUE);color(translated,"✪",ChatFormatting.GOLD);
        assertEquals("§9Heroic Aspect of the End §6✪✪",source.getString());
        assertEquals("Unknown 末影之刃",TextTranslationEngine.itemName(Component.literal("Unknown Aspect of the End"),"Aspect of the End","末影之刃",manager::reforge).getString());
        assertEquals("英雄的Aspect of the End",TextTranslationEngine.itemName(Component.literal("Heroic Aspect of the End"),"Aspect of the End","",manager::reforge).getString());
        assertEquals("⚚ 英雄的末影之刃 ✪",TextTranslationEngine.itemName(Component.literal("§6⚚ §9Heroic Aspect of the End §6✪"),"Aspect of the End","末影之刃",manager::reforge).getString());
        Component wrong=Component.literal("Heroic Aspect of the Ending");
        assertSame(wrong,TextTranslationEngine.itemName(wrong,"Aspect of the End","末影之刃",manager::reforge));
    }
    @Test void reorderingMappedWordsPreservesInteractiveStyleAndDoesNotTouchPayloads() {
        var event=new ClickEvent.RunCommand("/original");
        Component source=Component.literal("Red").withStyle(ChatFormatting.RED).withStyle(s->s.withClickEvent(event))
            .append(Component.literal(" Blue").setStyle(Style.EMPTY.withColor(ChatFormatting.BLUE).withClickEvent(event)));
        var match=new TranslationManager.Match("test",TranslationManager.replacement("test",source.getString(),"蓝 红").pieces(),Map.of("Red","红","Blue","蓝"));
        Component translated=TextTranslationEngine.apply(source,match);
        assertEquals("蓝 红",translated.getString());color(translated,"蓝",ChatFormatting.BLUE);color(translated,"红",ChatFormatting.RED);
        assertEquals(event,styleAt(translated,"红").getClickEvent());assertEquals(event,styleAt(translated,"蓝").getClickEvent());
    }
    @Test void unknownOrInterruptedParagraphDoesNotConsumeOtherLines() {
        var lines=List.<Component>of(Component.literal("Teleport 12 blocks ahead of you and"),Component.empty(),Component.literal("gain +50  Speed for 3 seconds."));
        var result=LoreTranslationEngine.translate(lines,manager,"ASPECT_OF_THE_END",(text,original)->List.of(text),false);
        assertFalse(result.changed());assertEquals(lines,result.lines());
    }
    @Test void invalidParagraphReloadPreservesPreviousDictionary() throws Exception {
        Files.writeString(root.resolve("translations/items/ASPECT_OF_THE_END.json"),"""
            {"id":"ASPECT_OF_THE_END","loreParagraphs":[{"source":["Teleport {distance} blocks"],"translation":"传送 {wrong} 格"}]}
            """);
        assertThrows(Exception.class,()->manager.load(root));assertEquals("末影之刃",manager.item("ASPECT_OF_THE_END").name());
    }
    @Test void ambiguousAnchorsFallBackAndRepeatedNumbersUsePlaceholderPositions() {
        Component ambiguous=Component.literal("Red Red").withStyle(ChatFormatting.RED);
        var replacement=TranslationManager.replacement("test",ambiguous.getString(),"红 红");
        assertEquals("红 红",TextTranslationEngine.apply(ambiguous,new TranslationManager.Match("test",replacement.pieces(),Map.of("Red","红"))).getString());
        Component different=Component.literal("Red ").withStyle(ChatFormatting.RED).append(Component.literal("Red").withStyle(ChatFormatting.BLUE));
        var ordered=TextTranslationEngine.apply(different,new TranslationManager.Match("test",replacement.pieces(),Map.of("Red","红")));
        assertEquals("红 红",ordered.getString());
        assertEquals(0xff5555,ordered.getSiblings().getFirst().getStyle().getColor().getValue());
        assertEquals(0x5555ff,ordered.getSiblings().getLast().getStyle().getColor().getValue());
        var ambiguousReplacement=TranslationManager.replacement("test",different.getString(),"红色 红");
        assertSame(different,TextTranslationEngine.apply(different,new TranslationManager.Match("test",ambiguousReplacement.pieces(),Map.of("Red","红"))));
        Component input=Component.literal("§7Gain §a3§7 for §b3");
        var match=new TranslationManager.Template("Gain {amount} for {duration}","持续 {duration},获得 {amount}").apply(TranslationManager.plain(input.getString()));
        Component result=TextTranslationEngine.apply(input,match);
        assertEquals("持续 3,获得 3",result.getString());color(result,"3",ChatFormatting.AQUA);
        assertEquals(0x55ff55,result.getSiblings().getLast().getStyle().getColor().getValue());
    }
    @Test void copiedSymbolsKeepAllStyleFlagsAndEvents() {
        Style style=Style.EMPTY.withColor(ChatFormatting.AQUA).withBold(true).withItalic(true).withUnderlined(true)
            .withStrikethrough(true).withObfuscated(true).withInsertion("original").withHoverEvent(new HoverEvent.ShowText(Component.literal("untranslated hover")));
        Component source=Component.literal("Mana Cost: ").withStyle(ChatFormatting.DARK_GRAY).append(Component.literal("50").setStyle(style));
        var result=TextTranslationEngine.translate(source,s->manager.match("item","ASPECT_OF_THE_END",s));
        assertEquals("魔力消耗:50",result.getString());assertEquals(style,styleAt(result,"50"));assertEquals(style,styleAt(result,""));
    }
}
