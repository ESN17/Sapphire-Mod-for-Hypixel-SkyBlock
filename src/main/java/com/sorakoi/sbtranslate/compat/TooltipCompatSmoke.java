package com.sorakoi.sbtranslate.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.component.DataComponents;
import java.util.*;

/** Exercises optional third-party parsers and layout using a synthetic stack inside the offline test client. */
public final class TooltipCompatSmoke {
    public static void run(ItemStack stack) {
        var loader = FabricLoader.getInstance();
        try {
            if (loader.isModLoaded("roughlyenoughitems")) checkRei(stack);
            if (loader.isModLoaded("iceberg")) {
                var raw = stack.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL);
                var font = Minecraft.getInstance().font;
                var gather = Arrays.stream(Class.forName("com.anthonyhilyard.iceberg.util.Tooltips").getMethods())
                        .filter(m -> m.getName().equals("gatherTooltipComponents") && m.getParameterCount() == 9).findFirst().orElseThrow();
                var rendered = (List<?>)gather.invoke(null, stack, raw, Optional.empty(), 0, 2000, 2000, font, font, -1);
                var text = new StringBuilder();
                for (Object row : rendered) {
                    if (!(row instanceof net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip)) continue;
                    var field = net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip.class.getDeclaredField("text");
                    field.setAccessible(true);
                    ((net.minecraft.util.FormattedCharSequence)field.get(row)).accept((i, style, point) -> { text.appendCodePoint(point); return true; });
                }
                if (!text.toString().contains("末影之刃") || !raw.getFirst().getString().contains("Aspect of the End"))
                    throw new IllegalStateException("Iceberg tooltip display or source mismatch: " + text);
                com.sorakoi.sbtranslate.Sapphire.LOGGER.info("SAPPHIRE_ICEBERG_DISPLAY_OK");
            }
            if (loader.isModLoaded("skycofl")) {
                var button = stack.copy();
                button.set(DataComponents.CUSTOM_NAME,Component.literal("Sell Instantly"));
                button.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("Total: 2,592,275 coins"))));
                var lines = new ArrayList<>(button.getTooltipLines(Item.TooltipContext.EMPTY,null,TooltipFlag.NORMAL));
                var warning = Component.literal("⚠ Sell Protection ⚠");
                lines.add(warning);
                var display = com.sorakoi.sbtranslate.DisplayTranslationHooks.tooltip(button,lines);
                long amount = (long)Class.forName("com.coflnet.utils.SellAmountParser").getMethod("extractSellInstantlyAmount",ItemStack.class).invoke(null,button);
                if (amount != 2592275 || display.getLast()!=warning || !button.getHoverName().getString().equals("Sell Instantly"))
                    throw new IllegalStateException("SkyCofl protection inputs or display changed");
                com.sorakoi.sbtranslate.Sapphire.LOGGER.info("SAPPHIRE_SKYCOFL_AMOUNT_OK");
            }
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static void checkRei(ItemStack stack) throws ReflectiveOperationException {
        var definition = Class.forName("me.shedaniel.rei.plugin.client.entry.ItemEntryDefinition");
        var entryType = Class.forName("me.shedaniel.rei.api.common.entry.EntryStack");
        var contextType = Class.forName("me.shedaniel.rei.api.client.gui.widgets.TooltipContext");
        var tooltipType = Class.forName("me.shedaniel.rei.api.client.gui.widgets.Tooltip");
        var rowType = Class.forName("me.shedaniel.rei.api.client.gui.widgets.Tooltip$Entry");
        var definitionObject = definition.getConstructor().newInstance();
        var entry = entryType.getMethod("of",Class.forName("me.shedaniel.rei.api.common.entry.type.EntryDefinition"),Object.class)
                .invoke(null,definitionObject,stack);
        var renderer = definition.getMethod("getRenderer").invoke(definitionObject);
        var draw = Class.forName("me.shedaniel.rei.api.client.entry.renderer.EntryRenderer").getMethod("getTooltip",entryType,contextType);
        var context = contextType.getMethod("of",Class.forName("me.shedaniel.math.Point"),Item.TooltipContext.class,TooltipFlag.class,boolean.class);
        var standard = contextType.getMethod("of",Item.TooltipContext.class).invoke(null,Item.TooltipContext.EMPTY);
        var point = contextType.getMethod("getPoint").invoke(standard);
        var original = stack.copy();
        for(boolean search : List.of(false,true)) {
            var tooltip = draw.invoke(renderer,entry,context.invoke(null,point,Item.TooltipContext.EMPTY,TooltipFlag.NORMAL,search));
            var text = new ArrayList<String>();
            for(Object row : (List<?>)tooltipType.getMethod("entries").invoke(tooltip)) {
                if((boolean)rowType.getMethod("isText").invoke(row))
                    text.add(((Component)rowType.getMethod("getAsText").invoke(row)).getString());
            }
            if(!text.getFirst().contains(search ? "Aspect of the End" : "末影之刃")
                    || text.stream().noneMatch(line -> line.contains(search ? "Damage" : "伤害"))
                    || !text.contains("Untranslated sentinel") || !ItemStack.isSameItemSameComponents(stack,original))
                throw new IllegalStateException("Unexpected REI tooltip (search="+search+"): "+text);
        }
        com.sorakoi.sbtranslate.Sapphire.LOGGER.info("SAPPHIRE_REI_TOOLTIP_OK");
    }
}
