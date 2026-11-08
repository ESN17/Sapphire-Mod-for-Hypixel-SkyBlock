package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class WikiCorpusTest {
    @TempDir Path directory;

    @Test void obsoleteStyleDoesNotPreventLoadingTheTranslationOrOtherStyles() throws Exception {
        Path folder=Files.createDirectories(directory.resolve("translations"));
        Files.writeString(folder.resolve("community-wiki.json"),"""
            {"schemaVersion":1,"entries":[
              {"scope":"gui","npc":"","source":"Open Menu","translation":"打开菜单",
               "styleMappings":{"Menu":"菜单","Open":"开启","Missing":"打开"}}
            ]}
            """);
        var manager=new TranslationManager();
        manager.load(directory);
        var entry=manager.community().get("gui","","Open Menu");
        assertEquals("打开菜单",entry.translation());
        assertEquals(java.util.Map.of("Menu","菜单"),entry.styles());
    }

    @Test void rewardCapturesAndMulticolorRatingKeepEveryOriginalStyle() {
        var source=Component.literal("§7Rewards: §8+§35000§7 Experience §8+§72 §e✰✰✰§7✰✰ §bBoxes §8+§7Bonus");
        var before=source.copy();
        var match=new TranslationManager.Template("Rewards: {{N0}} Experience {{N1}} ✰✰✰✰✰ Boxes +Bonus",
            "奖励: {{N0}} 经验 {{N1}} ✰✰✰✰✰ 盒子 +加成").apply(TranslationManager.plain(source.getString()));
        var result=TextTranslationEngine.apply(source,new TranslationManager.Match(match.rule(),match.pieces(),java.util.Map.of("Boxes","盒子")));
        assertEquals("奖励: +5000 经验 +2 ✰✰✰✰✰ 盒子 +加成",result.getString());assertEquals(before,source);
        StyledLoreTest.color(result,"5000",net.minecraft.ChatFormatting.DARK_AQUA);
        StyledLoreTest.color(result,"+",net.minecraft.ChatFormatting.DARK_GRAY);
        StyledLoreTest.color(result,"盒子",net.minecraft.ChatFormatting.AQUA);
        var starColors=new java.util.ArrayList<Integer>();
        result.visit((style,text)->{text.codePoints().filter(c->c=='✰').forEach(c->starColors.add(style.getColor().getValue()));return java.util.Optional.empty();},net.minecraft.network.chat.Style.EMPTY);
        assertEquals(java.util.List.of(0xffff55,0xffff55,0xffff55,0xaaaaaa,0xaaaaaa),starColors);
    }

    @Test void supplementKeepsReviewedEntriesAndScopeBoundaries() throws Exception {
        Path primary=directory.resolve("primary.json"), supplement=directory.resolve("supplement.json");
        Files.writeString(primary,"""
            {"schemaVersion":1,"entries":[
              {"scope":"tooltip","npc":"","source":"Go Back","translation":"返回","styleMappings":{}},
              {"scope":"tooltip","npc":"","source":"Duration: {{N0}} Hours","translation":"时长: {{N0}} 小时","styleMappings":{}}
            ]}
            """);
        Files.writeString(supplement,"""
            {"schemaVersion":1,"entries":[
              {"scope":"tooltip","npc":"","source":"Go Back","translation":"回去","styleMappings":{}},
              {"scope":"tooltip","npc":"","source":"Duration: 6 Hours","translation":"持续六小时","styleMappings":{}},
              {"scope":"system","npc":"","source":"Found {{N0}} Motes!","translation":"找到了 {{N0}} 裂隙尘埃!","styleMappings":{"Motes":"裂隙尘埃"}},
              {"scope":"npc","npc":"Enigma","source":"Welcome!","translation":"欢迎!","styleMappings":{}}
            ]}
            """);
        var merged=CommunityTranslationEngine.merge(CommunityTranslationEngine.load(primary),CommunityTranslationEngine.load(supplement));
        assertEquals("返回",merged.get("tooltip","","Go Back").translation());
        assertEquals("时长: 6 小时",merged.get("tooltip","","Duration: 6 Hours").translation());
        assertEquals(1,merged.candidates("tooltip","Go Back").size());
        assertNull(merged.get("system","","Go Back"));
        assertNull(merged.get("npc","Other NPC","Welcome!"));
        assertEquals("欢迎!",merged.get("npc","Enigma","Welcome!").translation());
        assertEquals(1,merged.candidates("system","Found 20 Motes!").size());
    }

    @Test void optionalWikiFileDoesNotChangeAbsentCorpus() throws Exception {
        var missing=CommunityTranslationEngine.load(directory.resolve("absent.json"));
        var merged=CommunityTranslationEngine.merge(CommunityTranslationEngine.empty(),missing);
        assertTrue(merged.entries().isEmpty());
        assertTrue(merged.paragraphs().isEmpty());
    }

    @Test void immutableIndexIsSharedButSameSizeEditsInvalidateIt() throws Exception {
        Path path=directory.resolve("shared.json");
        String json="""
            {"schemaVersion":1,"entries":[{"scope":"gui","npc":"","source":"Test","translation":"测试","styleMappings":{}}]}
            """;
        Files.writeString(path,json);var modified=Files.getLastModifiedTime(path);
        var first=CommunityTranslationEngine.load(path);
        assertSame(first,CommunityTranslationEngine.load(path));
        Files.writeString(path,json.replace("测试","试验"));Files.setLastModifiedTime(path,modified);
        var changed=CommunityTranslationEngine.load(path);
        assertNotSame(first,changed);assertEquals("试验",changed.get("gui","","Test").translation());
    }

    @Test void partsLoadAndRejectPathTraversal() throws Exception {
        Path manifest=directory.resolve("community-wiki.json");
        Files.createDirectories(directory.resolve("community-wiki"));
        Files.writeString(manifest,"""
            {"schemaVersion":1,"entries":[],"parts":["wiki-001.json"]}
            """);
        Files.writeString(directory.resolve("community-wiki/wiki-001.json"),"""
            {"schemaVersion":1,"entries":[{"scope":"gui","npc":"","source":"Menu","translation":"菜单","styleMappings":{}}]}
            """);
        assertEquals("菜单",CommunityTranslationEngine.load(manifest).get("gui","","Menu").translation());
        Files.writeString(manifest,"""
            {"schemaVersion":1,"entries":[],"parts":["../outside.json"]}
            """);
        assertThrows(java.io.IOException.class,()->CommunityTranslationEngine.load(manifest));
    }

    @Test void statusCountsKeepLastSuccessfulLoadOnFailure() throws Exception {
        Path translations=directory.resolve("translations");Files.createDirectories(translations);
        Path wiki=translations.resolve("community-wiki.json");
        Files.writeString(wiki,"""
            {"schemaVersion":1,"heldReviewRows":3,"entries":[{"scope":"gui","npc":"","source":"Test Menu","translation":"测试菜单","styleMappings":{}}]}
            """);
        var manager=new TranslationManager();manager.load(directory);assertEquals(1,manager.wikiRuleCount());
        assertEquals(3,manager.wikiPendingReviewCount());
        Files.writeString(wiki,"{}");
        assertThrows(java.io.IOException.class,()->manager.load(directory));
        assertEquals(1,manager.wikiRuleCount());
        assertEquals(3,manager.wikiPendingReviewCount());
        assertEquals("测试菜单",manager.community().get("gui","","Test Menu").translation());
    }

    @Test void numericNpcTextAndResetFormattingPreservePrefixAndOriginal() throws Exception {
        Path translations=directory.resolve("translations");Files.createDirectories(translations);
        Files.writeString(translations.resolve("community-wiki.json"),"""
            {"schemaVersion":1,"entries":[
             {"scope":"npc","npc":"Example","source":"First 0, second 0.","translation":"第一个 0, 第二个 0.",
              "numericSource":"First {{N0}}, second {{N1}}.","numericTranslation":"第一个 {{N0}}, 第二个 {{N1}}.",
              "styleMappings":{"First":"第一个","second":"第二个"}},
             {"scope":"npc","npc":"Example","source":"Today, vampires are on the menu.","translation":"今天, 吸血鬼上了菜单.",
              "styleMappings":{"vampires":"吸血鬼"}},
             {"scope":"npc","npc":"Example","source":"Welcome, [player]!","translation":"欢迎, [player]!","styleMappings":{}}
            ]}
            """);
        var manager=new TranslationManager();manager.load(directory);
        var original=Component.literal("§e[NPC] Example: §fFirst §a0§f, second §c0§f.");var before=original.copy();
        var result=CommunityTranslationEngine.translate(original,"chat",manager);
        assertEquals("[NPC] Example: 第一个 0, 第二个 0.",result.getString());assertEquals(before,original);
        var live=Component.literal("§e[NPC] Example: §fFirst §a17§f, second §c39§f.");
        var translated=CommunityTranslationEngine.translate(live,"chat",manager);
        assertEquals("[NPC] Example: 第一个 17, 第二个 39.",translated.getString());
        StyledLoreTest.color(translated,"17",net.minecraft.ChatFormatting.GREEN);
        StyledLoreTest.color(translated,"39",net.minecraft.ChatFormatting.RED);
        var other=Component.literal("[NPC] Stranger: First 17, second 39.");
        assertSame(other,CommunityTranslationEngine.translate(other,"chat",manager));
        var player=Component.literal("[MVP+] Example: First 17, second 39.");
        assertSame(player,CommunityTranslationEngine.translate(player,"chat",manager));
        var greeting=Component.literal("§e[NPC] Example:  §fWelcome, §bESN17_OwO§f!");
        var greeted=CommunityTranslationEngine.translate(greeting,"chat",manager);
        assertEquals("[NPC] Example:  欢迎, ESN17_OwO!",greeted.getString());
        StyledLoreTest.color(greeted,"ESN17_OwO",net.minecraft.ChatFormatting.AQUA);
        var reset=Component.literal("§e[NPC] Example: §fToday, §4vampires §rare on the menu.");
        assertEquals("[NPC] Example: 今天, 吸血鬼上了菜单.",CommunityTranslationEngine.translate(reset,"chat",manager).getString());
    }
}
