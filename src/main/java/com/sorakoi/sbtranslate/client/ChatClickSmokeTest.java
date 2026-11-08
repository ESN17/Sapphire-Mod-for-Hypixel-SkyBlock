package com.sorakoi.sbtranslate.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sorakoi.sbtranslate.Sapphire;
import com.sorakoi.sbtranslate.llm.LlmStyledText;
import com.sorakoi.sbtranslate.llm.LlmText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.*;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/** Offline smoke only: exercise the real mouse handler, without a player or network connection. */
final class ChatClickSmokeTest {
    static void run() {
        var mc = Minecraft.getInstance();
        require(Boolean.getBoolean("sapphire.smoke") && mc.player == null && mc.getConnection() == null,
            "click dispatch test must be offline");
        boolean copyEnabled = Sapphire.CONFIG.chatCopyOriginal, hoverEnabled = Sapphire.CONFIG.chatOriginalHover;
        String clipboard = mc.keyboardHandler.getClipboard();
        var chat = mc.gui.hud.getChat();
        try {
            Sapphire.CONFIG.chatCopyOriginal = true;
            Sapphire.CONFIG.chatOriginalHover = true;
            var screen = new ChatScreen("", false);
            // Init normally requires a connected player. Supply only the input widgets needed by mouseClicked.
            var input = new EditBox(mc.font, 4, mc.getWindow().getGuiScaledHeight() - 12, 200, 12, Component.empty());
            field(screen, "input", input);
            field(screen, "commandSuggestions", new CommandSuggestions(mc, screen, input, mc.font, false, false, 1, 10, true, 0));

            List<ClickEvent> actions = List.of(
                new ClickEvent.RunCommand("/socialoptions SapphireTest"),
                new ClickEvent.RunCommand("/report SapphireTest"),
                new ClickEvent.RunCommand("/ignore add SapphireTest"),
                new ClickEvent.RunCommand("/show fixture-item-token"),
                new ClickEvent.SuggestCommand("/msg SapphireTest "),
                new ClickEvent.CopyToClipboard("original copy payload"),
                new ClickEvent.OpenUrl(java.net.URI.create("https://example.com/unchanged")));
            for (var action : actions) {
                var source = Component.literal("Open item").withStyle(s -> s.withClickEvent(action)
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Diorite x2\nCOMMON"))));
                var request = LlmStyledText.prepare(source,
                    new LlmText.Selection(LlmText.Kind.SYSTEM, "", source.getString()), List.of());
                require(request != null, "prepare clickable translation");
                var translated = request.restore(request.text().replace("Open item", "打开物品"));
                require(translated != null, "restore clickable translation");
                chat.clearMessages(false);
                chat.addClientSystemMessage(ChatOriginalHover.attach(translated, source));
                var point = find(style -> action.equals(style.getClickEvent()), false);
                mc.keyboardHandler.setClipboard("sapphire-click-sentinel");
                boolean dispatched = false;
                try {
                    screen.mouseClicked(click(point, InputConstants.MOUSE_BUTTON_LEFT), false);
                } catch (NullPointerException expected) {
                    // Vanilla checks for a player at its action dispatcher entry. Reaching that exact
                    // guard proves the real handler ran; no report, command, URL or packet is executed.
                    dispatched = "Player not available".equals(expected.getMessage()) && Arrays.stream(expected.getStackTrace())
                        .anyMatch(frame -> frame.getClassName().equals("net.minecraft.client.gui.screens.Screen")
                            && frame.getMethodName().equals("defaultHandleGameClickEvent"));
                    if (!dispatched) throw expected;
                }
                require(dispatched, "native dispatcher reached for " + action.getClass().getSimpleName());
                require(mc.keyboardHandler.getClipboard().equals("sapphire-click-sentinel"), "native click was not replaced by copying English");
                require(screen.mouseClicked(click(point, InputConstants.MOUSE_BUTTON_RIGHT), false), "right click copies original on an actionable span");
                require(mc.keyboardHandler.getClipboard().equals(source.getString()), "actionable span copies original without dispatching its left-click action");
            }

            chat.clearMessages(false);
            var source = Component.literal("Untranslated smoke fixture");
            chat.addClientSystemMessage(ChatOriginalHover.attach(Component.literal("普通译文测试"), source));
            var point = find(style -> source.getString().equals(ChatOriginalHover.original(style)), true);
            require(!screen.mouseClicked(click(point, InputConstants.MOUSE_BUTTON_LEFT), false), "left click on ordinary text is not swallowed");
            require(!screen.mouseClicked(click(point, InputConstants.MOUSE_BUTTON_MIDDLE), false), "middle click does not copy text");
            require(screen.mouseClicked(click(point, InputConstants.MOUSE_BUTTON_RIGHT), false), "right click copies ordinary translated text");
            require(mc.keyboardHandler.getClipboard().equals(source.getString()), "copied English original");
            Sapphire.CONFIG.chatCopyOriginal = false;
            mc.keyboardHandler.setClipboard("sapphire-click-sentinel");
            require(!screen.mouseClicked(click(point, InputConstants.MOUSE_BUTTON_RIGHT), false), "copy setting disables fallback");
            require(mc.keyboardHandler.getClipboard().equals("sapphire-click-sentinel"), "disabled copy leaves clipboard alone");
            Sapphire.LOGGER.info("SAPPHIRE_CHAT_CLICK_DISPATCH_SMOKE_OK: platform LEFT reached vanilla dispatcher for 7 actions; RIGHT copied original on buttons and plain text; middle/left plain text and disabled fallback passed; no network actions sent");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Chat click smoke widget setup failed", e);
        } finally {
            Sapphire.CONFIG.chatCopyOriginal = copyEnabled;
            Sapphire.CONFIG.chatOriginalHover = hoverEnabled;
            mc.keyboardHandler.setClipboard(clipboard);
            chat.clearMessages(false);
        }
    }

    private static int[] find(Predicate<Style> match, boolean includeOriginal) {
        var mc = Minecraft.getInstance();
        int height = mc.getWindow().getGuiScaledHeight();
        for (int y = height - 180; y < height - 16; y += 2) {
            for (int x = 3; x < mc.getWindow().getGuiScaledWidth(); x += 3) {
                var finder = includeOriginal ? ChatOriginalHover.finder(mc.font, x, y)
                    : new ActiveTextCollector.ClickableStyleFinder(mc.font, x, y);
                mc.gui.hud.getChat().captureClickableText(finder, height, mc.gui.hud.getGuiTicks(), ChatComponent.DisplayMode.FOREGROUND);
                if (finder.result() != null && match.test(finder.result())) return new int[]{x, y};
            }
        }
        throw new IllegalStateException("Chat click smoke target not found");
    }

    private static MouseButtonEvent click(int[] point, int button) {
        return new MouseButtonEvent(point[0], point[1], new MouseButtonInfo(button, 0));
    }

    private static void field(ChatScreen screen, String name, Object value) throws ReflectiveOperationException {
        var field = ChatScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(screen, value);
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException("Chat click dispatch smoke failed: " + message);
    }
}
