package com.sorakoi.sbtranslate.llm;
import com.sorakoi.sbtranslate.translation.TranslationManager;
import java.util.*;
import java.util.regex.*;

/** Resolve model references through the live, reviewed dictionary, never invented names. */
public final class HelpNames {
    private static final Pattern TOKEN=Pattern.compile("\\[\\[([^\\[\\]\\r\\n]{1,160})]]");
    public static String render(String text,TranslationManager manager){
        text=text.replaceAll("(?i)\\bFestia Flask\\b","Fiesta Flask");
        Matcher m=TOKEN.matcher(text);StringBuilder result=new StringBuilder();
        while(m.find()) {
            String en=m.group(1).equals("Festia Flask")?"Fiesta Flask":m.group(1);
            String zh=manager.knownItemName(en);
            m.appendReplacement(result,Matcher.quoteReplacement(zh==null || zh.equals(en)?en:zh+"（"+en+"）"));
        }
        String rendered=m.appendTail(result).toString();
        // Handle a provider that omits [[...]]. Protect already-normalized references.
        var protectedNames=new ArrayList<String>();
        var references=Pattern.compile("([^\\s，。；：！？（）]{1,80})（([^（）\\r\\n]{1,160})）").matcher(rendered);
        var masked=new StringBuilder();
        while(references.find()){
            String en=references.group(2),zh=manager.knownItemName(en),prefix=references.group(1);
            if(zh==null || !prefix.endsWith(zh))continue;
            protectedNames.add(zh+"（"+en+"）");
            references.appendReplacement(masked,Matcher.quoteReplacement(prefix.substring(0,prefix.length()-zh.length())+"\u0001"+(protectedNames.size()-1)+"\u0002"));
        }
        rendered=references.appendTail(masked).toString();
        var names=new HashMap<String,String>();var ambiguous=new HashSet<String>();
        for(var item:manager.itemEntries()){
            String en=item.originalName(),zh=item.name();
            if(en.isBlank() || zh.isBlank() || en.equals(zh))continue;
            String label=zh+"（"+en+"）";
            if(rendered.toLowerCase(Locale.ROOT).contains(en.toLowerCase(Locale.ROOT)))names.put(en,label);
            if(zh.length()>=3 && rendered.contains(zh)){
                String old=names.putIfAbsent(zh,label);if(old!=null && !old.equals(label))ambiguous.add(zh);
            }
        }
        ambiguous.forEach(names::remove);
        if(!names.isEmpty()){
            String pattern=names.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed())
                .map(n->n.matches(".*[A-Za-z].*")?"(?<![A-Za-z0-9_])"+Pattern.quote(n)+"(?![A-Za-z0-9_])":Pattern.quote(n))
                .collect(java.util.stream.Collectors.joining("|"));
            var lookup=new TreeMap<String,String>(String.CASE_INSENSITIVE_ORDER);lookup.putAll(names);
            var match=Pattern.compile(pattern,Pattern.CASE_INSENSITIVE).matcher(rendered);var out=new StringBuilder();
            while(match.find())match.appendReplacement(out,Matcher.quoteReplacement(lookup.get(match.group())));
            rendered=match.appendTail(out).toString();
        }
        for(int i=0;i<protectedNames.size();i++)rendered=rendered.replace("\u0001"+i+"\u0002",protectedNames.get(i));
        for(var item:manager.itemEntries()){
            String en=item.originalName(),zh=item.name(),label=zh+"（"+en+"）";
            if(!rendered.contains(label))continue;
            for(int i=0;i<3;i++)rendered=rendered.replace(label+"（"+label+"）",label)
                .replace(zh+"（"+label+"）",label).replace(label+"（"+en+"）",label)
                .replace(en+"（"+label+"）",label);
        }
        return rendered;
    }
    public static Map<String,String> glossary(String text,TranslationManager manager){
        Map<String,String> result=new TreeMap<>();String low=text.toLowerCase(Locale.ROOT);
        for(var item:manager.itemEntries()) {
            if(result.size()>=100)break;
            if(item.originalName().length()>2 && (low.contains(item.originalName().toLowerCase(Locale.ROOT)) || text.contains(item.name())))result.put(item.originalName(),item.name());
        }
        return result;
    }
}
