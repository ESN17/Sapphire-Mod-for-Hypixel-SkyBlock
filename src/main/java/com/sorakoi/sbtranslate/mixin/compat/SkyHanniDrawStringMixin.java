package com.sorakoi.sbtranslate.mixin.compat;

import com.sorakoi.sbtranslate.compat.SkyHanniCompat;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;

/** Verified against official SkyHanni 9.3.0. Only drawing arguments are replaced. */
@Pseudo
@Mixin(targets="at.hannibal2.skyhanni.utils.GuiRenderUtils",remap=false)
public abstract class SkyHanniDrawStringMixin {
    @ModifyVariable(method={"drawString(Ljava/lang/String;FFIZ)V","drawString(Ljava/lang/String;IIIZ)V",
        "drawStringCentered(Ljava/lang/String;FFZI)V","drawStringCenteredScaledMaxWidth(Ljava/lang/String;FFZII)V"},
        at=@At("HEAD"),argsOnly=true,ordinal=0,require=0)
    private String sapphire$drawString(String text){return SkyHanniCompat.display(text);}

    @ModifyVariable(method={"drawString(Lnet/minecraft/network/chat/Component;FFIZ)V",
        "drawString(Lnet/minecraft/network/chat/Component;IIIZ)V"},
        at=@At("HEAD"),argsOnly=true,ordinal=0,require=0)
    private Component sapphire$drawComponent(Component text){return SkyHanniCompat.display(text);}
}
