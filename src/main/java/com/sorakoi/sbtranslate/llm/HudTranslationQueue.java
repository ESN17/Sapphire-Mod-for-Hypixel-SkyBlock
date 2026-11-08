package com.sorakoi.sbtranslate.llm;

import com.sorakoi.sbtranslate.translation.TextTranslationEngine;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;

/** Render reads cached display copies; only tick submits API work. Values are rebound per current row. */
public final class HudTranslationQueue {
    @FunctionalInterface public interface Submitter {
        boolean submit(String text,Predicate<String> valid,Consumer<String> success,Consumer<String> failure);
    }
    private record Key(String scope,Component source,long namesVersion) {}
    private static final class View {
        final LlmStyledText.Request request;
        String template;Component display;
        View(LlmStyledText.Request request){this.request=request;}
    }
    private final LinkedHashMap<Key,View> views=new LinkedHashMap<>(128,0.75f,true);
    private final LinkedHashMap<String,String> templates=new LinkedHashMap<>(128,0.75f,true);
    private final LinkedHashMap<String,LlmStyledText.Request> waiting=new LinkedHashMap<>();
    private final Set<String> active=new HashSet<>();
    private final LinkedHashMap<String,Long> failed=new LinkedHashMap<>();
    private final LongSupplier clock;
    private long generation;
    private long namesVersion;
    private Set<String> names=Set.of();
    public HudTranslationQueue(){this(System::currentTimeMillis);}
    public HudTranslationQueue(LongSupplier clock){this.clock=clock;}

    public Component displayLines(Component local,String scope,Set<String> playerNames) {
        var resolved=TextTranslationEngine.resolvedDisplay(local);
        var result=TextTranslationEngine.lines(resolved,line->display(line,scope,playerNames));
        return result==resolved?local:result;
    }

    public synchronized Component display(Component local,String scope,Set<String> playerNames) {
        if(playerNames!=names && !playerNames.equals(names)){names=Set.copyOf(playerNames);namesVersion++;}
        var key=new Key(scope,local,namesVersion);
        View view=views.get(key);
        if(view==null) {
            view=new View(HudLlmText.prepare(local,playerNames));
            views.put(new Key(scope,local.copy(),namesVersion),view);trim(views,512);
        }
        var request=view.request;if(request==null)return local;
        String saved=templates.get(request.text());
        if(saved!=null) {
            if(!saved.equals(view.template)){view.display=request.restore(saved);view.template=saved;}
            return view.display==null?local:view.display;
        }
        if(!active.contains(request.text()) && waiting.size()<128 && failed.getOrDefault(request.text(),0L)<=clock.getAsLong())
            waiting.putIfAbsent(request.text(),request);
        return local;
    }
    public synchronized void tick(Submitter submitter) {
        // Keep HUD work bounded so chat and commands can share the API workers.
        for(int count=0;count<8 && active.size()<16 && !waiting.isEmpty();count++) {
            var key=waiting.keySet().iterator().next();var request=waiting.remove(key);
            long current=generation;active.add(key);
            boolean accepted=submitter.submit(key,request::valid,response->complete(current,key,request,response),error->reject(current,key));
            if(!accepted)reject(current,key);
        }
    }
    private synchronized void complete(long current,String key,LlmStyledText.Request request,String response) {
        if(current!=generation)return;
        active.remove(key);
        if(request.valid(response)){templates.put(key,response);trim(templates,512);failed.remove(key);}
        else reject(current,key);
    }
    private synchronized void reject(long current,String key) {
        if(current!=generation)return;
        active.remove(key);failed.put(key,clock.getAsLong()+60000);trim(failed,512);
    }
    public synchronized void clear(){generation++;views.clear();templates.clear();waiting.clear();active.clear();failed.clear();}
    public synchronized int pendingCount(){return waiting.size()+active.size();}
    public synchronized int viewCount(){return views.size();}
    private static void trim(Map<?,?> map,int limit){while(map.size()>limit)map.remove(map.keySet().iterator().next());}
}
