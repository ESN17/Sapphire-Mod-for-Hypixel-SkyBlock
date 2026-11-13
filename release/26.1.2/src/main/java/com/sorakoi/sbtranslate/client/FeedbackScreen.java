package com.sorakoi.sbtranslate.client;

import com.sorakoi.sbtranslate.collection.FeedbackSender;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Reports a wrong or inconsistent translation. The note is uploaded anonymously. */
public final class FeedbackScreen extends Screen {
    private final Screen parent;
    private MultiLineEditBox text;
    private String status = "";
    private boolean sending;

    public FeedbackScreen(Screen parent) {
        super(Component.literal("Sapphire · 反馈译文问题"));
        this.parent = parent;
    }

    @Override protected void init() {
        int panelWidth = Math.min(520, width - 24), panelHeight = Math.min(300, height - 16);
        int left = (width - panelWidth) / 2, top = (height - panelHeight) / 2;
        int x = left + 12, w = panelWidth - 24;
        text = MultiLineEditBox.builder().setX(x).setY(top + 70)
                .setPlaceholder(Component.literal("例如：铁砧菜单里的“洗练”应为“重铸”；物品描述第二行数值对不上"))
                .build(font, w, Math.max(70, panelHeight - 140), Component.literal("反馈内容"));
        text.setCharacterLimit(2000);
        addRenderableWidget(text);
        int half = (w - 8) / 2;
        addRenderableWidget(SettingsTheme.primary("发送反馈", x, top + panelHeight - 32, half, 20, this::send));
        addRenderableWidget(SettingsTheme.button("取消", x + half + 8, top + panelHeight - 32, half, 20, this::onClose));
    }

    private void send() {
        if (sending) return;
        String note = text == null ? "" : text.getValue();
        if (note.isBlank()) {
            status = "请先填写反馈内容。";
            return;
        }
        sending = true;
        status = "正在发送…";
        FeedbackSender.submit(note).thenAccept(result -> minecraft.execute(() -> {
            sending = false;
            status = result;
            if (result.startsWith("已发送")) {
                minecraft.setScreen(parent);
                minecraft.gui.getChat().addClientSystemMessage(SapphireNotifications.status(result));
            } else if (minecraft.screen == this) {
                rebuildWidgets();
            }
        }));
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        int panelWidth = Math.min(520, width - 24), panelHeight = Math.min(300, height - 16);
        int left = (width - panelWidth) / 2, top = (height - panelHeight) / 2;
        SettingsTheme.frame(g, left, top, panelWidth, panelHeight, "反馈译文问题", "匿名发送 · 不附带玩家名、时间或版本");
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int panelWidth = Math.min(520, width - 24), panelHeight = Math.min(300, height - 16);
        int left = (width - panelWidth) / 2, top = (height - panelHeight) / 2;
        int x = left + 12, w = panelWidth - 24;
        g.text(font, Component.literal("说明具体是哪里不对、正确应该是什么；不需要写游戏账号信息。"),
                x, top + 52, SettingsTheme.MUTED, false);
        String message = status.isBlank() ? "只上传你填写的这段文字，可在译名库网站的管理员页面查看。" : status;
        g.text(font, font.plainSubstrByWidth(message, w), x, top + panelHeight - 52,
                sending ? SettingsTheme.MUTED : SettingsTheme.TEXT, false);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
