package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.TranslationManager;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FullDictionaryTest {
    @Test void everyNeuIdHasABundledDictionaryAndInstallerManifestEntry() throws Exception {
        Path root=Path.of("src/main/resources/defaults");
        var manager=new TranslationManager();manager.load(root);
        var ids=TranslationManager.readObject(root.resolve("neu-index.json")).getAsJsonObject("items").keySet();
        var manifest=Files.readAllLines(root.resolve("item-dictionaries.txt"));
        assertEquals(ids.size(),manager.itemCount());assertEquals(ids.size(),manifest.size());
        assertEquals(manifest.size(),new HashSet<>(manifest).size());
        Set<String> bundledIds=new HashSet<>();
        for(String filename:manifest) {
            assertTrue(filename.matches("[A-Za-z0-9_;.-]+"));
            var dictionary=TranslationManager.readObject(root.resolve("translations/items/"+filename+".json"));
            bundledIds.add(dictionary.get("id").getAsString());
            assertFalse(dictionary.get("name").getAsString().isBlank(),filename);
        }
        assertEquals(ids,bundledIds);
    }
}
