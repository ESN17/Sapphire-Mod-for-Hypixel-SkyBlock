package com.sorakoi.sbtranslate.item;

import net.minecraft.network.chat.*;
import java.util.*;

/** Bounded display-only snapshots; keys include styles as well as visible text. */
public final class TooltipDisplayCache {
    public record Run(String text, Style style) {}
    public record Key(Object dictionary,long revision,String id,String menu,int options,int width,Object font,
                      List<List<Run>> source) {}
    private final LinkedHashMap<Key,List<Component>> values=new LinkedHashMap<>(32,.75f,true);
    private final int limit;
    private long hits,misses;
    private List<List<Run>> lastSnapshot=List.of();
    public TooltipDisplayCache(){this(128);}
    public TooltipDisplayCache(int limit){if(limit<1)throw new IllegalArgumentException();this.limit=limit;}
    public static List<List<Run>> snapshot(List<Component> lines) {
        var all=new ArrayList<List<Run>>(lines.size());
        for(var line:lines){var runs=new ArrayList<Run>();line.visit((style,text)->{runs.add(new Run(text,style));return Optional.empty();},Style.EMPTY);all.add(List.copyOf(runs));}
        return List.copyOf(all);
    }
    public synchronized List<List<Run>> displaySnapshot(List<Component> lines) {
        if(lines.size()==lastSnapshot.size()){
            boolean same=true;
            for(int i=0;i<lines.size() && same;i++){
                var runs=lastSnapshot.get(i);
                int[] cursor={0};
                var mismatch=lines.get(i).visit((style,text)->{
                    if(cursor[0]>=runs.size())return Optional.of(true);
                    var run=runs.get(cursor[0]++);
                    return run.text().equals(text) && run.style().equals(style)?Optional.empty():Optional.of(true);
                },Style.EMPTY);
                same=mismatch.isEmpty() && cursor[0]==runs.size();
            }
            if(same)return lastSnapshot;
        }
        lastSnapshot=snapshot(lines);
        return lastSnapshot;
    }
    public synchronized List<Component> get(Key key) {
        var value=values.get(key);
        // Resource reloads explicitly clear this cache; time alone does not invalidate a tooltip.
        if(value==null){misses++;return null;}
        hits++;var result=new ArrayList<Component>();
        for(var line:value)result.add(copy(line));
        return result;
    }
    public synchronized void put(Key key,List<Component> lines){
        if(lines.size()>128 || lines.stream().mapToInt(c->c.getString().length()).sum()>16384)return;
        values.put(key,lines.stream().map(TooltipDisplayCache::copy).toList());
        while(values.size()>limit)values.remove(values.keySet().iterator().next());
    }
    public synchronized void clear(){values.clear();lastSnapshot=List.of();}
    private static Component copy(Component source){var result=source.plainCopy().setStyle(source.getStyle());for(var sibling:source.getSiblings())result.append(copy(sibling));return result;}
    public synchronized long hits(){return hits;}
    public synchronized long misses(){return misses;}
    public synchronized int size(){return values.size();}
}
