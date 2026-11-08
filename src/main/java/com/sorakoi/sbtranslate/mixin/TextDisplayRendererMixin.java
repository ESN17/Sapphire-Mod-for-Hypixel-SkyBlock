package com.sorakoi.sbtranslate.mixin;

import com.sorakoi.sbtranslate.client.EntityNameDisplay;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisplayRenderer.TextDisplayRenderer.class)
public abstract class TextDisplayRendererMixin {
    @Shadow @Final private Font font;
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;F)V",at=@At("TAIL"))
    private void sapphire$text(Display.TextDisplay entity,TextDisplayEntityRenderState state,float tick,CallbackInfo ci) {
        state.cachedInfo=EntityNameDisplay.layout(state.textRenderState,state.cachedInfo,font);
    }
}
