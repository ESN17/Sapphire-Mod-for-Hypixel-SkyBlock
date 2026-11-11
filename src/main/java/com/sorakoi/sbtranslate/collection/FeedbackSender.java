package com.sorakoi.sbtranslate.collection;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/** Anonymous in-game feedback about a wrong or inconsistent translation. */
public final class FeedbackSender {

    /** Only the note leaves the client: no player name, time, version or screen title. */
    public static String content(String note) {
        return note.strip() + "\n";
    }

    public static String fileName(){ return fileName(ThreadLocalRandom.current().nextInt(100_000, 1_000_000)); }

    public static String fileName(long number) {
        long value = Math.floorMod(number, 900_000L) + 100_000L;
        return "feedback-anon-" + value + ".txt";
    }

    /** Returns a user-facing result message; retries with a new number when the name exists. */
    public static CompletableFuture<String> submit(String note) {
        if (note == null || note.strip().isEmpty()) return CompletableFuture.completedFuture("请先填写反馈内容。");
        if (note.strip().length() > 2000) return CompletableFuture.completedFuture("反馈内容请控制在 2000 字以内。");
        return CompletableFuture.supplyAsync(() -> {
            byte[] body = content(note).getBytes(StandardCharsets.UTF_8);
            long seed = ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
            ChatCapture.UploadResult last = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                last = ChatCapture.uploadText(fileName(seed + attempt), body);
                if (last.ok()) return "已发送反馈，感谢！";
                if (last.code() != 409) break;
            }
            return last == null ? "发送失败" : last.message();
        });
    }

    private FeedbackSender() {}
}
