package com.sorakoi.sbtranslate.client;

import com.sorakoi.sbtranslate.Sapphire;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * First-launch consent. The mod can upload untranslated game text, so the
 * player has to accept this explanation before the main menu; declining quits
 * the game instead of silently disabling the feature.
 *
 * Text between 【 and 】 is rendered bold; the brackets themselves are not shown.
 * Chinese has no spaces, so the body is wrapped per character and can scroll.
 */
public final class ConsentScreen extends Screen {
    private static final String BODY = """
            欢迎您使用Sapphire，一站式Hypixel SkyBlock中文翻译模组！

            当前版本模组会在您同意后，【把游戏中仍然显示英文的文本上传到译名库服务器，用于补全中文翻译。模组仅会收集您在SkyBlock中看到、但还没有被译成中文的聊天行（系统提示、NPC对话等），不会收集账号、密码、登录令牌、身份凭据、电脑上的其他文件、剪贴板或浏览记录。文件名会带上您的游戏名和日期，只用于区分不同玩家的记录。】聊天漏译文本会临时保存在D:\\sapphire-source，通过HTTPS上传到Sapphire译名库服务器，上传成功后本地临时文件会被删除，失败则保留，并在下次启动时重试。您可以随时在/sapphire → 高级设置中关闭采集。

            选择“同意并继续”表示您已了解并接受以上说明。选择“不同意并退出”会立即关闭游戏。如果您不想使用含有此功能的模组，请前往模组官方交流群（934040643）或官方下载渠道（GitHub、Modrinth）下载1.0.1以及之前的版本。
            """;

    private record Segment(String text, boolean bold) {}

    private final Screen parent;
    private final List<List<Segment>> lines = new ArrayList<>();
    private int scroll;
    private int panelWidth, panelHeight, left, top, bodyX, bodyTop, bodyBottom, bodyWidth;

    public ConsentScreen(Screen parent) {
        super(Component.literal("Sapphire · 数据与隐私说明"));
        this.parent = parent;
    }

    @Override protected void init() {
        panelWidth = Math.min(660, width - 24);
        panelHeight = Math.min(460, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        bodyX = left + 16;
        bodyWidth = panelWidth - 32;
        bodyTop = top + 66;
        bodyBottom = top + panelHeight - 58;
        lines.clear();
        for (String paragraph : BODY.split("\n", -1)) lines.addAll(wrap(parse(paragraph), bodyWidth - 10));
        int half = (bodyWidth - 10) / 2;
        addRenderableWidget(SettingsTheme.primary("同意并继续", bodyX, top + panelHeight - 42, half, 22, this::accept));
        addRenderableWidget(SettingsTheme.button("不同意并退出", bodyX + half + 10, top + panelHeight - 42, half, 22, this::decline));
        String smoke = System.getProperty("sapphire.consentSmoke", "");
        if (smoke.equals("accept") || smoke.equals("decline")) {
            Sapphire.LOGGER.info("SAPPHIRE_CONSENT_SMOKE {}", smoke);
            java.util.concurrent.CompletableFuture.delayedExecutor(400, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .execute(() -> net.minecraft.client.Minecraft.getInstance().execute(
                            smoke.equals("accept") ? this::accept : this::decline));
        }
    }

    /** Turns 【bold】 markers into bold segments and drops the brackets. */
    private static List<Segment> parse(String paragraph) {
        List<Segment> segments = new ArrayList<>();
        if (paragraph.isEmpty()) return segments;
        int index = 0;
        while (index < paragraph.length()) {
            int open = paragraph.indexOf('【', index);
            if (open < 0) {
                segments.add(new Segment(paragraph.substring(index), false));
                break;
            }
            if (open > index) segments.add(new Segment(paragraph.substring(index, open), false));
            int close = paragraph.indexOf('】', open + 1);
            if (close < 0) {
                segments.add(new Segment(paragraph.substring(open + 1), true));
                break;
            }
            segments.add(new Segment(paragraph.substring(open + 1, close), true));
            index = close + 1;
        }
        return segments;
    }

    private List<List<Segment>> wrap(List<Segment> segments, int width) {
        List<List<Segment>> result = new ArrayList<>();
        if (segments.isEmpty()) {
            result.add(List.of());
            return result;
        }
        List<Segment> current = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        boolean bold = false;
        int used = 0;
        for (Segment segment : segments) {
            for (int i = 0; i < segment.text().length(); ) {
                int codePoint = segment.text().codePointAt(i);
                String character = new String(Character.toChars(codePoint));
                i += Character.charCount(codePoint);
                int advance = font.width(character) + (segment.bold() ? 1 : 0);
                boolean punctuation = "。，、；：！？）】」』".indexOf(character) >= 0;
                if (used + advance > width && !text.isEmpty() && !punctuation) {
                    current.add(new Segment(text.toString(), bold));
                    result.add(current);
                    current = new ArrayList<>();
                    text.setLength(0);
                    used = 0;
                }
                if (segment.bold() != bold && !text.isEmpty()) {
                    current.add(new Segment(text.toString(), bold));
                    text.setLength(0);
                }
                bold = segment.bold();
                text.append(character);
                used += advance;
            }
        }
        if (!text.isEmpty() || current.isEmpty()) current.add(new Segment(text.toString(), bold));
        result.add(current);
        return result;
    }

    private int visibleLines() {
        return Math.max(1, (bodyBottom - bodyTop) / 10);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        int max = Math.max(0, lines.size() - visibleLines());
        scroll = Math.clamp(scroll + (vertical > 0 ? -2 : vertical < 0 ? 2 : 0), 0, max);
        return true;
    }

    private void accept() {
        Sapphire.CONFIG.privacyAccepted = true;
        Sapphire.saveConfig();
        Sapphire.LOGGER.info("SAPPHIRE_CONSENT_ACCEPTED");
        minecraft.gui.setScreen(parent);
    }

    private void decline() {
        Sapphire.LOGGER.info("SAPPHIRE_CONSENT_DECLINED");
        minecraft.stop();
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        SettingsTheme.frame(g, left, top, panelWidth, panelHeight, "数据与隐私说明", "");
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int y = bodyTop;
        for (int i = scroll; i < lines.size() && y <= bodyBottom - 10; i++, y += 10) {
            int x = bodyX;
            for (Segment segment : lines.get(i)) {
                if (segment.text().isEmpty()) continue;
                var text = Component.literal(segment.text())
                        .withStyle(style -> style.withBold(segment.bold()).withColor(SettingsTheme.TEXT));
                g.text(font, text, x, y, SettingsTheme.TEXT, false);
                x += font.width(segment.text()) + (segment.bold() ? 1 : 0);
            }
        }
        if (lines.size() > visibleLines()) {
            String hint = (scroll > 0 ? "↑ " : "") + (scroll < lines.size() - visibleLines() ? "滚动查看更多 ↓" : "");
            g.text(font, Component.literal(hint), bodyX, top + panelHeight - 54, SettingsTheme.MUTED, false);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override public void onClose() { decline(); }

    @Override public boolean isPauseScreen() { return false; }

    @Override public boolean shouldCloseOnEsc() { return false; }
}
