package com.sorakoi.sbtranslate.sync;

import com.google.gson.*;
import com.google.gson.stream.*;
import com.sorakoi.sbtranslate.translation.TranslationManager;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.ZipFile;

/** A full, data-only dictionary snapshot. Nothing is extracted into the live directory. */
public final class DictionaryArchive {
    public static final long MAX_ZIP_BYTES=64L*1024*1024, MAX_TOTAL_BYTES=128L*1024*1024;
    public static final String MANIFEST="sapphire-dictionary.json";
    public static final List<String> REQUIRED=List.of("global","templates","gui","chat","scoreboard","actionbar","title","subtitle","bossbar","reforges","enchantments","enchantment-ids","gui-menus","name-modifiers","tooltip-labels","tooltip-paragraphs","runes","dyes","locations","skyblock-months","sidebar-formats","bazaar-events","tablist","mining-events","system-messages","death-messages","creature-messages","entity-names","entity-labels","entity-menus","community-skyzh","community-wiki");
    public static String sha256(byte[] bytes) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    public static String sha256(Path file) throws IOException {
        try(var input=Files.newInputStream(file)) {
            var digest=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];
            for(int n;(n=input.read(b))!=-1;)digest.update(b,0,n);
            return HexFormat.of().formatHex(digest.digest());
        }catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    private static boolean payload(String name) {
        if(name.matches("translations/[a-z][a-z0-9-]*\\.json"))return true;
        if(name.matches("translations/community-wiki/[a-z0-9-]+\\.json"))return true;
        if(name.matches("translations/items/[A-Za-z0-9_;.-]+\\.json")) {
            String base=name.substring(name.lastIndexOf('/')+1).split("\\.")[0];
            return !base.matches("(?i)(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])") && !base.isEmpty();
        }
        return Set.of("licenses/LICENSE.txt","licenses/THIRD_PARTY_NOTICES.md","licenses/NEU-LICENSE.txt","licenses/skyzh-LICENSE.txt","licenses/wiki-NOTICE.txt").contains(name);
    }
    public static TranslationManager extract(Path zip,Path stage,DictionaryVersion expected) throws IOException {
        if(Files.size(zip)>MAX_ZIP_BYTES)throw new IOException("Dictionary ZIP too large");
        Files.createDirectory(stage);
        try(var archive=new ZipFile(zip.toFile())) {
            var manifest=archive.getEntry(MANIFEST);
            if(manifest==null)throw new IOException("Missing dictionary manifest");
            JsonObject description;
            try(var in=archive.getInputStream(manifest)){description=object(bounded(in,2*1024*1024));}
            if(description.get("schemaVersion").getAsInt()!=1 || !description.get("kind").getAsString().equals("full") || !description.get("version").getAsString().equals(expected.toString()))throw new IOException("Unexpected dictionary manifest version");
            var hashes=description.getAsJsonObject("files");
            if(hashes==null || hashes.size()>20000)throw new IOException("Invalid dictionary file count");
            for(var row:hashes.entrySet())if(!payload(row.getKey()) || !row.getValue().getAsString().matches("[0-9a-f]{64}"))throw new IOException("Invalid dictionary payload declaration");
            for(String name:REQUIRED)if(!hashes.has("translations/"+name+".json"))throw new IOException("Incomplete dictionary: "+name);
            if(hashes.keySet().stream().noneMatch(s->s.startsWith("translations/items/")))throw new IOException("Missing item dictionary");
            var seen=new HashSet<String>();long total=0;int count=0;var entries=archive.entries();
            while(entries.hasMoreElements()) {
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Update interrupted");
                var entry=entries.nextElement();String name=entry.getName();
                if(!seen.add(name.toLowerCase(Locale.ROOT)))throw new IOException("Duplicate ZIP entry");
                if(name.equals(MANIFEST))continue;
                if(!payload(name) || !hashes.has(name) || entry.isDirectory())throw new IOException("Unexpected ZIP entry");
                byte[] bytes;try(var in=archive.getInputStream(entry)){bytes=bounded(in,8*1024*1024);}
                total+=bytes.length;if(total>MAX_TOTAL_BYTES)throw new IOException("Expanded dictionary too large");
                if(!sha256(bytes).equals(hashes.get(name).getAsString()))throw new IOException("Dictionary file checksum mismatch");
                if(name.endsWith(".json"))validate(bytes);
                Path target=stage.resolve(name).normalize();if(!target.startsWith(stage))throw new IOException("Unsafe ZIP path");
                Files.createDirectories(target.getParent());Files.write(target,bytes,StandardOpenOption.CREATE_NEW);count++;
            }
            if(count!=hashes.size())throw new IOException("Missing dictionary payload");
            var manager=new TranslationManager();manager.load(stage);return manager;
        }catch(RuntimeException e){throw new IOException("Invalid dictionary data",e);}
    }
    private static byte[] bounded(InputStream input,int limit) throws IOException {
        byte[] bytes=input.readNBytes(limit+1);if(bytes.length>limit)throw new IOException("Dictionary entry too large");return bytes;
    }
    // Validate depth and strict JSON before Gson builds its tree or translation indexes.
    private static JsonObject object(byte[] bytes) throws IOException {
        return JsonParser.parseString(validate(bytes)).getAsJsonObject();
    }
    private static String validate(byte[] bytes) throws IOException {
        String text=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        try(var reader=new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            if(reader.peek()!=JsonToken.BEGIN_OBJECT)throw new IOException("Dictionary JSON must be an object");
            value(reader,0);
            if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing dictionary data");
        }
        return text;
    }
    private static void value(JsonReader reader,int depth) throws IOException {
        if(depth>64)throw new IOException("Dictionary nesting too deep");
        switch(reader.peek()) {
            case BEGIN_OBJECT -> {reader.beginObject();var names=new HashSet<String>();while(reader.hasNext()){if(!names.add(reader.nextName()))throw new IOException("Duplicate JSON key");value(reader,depth+1);}reader.endObject();}
            case BEGIN_ARRAY -> {reader.beginArray();while(reader.hasNext())value(reader,depth+1);reader.endArray();}
            case STRING,NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IOException("Invalid JSON value");
        }
    }
}
