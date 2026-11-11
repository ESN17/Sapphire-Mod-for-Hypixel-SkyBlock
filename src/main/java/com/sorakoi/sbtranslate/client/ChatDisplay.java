package com.sorakoi.sbtranslate.client;

import com.sorakoi.sbtranslate.DisplayTranslationHooks;
import com.sorakoi.sbtranslate.Sapphire;
import com.sorakoi.sbtranslate.translation.DisplayTranslationSafety;
import com.sorakoi.sbtranslate.translation.TranslationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.chat.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Bounded, identity-keyed display substitutions; original messages are never rewritten. */
public final class ChatDisplay {
    private static final Map<Component,Component> ONLINE=new IdentityHashMap<>();
    private static final ArrayDeque<Component> ORDER=new ArrayDeque<>();
    private static final int HISTORY_LIMIT = 256;
    private static final int REFRESH_INTERVAL_TICKS = 4;
    private static final int MAX_REPLACEMENTS_PER_REFRESH = 16;
    private static final Map<Component, Supplier<Component>> pending = new IdentityHashMap<>();
    private static final ArrayDeque<Component> pendingOrder = new ArrayDeque<>();
    private static final ChatRefreshCache HISTORY = new ChatRefreshCache();
    private static final ChatTickQueue<GuiMessage> preparation = new ChatTickQueue<>();
    private static final ChatTickQueue<Runnable> additionalLines = new ChatTickQueue<>();
    private static final AtomicLong layoutRevision = new AtomicLong();
    private static long renderedRevision;
    private static boolean wasActive;
    private static int ticksUntilRefresh;

    public static void invalidateLayouts() { layoutRevision.incrementAndGet(); }
    public static void clear(){
        ONLINE.clear();
        ORDER.clear();
        HISTORY.clear();
        preparation.clear();
        additionalLines.clear();
        pending.clear();
        pendingOrder.clear();
        ticksUntilRefresh=0;
    }
    public static void tick(){
        if (!preparation.isEmpty()) preparation.drain(Sapphire.LLM.generation(), ChatDisplay::prepare);
        if (!additionalLines.isEmpty()) additionalLines.drain(Sapphire.LLM.generation(), Runnable::run);
        refreshIfDue(() -> Minecraft.getInstance().gui.hud.getChat().rescaleChat());
    }
    static void replace(Component raw, Supplier<Component> translated) {
        if (pending.put(raw, translated) == null) pendingOrder.addLast(raw);
        // Vanilla keeps 100 messages. Bound late completions beyond that history as well.
        if (pendingOrder.size() > HISTORY_LIMIT) pending.remove(pendingOrder.removeFirst());
    }
    static boolean refreshIfDue(Runnable refresh) {
        if (ticksUntilRefresh > 0) ticksUntilRefresh--;
        if (pendingOrder.isEmpty() || ticksUntilRefresh > 0) return false;
        boolean changed = false;
        for (int i = 0; i < MAX_REPLACEMENTS_PER_REFRESH && !pendingOrder.isEmpty(); i++) {
            Component raw = pendingOrder.removeFirst();
            var reply = pending.remove(raw);
            Component translated = DisplayTranslationSafety.preserveOriginal("online chat", null, reply);
            if (translated == null) continue;
            if (ONLINE.put(raw, translated) == null) ORDER.addLast(raw);
            if (ORDER.size() > HISTORY_LIMIT) ONLINE.remove(ORDER.removeFirst());
            changed = true;
        }
        if (!changed) return false;
        ticksUntilRefresh = REFRESH_INTERVAL_TICKS;
        HISTORY.replay(refresh);
        return true;
    }
    public static GuiMessage display(GuiMessage message){
        return DisplayTranslationSafety.preserveOriginal("chat display", message, () -> {
            boolean active = cacheActive();
            long revision = layoutRevision.get();
            if (active != wasActive || revision != renderedRevision) {
                HISTORY.clear();
                wasActive = active;
                renderedRevision = revision;
            }
            if (!active) return displayUnchecked(message);
            return HISTORY.display(message, ONLINE.get(message.content()), () -> displayUnchecked(message));
        });
    }
    private static boolean cacheActive() {
        return Sapphire.LLM_CONFIG.enabled && Sapphire.CONFIG.apiChatReplace && Sapphire.active("chat");
    }
    public static List<FormattedCharSequence> splitLines(GuiMessage message, Font font,
            int width, Supplier<List<FormattedCharSequence>> split) {
        return cacheActive() ? HISTORY.lines(message, font, width, split) : split.get();
    }
    private static GuiMessage displayUnchecked(GuiMessage message){
        Component raw=message.content(),text=raw;
        if(Sapphire.active("chat")) {
            Component online = Sapphire.CONFIG.apiChatReplace ? ONLINE.get(raw) : null;
            if (online != null) text = ChatOriginalHover.attach(online, raw);
            else {
                if(message.source()!=GuiMessageSource.PLAYER)text=DisplayTranslationHooks.text(raw,"chat");
                if(text==raw && raw.getString().contains("[SkyHanni]"))text=com.sorakoi.sbtranslate.compat.SkyHanniCompat.chat(raw);
                if(text==raw)text=com.sorakoi.sbtranslate.compat.OdinCompat.chat(raw);
                // Still English after every dictionary/API attempt: offer it to the opt-in capture.
                // Signed player chat is never recorded; party/guild lines are filtered by ChatCapture.
                if(text==raw && message.source()!=GuiMessageSource.PLAYER) com.sorakoi.sbtranslate.collection.ChatCapture.observe(raw);
            }
        }
        // Sapphire's own notices keep their text unchanged: rank decoration must not rewrite file paths or names in them.
        if(!TranslationManager.plain(text.getString()).startsWith(SapphireNotifications.PREFIX)) text=SapphireRanks.chat(text);
        return text==raw?message:new GuiMessage(message.addedTime(),text,message.signature(),message.source(),message.tag());
    }
    public static void receive(GuiMessage message){
        if (!Sapphire.LLM_CONFIG.enabled || !Sapphire.active("chat") || Minecraft.getInstance().getConnection() == null) return;
        DisplayTranslationSafety.preserveOriginal("chat receive",message,()->{enqueue(message);return message;});
    }
    private static void enqueue(GuiMessage message) {
        if (message.source() == GuiMessageSource.SYSTEM_CLIENT) {
            String plain=TranslationManager.plain(message.content().getString());
            if (!plain.startsWith("[SkyHanni]") && !plain.startsWith("Odin »")) return;
        }
        if (!preparation.offer(message, Sapphire.LLM.generation())) OnlineChatTranslation.failure("翻译队列已满，保留原文");
    }
    private static void prepare(GuiMessage message) {
        DisplayTranslationSafety.preserveOriginal("chat receive",message,()->{receiveUnchecked(message);return message;});
    }
    private static void receiveUnchecked(GuiMessage message){
        if(message.source()==GuiMessageSource.SYSTEM_CLIENT){
            if(com.sorakoi.sbtranslate.compat.SkyHanniCompat.chat(message.content())!=message.content() ||
                com.sorakoi.sbtranslate.compat.OdinCompat.chat(message.content())!=message.content())return;
        }
        OnlineChatTranslation.receive(message.content(),message.source()==GuiMessageSource.PLAYER,"",reply->{
            if(Sapphire.CONFIG.apiChatReplace) replace(message.content(),reply);
            else if (!additionalLines.offer(() -> append(message, reply), Sapphire.LLM.generation()))
                OnlineChatTranslation.failure("翻译队列已满，保留原文");
        });
    }
    private static void append(GuiMessage message, Supplier<Component> reply) {
        DisplayTranslationSafety.preserveOriginal("append online chat", message, () -> {
            Component translated = reply.get();
            if (translated != null) Minecraft.getInstance().gui.hud.getChat()
                .addClientSystemMessage(ChatOriginalHover.attach(translated, message.content()));
            return message;
        });
    }
}
