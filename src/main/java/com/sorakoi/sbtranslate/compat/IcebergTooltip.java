package com.sorakoi.sbtranslate.compat;

import com.mojang.datafixers.util.Either;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import java.util.*;
import java.util.function.Function;

public final class IcebergTooltip {
    private IcebergTooltip() {}

    /** Only translate original item lines. Leave custom text, images and event ordering intact. */
    public static List<Either<FormattedText, TooltipComponent>> display(
            List<? extends FormattedText> source, List<Either<FormattedText, TooltipComponent>> gathered,
            Function<List<Component>, List<Component>> translate) {
        if (source.isEmpty() || !(source.getFirst() instanceof Component title)) return gathered;
        var originals = Collections.newSetFromMap(new IdentityHashMap<FormattedText, Boolean>());
        originals.addAll(source);
        var output = new ArrayList<Either<FormattedText, TooltipComponent>>();
        int index = 0;
        while (index < gathered.size()) {
            int start = index;
            var lines = new ArrayList<Component>();
            while (index < gathered.size()) {
                var text = gathered.get(index).left().orElse(null);
                if (!(text instanceof Component line) || !originals.contains(line)) break;
                lines.add(line);
                index++;
            }
            if (lines.isEmpty()) {
                output.add(gathered.get(index++));
                continue;
            }
            boolean prepend = lines.getFirst() != title;
            if (prepend) lines.addFirst(title);
            var rendered = translate.apply(lines);
            if (rendered.equals(lines)) output.addAll(gathered.subList(start, index));
            else for (int i = prepend ? 1 : 0; i < rendered.size(); i++) output.add(Either.left(rendered.get(i)));
        }
        return output.equals(gathered) ? gathered : output;
    }
}
