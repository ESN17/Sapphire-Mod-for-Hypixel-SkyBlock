package com.sorakoi.sbtranslate.mixin.compat;

import com.sorakoi.sbtranslate.compat.SkyHanniCompat;
import net.minecraft.network.chat.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;

/** Measure the same display copy that GuiRenderUtils draws; getters remain English. */
@Pseudo
@Mixin(targets={"at.hannibal2.skyhanni.features.misc.compacttablist.TabLine",
    "at.hannibal2.skyhanni.features.misc.compacttablist.TabListRenderer"},remap=false)
public abstract class SkyHanniTabMeasureMixin {
    @ModifyArg(method={"getWidth()I","drawTabList()V"},at=@At(value="INVOKE",
        target="Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"),index=0,require=0)
    private FormattedText sapphire$width(FormattedText text){
        return text instanceof Component component?SkyHanniCompat.display(component):text;
    }
}
