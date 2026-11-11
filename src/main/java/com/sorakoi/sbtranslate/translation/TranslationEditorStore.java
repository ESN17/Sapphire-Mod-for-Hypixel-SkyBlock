package com.sorakoi.sbtranslate.translation;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Edits one selected local translation, with backup and atomic replacement. */
public final class TranslationEditorStore {
    public record Entry(String file,String kind,int index,String id,String source,String target) {}
    public static final List<String> SCOPES=List.of("global","chat","scoreboard","tablist","gui","locations","enchantments","tooltip-labels","mining-events","actionbar","title","subtitle","bossbar","templates","reforges","runes","dyes","name-modifiers","skyblock-months","sidebar-formats","bazaar-events","entity-names","entity-labels");
    private static final Set<String> STRUCTURED=Set.of("entity-menus.json","creature-messages.json","death-messages.json","system-messages.json","gui-menus.json","tooltip-paragraphs.json","community-sapphire.json","community-wiki.json");
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static synchronized List<Entry> entries(Path root) throws IOException {
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
        if(Files.isDirectory(translations.resolve("items")))try(var files=Files.list(translations.resolve("items"))) {
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
        for(String name:STRUCTURED)if(!name.equals("system-messages.json") && Files.isRegularFile(translations.resolve(name)))collect(TranslationManager.readObject(translations.resolve(name)),"",name,name,result);
        if(Files.isDirectory(translations.resolve("community-wiki")))try(var files=Files.list(translations.resolve("community-wiki"))) {
            for(Path file:files.filter(p->p.getFileName().toString().matches("[a-z0-9-]+\\.json")).sorted().toList())collect(TranslationManager.readObject(file),"","community-wiki/"+file.getFileName(),"Wiki",result);
        }
        return List.copyOf(result);
    }
    private static String escape(String key){return key.replace("~","~0").replace("/","~1");}
    private static String unescape(String key){return key.replace("~1","/").replace("~0","~");}
    private static String source(JsonElement source){return source.isJsonArray()?String.join("\n",source.getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList()):source.getAsString();}
    private static void collect(JsonElement value,String pointer,String file,String label,List<Entry> out) {
        if(value.isJsonArray()){int i=0;for(var child:value.getAsJsonArray())collect(child,pointer+"/"+i++,file,label,out);return;}
        if(!value.isJsonObject())return;var o=value.getAsJsonObject();
        String id=o.has("id")?o.get("id").getAsString():label;
        if(o.has("npc") && !o.get("npc").getAsString().isBlank())id+=" · "+o.get("npc").getAsString();
        if(o.has("scope"))id+=" · "+o.get("scope").getAsString();
        if(o.has("source") && o.has("translation")) {
            String from=o.has("numericSource")?"numericSource":"source",to=o.has("numericSource")?"numericTranslation":"translation";
            out.add(new Entry(file,"pointer:"+pointer+"/"+to,-1,id,source(o.get(from)),o.get(to).getAsString()));
        }
        for(String name:List.of("entries","rules","loreParagraphs","menus"))if(o.has(name))collect(o.get(name),pointer+"/"+name,file,id,out);
        if(o.has("titles"))for(var e:o.getAsJsonObject("titles").entrySet())out.add(new Entry(file,"pointer:"+pointer+"/titles/"+escape(e.getKey()),-1,id,e.getKey(),e.getValue().getAsString()));
    }
    private static Path checkedPath(Path root,String file) throws IOException {
        Path base=root.resolve("translations").toAbsolutePath().normalize(),path=base.resolve(file).normalize();
        boolean allowed=SCOPES.stream().anyMatch(s->file.equals(s+".json")) || STRUCTURED.contains(file) || file.matches("(?:items/[A-Za-z0-9_;.-]+|community-wiki/[a-z0-9-]+)\\.json");
        if(!allowed || !path.startsWith(base))throw new IOException("无效词典路径");
        for(Path p=path;p!=null && p.startsWith(base);p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("不允许编辑符号链接词典");
        return path;
    }
    private static JsonObject pointerParent(JsonObject data,String pointer) throws IOException {
        try{String[] parts=pointer.substring(1).split("/",-1);JsonElement current=data;for(int i=0;i<parts.length-1;i++)current=current.isJsonArray()?current.getAsJsonArray().get(Integer.parseInt(parts[i])):current.getAsJsonObject().get(unescape(parts[i]));return current.getAsJsonObject();}
        catch(RuntimeException e){throw new IOException("词典已变化，请重新搜索");}
    }
    /** Preview runs the same semantic style-preserving template renderer as in-game text. */
    public static synchronized net.minecraft.network.chat.Component preview(Path root,Entry entry,String target) throws IOException {
        var raw=net.minecraft.network.chat.Component.literal(entry.source().replace('\n',' ')).withColor(0xaaaaaa);
        var mappings=new HashMap<String,String>();
        if(entry.kind().startsWith("pointer:")) {
            var o=pointerParent(TranslationManager.readObject(checkedPath(root,entry.file())),entry.kind().substring(8));
            if(o.has("sampleRaw") && o.get("sampleRaw").isJsonPrimitive()) {
                String sample=o.get("sampleRaw").getAsString().replace('\n',' ');
                if(o.has("templateExamples"))for(var e:o.getAsJsonObject("templateExamples").entrySet())sample=sample.replace(e.getKey(),e.getValue().getAsString());
                raw=net.minecraft.network.chat.Component.literal(sample);
            }
            if(o.has("styleMappings"))o.getAsJsonObject("styleMappings").entrySet().forEach(e->{if(target.contains(e.getValue().getAsString()))mappings.put(e.getKey(),e.getValue().getAsString());});
        }
        var display=TextTranslationEngine.resolvedDisplay(raw);
        var match=new TranslationManager.Template(entry.source().replace('\n',' '),target).apply(TranslationManager.plain(display.getString()));
        if(match==null)return net.minecraft.network.chat.Component.literal(target).withColor(0xaaaaaa);
        return TextTranslationEngine.apply(display,new TranslationManager.Match(match.rule(),match.pieces(),mappings));
    }
    public static synchronized void save(Path root,Entry entry,String target) throws IOException {
        if(target==null || target.isBlank() || target.length()>2048 || target.contains("§") || target.contains("\n") || target.contains("\r") || target.contains("\0"))throw new IOException("译文须为 1–2048 字且不含换行或格式代码");
        if(entry.source()==null || entry.source().isBlank() || entry.source().length()>2048 || entry.source().contains("\0") || entry.source().contains("§") || entry.kind().equals("text") && (entry.source().contains("\n") || entry.source().contains("\r")))throw new IOException("原文须为 1–2048 字且不含格式代码");
        try{new TranslationManager.Template(entry.source().replace('\n',' '),target);}catch(IllegalArgumentException e){throw new IOException("请保留原文中的全部变量，如 {value}");}
        Path path=checkedPath(root,entry.file());
        var data=Files.exists(path)?TranslationManager.readObject(path):new JsonObject();
        if(entry.kind().startsWith("pointer:")) {
            // Re-enumerate this file to prevent arbitrary edits to IDs, attribution or metadata.
            var valid=new ArrayList<Entry>();collect(data,"",entry.file(),entry.file(),valid);
            if(valid.stream().noneMatch(e->e.kind().equals(entry.kind()) && e.source().equals(entry.source()) && e.target().equals(entry.target())))throw new IOException("词典已变化，请重新搜索");
            String pointer=entry.kind().substring(8),key=unescape(pointer.substring(pointer.lastIndexOf('/')+1));
            var parent=pointerParent(data,pointer);parent.addProperty(key,target);
            if(key.equals("numericTranslation")) {
                String original=parent.get("source").getAsString();
                var example=new TranslationManager.Template(entry.source(),target).apply(original);
                if(example==null && !original.equals(entry.source()))throw new IOException("动态模板与原文样例不匹配");
                parent.addProperty("translation",example==null?target:example.text());
            }
            if(key.equals("translation") || key.equals("numericTranslation")){parent.addProperty("userEdited",true);prune(parent,target);}
        }else {
        JsonElement old=switch(entry.kind()) {
            case "name" -> data.get("name");
            case "lore" -> data.getAsJsonObject("lore").get(entry.source());
            case "text" -> data.get(entry.source());
            case "system" -> data.getAsJsonArray("rules").get(entry.index()).getAsJsonObject().get("translation");
            case "paragraph" -> data.getAsJsonArray("loreParagraphs").get(entry.index()).getAsJsonObject().get("translation");
            default -> null;
        };
        if(old!=null && !old.getAsString().equals(entry.target()))throw new IOException("词典已变化或原文已存在，请重新搜索后修改");
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
