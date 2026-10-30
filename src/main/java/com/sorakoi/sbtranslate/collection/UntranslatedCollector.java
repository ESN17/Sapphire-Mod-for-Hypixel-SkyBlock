package com.sorakoi.sbtranslate.collection;
import com.google.gson.*;
import com.sorakoi.sbtranslate.translation.TranslationManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.*;

/** Bounded producer queue; the single worker owns aggregation and all disk access. */
public final class UntranslatedCollector implements AutoCloseable {
    public record Observation(String category, String context, String id, String skyblockId, String name,
                              List<String> missing, boolean partial) {}
    private final Path root;
    private final int maxRecords;
    private final long maxBytes;
    private final ArrayBlockingQueue<Observation> queue;
    private final ScheduledExecutorService worker;
    private final Map<String,JsonObject> records = new LinkedHashMap<>();
    private final Set<String> dirty = new HashSet<>();
    private final Map<String,Long> recent = new LinkedHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private volatile boolean enabled, closed;
    private volatile int recordCount;
    private volatile String error = "";
    private final AtomicLong dropped = new AtomicLong();
    private long writes;
    private static final Pattern NUMBERS = Pattern.compile("(?<![A-Za-z0-9_])[+-]?\\d+(?:,\\d{3})*(?:\\.\\d+)?%?");
    public static String normalize(String text) {
        Matcher m = NUMBERS.matcher(text); StringBuilder b = new StringBuilder(); int i = 0;
        while (m.find()) m.appendReplacement(b, "{value" + (++i) + "}"); m.appendTail(b); return b.toString();
    }
    public UntranslatedCollector(Path root, int maxRecords, int queueSize, long maxBytes) {
        this.root=root; this.maxRecords=maxRecords; this.maxBytes=maxBytes; queue=new ArrayBlockingQueue<>(queueSize);
        worker=Executors.newSingleThreadScheduledExecutor(r -> { Thread t=new Thread(r,"sapphire-collector"); t.setDaemon(true); return t; });
        worker.execute(this::restore);
        worker.scheduleWithFixedDelay(this::drain,1,1,TimeUnit.SECONDS);
        worker.scheduleWithFixedDelay(this::persist,30,30,TimeUnit.SECONDS);
    }
    public void enabled(boolean value) { enabled=value; }
    public synchronized void observe(Observation o, boolean confirmed, boolean safe) {
        if (!enabled || closed || !confirmed || !safe || o.missing.isEmpty()) return;
        if (o.missing.size()>64 || o.missing.stream().anyMatch(s -> s.length()>1024)) { dropped.incrementAndGet(); return; }
        String key = key(o); long now=System.nanoTime(); Long last=recent.get(key);
        if (last != null && now-last < TimeUnit.SECONDS.toNanos(1)) return;
        if (recent.size() >= maxRecords && last == null) recent.remove(recent.keySet().iterator().next());
        recent.put(key,now); if (!queue.offer(o)) dropped.incrementAndGet();
    }
    private static String key(Observation o) {
        String semantic = o.id != null ? o.id : normalize(o.name + "\n" + String.join("\n",o.missing));
        return hash(o.category + "\0" + o.context + "\0" + semantic);
    }
    private static String hash(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private void restore() {
        try {
            Files.createDirectories(root);
            try(var files=Files.list(root)) {
                for (Path p:files.filter(f -> f.getFileName().toString().matches("[a-f0-9]{64}\\.json")).limit(maxRecords).toList()) {
                    try { JsonObject o=TranslationManager.readObject(p); records.put(p.getFileName().toString().replace(".json",""),o); }
                    catch(Exception e) { error="Damaged record retained: " + p.getFileName(); }
                }
            }
            recordCount=records.size();
        } catch(IOException e) { error="Cannot read collection directory"; }
    }
    private void drain() {
        try {
            Observation o;
            while ((o=queue.poll()) != null) {
                String key=key(o); JsonObject r=records.get(key); String now=Instant.now().toString();
                if(r==null) {
                    if(records.size()>=maxRecords) { dropped.incrementAndGet(); continue; }
                    r=new JsonObject(); r.addProperty("schemaVersion",1); r.addProperty("category",o.category); r.addProperty("context",o.context);
                    r.addProperty("id",o.id); r.addProperty("skyblockId",o.skyblockId); r.addProperty("firstSeen",now);
                    r.addProperty("minecraftVersion","26.1.2"); r.addProperty("sapphireVersion","1.0.0"); r.addProperty("occurrences",0);
                    JsonObject original=new JsonObject(); original.addProperty("name",o.name); original.add("lore",new JsonArray()); r.add("original",original);
                    JsonObject translation=new JsonObject(); translation.addProperty("name",""); translation.add("lore",new JsonObject()); r.add("translation",translation);
                    r.add("candidates",new JsonObject()); records.put(key,r);
                }
                r.addProperty("lastSeen",now); r.addProperty("occurrences",r.get("occurrences").getAsLong()+1);
                r.addProperty("status",o.id==null && o.category.equals("item") ? "UNKNOWN_SOURCE" : o.partial ? "PARTIAL" : "UNTRANSLATED");
                JsonArray samples=r.getAsJsonObject("original").getAsJsonArray("lore"); JsonObject candidates=r.getAsJsonObject("candidates");
                for(String line:o.missing) {
                    String normalized=normalize(line);
                    if(!candidates.has(normalized) && candidates.size()<64) candidates.add(normalized,new JsonArray());
                    if(candidates.has(normalized)) { JsonArray examples=candidates.getAsJsonArray(normalized); JsonPrimitive p=new JsonPrimitive(line);
                        if(examples.size()<3 && !examples.contains(p)) { examples.add(p); if(samples.size()<64 && !samples.contains(p)) samples.add(p); }
                    }
                }
                dirty.add(key);
            }
            recordCount=records.size();
        } catch(RuntimeException e) { error="Invalid collected record; existing files retained"; }
    }
    private void persist() {
        drain();
        try {
            long diskBytes; int diskRecords;
            try(var files=Files.list(root)) {
                var disk=files.filter(Files::isRegularFile).toList();
                diskBytes=disk.stream().mapToLong(p -> {try{return Files.size(p);}catch(IOException e){return maxBytes;}}).sum();
                diskRecords=(int)disk.stream().filter(p->p.toString().endsWith(".json")).count();
            }
            for(String key:new ArrayList<>(dirty)) {
                Path path=root.resolve(key+".json"); JsonObject r=records.get(key);
                if(Files.exists(path)) {
                    try { JsonObject old=TranslationManager.readObject(path); if(old.has("translation")) r.add("translation",old.get("translation")); }
                    catch(Exception e) { error="Damaged record retained: "+path.getFileName(); continue; }
                }
                boolean exists=Files.exists(path);
                if(!exists && diskRecords>=maxRecords) {error="Collection file limit reached";continue;}
                boolean filled=r.getAsJsonObject("translation").get("name").getAsString().isBlank()==false ||
                    r.getAsJsonObject("translation").getAsJsonObject("lore").entrySet().stream().anyMatch(e->!e.getValue().getAsString().isBlank());
                r.addProperty("workflowStatus",filled?"TRANSLATION_EDITED":"COLLECTED_PENDING");
                byte[] bytes=(gson.toJson(r)+"\n").getBytes(StandardCharsets.UTF_8); long oldSize=exists?Files.size(path):0;
                if(bytes.length>256*1024 || diskBytes-oldSize+bytes.length>maxBytes) { error="Collection disk limit reached"; continue; }
                Path temp=root.resolve(key+".tmp"); Files.write(temp,bytes);
                try { Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
                catch(AtomicMoveNotSupportedException e) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); }
                diskBytes+=bytes.length-oldSize; if(!exists)diskRecords++; dirty.remove(key); writes++;
            }
        } catch(IOException | RuntimeException e) { error="Collection write failed; pending records retained in memory"; }
    }
    public CompletableFuture<Void> flush() { return CompletableFuture.runAsync(this::persist,worker); }
    public CompletableFuture<Void> clear() {
        enabled=false; queue.clear(); synchronized(this){recent.clear();}
        return CompletableFuture.runAsync(() -> {
            try(var paths=Files.list(root)) {
                for(Path p:paths.filter(p -> p.getFileName().toString().matches("[a-f0-9]{64}\\.json")).toList()) Files.delete(p);
                records.clear(); dirty.clear(); recordCount=0;
            }catch(IOException e){error="Cannot clear collection";}
        },worker);
    }
    public int size(){return recordCount;}
    public long dropped(){return dropped.get();}
    public long writes(){return writes;}
    public String error(){return error;}
    public void close() {
        enabled=false; closed=true;
        try { flush().get(10,TimeUnit.SECONDS); } catch(Exception e) { error="Collector shutdown did not finish"; }
        worker.shutdown();
    }
}
