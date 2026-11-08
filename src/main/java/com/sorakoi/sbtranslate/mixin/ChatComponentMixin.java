package com.sorakoi.sbtranslate.mixin;

import com.sorakoi.sbtranslate.client.ChatDisplay;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Original history, signatures, events and parsing inputs remain unchanged. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @ModifyVariable(method="addMessageToDisplayQueue",at=@At("HEAD"),argsOnly=true)
    private GuiMessage sapphire$display(GuiMessage message){return ChatDisplay.display(message);}
    @WrapOperation(method="addMessageToDisplayQueue",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/chat/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"))
    private List<FormattedCharSequence> sapphire$split(GuiMessage message, Font font, int width,
            Operation<List<FormattedCharSequence>> original) {
        return ChatDisplay.splitLines(message, font, width, () -> original.call(message, font, width));
    }
    @Inject(method="addMessageToQueue",at=@At("RETURN"))
    private void sapphire$online(GuiMessage message,CallbackInfo ci){ChatDisplay.receive(message);}
}
