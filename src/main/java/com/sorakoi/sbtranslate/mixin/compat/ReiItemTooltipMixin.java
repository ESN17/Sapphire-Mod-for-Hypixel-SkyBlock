package com.sorakoi.sbtranslate.mixin.compat;

import com.sorakoi.sbtranslate.DisplayTranslationHooks;
import com.sorakoi.sbtranslate.Sapphire;
import com.sorakoi.sbtranslate.translation.DisplayTranslationSafety;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.shedaniel.rei.plugin.client.entry.ItemEntryDefinition", remap = false)
public abstract class ReiItemTooltipMixin {
    @Inject(method = "tryGetItemStackToolTip(Lme/shedaniel/rei/api/common/entry/EntryStack;Lnet/minecraft/world/item/ItemStack;Lme/shedaniel/rei/api/client/gui/widgets/TooltipContext;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void sapphire$displayTooltip(@Coerce Object entry, ItemStack stack, @Coerce Object context,
                                         CallbackInfoReturnable<List<Component>> callback) {
        if (!(context instanceof ReiTooltipContextAccessor flags) || flags.sapphire$isSearch()
                || !Sapphire.active("item") || stack.isEmpty()) return;
        var original = callback.getReturnValue();
        if (original == null || original.isEmpty()) return;
        var translated = DisplayTranslationSafety.preserveOriginal("REI tooltip", original,
                () -> DisplayTranslationHooks.tooltip(stack, original));
        if (!original.equals(translated)) callback.setReturnValue(translated);
    }
}
