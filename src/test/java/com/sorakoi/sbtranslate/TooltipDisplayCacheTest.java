package com.sorakoi.sbtranslate;
import com.sorakoi.sbtranslate.item.TooltipDisplayCache;
import com.sorakoi.sbtranslate.config.SapphireConfig;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
class TooltipDisplayCacheTest {
    @Test void repeatedHoverReusesRunsButDetectsInPlaceStyleAndTextChanges(){
        var cache=new TooltipDisplayCache();
        var line=Component.literal("Price").withColor(0xffffff);
        var first=cache.displaySnapshot(List.of(line));
        assertSame(first,cache.displaySnapshot(List.of(line.copy())));
        line.withColor(0xff5555);
        var recolored=cache.displaySnapshot(List.of(line));
        assertNotSame(first,recolored);
        line.append(" 10");
        assertNotSame(recolored,cache.displaySnapshot(List.of(line)));
    }
    private TooltipDisplayCache.Key key(Object dictionary,long revision,int width,Component source){return new TooltipDisplayCache.Key(dictionary,revision,"ID","Menu",7,width,null,TooltipDisplayCache.snapshot(List.of(source)));}
    @Test void cachesStyledDisplayCopiesWithoutSharingMutableComponents(){
        var cache=new TooltipDisplayCache();Object dictionary=new Object();
        var input=Component.literal("Damage: 100").withColor(0xff5555);var key=key(dictionary,1,300,input);
        var output=Component.literal("伤害: 100").withColor(0xff5555);cache.put(key,List.of(output));output.append("outside");
        var hit=cache.get(key);assertEquals("伤害: 100",hit.getFirst().getString());
        hit.getFirst().getSiblings().clear();assertEquals("伤害: 100",cache.get(key).getFirst().getString());
        assertNull(cache.get(key(dictionary,2,300,input)));assertNull(cache.get(key(dictionary,1,200,input)));
        assertNull(cache.get(key(dictionary,1,300,Component.literal("Damage: 100").withColor(0x55ff55))));
        assertNull(cache.get(key(dictionary,1,300,Component.literal("Damage: 200").withColor(0xff5555))));
        assertNull(cache.get(key(new Object(),1,300,input)));
    }
    @Test void boundedCacheSurvivesTimeAndClearsOnResourceReload(){
        var cache=new TooltipDisplayCache(2);Object d=new Object();
        for(int n=0;n<10;n++)cache.put(key(d,1,300,Component.literal("Price "+n)),List.of(Component.literal("价格 "+n)));
        assertEquals(2,cache.size());assertNotNull(cache.get(key(d,1,300,Component.literal("Price 9"))));
        cache.clear();assertNull(cache.get(key(d,1,300,Component.literal("Price 9"))));
    }
    @Test void ctrlModesPreserveExistingDefaultAndAllowEnglishDefault(){
        var c=new SapphireConfig();assertTrue(c.translateTooltip(false));assertFalse(c.translateTooltip(true));
        c.englishByDefault=true;assertFalse(c.translateTooltip(false));assertTrue(c.translateTooltip(true));
    }
}
