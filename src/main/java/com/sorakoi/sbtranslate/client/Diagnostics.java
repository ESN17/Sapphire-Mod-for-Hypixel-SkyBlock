package com.sorakoi.sbtranslate.client;
import com.sorakoi.sbtranslate.Sapphire;

/** Diagnostics contain identifiers and counters, never captured source text or NBT. */
public final class Diagnostics {
    private static long lastLog;
    private static String previous="";
    public static String last="尚无诊断";
    public static void item(String id,boolean translated,int missing) {
        if(!Sapphire.CONFIG.debug)return;
        last="ID="+(id==null?"UNKNOWN":id)+", dictionary="+(Sapphire.TRANSLATIONS.item(id)!=null)+", translated="+translated+", missingLines="+missing;
        log();
    }
    public static void text(String scope,String rule,boolean changed) {
        if(!Sapphire.CONFIG.debug)return;
        last="scope="+scope+", rule="+(rule==null?"none":rule.split(":",2)[0])+", translated="+changed;log();
    }
    private static void log() {
        long now=System.nanoTime();
        if(!previous.equals(last) && now-lastLog>=5_000_000_000L){Sapphire.LOGGER.info("{}; SkyBlock={}",last,Sapphire.CONTEXT.confirmed());lastLog=now;previous=last;}
    }
}
