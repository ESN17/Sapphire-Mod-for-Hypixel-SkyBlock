package com.sorakoi.sbtranslate.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sorakoi.sbtranslate.compat.OdinCompat;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="com.odtheking.odin.utils.ui.rendering.NVGRenderer",remap=false)
public abstract class OdinLegacyTextMixin {
    @Shadow private static long vg;

    @ModifyVariable(method={"text","textShadow","textWidth","drawWrappedString","wrappedTextBounds"},
        at=@At("HEAD"),argsOnly=true,ordinal=0,require=0)
    private String sapphire$label(String original){
        String translated=OdinCompat.text(original);
        return translated.equals(original)?original:com.sorakoi.sbtranslate.translation.TranslationManager.plain(translated);
    }

    @ModifyExpressionValue(method="getFontID",at=@At(value="INVOKE",
        target="Lorg/lwjgl/nanovg/NanoVG;nvgCreateFontMem(JLjava/lang/CharSequence;Ljava/nio/ByteBuffer;Z)I"),require=0)
    private int sapphire$font(int id){
        com.sorakoi.sbtranslate.compat.OdinLegacyFont.attach(vg,id);
        return id;
    }
}
