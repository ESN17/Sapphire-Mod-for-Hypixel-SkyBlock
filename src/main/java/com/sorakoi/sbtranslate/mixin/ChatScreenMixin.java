package com.sorakoi.sbtranslate.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.sorakoi.sbtranslate.Sapphire;
import com.sorakoi.sbtranslate.client.ChatOriginalHover;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow private ChatComponent.DisplayMode displayMode;
    // Mouse IDs changed with SDL in 26.3 (1 is now LEFT, not RIGHT).
    // Use the platform constant and let native chat/widgets handle clicks first.
    @Inject(method="mouseClicked",at=@At("RETURN"),cancellable=true)
    private void sapphire$copy(MouseButtonEvent event,boolean doubleClick,CallbackInfoReturnable<Boolean> cir){
        if(cir.getReturnValueZ() || event.button()!=InputConstants.MOUSE_BUTTON_RIGHT || !Sapphire.CONFIG.chatCopyOriginal || !Sapphire.CONFIG.chatOriginalHover)return;
        var mc=Minecraft.getInstance();
        // Supply a synthetic click action only to the hit tester, never to actual chat styles.
        var finder=ChatOriginalHover.finder(mc.font,(int)event.x(),(int)event.y());
        mc.gui.hud.getChat().captureClickableText(finder,mc.getWindow().getGuiScaledHeight(),mc.gui.hud.getGuiTicks(),displayMode);
        String original=ChatOriginalHover.original(finder.result());
        if(original!=null){mc.keyboardHandler.setClipboard(original);cir.setReturnValue(true);}
    }
}
