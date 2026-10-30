package com.sorakoi.sbtranslate.translation;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.network.chat.Component;

/** Full, scoped server-message templates; player names and numbers remain original spans. */
public final class SystemTranslationEngine {
    public record Rule(Set<String> scopes,TranslationManager.Template template,Map<String,String> styles) {}
    public static List<Rule> load(Path path) throws IOException {
        if(!Files.exists(path))return List.of();var out=new ArrayList<Rule>();
        for(var element:TranslationManager.readObject(path).getAsJsonArray("rules")) {
            var row=element.getAsJsonObject();var scopes=new HashSet<String>();
            for(var scope:row.getAsJsonArray("scopes"))scopes.add(scope.getAsString());
            Map<String,String> styles=new LinkedHashMap<>();
            if(row.has("styleMappings"))for(var entry:row.getAsJsonObject("styleMappings").entrySet())styles.put(entry.getKey(),entry.getValue().getAsString());
            out.add(new Rule(Set.copyOf(scopes),new TranslationManager.Template(row.get("source").getAsString(),row.get("translation").getAsString()),Map.copyOf(styles)));
        }
        out.sort(Comparator.comparingInt((Rule r)->r.template().specificity).reversed());return List.copyOf(out);
    }
    public static TranslationManager.Match match(String text,String scope,TranslationManager manager) {
        if(text.length()>2048)return null;
        return manager.systemMatch(scope,text,()->uncachedMatch(text,scope,manager));
    }
    private static TranslationManager.Match uncachedMatch(String text,String scope,TranslationManager manager) {
        for(var rule:manager.systemRules()) {
            if(!rule.scopes().contains(scope))continue;
            var match=rule.template().apply(text);if(match==null)continue;
            var matcher=rule.template().pattern.matcher(text);if(!matcher.matches())continue;
            var pieces=new ArrayList<TranslationManager.Piece>();
            var styles=new LinkedHashMap<>(rule.styles());
            for(var piece:match.pieces()) {
                String translated=null;
                if(piece.original())for(int i=0;i<rule.template().variables.size();i++)if(piece.start()==matcher.start(i+1)) {
                    String variable=rule.template().variables.get(i);
                    if(variable.equals("name"))translated=manager.translatedName(piece.text());
                    if(variable.equals("name") && translated==null && scope.equals("tablist"))translated=manager.scope("tablist").get(piece.text());
                    if(variable.equals("location"))translated=lookup(manager.scope("locations"),piece.text());
                    if(variable.equals("event"))translated=lookup(manager.scope("mining-events"),piece.text());
                }
                if(translated!=null) {
                    String from=piece.text(),to=translated;int leading=0;
                    while(leading<from.length() && leading<to.length() && from.charAt(leading)==to.charAt(leading) && !Character.isLetterOrDigit(from.charAt(leading)))leading++;
                    if(leading<from.length() && leading<to.length())styles.put(from.substring(leading),to.substring(leading));
                }
                pieces.add(translated==null?piece:new TranslationManager.Piece(translated,piece.start(),piece.end(),false));
            }
            return new TranslationManager.Match(match.rule(),List.copyOf(pieces),Map.copyOf(styles));
        }
        return null;
    }
    private static String lookup(Map<String,String> map,String key) {
        String exact=map.get(key);if(exact!=null)return exact;
        return map.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(key)).map(Map.Entry::getValue).findFirst().orElse(null);
    }
    public static Component translate(Component source,String scope,TranslationManager manager) {
        String plain=TranslationManager.plain(source.getString());
        // Trim padding without losing its style or offsets.
        int start=0,end=plain.length();while(start<end && Character.isWhitespace(plain.charAt(start)))start++;
        while(end>start && Character.isWhitespace(plain.charAt(end-1)))end--;
        var match=match(plain.substring(start,end),scope,manager);
        if(match!=null) {
            var pieces=new ArrayList<TranslationManager.Piece>();
            if(start>0)pieces.add(new TranslationManager.Piece(plain.substring(0,start),0,start,true));
            for(var p:match.pieces())pieces.add(new TranslationManager.Piece(p.text(),p.start()+start,p.end()+start,p.original()));
            if(end<plain.length())pieces.add(new TranslationManager.Piece(plain.substring(end),end,plain.length(),true));
            return TextTranslationEngine.apply(source,new TranslationManager.Match(match.rule(),pieces,match.styleMappings()));
        }
        return TextTranslationEngine.translate(source,text->match(text,scope,manager));
    }
}
