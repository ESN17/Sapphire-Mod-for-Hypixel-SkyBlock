package com.sorakoi.sbtranslate.client;

import com.sorakoi.sbtranslate.collection.ChatCapture;
import com.sorakoi.sbtranslate.config.SapphireConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dev-only end-to-end check for the chat capture: writes one English line and
 * one player-chat line, closes the session, uploads, then verifies the local
 * file was deleted after a successful upload.
 *
 * Enabled with SAPPHIRE_CAPTURE_SMOKE=1; token from SAPPHIRE_CAPTURE_TOKEN,
 * optional directory from SAPPHIRE_CAPTURE_DIR (defaults to a temp folder).
 */
public final class CaptureSmokeTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("sapphire-capture-smoke");

    public static boolean enabled() {
        return System.getenv("SAPPHIRE_CAPTURE_SMOKE") != null || Boolean.getBoolean("sapphire.captureSmoke");
    }

    public static void start(Minecraft client) {
        try {
            var settings = smokeSettings();
            Path directory = Path.of(settings.getProperty("dir",
                    Path.of(System.getProperty("java.io.tmpdir"), "sapphire-capture-smoke").toString()));
            if (Files.exists(directory)) {
                try (var files = Files.list(directory)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) Files.deleteIfExists(file);
                }
            }
            var config = new SapphireConfig();
            config.collectChatCapture = true;
            config.privacyAccepted = true;
            config.chatCaptureDirectory = directory.toString();
            config.chatCaptureUploadUrl = settings.getProperty("url", "https://sapphire.esn17cabin.club/untranslated/upload");
            config.chatCaptureToken = settings.getProperty("token", "");
            ChatCapture.configure(config);
            ChatCapture.startSession("SapphireSmoke");
            ChatCapture.observe(Component.literal("§7In theory, you are constantly teleporting to yourself. smoke-" + System.currentTimeMillis()));
            ChatCapture.observe(Component.literal("§a[MVP§6+§a] Tester§f: player chat must be ignored"));
            ChatCapture.observe(Component.literal("§a你有 25 份未领取的物品!"));
            ChatCapture.stop();
            LOGGER.info("SAPPHIRE_CAPTURE_SMOKE_STARTED dir={} status={}", directory, ChatCapture.status());
            com.sorakoi.sbtranslate.collection.FeedbackSender
                    .submit("冒烟测试反馈 feedback-" + System.currentTimeMillis())
                    .thenAccept(result -> LOGGER.info("SAPPHIRE_FEEDBACK_SMOKE {} {}", result, result.startsWith("已发送") ? "OK" : "FAIL"));
            Thread worker = new Thread(() -> {
                long deadline = System.nanoTime() + 90_000_000_000L;
                while (System.nanoTime() < deadline) {
                    try {
                        boolean empty;
                        try (var files = Files.list(directory)) {
                            empty = files.findAny().isEmpty();
                        }
                        if (empty) {
                            LOGGER.info("SAPPHIRE_CAPTURE_SMOKE_OK");
                            client.execute(client::stop);
                            return;
                        }
                        Thread.sleep(500);
                    } catch (Exception e) {
                        LOGGER.error("SAPPHIRE_CAPTURE_SMOKE_FAIL {}", e.toString());
                        client.execute(client::stop);
                        return;
                    }
                }
                LOGGER.error("SAPPHIRE_CAPTURE_SMOKE_FAIL timeout; status={}", ChatCapture.status());
                client.execute(client::stop);
            }, "sapphire-capture-smoke");
            worker.setDaemon(true);
            worker.start();
        } catch (Exception e) {
            LOGGER.error("SAPPHIRE_CAPTURE_SMOKE_FAIL {}", e.toString());
            client.execute(client::stop);
        }
    }

    private static java.util.Properties smokeSettings() {
        var properties = new java.util.Properties();
        String path = System.getProperty("sapphire.captureSmokeConfig");
        if (path == null) path = System.getenv("SAPPHIRE_CAPTURE_SMOKE_CONFIG");
        if (path != null && !path.isBlank()) {
            try (var in = Files.newInputStream(Path.of(path))) {
                properties.load(in);
            } catch (Exception e) {
                LOGGER.error("SAPPHIRE_CAPTURE_SMOKE_CONFIG_FAIL {}", e.toString());
            }
        }
        if (properties.isEmpty()) {
            for (String key : new String[]{"dir", "url", "token"}) {
                String env = System.getenv("SAPPHIRE_CAPTURE_" + key.toUpperCase(java.util.Locale.ROOT));
                if (env != null) properties.setProperty(key, env);
            }
        }
        return properties;
    }

    private CaptureSmokeTest() {}
}
