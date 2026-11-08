package com.sorakoi.sbtranslate.mixin;

import com.sorakoi.sbtranslate.client.EntityNameDisplay;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    // Verified against the 26.3 client: runs after subclass name extraction.
    // Change only this frame's render state, never Entity#getCustomName/data.
    @Inject(method="finalizeRenderState",at=@At("TAIL"))
    private void sapphire$nameplate(Entity entity,EntityRenderState state,CallbackInfo ci) {
        if(entity instanceof Player player)state.nameTag=com.sorakoi.sbtranslate.client.SapphireRanks.tab(state.nameTag,player.getGameProfile().name());
        else state.nameTag=EntityNameDisplay.translate(state.nameTag);
    }
}
