package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GuiTranslationTest {
    static TranslationManager manager;
    @TempDir Path root;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release() {manager=null;}
    static LoreTranslationEngine.Result lore(String title,String... lines) {
        var menu=GuiTranslationEngine.menu(title,manager);assertNotNull(menu,title);
        return LoreTranslationEngine.translate(Arrays.stream(lines).<Component>map(Component::literal).toList(),manager,null,(s,original)->List.of(s),false,false,false,menu.paragraphs());
    }
    @Test void titlesPreserveUntranslatedSearchAndProductValues() {
        for(var row:List.of(new String[]{"SkyBlock Menu","SkyBlock 菜单"},new String[]{"Collections","收藏"},
            new String[]{"Auction House","拍卖行"},new String[]{"Bazaar ➜ Enchanted Diamond","集市 ➜ 附魔钻石"},
            new String[]{"Bazaar ➜ Mining","集市 ➜ 采矿"},
            new String[]{"Auctions: \"Storm Search_User123\"","拍卖: \"Storm Search_User123\""},
            new String[]{"Auctions Browser (12/30)","拍卖列表 (12/30)"})) {
            var source=Component.literal("§a"+row[0]);var copy=source.copy();
            var target=GuiTranslationEngine.title(source,manager);assertEquals(row[1],target.getString());assertEquals(copy,source);
            StyledLoreTest.color(target,row[1],ChatFormatting.GREEN);
        }
        for(String text:List.of("Large Chest","My Bazaar Chest","Auction House secrets","Auction House\nUser")) {
            var source=Component.literal(text);assertSame(source,GuiTranslationEngine.title(source,manager));
        }
    }
    @Test void pricesFeesAndTimersUseLiveValuesAndColors() {
        var lines=lore("Auction View","§7Seller: §b[MVP+] Player_123","§7Starting bid: §61,234,567.8 coins",
            "§7Extra fee: §6+25 coins §8(5%)","§7Ends in: §e12h 3m 4s").lines();
        assertEquals(List.of("卖家: [MVP+] Player_123","起拍价: 1,234,567.8 硬币","额外手续费: +25 硬币 (5%)","剩余时间: 12h 3m 4s"),lines.stream().map(Component::getString).toList());
        StyledLoreTest.color(lines.get(0),"[MVP+] Player_123",ChatFormatting.AQUA);
        StyledLoreTest.color(lines.get(1),"1,234,567.8",ChatFormatting.GOLD);StyledLoreTest.color(lines.get(1),"硬币",ChatFormatting.GOLD);
        StyledLoreTest.color(lines.get(2),"5%",ChatFormatting.DARK_GRAY);StyledLoreTest.color(lines.get(3),"12h 3m 4s",ChatFormatting.YELLOW);
    }
    @Test void bazaarProgressAndPricesDoNotUseHardcodedNumbers() {
        var result=lore("Bazaar ➜ Enchanted Diamond","§7Amount: §a71,680x","§7Price per unit: §6123.45 coins",
            "§7Filled: §a35,840§7/§a71,680 §8(50%)","§eClick to create buy order!");
        assertTrue(result.missing().isEmpty(),result.missing().toString());
        assertEquals(List.of("数量: 71,680x","单价: 123.45 硬币","已成交: 35,840/71,680 (50%)","点击创建买单!"),result.lines().stream().map(Component::getString).toList());
        StyledLoreTest.color(result.lines().get(2),"50%",ChatFormatting.DARK_GRAY);
        assertEquals("Seller: Player_123",lore("Bazaar","Seller: Player_123").lines().getFirst().getString());
        assertEquals("Mining",lore("Auction House","Mining").lines().getFirst().getString());
        assertEquals("状态: 已完成!",lore("Bazaar","Status: Filled!").lines().getFirst().getString());
    }
    @Test void selectedFilterArrowAndRarityRemainDistinct() {
        var lines=lore("Auctions Browser","§b▶ Highest Bid","§7 Lowest Bid","§b▶ Legendary").lines();
        assertEquals(List.of("▶ 最高出价"," 最低出价","▶ 传说"),lines.stream().map(Component::getString).toList());
        StyledLoreTest.color(lines.get(0),"▶",ChatFormatting.AQUA);StyledLoreTest.color(lines.get(1),"最低出价",ChatFormatting.GRAY);
    }
    @Test void buttonsRetainClickHoverAndOriginalComponents() {
        var click=new ClickEvent.RunCommand("/original_command_123");var hover=new HoverEvent.ShowText(Component.literal("private original hover"));
        var source=Component.literal("Manage Orders").withStyle(s->s.withColor(ChatFormatting.GREEN).withClickEvent(click).withHoverEvent(hover));
        var before=source.copy();var result=GuiTranslationEngine.button(source,GuiTranslationEngine.menu("Bazaar",manager));
        assertEquals("管理订单",result.getString());
        result.visit((style,text)->{if(!text.isBlank()){assertEquals(click,style.getClickEvent());assertEquals(hover,style.getHoverEvent());}return Optional.empty();},Style.EMPTY);
        assertEquals(before,source);assertSame(source,GuiTranslationEngine.button(source,null));
    }
    @Test void guiOnlyDoesNotTranslateItemStatsOrEnchantments() {
        assertEquals(List.of("Damage: +100","Sharpness V","点击查看!"),lore("SkyBlock Menu","Damage: +100","Sharpness V","Click to view!").lines().stream().map(Component::getString).toList());
    }
    @Test void damagedGuiDictionaryDoesNotReplaceWorkingSnapshot() throws Exception {
        Files.createDirectories(root.resolve("translations"));
        Files.copy(Path.of("src/main/resources/defaults/translations/gui-menus.json"),root.resolve("translations/gui-menus.json"));
        var local=new TranslationManager();local.load(root);
        Files.writeString(root.resolve("translations/gui-menus.json"),"{broken");
        assertThrows(Exception.class,()->local.load(root));
        assertEquals("拍卖行",GuiTranslationEngine.title(Component.literal("Auction House"),local).getString());
    }
    @TestFactory Collection<DynamicTest> publicMenuSamplesKeepDescriptionsAndCommands() throws Exception {
        var tests=new ArrayList<DynamicTest>();
        for(var row:AttachmentTooltipTest.fixtures("gui-tooltip-fixtures.json"))tests.add(DynamicTest.dynamicTest(row.get("menu").getAsString()+" / "+row.get("name").getAsString(),()->{
            var menu=GuiTranslationEngine.menu(row.get("menu").getAsString(),manager);
            var original=Component.literal(row.get("name").getAsString());
            var name=GuiTranslationEngine.button(original,menu);
            assertEquals(row.get("expected").getAsString(),name.getString());
            var source=AttachmentTooltipTest.lines(row);var copy=source.stream().map(Component::copy).toList();
            var result=LoreTranslationEngine.translate(source,manager,null,(s,lines)->List.of(s),false,false,false,menu.paragraphs());
            assertTrue(result.missing().isEmpty(),result.missing().toString());
            assertEquals(copy,source);
        }));return tests;
    }
}
