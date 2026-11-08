package com.sorakoi.sbtranslate.mixin.compat;
import com.sorakoi.sbtranslate.compat.OdinCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(GuiGraphicsExtractor.class)
public abstract class OdinGraphicsMixin {
    @ModifyVariable(method="text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",at=@At("HEAD"),argsOnly=true)
    private String sapphire$text(String text){return OdinCompat.scoped()?OdinCompat.text(text):text;}
    @ModifyVariable(method="text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",at=@At("HEAD"),argsOnly=true)
    private Component sapphire$component(Component text){return OdinCompat.scoped()?OdinCompat.text(text):text;}
}
