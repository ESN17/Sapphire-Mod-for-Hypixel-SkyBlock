package com.sorakoi.sbtranslate.translation;

import net.minecraft.network.chat.Component;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

/** Structured display-only sidebar fields; server data, time and IDs stay intact. */
public final class SidebarTranslationEngine {
    private static final Pattern REAL_DATE=Pattern.compile("^(\\s*)([0-9]{1,2})/([0-9]{1,2})/([0-9]{2}|[0-9]{4})(?=\\s|$)(.*)$");
    private static final Pattern GAME_DATE=Pattern.compile("^(\\s*)([A-Za-z ]{3,30}) ([0-9]{1,2})(st|nd|rd|th)(\\s*)$");
    private static final Pattern LOCATION_PREFIX=Pattern.compile("^[\\s⏣ф\\uE067\\uE020]*");
    private static TranslationManager.Piece copy(String text,int start,int end) {
        return new TranslationManager.Piece(text.substring(start,end),start,end,true);
    }
    public static Component location(Component source,TranslationManager manager) {
        String raw=TranslationManager.plain(source.getString());if(raw.length()>256)return source;
        // Hypixel can insert invisible scoreboard filler inside a location name.
        StringBuilder clean=new StringBuilder();var positions=new ArrayList<Integer>();
        for(int i=0;i<raw.length();i++)if(raw.charAt(i)!='⚽' && Character.getType(raw.charAt(i))!=Character.FORMAT) {
            clean.append(raw.charAt(i));positions.add(i);
        }
        String text=clean.toString();var prefix=LOCATION_PREFIX.matcher(text);prefix.find();
        int start=prefix.end(),end=text.stripTrailing().length();if(start>=end)return source;
        String translated=manager.scope("locations").get(text.substring(start,end));
        if(translated==null) {
            // Location glyphs and uppercase area names differ across sidebar layouts.
            for(var entry:manager.scope("locations").entrySet()) {
                int offset=end-entry.getKey().length();if(offset<0)continue;
                String lead=text.substring(0,offset).strip();
                boolean label=lead.equals("Area:") || lead.equals("Location:");
                if((label || lead.codePoints().noneMatch(Character::isLetterOrDigit)) && text.regionMatches(true,offset,entry.getKey(),0,entry.getKey().length())) {
                    start=offset;translated=entry.getValue();break;
                }
            }
        }
        if(translated==null)return source;
        return TextTranslationEngine.rangeReplacement(source,positions.get(start),positions.get(end-1)+1,translated);
    }
    public static Component line(Component source,TranslationManager manager) {
        Component located=location(source,manager);if(located!=source)return located;
        String text=TranslationManager.plain(source.getString());if(text.length()>256)return source;
        var formats=manager.scope("sidebar-formats");
        var date=REAL_DATE.matcher(text);
        if(date.matches()) {
            int month=Integer.parseInt(date.group(2)),day=Integer.parseInt(date.group(3)),year=Integer.parseInt(date.group(4));
            if(date.group(4).length()==2)year+=2000;
            try {LocalDate.of(year,month,day);} catch(DateTimeException invalid) {return source;}
            if(!formats.keySet().containsAll(List.of("year","month","day")))return source;
            var fields=new ArrayList<TranslationManager.Piece>();
            if(date.start(2)>0)fields.add(copy(text,0,date.start(2)));
            fields.add(new TranslationManager.Piece(year+formats.get("year"),date.start(4),date.end(4),false));
            fields.add(new TranslationManager.Piece(month+formats.get("month"),date.start(2),date.end(2),false));
            fields.add(new TranslationManager.Piece(day+formats.get("day"),date.start(3),date.end(3),false));
            if(date.end(4)<text.length())fields.add(copy(text,date.end(4),text.length()));
            return TextTranslationEngine.structured(source,fields);
        }
        var game=GAME_DATE.matcher(text);
        if(game.matches()) {
            String month=manager.scope("skyblock-months").get(game.group(2));int day=Integer.parseInt(game.group(3));
            String suffix=day>=11 && day<=13?"th":switch(day%10){case 1->"st";case 2->"nd";case 3->"rd";default->"th";};
            if(month==null || day<1 || day>31 || !suffix.equals(game.group(4)) || !formats.containsKey("day"))return source;
            var fields=new ArrayList<TranslationManager.Piece>();
            if(game.start(2)>0)fields.add(copy(text,0,game.start(2)));
            fields.add(new TranslationManager.Piece(month,game.start(2),game.end(2),false));
            fields.add(new TranslationManager.Piece(day+formats.get("day"),game.start(3),game.end(3),false));
            if(game.end(4)<text.length())fields.add(copy(text,game.end(4),text.length()));
            return TextTranslationEngine.structured(source,fields);
        }
        // A reviewed label may have a separate directional arrow, with its own style.
        var label=Pattern.compile("^(\\s*)(Objective)(?=\\s|$).*$").matcher(text);
        if(label.matches()) {
            String translated=manager.scope("scoreboard").get(label.group(2));
            if(translated!=null)return TextTranslationEngine.rangeReplacement(source,label.start(2),label.end(2),translated);
        }
        return source;
    }
}
