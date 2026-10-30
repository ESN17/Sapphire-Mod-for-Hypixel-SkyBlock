package com.sorakoi.sbtranslate.llm;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Single bounded worker, memory-only cache, explicit configuration/session invalidation. */
public final class LlmTranslationService implements AutoCloseable {
    @FunctionalInterface public interface Transport {String send(LlmConfig config,String text) throws Exception;}
    public record Result(long generation,String text) {}
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{var t=new Thread(r,"Sapphire translation");t.setDaemon(true);return t;});
    private final Transport transport;private final LongSupplier clock;
    private final Map<String,String> cache=new LinkedHashMap<>();private final Set<String> pending=new HashSet<>();
    private final Deque<Long> requests=new ArrayDeque<>();
    private LlmConfig config;private long generation,cooldownUntil;private Future<?> active;
    private volatile String status="在线翻译未启用";
    public LlmTranslationService(){this(LlmApi::translate,System::currentTimeMillis);}
    public LlmTranslationService(Transport transport,LongSupplier clock){this.transport=transport;this.clock=clock;}
    public synchronized void configure(LlmConfig config){reset();this.config=config.copy();status=config.enabled?"就绪":"在线翻译未启用";}
    public synchronized void reset(){generation++;worker.getQueue().clear();if(active!=null)active.cancel(true);cache.clear();pending.clear();}
    public synchronized boolean current(long value){return generation==value && config!=null && config.enabled;}
    public String status(){return status;}
    public synchronized boolean submit(String text,Consumer<Result> success) {
        if(config==null || !config.enabled)return false;
        String error=config.validation();if(!error.isEmpty()){status=error;return false;}
        if(text==null || text.isBlank() || text.length()>2000)return false;
        long token=generation;String saved=cache.get(text);
        if(saved!=null){success.accept(new Result(token,saved));return true;}
        if(!pending.add(text))return false;
        LlmConfig snapshot=config.copy();
        try{worker.execute(()->run(snapshot,text,token,success));return true;}
        catch(RejectedExecutionException e){pending.remove(text);status="翻译队列已满，保留原文";return false;}
    }
    private void run(LlmConfig snapshot,String text,long token,Consumer<Result> success) {
        FutureTask<String> call=new FutureTask<>(()->transport.send(snapshot,text));
        synchronized(this) {
            if(token!=generation)return;
            long now=clock.getAsLong();while(!requests.isEmpty() && now-requests.peekFirst()>=60000)requests.removeFirst();
            if(now<cooldownUntil || requests.size()>=snapshot.requestsPerMinute){pending.remove(text);status="已限速，保留原文";return;}
            requests.addLast(now);active=call;status="正在翻译...";
        }
        try {
            call.run();String translated=call.get();
            synchronized(this) {
                if(token!=generation)return;
                if(translated==null || translated.isBlank())throw new IllegalArgumentException();
                cache.put(text,translated);if(cache.size()>128)cache.remove(cache.keySet().iterator().next());status="翻译成功";
                success.accept(new Result(token,translated));
            }
        }catch(Exception e) {
            synchronized(this) {if(token==generation){cooldownUntil=clock.getAsLong()+30000;status=error(e);}}
        }finally {
            synchronized(this){if(token==generation){pending.remove(text);if(active==call)active=null;}}
        }
    }
    public static String error(Throwable e) {
        while(e.getCause()!=null)e=e.getCause();
        if(e instanceof LlmApi.ApiFailure f)return switch(f.status){case 401,403->"认证失败，请检查密钥和权限";case 429->"API 已限流或额度不足";case 400,404->"请检查 API 地址、模型和请求参数";default->"API 服务错误（HTTP "+f.status+"）";};
        if(e instanceof java.net.http.HttpTimeoutException)return "请求超时，保留原文";
        return "翻译失败，保留原文；请检查网络和 API 设置";
    }
    @Override public synchronized void close(){reset();worker.shutdownNow();}
}
