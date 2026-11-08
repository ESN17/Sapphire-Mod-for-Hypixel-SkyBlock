package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.llm.*;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class LlmTranslationTest {
    @TempDir Path temp;
    static LlmConfig config() throws Exception {var c=LlmConfig.defaults();c.enabled=true;c.keys.put(c.provider,"test-only-secret");return c;}
    static JsonObject body(java.net.http.HttpRequest request) throws Exception {
        var result=new CompletableFuture<String>();var bytes=new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription s){s.request(Long.MAX_VALUE);}
            public void onNext(ByteBuffer b){byte[] chunk=new byte[b.remaining()];b.get(chunk);bytes.writeBytes(chunk);}
            public void onError(Throwable t){result.completeExceptionally(t);}
            public void onComplete(){result.complete(bytes.toString(java.nio.charset.StandardCharsets.UTF_8));}
        });
        return JsonParser.parseString(result.get(2,TimeUnit.SECONDS)).getAsJsonObject();
    }
    @Test void defaultsAreOptInAndAllFiveProfilesPersistWithoutLeakingKeys() throws Exception {
        var c=LlmConfig.load(temp);assertFalse(c.enabled);assertTrue(c.npc);assertFalse(c.players);assertFalse(c.system);
        assertEquals(5,c.profiles.size());assertFalse(Files.exists(temp.resolve("api-keys.local.json")));
        for(var p:LlmConfig.Provider.values()){c.keys.put(p,"test-only-"+p);c.profiles.get(p).model="custom-"+p;}
        c.provider=LlmConfig.Provider.KIMI;c.prompt="自定义提示词\n第二行\n";c.save(temp);
        assertFalse(Files.readString(temp.resolve("api.json")).contains("test-only"));
        assertFalse(Files.readString(temp.resolve("translation-prompt.txt")).contains("test-only"));
        var reloaded=LlmConfig.load(temp);assertEquals(c.provider,reloaded.provider);assertEquals(c.prompt,reloaded.prompt);
        for(var p:LlmConfig.Provider.values()){assertEquals(c.keys.get(p),reloaded.keys.get(p));assertEquals(c.profiles.get(p).model,reloaded.profiles.get(p).model);}
        var copy=c.copy();copy.keys.put(c.provider,"changed");copy.profile().model="changed";assertNotEquals(copy.key(),c.key());assertNotEquals(copy.profile().model,c.profile().model);
    }
    @Test void invalidConfigDoesNotExposeParsingContent() throws Exception {
        Files.writeString(temp.resolve("api.json"),"private-secret-invalid");
        var e=assertThrows(IOException.class,()->LlmConfig.load(temp));assertFalse(e.toString().contains("private-secret"));
        for(String url:List.of("http://example.com/v1/messages","https://user:secret@example.com/v1/messages","https://example.com/v1?key=secret","file:///secret","https://example.com"))assertThrows(IllegalArgumentException.class,()->LlmConfig.endpoint(url));
        assertEquals("localhost",LlmConfig.endpoint("http://localhost:8080/v1/messages").getHost());
    }
    @Test void fiveProvidersUseCorrectHeadersAndPayloads() throws Exception {
        var c=config();
        for(var p:LlmConfig.Provider.values()) {
            c.provider=p;c.keys.put(p,"test-only-secret");var request=LlmApi.request(c,"Hello ⟦S0⟧");var json=body(request);
            assertEquals("POST",request.method());assertEquals(c.profile().model,json.get("model").getAsString());assertFalse(json.toString().contains(c.key()));
            assertEquals(20,request.timeout().orElseThrow().toSeconds());assertFalse(json.get("stream").getAsBoolean());
            if(p==LlmConfig.Provider.OPENAI){assertEquals(c.prompt,json.get("instructions").getAsString());assertEquals("Hello ⟦S0⟧",json.get("input").getAsString());assertFalse(json.get("store").getAsBoolean());assertTrue(json.has("max_output_tokens"));}
            else {assertTrue(json.has("max_tokens"));assertEquals("Hello ⟦S0⟧",json.getAsJsonArray("messages").asList().getLast().getAsJsonObject().get("content").getAsString());}
            if(p==LlmConfig.Provider.CLAUDE){assertEquals("test-only-secret",request.headers().firstValue("x-api-key").orElseThrow());assertTrue(request.headers().firstValue("Authorization").isEmpty());assertEquals(c.prompt,json.get("system").getAsString());}
            else assertEquals("Bearer test-only-secret",request.headers().firstValue("Authorization").orElseThrow());
        }
    }
    @Test void parsesOnlyFinalTextAndRejectsIncompleteResponses() throws Exception {
        assertEquals("你好",LlmApi.response(LlmConfig.Provider.OPENAI,"{\"status\":\"completed\",\"output\":[{\"type\":\"reasoning\"},{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"你好\"}]}]}"));
        assertEquals("你好",LlmApi.response(LlmConfig.Provider.CLAUDE,"{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"thinking\",\"thinking\":\"hidden\"},{\"type\":\"text\",\"text\":\"你好\"}]}"));
        for(var p:List.of(LlmConfig.Provider.DEEPSEEK,LlmConfig.Provider.KIMI,LlmConfig.Provider.GLM))assertEquals("你好",LlmApi.response(p,"{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"你好\",\"reasoning_content\":\"hidden\"}}]}"));
        for(String json:List.of("{}","not-json","{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"half\"}}]}","{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"§ccommand\"}}]}"))assertThrows(IOException.class,()->LlmApi.response(LlmConfig.Provider.GLM,json));
    }
    @Test void scopeClassificationKeepsNpcAndPlayersSeparate() throws Exception {
        var c=config();var npc=LlmText.select("[NPC] Bartender: Hello there!",false,c);assertNotNull(npc);assertEquals("Hello there!",npc.body());assertEquals("[NPC] Bartender: ",npc.prefix());
        for(String message:List.of("[MVP+] User123: hello","Guild > [VIP] User123 [TAG]: hello","Party > User123: hello","From [VIP] User123: hello","User123: hello")) {
            assertNull(LlmText.select(message,false,c));c.players=true;assertEquals(LlmText.Kind.PLAYER,LlmText.select(message,false,c).kind());c.players=false;
        }
        assertNull(LlmText.select("Hello there!",true,c));assertNull(LlmText.select("The event begins!",false,c));
        c.system=true;assertEquals(LlmText.Kind.SYSTEM,LlmText.select("The event begins!",false,c).kind());
        assertNull(LlmText.select("你好！",false,c));assertNull(LlmText.select("[译] already translated",false,c));
        c.players=true;
        for(String message:List.of("<User123> hello","[VIP] User123: hello","Guild > User123: hello")) {
            var signed=LlmText.select(message,true,c);assertEquals("hello",signed.body());assertFalse(signed.prefix().isBlank());
        }
        assertEquals("hello",LlmText.select("hello",true,c).body());
    }
    @Test void protectsNumbersNamesLinksCommandsAndRejectsMissingTokens() {
        var text=LlmText.protect("User123 paid 1,024.5 coins. Use /bz with Sharpness VI at https://example.com/a",List.of("User123"));
        assertNotNull(text);assertFalse(text.text().contains("User123"));assertFalse(text.text().contains("1,024.5"));assertFalse(text.text().contains("/bz"));
        assertEquals("User123 paid 1,024.5 coins. Use /bz with Sharpness VI at https://example.com/a",text.restore(text.text()));
        assertNull(text.restore("没有占位符"));assertNull(text.restore(text.text()+"⟦S0⟧"));assertNull(text.restore(text.text()+"⟦S99⟧"));assertNull(LlmText.protect("bad ⟦S0⟧",List.of()));
    }
    @Test void requestsRunOffThreadAndCacheWithoutExtraCalls() throws Exception {
        var calls=new AtomicInteger();var worker=new AtomicReference<Thread>();var main=Thread.currentThread();
        try(var service=new LlmTranslationService((c,s)->{calls.incrementAndGet();worker.set(Thread.currentThread());return "你好";},System::currentTimeMillis)) {
            var c=config();service.configure(c);var first=new CompletableFuture<LlmTranslationService.Result>();assertTrue(service.submit("hello",first::complete));assertEquals("你好",first.get(2,TimeUnit.SECONDS).text());assertNotSame(main,worker.get());
            var second=new CompletableFuture<LlmTranslationService.Result>();assertTrue(service.submit("hello",second::complete));assertEquals("你好",second.get(2,TimeUnit.SECONDS).text());assertEquals(1,calls.get());
            c.enabled=false;service.configure(c);assertFalse(service.current(first.get().generation()));assertFalse(service.submit("hello",r->fail()));
        }
    }
    @Test void disconnectInvalidatesInFlightAndQueuedResponses() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var responses=new AtomicInteger();
        try(var service=new LlmTranslationService((c,s)->{entered.countDown();release.await(2,TimeUnit.SECONDS);return "译文";},System::currentTimeMillis)) {
            service.configure(config());service.submit("first",r->responses.incrementAndGet());assertTrue(entered.await(2,TimeUnit.SECONDS));service.submit("queued",r->responses.incrementAndGet());service.reset();release.countDown();
            var fresh=new CompletableFuture<LlmTranslationService.Result>();service.submit("new session",fresh::complete);assertEquals("译文",fresh.get(2,TimeUnit.SECONDS).text());assertEquals(0,responses.get());
        }finally{release.countDown();}
    }
    @Test void queueIsBoundedAndDuplicateRequestsAreNotChargedTwice() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var service=new LlmTranslationService((c,s)->{entered.countDown();release.await(2,TimeUnit.SECONDS);return "译文";},System::currentTimeMillis)) {
            service.configure(config());assertTrue(service.submit("first",r->{}));assertTrue(entered.await(2,TimeUnit.SECONDS));
            assertTrue(service.submit("first",r->{}));
            for(int i=0;i<LlmTranslationService.MAX_PENDING-1;i++)assertTrue(service.submit("queued"+i,r->{}));
            assertFalse(service.submit("overflow",r->fail()));assertTrue(service.status().contains("队列已满"));
            service.reset();release.countDown();
        }finally{release.countDown();}
    }
    @Test void repeatedVisibleMessagesShareOneRequestAndBothReceiveTranslation()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new AtomicInteger();
        var first=new CompletableFuture<LlmTranslationService.Result>();var second=new CompletableFuture<LlmTranslationService.Result>();
        try(var service=new LlmTranslationService((c,s)->{calls.incrementAndGet();entered.countDown();release.await(2,TimeUnit.SECONDS);return "译文";},System::currentTimeMillis)){
            service.configure(config());service.submit("same notification",first::complete);assertTrue(entered.await(2,TimeUnit.SECONDS));
            assertTrue(service.submit("same notification",second::complete));release.countDown();
            assertEquals("译文",first.get(2,TimeUnit.SECONDS).text());assertEquals("译文",second.get(2,TimeUnit.SECONDS).text());assertEquals(1,calls.get());
        }finally{release.countDown();}
    }
    @Test void unlimitedRequestsAndInvalidCredentialsAreReported() throws Exception {
        var time=new AtomicLong(100000);var calls=new AtomicInteger();var c=config();c.requestsPerMinute=1;
        try(var service=new LlmTranslationService((cfg,s)->{calls.incrementAndGet();return "译文";},time::get)) {
            service.configure(c);var first=new CompletableFuture<LlmTranslationService.Result>();service.submit("one",first::complete);first.get(2,TimeUnit.SECONDS);
            for(int i=0;i<70;i++) {
                var next=new CompletableFuture<LlmTranslationService.Result>();service.submit("next"+i,next::complete);next.get(2,TimeUnit.SECONDS);
            }
            assertEquals(71,calls.get());
        }
        try(var service=new LlmTranslationService((cfg,s)->{throw new LlmApi.ApiFailure(401);},time::get)) {
            service.configure(config());var failure=new CompletableFuture<String>();
            service.submit("bad",s->true,r->fail(),failure::complete);
            assertTrue(failure.get(2,TimeUnit.SECONDS).contains("认证失败"));
            assertFalse(service.submit("next",r->fail()));assertTrue(service.status().contains("认证失败"));
            assertFalse(LlmTranslationService.error(new IOException("private-secret" )).contains("private-secret"));
        }
    }
    static void awaitStatus(LlmTranslationService service,String expected) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(!service.status().contains(expected) && System.nanoTime()<end)Thread.sleep(5);assertTrue(service.status().contains(expected),service.status());
    }
    @Test void actualHttpAdaptersWorkAgainstLocalMockOnly() throws Exception {
        var server=com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var auth=new AtomicReference<String>();var received=new AtomicReference<JsonObject>();
        server.createContext("/test",exchange->{
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));received.set(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
            byte[] response="{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"你好\"}}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,response.length);try(var out=exchange.getResponseBody()){out.write(response);}
        });server.start();
        try{var c=config();c.provider=LlmConfig.Provider.GLM;c.keys.put(c.provider,"mock-key");c.profile().endpoint="http://127.0.0.1:"+server.getAddress().getPort()+"/test";assertEquals("你好",LlmApi.translate(c,"hello"));assertEquals("Bearer mock-key",auth.get());assertEquals("hello",received.get().getAsJsonArray("messages").get(1).getAsJsonObject().get("content").getAsString());}
        finally{server.stop(0);}
    }
    @Test void transportRejectsRedirectsOversizeAndHttpErrorsWithoutEchoingBody() throws Exception {
        var server=com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var redirected=new AtomicInteger();
        server.createContext("/redirect",e->{e.getResponseHeaders().add("Location","/target");e.sendResponseHeaders(302,-1);e.close();});
        server.createContext("/target",e->{redirected.incrementAndGet();e.sendResponseHeaders(200,-1);e.close();});
        server.createContext("/auth",e->{byte[] bytes="private-server-error-secret".getBytes();e.sendResponseHeaders(401,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}});
        server.createContext("/large",e->{byte[] bytes=new byte[140000];e.sendResponseHeaders(200,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}catch(IOException ignored){}});
        server.start();
        try {
            var c=config();String root="http://127.0.0.1:"+server.getAddress().getPort();
            c.profile().endpoint=root+"/redirect";assertEquals(302,assertThrows(LlmApi.ApiFailure.class,()->LlmApi.translate(c,"hello")).status);assertEquals(0,redirected.get());
            c.profile().endpoint=root+"/auth";var error=assertThrows(LlmApi.ApiFailure.class,()->LlmApi.translate(c,"hello"));assertEquals(401,error.status);assertFalse(error.toString().contains("private-server"));
            c.profile().endpoint=root+"/large";assertThrows(IOException.class,()->LlmApi.translate(c,"hello"));
        }finally{server.stop(0);}
    }
}
