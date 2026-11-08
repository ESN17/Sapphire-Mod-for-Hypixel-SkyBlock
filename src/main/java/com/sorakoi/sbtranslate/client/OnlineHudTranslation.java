package com.sorakoi.sbtranslate.client;

import com.sorakoi.sbtranslate.Sapphire;
import com.sorakoi.sbtranslate.llm.HudTranslationQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Optional API fallback for display-only Tab rows/header/footer and every visible boss bar. */
public final class OnlineHudTranslation {
    private static final HudTranslationQueue QUEUE=new HudTranslationQueue();
    private static Set<String> names=Set.of();
    private static long generation=-1,nextNamesRefresh;
    private static Object dictionary;
    private static int settings;
    private static boolean allowed(String scope){return Sapphire.active(scope) && Sapphire.LLM_CONFIG.enabled &&
        (scope.equals("tablist")?Sapphire.LLM_CONFIG.tablist:Sapphire.LLM_CONFIG.bossbar);}
    private static void synchronize(){
        int current=Objects.hash(Sapphire.CONFIG.enabled,Sapphire.CONFIG.tablist,Sapphire.CONFIG.bossbar,
            Sapphire.CONFIG.forceTranslation,Sapphire.LLM_CONFIG.enabled,Sapphire.LLM_CONFIG.tablist,Sapphire.LLM_CONFIG.bossbar);
        if(generation!=Sapphire.LLM.generation() || dictionary!=Sapphire.TRANSLATIONS || settings!=current) {
            QUEUE.clear();generation=Sapphire.LLM.generation();dictionary=Sapphire.TRANSLATIONS;settings=current;
        }
    }
    public static Component display(Component local,String scope){
        if(local==null || !allowed(scope) || Minecraft.getInstance().getConnection()==null)return local;
        synchronize();
        refreshNames(Minecraft.getInstance());
        return QUEUE.displayLines(local,scope,names);
    }
    public static void tick(){
        var client=Minecraft.getInstance();
        if(client.getConnection()==null){clear();return;}
        synchronize();
        if(!allowed("tablist") && !allowed("bossbar"))return;
        refreshNames(client);
        QUEUE.tick((text,valid,success,failure)->Sapphire.LLM.submit(text,valid,
            result->client.execute(()->{if(Sapphire.LLM.current(result.generation()))success.accept(result.text());}),
            error->client.execute(()->failure.accept(error))));
    }
    private static void refreshNames(Minecraft client){
        long now=System.currentTimeMillis();
        if(now>=nextNamesRefresh) {
            var found=new HashSet<String>();
            for(var info:client.getConnection().getOnlinePlayers())found.add(info.getProfile().name());
            if(!found.equals(names))names=Set.copyOf(found);nextNamesRefresh=now+1000;
        }
    }
    public static void clear(){QUEUE.clear();names=Set.of();nextNamesRefresh=0;}
    private OnlineHudTranslation() {}
}
