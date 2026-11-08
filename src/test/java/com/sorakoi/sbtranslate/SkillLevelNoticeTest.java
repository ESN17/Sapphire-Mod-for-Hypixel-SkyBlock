package com.sorakoi.sbtranslate;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SkillLevelNoticeTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;}
    static Component translate(String raw) {
        var source=Component.literal(raw);var copy=source.copy();
        var out=SystemTranslationEngine.translate(source,"chat",manager);
        assertEquals(copy,source);assertNotSame(source,out,raw);return out;
    }
    @Test void screenshotHeadersRetainRomanNumeralsAndDistinctStyles() {
        for(String skill:List.of("Taming","Combat","Farming","Mining","Foraging","Fishing","Enchanting","Alchemy","Carpentry","Hunting","Runecrafting","Social","Dungeoneering"))
            for(String[] levels:List.of(new String[]{"XXXIII","XXXIV"},new String[]{"XLIV","XLV"},new String[]{"0","I"},new String[]{"59","60"}))
                for(String arrow:List.of("➜","→","➔","➡","->")) {
                    var out=translate("  §b§lSKILL LEVEL UP §3"+skill+" §8"+levels[0]+arrow+"§3"+levels[1]);
                    assertFalse(out.getString().contains(skill));assertTrue(out.getString().startsWith("  技能升级"));
                    assertTrue(out.getString().endsWith(levels[0]+arrow+levels[1]));
                    StyledLoreTest.color(out,levels[0],ChatFormatting.DARK_GRAY);
                    StyledLoreTest.color(out,levels[1],ChatFormatting.DARK_AQUA);
                }
    }
    @Test void screenshotRewardsKeepValuesSymbolsAndColors() {
        for(String[] pair:List.of(new String[]{"33","34"},new String[]{"6.6","6.8"},new String[]{"49.5","50"})) {
            String before=pair[0],after=pair[1];
            var pet=translate("  §fGain §8"+before+"➜§a"+after+"% §fextra pet exp.");
            assertEquals("  获得 "+before+"➜"+after+"% 额外宠物经验。",pet.getString());
            StyledLoreTest.color(pet,before,ChatFormatting.DARK_GRAY);StyledLoreTest.color(pet,after,ChatFormatting.GREEN);
            var share=translate("§8+§fGain §8"+before+"➜§a"+after+"% §fExp Share rate.");
            assertFalse(share.getString().contains("Exp Share"));assertTrue(share.getString().contains(before+"➜"+after+"%"));
            StyledLoreTest.color(share,after,ChatFormatting.GREEN);
        }
        for(String raw:List.of("§8+§d1♣ Pet Luck","§8+§90.5%☣ Crit Chance","§8+§61,000,000 §7Coins","§8+§b20 SkyBlock XP")) {
            var out=translate(raw);assertFalse(out.getString().matches(".*[A-Za-z].*"),out.getString());
        }
        assertEquals("动物学家 XXXIV",translate("§eZoologist XXXIV").getString());
        assertEquals("战士 XLV",translate("§eWarrior XLV").getString());
    }
    @Test void multilinePacketAndOtherSkillPerksWorkWithoutChangingPlayerChat() {
        var raw=Component.empty().append(Component.literal("§bSKILL LEVEL UP Combat §8XLIV➜§3XLV\n"))
            .append(Component.literal("§eWarrior XLV\n§fDeal §8176➜§a180% §fmore damage to mobs.\n§8+§90.5%☣ Crit Chance"));
        var copy=raw.copy();var out=SystemTranslationEngine.translate(raw,"chat",manager);
        assertTrue(out.getString().contains("技能升级 战斗 XLIV➜XLV"));assertTrue(out.getString().contains("暴击率"));assertEquals(copy,raw);
        for(String perk:List.of("Farmhand","Logger","Spelunker","Treasure Hunter","Conjurer","Brewer"))
            assertFalse(translate(perk+" XII").getString().contains(perk));
        var unrelated=Component.literal("Player_123: SKILL LEVEL UP Combat XLIV➜XLV");
        assertSame(unrelated,SystemTranslationEngine.translate(unrelated,"chat",manager));
    }
}
