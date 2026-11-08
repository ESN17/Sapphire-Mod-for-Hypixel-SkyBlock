package com.sorakoi.sbtranslate.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Presentation for Sapphire-owned notices only; never wraps incoming server or player chat. */
public final class SapphireNotifications {
    public static final String PREFIX = "[Sapphire] ";
    public enum Tone {
        INFO(ChatFormatting.GRAY), SUCCESS(ChatFormatting.GREEN),
        WARNING(ChatFormatting.YELLOW), ERROR(ChatFormatting.RED);
        private final ChatFormatting color;
        Tone(ChatFormatting color) { this.color = color; }
    }
    private SapphireNotifications() {}

    public static MutableComponent message(Tone tone, String body) {
        return message(tone, Component.literal(body));
    }

    public static MutableComponent message(Tone tone, Component body) {
        // Keep prefix styling independent from the body, including click/hover events.
        return Component.empty()
            .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal("Sapphire").withStyle(ChatFormatting.BLUE))
            .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.empty().withStyle(tone.color).append(body.copy()));
    }

    /** Existing services return plain status text, shared with logs and status screens. */
    public static Tone tone(String status) {
        if (status.contains("失败") || status.contains("无法") || status.contains("错误") || status.contains("未能") || status.contains("请求超时")) return Tone.ERROR;
        if (status.contains("未发送") || status.contains("尚未发送") || status.startsWith("请") || status.contains("未配置")
            || status.contains("须为") || status.contains("超出允许范围") || status.contains("已限速") || status.contains("队列已满")) return Tone.WARNING;
        if (status.startsWith("正在")) return Tone.INFO;
        if (status.contains("成功") || status.contains("已加载") || status.contains("已保存") || status.contains("保存完成") || status.contains("最新版") || status.contains("已开启")) return Tone.SUCCESS;
        return Tone.INFO;
    }

    public static MutableComponent status(String status) { return message(tone(status), status); }
}
