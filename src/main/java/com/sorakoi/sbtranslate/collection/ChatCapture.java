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
 * One file per capture window: untranslated-&lt;player&gt;-&lt;yyyyMMdd&gt;-&lt;n&gt;.txt,
 * one styled line per entry (§ colour codes kept, numbers preserved as seen).
 * A window closes after ten minutes, on disconnect, or when full. After a
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
    private static final Pattern TIMESTAMP = Pattern.compile("^\\[\\d{1,2}:\\d{2}(?::\\d{2})?\\]\\s*");
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
    private static volatile boolean enabled;
    private static volatile boolean chatEnabled, uiEnabled;
    private static final java.util.ArrayDeque<String> pendingUi = new java.util.ArrayDeque<>();
    private static boolean drainScheduled;
    private static Component lastUiTitle;
    private static long titleRetry;
    private static final Set<Path> queued = new HashSet<>();
    private static long nextRotation;
    private static long retryStartAfter;
    private static String sequenceStem="";
    private static int sequence;
    private static boolean notifyUploads = true;
    private static Path directory;
    private static String uploadUrl = "";
    private static String token = "";
    private static volatile String lastError = "";
    private static volatile String lastResult = "尚未记录";
    private static volatile boolean session;
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
            chatEnabled = config.collectChatCapture && config.privacyAccepted;
            uiEnabled = config.collectUiCapture && config.privacyAccepted;
            enabled = chatEnabled || uiEnabled;
            notifyUploads = config.uploadNotifications;
            Path dest = Path.of(config.chatCaptureDirectory == null || config.chatCaptureDirectory.isBlank()
                    ? "D:/Sapphire-source" : config.chatCaptureDirectory).toAbsolutePath().normalize();
            if(!dest.equals(directory)){
                stop();
                sequenceStem="";
                sequence=0;
                retryStartAfter=0;
            }
            directory=dest;
            uploadUrl = config.chatCaptureUploadUrl == null ? "" : config.chatCaptureUploadUrl.strip();
            token = config.chatCaptureToken == null ? "" : config.chatCaptureToken.strip();
            if(token.isEmpty() && uploadUrl.equals("https://sapphire.esn17cabin.club/untranslated/upload")){
                try(var in=ChatCapture.class.getResourceAsStream("/upload-token.txt")){
                    if(in!=null) token=new String(in.readAllBytes(),StandardCharsets.UTF_8).strip();
                }catch(IOException e){lastError="无法读取上传配置";}
            }
            if (wasEnabled && !enabled) stop();
        }
    }

    public static boolean enabled() { return chatEnabled; }
    public static boolean uiActive() { return uiEnabled && session; }

    public static void startSession(String playerName) {
        synchronized (LOCK) {
            if (!enabled || session || stopping || System.nanoTime() < retryStartAfter) return;
            retryStartAfter = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            String safe = playerName == null ? "" : playerName.replaceAll("[^A-Za-z0-9_]", "");
            if (safe.isEmpty()) safe = "player";
            player = safe.substring(0, Math.min(safe.length(), 16));
            Path created = nextFile();
            if (created == null) return;
            try {
                Files.createDirectories(created.getParent());
                writer = Files.newBufferedWriter(created, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
            } catch (IOException e) {
                lastError = "无法创建记录文件：" + e.getMessage();
                return;
            }
            file = created;
            session = true;
            retryStartAfter = 0;
            nextRotation = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
            lineCount = 0;
            bytes = 0;
            seen.clear();
            lastUiTitle = null;
            lastResult = "记录中";
            com.sorakoi.sbtranslate.Sapphire.LOGGER.info("Sapphire chat capture started: {}", created);
            notifyChat("已开始记录所选类型的漏译文本，每十分钟或退出时自动上传。");
        }
    }

    public static void tick() {
        synchronized (LOCK) {
            if (!session || System.nanoTime() < nextRotation) return;
            nextRotation = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
            retryPending();
            rotate();
        }
    }

    public static void observe(Component message) { observe(message, false); }

    public static void observeDisplayed(Component raw,Component display,boolean publicLocation) {
        if(!chatEnabled || !session) return;
        if(raw==display){observe(raw,publicLocation);return;}
        // Prefix decoration can change every line even when its English body survives.
        var source=styledLines(raw);
        var visible=styledLines(display);
        if(!publicLocation && source.stream().anyMatch(line->TranslationManager.plain(line).contains("[NPC]")))return;
        if(source.size()!=visible.size())return;
        for(int i=0;i<source.size();i++){
            String line=TranslationManager.plain(visible.get(i));
            if(!CJK.matcher(line).find() && ENGLISH.matcher(line).find())
                observe(Component.literal(source.get(i)),publicLocation);
        }
    }

    public static void observe(Component message, boolean publicLocation) {
        if (!chatEnabled || !session) return;
        List<String> lines = styledLines(message);
        if (lines.isEmpty()) return;
        if (!publicLocation && lines.stream().anyMatch(line -> TranslationManager.plain(line).contains("[NPC]"))) return;
        synchronized (LOCK) {
            if (!session) return;
            for (String line : lines) {
                if (!keep(line) || line.indexOf(0) >= 0 || line.getBytes(StandardCharsets.UTF_8).length > 2048) continue;
                String plain = TranslationManager.plain(line).strip();
                String key = plain.replaceAll("[0-9]+", "#").toLowerCase(Locale.ROOT);
                if (!seen.add(key)) continue;
                append(line,true);
            }
        }
    }

    public static void stop() {
        synchronized (LOCK) {
            if (!session) return;
            drainUi();
            session = false;
            rotate();
        }
    }

    public static void observeTitle(Component original) {
        if (!uiActive() || (original == lastUiTitle && System.nanoTime() < titleRetry)) return;
        lastUiTitle = original;
        titleRetry = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        String title = TranslationManager.plain(original.getString());
        if (UiCapture.publicMenu(title)) {
            observeUi("gui", original);
            titleRetry = Long.MAX_VALUE;
        }
    }

    public static void observeUi(String scope, Component original) {
        if (!uiActive()) return;
        String plain = TranslationManager.plain(original.getString()).strip();
        if (!UiCapture.eligible(plain)) return;
        synchronized (LOCK) {
            if (!uiActive() || !seen.add("ui/" + scope + "/" + UiCapture.dedupKey(plain))) return;
            String prefix = "[Sapphire:UI:" + scope + "] ";
            for (String styled : styledLines(original)) {
                String record = prefix + styled;
                if (record.getBytes(StandardCharsets.UTF_8).length <= 2048) pendingUi.add(record);
            }
            if (!drainScheduled && !pendingUi.isEmpty()) {
                drainScheduled = true;
                WORKER.execute(ChatCapture::drainUi);
            }
        }
    }

    private static void drainUi() {
        synchronized (LOCK) {
            boolean wrote=!pendingUi.isEmpty();
            while (!pendingUi.isEmpty()) append(pendingUi.removeFirst(),false);
            if(wrote && writer!=null)try { writer.flush(); }
            catch(IOException e){lastError="写入失败："+e.getMessage();closeWriter();}
            drainScheduled = false;
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
            if (!enabled || directory == null || stopping) return;
            Path folder = directory;
            WORKER.execute(() -> scanPending(folder));
        }
    }

    private static void scanPending(Path folder) {
        if (!Files.isDirectory(folder)) return;
        try (var files = Files.list(folder)) {
            for (Path path : files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("untranslated-") && UPLOAD_NAME.matcher(p.getFileName().toString()).matches())
                    .sorted().toList()) {
                synchronized(LOCK){
                    if(enabled && !stopping && folder.equals(directory) && !path.equals(file) && Files.isRegularFile(path) && Files.size(path)>0)enqueue(path);
                }
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

    private static void append(String line,boolean flush) {
        try {
            if (writer == null) {
                Path created = nextFile();
                if (created == null) return;
                Files.createDirectories(created.getParent());
                writer = Files.newBufferedWriter(created, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
                file = created;
            }
            if (lineCount >= 5000 || bytes >= 1024 * 1024) {
                lastError = "记录已达上限";
                return;
            }
            writer.write(line);
            writer.write('\n');
            if(flush)writer.flush();
            lineCount++;
            bytes += line.getBytes(StandardCharsets.UTF_8).length + 1;
            if (lineCount >= 5000 || bytes >= 1024 * 1024 - 2049) rotate();
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
        int highest = stem.equals(sequenceStem) ? sequence : 0;
        try (var files = Files.list(directory)) {
            for (Path existing : files.toList()) {
                var matcher = pattern.matcher(existing.getFileName().toString());
                if (matcher.matches()) highest = Math.max(highest, Integer.parseInt(matcher.group(1)));
            }
        } catch (IOException ignored) {
        }
        for (int index = highest + 1; index <= highest + 999; index++) {
            Path candidate = directory.resolve(stem + index + ".txt");
            if (!Files.exists(candidate)) {
                sequenceStem=stem;
                sequence=index;
                return candidate;
            }
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
            String endpoint, uploadToken;
            synchronized (LOCK) {
                if (!queued.add(path)) return;
                endpoint = uploadUrl;
                uploadToken = token;
            }
            WORKER.execute(() -> {
                try { upload(path, endpoint, uploadToken); }
                finally { synchronized (LOCK) { queued.remove(path); } }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            synchronized (LOCK) { queued.remove(path); }
            lastError = "上传队列已关闭";
        }
    }

    private static void upload(Path path, String endpoint, String uploadToken) {
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
        UploadResult result = uploadText(name, body, endpoint, uploadToken);
        // Never move or overwrite another queued (or currently open) local file.
        // A high alternate number also escapes long runs of old server-side names.
        for (int attempt = 0; attempt < 5 && result.code() == 409; attempt++) {
            int step = java.util.concurrent.ThreadLocalRandom.current().nextInt(1000, 100000);
            String next = bumpNumber(name, step);
            if (next.equals(name)) break;
            name = next;
            result = uploadText(name, body, endpoint, uploadToken);
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
        return matcher.group(1) + (1 + (number - 1) % 9999999) + matcher.group(3);
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
        String endpoint, uploadToken;
        synchronized (LOCK) {
            endpoint = uploadUrl;
            uploadToken = token;
        }
        return uploadText(fileName, body, endpoint, uploadToken);
    }

    private static UploadResult uploadText(String fileName, byte[] body, String endpoint, String uploadToken) {
        if (endpoint.isEmpty() || uploadToken.isEmpty()) return new UploadResult(false, 0, "未配置上传地址或令牌");
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            return new UploadResult(false, 0, "上传地址无效");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) return new UploadResult(false, 0, "上传地址必须使用 HTTPS");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("User-Agent", "Sapphire-Capture/1");
            connection.setRequestProperty("X-Sapphire-Token", uploadToken);
            connection.setRequestProperty("X-Sapphire-File", fileName);
            connection.setFixedLengthStreamingMode(body.length);
            try (var out = connection.getOutputStream()) {
                out.write(body);
            }
            int code = connection.getResponseCode();
            if (code == 201 || code == 200) {
                try (var in = connection.getInputStream()) {
                    var ack = com.google.gson.JsonParser.parseString(new String(in.readNBytes(4096), StandardCharsets.UTF_8)).getAsJsonObject();
                    if (ack.get("ok").getAsBoolean() && fileName.equals(ack.get("name").getAsString()) && ack.get("size").getAsLong() == body.length)
                        return new UploadResult(true, code, "已上传 " + fileName);
                } catch (RuntimeException malformed) { /* Login pages are not upload acknowledgements. */ }
                return new UploadResult(false, code, "服务器未确认接收完整文件");
            }
            String detail = "";
            try (var error = connection.getErrorStream()) {
                if (error != null) {
                    var response = com.google.gson.JsonParser.parseString(new String(error.readNBytes(2048), StandardCharsets.UTF_8)).getAsJsonObject();
                    if (response.has("error")) detail = response.get("error").getAsString();
                }
            } catch (RuntimeException ignored) {
                // Proxies sometimes send an HTML error page.
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
        // Chat-timestamp mods prepend [HH:MM:SS]; strip it before the chat checks.
        plain = TIMESTAMP.matcher(plain).replaceFirst("");
        if (plain.isEmpty()) return false;
        if (CJK.matcher(plain).find()) return false;
        if (plain.startsWith("[Sapphire]")) return false;
        if (plain.contains("http") || plain.contains("www.")) return false;
        if (plain.contains("joined the lobby")) return false;
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
