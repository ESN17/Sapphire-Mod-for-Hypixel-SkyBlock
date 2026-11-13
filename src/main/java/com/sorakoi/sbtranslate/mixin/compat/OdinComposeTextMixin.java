package com.sorakoi.sbtranslate.mixin.compat;

import com.sorakoi.sbtranslate.compat.OdinCompat;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** Odin may measure Compose labels before UiHost enters its render scope. */
@Pseudo
@Mixin(targets={"com.odtheking.odin.utils.ui.compose.LayoutsKt$Text$2$1",
    "com.odtheking.odin.utils.ui.compose.LayoutsKt$Text$3$1"},remap=false)
public abstract class OdinComposeTextMixin {
    @ModifyArg(method="invoke",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I"),index=0,require=0)
    private String sapphire$measure(String source){return OdinCompat.text(source);}
    @ModifyArg(method="invoke",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"),index=1,require=0)
    private String sapphire$draw(String source){return OdinCompat.text(source);}
}
