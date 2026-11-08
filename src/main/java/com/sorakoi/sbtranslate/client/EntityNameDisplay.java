package com.sorakoi.sbtranslate.client;

import com.sorakoi.sbtranslate.Sapphire;
import com.sorakoi.sbtranslate.item.TooltipDisplayCache;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import java.util.*;

/** Bounded nameplate snapshots. No network, file I/O, or tooltip scans on render. */
public final class EntityNameDisplay {
    public static final TooltipDisplayCache CACHE=new TooltipDisplayCache(512);
    private record LayoutKey(Object dictionary,long revision,Font font,int width,List<List<TooltipDisplayCache.Run>> text) {}
    private static final Map<LayoutKey,Display.TextDisplay.CachedInfo> LAYOUTS=new LinkedHashMap<>(32,.75f,true);
    public static Component translate(Component source) {
        if(source==null || !Sapphire.active("entity"))return source;
        var translated=translate(source,Sapphire.TRANSLATIONS);
        var capture=com.sorakoi.sbtranslate.collection.DungeonCapture.INSTANCE;
        if(capture!=null)capture.observe(Sapphire.CONTEXT.dungeon(),"entity","",source.getString(),translated.getString());
        return Sapphire.originalMode(translated,source);
    }
    public static Component translate(Component source,TranslationManager manager) {
        if(source==null)return null;
        var snapshot=TooltipDisplayCache.snapshot(List.of(source));
        if(snapshot.getFirst().stream().mapToInt(r->r.text().length()).sum()>1024)return source;
        var key=new TooltipDisplayCache.Key(manager,manager.revision(),"entity","",0,0,null,snapshot);
        var hit=CACHE.get(key);if(hit!=null)return hit.isEmpty()?source:hit.getFirst();
        var resolved=TextTranslationEngine.resolvedDisplay(source);
        var translated=TextTranslationEngine.lines(resolved,line->TextTranslationEngine.padded(line,inner->{
            var named=TextTranslationEngine.apply(inner,manager.entityNames().match(TranslationManager.plain(inner.getString())));
            return named!=inner?named:SystemTranslationEngine.translate(inner,"entity",manager);
        }));
        boolean changed=translated!=resolved;
        CACHE.put(key,changed?List.of(translated):List.of());
        return changed?translated:source;
    }
    public static Display.TextDisplay.CachedInfo layout(Display.TextDisplay.TextRenderState state,
            Display.TextDisplay.CachedInfo original,Font font) {
        if(state==null || !Sapphire.active("entity"))return original;
        Component translated=translate(state.text());if(translated==state.text())return original;
        var manager=Sapphire.TRANSLATIONS;
        var key=new LayoutKey(manager,manager.revision(),font,state.lineWidth(),TooltipDisplayCache.snapshot(List.of(translated)));
        synchronized(LAYOUTS) {
            var hit=LAYOUTS.get(key);if(hit!=null)return hit;
            var lines=new ArrayList<Display.TextDisplay.CachedLine>();int width=0;
            for(var line:font.split(translated,state.lineWidth())) {
                int w=font.width(line);width=Math.max(width,w);lines.add(new Display.TextDisplay.CachedLine(line,w));
            }
            var result=new Display.TextDisplay.CachedInfo(List.copyOf(lines),width);LAYOUTS.put(key,result);
            while(LAYOUTS.size()>128)LAYOUTS.remove(LAYOUTS.keySet().iterator().next());
            return result;
        }
    }
    public static void clear(){CACHE.clear();synchronized(LAYOUTS){LAYOUTS.clear();}}
}
