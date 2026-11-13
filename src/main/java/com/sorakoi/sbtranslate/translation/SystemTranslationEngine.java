package com.sorakoi.sbtranslate.translation;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.network.chat.Component;

/** Full, scoped server-message templates; player names and numbers remain original spans. */
public final class SystemTranslationEngine {
    public record Rule(Set<String> scopes,TranslationManager.Template template,Map<String,String> styles,boolean wholeMessageOnly) {}
    public static List<Rule> load(Path path) throws IOException {
        if(!Files.exists(path))return List.of();
        return rules(TranslationManager.readObject(path));
    }
    static List<Rule> bundled(String name) throws IOException {
        try(var stream=SystemTranslationEngine.class.getResourceAsStream("/defaults/translations/"+name)){
            if(stream==null)return List.of();
            return rules(JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
        }
    }
    private static List<Rule> rules(JsonObject document){
        var out=new ArrayList<Rule>();
        boolean wholeMessageOnly=document.has("wholeMessageOnly") && document.get("wholeMessageOnly").getAsBoolean();
        for(var element:document.getAsJsonArray("rules")) {
            var row=element.getAsJsonObject();var scopes=new HashSet<String>();
            for(var scope:row.getAsJsonArray("scopes"))scopes.add(scope.getAsString());
            Map<String,String> styles=new LinkedHashMap<>();
            if(row.has("styleMappings"))for(var entry:row.getAsJsonObject("styleMappings").entrySet())styles.put(entry.getKey(),entry.getValue().getAsString());
            out.add(new Rule(Set.copyOf(scopes),new TranslationManager.Template(row.get("source").getAsString(),row.get("translation").getAsString()),Map.copyOf(styles),wholeMessageOnly));
        }
        out.sort(Comparator.comparingInt((Rule r)->r.template().specificity).reversed());return List.copyOf(out);
    }
    public static TranslationManager.Match match(String text,String scope,TranslationManager manager) {
        return match(text,scope,manager,true);
    }
    private static TranslationManager.Match match(String text,String scope,TranslationManager manager,boolean wholeMessage) {
        if(text.length()>2048)return null;
        return manager.systemMatch(scope+(wholeMessage?"":"/fragment"),text,()->uncachedMatch(text,scope,manager,wholeMessage));
    }
    private static TranslationManager.Match uncachedMatch(String text,String scope,TranslationManager manager,boolean wholeMessage) {
        if(scope.equals("chat") && text.startsWith("CHARM! You charmed the ")){
            var drop=uncachedMatch(text,"charm-drop",manager,wholeMessage);
            if(drop!=null)return drop;
        }
        for(var rule:manager.systemRules(scope)) {
            if(!wholeMessage && rule.wholeMessageOnly())continue;
            var match=rule.template().apply(text);if(match==null)continue;
            var matcher=rule.template().pattern.matcher(text);if(!matcher.matches())continue;
            var pieces=new ArrayList<TranslationManager.Piece>();
            var styles=new LinkedHashMap<>(rule.styles());
            for(var piece:match.pieces()) {
                String translated=null;
                boolean mob=false;
                if(piece.original())for(int i=0;i<rule.template().variables.size();i++)if(piece.start()==matcher.start(i+1)) {
                    String variable=rule.template().variables.get(i);
                    mob=variable.equals("mob");
                    if(variable.equals("name"))translated=manager.translatedName(piece.text());
                    if(variable.equals("fireSaleItem") || variable.equals("catalogItem"))translated=manager.knownItemName(piece.text());
                    if(variable.equals("shardReward")){
                        String reward=piece.text();
                        if(reward.endsWith(" Shards"))reward=reward.substring(0,reward.length()-1);
                        if(!reward.endsWith(" Shard") || (translated=manager.knownItemName(reward))==null)return null;
                    }
                    if(variable.equals("loadoutItem")){
                        String value=piece.text();
                        translated=value.equals("None")?"无":manager.knownItemName(value);
                        int space=value.indexOf(' ');
                        if(translated==null && space>0){
                            String reforge=manager.reforge(value.substring(0,space));
                            String item=manager.knownItemName(value.substring(space+1));
                            if(reforge!=null && item!=null)translated=reforge+item;
                        }
                    }
                    if(variable.equals("name") && translated==null && scope.equals("tablist"))translated=manager.scope("tablist").get(piece.text());
                    if(variable.equals("location"))translated=lookup(manager.scope("locations"),piece.text());
                    if(variable.equals("event"))translated=lookup(manager.scope("mining-events"),piece.text());
                }
                if(mob && appendMob(piece,manager.scope("entity-names"),pieces,styles))continue;
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
    // Only registered mob names are translated. Player names and NPC proper
    // names remain untouched; level/tier spans keep their individual styles.
    private static final java.util.regex.Pattern MOB_PARTS=java.util.regex.Pattern.compile(
        "(?<prefix>(?:\\[Lv(?:l)? ?[0-9,]+\\] )?)(?<name>.+?)(?<tier> [IVXLCDM]{1,8})?");
    private static boolean appendMob(TranslationManager.Piece piece,Map<String,String> names,
            List<TranslationManager.Piece> out,Map<String,String> styles) {
        var parts=MOB_PARTS.matcher(piece.text());if(!parts.matches())return false;
        int start=parts.start("name"),end=parts.end("name");
        String name=parts.group("name"),translated=names.get(name);
        // Some proper names end in a Roman letter (e.g. Barbarian Duke X).
        String complete=piece.text().substring(start);String exact=names.get(complete);
        if(exact!=null){name=complete;translated=exact;end=piece.text().length();}
        if(translated==null || name.equals(translated))return false;
        if(start>0)out.add(new TranslationManager.Piece(piece.text().substring(0,start),piece.start(),piece.start()+start,true));
        out.add(new TranslationManager.Piece(translated,piece.start()+start,piece.start()+end,false));
        if(end<piece.text().length())out.add(new TranslationManager.Piece(piece.text().substring(end),piece.start()+end,piece.end(),true));
        styles.put(name,translated);return true;
    }
    private static String lookup(Map<String,String> map,String key) {
        String exact=map.get(key);if(exact!=null)return exact;
        return map.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(key)).map(Map.Entry::getValue).findFirst().orElse(null);
    }
    public static Component translate(Component source,String scope,TranslationManager manager) {
        // Match and style against the same display snapshot. The renderer also
        // consumes unknown legacy codes (e.g. section-sign v), while plain()
        // removes only recognized codes. Resolving AFTER matching shifts every
        // subsequent capture and can crash chat restoration during server transfer.
        Component display=TextTranslationEngine.resolvedDisplay(source);
        Component translated=translateResolved(display,scope,manager);
        return translated==display?source:translated;
    }
    private static Component translateResolved(Component source,String scope,TranslationManager manager) {
        String plain=TranslationManager.plain(source.getString());
        // Trim padding without losing its style or offsets.
        int start=0,end=plain.length();while(start<end && Character.isWhitespace(plain.charAt(start)))start++;
        while(end>start && Character.isWhitespace(plain.charAt(end-1)))end--;
        if(start>0 || end<plain.length()) {
            // Style templates bind captures against the complete input. Applying
            // a trimmed template to padded text loses those bindings when two
            // values are equal (e.g. both Life blessing multipliers are 1.15).
            // Translate the actual inner component and copy styled padding back.
            Component display=TextTranslationEngine.resolvedDisplay(source);
            Component translated=TextTranslationEngine.padded(display,inner->translate(inner,scope,manager));
            return translated==display?source:translated;
        }
        var match=match(plain.substring(start,end),scope,manager);
        if(match!=null) {
            var pieces=new ArrayList<TranslationManager.Piece>();
            if(start>0)pieces.add(new TranslationManager.Piece(plain.substring(0,start),0,start,true));
            for(var p:match.pieces())pieces.add(new TranslationManager.Piece(p.text(),p.start()+start,p.end()+start,p.original()));
            if(end<plain.length())pieces.add(new TranslationManager.Piece(plain.substring(end),end,plain.length(),true));
            Component display=TextTranslationEngine.resolvedDisplay(source);
            Component translated=TextTranslationEngine.apply(display,new TranslationManager.Match(match.rule(),pieces,match.styleMappings()));
            return translated==display?source:translated;
        }
        // Keep explicit multi-line rules first (e.g. sea-creature announcements).
        // Otherwise round history and payouts can cross color siblings within
        // one packet: match complete visual lines rather than each sibling.
        if(scope.equals("chat") && plain.indexOf('\n')>=0 && plain.length()<=32768) {
            Component display=TextTranslationEngine.resolvedDisplay(source);
            Component translated=TextTranslationEngine.lines(display,line->translate(line,scope,manager));
            return translated==display?source:translated;
        }
        return TextTranslationEngine.translate(source,text->match(text,scope,manager,false));
    }
}
