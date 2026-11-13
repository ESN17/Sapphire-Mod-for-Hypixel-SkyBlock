package com.sorakoi.sbtranslate.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sorakoi.sbtranslate.compat.SboText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Changes display strings inside SBO's settings screen; leaves its settings, category IDs and inputs intact. */
@Pseudo
@Mixin(targets = "net.sbo.mod.config.ConfigGuiKt", remap = false)
public abstract class SboConfigGuiMixin {
    @ModifyArg(method = "*", at = @At(value = "INVOKE", target = "Lnet/sbo/guilib/core/dsl/NodeBuilder;unaryPlus(Ljava/lang/String;)V"), index = 0, require = 0)
    private static String sapphire$label(String text) { return SboText.display(text); }

    // Translate a paragraph before SBO splits it into individual lines.
    @ModifyVariable(method = "description(Lnet/sbo/guilib/core/dsl/NodeBuilder;Ljava/lang/String;Ljava/lang/String;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private static String sapphire$description(String text) { return SboText.display(text); }

    @ModifyExpressionValue(method = "*", require = 0, at = {
        @At(value = "CONSTANT", args = "stringValue=Search all settings…"),
        @At(value = "CONSTANT", args = "stringValue=Clear the search"),
        @At(value = "CONSTANT", args = "stringValue=Back"),
        @At(value = "CONSTANT", args = "stringValue=Close"),
        @At(value = "CONSTANT", args = "stringValue=Edit in a bigger field"),
        @At(value = "CONSTANT", args = "stringValue=Show it in its category"),
        @At(value = "CONSTANT", args = "stringValue=Show only General"),
        @At(value = "CONSTANT", args = "stringValue=Turn every new feature below on"),
        @At(value = "CONSTANT", args = "stringValue=Turn every new feature below off")
    })
    private static String sapphire$controlHint(String text) { return SboText.display(text); }
}
