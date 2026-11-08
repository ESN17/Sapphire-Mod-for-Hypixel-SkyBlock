package com.sorakoi.sbtranslate.client;

import com.sorakoi.sbtranslate.translation.TranslationManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Attach once when a translated message enters chat, never during frame rendering. */
public final class ChatOriginalHover {
    // Equal tooltips on separate lines have independent lifetimes when chat is rewrapped.
    private static final Map<HoverEvent,String> ORIGINALS=new com.google.common.collect.MapMaker().weakKeys().makeMap();
    public static String original(Style style){
        var hover=style==null?null:style.getHoverEvent();
        return hover==null?null:ORIGINALS.get(hover);
    }
    public static net.minecraft.client.gui.ActiveTextCollector.ClickableStyleFinder finder(net.minecraft.client.gui.Font font,int x,int y){
        return new net.minecraft.client.gui.ActiveTextCollector.ClickableStyleFinder(font,x,y){
            @Override public void accept(net.minecraft.client.gui.TextAlignment alignment,int px,int py,net.minecraft.client.gui.ActiveTextCollector.Parameters parameters,net.minecraft.util.FormattedCharSequence text){
                super.accept(alignment,px,py,parameters,sink->text.accept((index,style,code)->sink.accept(index,
                    original(style)==null?style:style.withClickEvent(new ClickEvent.CopyToClipboard("")),code)));
            }
        };
    }
    private ChatOriginalHover() {}

    public static Component attach(Component translated, Component original) {
        if(!com.sorakoi.sbtranslate.Sapphire.CONFIG.chatOriginalHover)return translated;
        if (translated == null || original == null || translated == original
            || TranslationManager.plain(translated.getString()).equals(TranslationManager.plain(original.getString()))) return translated;

        Component originalTip = Component.empty()
            .append(Component.literal("英文原文\n").withStyle(ChatFormatting.AQUA))
            .append(snapshot(original));
        var defaultHover = new HoverEvent.ShowText(originalTip);
        String plain=TranslationManager.plain(original.getString());ORIGINALS.put(defaultHover,plain);
        Map<HoverEvent, HoverEvent> tips = new HashMap<>();
        var result = Component.empty();
        boolean[] nativeTooltip = {false};
        // Resolve inherited styles per span, so child hovers cannot hide the original.
        translated.visit((style, text) -> {
            if (text.isEmpty()) return Optional.empty();
            var previous = style.getHoverEvent();
            HoverEvent hover;
            if (previous == null) hover = defaultHover;
            else if (previous instanceof HoverEvent.ShowText shown) {
                hover = tips.computeIfAbsent(previous, ignored -> new HoverEvent.ShowText(
                    Component.empty().append(snapshot(shown.value())).append("\n\n").append(originalTip)));
            } else {
                // A style supports only one hover action. Retain native item/entity details.
                hover = previous;
                nativeTooltip[0] = true;
            }
            if(previous==null || previous instanceof HoverEvent.ShowText)ORIGINALS.put(hover,plain);
            result.append(Component.literal(text).setStyle(style.withHoverEvent(hover)));
            return Optional.empty();
        }, Style.EMPTY);
        if (nativeTooltip[0]) result.append(Component.literal(" [原文]")
            .withStyle(s -> s.withColor(ChatFormatting.DARK_AQUA).withHoverEvent(defaultHover)));
        return result;
    }

    /** Detached display snapshot: no mutations, nested hover chains, or live sibling references. */
    private static Component snapshot(Component source) {
        var result = Component.empty();
        source.visit((style, text) -> {
            result.append(Component.literal(text).setStyle(style.withHoverEvent(null).withClickEvent(null)));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }
}
