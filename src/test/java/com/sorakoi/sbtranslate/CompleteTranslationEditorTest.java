package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class CompleteTranslationEditorTest {
    @TempDir Path root;
    private void write(String name,String data) throws Exception {var p=root.resolve("translations").resolve(name);Files.createDirectories(p.getParent());Files.writeString(p,data);}
    @Test void editsNestedMenuTitlesAndParagraphsWithoutTouchingMetadata() throws Exception {
        write("gui-menus.json","""
            {"menus":[{"id":"auction","titles":{"Auction/House":"拍卖行"},"loreParagraphs":[{"source":["You own {value} auctions."],"translation":"你有 {value} 场拍卖。","styleMappings":{}}]}]}
            """);
        var entries=TranslationEditorStore.entries(root);assertEquals(2,entries.size());
        var title=entries.stream().filter(e->e.source().equals("Auction/House")).findFirst().orElseThrow();
        TranslationEditorStore.save(root,title,"拍卖大厅");
        var paragraph=entries.stream().filter(e->e.source().contains("auctions")).findFirst().orElseThrow();
        assertThrows(java.io.IOException.class,()->TranslationEditorStore.save(root,paragraph,"你有拍卖"));
        TranslationEditorStore.save(root,paragraph,"正在管理 {value} 场拍卖。");
        var data=TranslationManager.readObject(root.resolve("translations/gui-menus.json"));assertEquals("auction",data.getAsJsonArray("menus").get(0).getAsJsonObject().get("id").getAsString());
        assertThrows(java.io.IOException.class,()->TranslationEditorStore.save(root,title,"旧编辑器覆盖"));
    }
    @Test void indexesCommunityShardsAndProtectsAttribution() throws Exception {
        write("community-wiki/npc-0.json","""
            {"license":"CC-BY-NC-SA-3.0","entries":[{"scope":"npc","npc":"Enigma","source":"Hello!","translation":"你好！","sampleRaw":"§aHello!","sourceUrl":"https://example.com/source"}]}
            """);
        var e=TranslationEditorStore.entries(root).getFirst();assertTrue(e.id().contains("Enigma"));
        assertEquals("欢迎！",TranslationEditorStore.preview(root,e,"欢迎！").getString());
        TranslationEditorStore.save(root,e,"欢迎！");
        var o=TranslationManager.readObject(root.resolve("translations/community-wiki/npc-0.json"));assertEquals("CC-BY-NC-SA-3.0",o.get("license").getAsString());assertEquals("https://example.com/source",o.getAsJsonArray("entries").get(0).getAsJsonObject().get("sourceUrl").getAsString());
        var forged=new TranslationEditorStore.Entry(e.file(),"pointer:/entries/0/sourceUrl",-1,e.id(),e.source(),"https://example.com/source");
        assertThrows(java.io.IOException.class,()->TranslationEditorStore.save(root,forged,"伪造来源"));
    }
    @Test void bundledEditorIncludesAllDictionaryFamilies() throws Exception {
        var entries=TranslationEditorStore.entries(Path.of("src/main/resources/defaults"));
        assertTrue(entries.size()>60000);
        for(String family:new String[]{"community-skyzh.json","community-wiki/","gui-menus.json","tooltip-paragraphs.json","system-messages.json","items/","templates.json","runes.json","dyes.json"})assertTrue(entries.stream().anyMatch(e->e.file().startsWith(family)),family);
    }
    @Test void editingNumericCommunityTemplateAlsoUpdatesItsExactExample() throws Exception {
        write("community-skyzh.json","""
            {"schemaVersion":1,"entries":[{"scope":"npc","npc":"Jerry","source":"Bring 10 apples.","translation":"带来 10 个苹果。","numericSource":"Bring {value} apples.","numericTranslation":"带来 {value} 个苹果。","styleMappings":{}}]}
            """);
        var entry=TranslationEditorStore.entries(root).getFirst();assertTrue(entry.source().contains("{value}"));
        TranslationEditorStore.save(root,entry,"请交给我 {value} 个苹果。");
        var index=CommunityTranslationEngine.load(root.resolve("translations/community-skyzh.json"));
        assertEquals("请交给我 10 个苹果。",index.get("npc","Jerry","Bring 10 apples.").translation());
        assertEquals("请交给我 20 个苹果。",index.candidates("npc:Jerry","Bring 20 apples.").getFirst().template().apply("Bring 20 apples.").text());
    }
}
