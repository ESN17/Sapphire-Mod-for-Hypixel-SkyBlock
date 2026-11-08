package com.sorakoi.sbtranslate;

import com.google.gson.*;
import com.sorakoi.sbtranslate.llm.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HelpEvidenceTest {
    @Test void normalAnswersStayShortWithoutCuttingFactsOrShowingCitationNumbers(){
        assertTrue(HelpEvidence.needsShortening("字".repeat(351),"怎么提高掉落？"));
        assertFalse(HelpEvidence.needsShortening("字".repeat(351),"请详细解释掉落公式"));
        assertEquals("先用狂欢之瓶（Fiesta Flask）。\n再提升属性。",HelpEvidence.visibleAnswer("先用狂欢之瓶（Fiesta Flask） [W3] 。\n再提升属性。[F4]"));
    }
    @Test void plannerQueriesAreBoundedAndOldPlansStillSearchTheWeb(){
        var p=HelpEvidence.plan("[\"Voidgloom Seraph\",\"Final Destination Armor\"]");
        assertEquals(2,p.wiki().size());assertFalse(p.forum().isEmpty());assertFalse(p.web().isEmpty());
        p=HelpEvidence.plan("```json\n{\"wiki\":[\"A\",\"A\",\"B\",\"C\",\"D\"],\"forum\":[null,3,\"E\",\"F\"],\"web\":[\"G\",\"H\",\"I\"]}\n```");
        assertEquals(List.of("A","B","C"),p.wiki());assertEquals(List.of("E"),p.forum());assertEquals(List.of("G","H"),p.web());
    }
    @Test void webIsNotRestrictedToWikiOrForumsButUnsafeUrlsAreRejected(){
        assertTrue(HelpEvidence.approved("web","https://github.com/SkyHanniStudios/SkyHanni"));
        assertTrue(HelpEvidence.approved("web","https://www.bilibili.com/video/example"));
        for(String url:List.of("http://example.com/a","https://localhost/a","https://127.0.0.1/a","https://foo.local/a","https://a@example.com/a","https://example.com:8443/a"))assertFalse(HelpEvidence.approved("web",url));
        assertFalse(HelpEvidence.approved("wiki","https://hypixelskyblock.minecraft.wiki.evil/w/Foo"));
        assertFalse(HelpEvidence.approved("forum","https://hypixel.net/login/"));
    }
    private static JsonObject response(String title,String url,String text){
        var o=new JsonObject();var d=new JsonObject();d.addProperty("title",title);d.addProperty("url",url);d.addProperty("text",text);
        var a=new JsonArray();a.add(d);o.add("documents",a);return o;
    }
    @Test void oneFailedSourceDoesNotDiscardOtherEvidenceAndDuplicatesAreRemoved(){
        var c=new HelpEvidence.Collection();
        c.search((s,q,f)->{throw new java.io.IOException();},"wiki",List.of("Gear"),"");
        c.search((s,q,f)->response("Guide","https://hypixel.net/threads/guide.123/","Use a tested strategy."),"forum",List.of("Guide","Guide again"),"");
        assertEquals(1,c.documents().size());assertTrue(c.notes().toString().contains("wiki: unavailable"));
        assertTrue(c.appendix("[F1]").contains("论坛经验"));assertTrue(c.appendix("[F1]").contains("部分网络检索"));
        c.documents().get(0).getAsJsonObject().addProperty("text","changed");assertFalse(c.documents().toString().contains("changed"));
    }
    @Test void snippetDatesAndCacheStatusReachTheAnswerAndBodiesAreBounded(){
        var c=new HelpEvidence.Collection();
        c.search((s,q,f)->{var r=response("Video guide","https://www.bilibili.com/video/example","X".repeat(20000));
            r.addProperty("status","stale");var d=r.getAsJsonArray("documents").get(0).getAsJsonObject();
            d.addProperty("coverage","search-snippet");d.addProperty("published","2023-01-14");return r;},"web",List.of("Guide"),"");
        assertEquals(8000,c.documents().get(0).getAsJsonObject().get("text").getAsString().length());
        String appendix=c.appendix("建议 [S1]");assertTrue(appendix.contains("仅搜索摘要"));assertTrue(appendix.contains("2023-01-14"));assertTrue(appendix.contains("缓存"));
    }
    @Test void onlyUnmodifiedLegacyPromptIsUpgraded()throws Exception{
        String current=LlmConfig.defaults().helpPrompt;
        String legacy=Files.readString(Path.of("src/test/resources/help-prompt-legacy.txt"));
        assertEquals(current,LlmConfig.upgradeHelpPrompt(legacy,current));
        assertEquals(current,LlmConfig.upgradeHelpPrompt(legacy.replace("\n","\r\n"),current));
        assertEquals(legacy+"\n我的自定义设置",LlmConfig.upgradeHelpPrompt(legacy+"\n我的自定义设置",current));
        assertTrue(current.contains("论坛"));assertTrue(current.contains("优先级"));assertTrue(current.contains("search-snippet"));
    }
}
