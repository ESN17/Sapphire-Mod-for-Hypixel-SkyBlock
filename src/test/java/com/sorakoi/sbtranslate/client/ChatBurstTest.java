package com.sorakoi.sbtranslate.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatBurstTest {
    private static GuiMessage message(Component content) {
        return new GuiMessage(0, content, null, GuiMessageSource.SYSTEM_SERVER, null);
    }

    @Test void onlyChangedMessagesAreTranslatedAndWrappedDuringReplay() {
        var cache = new ChatRefreshCache();
        var history = new ArrayList<GuiMessage>();
        var translated = new IdentityHashMap<GuiMessage, Component>();
        int[] displays = {0}, wraps = {0}, width = {200};
        for (int i = 0; i < 100; i++) history.add(message(Component.literal("Countdown " + i)));
        Runnable redraw = () -> {
            for (var raw : history) {
                var online = translated.get(raw);
                var shown = cache.display(raw, online, () -> {
                    displays[0]++;
                    return online == null ? raw : message(online);
                });
                assertSame(online == null ? raw.content() : online, shown.content());
                cache.lines(shown, null, width[0], () -> {
                    wraps[0]++;
                    return List.of(FormattedCharSequence.forward(shown.content().getString(), Style.EMPTY));
                });
            }
        };
        redraw.run();
        for (int i = 0; i < 100; i += 5) {
            for (int j = i; j < i + 5; j++) translated.put(history.get(j), Component.literal("倒计时 " + j));
            cache.replay(redraw);
        }
        assertEquals(200, displays[0]); // 100 originals, then each of the 100 replacements once.
        assertEquals(200, wraps[0]);
        width[0] = 120;
        cache.replay(redraw);
        assertEquals(200, displays[0]);
        assertEquals(300, wraps[0]);
        cache.clear();
        cache.replay(redraw);
        assertEquals(300, displays[0]);
        assertEquals(400, wraps[0]);
        redraw.run(); // A native refresh can reflect changes from other mods.
        assertEquals(400, displays[0]);
        assertEquals(500, wraps[0]);
    }

    @Test void preparationSpreadsWorkAcrossTicksInOrderAndDropsStaleConnectionWork() {
        var queue = new ChatTickQueue<GuiMessage>();
        var expected = new ArrayList<GuiMessage>();
        var actual = new ArrayList<GuiMessage>();
        for (int i = 0; i < 120; i++) {
            expected.add(message(Component.literal("Dungeon " + i)));
            assertTrue(queue.offer(expected.getLast(), 1));
        }
        while (!queue.isEmpty()) {
            int before = actual.size();
            queue.drain(1, actual::add);
            assertTrue(actual.size() > before && actual.size() - before <= ChatTickQueue.MAX_PER_TICK);
        }
        assertEquals(expected, actual);
        queue.offer(expected.getFirst(), 1);
        queue.drain(2, unused -> fail("prepared a previous session's message"));
        queue.offer(expected.getFirst(), 2);
        queue.offer(expected.getLast(), 3);
        queue.drain(3, msg -> assertSame(expected.getLast(), msg));
        assertTrue(queue.isEmpty());
    }
}
