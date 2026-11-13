package com.sorakoi.sbtranslate.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.datafixers.util.Either;
import com.sorakoi.sbtranslate.DisplayTranslationHooks;
import com.sorakoi.sbtranslate.Sapphire;
import com.sorakoi.sbtranslate.compat.IcebergTooltip;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import java.util.List;

@Pseudo
@Mixin(targets = "com.anthonyhilyard.iceberg.util.Tooltips", remap = false)
public abstract class IcebergTooltipMixin {
    @ModifyExpressionValue(method = "gatherTooltipComponents(Lnet/minecraft/world/item/ItemStack;Ljava/util/List;Ljava/util/Optional;IIILnet/minecraft/client/gui/Font;Lnet/minecraft/client/gui/Font;II)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Lcom/anthonyhilyard/iceberg/events/client/RenderTooltipEvents$GatherResult;tooltipElements()Ljava/util/List;"), require = 0)
    private static List<Either<FormattedText, TooltipComponent>> sapphire$displayGathered(
            List<Either<FormattedText, TooltipComponent>> gathered, @Local(argsOnly = true) ItemStack stack,
            @Local(argsOnly = true) List<? extends FormattedText> source,
            @Share("displayLines") LocalRef<List<Either<FormattedText, TooltipComponent>>> cache) {
        if (stack.isEmpty() || !(Sapphire.active("item") || Sapphire.active("gui"))) return gathered;
        if (cache.get() == null) cache.set(IcebergTooltip.display(source, gathered, lines -> DisplayTranslationHooks.tooltip(stack, lines)));
        return cache.get();
    }
}
