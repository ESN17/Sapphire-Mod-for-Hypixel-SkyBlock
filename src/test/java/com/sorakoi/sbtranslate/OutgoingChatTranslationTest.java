package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.llm.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class OutgoingChatTranslationTest {
    private LlmConfig config() throws Exception {var c=LlmConfig.defaults();c.enabled=true;c.outgoing=true;c.keys.put(c.provider,"test-only-not-a-real-key");return c;}
    @Test void validatesSingleEnglishChatWithoutCommandsOrTruncation(){
        assertTrue(OutgoingChatTranslationService.hasChinese("出售 DR-X655"));
        assertFalse(OutgoingChatTranslationService.hasChinese("Selling DR-X655"));
        assertEquals("Selling DR-X655",OutgoingChatTranslationService.validate(" Selling DR-X655 "));
        for(String s:new String[]{"", "/msg user hello", "仍有中文", "hello\nworld", "§ahello", "x".repeat(257)})assertThrows(IllegalArgumentException.class,()->OutgoingChatTranslationService.validate(s));
    }
    @Test void preservesOrderAndRepeatedMessagesAndUsesOutgoingPrompt() throws Exception {
        var results=new LinkedBlockingQueue<OutgoingChatTranslationService.Result>();var prompts=new LinkedBlockingQueue<String>();
        try(var service=new OutgoingChatTranslationService((c,t)->{prompts.add(c.prompt);return t.equals("第一条")?"first":"second";},System::currentTimeMillis)) {
            var c=config();c.outgoingPrompt="translate into English";
            service.submit(c,"第一条",results::add);service.submit(c,"第二条",results::add);service.submit(c,"第二条",results::add);
            for(String expected:new String[]{"first","second","second"}){var r=results.poll(5,TimeUnit.SECONDS);assertNotNull(r);assertEquals(expected,r.translated());assertEquals(c.outgoingPrompt,prompts.poll());}
        }
    }
    @Test void failureRestoresOriginalAndDoesNotRetry() throws Exception {
        var calls=new AtomicInteger();var results=new LinkedBlockingQueue<OutgoingChatTranslationService.Result>();
        try(var service=new OutgoingChatTranslationService((c,t)->{calls.incrementAndGet();throw new java.net.http.HttpTimeoutException("secret detail");},System::currentTimeMillis)) {
            service.submit(config(),"你好",results::add);var r=results.poll(5,TimeUnit.SECONDS);assertNotNull(r);assertEquals("你好",r.original());assertNull(r.translated());assertFalse(r.error().contains("secret"));assertEquals(1,calls.get());
        }
    }
    @Test void legacyRateLimitNoLongerPreventsSending() throws Exception {
        var results=new LinkedBlockingQueue<OutgoingChatTranslationService.Result>();var c=config();c.requestsPerMinute=1;
        try(var service=new OutgoingChatTranslationService((cfg,t)->"hello",()->1000L)) {
            service.submit(c,"你好",results::add);assertNotNull(results.poll(5,TimeUnit.SECONDS).translated());
            for(int i=0;i<70;i++) {
                service.submit(c,"第二条"+i,results::add);var r=results.poll(5,TimeUnit.SECONDS);assertNotNull(r);assertEquals("hello",r.translated());assertEquals("",r.error());
            }
        }
    }
    @Test void disconnectInvalidatesCompletedCallbacksAndCancelsActive() throws Exception {
        var started=new CountDownLatch(1);var cancelled=new CountDownLatch(1);var results=new LinkedBlockingQueue<OutgoingChatTranslationService.Result>();
        try(var service=new OutgoingChatTranslationService((c,t)->{started.countDown();try{new CountDownLatch(1).await();}catch(InterruptedException e){cancelled.countDown();throw e;}return "hello";},System::currentTimeMillis)) {
            service.submit(config(),"你好",results::add);assertTrue(started.await(5,TimeUnit.SECONDS));service.reset();assertTrue(cancelled.await(5,TimeUnit.SECONDS));assertFalse(service.current(0));assertTrue(results.isEmpty());
        }
    }
}
