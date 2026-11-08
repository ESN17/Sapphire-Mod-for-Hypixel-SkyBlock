package com.sorakoi.sbtranslate.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sorakoi.sbtranslate.compat.SkyHanniCompat;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="at.hannibal2.skyhanni.utils.renderables.primitives.StringRenderable",remap=false)
public abstract class SkyHanniStringRenderableMixin {
    @Shadow public abstract String getText();
    @Shadow public abstract double getScale();
    @Unique private String sapphire$lastText;
    @Unique private int sapphire$width;
    @Inject(method="getWidth()I",at=@At("HEAD"),cancellable=true,require=0)
    private void sapphire$width(CallbackInfoReturnable<Integer> cir) {
        String text=SkyHanniCompat.display(getText());if(text.equals(getText()))return;
        if(!text.equals(sapphire$lastText)) {
            sapphire$lastText=text;sapphire$width=(int)(Minecraft.getInstance().font.width(text)*getScale())+1;
        }
        cir.setReturnValue(sapphire$width);
    }
    @ModifyExpressionValue(method="render(II)V",at=@At(value="FIELD",target="Lat/hannibal2/skyhanni/utils/renderables/primitives/StringRenderable;text:Ljava/lang/String;"),require=0)
    private String sapphire$text(String original){return SkyHanniCompat.display(original);}
}
