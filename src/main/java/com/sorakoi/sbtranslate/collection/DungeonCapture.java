package com.sorakoi.sbtranslate.collection;

import com.google.gson.Gson;
import com.sorakoi.sbtranslate.translation.TranslationManager;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in diagnostic build: UTF-8 JSON lines, never network uploads or render-thread IO. */
public final class DungeonCapture implements AutoCloseable {
    public static final DungeonCapture INSTANCE=create();
    private final Path file;
    private final Set<String> seen=new HashSet<>();
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"sapphire-dungeon-capture");t.setDaemon(true);return t;});
    private final Semaphore pending=new Semaphore(2048);
    private volatile String error="";
    private volatile boolean enabled=true;
    private int dropped;
    private static final java.util.regex.Pattern ENGLISH=java.util.regex.Pattern.compile("[A-Za-z]{3,}");
    private static final java.util.regex.Pattern PLAYER=java.util.regex.Pattern.compile("^(?:(?:Party|Guild|Co-op|From|To) >.*|\\[(?:VIP\\+?|MVP\\+{0,2}|YOUTUBE|ADMIN|MOD|HELPER)\\] [A-Za-z0-9_]{1,16}: .*)$");
    public DungeonCapture(Path file){this.file=file;}
    private static DungeonCapture create(){
        try(var in=DungeonCapture.class.getResourceAsStream("/dungeon-capture.properties")){
            if(in==null)return null;var p=new Properties();p.load(in);return new DungeonCapture(Path.of(p.getProperty("output")));
        }catch(Exception e){throw new IllegalStateException("Invalid dungeon capture configuration",e);}
    }
    public static boolean dungeonEvidence(List<String> lines){
        return lines.stream().map(TranslationManager::plain).anyMatch(s->s.contains("The Catacombs (") || s.contains("Cleared:") || s.contains("Secrets Found:") || s.contains("Dungeon Cleared:") || s.contains("地牢已完成"));
    }
    public static boolean candidate(String original,String display){
        String text=TranslationManager.plain(original).strip();
        if(text.contains("[SkyBlocker]") || text.contains("[SkyHanni]") || text.startsWith("[Sapphire]"))return false;
        // Exclude ordinary player chat, but retain [NPC]/[BOSS] dialogue.
        if(!text.startsWith("[BOSS]") && !text.startsWith("[NPC]") && PLAYER.matcher(text).matches())return false;
        return ENGLISH.matcher(TranslationManager.plain(display)).find() && !text.isBlank();
    }
    public synchronized void observe(boolean dungeon,String scope,String context,String original,String display){
        if(!enabled || !dungeon || original==null || display==null || !candidate(original,display))return;
        if(original.length()>16384 || display.length()>16384){dropped++;return;}
        String key=scope+'\0'+context+'\0'+original+'\0'+display;
        if(seen.contains(key))return;
        if(seen.size()>=50000 || !pending.tryAcquire()){dropped++;return;}
        seen.add(key);
        var row=new LinkedHashMap<String,Object>();row.put("time",Instant.now().toString());row.put("scope",scope);row.put("context",context);
        row.put("original",original);row.put("display",display);row.put("plain",TranslationManager.plain(original));
        writer.execute(()->{try{
            Files.createDirectories(file.toAbsolutePath().getParent());
            if(Files.exists(file) && Files.size(file)>100L*1024*1024)throw new java.io.IOException("100 MiB limit reached");
            Files.writeString(file,new Gson().toJson(row)+System.lineSeparator(),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        }catch(Exception e){error=e.toString();synchronized(this){seen.remove(key);}}finally{pending.release();}});
    }
    public void enabled(boolean value){enabled=value;}
    public synchronized String status(){return "地牢采集="+enabled+"，记录="+seen.size()+"，丢弃="+dropped+"，文件="+file+(error.isEmpty()?"":"，错误="+error);}
    public void close(){enabled=false;writer.shutdown();try{writer.awaitTermination(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
