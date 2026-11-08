package com.sorakoi.sbtranslate.mixin.compat;
import com.sorakoi.sbtranslate.compat.OdinCompat;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
/** Odin-owned drawing helpers: text/measurement only, never stored settings or item metadata. */
@Pseudo
@Mixin(targets="com.odtheking.odin.utils.render.DrawContextUtilsKt",remap=false)
public abstract class OdinDrawContextMixin {
    @ModifyVariable(method={
        "text(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/lang/String;IILcom/odtheking/odin/utils/Color;Z)V",
        "textDim(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/lang/String;IILcom/odtheking/odin/utils/Color;Z)Lkotlin/Pair;",
        "getStringWidth(Ljava/lang/String;)I"},at=@At("HEAD"),argsOnly=true,ordinal=0,require=0)
    private static String sapphire$text(String text){return OdinCompat.text(text);}
}
