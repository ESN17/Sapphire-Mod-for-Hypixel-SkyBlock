package com.sorakoi.sbtranslate.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sorakoi.sbtranslate.DisplayTranslationHooks;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Translate only render-time copies, before width calculation. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    // Verified in the 26.3 bytecode: this return value feeds both width and row drawing.
    @ModifyExpressionValue(method="extractRenderState",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/components/PlayerTabOverlay;getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;"))
    private Component sapphire$row(Component source,@com.llamalad7.mixinextras.sugar.Local net.minecraft.client.multiplayer.PlayerInfo info){
        Component translated=info.getTabListDisplayName()==null
            ?source:DisplayTranslationHooks.text(source,"tablist");
        return com.sorakoi.sbtranslate.client.SapphireRanks.tab(translated,info.getProfile().name());
    }
    @ModifyExpressionValue(method="extractRenderState",at=@At(value="FIELD",target="Lnet/minecraft/client/gui/components/PlayerTabOverlay;header:Lnet/minecraft/network/chat/Component;"))
    private Component sapphire$header(Component source){return DisplayTranslationHooks.text(source,"tablist");}
    @ModifyExpressionValue(method="extractRenderState",at=@At(value="FIELD",target="Lnet/minecraft/client/gui/components/PlayerTabOverlay;footer:Lnet/minecraft/network/chat/Component;"))
    private Component sapphire$footer(Component source){return DisplayTranslationHooks.text(source,"tablist");}
}
