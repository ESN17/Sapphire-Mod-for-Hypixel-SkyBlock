package com.sorakoi.sbtranslate.translation;

import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.regex.Pattern;

/** Structured Bazaar display rows. Values and glyphs remain copied styled spans. */
public final class BazaarTranslationEngine {
    private static final Pattern DECORATION=Pattern.compile("^[^\\p{L}\\p{N}\\[]*",Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern NAVIGATION=Pattern.compile("^(?:\\([1-9][0-9]{0,2}/[1-9][0-9]{0,2}\\) )?(.{1,128}) (➜|➔|→|➤) (.{1,128})$");
    private static final Pattern LEVEL_RANGE=Pattern.compile("^(.+) ([1-9][0-9]?)-([1-9][0-9]?)$");
    private static String translatedLabel(String label,GuiTranslationEngine.Menu menu) {
        String exact=menu.labels().get(label);
        if(exact!=null)return exact;
        var range=LEVEL_RANGE.matcher(label);
        // Range groups have no item ID. Require both actual book tiers instead
        // of treating an arbitrary trailing number as an enchantment.
        if(!range.matches() || Integer.parseInt(range.group(2))>Integer.parseInt(range.group(3)))return null;
        String base=range.group(1);
        if(!menu.labels().containsKey(base+" "+range.group(2)) || !menu.labels().containsKey(base+" "+range.group(3)))return null;
        String translated=menu.labels().get(base);
        return translated==null?null:translated+label.substring(base.length());
    }
    private static String navigationTail(String title,String tail,GuiTranslationEngine.Menu menu) {
        String exact=translatedLabel(tail,menu);
        if(exact!=null)return exact;
        // Server navigation titles can end mid-word at the title length limit.
        // Only recover a known label prefix at that boundary. Never guess the
        // missing tier: retain an ellipsis when several products share a prefix.
        if((title.length()!=31 && title.length()!=32) || tail.length()<3)return null;
        String common=null;int matches=0;
        for(var entry:menu.labels().entrySet())if(entry.getKey().startsWith(tail)) {
            matches++;
            if(common==null)common=entry.getValue();
            else {
                int n=0;while(n<common.length() && n<entry.getValue().length() && common.charAt(n)==entry.getValue().charAt(n))n++;
                common=common.substring(0,n);
            }
        }
        if(common==null || common.isBlank())return null;
        return matches==1?common:common.stripTrailing()+"…";
    }
    static String bareLabel(String name) {
        return DECORATION.matcher(TranslationManager.plain(name)).replaceFirst("").strip();
    }
    static String roman(int level) {
        var out=new StringBuilder();int[] values={100,90,50,40,10,9,5,4,1};String[] symbols={"C","XC","L","XL","X","IX","V","IV","I"};
        for(int i=0;i<values.length;i++)while(level>=values[i]){out.append(symbols[i]);level-=values[i];}
        return out.toString();
    }
    public static boolean navigation(String title,GuiTranslationEngine.Menu menu) {
        if(menu==null || !menu.id().equals("bazaar"))return false;
        var match=NAVIGATION.matcher(title);
        return match.matches() && translatedLabel(match.group(1),menu)!=null && navigationTail(title,match.group(3),menu)!=null;
    }
    public static Component title(Component source,GuiTranslationEngine.Menu menu) {
        var match=NAVIGATION.matcher(TranslationManager.plain(source.getString()));
        if(!match.matches() || !navigation(match.group(),menu))return source;
        Component result=TextTranslationEngine.rangeReplacement(source,match.start(3),match.end(3),navigationTail(match.group(),match.group(3),menu));
        return TextTranslationEngine.rangeReplacement(result,match.start(1),match.end(1),translatedLabel(match.group(1),menu));
    }
    private static Component label(Component source,int start,int end,GuiTranslationEngine.Menu menu) {
        String text=TranslationManager.plain(source.getString());
        String value=text.substring(start,end),bare=bareLabel(value),translated=translatedLabel(bare,menu);
        if(translated==null || translated.equals(bare))return source;
        int offset=value.indexOf(bare);
        var range=LEVEL_RANGE.matcher(bare);
        if(range.matches() && menu.labels().containsKey(range.group(1)))
            return TextTranslationEngine.rangeReplacement(source,start+offset,start+offset+range.group(1).length(),menu.labels().get(range.group(1)));
        // Copy enchantment levels with their own colors/boldness. A whole-name
        // replacement cannot safely flatten an ultimate-enchant colored prefix.
        var header=EnchantmentTranslationEngine.header(bare);
        if(header!=null && menu.labels().containsKey(header.name()))
            return TextTranslationEngine.rangeReplacement(source,start+offset,start+offset+header.name().length(),menu.labels().get(header.name()));
        return TextTranslationEngine.rangeReplacement(source,start+offset,start+offset+bare.length(),translated);
    }
    public static Component line(Component source,GuiTranslationEngine.Menu menu) {
        if(menu==null || !menu.id().equals("bazaar"))return source;
        String plain=TranslationManager.plain(source.getString());
        if(plain.length()>2048)return source;
        Component exact=label(source,0,plain.length(),menu);
        if(exact!=source)return exact;
        // Only explicitly reviewed rows have a product-name slot. Never replace
        // arbitrary substrings in a player name, search query or explanatory text.
        for(var rule:menu.paragraphs()) {
            var template=rule.template();int slot=template.variables.indexOf("name")+1;
            if(slot==0)continue;
            var match=template.pattern.matcher(plain);
            if(!match.matches())continue;
            var named=label(source,match.start(slot),match.end(slot),menu);
            var translated=template.apply(TranslationManager.plain(named.getString()));
            if(translated==null)return named;
            if(translated.text().equals(TranslationManager.plain(named.getString())))return named;
            return TextTranslationEngine.apply(named,new TranslationManager.Match(translated.rule(),translated.pieces(),rule.styleMappings()==null?Map.of():rule.styleMappings()));
        }
        return source;
    }
    public static Component systemMessage(Component source,TranslationManager manager) {
        String plain=TranslationManager.plain(source.getString());
        boolean prefixed=plain.startsWith("[Bazaar] ");
        // Unprefixed order-limit notices identify Bazaar explicitly. Other
        // unprefixed status messages use the reviewed exact chat dictionary.
        if(!prefixed && !(plain.startsWith("You ") && plain.contains("Bazaar")))return source;
        var menu=manager.guiMenus().stream().filter(m->m.id().equals("bazaar")).findFirst().orElse(null);
        if(menu==null)return source;
        if(prefixed) {var named=line(source,menu);if(named!=source)return named;}
        for(var rule:menu.paragraphs())if(rule.source().getFirst().startsWith("[Bazaar] ") ||
                (rule.source().getFirst().startsWith("You ") && rule.source().getFirst().contains("Bazaar"))) {
            var match=rule.template().apply(plain);
            if(match!=null)return TextTranslationEngine.apply(source,new TranslationManager.Match(match.rule(),match.pieces(),rule.styleMappings()==null?Map.of():rule.styleMappings()));
        }
        return source;
    }
}
