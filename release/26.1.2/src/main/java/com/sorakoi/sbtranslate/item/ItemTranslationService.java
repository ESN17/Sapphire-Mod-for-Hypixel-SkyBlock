package com.sorakoi.sbtranslate.item;
import com.sorakoi.sbtranslate.Sapphire;
import com.sorakoi.sbtranslate.DisplayTranslationHooks;
import com.sorakoi.sbtranslate.collection.UntranslatedCollector.Observation;
import com.sorakoi.sbtranslate.collection.UiCapture;
import com.sorakoi.sbtranslate.translation.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import java.util.*;
import java.util.function.IntPredicate;


public final class ItemTranslationService {
    public static final TooltipDisplayCache DISPLAY_CACHE=new TooltipDisplayCache();
    private static final GuiTranslationEngine.Menu SERVER_NAVIGATION = new GuiTranslationEngine.Menu("server-navigation",List.of(),List.of(),Map.of());
    private static final Set<CollectionKey> COLLECTED=new LinkedHashSet<>();
    private record CollectionKey(Object dictionary,long revision,String id,List<List<TooltipDisplayCache.Run>> lines) {}
    private static final ItemStack[] scannedSlots=new ItemStack[180];
    private static Object scannedDictionary;
    private static long scannedRevision;
    public static void clearCaches(){DISPLAY_CACHE.clear();COLLECTED.clear();Arrays.fill(scannedSlots,null);}
    public static void collectSlot(int slot,ItemStack stack) {
        var dictionary=Sapphire.TRANSLATIONS;
        if(dictionary!=scannedDictionary || dictionary.revision()!=scannedRevision){
            Arrays.fill(scannedSlots,null);
            scannedDictionary=dictionary;
            scannedRevision=dictionary.revision();
        }
        var previous=scannedSlots[slot];
        if(previous!=null && ItemStack.isSameItemSameComponents(previous,stack))return;
        collectStack(stack);
        scannedSlots[slot]=stack.copy();
    }
    public static String id(ItemStack stack) { return Sapphire.NEU.resolve(ItemIdResolver.resolve(stack).variantId()); }
    public static Component name(ItemStack stack, Component original) {
        return name(stack,original,currentMenuTitle(stack));
    }
    private static Component name(ItemStack stack, Component original,String menuTitle) {
        var identity=ItemIdResolver.resolve(stack);
        return name(stack,original,menuTitle,identity,Sapphire.NEU.resolve(identity.variantId()));
    }
    private static Component name(ItemStack stack,Component original,String menuTitle,ItemIdResolver.Identity identity,String id) {
        // Fresh sale cosmetics have no safe name mapping until NEU and the
        // reviewed item dictionary contain them. Do not use menu fallbacks.
        String rawId=identity.skyblockId();
        if(preserveNewCosmetic(rawId,id,Sapphire.NEU.get(id)!=null,Sapphire.TRANSLATIONS.item(id)!=null))return original;
        if(Sapphire.active("gui")) {
            var menu=GuiTranslationEngine.menu(menuTitle,Sapphire.TRANSLATIONS);
            var creature=GuiTranslationEngine.creatureName(original,menu,Sapphire.TRANSLATIONS);
            if(creature!=original)return Sapphire.originalMode(creature,original);
            var translated=GuiTranslationEngine.button(original,menu);
            if(translated!=original)return Sapphire.originalMode(translated,original);
        }
        if (!Sapphire.active("item")) return communityName(original,original,menuTitle);
        var entry=Sapphire.TRANSLATIONS.item(id);
        if (entry==null && isEnchantedBook(stack))
            return Sapphire.originalMode(TextTranslationEngine.translate(original,text->Sapphire.TRANSLATIONS.match("item",id,text)),original);
        var neu=Sapphire.NEU.get(id); String english=neu!=null ? neu.name() : entry!=null ? entry.originalName() : "";
        var translated=TextTranslationEngine.itemName(original,english,entry==null ? "" : entry.name(),Sapphire.TRANSLATIONS::reforge,entry==null ? Map.of() : entry.styleMappings(),Sapphire.TRANSLATIONS.scope("name-modifiers"));
        if(translated==original)translated=SystemTranslationEngine.translate(original,"item-name",Sapphire.TRANSLATIONS);
        return communityName(original,translated,menuTitle);
    }
    public static boolean preserveNewCosmetic(String rawId,String id,boolean inNeu,boolean translated) {
        if(inNeu && translated)return false;
        String kind=id==null?rawId:id;
        return kind!=null && (kind.equals("RUNE") || kind.equals("UNIQUE_RUNE") || kind.contains("_RUNE;") ||
            kind.contains("_SKIN") || kind.startsWith("SKIN_") || kind.startsWith("DYE_"));
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
        tooltip(stack,lines,keyDown,menuTitle,Sapphire.CONTEXT.publicLocation());
    }
    public static void tooltip(ItemStack stack,List<Component> lines,IntPredicate keyDown,String menuTitle,boolean publicLocation) {
        boolean itemRules=Sapphire.active("item");
        boolean gui=Sapphire.active("gui");
        var knownMenu=gui ? GuiTranslationEngine.menu(menuTitle,Sapphire.TRANSLATIONS) : null;
        var menu=knownMenu==null && gui && UiCapture.publicMenu(menuTitle,publicLocation)
                ? SERVER_NAVIGATION : knownMenu;
        if((!itemRules && menu==null) || lines.isEmpty()) return;
        boolean control=com.sorakoi.sbtranslate.compat.SkyCoflCompat.previewKey(com.sorakoi.sbtranslate.compat.SkyCoflCompat.installed(),keyDown);
        if(!Sapphire.CONFIG.translateTooltip(control))return;
        var client=Minecraft.getInstance();
        int options=(itemRules?1:0)|(menu!=null?2:0)|(Sapphire.CONFIG.lore?4:0)|(Sapphire.CONFIG.showOriginal?8:0)|(Sapphire.canCollect()?16:0);
        boolean captureUi=menu!=null && com.sorakoi.sbtranslate.collection.ChatCapture.uiActive() && UiCapture.publicMenu(menuTitle,publicLocation);
        if(captureUi) options |= 32;
        var identity=ItemIdResolver.resolve(stack);
        String id=Sapphire.NEU.resolve(identity.variantId());
        var key=new TooltipDisplayCache.Key(Sapphire.TRANSLATIONS,Sapphire.TRANSLATIONS.revision(),id,menuTitle,options,
            client.getWindow().getGuiScaledWidth(),client.font,DISPLAY_CACHE.displaySnapshot(lines));
        var cached=DISPLAY_CACHE.get(key);
        if(cached!=null){lines.clear();lines.addAll(cached);return;}
        Component before=lines.getFirst(), after=name(stack,before,menuTitle,identity,id);
        if(after==before && menu!=null) {
            String label=Sapphire.TRANSLATIONS.wikiButtons(menuTitle).get(TranslationManager.plain(before.getString()));
            if(label!=null)after=Sapphire.originalMode(TextTranslationEngine.rangeReplacement(before,0,TranslationManager.plain(before.getString()).length(),label),before);
            else after=Sapphire.originalMode(SystemTranslationEngine.translate(before,"gui-button",Sapphire.TRANSLATIONS),before);
        }
        lines.set(0,after);
        var tooltipMenu=GuiTranslationEngine.tooltipContext(menu,before,Sapphire.TRANSLATIONS);
        List<String> missing=new ArrayList<>(); boolean changed=after!=before;
        if(captureUi && !changed && com.sorakoi.sbtranslate.collection.UiCapture.allowMenuLine(menuTitle,before.getString()))
            com.sorakoi.sbtranslate.collection.ChatCapture.observeUi("gui-button",before);
        if (itemRules && Sapphire.CONFIG.lore || menu!=null) {
            var neu=Sapphire.NEU.get(id);
            boolean wikiLore=menu!=null || neu!=null && neu.lore().stream().anyMatch(line->line.contains("Rift-Transferable") || line.contains("Rift-Exportable") || line.contains("Rift-Importable"));
            var result=LoreTranslationEngine.translate(List.copyOf(lines.subList(1,lines.size())),Sapphire.TRANSLATIONS,id,ItemTranslationService::wrap,Sapphire.CONFIG.showOriginal,isEnchantedBook(stack),itemRules && Sapphire.CONFIG.lore,GuiTranslationEngine.tooltipRules(tooltipMenu,before,Sapphire.TRANSLATIONS),line->GuiTranslationEngine.detailLine(line,tooltipMenu,Sapphire.TRANSLATIONS),line->wikiLore?CommunityTranslationEngine.translate(line,"tooltip",Sapphire.TRANSLATIONS):line,line->wikiLore?Sapphire.TRANSLATIONS.community().candidates("tooltip",TranslationManager.plain(line.getString())):List.of());
            if(captureUi && Sapphire.CONFIG.showOriginal && !result.missing().isEmpty()) {
                var untranslated=new HashSet<>(result.missing());
                for(var line:lines.subList(1,lines.size()))
                    if(untranslated.contains(TranslationManager.plain(line.getString())) && com.sorakoi.sbtranslate.collection.UiCapture.allowMenuLine(menuTitle,line.getString()))
                        com.sorakoi.sbtranslate.collection.ChatCapture.observeUi("gui-tooltip",line);
            }
            if(captureUi && !Sapphire.CONFIG.showOriginal)for(var line:result.lines()) {
                String remainder=TranslationManager.plain(line.getString());
                if(UiCapture.allowMenuLine(menuTitle,remainder))
                    com.sorakoi.sbtranslate.collection.ChatCapture.observeUi("gui-tooltip",line);
            }
            lines.subList(1,lines.size()).clear(); lines.addAll(result.lines());
            changed |= result.changed(); missing.addAll(result.missing());
        } else {
            for (int i=1;i<lines.size();i++) if (!lines.get(i).getString().isBlank()) missing.add(TranslationManager.plain(lines.get(i).getString()));
        }
        collect(stack,before,after!=before,missing,changed);
        var dungeonCapture=com.sorakoi.sbtranslate.collection.DungeonCapture.INSTANCE;
        if(dungeonCapture!=null) {
            if(after==before)dungeonCapture.observe(Sapphire.CONTEXT.dungeon(),"tooltip-name",String.valueOf(menuTitle),before.getString(),after.getString());
            for(String line:missing)dungeonCapture.observe(Sapphire.CONTEXT.dungeon(),"tooltip",String.valueOf(menuTitle)+" / "+id,line,line);
        }
        com.sorakoi.sbtranslate.client.Diagnostics.item(id,changed,missing.size());
        DISPLAY_CACHE.put(key,lines);
    }
    private static String currentMenuTitle(ItemStack stack) {
        var client=Minecraft.getInstance();var screen=client.screen;
        if(!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container) || client.player==null)return null;
        var hovered=((com.sorakoi.sbtranslate.mixin.ContainerHoveredSlot)container).sapphire$hoveredSlot();
        if(hovered!=null && ItemStack.isSameItemSameComponents(hovered.getItem(),stack))
            return hovered.container==client.player.getInventory()?null:screen.getTitle().getString();
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
        var source=new ArrayList<Component>();source.add(name);source.addAll(stack.getOrDefault(DataComponents.LORE,ItemLore.EMPTY).lines());
        var key=new CollectionKey(Sapphire.TRANSLATIONS,Sapphire.TRANSLATIONS.revision(),resolvedId,TooltipDisplayCache.snapshot(source));
        if(!COLLECTED.add(key))return;
        if(COLLECTED.size()>512)COLLECTED.remove(COLLECTED.iterator().next());
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
