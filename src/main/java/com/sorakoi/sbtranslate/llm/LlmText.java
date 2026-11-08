package com.sorakoi.sbtranslate.llm;

import java.util.*;
import java.util.regex.Pattern;

/** Classifies message bodies and protects literal numbers, commands and names. */
public final class LlmText {
    public enum Kind { NPC, PLAYER, SYSTEM }
    private static final Pattern ENGLISH = Pattern.compile("[A-Za-z]{2}");
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern PLACEHOLDER = Pattern.compile("⟦S[0-9]+⟧");
    private static final Pattern UNPROTECTED = Pattern.compile("[0-9]|https?://|(?<!\\w)/[A-Za-z]");
    public record Selection(Kind kind,String prefix,String body) {}
    private static final Pattern VANILLA_PLAYER=Pattern.compile("^(<[A-Za-z0-9_]{1,16}> )(.+)$",Pattern.DOTALL);
    private static final Pattern NPC=Pattern.compile("^(\\[NPC] [^:：]{1,64}[:：] ?)(.+)$",Pattern.DOTALL);
    private static final String BADGE="[^\\p{IsLatin}\\p{IsHan}\\p{N}\\[\\]:\\r\\n ]{1,8}";
    private static final Pattern PLAYER=Pattern.compile("^((?:(?:Guild|Party|Co-op|Officer) > |(?:From|To) )?"
        +"(?:\\[[0-9]{1,6}\\] )?(?:"+BADGE+" )?(?:\\[[A-Z][A-Z0-9_+]{0,24}\\] )?"
        +"[A-Za-z0-9_]{1,16}(?: "+BADGE+")?(?: \\[[^\\]\\r\\n]{1,24}\\])?: )(.+)$",Pattern.DOTALL);
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
        if(!ENGLISH.matcher(selected.body()).find())return null;
        return selected;
    }
    public record Protected(String text,List<String> values,int firstId) {
        public Protected(String text,List<String> values){this(text,values,0);}
        public String restore(String translated) {
            if(translated==null || translated.length()>4096)return null;
            String unprotected=PLACEHOLDER.matcher(translated).replaceAll("");
            if(UNPROTECTED.matcher(unprotected).find())return null;
            String result=translated;
            for(int i=0;i<values.size();i++) {
                String marker="⟦S"+(i+firstId)+"⟧";
                if(result.indexOf(marker)<0 || result.indexOf(marker)!=result.lastIndexOf(marker))return null;
                result=result.replace(marker,values.get(i));
            }
            if(result.contains("⟦") || result.contains("⟧"))return null;
            return result;
        }
    }
    public static Protected protect(String text,Collection<String> playerNames) {
        return protect(text,playerNames,0);
    }
    static Protected protect(String text,Collection<String> playerNames,int firstId) {
        return protect(text,playerNames,List.of(),firstId);
    }
    static Protected protect(String text,Collection<String> playerNames,Collection<String> literals,int firstId) {
        if(text.contains("⟦") || text.contains("⟧"))return null;
        var names=playerNames.stream().filter(s->text.contains(s) && PLAYER_NAME.matcher(s).matches()).sorted(Comparator.comparingInt(String::length).reversed()).map(Pattern::quote).toList();
        String base=names.isEmpty()?TOKEN.pattern():"\\b(?:"+String.join("|",names)+")\\b|"+TOKEN.pattern();
        var extra=literals.stream().filter(s->!s.isEmpty() && text.contains(s)).distinct()
            .sorted(Comparator.comparingInt(String::length).reversed()).map(s->{
                String quoted=Pattern.quote(s);
                return s.matches("[A-Za-z0-9_].*[A-Za-z0-9_]|[A-Za-z0-9_]")?"(?<![A-Za-z0-9_])"+quoted+"(?![A-Za-z0-9_])":quoted;
            }).toList();
        Pattern pattern=extra.isEmpty() && names.isEmpty() ? TOKEN :
            Pattern.compile(extra.isEmpty()?base:String.join("|",extra)+"|"+base);
        var matcher=pattern.matcher(text);var values=new ArrayList<String>();var output=new StringBuilder();
        while(matcher.find()){values.add(matcher.group());matcher.appendReplacement(output,"⟦S"+(firstId+values.size()-1)+"⟧");}
        matcher.appendTail(output);return new Protected(output.toString(),List.copyOf(values),firstId);
    }
}
