package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Regression for clientbound finish_configuration -> restoreState -> display translation. */
class ChatRestoreSafetyTest {
    @TempDir Path root;
    TranslationManager manager;
    @BeforeEach void load() throws Exception {
        Files.createDirectories(root.resolve("translations"));
        Files.writeString(root.resolve("translations/system-messages.json"),"""
            {"rules":[{"scopes":["chat"],"source":"Reward for {name}: {count}",
              "translation":"{name} 的奖励：{count}","styleMappings":{"Reward for ":" 的奖励："}}]}
            """);
        manager=new TranslationManager();manager.load(root);
    }
    @Test void unknownLegacyCodeMustBeRemovedBeforeCapturingOffsets() {
        var click=new ClickEvent.RunCommand("/viewprofile TestPlayer");
        var source=Component.literal("Reward for §vTestPlayer: ").withStyle(ChatFormatting.GREEN)
            .append(Component.literal("123").withStyle(s->s.withColor(ChatFormatting.GOLD).withClickEvent(click)));
        var before=source.copy();
        Component translated=assertDoesNotThrow(()->SystemTranslationEngine.translate(source,"chat",manager));
        assertEquals("TestPlayer 的奖励：123",translated.getString());
        assertEquals(click,StyledLoreTest.styleAt(translated,"123").getClickEvent());
        StyledLoreTest.color(translated,"123",ChatFormatting.GOLD);
        assertEquals(before,source);
    }
    @Test void staleAndInvalidCaptureRangesKeepTheOriginalClickableMessage() {
        var source=Component.literal("A 12").withStyle(s->s.withClickEvent(new ClickEvent.RunCommand("/viewauction example")));
        var before=source.copy();
        for(var bad:List.of(new TranslationManager.Piece("12",2,99,true),
                new TranslationManager.Piece("12",-1,1,true),new TranslationManager.Piece("12",3,2,true),
                new TranslationManager.Piece("99",2,4,true))) {
            var match=new TranslationManager.Match("stale",List.of(new TranslationManager.Piece("甲 ",0,4,false),bad));
            var diagnostics=new ArrayList<String>();
            assertSame(source,assertDoesNotThrow(()->TextTranslationEngine.apply(source,match,diagnostics::add)));
            assertFalse(diagnostics.isEmpty());assertEquals(before,source);
        }
    }
    @Test void unknownFormatOnlyMessageRemainsTheSameObjectWhenNoRuleMatches() {
        var source=Component.literal("Unmatched §zmessage");
        assertSame(source,SystemTranslationEngine.translate(source,"chat",manager));
    }
    @Test void regularCodesAndRepeatedHistoryRestoresKeepNumbersAndActions() {
        for(String prefix:List.of("§v","§z","§x","§a","§§","")) {
            var source=Component.literal("Reward for "+prefix+"TestPlayer: 987654321");
            var before=source.copy();
            for(int i=0;i<5;i++)assertEquals("TestPlayer 的奖励：987654321",SystemTranslationEngine.translate(source,"chat",manager).getString());
            assertEquals(before,source);
        }
    }
    @Test void failedDisplayOperationPreservesIdentityAndDoesNotDisableFollowingMessages() {
        var original=Component.literal("Clickable original").withStyle(s->s.withClickEvent(new ClickEvent.RunCommand("/original")));
        assertSame(original,DisplayTranslationSafety.preserveOriginal("test",original,()->{throw new StringIndexOutOfBoundsException(26);}));
        var next=Component.literal("Next translation");
        assertSame(next,DisplayTranslationSafety.preserveOriginal("test",original,()->next));
        assertThrows(AssertionError.class,()->DisplayTranslationSafety.preserveOriginal("test",original,()->{throw new AssertionError("Not a recoverable display exception");}));
    }
}
