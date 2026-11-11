package com.sorakoi.sbtranslate;

import com.google.gson.*;
import com.sorakoi.sbtranslate.sync.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class DictionaryUpdateTest {
    @TempDir Path root;
    private static byte[] utf(String text){return text.getBytes(StandardCharsets.UTF_8);}
    private static Map<String,byte[]> payload() {
        var data=new LinkedHashMap<String,byte[]>();
        for(String name:DictionaryArchive.REQUIRED)data.put("translations/"+name+".json",utf("{}"));
        for(String name:List.of("gui-menus","entity-menus"))data.put("translations/"+name+".json",utf("{\"schemaVersion\":1,\"menus\":[]}"));
        for(String name:List.of("community-sapphire","community-wiki"))data.put("translations/"+name+".json",utf("{\"schemaVersion\":1,\"entries\":[]}"));
        for(String name:List.of("system-messages","death-messages","creature-messages"))data.put("translations/"+name+".json",utf("{\"rules\":[]}"));
        data.put("translations/items/TEST.json",utf("{\"id\":\"TEST\",\"name\":\"测试物品\",\"_original_name\":\"Test item\"}"));
        return data;
    }
    private static byte[] archive(String version,Map<String,byte[]> data) throws IOException {
        var manifest=new JsonObject();manifest.addProperty("schemaVersion",1);manifest.addProperty("kind","full");manifest.addProperty("version",version);
        var hashes=new JsonObject();data.forEach((name,raw)->hashes.addProperty(name,DictionaryArchive.sha256(raw)));manifest.add("files",hashes);
        var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(DictionaryArchive.MANIFEST));zip.write(utf(manifest.toString()));zip.closeEntry();
            for(var row:data.entrySet()){zip.putNextEntry(new ZipEntry(row.getKey()));zip.write(row.getValue());zip.closeEntry();}
        }return bytes.toByteArray();
    }
    private static class Fixture implements DictionaryTransport {
        String version="20261030_1";byte[] zip;int downloads;boolean offline;
        Fixture() throws IOException {zip=archive(version,payload());}
        public String index(URI uri) throws IOException {if(offline)throw new IOException("offline");return "[\""+version+".zip\"]";}
        public void download(URI uri,Path destination) throws IOException {downloads++;Files.write(destination,zip);}
    }
    private Path oldDictionary() throws IOException {
        Files.createDirectories(root.resolve("translations"));Files.writeString(root.resolve("translations/old.json"),"{\"Old\":\"原译文\"}");
        Files.writeString(root.resolve("config.json"),"local settings remain unchanged");return root.resolve("translations/old.json");
    }
    @Test void numericVersionsDatesAndSameDirectoryIndex() throws Exception {
        var uri=URI.create("http://localhost/translate");
        assertEquals("20261030_10",DictionaryIndex.latest("[\"20261030_2.zip\",\"20261030_10.zip\",\"20261301_1.zip\"]",uri).version().toString());
        assertEquals("20261030_2",DictionaryIndex.latest("<a href='20261030_2.zip'>ok</a><a href='http://elsewhere/20261031_1.zip'>no</a><a href='../20261031_2.zip'>no</a>",uri).version().toString());
        assertTrue(DictionaryVersion.parse("20261031_1").compareTo(DictionaryVersion.parse("20261030_999"))>0);
        for(String bad:List.of("20260230_1","20261030_0","../20261030_1","20261030_01"))assertThrows(RuntimeException.class,()->DictionaryVersion.parse(bad));
        assertThrows(IOException.class,()->DictionaryIndex.latest("<h1>Site not ready</h1>",uri));
        assertThrows(IOException.class,()->DictionaryIndex.endpoint("http://username:password@localhost/translate"));
    }
    @Test void replaceBackUpPreserveConfigurationAndDoNotDownloadTwiceOrDowngrade() throws Exception {
        oldDictionary();var transport=new Fixture();var updater=new DictionaryUpdater(root,transport);
        var result=updater.update("http://localhost/translate");assertEquals(DictionaryUpdater.Status.UPDATED,result.status(),result.detail());
        assertEquals("测试物品",result.translations().translatedName("Test item"));assertFalse(Files.exists(root.resolve("translations/old.json")));
        try(var files=Files.walk(root.resolve("dictionary-sync/backups"))){assertEquals(1,files.filter(p->p.getFileName().toString().equals("old.json")).count());}
        assertEquals("local settings remain unchanged",Files.readString(root.resolve("config.json")));
        assertEquals(DictionaryUpdater.Status.CURRENT,updater.update("http://localhost/translate").status());assertEquals(1,transport.downloads);
        transport.version="20261029_99";assertEquals(DictionaryUpdater.Status.CURRENT,updater.update("http://localhost/translate").status());assertEquals(1,transport.downloads);
        assertTrue(DictionaryUpdater.installed(root));
    }
    @Test void rejectCorruptionTraversalConfigInjectionInvalidJsonAndIncompleteLibrary() throws Exception {
        Path old=oldDictionary();var original=Files.readString(old);
        for(String attack:List.of("traversal","config","json","missing","legacyCorpus","version","truncated","case","template","depth")) {
            var data=payload();var fixture=new Fixture();
            switch(attack) {
                case "traversal" -> data.put("../outside.json",utf("{}"));
                case "config" -> data.put("config.json",utf("{}"));
                case "json" -> data.put("translations/chat.json",utf("{\"x\":\"a\",\"x\":\"b\"}"));
                case "missing" -> data.remove("translations/locations.json");
                case "legacyCorpus" -> data.put("translations/community-skyzh.json",data.remove("translations/community-sapphire.json"));
                case "case" -> data.put("translations/items/test.json",utf("{\"id\":\"second\"}"));
                case "template" -> data.put("translations/templates.json",utf("{\"You have {amount} coins\":\"有{wrong}硬币\"}"));
                case "depth" -> data.put("translations/chat.json",utf("{\"x\":"+"[".repeat(70)+"0"+"]".repeat(70)+"}"));
            }
            fixture.zip=archive(attack.equals("version")?"20261030_2":"20261030_1",data);
            if(attack.equals("truncated"))fixture.zip=Arrays.copyOf(fixture.zip,fixture.zip.length/2);
            var result=new DictionaryUpdater(root,fixture).update("http://localhost/translate");
            assertEquals(DictionaryUpdater.Status.FAILED,result.status(),attack+": "+result.detail());assertEquals(original,Files.readString(old));assertEquals("",DictionaryUpdater.installedVersion(root));
        }
        assertFalse(Files.exists(root.resolve("outside.json")));
    }
    @Test void connectionFailureLeavesDictionaryUntouched() throws Exception {
        Path old=oldDictionary();var fixture=new Fixture();fixture.offline=true;
        var result=new DictionaryUpdater(root,fixture).update("http://localhost/translate");
        assertEquals(DictionaryUpdater.Status.UNAVAILABLE,result.status());assertTrue(Files.exists(old));assertEquals(0,fixture.downloads);
        assertEquals("暂时无法连接到译名库服务器，请检查防火墙设置或检查是否可以正常登录至译名库服务器！",result.message());
    }
    @Test void wholeArchiveAndPerFileHashesAreBothEnforced() throws Exception {
        Path old=oldDictionary();var fixture=new Fixture() {
            @Override public String index(URI uri){return "{\"files\":[{\"name\":\"20261030_1.zip\",\"sha256\":\""+"0".repeat(64)+"\"}]}";}
        };
        assertEquals(DictionaryUpdater.Status.FAILED,new DictionaryUpdater(root,fixture).update("http://localhost/translate").status());
        var changed=new ByteArrayOutputStream();var source=new Fixture();
        try(var input=new ZipInputStream(new ByteArrayInputStream(source.zip));var output=new ZipOutputStream(changed)) {
            for(ZipEntry entry;(entry=input.getNextEntry())!=null;) {
                output.putNextEntry(new ZipEntry(entry.getName()));byte[] raw=input.readAllBytes();
                output.write(entry.getName().equals("translations/chat.json")?utf("{\"changed\":\"不同内容\"}"):raw);output.closeEntry();
            }
        }
        source.zip=changed.toByteArray();var result=new DictionaryUpdater(root,source).update("http://localhost/translate");
        assertEquals(DictionaryUpdater.Status.FAILED,result.status());assertTrue(result.detail().contains("checksum"));assertTrue(Files.exists(old));
    }
    @Test void failedDownloadAfterSuccessfulInstallKeepsVersionAndTranslations() throws Exception {
        var fixture=new Fixture();var updater=new DictionaryUpdater(root,fixture);assertEquals(DictionaryUpdater.Status.UPDATED,updater.update("http://localhost/translate").status());
        var broken=new Fixture() {
            @Override public void download(URI uri,Path destination) throws IOException {Files.write(destination,new byte[]{1,2,3});throw new IOException("interrupted transfer");}
        };broken.version="20261030_2";
        assertEquals(DictionaryUpdater.Status.UNAVAILABLE,new DictionaryUpdater(root,broken).update("http://localhost/translate").status());
        assertEquals("20261030_1",DictionaryUpdater.installedVersion(root));assertTrue(Files.readString(root.resolve("translations/items/TEST.json")).contains("测试物品"));
        try(var files=Files.list(root.resolve("dictionary-sync"))){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith("work-")));}
    }
    @Test void recoveryBeforeOldDirectoryMoveAndOnFirstInstallIsIdempotent() throws Exception {
        Path old=oldDictionary();Path sync=root.resolve("dictionary-sync");Files.createDirectories(sync);String id=UUID.randomUUID().toString();
        Files.writeString(sync.resolve("transaction.json"),"{\"id\":\""+id+"\",\"previous\":\"\",\"next\":\"20261030_1\",\"hadOld\":true}");
        var updater=new DictionaryUpdater(root,new Fixture());updater.recover();assertTrue(Files.exists(old));assertEquals("",DictionaryUpdater.installedVersion(root));
        Files.writeString(sync.resolve("transaction.json"),"{\"id\":\""+id+"\",\"previous\":\"\",\"next\":\"20261030_1\",\"hadOld\":false}");
        updater.recover();updater.recover();assertFalse(Files.exists(root.resolve("translations")));assertEquals("",DictionaryUpdater.installedVersion(root));
    }
    @Test void interruptedReplacementRollsBackBeforeNextLoad() throws Exception {
        oldDictionary();Path sync=root.resolve("dictionary-sync");String id=UUID.randomUUID().toString();Path backup=sync.resolve("backups/"+id);
        Files.createDirectories(backup);Files.move(root.resolve("translations"),backup.resolve("translations"));Files.createDirectories(root.resolve("translations"));Files.writeString(root.resolve("translations/new.json"),"{}");
        Files.writeString(sync.resolve("state.json"),"{\"version\":\"20261030_2\"}");
        Files.writeString(sync.resolve("transaction.json"),"{\"id\":\""+id+"\",\"previous\":\"20261030_1\",\"next\":\"20261030_2\",\"hadOld\":true}");
        var updater=new DictionaryUpdater(root,new Fixture());updater.recover();updater.recover();
        assertTrue(Files.exists(root.resolve("translations/old.json")));assertFalse(Files.exists(root.resolve("translations/new.json")));assertEquals("20261030_1",DictionaryUpdater.installedVersion(root));
    }
    @Test void noticeWaitsForResultAndOnlyShowsOnHypixelOncePerConnection() {
        var notice=new DictionaryUpdateNotice();var connection=new Object();assertNull(notice.poll(connection,"mc.hypixel.net"));
        notice.result(new DictionaryUpdater.Result(DictionaryUpdater.Status.CURRENT,"20261030_1",null,""));
        assertNull(notice.poll(connection,"hypixel.net.evil.example"));assertNull(notice.poll(connection,"otherhypixel.net"));
        assertEquals("译名库已更新至最新版！",notice.poll(connection,"MC.HYPIXEL.NET:25565"));assertNull(notice.poll(connection,"mc.hypixel.net"));
        notice.disconnect();assertNotNull(notice.poll(new Object(),"hypixel.net"));
    }
    @Test void progressOccursOnlyBeforeANewDownload() throws Exception {
        var fixture=new Fixture();var updater=new DictionaryUpdater(root,fixture);var events=new ArrayList<String>();
        var result=updater.update("http://localhost/translate",version->{assertEquals(0,fixture.downloads);events.add(version);});
        assertEquals(DictionaryUpdater.Status.UPDATED,result.status());assertEquals(List.of("20261030_1"),events);
        updater.update("http://localhost/translate",events::add);assertEquals(1,events.size());
        fixture.offline=true;updater.update("http://localhost/translate",events::add);assertEquals(1,events.size());
    }
    @Test void progressAndCompletionEachShowOnceAndDoNotReplayStaleDownload() {
        var notice=new DictionaryUpdateNotice();var connection=new Object();
        notice.checking();assertNull(notice.poll(connection,"mc.hypixel.net"));
        notice.downloading("20261104_2");assertNull(notice.poll(connection,"example.net"));
        assertEquals("已检测到新版译名库（20261104_2），正在下载中…",notice.poll(connection,"mc.hypixel.net"));
        assertNull(notice.poll(connection,"mc.hypixel.net"));
        notice.result(new DictionaryUpdater.Result(DictionaryUpdater.Status.UPDATED,"20261104_2",null,""));
        assertEquals("译名库已更新至最新版！",notice.poll(connection,"mc.hypixel.net"));
        assertNull(notice.poll(connection,"mc.hypixel.net"));
        notice.disconnect();assertEquals("译名库已更新至最新版！",notice.poll(new Object(),"mc.hypixel.net"));
        notice.checking();notice.downloading("20261104_3");
        notice.result(new DictionaryUpdater.Result(DictionaryUpdater.Status.FAILED,"20261104_2",null,""));
        assertEquals("译名库更新失败，已保留原有译名库。",notice.poll(connection,"mc.hypixel.net"));
    }
    @Test void httpDownloadsChecksHashAndHandles404AndRedirect() throws Exception {
        byte[] zip=archive("20261030_1",payload());var downloads=new AtomicInteger();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/translate",exchange->{
            String path=exchange.getRequestURI().getPath();byte[] response;
            if(path.equals("/translate")){exchange.getResponseHeaders().set("Location","/translate/");exchange.sendResponseHeaders(301,-1);exchange.close();return;}
            if(path.equals("/translate/"))response=utf("{\"files\":[{\"name\":\"20261030_1.zip\",\"sha256\":\""+DictionaryArchive.sha256(zip)+"\",\"size\":"+zip.length+"}]}");
            else if(path.equals("/translate/20261030_1.zip")){downloads.incrementAndGet();response=zip;}
            else {exchange.sendResponseHeaders(404,-1);exchange.close();return;}
            exchange.sendResponseHeaders(200,response.length);try(var output=exchange.getResponseBody()){output.write(response);}
        });server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort()+"/translate";var updater=new DictionaryUpdater(root,new DictionaryTransport.Http(1000,1000));
            var first=updater.update(base);assertEquals(DictionaryUpdater.Status.UPDATED,first.status(),first.detail());assertEquals(DictionaryUpdater.Status.CURRENT,updater.update(base).status());assertEquals(1,downloads.get());
            assertEquals(DictionaryUpdater.Status.UNAVAILABLE,updater.update(base+"/missing").status());
        }finally{server.stop(0);}
    }
    @Test void fullReleaseOfflineHttpInstallation() throws Exception {
        String archive=System.getenv("SAPPHIRE_DICTIONARY_ZIP");Assumptions.assumeTrue(archive!=null,"Set SAPPHIRE_DICTIONARY_ZIP to verify a complete release");
        Path zip=Path.of(archive);String filename=zip.getFileName().toString();byte[] bytes=Files.readAllBytes(zip);oldDictionary();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var downloads=new AtomicInteger();
        server.createContext("/translate",exchange->{
            byte[] response;
            if(exchange.getRequestURI().getPath().endsWith(".zip")){downloads.incrementAndGet();response=bytes;}
            else response=utf("{\"files\":[{\"name\":\""+filename+"\",\"sha256\":\""+DictionaryArchive.sha256(bytes)+"\",\"size\":"+bytes.length+"}]}");
            exchange.sendResponseHeaders(200,response.length);try(var output=exchange.getResponseBody()){output.write(response);}
        });server.start();long start=System.nanoTime();
        try {
            String endpoint="http://127.0.0.1:"+server.getAddress().getPort()+"/translate";var updater=new DictionaryUpdater(root,new DictionaryTransport.Http());
            var first=updater.update(endpoint);assertEquals(DictionaryUpdater.Status.UPDATED,first.status(),first.detail());assertTrue(first.translations().itemCount()>8000);
            int count=0;try(var input=new ZipFile(zip.toFile())) {
                var entries=input.entries();while(entries.hasMoreElements()) {
                    var entry=entries.nextElement();if(!entry.getName().startsWith("translations/"))continue;
                    try(var raw=input.getInputStream(entry)){assertEquals(DictionaryArchive.sha256(raw.readAllBytes()),DictionaryArchive.sha256(root.resolve(entry.getName())),entry.getName());}count++;
                }
            }
            assertEquals(DictionaryUpdater.Status.CURRENT,updater.update(endpoint).status());assertEquals(1,downloads.get());
            assertEquals("村庄",first.translations().scope("locations").get("Village"));
            Path report=Path.of("build/dictionary-offline-verification.json");Files.createDirectories(report.getParent());
            Files.writeString(report,"{\"version\":\""+first.version()+"\",\"verifiedFiles\":"+count+",\"itemCount\":"+first.translations().itemCount()+",\"downloads\":"+downloads+",\"milliseconds\":"+(System.nanoTime()-start)/1_000_000+",\"sha256\":\""+DictionaryArchive.sha256(bytes)+"\"}\n");
        }finally{server.stop(0);}
    }
}
