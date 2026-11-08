package com.sorakoi.sbtranslate.client;

import com.sorakoi.sbtranslate.Sapphire;
import com.sorakoi.sbtranslate.rank.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.*;

public final class SapphireRanks {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Sapphire ranks");t.setDaemon(true);return t;});
    private static volatile RankRoster roster=RankRoster.empty();private static long generation;private static int ticks;private static boolean refreshing;
    private record Cached(Component source,String name,Component display){}
    private static final Map<String,Cached> TAB_CACHE=new LinkedHashMap<>(128,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,Cached> e){return size()>256;}};
    private static void clearDisplayCaches() {
        TAB_CACHE.clear();
        ChatDisplay.invalidateLayouts();
    }
    public static boolean hypixel(String host){return host!=null && (host.toLowerCase(Locale.ROOT).replaceFirst(":\\d+$","").equals("hypixel.net") || host.toLowerCase(Locale.ROOT).replaceFirst(":\\d+$","").endsWith(".hypixel.net"));}
    private static boolean active(){var c=Minecraft.getInstance();return Sapphire.CONFIG.enabled && Sapphire.CONFIG.sapphireRanks && c.getConnection()!=null && c.getConnection().getServerData()!=null && hypixel(c.getConnection().getServerData().ip);}
    public static void join(Minecraft client) {
        long ticket=++generation;ticks=0;refreshing=false;clearDisplayCaches();if(!active())return;
        refreshing=true;
        String name=client.getUser().getName();var service=new RankClient(Sapphire.ROOT,new RankClient.Http());
        WORKER.execute(()->{
            var cached=service.cached();client.execute(()->{if(ticket==generation){roster=cached;clearDisplayCaches();}});
            try{var next=service.refresh(name);client.execute(()->{if(ticket==generation){roster=next;clearDisplayCaches();}});}
            catch(Exception e){Sapphire.LOGGER.warn("Sapphire 称号服务暂不可用，保留本地名册：{}",e.getClass().getSimpleName());}
            finally{client.execute(()->{if(ticket==generation)refreshing=false;});}
        });
    }
    public static void tick(Minecraft client){
        if(++ticks%1200!=0 || refreshing || !active())return;
        long ticket=generation;refreshing=true;
        WORKER.execute(()->{
            try{var next=new RankClient(Sapphire.ROOT,new RankClient.Http()).refreshRoster();client.execute(()->{if(ticket==generation && !next.equals(roster)){roster=next;clearDisplayCaches();}});}
            catch(Exception ignored){/* Keep the cached roster; retry on the next bounded interval. */}
            finally{client.execute(()->{if(ticket==generation)refreshing=false;});}
        });
    }
    public static void disconnect(){generation++;ticks=0;refreshing=false;clearDisplayCaches();roster=RankRoster.empty();}
    public static void close(){WORKER.shutdownNow();}
    public static Component tab(Component source,String name) {
        if(source==null || !active())return source;var old=TAB_CACHE.get("tab:"+name);
        if(old!=null && old.source.equals(source))return old.display;
        var translated=RankDisplay.tab(source,name,roster);
        TAB_CACHE.put("tab:"+name,new Cached(source.copy(),name,translated));return translated;
    }
    public static Component text(Component source){
        if(source==null || !active() || source.getString().length()>32768)return source;
        String key="text:"+source.getString();var old=TAB_CACHE.get(key);
        if(old!=null && old.source.equals(source))return old.display;
        var translated=RankDisplay.text(source,roster);
        TAB_CACHE.put(key,new Cached(source.copy(),key,translated));return translated;
    }
    public static Component chat(Component source){return text(source);}
}
