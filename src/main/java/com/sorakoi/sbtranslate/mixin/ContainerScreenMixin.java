package com.sorakoi.sbtranslate.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sorakoi.sbtranslate.DisplayTranslationHooks;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
    @ModifyExpressionValue(method="extractTooltip",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;getTooltipFromContainerItem(Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;"))
    private java.util.List<Component> sapphire$tooltip(java.util.List<Component> source,
            @com.llamalad7.mixinextras.sugar.Local net.minecraft.world.item.ItemStack stack) {
        return DisplayTranslationHooks.tooltip(stack,source);
    }
    @ModifyExpressionValue(method="extractLabels",at=@At(value="FIELD",target="Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;title:Lnet/minecraft/network/chat/Component;"))
    private Component sapphire$title(Component text){return DisplayTranslationHooks.text(text,"gui");}
}
