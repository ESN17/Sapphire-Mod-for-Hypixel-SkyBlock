package com.sorakoi.sbtranslate.compat;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import java.util.*;

/** Optional display-only bridge. Bounded caches are invalidated on dictionary reload. */
public final class SkyHanniLiveTranslation {
    private TranslationManager owner;
    private long revision;
    private SkyHanniDictionary dictionary;
    private int hudScopes;
    private final Map<Component,Component> cache = new LinkedHashMap<>(64,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Component,Component> e){return size()>512;}
    };
    private final Map<String,String> strings = new LinkedHashMap<>(64,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,String> e){return size()>512;}
    };
    public synchronized Component translate(Component source, TranslationManager manager, SkyHanniDictionary dict) {
        return translate(source,manager,dict,0);
    }
    /** Display-only fallback; bit 1 = Tab, bit 2 = sidebar. Chat never uses these scopes. */
    public synchronized Component translate(Component source, TranslationManager manager, SkyHanniDictionary dict,int scopes) {
        if(source==null)return null;
        if(owner!=manager || revision!=manager.revision() || dictionary!=dict || hudScopes!=scopes) {
            cache.clear();strings.clear();owner=manager;revision=manager.revision();dictionary=dict;hudScopes=scopes;
        }
        var cached=cache.get(source);if(cached!=null)return cached.equals(source)?source:cached.copy();
        var resolved=SkyHanniDictionary.resolve(source);
        var result=SystemTranslationEngine.translate(resolved,"skyhanni",manager);
        if(result==resolved)result=TextTranslationEngine.padded(resolved,inner->{
            String english=inner.getString();
            String translated=manager.knownItemName(english);
            // SkyHanni abbreviates this prefix in trackers. Require an exact catalog hit.
            if(translated==null && english.startsWith("Ench "))translated=manager.knownItemName("Enchanted "+english.substring(5));
            if(translated==null)translated=manager.translatedName(english);
            if(translated!=null && !translated.equals(english))return TextTranslationEngine.apply(inner,
                new TranslationManager.Match("skyhanni-catalog",List.of(new TranslationManager.Piece(translated,0,0,false))));
            return inner;
        });
        if(result==resolved)result=TextTranslationEngine.padded(resolved,dict::translate);
        // SkyHanni's compact Tab and custom sidebar bypass the vanilla HUD entirely.
        // Reuse the same reviewed dictionaries, keeping the styled source as the cache key.
        if(result==resolved && (scopes&1)!=0)result=HudTranslationEngine.translate(resolved,"tablist",manager);
        if(result==resolved && (scopes&2)!=0)result=HudTranslationEngine.translate(resolved,"scoreboard",manager);
        if(result==resolved && !source.getSiblings().isEmpty()) {
            var copy=source.plainCopy().setStyle(source.getStyle());boolean changed=false;
            for(var child:source.getSiblings()){var next=translate(child,manager,dict,scopes);copy.append(next);changed|=!next.equals(child);}
            if(changed)result=copy;
        }
        if(result==resolved)result=source;
        cache.put(source.copy(),result.copy());return result;
    }
    public synchronized String translate(String source,TranslationManager manager,SkyHanniDictionary dict) {
        return translate(source,manager,dict,0);
    }
    public synchronized String translate(String source,TranslationManager manager,SkyHanniDictionary dict,int scopes) {
        if(source==null)return null;
        if(source.length()>4096)return source;
        if(owner==manager && revision==manager.revision() && dictionary==dict && hudScopes==scopes) {
            String cached=strings.get(source);if(cached!=null)return cached;
        }
        Component original=Component.literal(source), translated=translate(original,manager,dict,scopes);
        if(translated.getString().equals(SkyHanniDictionary.resolve(original).getString())){strings.put(source,source);return source;}
        // Renderable strings only support legacy formatting. Keep all supported styles.
        var output=new StringBuilder();boolean[] unsupported={false};
        translated.visit((style,text)->{
            if(text.isEmpty())return Optional.empty();
            output.append(ChatFormatting.RESET);
            if(style.getColor()!=null) {
                ChatFormatting color=null;
                for(var candidate:ChatFormatting.values())if(Objects.equals(TextColor.fromLegacyFormat(candidate),style.getColor())){color=candidate;break;}
                if(color==null){unsupported[0]=true;return Optional.empty();}
                output.append(color);
            }
            if(style.isBold())output.append(ChatFormatting.BOLD);
            if(style.isItalic())output.append(ChatFormatting.ITALIC);
            if(style.isUnderlined())output.append(ChatFormatting.UNDERLINE);
            if(style.isStrikethrough())output.append(ChatFormatting.STRIKETHROUGH);
            if(style.isObfuscated())output.append(ChatFormatting.OBFUSCATED);
            output.append(text);return Optional.empty();
        },Style.EMPTY);
        String result=unsupported[0]?source:output.toString();strings.put(source,result);return result;
    }
}
