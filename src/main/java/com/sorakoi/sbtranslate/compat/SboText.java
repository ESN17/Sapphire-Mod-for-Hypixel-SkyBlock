package com.sorakoi.sbtranslate.compat;

import net.minecraft.locale.Language;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** SBO settings use literal strings. Keep the key cache bounded, and resolve the active language on each draw. */
public final class SboText {
    private SboText() {}
    private static final Map<String, String> keys = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,String> entry) { return size() > 1024; }
    };
    static String key(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return "sapphire.sbo.ui." + HexFormat.of().formatHex(hash, 0, 12);
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    public static String display(String text) {
        if (text == null || text.isBlank() || text.length() > 4096) return text;
        String lookup;
        synchronized (keys) { lookup = keys.computeIfAbsent(text, SboText::key); }
        return Language.getInstance().getOrDefault(lookup, text);
    }
}
