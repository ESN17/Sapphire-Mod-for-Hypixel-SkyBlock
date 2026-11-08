package com.sorakoi.sbtranslate.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sorakoi.sbtranslate.compat.SkyHanniCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="at.hannibal2.skyhanni.utils.renderables.primitives.TextRenderable",remap=false)
public abstract class SkyHanniTextRenderableMixin {
    @Shadow public abstract Component getText();
    @Shadow public abstract double getScale();
    @Unique private Component sapphire$lastText;
    @Unique private int sapphire$width;
    @Inject(method="getWidth()I",at=@At("HEAD"),cancellable=true,require=0)
    private void sapphire$width(CallbackInfoReturnable<Integer> cir) {
        Component text=SkyHanniCompat.display(getText());if(text==getText())return;
        if(!text.equals(sapphire$lastText)) {
            sapphire$lastText=text.copy();sapphire$width=(int)(Minecraft.getInstance().font.width(text)*getScale())+1;
        }
        cir.setReturnValue(sapphire$width);
    }
    @ModifyExpressionValue(method="render(II)V",at=@At(value="FIELD",target="Lat/hannibal2/skyhanni/utils/renderables/primitives/TextRenderable;text:Lnet/minecraft/network/chat/Component;"),require=0)
    private Component sapphire$text(Component original){return SkyHanniCompat.display(original);}
}
