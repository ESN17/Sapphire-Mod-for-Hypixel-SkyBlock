package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SidebarTranslationTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release() {manager=null;}
    @Test void allLocationLabelsAreChineseAndPreserveMarkersAndSource() {
        assertTrue(manager.scope("locations").size()>=344);
        var click=new ClickEvent.RunCommand("/unchanged");
        for(var entry:manager.scope("locations").entrySet()) {
            assertFalse(entry.getValue().matches(".*[A-Za-z].*"),entry.toString());
            for(String marker:List.of("", "⏣ ","ф "," "," ")) {
                var source=Component.literal(marker).withStyle(ChatFormatting.AQUA).append(Component.literal(entry.getKey()).withStyle(s->s.withColor(ChatFormatting.YELLOW).withClickEvent(click)));var copy=source.copy();
                var result=SidebarTranslationEngine.line(source,manager);
                assertEquals(marker+entry.getValue(),result.getString());assertEquals(copy,source);
                StyledLoreTest.color(result,entry.getValue(),ChatFormatting.YELLOW);
                assertEquals(click,StyledLoreTest.styleAt(result,entry.getValue()).getClickEvent());
            }
        }
        assertEquals("⏣ 集市巷",SidebarTranslationEngine.line(Component.literal("⏣ Baz⚽aar Alley"),manager).getString());
        assertEquals(" 集市巷",SidebarTranslationEngine.line(Component.literal(" Bazaar\u200b Alley"),manager).getString());
    }
    @Test void realDateUsesServerDateAndPreservesServerIdAndFieldStyles() {
        var raw=Component.literal("§710/17/26 §8m66CS");var copy=raw.copy();
        var result=SidebarTranslationEngine.line(raw,manager);
        assertEquals("2026年10月17日 m66CS",result.getString());assertEquals(copy,raw);
        StyledLoreTest.color(result,"2026年10月17日",ChatFormatting.GRAY);StyledLoreTest.color(result,"m66CS",ChatFormatting.DARK_GRAY);
        var fields=SidebarTranslationEngine.line(Component.literal("§a01§7/§b02§7/§d2027 §8mini123A"),manager);
        assertEquals("2027年1月2日 mini123A",fields.getString());
        StyledLoreTest.color(fields,"2027年",ChatFormatting.LIGHT_PURPLE);StyledLoreTest.color(fields,"1月",ChatFormatting.GREEN);StyledLoreTest.color(fields,"2日",ChatFormatting.AQUA);
        assertEquals("2028年2月29日",SidebarTranslationEngine.line(Component.literal("02/29/28"),manager).getString());
        for(String invalid:List.of("02/29/26","13/01/26","10/32/26","not 10/17/26")) {
            var source=Component.literal(invalid);assertSame(source,SidebarTranslationEngine.line(source,manager));
        }
    }
    @Test void everyMonthAndAllThirtyOneOrdinalsAreTranslated() {
        assertEquals(12,manager.scope("skyblock-months").size());
        for(var month:manager.scope("skyblock-months").entrySet())for(int day=1;day<=31;day++) {
            String suffix=day>=11 && day<=13?"th":switch(day%10){case 1->"st";case 2->"nd";case 3->"rd";default->"th";};
            var raw=Component.literal("§f"+month.getKey()+" §e"+day+suffix);
            var result=SidebarTranslationEngine.line(raw,manager);
            assertEquals(month.getValue()+day+"日",result.getString());
            StyledLoreTest.color(result,month.getValue(),ChatFormatting.WHITE);StyledLoreTest.color(result,day+"日",ChatFormatting.YELLOW);
        }
        assertEquals("初秋31日",SidebarTranslationEngine.line(Component.literal("Early Autumn 31st"),manager).getString());
    }
    @Test void objectiveKeepsDirectionAndUnrelatedContentIsUntouched() {
        var result=SidebarTranslationEngine.line(Component.literal("§fObjective §a⬊"),manager);
        assertEquals("任务 ⬊",result.getString());StyledLoreTest.color(result,"任务",ChatFormatting.WHITE);StyledLoreTest.color(result,"⬊",ChatFormatting.GREEN);
        for(String text:List.of("5:00am","12:00pm ☀","www.hypixel.net","SKYBLOCK","Player Bazaar Alley","Early Autumn 32nd","Early Autumn 11st","10/17/26private")) {
            var raw=Component.literal(text);assertSame(raw,SidebarTranslationEngine.line(raw,manager));
        }
        assertEquals("与酒保交谈",TextTranslationEngine.translate(Component.literal("Talk to the Bartender"),s->manager.match("scoreboard",null,s)).getString());
    }
    @Test void eventGroupGlossaryOnlyAppliesToEventsTooltip() {
        var base=GuiTranslationEngine.menu("Bazaar",manager);
        assertEquals("活动",GuiTranslationEngine.button(Component.literal("Events"),base).getString());
        assertSame(base,GuiTranslationEngine.tooltipContext(base,Component.literal("Other Category"),manager));
        for(String header:List.of("Events","活动")) {
            var menu=GuiTranslationEngine.tooltipContext(base,Component.literal(header),manager);
            for(var entry:manager.scope("bazaar-events").entrySet()) {
                var source=Component.literal("§b▸ §7"+entry.getKey());
                var result=BazaarTranslationEngine.line(source,menu);
                assertEquals("▸ "+entry.getValue(),result.getString());
                StyledLoreTest.color(result,"▸",ChatFormatting.AQUA);StyledLoreTest.color(result,entry.getValue(),ChatFormatting.GRAY);
            }
            var source=List.<Component>of(Component.literal("12 products"),Component.literal("§6▸ §7Spooky"),Component.literal("§b▸ §7Winter"),Component.literal("§b▸ §7Minerals"),Component.literal("§6▸ §7Feast"),Component.literal("Click to view products!"));
            var result=LoreTranslationEngine.translate(source,manager,null,(s,o)->List.of(s),false,false,false,menu.paragraphs(),s->BazaarTranslationEngine.line(s,menu));
            assertEquals(List.of("12 种商品","▸ 万圣节","▸ 圣诞节","▸ 挖矿节","▸ 丰收节","点击查看商品!"),result.lines().stream().map(Component::getString).toList());
            assertTrue(result.missing().isEmpty(),result.missing().toString());
        }
        assertFalse(BazaarTranslationEngine.line(Component.literal("Winter"),base).getString().equals("圣诞节"));
        assertEquals("仲冬1日",SidebarTranslationEngine.line(Component.literal("Winter 1st"),manager).getString());
    }
    @Test void eventDirectoryUsesSameScopedCategoryNames() {
        var base=GuiTranslationEngine.menu("Bazaar",manager);
        for(String title:List.of("Oddities ➜ Events","(1/2) Oddities ➜ Events","Events ➜ Feast")) {
            var menu=GuiTranslationEngine.menu(title,manager);assertNotNull(menu);
            for(var entry:manager.scope("bazaar-events").entrySet()) {
                var raw=Component.literal(entry.getKey()).withStyle(ChatFormatting.LIGHT_PURPLE);var before=raw.copy();
                var result=GuiTranslationEngine.button(raw,menu);
                assertEquals(entry.getValue(),result.getString());assertEquals(before,raw);
                StyledLoreTest.color(result,entry.getValue(),ChatFormatting.LIGHT_PURPLE);
            }
            assertEquals(base.labels().get("Feast Flask"),menu.labels().get("Feast Flask"));
        }
        assertEquals("活动 ➜ 丰收节",GuiTranslationEngine.title(Component.literal("Events ➜ Feast"),manager).getString());
        assertNotEquals("圣诞节",GuiTranslationEngine.button(Component.literal("Winter"),base).getString());
        assertNull(GuiTranslationEngine.menu("Player ➜ Events",manager));
        assertNull(GuiTranslationEngine.menu("Events ➜ Player",manager));
    }
}
