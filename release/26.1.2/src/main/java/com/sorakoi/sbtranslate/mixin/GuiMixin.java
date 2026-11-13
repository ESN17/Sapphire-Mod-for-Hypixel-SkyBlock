package com.sorakoi.sbtranslate.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sorakoi.sbtranslate.DisplayTranslationHooks;
import com.sorakoi.sbtranslate.item.ItemTranslationService;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Shadow private ItemStack lastToolHighlight;
    @ModifyExpressionValue(method="extractSelectedItemName",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;getHoverName()Lnet/minecraft/network/chat/Component;"))
    private Component sapphire$item(Component text){return ItemTranslationService.name(lastToolHighlight,text);}
    @ModifyExpressionValue(method="displayScoreboardSidebar",at=@At(value="INVOKE",target="Lnet/minecraft/world/scores/Objective;getDisplayName()Lnet/minecraft/network/chat/Component;"))
    private Component sapphire$scoreTitle(Component text){return DisplayTranslationHooks.text(text,"scoreboard");}
    @ModifyExpressionValue(method="lambda$displayScoreboardSidebar$1",at=@At(value="INVOKE",target="Lnet/minecraft/world/scores/PlayerTeam;formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"))
    private net.minecraft.network.chat.MutableComponent sapphire$scoreLine(net.minecraft.network.chat.MutableComponent text){return DisplayTranslationHooks.text(text,"scoreboard").copy();}
    @ModifyExpressionValue(method="extractOverlayMessage",at=@At(value="FIELD",target="Lnet/minecraft/client/gui/Gui;overlayMessageString:Lnet/minecraft/network/chat/Component;"))
    private Component sapphire$action(Component text){return DisplayTranslationHooks.text(text,"actionbar");}
    @ModifyExpressionValue(method="extractTitle",at=@At(value="FIELD",target="Lnet/minecraft/client/gui/Gui;title:Lnet/minecraft/network/chat/Component;"))
    private Component sapphire$title(Component text){return DisplayTranslationHooks.text(text,"title");}
    @ModifyExpressionValue(method="extractTitle",at=@At(value="FIELD",target="Lnet/minecraft/client/gui/Gui;subtitle:Lnet/minecraft/network/chat/Component;"))
    private Component sapphire$subtitle(Component text){return DisplayTranslationHooks.text(text,"subtitle");}
}
