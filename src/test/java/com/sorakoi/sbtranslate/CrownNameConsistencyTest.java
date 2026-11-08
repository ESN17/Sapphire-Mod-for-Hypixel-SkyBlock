package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CrownNameConsistencyTest {
    @Test void crownVariantsAndChatCatalogUseDistinctNames() throws Exception {
        var manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));
        assertEquals("贪婪之冠",manager.item("CROWN_OF_GREED").name());
        assertEquals("贪欲之冠",manager.item("CROWN_OF_AVARICE").name());
        assertEquals("贪欲之冠",manager.item("CROWN_OF_AVARICE_CELEBRATION").name());
        assertEquals("贪欲之冠",manager.knownItemName("Crown of Avarice"));
        assertEquals("贪婪之冠",manager.knownItemName("Crown of Greed"));
        for(String name:new String[]{"Crown of Avarice","Crown of Greed"}) {
            var source=Component.literal("§aYou sold §6"+name+" §ax1 for §650,000 §aCoins!");
            var copy=source.copy();var out=SystemTranslationEngine.translate(source,"chat",manager);
            String expected=name.endsWith("Avarice")?"贪欲之冠":"贪婪之冠";
            assertTrue(out.getString().contains(expected),out.getString());
            assertTrue(out.getString().contains("50,000"));
            StyledLoreTest.color(out,expected,ChatFormatting.GOLD);
            assertEquals(copy,source);
        }
    }
    @Test void relatedSkinAndAutomationTerminologyAreConsistent() throws Exception {
        var base=Path.of("src/main/resources/defaults");
        var skin=TranslationManager.readObject(base.resolve("translations/items/CROWN_OF_AVARICE_ROYAL.json"));
        assertTrue(skin.toString().contains("贪欲之冠"));
        assertFalse(skin.toString().contains("贪婪之冠"));
        var glossary=TranslationManager.readObject(Path.of("tools/sapphire-translation-rules.json"));
        assertTrue(glossary.toString().contains("\"Crown of Avarice\":\"贪欲之冠\""));
        assertTrue(glossary.toString().contains("\"Crown of Greed\":\"贪婪之冠\""));
    }
}
