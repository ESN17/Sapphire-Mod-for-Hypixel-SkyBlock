package com.sorakoi.sbtranslate.translation;

import java.util.*;
import java.util.regex.Pattern;

/** A small display-only matcher. Never searches item lore or community prose. */
public final class EntityNameTranslationEngine {
    private final Map<String,String> names,labels;
    private final Pattern nameplate;
    public EntityNameTranslationEngine(Map<String,String> names,Map<String,String> labels) {
        this.names=Map.copyOf(names);this.labels=Map.copyOf(labels);
        String alternatives=names.keySet().stream().filter(s->!s.isBlank())
            .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(s->s))
            .map(Pattern::quote).reduce((a,b)->a+"|"+b).orElse("(?!)");
        String modifiers=labels.keySet().stream().filter(s->s.endsWith(" "))
            .sorted(Comparator.comparingInt(String::length).reversed()).map(Pattern::quote)
            .reduce((a,b)->a+"|"+b).orElse("(?!)");
        String icons="[\\p{S}\\p{Co}✯✫✪✰✧✦★☆☠ ]*";
        nameplate=Pattern.compile("\\A(?<prefix>"+icons+"(?:\\[(?:Lv|Lvl) ?[0-9,]+\\] *)?"+icons+")"
            +"(?<modifier>(?:(?:"+modifiers+")){0,2}?)(?<name>"+alternatives+")"
            +"(?<suffix>(?: [IVXLCDM]{1,8})?"+icons+"(?:[0-9][0-9,.]*[kKmMbBtT]?(?:/[0-9][0-9,.]*[kKmMbBtT]?)?"+icons+")?)\\z");
    }
    public TranslationManager.Match match(String text) {
        if(text.length()>256)return null;
        String label=labels.get(text);
        if(label!=null)return new TranslationManager.Match("entity-label:"+text,
            TranslationManager.replacement("entity-label",text,label).pieces(),Map.of(text,label));
        var m=nameplate.matcher(text);if(!m.matches())return null;
        String original=m.group("name"),translated=names.get(original);
        if(original.equals(translated) && m.group("modifier").isEmpty())return null;
        var pieces=new ArrayList<TranslationManager.Piece>();var styles=new LinkedHashMap<String,String>();
        if(m.start("name")>0) {
            String prefix=text.substring(0,m.start("name"));
            String modifier=m.group("modifier");
            if(!modifier.isEmpty()) {
                int at=m.start("modifier");
                if(at>0)pieces.add(new TranslationManager.Piece(text.substring(0,at),0,at,true));
                String converted=modifier;
                for(var e:labels.entrySet())if(e.getKey().endsWith(" ") && converted.contains(e.getKey())) {
                    converted=converted.replace(e.getKey(),e.getValue());styles.put(e.getKey().strip(),e.getValue().strip());
                }
                pieces.add(new TranslationManager.Piece(converted,at,m.start("name"),false));
            } else pieces.add(new TranslationManager.Piece(prefix,0,m.start("name"),true));
        }
        pieces.add(new TranslationManager.Piece(translated,m.start("name"),m.end("name"),false));
        if(m.end("name")<text.length())pieces.add(new TranslationManager.Piece(text.substring(m.end("name")),m.end("name"),text.length(),true));
        styles.put(original,translated);
        return new TranslationManager.Match("entity-name:"+original,List.copyOf(pieces),Map.copyOf(styles));
    }
}
