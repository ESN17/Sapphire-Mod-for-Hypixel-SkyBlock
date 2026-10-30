package com.sorakoi.sbtranslate.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sorakoi.sbtranslate.DisplayTranslationHooks;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
    @ModifyExpressionValue(method="extractRenderState",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/components/LerpingBossEvent;getName()Lnet/minecraft/network/chat/Component;"))
    private Component sapphire$name(Component text){return DisplayTranslationHooks.text(text,"bossbar");}
}
