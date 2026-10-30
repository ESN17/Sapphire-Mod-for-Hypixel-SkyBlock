package com.sorakoi.sbtranslate.translation;

import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.regex.Pattern;

/** Translates labels around live upgrade data, copying the numeric and decorative spans. */
public final class TooltipTranslationEngine {
    private static final Pattern STAT = Pattern.compile("^([A-Za-z][A-Za-z ']+):[ \\t]*([+-]?[0-9][0-9.,+%()\\[\\] \\t\\-kKmMbB]*)(?: (Coins))?$");
    private static final Pattern RARITY = Pattern.compile("^[^A-Za-z0-9]*(?:a )?((?:SHINY )?(?:VERY SPECIAL|COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|ULTIMATE)(?: [A-Z]+)*)(?: a)?[^A-Za-z0-9]*$");
    private static final Pattern BOUND = Pattern.compile("^[^A-Za-z0-9]*(Co-op Soulbound|Soulbound)[^A-Za-z0-9]*$");
    private static final Pattern WORD = Pattern.compile("VERY SPECIAL|[A-Z]+");
    private record Replacement(int start,int end,String value) {}
    private static Component replace(Component source,List<Replacement> replacements) {
        Component result=source;
        for(int i=replacements.size()-1;i>=0;i--) {
            var r=replacements.get(i);
            result=TextTranslationEngine.rangeReplacement(result,r.start(),r.end(),r.value());
        }
        return result;
    }
    public static Component translate(Component source,TranslationManager manager) {
        String text=TranslationManager.plain(source.getString());
        if(text.isBlank() || text.length()>2048)return source;
        var labels=manager.scope("tooltip-labels");
        var stat=STAT.matcher(text);
        if(stat.matches() && labels.containsKey(stat.group(1))) {
            var changes=new ArrayList<Replacement>();
            changes.add(new Replacement(stat.start(1),stat.end(1),labels.get(stat.group(1))));
            if(stat.group(3)!=null)changes.add(new Replacement(stat.start(3),stat.end(3),labels.getOrDefault("Coins","Coins")));
            return replace(source,changes);
        }
        var rarity=RARITY.matcher(text);
        if(rarity.matches()) {
            var words=WORD.matcher(text); words.region(rarity.start(1),rarity.end(1));
            var changes=new ArrayList<Replacement>();
            while(words.find()) {
                String value=labels.get(words.group());
                if(value==null)return source; // Never infer a new item type.
                changes.add(new Replacement(words.start(),words.end(),value));
            }
            return replace(source,changes);
        }
        var bound=BOUND.matcher(text);
        if(bound.matches() && labels.containsKey(bound.group(1)))
            return TextTranslationEngine.rangeReplacement(source,bound.start(1),bound.end(1),labels.get(bound.group(1)));
        for(var entry:manager.scope("dyes").entrySet()) {
            String tail=entry.getKey()+" Dyed";
            if(text.endsWith(tail)) {
                int start=text.length()-tail.length();
                if(!text.substring(0,start).codePoints().anyMatch(Character::isLetterOrDigit))
                    return replace(source,List.of(new Replacement(start,start+entry.getKey().length(),entry.getValue()),
                        new Replacement(text.length()-4,text.length(),labels.getOrDefault("Dyed","Dyed"))));
            }
        }
        for(var entry:manager.scope("runes").entrySet()) {
            int start=text.indexOf(entry.getKey()),end=start+entry.getKey().length();
            if(start>=0 && !text.substring(0,start).codePoints().anyMatch(Character::isLetterOrDigit)
                && text.substring(end).matches(" [IVXLCDM]+| [0-9]+"))
                return TextTranslationEngine.rangeReplacement(source,start,end,entry.getValue());
        }
        if(text.endsWith(" Bonus")) {
            String prefix=text.substring(0,text.length()-6),translated=manager.reforge(prefix);
            if(translated!=null) {
                if(translated.endsWith("的"))translated=translated.substring(0,translated.length()-1);
                return replace(source,List.of(new Replacement(0,prefix.length(),translated),
                    new Replacement(prefix.length()+1,text.length(),labels.getOrDefault("Bonus","Bonus"))));
            }
        }
        return source;
    }
}
