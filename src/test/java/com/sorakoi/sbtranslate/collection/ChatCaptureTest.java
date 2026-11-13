package com.sorakoi.sbtranslate.collection;

import com.sorakoi.sbtranslate.config.SapphireConfig;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatCaptureTest {
    @TempDir Path root;

    private SapphireConfig config(String token) {
        var config = new SapphireConfig();
        config.collectChatCapture = true;
        config.privacyAccepted = true;
        config.chatCaptureDirectory = root.toString();
        config.chatCaptureUploadUrl = System.getenv().getOrDefault("SAPPHIRE_CAPTURE_URL",
                "https://sapphire.esn17cabin.club/untranslated/upload");
        config.chatCaptureToken = token;
        if(token.isEmpty())config.chatCaptureUploadUrl="https://localhost.invalid/upload";
        return config;
    }

    @Test void styledLinesKeepColourCodesAndLineBreaks() {
        var message = Component.literal("§7In theory, you are constantly teleporting")
                .append(Component.literal("\n§cRed line").withStyle(style -> style.withColor(0xFF5555)));
        List<String> lines = ChatCapture.styledLines(message);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).startsWith("§7"), lines.get(0));
        assertTrue(lines.get(1).contains("§c"), lines.get(1));
        assertTrue(lines.get(0).contains("teleporting"));
    }

    @Test void rankDecorationDoesNotHideAnEnglishSystemNotice() throws Exception {
        ChatCapture.configure(config(""));ChatCapture.startSession("AuctionSmoke");
        var raw=Component.literal("§a[MVP+] Example created a BIN auction for Combat Exp Boost at 1,000,000 coins!");
        ChatCapture.observeDisplayed(raw,Component.literal("§c[SAPPDEV] Example 创建了一口价拍卖"),true);
        ChatCapture.observeDisplayed(raw,Component.literal(raw.getString().replace("[MVP+]","[SAPPDEV]")),true);
        ChatCapture.stop();
        try(var files=Files.list(root)){
            assertEquals(List.of(raw.getString()),Files.readAllLines(files.findFirst().orElseThrow()));
        }
    }

    @Test void uiOnlySessionKeepsOriginalColourAndDeduplicatesNumbers() throws Exception {
        assertTrue(UiCapture.allowMenuLine("Auctions Browser","§aBuy price: 5,000 coins"));
        assertTrue(UiCapture.allowMenuLine("Pets","Click to summon!"));
        assertFalse(UiCapture.allowMenuLine("Auctions Browser","Seller: PrivateName"));
        assertFalse(UiCapture.allowMenuLine("Pets","My Custom Pet Name"));
        var cfg=config("");
        cfg.collectChatCapture=false;
        ChatCapture.configure(cfg);
        assertFalse(ChatCapture.uiActive());
        cfg.collectUiCapture=true;
        ChatCapture.configure(cfg);
        ChatCapture.startSession("UiSmoke");
        ChatCapture.observe(Component.literal("An English chat message!"));
        ChatCapture.observeUi("gui-tooltip",Component.literal("§aBuy 15 tickets!"));
        ChatCapture.observeUi("gui-tooltip",Component.literal("§aBuy 30 tickets!"));
        ChatCapture.observeUi("gui-button",Component.literal("§aBuy 15 tickets!"));
        ChatCapture.observeUi("gui-tooltip",Component.literal("Seller: PrivateName"));
        ChatCapture.observeUi("gui-tooltip",Component.literal("已翻译"));
        ChatCapture.stop();
        try(var files=Files.list(root)) {
            assertEquals(List.of("[Sapphire:UI:gui-tooltip] §aBuy 15 tickets!",
                                 "[Sapphire:UI:gui-button] §aBuy 15 tickets!"),Files.readAllLines(files.findFirst().orElseThrow()));
        }
    }

    @Test void islandAndUnknownAreasExcludeNpcDialogueButRetainEnglishSystemMessages() throws Exception {
        ChatCapture.configure(config(""));
        ChatCapture.startSession("IslandSmoke");
        for(String place:List.of("⏣ Your Island","⏣ 你的岛屿","")){
            boolean publicArea=com.sorakoi.sbtranslate.environment.SkyBlockContext.publicLocation(List.of(place));
            assertFalse(publicArea);
            ChatCapture.observe(Component.literal("§e[NPC] Jerry: Private text\nPrivate continuation"),publicArea);
        }
        ChatCapture.observe(Component.literal("§aThe event has started!"),false);
        boolean hub=com.sorakoi.sbtranslate.environment.SkyBlockContext.publicLocation(List.of("⏣ Village"));
        assertTrue(hub);
        ChatCapture.observe(Component.literal("§e[NPC] Jerry: Public text"),hub);
        ChatCapture.retryPending();
        ChatCapture.stop();
        try(var files=Files.list(root)){
            var text=Files.readString(files.findFirst().orElseThrow());
            assertFalse(text.contains("Private"));assertTrue(text.contains("Public text"));
            assertTrue(text.contains("§aThe event has started!"));
        }
    }

    @Test void chinesePlayerChatAndUrlsAreNotRecorded() throws Exception {
        ChatCapture.configure(config(""));
        ChatCapture.startSession("ESN17_OwO");
        ChatCapture.observe(Component.literal("§a你有 25 份未领取的物品!"));
        ChatCapture.observe(Component.literal("§7[VIP] Steve: hello there"));
        ChatCapture.observe(Component.literal("§a[MVP§6+§a] Theblueplayer67§f: anything helps really"));
        ChatCapture.observe(Component.literal("§9Party §8> §a[MVP§6+§a] Steve§f: party message"));
        ChatCapture.observe(Component.literal("§2Guild §8> Steve [MVP+]: guild message"));
        ChatCapture.observe(Component.literal("§dFrom §a[MVP+] Steve§7: direct message"));
        ChatCapture.observe(Component.literal("§8[§a135§8] §7☃ §a[VIP] Crenoxi§f: ikr"));
        ChatCapture.observe(Component.literal("§8[§725§8] §b[MVP§f+§b] kolariwnl§f: but if u dont think so then whos to tell u otherwise"));
        ChatCapture.observe(Component.literal("§8[§a135§8] §7Server restart in 5 minutes"));
        ChatCapture.observe(Component.literal("§7Visit https://example.com now"));
        ChatCapture.stop();
        Thread.sleep(300);
        try (var files = Files.list(root)) {
            assertEquals(0, files.count(), "player chat must never be written");
        }
    }

    @Test void npcDialogueIsStillRecorded() throws Exception {
        ChatCapture.configure(config(""));
        ChatCapture.startSession("ESN17_OwO");
        ChatCapture.observe(Component.literal("§e[NPC] Jerry§f: In theory, you are constantly teleporting to yourself."), true);
        ChatCapture.stop();
        Path file;
        try (var files = Files.list(root)) { file = files.findFirst().orElseThrow(); }
        assertEquals(1, Files.readAllLines(file).size());
    }

    @Test void englishLinesUseTheAgreedFileName() throws Exception {
        ChatCapture.configure(config(""));
        ChatCapture.startSession("ESN17_OwO");
        ChatCapture.observe(Component.literal("§7In theory, you are constantly teleporting to yourself."));
        ChatCapture.observe(Component.literal("§e§lSALE! §7Get 50% off!"));
        ChatCapture.observe(Component.literal("§7In theory, you are constantly teleporting to yourself."));
        ChatCapture.stop();
        Path file;
        try (var files = Files.list(root)) { file = files.findFirst().orElseThrow(); }
        assertTrue(file.getFileName().toString().matches("untranslated-ESN17_OwO-\\d{8}-1\\.txt"), file.getFileName().toString());
        List<String> lines = Files.readAllLines(file);
        assertEquals(2, lines.size(), "duplicates must be dropped: " + lines);
        assertTrue(lines.get(0).startsWith("§7"), lines.get(0));
        assertTrue(lines.get(1).contains("§e§lSALE!"), lines.get(1));
        String raw = Files.readString(file);
        assertFalse(raw.contains("ESN17") || raw.contains("2026"), "no player name or date inside the file");
    }

    @Test void secondSessionUsesNextNumber() throws Exception {
        ChatCapture.configure(config(""));
        ChatCapture.startSession("ESN17_OwO");
        ChatCapture.observe(Component.literal("§7First line"));
        ChatCapture.stop();
        ChatCapture.startSession("ESN17_OwO");
        ChatCapture.observe(Component.literal("§7Second line"));
        ChatCapture.stop();
        try (var files = Files.list(root)) {
            List<String> names = files.map(p -> p.getFileName().toString()).sorted().toList();
            assertEquals(2, names.size(), names.toString());
            assertTrue(names.get(1).endsWith("-2.txt"), names.toString());
        }
    }

    @Test void chatCaptureIsOnByDefaultAndNotificationsToo() {
        var defaults = new SapphireConfig();
        assertTrue(defaults.collectChatCapture, "chat capture should default to on");
        assertTrue(defaults.collectUntranslated, "local untranslated collection should default to on too");
        assertTrue(defaults.uploadNotifications, "upload notice should default to on");
        assertFalse(defaults.privacyAccepted, "consent must be asked on first launch");
    }

    @Test void uploadNotificationMatchesTheRequestedText() {
        String text = com.sorakoi.sbtranslate.client.SapphireNotifications.message(
                com.sorakoi.sbtranslate.client.SapphireNotifications.Tone.INFO,
                "已收集的未翻译文本正在上传中……").getString();
        assertEquals("[Sapphire] 已收集的未翻译文本正在上传中……", text);
    }

    @Test void uploadDeletesLocalFileWhenConfigured() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("SAPPHIRE_CAPTURE_LIVE")),
                "live capture upload test is opt-in");
        var settings = config("");
        settings.chatCaptureUploadUrl = "https://sapphire.esn17cabin.club/untranslated/upload";
        ChatCapture.configure(settings);
        ChatCapture.startSession("SapphireSmoke");
        ChatCapture.observe(Component.literal("§7Live capture smoke test " + System.currentTimeMillis()));
        assertEquals(1, ChatCapture.lineCount());
        ChatCapture.stop();
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            while (true) {
                try (var files = Files.list(root)) {
                    if (files.findAny().isEmpty()) return;
                }
                Thread.sleep(250);
            }
        });
    }
}
