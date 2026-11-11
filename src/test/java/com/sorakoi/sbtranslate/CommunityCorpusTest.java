package com.sorakoi.sbtranslate;

import com.google.gson.*;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CommunityCorpusTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;}

    @Test void npcPrefixNamesAndClickEventsArePreserved() {
        var raw=Component.literal("§e[NPC] Bartender: §fAh! You look like someone strong.")
            .withStyle(s->s.withClickEvent(new ClickEvent.RunCommand("/unchanged")));
        var copy=raw.copy();var out=CommunityTranslationEngine.translate(raw,"chat",manager);
        assertEquals("[NPC] Bartender: 哟！看你这身板，力气应该不小。",out.getString());assertEquals(copy,raw);
        StyledLoreTest.color(out,"Bartender",ChatFormatting.YELLOW);StyledLoreTest.color(out,"哟！",ChatFormatting.WHITE);
        assertEquals(raw.getStyle().getClickEvent(),StyledLoreTest.styleAt(out,"哟！").getClickEvent());
        for(String text:List.of("Player: Ah! You look like someone strong.","[Party] [NPC] Bartender: Ah! You look like someone strong.",
                "[NPC] Unknown: Ah! You look like someone strong.","Ah! You look like someone strong.")) {
            var unrelated=Component.literal(text);assertSame(unrelated,CommunityTranslationEngine.translate(unrelated,"chat",manager));
        }
    }

    @Test void independentlyTranslatedCorpusPreservesCapturedStylesWithoutSourceMutation() throws Exception {
        var data=TranslationManager.readObject(Path.of("src/main/resources/defaults/translations/community-sapphire.json"));
        assertEquals("Sapphire",data.get("translator").getAsString());
        assertFalse(Files.exists(Path.of("src/main/resources/defaults/translations/community-skyzh.json")));
        var failures=new JsonArray();int checked=0;
        for(var value:data.getAsJsonArray("entries")) {
            var row=value.getAsJsonObject();String scope=row.get("scope").getAsString();
            String original=row.get("source").getAsString(),target=row.get("translation").getAsString();
            String raw=row.has("sampleRaw")?row.get("sampleRaw").getAsString():original;
            if(row.has("templateExamples"))for(var example:row.getAsJsonObject("templateExamples").entrySet()) {
                original=original.replace(example.getKey(),example.getValue().getAsString());
                target=target.replace(example.getKey(),example.getValue().getAsString());
                raw=raw.replace(example.getKey(),example.getValue().getAsString());
            }
            assertEquals(original,TranslationManager.plain(raw).strip(),"Captured source must match "+row.get("sourceId"));
            if(scope.equals("npc")) {
                String prefix="[NPC] "+row.get("npc").getAsString()+": ";
                raw="§e"+prefix+"§f"+raw;target=prefix+target;scope="chat";
            }
            else if(scope.equals("system"))scope="chat";
            var source=Component.literal(raw);var before=source.copy();
            var result=CommunityTranslationEngine.translate(source,scope,manager);checked++;
            assertEquals(before,source);
            boolean translated=TranslationManager.plain(result.getString()).strip().equals(target);
            if(scope.equals("tooltip") && row.has("rawLines")) {
                var wrapped=new ArrayList<Component>();
                for(var line:row.getAsJsonArray("rawLines")) {
                    String text=line.getAsString();
                    if(row.has("templateExamples"))for(var example:row.getAsJsonObject("templateExamples").entrySet())text=text.replace(example.getKey(),example.getValue().getAsString());
                    wrapped.add(Component.literal(text));
                }
                var originals=wrapped.stream().map(Component::copy).toList();
                var rendered=LoreTranslationEngine.translate(wrapped,manager,null,(s,o)->List.of(s),false,false,false,List.of(),s->s,
                    s->CommunityTranslationEngine.translate(s,"tooltip",manager),s->manager.community().candidates("tooltip",TranslationManager.plain(s.getString())));
                assertEquals(originals,wrapped);
                translated &= rendered.lines().stream().map(Component::getString).reduce((a,b)->a+" "+b).orElse("").strip().equals(target);
            }
            if(!translated) {
                var failure=new JsonObject();failure.addProperty("scope",row.get("scope").getAsString());
                failure.addProperty("npc",row.get("npc").getAsString());failure.addProperty("source",row.get("source").getAsString());
                failure.addProperty("sourceFile",row.get("sourceFile").getAsString());failure.addProperty("sourceId",row.get("sourceId").getAsString());
                failures.add(failure);
            }
        }
        var path=Path.of("build/reports/sapphire-corpus-style-failures.json");Files.createDirectories(path.getParent());
        Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(failures));
        assertTrue(checked>=8806);assertEquals(0,failures.size(),"Corpus rendering failures; see "+path);
    }

    @Test void existingUiWordsAndScopeBoundariesWin() {
        assertEquals("返回",GuiTranslationEngine.button(Component.literal("Go Back"),GuiTranslationEngine.menu("Bazaar",manager)).getString());
        var source=Component.literal("Click to store the set you're wearing!");
        var result=CommunityTranslationEngine.translate(source,"tooltip",manager);
        assertEquals("点击收起身上这套装备！",result.getString());
        assertSame(source,CommunityTranslationEngine.translate(source,"chat",manager));
        var unknown=Component.literal("unrecognized menu entry");
        assertSame(unknown,CommunityTranslationEngine.translate(unknown,"tooltip",manager));
        assertEquals("比特商店 - 染料",GuiTranslationEngine.title(Component.literal("Bits Shop - Dyes"),manager).getString());
        assertEquals("末影人杀手",CommunityTranslationEngine.translate(Component.literal("Enderman Slayer"),"tooltip",manager).getString());
        assertEquals("提纯提示",CommunityTranslationEngine.translate(Component.literal("Pristine Chat"),"tooltip",manager).getString());
    }
}
