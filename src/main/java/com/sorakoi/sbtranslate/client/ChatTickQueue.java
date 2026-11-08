package com.sorakoi.sbtranslate.client;

import com.sorakoi.sbtranslate.llm.LlmTranslationService;
import java.util.ArrayDeque;
import java.util.function.Consumer;

/** Client-thread work that must use Minecraft components or GUI state for the current session. */
final class ChatTickQueue<T> {
    static final int MAX_PER_TICK = 8;
    private static final long BUDGET_NANOS = 2_000_000;
    private final ArrayDeque<T> messages = new ArrayDeque<>();
    private long generation;

    boolean offer(T message, long currentGeneration) {
        if (generation != currentGeneration) {
            clear();
            generation = currentGeneration;
        }
        if (messages.size() >= LlmTranslationService.MAX_PENDING) return false;
        messages.addLast(message);
        return true;
    }

    void drain(long currentGeneration, Consumer<T> process) {
        if (messages.isEmpty()) return;
        if (generation != currentGeneration) {
            clear();
            return;
        }
        long start = System.nanoTime();
        for (int i = 0; i < MAX_PER_TICK && !messages.isEmpty(); i++) {
            process.accept(messages.removeFirst());
            if (System.nanoTime() - start >= BUDGET_NANOS) break;
        }
    }

    boolean isEmpty() { return messages.isEmpty(); }
    void clear() { messages.clear(); }
}
