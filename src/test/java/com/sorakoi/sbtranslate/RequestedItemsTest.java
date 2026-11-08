package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RequestedItemsTest {
    TranslationManager manager;
    JsonObject fixtures;
    @BeforeEach void load() throws Exception {
        manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));
        fixtures=TranslationManager.readObject(Path.of("src/test/resources/fixtures/requested-item-lore.json"));
    }
    static String values(String text,int level) {
        return text.replace("{LVL}",""+level).replace("{CRIT_DAMAGE}","75").replace("{0}","30")
            .replace("{1}","50").replace("{2}","25").replace("{3}","40");
    }
    @Test void allRequestedReferenceLoreTranslatesWithoutMissingLines() {
        for (var fixture : fixtures.entrySet()) {
            var data=fixture.getValue().getAsJsonObject(); List<Component> source=new ArrayList<>();
            for (var line : data.getAsJsonArray("lore")) source.add(Component.literal(values(line.getAsString(),100)));
            var before=source.stream().map(Component::copy).toList();
            var result=LoreTranslationEngine.translate(source,manager,fixture.getKey(),(text,lines)->List.of(text),false);
            assertTrue(result.changed(),fixture.getKey());assertEquals(List.of(),result.missing(),fixture.getKey());assertEquals(before,source);
            assertEquals(source.stream().filter(c->c.getString().isBlank()).count(),result.lines().stream().filter(c->c.getString().isBlank()).count(),"blank separators");
        }
    }
    @Test void petNumericStatKeepsSourceSpacingValueAndColor() {
        var source=List.<Component>of(Component.literal("§7Crit Damage: §9+75%"));
        var result=LoreTranslationEngine.translate(source,manager,"ENDERMAN;4",(text,lines)->List.of(text),false);
        assertEquals(List.of(),result.missing());
        var stat=result.lines().getFirst();
        assertEquals("暴击伤害: +75%",stat.getString());
        StyledLoreTest.color(stat,"暴击伤害",ChatFormatting.GRAY);
        StyledLoreTest.color(stat,"+75%",ChatFormatting.BLUE);
    }
    @Test void petNamesKeepLevelRarityAndTrailingDecorationForAllSixVariants() {
        ChatFormatting[] colors={ChatFormatting.WHITE,ChatFormatting.GREEN,ChatFormatting.BLUE,ChatFormatting.DARK_PURPLE,ChatFormatting.GOLD,ChatFormatting.LIGHT_PURPLE};
        for(int tier=0;tier<6;tier++) for(int level:List.of(1,50,100)) {
            String id="ENDERMAN;"+tier; var entry=manager.item(id);
            Component source=Component.literal(values(fixtures.getAsJsonObject(id).get("name").getAsString(),level)+" §b✦");
            Component translated=TextTranslationEngine.itemName(source,entry.originalName(),entry.name(),manager::reforge,entry.styleMappings());
            assertEquals("[等级 "+level+"] 末影人 ✦",translated.getString());
            StyledLoreTest.color(translated,"末影人",colors[tier]);StyledLoreTest.color(translated,"等级",ChatFormatting.GRAY);
            StyledLoreTest.color(translated,""+level,ChatFormatting.GRAY);StyledLoreTest.color(translated,"✦",ChatFormatting.AQUA);
        }
    }
    @Test void dragonScaleKeepsReforgeArmorRequirementAndItalicFlavorStyles() {
        var fixture=fixtures.getAsJsonObject("DRAGON_SCALE"); List<Component> source=new ArrayList<>();
        for(var line:fixture.getAsJsonArray("lore")) source.add(Component.literal(line.getAsString()));
        var result=LoreTranslationEngine.translate(source,manager,"DRAGON_SCALE",(text,lines)->List.of(text),false);
        Component reforge=result.lines().stream().filter(c->c.getString().contains("赋予")).findFirst().orElseThrow();
        StyledLoreTest.color(reforge,"尖刺",ChatFormatting.BLUE);StyledLoreTest.color(reforge,"盔甲",ChatFormatting.RED);
        Component flavor=result.lines().stream().filter(c->c.getString().contains("钻石")).findFirst().orElseThrow();
        assertTrue(StyledLoreTest.styleAt(flavor,"钻石").isItalic());StyledLoreTest.color(flavor,"钻石",ChatFormatting.DARK_GRAY);
        Component required=result.lines().stream().filter(c->c.getString().contains("需要")).findFirst().orElseThrow();
        StyledLoreTest.color(required,"❣",ChatFormatting.DARK_RED);StyledLoreTest.color(required,"需要",ChatFormatting.RED);
        StyledLoreTest.color(required,"采矿技能",ChatFormatting.GREEN);StyledLoreTest.color(required,"20",ChatFormatting.GREEN);
    }
    @Test void petWisdomAndQuickClawRepeatedBonusesKeepTheRightColors() {
        var source=List.<Component>of(Component.literal("§7Gain §3+40☯ Combat Wisdom §7towards"),Component.literal("§5Endermen§7."));
        Component pet=LoreTranslationEngine.translate(source,manager,"ENDERMAN;5",(text,lines)->List.of(text),false).lines().getFirst();
        assertEquals("对末影人获得 +40☯ 战斗智慧.",pet.getString());StyledLoreTest.color(pet,"末影人",ChatFormatting.DARK_PURPLE);
        StyledLoreTest.color(pet,"+40",ChatFormatting.DARK_AQUA);StyledLoreTest.color(pet,"☯",ChatFormatting.DARK_AQUA);StyledLoreTest.color(pet,"战斗智慧",ChatFormatting.DARK_AQUA);
        source=List.of(Component.literal("§7Every 2 pet levels, you gain §6+1"),Component.literal("§6Mining Speed §7and §6+1 Mining Fortune§7."));
        Component claw=LoreTranslationEngine.translate(source,manager,"PET_ITEM_QUICK_CLAW",(text,lines)->List.of(text),false).lines().getFirst();
        assertEquals("宠物每提升 2 级,你就获得 +1 挖掘速度和 +1 挖掘时运.",claw.getString());
        StyledLoreTest.color(claw,"2",ChatFormatting.GRAY);StyledLoreTest.color(claw,"挖掘速度",ChatFormatting.GOLD);StyledLoreTest.color(claw,"挖掘时运",ChatFormatting.GOLD);
    }
}
