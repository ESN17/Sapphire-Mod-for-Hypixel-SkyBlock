package com.sorakoi.sbtranslate.collection;

import com.sorakoi.sbtranslate.config.SapphireConfig;
import com.sorakoi.sbtranslate.translation.TranslationManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.StringDecomposer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Opt-in capture of chat lines that Sapphire could not translate.
 *
 * One file per SkyBlock session: untranslated-&lt;player&gt;-&lt;yyyyMMdd&gt;-&lt;n&gt;.txt,
 * one styled line per entry (§ colour codes kept, numbers preserved as seen).
 * The file is uploaded when the session ends or the game exits; after a
 * successful upload the local copy is removed.
 */
public final class ChatCapture {
    private static final Pattern CJK = Pattern.compile("[\\u3400-\\u9fff]");
    private static final Pattern ENGLISH = Pattern.compile("[A-Za-z]{2,}");
    private static final Pattern PLAYER = Pattern.compile(
            "^(?:"
                    + "(?:Party|Guild|Co-op|Co-Op|Officer|Ally|Island|SkyBlock|From|To|PM|MSG)\\b.*"
                    // Hypixel player chat: [level] [icon] [rank] Name: message
                    + "|.*>\\s*(?:\\[[^\\]]{0,32}\\]\\s*|[^\\p{L}\\p{N}\\s]{1,3}\\s*)*[A-Za-z0-9_]{1,16}\\s*:\\s.*"
                    // Any "Name: message" line, with optional level/icon/rank prefixes.
                    + "|(?:\\[[^\\]]{0,32}\\]\\s*|[^\\p{L}\\p{N}\\s]{1,3}\\s*)*[A-Za-z0-9_]{1,16}\\s*:\\s.*"
                    + ")$");
    private static final Pattern LEVEL_PREFIX = Pattern.compile("^(?:[^\\p{L}\\p{N}\\s]{0,3}\\s*)?\\[\\s*[0-9]{1,4}\\s*\\].*");
    private static final Pattern UPLOAD_NAME = Pattern.compile(
            "^(?:untranslated-anon-[0-9]{1,9}"
                    + "|untranslated-[A-Za-z0-9_]{1,16}-[0-9]{8}-[0-9]{1,7}"
                    + "|feedback-anon-[0-9]{1,9}"
                    + "|feedback-[A-Za-z0-9_]{1,16}-[0-9]{8}-[0-9]{1,7})\\.txt$");
    private static final int COLOR_COUNT = 16;
    private static final int[] COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
    private static final char[] CODES = "0123456789abcdef".toCharArray();

    private static final Object LOCK = new Object();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "sapphire-capture");
        thread.setDaemon(true);
        return thread;
    });
    private static final Set<String> seen = new HashSet<>();
    private static boolean enabled;
    private static boolean notifyUploads = true;
    private static Path directory;
    private static String uploadUrl = "";
    private static String token = "";
    private static String lastError = "";
    private static String lastResult = "尚未记录";
    private static boolean session;
    private static String player = "";
    private static boolean stopping;
    private static Path file;
    private static BufferedWriter writer;
    private static int lineCount;
    private static long bytes;

    private ChatCapture() {}

    /** Applies the current configuration; safe to call from the settings screen. */
    public static void configure(SapphireConfig config) {
        synchronized (LOCK) {
            boolean wasEnabled = enabled;
            enabled = config.collectChatCapture;
            notifyUploads = config.uploadNotifications;
            directory = Path.of(config.chatCaptureDirectory == null || config.chatCaptureDirectory.isBlank()
                    ? "D:/Sapphire-source" : config.chatCaptureDirectory);
            uploadUrl = config.chatCaptureUploadUrl == null ? "" : config.chatCaptureUploadUrl.strip();
            token = config.chatCaptureToken == null ? "" : config.chatCaptureToken.strip();
            if (wasEnabled && !enabled) stop();
        }
    }

    public static boolean enabled() { return enabled; }

    public static void startSession(String playerName) {
        synchronized (LOCK) {
            if (!enabled || session || stopping) return;
            String safe = playerName == null ? "" : playerName.replaceAll("[^A-Za-z0-9_]", "");
            if (safe.isEmpty()) safe = "player";
            player = safe.substring(0, Math.min(safe.length(), 16));
            Path created = nextFile();
            if (created == null) return;
            try {
                Files.createDirectories(created.getParent());
                writer = Files.newBufferedWriter(created, StandardCharsets.UTF_8);
            } catch (IOException e) {
                lastError = "无法创建记录文件：" + e.getMessage();
                return;
            }
            file = created;
            session = true;
            lineCount = 0;
            bytes = 0;
            seen.clear();
            lastResult = "记录中";
            com.sorakoi.sbtranslate.Sapphire.LOGGER.info("Sapphire chat capture started: {}", created);
            notifyChat("已开始记录未翻译聊天，退出游戏后自动上传。");
        }
    }

    /** Lines already seen this session are dropped; only untranslated English lines are kept. */
    public static void observe(Component message) {
        if (!enabled || !session) return;
        List<String> lines = styledLines(message);
        if (lines.isEmpty()) return;
        synchronized (LOCK) {
            if (!session) return;
            for (String line : lines) {
                if (!keep(line)) continue;
                String plain = TranslationManager.plain(line).strip();
                String key = plain.replaceAll("[0-9]+", "#").toLowerCase(Locale.ROOT);
                if (!seen.add(key)) continue;
                append(line);
            }
        }
    }

    public static void stop() {
        synchronized (LOCK) {
            if (!session) return;
            session = false;
            rotate();
        }
    }

    /**
     * Uploads what has been captured so far and starts a fresh file for the
     * rest of the session. Called periodically so a hard window close or crash
     * still leaves at most a few minutes of local data.
     */
    public static void rotate() {
        synchronized (LOCK) {
            closeWriter();
            Path finished = file;
            int written = lineCount;
            file = null;
            lineCount = 0;
            bytes = 0;
            if (finished == null) return;
            if (written == 0) {
                // Nothing was captured in this window; do not leave an empty file behind.
                try {
                    Files.deleteIfExists(finished);
                } catch (IOException ignored) {
                }
                return;
            }
            enqueue(finished);
        }
    }

    public static int lineCount() {
        synchronized (LOCK) {
            return lineCount;
        }
    }

    /** Called on disconnect, normal quit, window close and JVM shutdown. */
    public static void shutdown() {
        synchronized (LOCK) {
            stopping = true;
            stop();
        }
        WORKER.shutdown();
        try {
            WORKER.awaitTermination(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Uploads files left by an earlier crash or by a failed upload. */
    public static void retryPending() {
        synchronized (LOCK) {
            if (!enabled || directory == null) return;
        }
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(Files::isRegularFile)
                    .filter(p -> UPLOAD_NAME.matcher(p.getFileName().toString()).matches())
                    .sorted().toList()) {
                enqueue(path);
            }
        } catch (IOException e) {
            lastError = "无法读取记录目录";
        }
    }

    public static String status() {
        synchronized (LOCK) {
            return "漏译采集=" + enabled + "，会话=" + session + "，行数=" + lineCount
                    + "，文件=" + (file == null ? "未创建" : file.getFileName().toString())
                    + "，结果=" + lastResult + (lastError.isEmpty() ? "" : "，错误=" + lastError);
        }
    }

    private static void append(String line) {
        try {
            if (writer == null) {
                Path created = nextFile();
                if (created == null) return;
                Files.createDirectories(created.getParent());
                writer = Files.newBufferedWriter(created, StandardCharsets.UTF_8);
                file = created;
            }
            if (lineCount >= 5000 || bytes >= 1024 * 1024) {
                lastError = "记录已达上限";
                return;
            }
            writer.write(line);
            writer.write('\n');
            writer.flush();
            lineCount++;
            bytes += line.getBytes(StandardCharsets.UTF_8).length + 1;
            if (lineCount >= 200 || bytes >= 512 * 1024) rotate();
        } catch (IOException e) {
            lastError = "写入失败：" + e.getMessage();
            closeWriter();
        }
    }

    private static Path nextFile() {
        if (directory == null) return null;
        // Naming agreed with the maintainer: untranslated-<player>-<yyyyMMdd>-<n>.txt
        String day = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String stem = "untranslated-" + player + "-" + day + "-";
        var pattern = java.util.regex.Pattern.compile("^" + java.util.regex.Pattern.quote(stem) + "([0-9]{1,9})\\.txt$");
        int highest = 0;
        try (var files = Files.list(directory)) {
            for (Path existing : files.toList()) {
                var matcher = pattern.matcher(existing.getFileName().toString());
                if (matcher.matches()) highest = Math.max(highest, Integer.parseInt(matcher.group(1)));
            }
        } catch (IOException ignored) {
        }
        for (int index = highest + 1; index <= highest + 999; index++) {
            Path candidate = directory.resolve(stem + index + ".txt");
            if (!Files.exists(candidate)) return candidate;
        }
        lastError = "当天文件数量过多";
        return null;
    }

    private static void closeWriter() {
        if (writer == null) return;
        try {
            writer.close();
        } catch (IOException ignored) {
        }
        writer = null;
    }

    private static void enqueue(Path path) {
        try {
            WORKER.execute(() -> upload(path));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            lastError = "上传队列已关闭";
        }
    }

    private static void upload(Path path) {
        byte[] body;
        try {
            body = Files.readAllBytes(path);
        } catch (IOException e) {
            lastResult = "读取失败：" + e.getMessage();
            lastError = lastResult;
            return;
        }
        String name = path.getFileName().toString();
        notifyUploading();
        UploadResult result = uploadText(name, body);
        // Another client may already have used this number on the server; take the next one.
        for (int attempt = 1; attempt <= 5 && !result.ok() && result.code() == 409; attempt++) {
            String next = bumpNumber(name, attempt);
            if (next.equals(name)) break;
            try {
                Path renamed = path.resolveSibling(next);
                Files.move(path, renamed, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                path = renamed;
            } catch (IOException ignored) {
            }
            name = next;
            result = uploadText(name, body);
        }
        if (result.ok()) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
            lastResult = "已上传并删除 " + name;
            lastError = "";
        } else {
            lastResult = result.message();
            lastError = result.message();
            notifyChat("上传失败：" + result.message() + "，记录保留在 " + path);
        }
    }

    private static String bumpNumber(String fileName, int step) {
        var matcher = java.util.regex.Pattern.compile("^(.*-)([0-9]{1,7})(\\.txt)$").matcher(fileName);
        if (!matcher.matches()) return fileName;
        long number = Long.parseLong(matcher.group(2)) + step;
        return matcher.group(1) + number + matcher.group(3);
    }

    /** Chat notice, switchable in the advanced settings. */
    private static void notifyUploading() {
        notifyChat("已收集的未翻译文本正在上传中……");
    }

    private static void notifyChat(String message) {
        if (!notifyUploads) return;
        try {
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                var minecraft = net.minecraft.client.Minecraft.getInstance();
                if (minecraft.gui != null) minecraft.gui.hud.getChat().addClientSystemMessage(
                        com.sorakoi.sbtranslate.client.SapphireNotifications.message(
                                com.sorakoi.sbtranslate.client.SapphireNotifications.Tone.INFO, message));
            });
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * Uploads one text file with the configured endpoint and token.
     * Shared by the chat capture and the in-game feedback screen.
     */
    public static UploadResult uploadText(String fileName, byte[] body) {
        if (uploadUrl.isEmpty() || token.isEmpty()) return new UploadResult(false, 0, "未配置上传地址或令牌");
        URI uri;
        try {
            uri = URI.create(uploadUrl);
        } catch (IllegalArgumentException e) {
            return new UploadResult(false, 0, "上传地址无效");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) return new UploadResult(false, 0, "上传地址必须使用 HTTPS");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("User-Agent", "Sapphire-Capture/1");
            connection.setRequestProperty("X-Sapphire-Token", token);
            connection.setRequestProperty("X-Sapphire-File", fileName);
            connection.setFixedLengthStreamingMode(body.length);
            try (var out = connection.getOutputStream()) {
                out.write(body);
            }
            int code = connection.getResponseCode();
            if (code == 201 || code == 200) return new UploadResult(true, code, "已上传 " + fileName);
            String detail = "";
            try (var error = connection.getErrorStream()) {
                if (error != null) detail = new String(error.readNBytes(200), StandardCharsets.UTF_8);
            }
            return new UploadResult(false, code, "上传失败 HTTP " + code + (detail.isBlank() ? "" : "：" + detail));
        } catch (IOException e) {
            return new UploadResult(false, 0, "上传失败：" + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public record UploadResult(boolean ok, int code, String message) {}

    private static boolean keep(String styled) {
        String plain = TranslationManager.plain(styled).strip();
        if (plain.isEmpty() || plain.length() > 1024) return false;
        if (CJK.matcher(plain).find()) return false;
        if (plain.startsWith("[Sapphire]")) return false;
        if (plain.contains("http") || plain.contains("www.")) return false;
        // Hypixel prefixes player and lobby lines with a level like [135]; never capture those.
        if (LEVEL_PREFIX.matcher(plain).matches()) return false;
        boolean dialogue = plain.startsWith("[NPC]") || plain.startsWith("[BOSS]");
        if (!dialogue && PLAYER.matcher(plain).matches()) return false;
        return ENGLISH.matcher(plain).find();
    }

    /** Splits a message into styled lines, re-emitting colours after every line break. */
    static List<String> styledLines(Component message) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Style[] previous = {null};
        StringDecomposer.iterateFormatted(message, Style.EMPTY, (index, style, point) -> {
            if (point == '\n') {
                lines.add(finish(current));
                current.setLength(0);
                previous[0] = null;
                return true;
            }
            if (!style.equals(previous[0])) {
                codes(current, style, previous[0] != null);
                previous[0] = style;
            }
            current.appendCodePoint(point);
            return true;
        });
        lines.add(finish(current));
        return lines;
    }

    private static String finish(StringBuilder builder) {
        String text = builder.toString();
        return text.isBlank() ? "" : text.stripTrailing();
    }

    private static void codes(StringBuilder out, Style style, boolean hadPrevious) {
        TextColor color = style.getColor();
        if (color != null) {
            out.append('§').append(nearest(color.getValue()));
        } else if (hadPrevious) {
            out.append("§r");
        }
        if (style.isObfuscated()) out.append("§k");
        if (style.isBold()) out.append("§l");
        if (style.isStrikethrough()) out.append("§m");
        if (style.isUnderlined()) out.append("§n");
        if (style.isItalic()) out.append("§o");
    }

    private static char nearest(int rgb) {
        int best = 0;
        long distance = Long.MAX_VALUE;
        for (int i = 0; i < COLOR_COUNT; i++) {
            int dr = ((rgb >> 16) & 0xFF) - ((COLORS[i] >> 16) & 0xFF);
            int dg = ((rgb >> 8) & 0xFF) - ((COLORS[i] >> 8) & 0xFF);
            int db = (rgb & 0xFF) - (COLORS[i] & 0xFF);
            long candidate = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (candidate < distance) {
                distance = candidate;
                best = i;
            }
        }
        return CODES[best];
    }
}
