package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.llm.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class LlmBurstTest {
    @Test void burstOfOneHundredTwentyMessagesDrainsWithBoundedConcurrency() throws Exception {
        var started=new CountDownLatch(LlmTranslationService.CONCURRENCY);var release=new CountDownLatch(1);
        var running=new AtomicInteger();var peak=new AtomicInteger();var calls=new AtomicInteger();
        try(var service=new LlmTranslationService((c,text)->{
            int active=running.incrementAndGet();peak.accumulateAndGet(active,Math::max);calls.incrementAndGet();started.countDown();
            try {release.await(5,TimeUnit.SECONDS);return "译文:"+text;}finally{running.decrementAndGet();}
        },System::currentTimeMillis,5)) {
            service.configure(LlmTranslationTest.config());var results=new ArrayList<CompletableFuture<LlmTranslationService.Result>>();
            for(int i=0;i<120;i++){var result=new CompletableFuture<LlmTranslationService.Result>();results.add(result);assertTrue(service.submit("message "+i,result::complete));}
            assertTrue(started.await(2,TimeUnit.SECONDS));assertEquals(120,service.pendingCount());release.countDown();
            for(int i=0;i<120;i++)assertEquals("译文:message "+i,results.get(i).get(5,TimeUnit.SECONDS).text());
            assertEquals(120,calls.get());assertEquals(LlmTranslationService.CONCURRENCY,peak.get());assertEquals(0,service.pendingCount());
        }finally{release.countDown();}
    }
    @Test void temporaryFailureAndInvalidFormattingRetryWithoutDiscardingOtherMessages() throws Exception {
        var counts=new ConcurrentHashMap<String,AtomicInteger>();
        try(var service=new LlmTranslationService((c,text)->{
            int call=counts.computeIfAbsent(text,k->new AtomicInteger()).incrementAndGet();
            if(text.equals("limited") && call==1)throw new LlmApi.ApiFailure(429);
            if(text.equals("timeout") && call==1)throw new java.net.http.HttpTimeoutException("test");
            if(text.equals("bad format") && call==1)return "invalid";
            return "valid";
        },System::currentTimeMillis,5)) {
            service.configure(LlmTranslationTest.config());var results=new ArrayList<CompletableFuture<LlmTranslationService.Result>>();
            for(String text:List.of("limited","timeout","bad format","normal")) {
                var result=new CompletableFuture<LlmTranslationService.Result>();results.add(result);
                assertTrue(service.submit(text,"valid"::equals,result::complete,e->result.completeExceptionally(new AssertionError(e))));
            }
            for(var result:results)assertEquals("valid",result.get(3,TimeUnit.SECONDS).text());
            assertEquals(1,counts.get("normal").get());assertEquals(2,counts.get("limited").get());assertEquals(2,counts.get("bad format").get());
        }
    }
    @Test void persistentFailureIsReportedAfterBoundedAttempts() throws Exception {
        var calls=new AtomicInteger();var failure=new CompletableFuture<String>();
        try(var service=new LlmTranslationService((c,text)->{calls.incrementAndGet();throw new LlmApi.ApiFailure(503);},System::currentTimeMillis,5)) {
            service.configure(LlmTranslationTest.config());service.submit("fail",s->true,r->fail(),failure::complete);
            assertTrue(failure.get(3,TimeUnit.SECONDS).contains("503"));assertEquals(3,calls.get());assertEquals(0,service.pendingCount());
        }
    }
    @Test void duplicateBurstNotifiesEveryListenerWithOneApiRequest() throws Exception {
        var release=new CountDownLatch(1);var calls=new AtomicInteger();var received=new CountDownLatch(160);
        try(var service=new LlmTranslationService((c,text)->{calls.incrementAndGet();release.await(5,TimeUnit.SECONDS);return "译文";},System::currentTimeMillis)) {
            service.configure(LlmTranslationTest.config());
            for(int i=0;i<160;i++)assertTrue(service.submit("same notification",r->received.countDown()));
            release.countDown();assertTrue(received.await(3,TimeUnit.SECONDS));assertEquals(1,calls.get());
        }finally{release.countDown();}
    }
}
