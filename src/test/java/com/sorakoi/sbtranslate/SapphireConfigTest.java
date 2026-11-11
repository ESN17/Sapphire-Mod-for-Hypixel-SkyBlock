package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.config.SapphireConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SapphireConfigTest {
    @TempDir Path root;

    @Test void newInstallTranslatesWithoutEnvironmentAndCollectsLocallyByDefault() throws Exception {
        var config = SapphireConfig.load(root);
        assertTrue(config.translationAllowed(false));
        assertTrue(config.scope("item"));
        assertTrue(config.collectUntranslated);
        assertTrue(config.collectChatCapture);
        assertFalse(config.privacyAccepted);
        assertTrue(SapphireConfig.load(root).forceTranslation);
    }

    @Test void masterAndCategorySwitchesStillDisableTranslation() {
        var config = new SapphireConfig();
        config.items = false;
        assertFalse(config.scope("item"));
        assertTrue(config.scope("gui"));
        config.enabled = false;
        assertFalse(config.translationAllowed(false));
        assertFalse(config.translationAllowed(true));
        assertFalse(config.scope("gui"));
    }

    @Test void automaticModeRequiresSkyBlockAndSurvivesReload() throws Exception {
        var config = new SapphireConfig();
        config.forceTranslation = false;
        config.save(root);
        var reloaded = SapphireConfig.load(root);
        assertFalse(reloaded.translationAllowed(false));
        assertTrue(reloaded.translationAllowed(true));
    }

    @Test void existingExplicitPreferencesArePreserved() throws Exception {
        Files.writeString(root.resolve("config.json"), "{\"enabled\":false,\"forceTranslation\":false}");
        var config = SapphireConfig.load(root);
        assertFalse(config.enabled);
        assertFalse(config.forceTranslation);
        assertFalse(config.translationAllowed(true));
    }
}
