package com.sorakoi.sbtranslate.neu;
import com.google.gson.*;
import com.sorakoi.sbtranslate.translation.TranslationManager;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Generated local metadata, not a runtime network client. Ambiguous aliases are never guessed. */
public final class NeuRepositoryManager {
    public record Entry(String name, List<String> lore) {}
    private volatile Map<String,Entry> items = Map.of();
    private volatile Map<String,String> aliases = Map.of();
    public void load(Path root) throws IOException {
        Path path = root.resolve("neu-index.json"); if (!Files.exists(path)) return;
        JsonObject o = TranslationManager.readObject(path); Map<String,Entry> next = new HashMap<>();
        for (var e : o.getAsJsonObject("items").entrySet()) {
            JsonObject value = e.getValue().getAsJsonObject(); List<String> lore = new ArrayList<>();
            if (value.has("lore")) value.getAsJsonArray("lore").forEach(s -> lore.add(TranslationManager.plain(s.getAsString())));
            next.put(e.getKey(), new Entry(TranslationManager.plain(value.get("name").getAsString()), List.copyOf(lore)));
        }
        Map<String,String> mapping = new HashMap<>();
        if (o.has("aliases")) for (var e : o.getAsJsonObject("aliases").entrySet()) if (next.containsKey(e.getValue().getAsString())) mapping.put(e.getKey(), e.getValue().getAsString());
        items = Map.copyOf(next); aliases = Map.copyOf(mapping);
    }
    public String resolve(String skyblockId) { return skyblockId == null ? null : aliases.getOrDefault(skyblockId, skyblockId); }
    public Entry get(String id) { return id == null ? null : items.get(id); }
    public int size() { return items.size(); }
}
