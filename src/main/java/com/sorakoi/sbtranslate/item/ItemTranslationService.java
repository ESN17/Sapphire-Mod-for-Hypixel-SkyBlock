package com.sorakoi.sbtranslate.item;
import com.sorakoi.sbtranslate.Sapphire;
import com.sorakoi.sbtranslate.DisplayTranslationHooks;
import com.sorakoi.sbtranslate.collection.UntranslatedCollector.Observation;
import com.sorakoi.sbtranslate.translation.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import java.util.*;
import java.util.function.IntPredicate;
import org.lwjgl.glfw.GLFW;

public final class ItemTranslationService {
    public static String id(ItemStack stack) { return Sapphire.NEU.resolve(ItemIdResolver.resolve(stack).variantId()); }
    public static Component name(ItemStack stack, Component original) {
        return name(stack,original,currentMenuTitle(stack));
    }
    private static Component name(ItemStack stack, Component original,String menuTitle) {
        String id=id(stack);
        if(Sapphire.active("gui")) {
            var menu=GuiTranslationEngine.menu(menuTitle,Sapphire.TRANSLATIONS);
            var translated=GuiTranslationEngine.button(original,menu);
            if(translated!=original)return Sapphire.originalMode(translated,original);
        }
        if (!Sapphire.active("item")) return communityName(original,original,menuTitle);
        var entry=Sapphire.TRANSLATIONS.item(id);
        if (entry==null && isEnchantedBook(stack))
            return Sapphire.originalMode(TextTranslationEngine.translate(original,text->Sapphire.TRANSLATIONS.match("item",id,text)),original);
        var neu=Sapphire.NEU.get(id); String english=neu!=null ? neu.name() : entry!=null ? entry.originalName() : "";
        var translated=TextTranslationEngine.itemName(original,english,entry==null ? "" : entry.name(),Sapphire.TRANSLATIONS::reforge,entry==null ? Map.of() : entry.styleMappings(),Sapphire.TRANSLATIONS.scope("name-modifiers"));
        return communityName(original,translated,menuTitle);
    }
    private static Component communityName(Component original,Component translated,String menuTitle) {
        if(translated==original && Sapphire.active("gui") && GuiTranslationEngine.menu(menuTitle,Sapphire.TRANSLATIONS)!=null)
            translated=CommunityTranslationEngine.translate(original,"tooltip",Sapphire.TRANSLATIONS);
        return Sapphire.originalMode(translated,original);
    }
    public static void tooltip(ItemStack stack, List<Component> lines) {
        tooltip(stack,lines,key->InputConstants.isKeyDown(Minecraft.getInstance().getWindow(),key));
    }
    /** Read keys for each tooltip; no sticky mode or changes to the underlying stack. */
    public static void tooltip(ItemStack stack, List<Component> lines, IntPredicate keyDown) {
        tooltip(stack,lines,keyDown,currentMenuTitle(stack));
    }
    public static void tooltip(ItemStack stack,List<Component> lines,IntPredicate keyDown,String menuTitle) {
        boolean itemRules=Sapphire.active("item");
        var menu=Sapphire.active("gui") ? GuiTranslationEngine.menu(menuTitle,Sapphire.TRANSLATIONS) : null;
        if((!itemRules && menu==null) || lines.isEmpty()) return;
        if(keyDown.test(GLFW.GLFW_KEY_LEFT_CONTROL) || keyDown.test(GLFW.GLFW_KEY_RIGHT_CONTROL)) return;
        String id=id(stack); Component before=lines.getFirst(), after=name(stack,before,menuTitle); lines.set(0,after);
        var tooltipMenu=GuiTranslationEngine.tooltipContext(menu,before,Sapphire.TRANSLATIONS);
        List<String> missing=new ArrayList<>(); boolean changed=after!=before;
        if (itemRules && Sapphire.CONFIG.lore || menu!=null) {
            var result=LoreTranslationEngine.translate(List.copyOf(lines.subList(1,lines.size())),Sapphire.TRANSLATIONS,id,ItemTranslationService::wrap,Sapphire.CONFIG.showOriginal,isEnchantedBook(stack),itemRules && Sapphire.CONFIG.lore,GuiTranslationEngine.tooltipRules(tooltipMenu,before,Sapphire.TRANSLATIONS),line->GuiTranslationEngine.detailLine(line,tooltipMenu,Sapphire.TRANSLATIONS),line->menu==null?line:CommunityTranslationEngine.translate(line,"tooltip",Sapphire.TRANSLATIONS),line->menu==null?List.of():Sapphire.TRANSLATIONS.community().candidates("tooltip",TranslationManager.plain(line.getString())));
            lines.subList(1,lines.size()).clear(); lines.addAll(result.lines());
            changed |= result.changed(); missing.addAll(result.missing());
        } else {
            for (int i=1;i<lines.size();i++) if (!lines.get(i).getString().isBlank()) missing.add(TranslationManager.plain(lines.get(i).getString()));
        }
        collect(stack,before,after!=before,missing,changed);
        com.sorakoi.sbtranslate.client.Diagnostics.item(id,changed,missing.size());
    }
    private static String currentMenuTitle(ItemStack stack) {
        var client=Minecraft.getInstance();var screen=client.screen;
        if(!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container) || client.player==null)return null;
        // A player's own inventory remains an inventory even while an auction
        // screen is open. Only server-container slots receive menu rules.
        for(var slot:container.getMenu().slots)
            if(slot.container!=client.player.getInventory() && slot.getItem()==stack)return screen.getTitle().getString();
        return null;
    }
    private static boolean isEnchantedBook(ItemStack stack) {
        return "ENCHANTED_BOOK".equals(ItemIdResolver.resolve(stack).skyblockId());
    }
    private static List<Component> wrap(Component translated,List<Component> originals) {
        var client=net.minecraft.client.Minecraft.getInstance(); var font=client.font;
        int width=Math.max(120,Math.min(320,originals.stream().mapToInt(font::width).max().orElse(240)));
        width=Math.min(width,Math.max(40,client.getWindow().getGuiScaledWidth()-24));
        List<Component> result=new ArrayList<>();
        for (FormattedText line : font.getSplitter().splitLines(translated,width,Style.EMPTY)) {
            MutableComponent component=Component.empty();
            line.visit((style,text) -> { component.append(Component.literal(text).setStyle(style)); return Optional.empty(); },Style.EMPTY);
            result.add(component);
        }
        return result;
    }
    public static void collectStack(ItemStack stack) {
        if(stack.isEmpty() || !Sapphire.canCollect()) return;
        Component name=stack.getHoverName(); String resolvedId=id(stack);
        var result=LoreTranslationEngine.translate(stack.getOrDefault(DataComponents.LORE,ItemLore.EMPTY).lines(),Sapphire.TRANSLATIONS,resolvedId,(text,originals)->List.of(text),false,isEnchantedBook(stack));
        boolean named=name(stack,name)!=name; collect(stack,name,named,result.missing(),result.changed()||named);
    }
    private static void collect(ItemStack stack,Component name,boolean named,List<String> missing,boolean changed) {
        if(!Sapphire.canCollect()) return;
        var identity=ItemIdResolver.resolve(stack); String id=Sapphire.NEU.resolve(identity.variantId()); var neu=Sapphire.NEU.get(id);
        // Never persist arbitrary custom item text. NEU reference text and reviewed numeric rules are safe sources.
        List<String> safe=missing.stream().filter(s -> (neu!=null && neu.lore().contains(s)) || Sapphire.POLICY.allows("item",s)).limit(64).toList();
        String original=neu!=null ? neu.name() : "";
        List<String> toSave=new ArrayList<>(safe); if(!named && !original.isBlank()) toSave.addFirst(original);
        if(toSave.isEmpty()) return;
        Sapphire.COLLECTOR.observe(new Observation("item",Sapphire.itemContext(),id,identity.skyblockId(),original,toSave,changed),true,true);
    }
}
