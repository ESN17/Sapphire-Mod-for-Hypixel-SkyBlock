package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StatsMenuFeedbackTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;}
    @Test void actualStatsEquipmentMenuUsesSharedRulesForHeadingAndRows() {
        var menu=GuiTranslationEngine.menu("Stats & Equipment",manager);
        assertNotNull(menu);assertEquals("stats",menu.id());assertFalse(menu.paragraphs().isEmpty());
        assertEquals("属性与装备",GuiTranslationEngine.title(Component.literal("Stats & Equipment"),manager).getString());
        var heading=Component.literal("§bFishing Stats");
        var translatedHeading=GuiTranslationEngine.button(heading,menu);
        assertEquals("钓鱼属性",translatedHeading.getString());StyledLoreTest.color(translatedHeading,"钓鱼属性",ChatFormatting.AQUA);
        var source=List.<Component>of(Component.literal("§7Stats that influence what you catch"),
            Component.literal("§7and how quickly you catch it while"),Component.literal("§7fishing."),Component.literal(""),
            Component.literal("§3 Sea Creature Chance §f20%"),Component.literal("§6 Treasure Chance §f0.5%"),
            Component.literal(""),Component.literal("§eClick to view details!"));
        var before=source.stream().map(Component::copy).toList();
        var result=LoreTranslationEngine.translate(source,manager,null,(s,o)->List.of(s),false,false,false,
            GuiTranslationEngine.tooltipRules(menu,heading,manager),s->GuiTranslationEngine.detailLine(s,menu,manager),
            s->CommunityTranslationEngine.translate(s,"tooltip",manager),s->manager.community().candidates("tooltip",TranslationManager.plain(s.getString())));
        assertEquals(before,source);assertTrue(result.missing().isEmpty(),result.missing().toString());
        assertTrue(result.lines().stream().noneMatch(s->s.getString().matches(".*[A-Za-z].*")),result.lines().toString());
        var sea=result.lines().stream().filter(s->s.getString().contains("20%")).findFirst().orElseThrow();
        assertEquals(" 海洋生物概率 20%",sea.getString());StyledLoreTest.color(sea,"海洋生物概率",ChatFormatting.DARK_AQUA);StyledLoreTest.color(sea,"20%",ChatFormatting.WHITE);
        var treasure=result.lines().stream().filter(s->s.getString().contains("0.5%")).findFirst().orElseThrow();
        assertEquals(" 宝藏概率 0.5%",treasure.getString());StyledLoreTest.color(treasure,"宝藏概率",ChatFormatting.GOLD);StyledLoreTest.color(treasure,"0.5%",ChatFormatting.WHITE);
    }
    @Test void statsCategoriesAndChangingValuesWorkInCommunityMenus() {
        for(String title:List.of("Stats & Equipment","Fishing Stats","Your Stats Breakdown")) {
            var menu=GuiTranslationEngine.menu(title,manager);assertNotNull(menu,title);
            for(var row:List.of(new String[]{"Combat Stats","战斗属性"},new String[]{"Mining Stats","采矿属性"},
                    new String[]{"Farming Stats","农业属性"},new String[]{"Foraging Stats","采伐属性"},
                    new String[]{"Hunting Stats","狩猎属性"},new String[]{"Wisdom Stats","智慧属性"}))
                assertEquals(row[1],GuiTranslationEngine.button(Component.literal(row[0]),menu).getString());
            for(var row:List.of(new String[]{"❁ Strength","❁ 力量"},new String[]{"⸕ Mining Speed","⸕ 挖掘速度"},
                    new String[]{"☘ Mangrove Fortune","☘ 红树时运"},new String[]{" Trophy Chance"," 奖杯概率"},
                    new String[]{"α Sea Creature Chance","α 海洋生物概率"})) {
                for(String value:List.of("0","0.5%","100%","1,234.56","+75")) {
                    var source=Component.literal("§6"+row[0]+" §f"+value);var before=source.copy();
                    var out=GuiTranslationEngine.detailLine(source,menu,manager);
                    assertEquals(row[1]+" "+value,out.getString());assertEquals(before,source);
                    StyledLoreTest.color(out,value,ChatFormatting.WHITE);
                }
            }
        }
    }
    @Test void menuRulesKeepInventoryAndAuctionBoundaries() {
        var row=Component.literal(" Sea Creature Chance 20%");
        assertNull(GuiTranslationEngine.menu("Large Chest",manager));
        assertSame(row,GuiTranslationEngine.button(row,null));
        var seller=Component.literal("Seller: Player_123");
        assertSame(seller,GuiTranslationEngine.button(seller,GuiTranslationEngine.menu("Stats & Equipment",manager)));
        var player=Component.literal("Player_123: Fishing Stats");
        assertSame(player,GuiTranslationEngine.button(player,GuiTranslationEngine.menu("Stats & Equipment",manager)));
        var itemStat=Component.literal("Damage: +100");
        assertSame(itemStat,GuiTranslationEngine.button(itemStat,GuiTranslationEngine.menu("SkyBlock Menu",manager)));
    }
    @Test void communityExactAndNumberedMenusInheritOnlyCommonRules() {
        for(String title:List.of("Stats Tuning","(1/2) Equipment Sets")) {
            var menu=GuiTranslationEngine.menu(title,manager);assertNotNull(menu);assertEquals("community",menu.id());
            assertEquals("钓鱼属性",GuiTranslationEngine.button(Component.literal("Fishing Stats"),menu).getString());
            var localOnly=Component.literal("Damage: +100");assertSame(localOnly,GuiTranslationEngine.button(localOnly,menu));
        }
    }
}
