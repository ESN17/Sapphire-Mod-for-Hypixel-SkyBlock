package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.client.SapphireNotifications;
import com.sorakoi.sbtranslate.client.SapphireNotifications.Tone;
import com.sorakoi.sbtranslate.sync.DictionaryUpdater;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SapphireNotificationsTest {
    @Test void everyToneHasIndependentBluePrefixAndReadableBody() {
        var colors = new ChatFormatting[]{ChatFormatting.GRAY, ChatFormatting.GREEN, ChatFormatting.YELLOW, ChatFormatting.RED};
        for (Tone tone : Tone.values()) {
            var result = SapphireNotifications.message(tone, "通知正文");
            assertEquals("[Sapphire] 通知正文", result.getString());
            StyledLoreTest.color(result, "Sapphire", ChatFormatting.BLUE);
            StyledLoreTest.color(result, "通知正文", colors[tone.ordinal()]);
        }
    }

    @Test void recoveryAndTranslationRetainEventsWithoutMakingPrefixClickable() {
        var click = new ClickEvent.SuggestCommand("你好");
        var hover = new HoverEvent.ShowText(Component.literal("原文"));
        var body = Component.literal("点击恢复原文").withStyle(s -> s.withClickEvent(click).withHoverEvent(hover))
            .append(Component.literal(" 玩家名").withStyle(ChatFormatting.WHITE));
        var before = body.copy();
        var result = SapphireNotifications.message(Tone.WARNING, body);
        result.visit((style, text) -> {
            if (text.contains("Sapphire")) { assertNull(style.getClickEvent()); assertNull(style.getHoverEvent()); }
            if (text.contains("恢复")) { assertEquals(click, style.getClickEvent()); assertEquals(hover, style.getHoverEvent()); }
            return Optional.empty();
        }, Style.EMPTY);
        StyledLoreTest.color(result, "玩家名", ChatFormatting.WHITE);
        assertEquals(before, body);
        body.append("后续变动");
        assertFalse(result.getString().contains("后续变动"));
    }

    @Test void updateFailuresNeverLookSuccessful() {
        for (var status : DictionaryUpdater.Status.values()) {
            String text = new DictionaryUpdater.Result(status, "", null, "").message();
            assertEquals(status == DictionaryUpdater.Status.UPDATED || status == DictionaryUpdater.Status.CURRENT
                ? Tone.SUCCESS : Tone.ERROR, SapphireNotifications.tone(text));
        }
        assertEquals(Tone.ERROR, SapphireNotifications.tone("重载失败，保留原词典"));
        assertEquals(Tone.SUCCESS, SapphireNotifications.tone("词典已加载：8937 个物品"));
        assertEquals(Tone.WARNING, SapphireNotifications.tone("请填写完整原文"));
        assertEquals(Tone.INFO, SapphireNotifications.tone("正在后台加载词典..."));
        assertEquals(Tone.INFO, SapphireNotifications.tone("正在翻译，成功后自动发送英文..."));
        assertEquals(Tone.ERROR, SapphireNotifications.tone("请求超时，保留原文"));
        assertEquals(Tone.WARNING, SapphireNotifications.tone("API 地址须为完整 HTTPS 地址（本机服务可用 HTTP）"));
        assertEquals(Tone.WARNING, SapphireNotifications.tone("已限速，保留原文"));
    }
}
