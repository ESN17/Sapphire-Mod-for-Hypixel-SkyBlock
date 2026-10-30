package com.sorakoi.sbtranslate.collection;
import com.sorakoi.sbtranslate.translation.TranslationManager;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Explicit reviewed allowlist. Never treats a chat prefix or ASCII letters as proof of provenance. */
public final class CollectionPolicy {
    private Map<String,List<TranslationManager.Template>> scopes = Map.of();
    public void load(Path root) throws IOException {
        Path p = root.resolve("collection-rules.json"); Map<String,List<TranslationManager.Template>> next = new HashMap<>();
        if (Files.exists(p)) for (var e : TranslationManager.readObject(p).entrySet()) {
            List<TranslationManager.Template> rules = new ArrayList<>();
            for (var entry : e.getValue().getAsJsonArray()) {
                String s = entry.getAsString();
                if (s.matches("(?s).*\\{(?:name|text|prefix|suffix)\\}.*")) throw new IOException("Collection rules must not capture arbitrary text: " + e.getKey());
                rules.add(new TranslationManager.Template(s,s));
            }
            next.put(e.getKey(), List.copyOf(rules));
        }
        scopes = Map.copyOf(next);
    }
    public boolean allows(String scope, String text) {
        if (text.length() > 1024 || text.contains("http") || text.contains("@") || text.startsWith("/") || text.contains("\n")) return false;
        return scopes.getOrDefault(scope, List.of()).stream().anyMatch(t -> t.apply(text) != null);
    }
}
