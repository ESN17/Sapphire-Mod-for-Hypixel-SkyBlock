package com.sorakoi.sbtranslate.llm;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Bounded asynchronous backlog, coalesced requests and delayed retries; no per-minute quota. */
public final class LlmTranslationService implements AutoCloseable {
    public static final int MAX_PENDING=512, CONCURRENCY=4, MAX_LISTENERS=512;
    private static final int MAX_ATTEMPTS=3;
    @FunctionalInterface public interface Transport {String send(LlmConfig config,String text) throws Exception;}
    public record Result(long generation,String text) {}
    private record Listener(Consumer<Result> success,Consumer<String> failure) {}
    private static final class Job {
        final String text;final long generation;final LlmConfig config;final Predicate<String> valid;
        final List<Listener> listeners=new ArrayList<>();
        int attempts;Future<?> scheduled;FutureTask<String> active;
        Job(String text,long generation,LlmConfig config,Predicate<String> valid,Listener listener) {
            this.text=text;this.generation=generation;this.config=config;this.valid=valid;listeners.add(listener);
        }
    }
    private final ScheduledThreadPoolExecutor worker=new ScheduledThreadPoolExecutor(CONCURRENCY,r->{
        var t=new Thread(r,"Sapphire translation");t.setDaemon(true);return t;
    });
    private final Transport transport;private final LongSupplier clock;private final long retryDelayMillis;
    private final Map<String,String> cache=new LinkedHashMap<>();
    private final Map<String,Job> pending=new HashMap<>();
    private LlmConfig config;private long generation,cooldownUntil;private String permanentError;
    private String configError="";
    private volatile String status="在线翻译未启用";
    public LlmTranslationService(){this(LlmApi::translate,System::currentTimeMillis);}
    public LlmTranslationService(Transport transport,LongSupplier clock){this(transport,clock,1000);}
    public LlmTranslationService(Transport transport,LongSupplier clock,long retryDelayMillis) {
        this.transport=transport;this.clock=clock;this.retryDelayMillis=Math.max(1,retryDelayMillis);
        worker.setRemoveOnCancelPolicy(true);
    }
    public synchronized void configure(LlmConfig config){
        reset();
        this.config=config.copy();
        configError=config.enabled ? this.config.validation() : "";
        status=config.enabled?"就绪":"在线翻译未启用";
    }
    public synchronized void reset() {
        generation++;
        for(var job:pending.values()) {
            if(job.scheduled!=null)job.scheduled.cancel(true);
            if(job.active!=null)job.active.cancel(true);
        }
        pending.clear();worker.purge();cache.clear();cooldownUntil=0;permanentError=null;
    }
    public synchronized boolean current(long value){return generation==value && config!=null && config.enabled;}
    public synchronized long generation(){return generation;}
    public String status(){return status;}
    public synchronized int pendingCount(){return pending.size();}
    public boolean submit(String text,Consumer<Result> success){return submit(text,s->s!=null && !s.isBlank(),success,error->{});}
    public synchronized boolean submit(String text,Predicate<String> valid,Consumer<Result> success,Consumer<String> failure) {
        if(config==null || !config.enabled)return false;
        if(!configError.isEmpty()){status=configError;return false;}
        if(permanentError!=null){status=permanentError;return false;}
        if(text==null || text.isBlank() || text.length()>8000){status="待翻译消息过长，保留原文";return false;}
        String saved=cache.get(text);
        if(saved!=null && valid.test(saved)){success.accept(new Result(generation,saved));return true;}
        var waiting=pending.get(text);
        if(waiting!=null) {
            if(waiting.listeners.size()>=MAX_LISTENERS){status="重复消息过多，保留原文";return false;}
            waiting.listeners.add(new Listener(success,failure));return true;
        }
        if(pending.size()>=MAX_PENDING){status="翻译队列已满，保留原文";return false;}
        var job=new Job(text,generation,config,valid,new Listener(success,failure));
        pending.put(text,job);
        try{schedule(job,0);status="等待翻译："+pending.size()+" 条";return true;}
        catch(RejectedExecutionException e){pending.remove(text);status="翻译服务已停止，保留原文";return false;}
    }
    private void schedule(Job job,long delay){job.scheduled=worker.schedule(()->run(job),delay,TimeUnit.MILLISECONDS);}
    private boolean current(Job job){return job.generation==generation && pending.get(job.text)==job;}
    private void run(Job job) {
        FutureTask<String> call=new FutureTask<>(()->transport.send(job.config.copy(),job.text));
        synchronized(this) {
            if(!current(job))return;
            long delay=cooldownUntil-clock.getAsLong();
            if(delay>0){schedule(job,delay);return;}
            job.active=call;job.attempts++;status="正在翻译，待完成："+pending.size()+" 条";
        }
        try {
            call.run();String translated=call.get();
            if(translated==null || translated.isBlank() || !job.valid.test(translated))throw new InvalidTranslation();
            synchronized(this) {
                if(!current(job))return;
                cache.put(job.text,translated);if(cache.size()>256)cache.remove(cache.keySet().iterator().next());
                pending.remove(job.text);status=pending.isEmpty()?"翻译成功":"正在翻译，待完成："+pending.size()+" 条";
                for(var listener:job.listeners)notifySuccess(listener,new Result(job.generation,translated));
            }
        }catch(Exception e) {
            synchronized(this) {
                if(!current(job))return;
                Throwable cause=cause(e);String message=error(cause);
                if(permanent(cause)) {
                    permanentError=message;status=message;
                    // Invalid credentials/configuration need user action, not an endless retry storm.
                    for(var waiting:pending.values()) {
                        if(waiting.scheduled!=null && waiting!=job)waiting.scheduled.cancel(true);
                        if(waiting.active!=null && waiting!=job)waiting.active.cancel(true);
                        for(var listener:waiting.listeners)notifyFailure(listener,message);
                    }
                    pending.clear();
                } else if(job.attempts<MAX_ATTEMPTS) {
                    long delay=retryDelayMillis*(1L<<(job.attempts-1));
                    if(cause instanceof LlmApi.ApiFailure f && f.status==429) {
                        delay*=5;cooldownUntil=Math.max(cooldownUntil,clock.getAsLong()+delay);
                    }
                    status="请求暂未成功，已保留队列并等待重试";schedule(job,delay);
                } else {
                    pending.remove(job.text);status=message;
                    for(var listener:job.listeners)notifyFailure(listener,message);
                }
            }
        }finally {
            synchronized(this){if(job.active==call)job.active=null;}
        }
    }
    private static void notifySuccess(Listener listener,Result result){try{listener.success().accept(result);}catch(RuntimeException ignored){}}
    private static void notifyFailure(Listener listener,String error){try{listener.failure().accept(error);}catch(RuntimeException ignored){}}
    private static Throwable cause(Throwable e){while(e.getCause()!=null)e=e.getCause();return e;}
    private static boolean permanent(Throwable e){return e instanceof LlmApi.ApiFailure f && f.status>=400 && f.status<500 && f.status!=408 && f.status!=429;}
    private static final class InvalidTranslation extends Exception {}
    public static String error(Throwable e) {
        e=cause(e);
        if(e instanceof InvalidTranslation)return "API 未保留原文的格式或数值，保留原文";
        if(e instanceof LlmApi.ApiFailure f)return switch(f.status){case 401,403->"认证失败，请检查密钥和权限";case 429->"API 已限流或额度不足";case 400,404->"请检查 API 地址、模型和请求参数";default->"API 服务错误（HTTP "+f.status+"）";};
        if(e instanceof java.net.http.HttpTimeoutException)return "请求超时，保留原文";
        return "翻译失败，保留原文；请检查网络和 API 设置";
    }
    @Override public synchronized void close(){reset();worker.shutdownNow();}
}
