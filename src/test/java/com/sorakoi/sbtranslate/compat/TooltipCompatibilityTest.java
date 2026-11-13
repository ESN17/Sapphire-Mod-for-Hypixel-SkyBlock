package com.sorakoi.sbtranslate.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Either;
import net.minecraft.network.chat.*;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TooltipCompatibilityTest {
    @Test void keepPluginElementsAndOriginalComponentsThroughLayout() {
        var name=Component.literal("Item");
        var lore=Component.literal("Lore");
        var warning=Component.literal("Plugin warning");
        var image=new TooltipComponent(){};
        List<Either<FormattedText,TooltipComponent>> raw=List.of(Either.left(name),Either.right(image),Either.left(lore),Either.left(warning));
        var rendered=IcebergTooltip.display(List.of(name,lore),raw,lines->lines.stream().map(c->(Component)Component.literal("译"+c.getString())).toList());
        assertEquals("译Item",rendered.getFirst().left().orElseThrow().getString());
        assertEquals("译Lore",rendered.get(2).left().orElseThrow().getString());
        assertSame(raw.get(1),rendered.get(1));
        assertSame(raw.get(3),rendered.get(3));
        assertSame(name,raw.getFirst().left().orElseThrow());
        assertSame(raw,IcebergTooltip.display(List.of(name,lore),raw,lines->lines));
    }
    @Test void previewDoesNotRequireSellProtectionOverride() {
        assertEquals("检测到SkyCofl，Sapphire已经自动改为按住Alt查看物品英语原文。",SkyCoflCompat.joinNotice(false));
        assertTrue(SkyCoflCompat.joinNotice(true).contains("按住Alt查看中文翻译"));
        for(String name:List.of("Sell Instantly","Sell Inventory Now","Sell Sacks Now"))assertTrue(SkyCoflCompat.sellButton(name));
        assertFalse(SkyCoflCompat.sellButton("Glacite Jewel"));
        assertTrue(SkyCoflCompat.previewKey(true,key->key==InputConstants.KEY_LALT));
        assertFalse(SkyCoflCompat.previewKey(true,key->key==InputConstants.KEY_LCONTROL));
        assertTrue(SkyCoflCompat.previewKey(false,key->key==InputConstants.KEY_RCONTROL));
    }
}
