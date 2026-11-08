package com.sorakoi.sbtranslate.client;

import com.sorakoi.sbtranslate.Sapphire;
import com.sorakoi.sbtranslate.llm.LlmStyledText;
import com.sorakoi.sbtranslate.llm.LlmText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

/** Exercises a simultaneous batch of API completions using the native chat history and font. */
final class ChatBurstSmokeTest {
    static void run() {
        var chat = new ChatComponent(Minecraft.getInstance());
        boolean enabled = Sapphire.LLM_CONFIG.enabled, replace = Sapphire.CONFIG.apiChatReplace, hover = Sapphire.CONFIG.chatOriginalHover;
        try {
            Sapphire.LLM_CONFIG.enabled = true;
            Sapphire.CONFIG.apiChatReplace = true;
            Sapphire.CONFIG.chatOriginalHover = true;
            ChatDisplay.clear();
            var originals = new ArrayList<Component>();
            var action = new ClickEvent.SuggestCommand("/party ready");
            for (int i = 0; i < 100; i++) {
                var source = Component.literal("Dungeon ").withStyle(s -> s.withClickEvent(action).withColor(0x55ff55))
                    .append(Component.literal(Integer.toString(i)).withStyle(s -> s.withColor(0xffaa00)));
                originals.add(source);
                chat.addServerSystemMessage(source);
                var selection = new LlmText.Selection(LlmText.Kind.SYSTEM, "", source.getString());
                var request = LlmStyledText.prepare(source, selection, List.of());
                if (request == null) throw new IllegalStateException("Failed to prepare styled dungeon message");
                String response = request.text().replace("Dungeon", "译文");
                ChatDisplay.replace(source, () -> request.restore(response));
            }
            var field = ChatComponent.class.getDeclaredField("trimmedMessages");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var lines = (List<GuiMessage.Line>)field.get(chat);
            var prior = Collections.newSetFromMap(new IdentityHashMap<net.minecraft.util.FormattedCharSequence, Boolean>());
            lines.forEach(line -> prior.add(line.content()));
            int refreshes = 0, changed = 0;
            for (int tick = 0; tick < 40; tick++) {
                if (!ChatDisplay.refreshIfDue(chat::rescaleChat)) continue;
                refreshes++;
                int dirty = 0;
                for (var line : lines) if (!prior.contains(line.content())) dirty++;
                if (dirty < 1 || dirty > 16) throw new IllegalStateException("Unexpected number of rewrapped lines: " + dirty);
                changed += dirty;
                prior.clear();
                lines.forEach(line -> prior.add(line.content()));
            }
            var translated = new HashSet<String>();
            for (int i = 0; i < lines.size(); i++) {
                var text = new StringBuilder();
                var expected = originals.get(99 - i);
                lines.get(i).content().accept((index, style, point) -> {
                    text.appendCodePoint(point);
                    if (!action.equals(style.getClickEvent()) || !expected.getString().equals(ChatOriginalHover.original(style)))
                        throw new IllegalStateException("Lost original hover or click during burst");
                    int color = Character.isDigit(point) ? 0xffaa00 : 0x55ff55;
                    if (style.getColor() == null || style.getColor().getValue() != color)
                        throw new IllegalStateException("Lost span color during burst");
                    return true;
                });
                if (!text.toString().equals("译文 " + (99 - i))) throw new IllegalStateException("Burst changed chat order");
                translated.add(text.toString());
            }
            var raw = ChatComponent.class.getDeclaredField("allMessages");
            raw.setAccessible(true);
            var history = (List<?>)raw.get(chat);
            for (int i = 0; i < 100; i++)
                if (((GuiMessage)history.get(i)).content() != originals.get(99 - i)) throw new IllegalStateException("Lost original message");
            if (translated.size() != 100 || changed != 100 || refreshes != 7) throw new IllegalStateException("Incomplete API burst");
            Sapphire.LOGGER.info("SAPPHIRE_CHAT_BURST_SMOKE_OK: 100 simultaneous replacements, {} refreshes, {} changed line layouts, original hover and click retained", refreshes, changed);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Native chat burst smoke failed", e);
        } finally {
            ChatDisplay.clear();
            Sapphire.LLM_CONFIG.enabled = enabled;
            Sapphire.CONFIG.apiChatReplace = replace;
            Sapphire.CONFIG.chatOriginalHover = hover;
        }
    }
}
