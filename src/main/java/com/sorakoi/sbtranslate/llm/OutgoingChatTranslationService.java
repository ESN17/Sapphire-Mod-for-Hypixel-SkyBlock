package com.sorakoi.sbtranslate.llm;

import java.util.ArrayDeque;
import java.util.concurrent.*;
import java.util.function.*;

/** Ordered outgoing requests. No retries or deduplication: each Enter means one message. */
public final class OutgoingChatTranslationService implements AutoCloseable {
    public record Result(long generation,String original,String translated,String error) {}
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{var t=new Thread(r,"Sapphire outgoing chat");t.setDaemon(true);return t;});
    private final LlmTranslationService.Transport transport;
    private final LongSupplier clock;
    private final ArrayDeque<Long> requests=new ArrayDeque<>();
    private long generation;
    private FutureTask<String> active;
    public OutgoingChatTranslationService(){this(LlmApi::translate,System::currentTimeMillis);}
    public OutgoingChatTranslationService(LlmTranslationService.Transport transport,LongSupplier clock){this.transport=transport;this.clock=clock;}
    public static boolean hasChinese(String text){return text.codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN);}
    public static String validate(String value) {
        if(value==null)throw new IllegalArgumentException("empty translation");
        String text=value.strip();
        if(text.isEmpty() || text.length()>256 || text.startsWith("/") || hasChinese(text) || text.codePoints().anyMatch(c->Character.isISOControl(c) || c=='§'))throw new IllegalArgumentException("invalid chat translation");
        return text;
    }
    public synchronized boolean current(long token){return token==generation;}
    public synchronized void reset(){generation++;worker.getQueue().clear();if(active!=null)active.cancel(true);active=null;}
    public synchronized void submit(LlmConfig config,String original,Consumer<Result> complete) {
        long token=generation;
        String error=config.validation();
        if(!config.enabled || !config.outgoing || !error.isEmpty()) {complete.accept(new Result(token,original,null,error.isEmpty()?"聊天英译未启用":error));return;}
        if(original==null || original.length()>256 || original.startsWith("/")){complete.accept(new Result(token,original,null,"原文超出聊天限制"));return;}
        LlmConfig snapshot=config.copy();snapshot.prompt=snapshot.outgoingPrompt;
        try{worker.execute(()->run(snapshot,original,token,complete));}
        catch(RejectedExecutionException e){complete.accept(new Result(token,original,null,"聊天翻译队列已满"));}
    }
    private void run(LlmConfig config,String original,long token,Consumer<Result> complete) {
        FutureTask<String> call=new FutureTask<>(()->validate(transport.send(config,original)));
        synchronized(this) {
            if(token!=generation)return;
            long now=clock.getAsLong();while(!requests.isEmpty() && now-requests.peekFirst()>=60000)requests.removeFirst();
            active=call;
        }
        String translated=null,error="";
        try{call.run();translated=call.get();}catch(Exception e){error=LlmTranslationService.error(e);}
        synchronized(this){if(token!=generation)return;if(active==call)active=null;complete.accept(new Result(token,original,translated,error));}
    }
    @Override public synchronized void close(){reset();worker.shutdownNow();}
}
