package com.sorakoi.sbtranslate.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "me.shedaniel.rei.api.client.gui.widgets.TooltipContext", remap = false)
public interface ReiTooltipContextAccessor {
    @Invoker("isSearch")
    boolean sapphire$isSearch();
}
