package com.sorakoi.sbtranslate.mixin.compat;
import com.sorakoi.sbtranslate.compat.OdinCompat;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(Font.class)
public abstract class OdinFontMixin {
    @ModifyVariable(method="width(Ljava/lang/String;)I",at=@At("HEAD"),argsOnly=true)
    private String sapphire$width(String text){return OdinCompat.scoped()?OdinCompat.text(text):text;}
    @ModifyVariable(method={"width(Lnet/minecraft/network/chat/FormattedText;)I","split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"},at=@At("HEAD"),argsOnly=true)
    private FormattedText sapphire$formatted(FormattedText text){return OdinCompat.scoped() && text instanceof Component c?OdinCompat.text(c):text;}
}
