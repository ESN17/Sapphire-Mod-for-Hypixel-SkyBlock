package com.sorakoi.sbtranslate.llm;

import java.util.*;
import java.util.regex.Pattern;

/** Classifies message bodies and protects literal numbers, commands and names. */
public final class LlmText {
    public enum Kind { NPC, PLAYER, SYSTEM }
    public record Selection(Kind kind,String prefix,String body) {}
    private static final Pattern VANILLA_PLAYER=Pattern.compile("^(<[A-Za-z0-9_]{1,16}> )(.+)$",Pattern.DOTALL);
    private static final Pattern NPC=Pattern.compile("^(\\[NPC] [^:：]{1,64}[:：] ?)(.+)$",Pattern.DOTALL);
    private static final Pattern PLAYER=Pattern.compile("^((?:(?:Guild|Party|Co-op|Officer) > |(?:From|To) )?(?:\\[[^\\]\\r\\n]{1,24}\\] )?[A-Za-z0-9_]{1,16}(?: \\[[^\\]\\r\\n]{1,24}\\])?: )(.+)$",Pattern.DOTALL);
    private static final Pattern TOKEN=Pattern.compile("https?://\\S+|(?<!\\w)/[A-Za-z][A-Za-z0-9_:-]*|@[A-Za-z0-9_]+|[+-]?[0-9]+(?:[,.][0-9]+)*(?:[kKmMbBtT]|%)?|\\b[IVXLCDM]{2,8}\\b");
    public static Selection select(String text,boolean signedPlayer,LlmConfig c) {
        if(text==null || text.isBlank() || text.length()>1500 || text.contains("[Sapphire") || text.startsWith("[译]"))return null;
        Selection selected;var npc=NPC.matcher(text);var player=PLAYER.matcher(text);
        if(signedPlayer) {
            var vanilla=VANILLA_PLAYER.matcher(text);
            if(player.matches())selected=new Selection(Kind.PLAYER,player.group(1),player.group(2));
            else if(vanilla.matches())selected=new Selection(Kind.PLAYER,vanilla.group(1),vanilla.group(2));
            else selected=new Selection(Kind.PLAYER,"",text);
        }
        else if(npc.matches())selected=new Selection(Kind.NPC,npc.group(1),npc.group(2));
        else if(player.matches())selected=new Selection(Kind.PLAYER,player.group(1),player.group(2));
        else selected=new Selection(Kind.SYSTEM,"",text);
        if(!(switch(selected.kind()){case NPC->c.npc;case PLAYER->c.players;case SYSTEM->c.system;}))return null;
        // Do not spend API calls on numbers, symbols or Chinese-only lines.
        if(!Pattern.compile("[A-Za-z]{2}").matcher(selected.body()).find())return null;
        return selected;
    }
    public record Protected(String text,List<String> values) {
        public String restore(String translated) {
            if(translated==null || translated.length()>4096)return null;
            String unprotected=translated.replaceAll("⟦S[0-9]+⟧","");
            if(Pattern.compile("[0-9]|https?://|(?<!\\w)/[A-Za-z]").matcher(unprotected).find())return null;
            String result=translated;
            for(int i=0;i<values.size();i++) {
                String marker="⟦S"+i+"⟧";
                if(result.indexOf(marker)<0 || result.indexOf(marker)!=result.lastIndexOf(marker))return null;
                result=result.replace(marker,values.get(i));
            }
            if(result.contains("⟦") || result.contains("⟧"))return null;
            return result;
        }
    }
    public static Protected protect(String text,Collection<String> playerNames) {
        if(text.contains("⟦") || text.contains("⟧"))return null;
        var names=playerNames.stream().filter(s->s.matches("[A-Za-z0-9_]{1,16}") && text.contains(s)).sorted(Comparator.comparingInt(String::length).reversed()).map(Pattern::quote).toList();
        Pattern pattern=names.isEmpty()?TOKEN:Pattern.compile("\\b(?:"+String.join("|",names)+")\\b|"+TOKEN.pattern());
        var matcher=pattern.matcher(text);var values=new ArrayList<String>();var output=new StringBuilder();
        while(matcher.find()){values.add(matcher.group());matcher.appendReplacement(output,"⟦S"+(values.size()-1)+"⟧");}
        matcher.appendTail(output);return new Protected(output.toString(),List.copyOf(values));
    }
}
