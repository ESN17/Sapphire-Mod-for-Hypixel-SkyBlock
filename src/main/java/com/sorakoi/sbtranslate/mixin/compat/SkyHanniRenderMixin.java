package com.sorakoi.sbtranslate.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sorakoi.sbtranslate.compat.SkyHanniCompat;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets="at.hannibal2.skyhanni.deps.moulconfig.platform.MoulConfigRenderContext",remap=false)
public abstract class SkyHanniRenderMixin {
    @ModifyExpressionValue(method="drawString",at=@At(value="INVOKE",target="Lat/hannibal2/skyhanni/deps/moulconfig/platform/MoulConfigPlatform;unwrap(Lat/hannibal2/skyhanni/deps/moulconfig/common/text/StructuredText;)Lnet/minecraft/network/chat/Component;"),require=0)
    private Component sapphire$drawTranslation(Component original){return SkyHanniCompat.display(original);}
}
