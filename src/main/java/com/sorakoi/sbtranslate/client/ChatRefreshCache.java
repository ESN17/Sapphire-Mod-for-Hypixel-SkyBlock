package com.sorakoi.sbtranslate.client;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Client-thread cache. Only an API-triggered replay may reuse the last native layout. */
final class ChatRefreshCache {
    private record Display(Component online, GuiMessage message) {}
    private record Layout(Font font, int width, List<FormattedCharSequence> lines) {}

    private static final class History<V> {
        private final IdentityHashMap<GuiMessage, V> values = new IdentityHashMap<>();
        private final ArrayDeque<GuiMessage> order = new ArrayDeque<>();

        void put(GuiMessage message, V value) {
            if (values.put(message, value) == null) order.addLast(message);
            if (order.size() > 256) values.remove(order.removeFirst());
        }
        void clear() {
            values.clear();
            order.clear();
        }
    }

    private final History<Display> displays = new History<>();
    private final History<Layout> layouts = new History<>();
    private boolean replay;

    GuiMessage display(GuiMessage raw, Component online, Supplier<GuiMessage> translate) {
        Display last = replay ? displays.values.get(raw) : null;
        if (last != null && last.online == online) return last.message;
        GuiMessage result = translate.get();
        displays.put(raw, new Display(online, result));
        return result;
    }

    List<FormattedCharSequence> lines(GuiMessage message, Font font, int width, Supplier<List<FormattedCharSequence>> split) {
        Layout last = replay ? layouts.values.get(message) : null;
        if (last != null && last.font == font && last.width == width) return last.lines;
        var result = split.get();
        layouts.put(message, new Layout(font, width, List.copyOf(result)));
        return result;
    }

    void replay(Runnable refresh) {
        boolean previous = replay;
        replay = true;
        try { refresh.run(); }
        finally { replay = previous; }
    }

    void clear() {
        displays.clear();
        layouts.clear();
    }
}
