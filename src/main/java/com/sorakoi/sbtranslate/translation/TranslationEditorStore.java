package com.sorakoi.sbtranslate.translation;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Edits one selected local translation, with backup and atomic replacement. */
public final class TranslationEditorStore {
    public record Entry(String file,String kind,int index,String id,String source,String target) {}
    public static final List<String> SCOPES=List.of("global","chat","scoreboard","tablist","gui","locations","enchantments","tooltip-labels","mining-events");
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static List<Entry> entries(Path root) throws IOException {
        var result=new ArrayList<Entry>();Path translations=root.resolve("translations");
        for(String scope:SCOPES) {
            Path file=translations.resolve(scope+".json");if(!Files.exists(file))continue;
            for(var e:TranslationManager.readObject(file).entrySet())result.add(new Entry(scope+".json","text",-1,scope,e.getKey(),e.getValue().getAsString()));
        }
        Path system=translations.resolve("system-messages.json");
        if(Files.exists(system)) {
            var rules=TranslationManager.readObject(system).getAsJsonArray("rules");
            for(int i=0;i<rules.size();i++) {
                var r=rules.get(i).getAsJsonObject();result.add(new Entry("system-messages.json","system",i,r.get("scopes").toString(),r.get("source").getAsString(),r.get("translation").getAsString()));
            }
        }
        try(var files=Files.list(translations.resolve("items"))) {
            for(Path file:files.filter(p->p.toString().endsWith(".json")).sorted().toList()) {
                var data=TranslationManager.readObject(file);String id=data.get("id").getAsString(),relative="items/"+file.getFileName();
                result.add(new Entry(relative,"name",-1,id,data.has("_original_name")?data.get("_original_name").getAsString():id,data.get("name").getAsString()));
                if(data.has("lore"))for(var e:data.getAsJsonObject("lore").entrySet())result.add(new Entry(relative,"lore",-1,id,e.getKey(),e.getValue().getAsString()));
                if(data.has("loreParagraphs")) {
                    var paragraphs=data.getAsJsonArray("loreParagraphs");
                    for(int i=0;i<paragraphs.size();i++) {
                        var p=paragraphs.get(i).getAsJsonObject();var lines=new ArrayList<String>();for(var line:p.getAsJsonArray("source"))lines.add(line.getAsString());
                        result.add(new Entry(relative,"paragraph",i,id,String.join("\n",lines),p.get("translation").getAsString()));
                    }
                }
            }
        }
        return List.copyOf(result);
    }
    public static void save(Path root,Entry entry,String target) throws IOException {
        if(target==null || target.isBlank() || target.length()>2048 || target.contains("§") || target.contains("\n") || target.contains("\r") || target.contains("\0"))throw new IOException("译文须为 1–2048 字且不含换行或格式代码");
        if(entry.source()==null || entry.source().isBlank() || entry.source().length()>2048 || entry.source().contains("\0") || entry.source().contains("§") || entry.kind().equals("text") && (entry.source().contains("\n") || entry.source().contains("\r")))throw new IOException("原文须为 1–2048 字且不含格式代码");
        try{new TranslationManager.Template(entry.source().replace('\n',' '),target);}catch(IllegalArgumentException e){throw new IOException("请保留原文中的全部变量，如 {value}");}
        Path translations=root.resolve("translations").toAbsolutePath().normalize(),path=translations.resolve(entry.file()).normalize();
        if(!path.startsWith(translations) || !(SCOPES.stream().anyMatch(s->entry.file().equals(s+".json")) || entry.file().equals("system-messages.json") || entry.file().matches("items/[A-Za-z0-9_;.-]+\\.json")))throw new IOException("无效词典路径");
        var data=Files.exists(path)?TranslationManager.readObject(path):new JsonObject();
        switch(entry.kind()) {
            case "name" -> data.addProperty("name",target);
            case "lore" -> {
                data.getAsJsonObject("lore").addProperty(entry.source(),target);
                if(data.has("loreParagraphs"))for(var e:data.getAsJsonArray("loreParagraphs")) {
                    var p=e.getAsJsonObject();var lines=p.getAsJsonArray("source");
                    if(lines.size()==1 && lines.get(0).getAsString().equals(entry.source())){p.addProperty("translation",target);p.addProperty("userEdited",true);prune(p,target);}
                }
            }
            case "system" -> {
                var r=data.getAsJsonArray("rules").get(entry.index()).getAsJsonObject();
                if(!r.get("source").getAsString().equals(entry.source()))throw new IOException("词典已变化，请重新搜索");
                r.addProperty("translation",target);prune(r,target);
            }
            case "paragraph" -> {
                var p=data.getAsJsonArray("loreParagraphs").get(entry.index()).getAsJsonObject();
                String actual=String.join("\n",p.getAsJsonArray("source").asList().stream().map(JsonElement::getAsString).toList());
                if(!actual.equals(entry.source()))throw new IOException("词典已变化，请重新搜索");
                p.addProperty("translation",target);
                p.addProperty("userEdited",true);
                prune(p,target);
            }
            case "text" -> data.addProperty(entry.source(),target);
            default -> throw new IOException("无效词条类型");
        }
        Files.createDirectories(path.getParent());
        if(Files.exists(path)) {
            Path backup=root.resolve("translation-edits").resolve(Long.toString(System.nanoTime())).resolve(entry.file());
            Files.createDirectories(backup.getParent());Files.copy(path,backup);
        }
        Path tmp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(tmp,JSON.toJson(data)+"\n");
        try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
    }
    private static void prune(JsonObject object,String target) {
        if(object.has("styleMappings"))object.getAsJsonObject("styleMappings").entrySet().removeIf(e->!target.contains(e.getValue().getAsString()));
    }
}
