package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Live Bazaar screenshots: retain menu context in nested and paginated categories. */
class BazaarMenuRegressionTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {
        manager = new TranslationManager();
        manager.load(Path.of("src/main/resources/defaults"));
    }
    @AfterAll static void release() { manager = null; }

    private static GuiTranslationEngine.Menu menu(String title) {
        var result = GuiTranslationEngine.menu(title, manager);
        assertNotNull(result, title);
        assertEquals("bazaar", result.id(), title);
        return result;
    }
    private static String translate(String title, String line) {
        return GuiTranslationEngine.button(Component.literal(line), menu(title)).getString();
    }

    @Test void truncatedUltimateEnchantPage() {
        String title = "Ultimate Enchantments ➜ Bobbin'";
        menu(title);
        assertTrue(GuiTranslationEngine.title(Component.literal(title), manager).getString().startsWith("终极附魔 ➜ 浮漂时刻"));
        var raw = Component.literal("§d§lBobbin' Time §d§lV");
        var result = GuiTranslationEngine.button(raw, menu(title));
        assertEquals("浮漂时刻 V", result.getString());
        StyledLoreTest.color(result, "V", ChatFormatting.LIGHT_PURPLE);
        assertTrue(StyledLoreTest.styleAt(result, "V").isBold());
        assertEquals("罕见商品", translate(title, "Uncommon commodity"));
        assertEquals("买入价: 261,997,999 硬币", translate(title, "Buy price: 261,997,999 coins"));
    }

    @Test void paginatedMutationPageAndRestriction() {
        String title = "(1/2) Garden ➜ Mutations";
        menu(title);
        assertEquals("(1/2) 花园 ➜ 变异作物", GuiTranslationEngine.title(Component.literal(title), manager).getString());
        assertEquals("你尚未分析这种变异作物!", translate(title, "You haven't analyzed this mutation!"));
        assertEquals("24 个卖单,共 3.6k 个", translate(title, "3.6k in 24 offers"));
    }

    @Test void essencePageAndDynamicCatacombsRequirement() {
        String title = "Combat ➜ Essences";
        menu(title);
        for (String level : List.of("1", "20", "50"))
            assertEquals("需要地下墓穴等级 " + level + "!", translate(title, "You must have Catacombs Skill " + level + "!"));
        assertEquals("274 个卖单,共 18M 个", translate(title, "18M in 274 offers"));
        assertEquals("7 天内即时买入 10.1M 个", translate(title, "10.1M insta-buys in 7d"));
        assertEquals("66 个买单,共 3.7M 个", translate(title, "3.7M in 66 orders"));
    }

    @Test void everyGardenChipPreviewUsesItemDictionary() {
        var context = menu("Farming ➜ Garden");
        var click = new ClickEvent.RunCommand("/bz");
        for (String id : List.of("VERMIN_VAPORIZER", "SYNTHESIS", "SOWLEDGE", "MECHAMIND", "HYPERCHARGE",
                "EVERGREEN", "OVERDRIVE", "CROPSHOT", "QUICKDRAW", "RAREFINDER")) {
            var item = manager.item(id + "_GARDEN_CHIP");
            assertNotNull(item, id);
            var source = Component.literal("§b▸ §7" + item.originalName() + " §c113k §7| §a151.3k")
                    .withStyle(s -> s.withClickEvent(click));
            var copy = source.copy();
            var result = GuiTranslationEngine.button(source, context);
            assertEquals("▸ " + item.name() + " 113k | 151.3k", result.getString(), id);
            assertEquals(copy, source);
            StyledLoreTest.color(result, "113k", ChatFormatting.RED);
            StyledLoreTest.color(result, "151.3k", ChatFormatting.GREEN);
            assertEquals(click, StyledLoreTest.styleAt(result, item.name()).getClickEvent());
        }
    }

    @Test void marketQuantitiesAndDaysRemainVariable() {
        for (String title : List.of("Ultimate Enchantments ➜ Bobbin'", "(1/2) Garden ➜ Mutations", "Combat ➜ Essences"))
            for (String count : List.of("1", "103", "1,024", "3.6k", "18M", "10.1M", "1.2B"))
                for (String days : List.of("1", "7", "30")) {
                    assertEquals(days + " 天内即时买入 " + count + " 个", translate(title, count + " insta-buys in " + days + "d"));
                    assertEquals(days + " 天内即时卖出 " + count + " 个", translate(title, count + " insta-sells in " + days + "d"));
                    assertEquals(days + " 天均价: " + count + " 硬币", translate(title, days + "d Avg. price: " + count + " coins"));
                    assertEquals("1 个卖单,共 " + count + " 个", translate(title, count + " in 1 offer"));
                    assertEquals("1 个买单,共 " + count + " 个", translate(title, count + " in 1 order"));
                }
    }
}
