package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TranslationStandardTest {
    @TempDir Path root;
    @Test void doubleBraceSlotsKeepLiveValuesSymbolsAndStyles() {
        var template=new TranslationManager.Template("Gain {{N0}}☘ for {{N1}} seconds.","获得 {{N0}}☘, 持续 {{N1}} 秒.");
        var original=Component.literal("§7Gain §a+25%☘ §7for §b7.5 §7seconds.");
        var match=template.apply(TranslationManager.plain(original.getString()));
        var result=TextTranslationEngine.apply(original,match);
        assertEquals("获得 +25%☘, 持续 7.5 秒.",result.getString());
        StyledLoreTest.color(result,"+25%",ChatFormatting.GREEN);StyledLoreTest.color(result,"☘",ChatFormatting.GREEN);
        StyledLoreTest.color(result,"7.5",ChatFormatting.AQUA);
        assertEquals("§7Gain §a+25%☘ §7for §b7.5 §7seconds.",original.getString());
    }
    @Test void placeholderBraceFormAndCountCannotBeChanged() {
        assertThrows(IllegalArgumentException.class,()->new TranslationManager.Template("Gain {{N0}}","获得 {N0}"));
        assertThrows(IllegalArgumentException.class,()->new TranslationManager.Template("Gain {{N0}}","获得 {{N0}} {{N0}}"));
        assertThrows(IllegalArgumentException.class,()->new TranslationManager.Template("Gain {{N0}}","获得 {{N1}}"));
        assertNotNull(new TranslationManager.Template("Gain {value}","获得 {value}").apply("Gain +50%"));
    }
    @Test void romanRangesAndPunctuationKeepTheirSeparateStyles() {
        var source=Component.literal(" §7- Enchantment Table (§aI-V§7)");
        var replacement=TranslationManager.replacement("test",TranslationManager.plain(source.getString())," - 附魔台 (I-V)");
        var result=TextTranslationEngine.apply(source,new TranslationManager.Match("test",replacement.pieces(),Map.of("Enchantment Table","附魔台")));
        assertEquals(" - 附魔台 (I-V)",result.getString());
        StyledLoreTest.color(result,"I-V",ChatFormatting.GREEN);
        StyledLoreTest.color(result,"(",ChatFormatting.GRAY);
        var ranged=Component.literal("§c❤ Health§8: §c10 - 20");
        var match=new TranslationManager.Template("❤ Health: {{N0}} - {{N1}}","❤ 生命值: {{N0}} - {{N1}}").apply(TranslationManager.plain(ranged.getString()));
        result=TextTranslationEngine.apply(ranged,new TranslationManager.Match("test",match.pieces(),Map.of("Health","生命值")));
        assertEquals("❤ 生命值: 10 - 20",result.getString());
        StyledLoreTest.color(result,":",ChatFormatting.DARK_GRAY);
        StyledLoreTest.color(result,"-",ChatFormatting.RED);
    }
    @Test void unchangedNpcAndOrdinalsPreserveStylesWithoutSuffixLeakage() {
        var source=Component.literal("§7Visit §dKat §7on the §a6th §7anniversary.");
        var match=new TranslationManager.Template("Visit Kat on the {{N0}}th anniversary.","在 {{N0}} 周年庆期间拜访 Kat.").apply(TranslationManager.plain(source.getString()));
        var result=TextTranslationEngine.apply(source,match);
        assertEquals("在 6 周年庆期间拜访 Kat.",result.getString());
        StyledLoreTest.color(result,"Kat",ChatFormatting.LIGHT_PURPLE);
        StyledLoreTest.color(result,"6",ChatFormatting.GREEN);
        StyledLoreTest.color(result,"周年",ChatFormatting.GRAY);
    }
    @Test void literalPercentAndPlusBesideNumericSlotsKeepTheirColors() {
        var source=Component.literal("§7Gain §a+50% §7in §2⸙ Woodland§7.");
        var match=new TranslationManager.Template("Gain +{{N0}}% in ⸙ Woodland.","在 ⸙ 林地获得 +{{N0}}%.").apply(TranslationManager.plain(source.getString()));
        var result=TextTranslationEngine.apply(source,new TranslationManager.Match("test",match.pieces(),Map.of("Woodland","林地")));
        assertEquals("在 ⸙ 林地获得 +50%.",result.getString());
        StyledLoreTest.color(result,"+",ChatFormatting.GREEN);
        StyledLoreTest.color(result,"%",ChatFormatting.GREEN);
        StyledLoreTest.color(result,"⸙",ChatFormatting.DARK_GREEN);
        StyledLoreTest.color(result,"林地",ChatFormatting.DARK_GREEN);
    }
    @Test void repeatedPeriodsCanUseUniqueClauseBoundariesWithoutMixingColors() {
        var source=Component.literal("§7Gain §a10§7. §8(Max 20).");
        var match=new TranslationManager.Template("Gain {{N0}}. (Max {{N1}}).","获得 {{N0}}. (上限 {{N1}}).").apply(TranslationManager.plain(source.getString()));
        var result=TextTranslationEngine.apply(source,new TranslationManager.Match("test",match.pieces(),Map.of("Max","上限")));
        assertEquals("获得 10. (上限 20).",result.getString());
        StyledLoreTest.color(result,". (",ChatFormatting.GRAY);
        StyledLoreTest.color(result,"上限",ChatFormatting.DARK_GRAY);
        StyledLoreTest.color(result,").",ChatFormatting.DARK_GRAY);
    }
    @Test void explicitlyMappedRainbowWordUsesItsPaletteButMixedPhrasesStillFallBack() {
        var source=Component.literal("§7Find §cD§6y§ee§as§7.");
        var match=TranslationManager.replacement("test",TranslationManager.plain(source.getString()),"寻找染料.");
        var result=TextTranslationEngine.apply(source,new TranslationManager.Match("test",match.pieces(),Map.of("Dyes","染料")));
        assertEquals("寻找染料.",result.getString());
        StyledLoreTest.color(result,"染",ChatFormatting.RED);StyledLoreTest.color(result,"料",ChatFormatting.GREEN);
        var mixed=Component.literal("§cRed §6Gold §aGreen");
        var bad=TranslationManager.replacement("test",TranslationManager.plain(mixed.getString()),"测试");
        assertSame(mixed,TextTranslationEngine.apply(mixed,new TranslationManager.Match("test",bad.pieces(),Map.of("Red Gold Green","测试"))));
    }
    @Test void uniformTemplateAnchorPreservesRepeatedLiveValuesAndCompleteStyle() {
        var source=Component.literal("§7Gain §c10 Strength§7. §8(Max 10 Strength)");
        var template=new TranslationManager.Template("Gain {{N0}} Strength. (Max {{N1}} Strength)","获得 {{N0}} 力量. (上限 {{N1}} 力量)");
        var match=template.apply(TranslationManager.plain(source.getString()));
        var result=TextTranslationEngine.apply(source,new TranslationManager.Match("test",match.pieces(),Map.of("Strength","力量","(Max {{N1}} Strength)","(上限 {{N1}} 力量)")));
        assertEquals("获得 10 力量. (上限 10 力量)",result.getString());
        StyledLoreTest.color(result,"10",ChatFormatting.RED);
        StyledLoreTest.color(result,"(上限",ChatFormatting.DARK_GRAY);
        var mixed=Component.literal("§7Gain §c10 Strength§7. §8(Max §a10 §8Strength)");
        assertSame(mixed,TextTranslationEngine.apply(mixed,new TranslationManager.Match("test",match.pieces(),Map.of("Strength","力量","(Max {{N1}} Strength)","(上限 {{N1}} 力量)"))));
    }
    @Test void repeatedUnitsFollowTheirNumericCapturesAfterReordering() {
        var source=Component.literal("§7Gain §a2s §7then §c4s§7.");
        var match=new TranslationManager.Template("Gain {{N0}}s then {{N1}}s.","随后 {{N1}}s,先获得 {{N0}}s.").apply(TranslationManager.plain(source.getString()));
        var result=TextTranslationEngine.apply(source,match);
        assertEquals("随后 4s,先获得 2s.",result.getString());
        StyledLoreTest.color(result,"4s",ChatFormatting.RED);StyledLoreTest.color(result,"2s",ChatFormatting.GREEN);
    }
    @Test void gemstoneSlotsKeepDifferentFilledAndEmptyColors() {
        var source=Component.literal("§7Gemstones: §8[§7⚔§8] [⚔]");
        var match=TranslationManager.replacement("test",TranslationManager.plain(source.getString()),"宝石: [⚔] [⚔]");
        var result=TextTranslationEngine.apply(source,match);
        assertEquals("宝石: [⚔] [⚔]",result.getString());
        StyledLoreTest.color(result,"⚔",ChatFormatting.GRAY);
        assertEquals(0x555555,result.getSiblings().getLast().getStyle().getColor().getValue());
    }
    @Test void singularAndPluralAliasesCanShareChineseOnlyWhenTheirStylesAgree() {
        var source=Component.literal("§7Use §cEnemies §7and §cEnemy§7.");
        var match=TranslationManager.replacement("test",TranslationManager.plain(source.getString()),"使用敌人和敌人.");
        var result=TextTranslationEngine.apply(source,new TranslationManager.Match("test",match.pieces(),Map.of("Enemies","敌人","Enemy","敌人")));
        assertEquals("使用敌人和敌人.",result.getString());StyledLoreTest.color(result,"敌人",ChatFormatting.RED);
        var different=Component.literal("§7Use §cEnemies §7and §aEnemy§7.");
        assertSame(different,TextTranslationEngine.apply(different,new TranslationManager.Match("test",match.pieces(),Map.of("Enemies","敌人","Enemy","敌人"))));
    }
    @Test void perParagraphAnchorsDoNotInterfereWithNameOrOtherParagraphs() throws Exception {
        var directory=root.resolve("translations/items");Files.createDirectories(directory);
        Files.writeString(directory.resolve("TEST.json"),"""
            {"id":"TEST","name":"测试","styleMappings":{"Red":"赤"},
             "loreParagraphs":[{"source":["Red Blue"],"translation":"蓝 红","styleMappings":{"Red":"红","Blue":"蓝"}},
                               {"source":["Red Green"],"translation":"绿 赤","styleMappings":{"Red":"赤","Green":"绿"}}]}
            """);
        var manager=new TranslationManager();manager.load(root);
        var source=List.<Component>of(Component.literal("§cRed §9Blue"),Component.literal("§cRed §aGreen"));
        var result=LoreTranslationEngine.translate(source,manager,"TEST",(text,lines)->List.of(text),false);
        assertEquals(List.of(),result.missing());assertEquals("蓝 红",result.lines().getFirst().getString());
        StyledLoreTest.color(result.lines().getFirst(),"红",ChatFormatting.RED);
        StyledLoreTest.color(result.lines().get(1),"绿",ChatFormatting.GREEN);
    }
}
