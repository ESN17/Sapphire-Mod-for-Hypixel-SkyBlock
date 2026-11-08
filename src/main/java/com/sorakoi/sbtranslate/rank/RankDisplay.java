package com.sorakoi.sbtranslate.rank;

import com.sorakoi.sbtranslate.translation.TextTranslationEngine;
import net.minecraft.network.chat.*;
import java.util.*;
import java.util.regex.*;

public final class RankDisplay {
    // Resolve the complete component before matching: ranks and names often span siblings.
    // A token scan + roster lookup is independent of the number of registered users.
    private static final Pattern USER=Pattern.compile("(?<![A-Za-z0-9_])(?:(?<level>\\[[0-9]+\\])[ \\t]*)?(?<rank>(?:\\[[A-Z][A-Z0-9_+]{0,24}\\][ \\t]*)?)(?<name>[A-Za-z0-9_]{1,16})(?![A-Za-z0-9_])");
    public static final int SAPPHIRE_BRACKET=0x0000aa, SAPPHIRE_LABEL=0xffffff, SAPPHIRE_USERNAME=0x5555ff;
    private record Span(String text,Style style,int start,int end){}
    public static Component tab(Component source,String profileName,RankRoster roster) {
        // Hypixel may use a synthetic profile name unrelated to the visible player row.
        return text(source,roster);
    }
    public static Component chat(Component source,RankRoster roster) {
        return text(source,roster);
    }
    public static Component text(Component source,RankRoster roster) {
        if(source==null || roster.entries().isEmpty())return source;
        Component display=resolved(source);String text=display.getString();if(text.length()>32768)return source;
        var matcher=USER.matcher(text);List<Span> spans=null;var out=Component.empty();int cursor=0;
        while(matcher.find()) {
            var entry=roster.find(matcher.group("name"));if(entry==null)continue;
            // Visible commands/URLs and NPC labels are not player identity labels.
            int wordStart=matcher.start("name"),wordEnd=matcher.end("name");
            while(wordStart>0 && !Character.isWhitespace(text.charAt(wordStart-1)))wordStart--;
            while(wordEnd<text.length() && !Character.isWhitespace(text.charAt(wordEnd)))wordEnd++;
            String word=text.substring(wordStart,wordEnd);
            if(word.contains("/") || word.contains("@") || matcher.group("rank").strip().equals("[NPC]"))continue;
            if(spans==null)spans=spans(display);
            append(out,spans,cursor,matcher.start());
            if(matcher.start("level")>=0) {
                append(out,spans,matcher.start("level"),matcher.end("level"));
                append(out,spans,matcher.end("level"),matcher.start("rank"));
            }
            appendName(out,spans,text,matcher.start("name"),matcher.end("name"),entry);
            cursor=matcher.end();
        }
        if(spans==null)return source;
        append(out,spans,cursor,text.length());return out.equals(display)?source:out;
    }
    public static Component selfLevel(Component source,String username,RankRoster roster) {
        // SkyBlock levels belong to Hypixel's progression styling, not our rank.
        return source;
    }
    private static List<Span> spans(Component source) {
        var spans=new ArrayList<Span>();int[] offset={0};
        source.visit((style,text)->{spans.add(new Span(text,style,offset[0],offset[0]+text.length()));offset[0]+=text.length();return Optional.empty();},Style.EMPTY);
        return spans;
    }
    private static void appendName(MutableComponent out,List<Span> spans,String text,int nameStart,int end,RankRoster.Entry entry) {
        Style original=spans.stream().filter(s->s.start<=nameStart && s.end>nameStart).map(Span::style).findFirst().orElse(Style.EMPTY);
        Style clean=original.withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);
        if(entry.tag().equals("SAPPHIRE")) {
            out.append(Component.literal("[").setStyle(clean.withColor(SAPPHIRE_BRACKET)));
            out.append(Component.literal("SAPPHIRE").setStyle(clean.withColor(SAPPHIRE_LABEL)));
            out.append(Component.literal("] ").setStyle(clean.withColor(SAPPHIRE_BRACKET)));
            out.append(Component.literal(text.substring(nameStart,end)).setStyle(clean.withColor(SAPPHIRE_USERNAME)));
        } else {
            out.append(Component.literal("["+entry.tag()+"] ").setStyle(clean.withColor(entry.tagColor())));
            out.append(Component.literal(text.substring(nameStart,end)).setStyle(clean.withColor(entry.nameColor())));
        }
    }
    private static void append(MutableComponent out,List<Span> spans,int from,int to){for(var s:spans){int a=Math.max(from,s.start),b=Math.min(to,s.end);if(a<b)out.append(Component.literal(s.text.substring(a-s.start,b-s.start)).setStyle(s.style));}}
    private static Component resolved(Component source){
        if(source.getString().indexOf('§')<0)return TextTranslationEngine.resolvedDisplay(source);
        var out=Component.empty();var chunk=new StringBuilder();Style[] last={null};
        net.minecraft.util.StringDecomposer.iterateFormatted(source,Style.EMPTY,(index,style,point)->{
            if(last[0]!=null && !last[0].equals(style) && !chunk.isEmpty()){out.append(Component.literal(chunk.toString()).setStyle(last[0]));chunk.setLength(0);}
            last[0]=style;chunk.appendCodePoint(point);return true;
        });
        if(!chunk.isEmpty())out.append(Component.literal(chunk.toString()).setStyle(last[0]));return out;
    }
}
