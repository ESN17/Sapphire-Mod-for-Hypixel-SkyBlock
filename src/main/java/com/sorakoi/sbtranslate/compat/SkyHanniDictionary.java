package com.sorakoi.sbtranslate.compat;

import com.google.gson.JsonParser;
import com.sorakoi.sbtranslate.translation.TextTranslationEngine;
import com.sorakoi.sbtranslate.translation.TranslationManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Independent, exact-match display dictionary. Never scans or edits config values. */
public final class SkyHanniDictionary {
    private final Map<String,String> exact;
    private final Map<String,String> plain;
    private final Map<String,List<Dynamic>> templates;
    private static final Pattern VARIABLE=Pattern.compile("\\{sh([0-5])}");
    public record Dynamic(String source,String translation,Pattern pattern,Pattern rawPattern,List<String> names) {
        public static Dynamic compile(String source,String translation) {
            String rawSource=source;
            source=TranslationManager.plain(source);
            var matcher=VARIABLE.matcher(source);var regex=new StringBuilder("^");var names=new ArrayList<String>();int end=0;
            while(matcher.find()) {
                if(names.isEmpty() && matcher.start()<8)throw new IllegalArgumentException("Dynamic prefix too short");
                regex.append(Pattern.quote(source.substring(end,matcher.start()))).append("([^\\n]{0,256}?)");
                names.add(matcher.group());end=matcher.end();
            }
            if(names.isEmpty() || names.size()>6 || new HashSet<>(names).size()!=names.size())throw new IllegalArgumentException("Invalid variables");
            var targetNames=new ArrayList<String>();var tm=VARIABLE.matcher(translation);while(tm.find())targetNames.add(tm.group());
            if(!names.equals(targetNames))throw new IllegalArgumentException("Changed variables");
            regex.append(Pattern.quote(source.substring(end))).append('$');
            var raw=new StringBuilder("^");var rm=VARIABLE.matcher(rawSource);end=0;
            while(rm.find()){raw.append(Pattern.quote(rawSource.substring(end,rm.start()))).append("([^\\n]{0,256}?)");end=rm.end();}
            raw.append(Pattern.quote(rawSource.substring(end))).append('$');
            return new Dynamic(source,translation,Pattern.compile(regex.toString()),Pattern.compile(raw.toString()),List.copyOf(names));
        }
    }
    private final Map<Component,Component> cache = new LinkedHashMap<>(64,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Component,Component> e) { return size()>512; }
    };
    public SkyHanniDictionary(Map<String,String> translations) {
        this(translations,List.of());
    }
    public SkyHanniDictionary(Map<String,String> translations,List<Dynamic> dynamic) {
        exact=Map.copyOf(translations);
        var index=new HashMap<String,String>();var conflicts=new HashSet<String>();
        translations.forEach((source,target)->{
            String key=TranslationManager.plain(source);
            String old=index.putIfAbsent(key,target);
            if(old!=null && !old.equals(target))conflicts.add(key);
        });
        conflicts.forEach(index::remove);plain=Map.copyOf(index);
        var grouped=new HashMap<String,List<Dynamic>>();
        var latest=new LinkedHashMap<String,Dynamic>();
        for(var entry:dynamic)latest.put(entry.rawPattern.pattern(),entry);
        for(var entry:latest.values())grouped.computeIfAbsent(entry.source.substring(0,8),k->new ArrayList<>()).add(entry);
        templates=Map.copyOf(grouped);
    }
    public static SkyHanniDictionary load(Path root) throws IOException {
        return load(root,"skyhanni-zh_cn.json");
    }
    public static SkyHanniDictionary load(Path root,String filename) throws IOException {
        var entries=new LinkedHashMap<String,String>();
        var dynamic=new ArrayList<Dynamic>();
        try(var in=SkyHanniDictionary.class.getResourceAsStream("/defaults/compat/"+filename)) {
            if(in!=null)read(new InputStreamReader(in,StandardCharsets.UTF_8),entries,dynamic);
        }
        // Data-only archive path remains accepted by older clients, which simply ignore it.
        Path official=root.resolve("translations/compat-"+filename.replace("_","-"));
        if(Files.isRegularFile(official))try(var reader=Files.newBufferedReader(official)){read(reader,entries,dynamic);}
        // User overrides remain separate from official dictionary downloads.
        Path custom=root.resolve("compat/"+filename);
        if(Files.isRegularFile(custom))try(var reader=Files.newBufferedReader(custom)){read(reader,entries,dynamic);}
        return new SkyHanniDictionary(entries,dynamic);
    }
    private static void read(Reader reader,Map<String,String> entries,List<Dynamic> dynamic) throws IOException {
        try {
            var root=JsonParser.parseReader(reader).getAsJsonObject();var json=root.getAsJsonObject("translations");
            for(var e:json.entrySet())if(e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isString())
                entries.put(e.getKey(),e.getValue().getAsString());
            if(root.has("templates"))for(var row:root.getAsJsonArray("templates")) {
                var entry=row.getAsJsonObject();
                dynamic.add(Dynamic.compile(entry.get("source").getAsString(),entry.get("translation").getAsString()));
            }
        }catch(RuntimeException e){throw new IOException("Invalid SkyHanni dictionary",e);}
    }
    public int size(){return exact.size();}
    public String translate(String text) {
        String found=lookup(text);if(!found.equals(text))return found;
        String normalized=TranslationManager.plain(text);
        if(normalized.length()<8 || normalized.length()>4096)return text;
        for(var rule:templates.getOrDefault(normalized.substring(0,8),List.of())) {
            var match=rule.rawPattern.matcher(text);if(!match.matches())continue;
            var variables=VARIABLE.matcher(rule.translation);var result=new StringBuilder();
            while(variables.find())variables.appendReplacement(result,Matcher.quoteReplacement(match.group(rule.names.indexOf(variables.group())+1)));
            variables.appendTail(result);return result.toString();
        }
        return text;
    }
    private String lookup(String text) {
        String found=exact.get(text);if(found!=null)return found;
        found=plain.get(text);return found==null?text:found;
    }
    public synchronized Component translate(Component source) {
        if(source==null)return null;
        Component cached=cache.get(source);if(cached!=null)return cached.copy();
        String original=source.getString();String target=lookup(original);
        if(target.equals(original)) {
            Component result=dynamic(source);
            if(result!=source)cache.put(source.copy(),result.copy());
            return result;
        }
        if(target.indexOf('§')<0) {
            Component result=TextTranslationEngine.apply(source,new TranslationManager.Match("skyhanni-exact",
                List.of(new TranslationManager.Piece(target,0,0,false))));
            if(result!=source)cache.put(source.copy(),result.copy());
            return result;
        }
        // A display copy inherits click/hover/font styling. Legacy formatting in
        // the reviewed dictionary is preserved, including resets and newlines.
        Style[] base={source.getStyle()};boolean[] first={true},eventsDiffer={false};
        source.visit((style,text)->{
            if(first[0] && !text.isEmpty()){base[0]=style;first[0]=false;}
            if(!Objects.equals(style.getClickEvent(),base[0].getClickEvent()) ||
               !Objects.equals(style.getHoverEvent(),base[0].getHoverEvent()))eventsDiffer[0]=true;
            return Optional.empty();
        },Style.EMPTY);
        if(eventsDiffer[0])return source; // Never merge distinct interactive links.
        Component result=resolve(Component.literal(target).setStyle(base[0]));
        cache.put(source.copy(),result.copy());return result;
    }
    private Component dynamic(Component original) {
        String text=TranslationManager.plain(original.getString());
        if(text.length()<8 || text.length()>4096)return original;
        for(var rule:templates.getOrDefault(text.substring(0,8),List.of())) {
            var match=rule.pattern.matcher(text);if(!match.matches())continue;
            var resolved=resolve(original);
            var output=Component.empty();var tokens=VARIABLE.matcher(rule.translation);int end=0;
            Style[] initial={resolved.getStyle()};boolean[] first={true};
            resolved.visit((s,t)->{if(first[0] && !t.isEmpty()){initial[0]=s;first[0]=false;}return Optional.empty();},Style.EMPTY);
            Style style=initial[0];
            while(tokens.find()) {
                Component literal=resolve(Component.literal(rule.translation.substring(end,tokens.start())).setStyle(style));
                output.append(literal);Style[] last={style};literal.visit((s,t)->{last[0]=s;return Optional.empty();},style);style=last[0];
                int group=rule.names.indexOf(tokens.group())+1;int start=match.start(group),stop=match.end(group);int[] cursor={0};
                resolved.visit((s,t)->{
                    int from=Math.max(0,start-cursor[0]),to=Math.min(t.length(),stop-cursor[0]);
                    if(from<to)output.append(Component.literal(t.substring(from,to)).setStyle(s));
                    cursor[0]+=t.length();return Optional.empty();
                },Style.EMPTY);
                end=tokens.end();
            }
            output.append(resolve(Component.literal(rule.translation.substring(end)).setStyle(style)));
            return output;
        }
        // Independently translate siblings only when no complete phrase matched.
        if(!original.getSiblings().isEmpty()) {
            var result=original.plainCopy();boolean changed=false;
            for(var child:original.getSiblings()){var translated=translate(child);changed|=translated!=child;result.append(translated);}
            if(changed)return result;
        }
        return original;
    }
    static Component resolve(Component source) {
        if(source.getString().indexOf('§')<0)return TextTranslationEngine.resolvedDisplay(source);
        var result=Component.empty();var chunk=new StringBuilder();Style[] current={null};
        net.minecraft.util.StringDecomposer.iterateFormatted(source,Style.EMPTY,(index,style,point)->{
            if(current[0]!=null && !current[0].equals(style) && !chunk.isEmpty()) {
                result.append(Component.literal(chunk.toString()).setStyle(current[0]));chunk.setLength(0);
            }
            current[0]=style;chunk.appendCodePoint(point);return true;
        });
        if(!chunk.isEmpty())result.append(Component.literal(chunk.toString()).setStyle(current[0]));
        return result;
    }
}
