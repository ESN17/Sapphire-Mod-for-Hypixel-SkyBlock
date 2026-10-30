package com.sorakoi.sbtranslate;
import com.sorakoi.sbtranslate.collection.*;
import com.sorakoi.sbtranslate.translation.TranslationManager;
import com.sorakoi.sbtranslate.environment.SkyBlockContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class CollectorTest {
    @TempDir Path root;
    UntranslatedCollector.Observation item(String id,List<String> lines){return new UntranslatedCollector.Observation("item","INVENTORY",id,id,"Test",lines,false);}
    @Test void disabledUnconfirmedUnsafeAndFullyTranslatedAreExcluded()throws Exception {
        try(var c=new UntranslatedCollector(root,100,32,1024*1024)) {
            c.observe(item("TEST",List.of("Damage: +100")),true,true);c.enabled(true);
            c.observe(item("TEST",List.of("Damage: +100")),false,true);
            c.observe(item("TEST",List.of("private message")),true,false);
            c.observe(item("TEST",List.of()),true,true);c.flush().get(3,TimeUnit.SECONDS);assertEquals(0,c.size());
        }
    }
    @Test void repeatAndDynamicValuesAreBoundedAndPersistAtExit()throws Exception {
        var c=new UntranslatedCollector(root,100,32,1024*1024);c.enabled(true);
        assertTimeout(java.time.Duration.ofSeconds(5),()->{for(int i=0;i<20000;i++)c.observe(item("TEST",List.of("Damage: +"+i)),true,true);});
        assertEquals(0,c.writes());c.close();assertEquals(1,c.size());assertEquals(1,c.writes());
        try(var files=Files.list(root)){assertEquals(1,files.filter(p->p.toString().endsWith(".json")).count());}
    }
    @Test void metadataAndUserTranslationSurviveRestart()throws Exception {
        try(var c=new UntranslatedCollector(root,100,32,1024*1024)){c.enabled(true);c.observe(item("TEST",List.of("Damage: +100")),true,true);}
        Path path;try(var files=Files.list(root)){path=files.filter(p->p.toString().endsWith(".json")).findFirst().orElseThrow();}
        var record=TranslationManager.readObject(path);record.getAsJsonObject("translation").addProperty("name","已经完成");Files.writeString(path,record.toString());
        try(var c=new UntranslatedCollector(root,100,32,1024*1024)){c.enabled(true);c.observe(item("TEST",List.of("Damage: +150")),true,true);}
        record=TranslationManager.readObject(path);assertEquals("已经完成",record.getAsJsonObject("translation").get("name").getAsString());
        assertEquals(2,record.get("occurrences").getAsLong());assertEquals(1,record.getAsJsonObject("candidates").size());
    }
    @Test void corruptFilesAreRetainedAndDoNotCrash()throws Exception {
        try(var c=new UntranslatedCollector(root,100,32,1024*1024)){c.enabled(true);c.observe(item("TEST",List.of("Damage: +100")),true,true);}
        Path path;try(var files=Files.list(root)){path=files.findFirst().orElseThrow();}Files.writeString(path,"{broken");
        try(var c=new UntranslatedCollector(root,100,32,1024*1024)){c.enabled(true);c.observe(item("TEST",List.of("Damage: +100")),true,true);c.flush().get(3,TimeUnit.SECONDS);assertFalse(c.error().isEmpty());}
        assertEquals("{broken",Files.readString(path));
    }
    @Test void noIdCannotCollideWithKnownItemAndRecordLimitIsEnforced()throws Exception {
        try(var c=new UntranslatedCollector(root,2,32,1024*1024)){
            c.enabled(true);c.observe(item(null,List.of("Damage: +100")),true,true);c.observe(item("TEST",List.of("Damage: +100")),true,true);
            c.observe(item("OTHER",List.of("Damage: +100")),true,true);c.flush().get(3,TimeUnit.SECONDS);assertEquals(2,c.size());assertEquals(1,c.dropped());
        }
    }
    @Test void policyRejectsArbitraryChatNamesUrlsAndPrivateMessages()throws Exception {
        Files.writeString(root.resolve("collection-rules.json"),"{\"chat\":[\"Quest completed!\"],\"scoreboard\":[\"Purse: {value}\"]}");
        var policy=new CollectionPolicy();policy.load(root);
        assertFalse(policy.allows("chat","From Player: private message"));assertFalse(policy.allows("chat","[NPC] Fake: secret"));
        assertFalse(policy.allows("chat","https://example.com"));assertTrue(policy.allows("chat","Quest completed!"));
        assertTrue(policy.allows("scoreboard","Purse: 1,250"));assertFalse(policy.allows("scoreboard","Purse: Player"));
        Files.writeString(root.resolve("collection-rules.json"),"{\"chat\":[\"Hello {name}\"]}");assertThrows(Exception.class,()->policy.load(root));
    }
    @Test void environmentNeedsIndependentSignalsAndExactHostBoundary() {
        List<String> lines=List.of("www.hypixel.net","Purse: 100");
        assertTrue(SkyBlockContext.evidence("mc.hypixel.net:25565","Hypixel BungeeCord","SKYBLOCK",lines));
        assertFalse(SkyBlockContext.evidence("hypixel.net.evil.test","Hypixel","SKYBLOCK",lines));
        assertFalse(SkyBlockContext.evidence("mc.hypixel.net","vanilla","SKYBLOCK",lines));
        assertFalse(SkyBlockContext.evidence("mc.hypixel.net","Hypixel","BED WARS",lines));
        assertFalse(SkyBlockContext.evidence("mc.hypixel.net","Hypixel","SKYBLOCK",List.of("www.hypixel.net")));
    }
    @Test void diskBudgetStopsWritesAndDisablingStopsNewObservations()throws Exception {
        try(var c=new UntranslatedCollector(root,10,32,10)) {
            c.enabled(true);c.observe(item("TEST",List.of("Damage: +100")),true,true);c.flush().get(3,TimeUnit.SECONDS);
            assertEquals(0,c.writes());assertTrue(c.error().contains("disk limit"));
            c.enabled(false);c.observe(item("OTHER",List.of("Damage: +100")),true,true);c.flush().get(3,TimeUnit.SECONDS);assertEquals(1,c.size());
        }
    }
    @Test void damagedFilesStillCountTowardsFileBudget()throws Exception {
        Files.writeString(root.resolve("0".repeat(64)+".json"),"{broken");
        try(var c=new UntranslatedCollector(root,1,32,1024*1024)) {
            c.enabled(true);c.observe(item("TEST",List.of("Damage: +100")),true,true);c.flush().get(3,TimeUnit.SECONDS);
            assertEquals(0,c.writes());assertTrue(c.error().contains("file limit"));
        }
    }
}
