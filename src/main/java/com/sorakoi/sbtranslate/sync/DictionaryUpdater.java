package com.sorakoi.sbtranslate.sync;

import com.google.gson.*;
import com.sorakoi.sbtranslate.translation.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** All calls run on Sapphire's single dictionary worker, never on a rendering thread. */
public final class DictionaryUpdater {
    public static final String DEFAULT_URL="http://sapphire.esn17cabin.club/translate";
    public enum Status { UPDATED, CURRENT, UNAVAILABLE, FAILED }
    public record Result(Status status,String version,TranslationManager translations,String detail) {
        public String message(){return switch(status){
            case UPDATED,CURRENT -> "译名库已更新至最新版！";
            case UNAVAILABLE -> "暂时无法连接到译名库服务器，请检查防火墙设置或检查是否可以正常登录至译名库服务器！";
            case FAILED -> "译名库更新失败，已保留原有译名库。";
        };}
    }
    private final Path root,sync;private final DictionaryTransport transport;
    public DictionaryUpdater(Path root,DictionaryTransport transport){this.root=root.toAbsolutePath().normalize();this.sync=this.root.resolve("dictionary-sync");this.transport=transport;}
    public static String installedVersion(Path root) throws IOException {
        Path state=root.resolve("dictionary-sync/state.json");
        if(!Files.exists(state))return "";
        if(Files.size(state)>4096)throw new IOException("Invalid dictionary state");
        try{return DictionaryVersion.parse(JsonParser.parseString(Files.readString(state)).getAsJsonObject().get("version").getAsString()).toString();}
        catch(RuntimeException e){throw new IOException("Invalid dictionary state",e);}
    }
    public static boolean installed(Path root) throws IOException {return !installedVersion(root).isEmpty() && Files.isDirectory(root.resolve("translations"));}
    public Result update(String url) {
        return update(url,version->{});
    }
    /** Called once after a newer release is selected, immediately before its download. */
    public Result update(String url,java.util.function.Consumer<String> downloading) {
        Path work=null;String current="";
        try {
            recover();current=installedVersion(root);
            var endpoint=DictionaryIndex.endpoint(url);String index;
            try{index=transport.index(endpoint);}catch(IOException e){return result(Status.UNAVAILABLE,current,e);}
            var release=DictionaryIndex.latest(index,endpoint);
            if(!current.isEmpty() && Files.isDirectory(root.resolve("translations")) && release.version().compareTo(DictionaryVersion.parse(current))<=0)return new Result(Status.CURRENT,current,null,"");
            ensureSafe(sync);Files.createDirectories(sync);work=Files.createTempDirectory(sync,"work-");Path zip=work.resolve("download.zip");
            downloading.accept(release.version().toString());
            try{transport.download(release.uri(),zip);}catch(IOException e){return result(Status.UNAVAILABLE,current,e);}
            if(release.size()>=0 && Files.size(zip)!=release.size())throw new IOException("ZIP size mismatch");
            if(!release.sha256().isEmpty() && !DictionaryArchive.sha256(zip).equals(release.sha256()))throw new IOException("ZIP checksum mismatch");
            Path stage=work.resolve("stage");var manager=DictionaryArchive.extract(zip,stage,release.version());
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Update interrupted");
            synchronized(TranslationEditorStore.class) {commit(stage,current,release.version().toString());}
            return new Result(Status.UPDATED,release.version().toString(),manager,"");
        }catch(Exception e){return result(Status.FAILED,current,e);}
        finally{if(work!=null)try{deleteWork(work);}catch(IOException ignored){/* A leftover staging directory is harmless. */}}
    }
    private static Result result(Status status,String version,Exception e){return new Result(status,version,null,e.getClass().getSimpleName()+": "+e.getMessage());}
    private void commit(Path stage,String previous,String next) throws IOException {
        String id=UUID.randomUUID().toString();Path backup=sync.resolve("backups").resolve(id);
        ensureSafe(root.resolve("translations"));ensureSafe(backup);Files.createDirectories(backup);
        JsonObject journal=new JsonObject();journal.addProperty("id",id);journal.addProperty("previous",previous);journal.addProperty("next",next);journal.addProperty("hadOld",Files.exists(root.resolve("translations")));
        write(sync.resolve("transaction.json"),journal);
        try {
            if(journal.get("hadOld").getAsBoolean())Files.move(root.resolve("translations"),backup.resolve("translations"));
            Files.move(stage.resolve("translations"),root.resolve("translations"));
            if(Files.exists(stage.resolve("licenses")))Files.move(stage.resolve("licenses"),backup.resolve("installed-licenses"));
            JsonObject state=new JsonObject();state.addProperty("version",next);write(sync.resolve("state.json"),state);
            Files.delete(sync.resolve("transaction.json"));
        }catch(IOException e){try{recover();}catch(IOException failure){e.addSuppressed(failure);}throw e;}
    }
    /** Journal existence means an interrupted transaction; prefer the previous known working snapshot. */
    public void recover() throws IOException {
        synchronized(TranslationEditorStore.class) {
            ensureSafe(sync);Path transaction=sync.resolve("transaction.json");if(!Files.exists(transaction))return;
            if(Files.size(transaction)>4096)throw new IOException("Invalid dictionary transaction");
            try {
                var journal=JsonParser.parseString(Files.readString(transaction)).getAsJsonObject();
                String id=journal.get("id").getAsString();if(!UUID.fromString(id).toString().equals(id))throw new IOException("Invalid transaction ID");
                String previous=journal.get("previous").getAsString();if(!previous.isEmpty())DictionaryVersion.parse(previous);
                Path backup=sync.resolve("backups").resolve(id).resolve("translations"),live=root.resolve("translations");ensureSafe(backup);ensureSafe(live);
                if(Files.exists(backup) || !journal.get("hadOld").getAsBoolean()) {
                    if(Files.exists(live)) {
                        Path failed=sync.resolve("failed").resolve(id+"-"+UUID.randomUUID());ensureSafe(failed);Files.createDirectories(failed.getParent());Files.move(live,failed);
                    }
                    if(Files.exists(backup))Files.move(backup,live);
                }
                if(previous.isEmpty())Files.deleteIfExists(sync.resolve("state.json"));
                else {JsonObject state=new JsonObject();state.addProperty("version",previous);write(sync.resolve("state.json"),state);}
                Files.delete(transaction);
            }catch(RuntimeException e){throw new IOException("Cannot recover dictionary transaction",e);}
        }
    }
    private void ensureSafe(Path path) throws IOException {
        Path p=path.toAbsolutePath().normalize();if(!p.startsWith(root))throw new IOException("Path outside dictionary root");
        for(Path cursor=p;cursor!=null;cursor=cursor.getParent())if(Files.isSymbolicLink(cursor))throw new IOException("Symbolic dictionary path");
    }
    private void write(Path path,JsonObject object) throws IOException {
        ensureSafe(path);Files.createDirectories(path.getParent());Path temp=path.resolveSibling(path.getFileName()+".tmp");ensureSafe(temp);
        Files.writeString(temp,object+"\n");
        try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
    }
    private void deleteWork(Path path) throws IOException {
        ensureSafe(path);if(!path.getParent().equals(sync) || !path.getFileName().toString().startsWith("work-"))throw new IOException("Invalid temporary directory");
        try(var files=Files.walk(path)){for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.delete(file);}
    }
}
