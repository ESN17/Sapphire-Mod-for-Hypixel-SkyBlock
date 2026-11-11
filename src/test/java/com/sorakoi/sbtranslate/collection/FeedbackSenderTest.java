package com.sorakoi.sbtranslate.collection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeedbackSenderTest {
    @Test void onlyTheNoteIsUploaded() {
        String text = FeedbackSender.content("  洗练应为重铸  ");
        assertEquals("洗练应为重铸\n", text);
        assertFalse(text.contains("玩家") || text.contains("时间") || text.contains("界面"), text);
    }

    @Test void fileNamesAreAnonymous() {
        String name = FeedbackSender.fileName(1_234_567_890L);
        assertTrue(name.matches("feedback-anon-[1-9][0-9]{5}\\.txt"), name);
        assertTrue(FeedbackSender.fileName().matches("feedback-anon-[1-9][0-9]{5}\\.txt"));
    }

    @Test void emptyNotesAreRejectedLocally() throws Exception {
        assertTrue(FeedbackSender.submit("  ").get().contains("请先填写"));
        assertTrue(FeedbackSender.submit("x".repeat(2001)).get().contains("2000"));
    }
}
