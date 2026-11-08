package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EnchantmentTranslationTest {
    @TempDir Path root;
    private static final Map<String,String> NAMES = Map.of(
        "Sharpness","锋利", "Champion","近战战斗专精", "Toxophilite","远程战斗专精",
        "Ultimate Wise","终极智慧", "Scuba","潜水", "Looting","掠夺",
        "First Strike","一次打击", "Bobbin' Time","浮漂时刻", "Counter-Strike","反击");
    private Component translate(Component source) {
        return EnchantmentTranslationEngine.translate(source,NAMES::get);
    }
    @Test void gearListsPreserveEveryLevelColorSeparatorAndUltimateBold() {
        var source=Component.literal("§d§lUltimate Wise V§9, Sharpness §6VII§9, Looting §bV§9, ");
        String original=source.getString();
        var result=translate(source);
        assertEquals("终极智慧 V, 锋利 VII, 掠夺 V, ",result.getString());
        StyledLoreTest.color(result,"终极智慧",ChatFormatting.LIGHT_PURPLE);
        StyledLoreTest.color(result,"锋利",ChatFormatting.BLUE);
        StyledLoreTest.color(result,"VII",ChatFormatting.GOLD);
        StyledLoreTest.color(result,"掠夺",ChatFormatting.BLUE);
        result.visit((style,text)->{
            if(text.contains("终极智慧")) assertTrue(style.isBold());
            if(text.contains("锋利") || text.contains("掠夺")) assertFalse(style.isBold());
            return Optional.empty();
        },Style.EMPTY);
        assertEquals(original,source.getString());
    }
    @Test void repeatedNamesAndLevelsRetainDistinctStylesAndEvents() {
        var click=new ClickEvent.RunCommand("/view");
        var hover=new HoverEvent.ShowText(Component.literal("original"));
        var left=Component.literal("Sharpness V").withStyle(ChatFormatting.BLUE)
            .withStyle(s->s.withClickEvent(click).withHoverEvent(hover));
        var source=Component.empty().append(left).append(Component.literal(", ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("Sharpness V").withStyle(ChatFormatting.GOLD));
        var result=translate(source);
        assertEquals("锋利 V, 锋利 V",result.getString());
        var colors=new ArrayList<Integer>();
        result.visit((style,text)->{
            if(text.contains("锋利")) {
                colors.add(style.getColor().getValue());
                if(colors.size()==1){assertEquals(click,style.getClickEvent());assertEquals(hover,style.getHoverEvent());}
            }
            return Optional.empty();
        },Style.EMPTY);
        assertEquals(List.of(0x5555ff,0xffaa00),colors);
    }
    @Test void liveProgressionArabicLevelsHyphensAndApostrophesWork() {
        assertEquals("近战战斗专精 X, 远程战斗专精 IX",translate(Component.literal("Champion X, Toxophilite IX")).getString());
        assertEquals("潜水 5",translate(Component.literal("Scuba 5")).getString());
        assertEquals("反击 V, 浮漂时刻 III",translate(Component.literal("Counter-Strike V, Bobbin' Time III")).getString());
    }
    @Test void unknownEntriesRemainAndDescriptionsAreNeverWordReplaced() {
        assertEquals("锋利 V, Future Enchant III, 一次打击 IV",
            translate(Component.literal("Sharpness V, Future Enchant III, First Strike IV")).getString());
        for(String text:List.of("Gain Sharpness V damage.","Ability: Champion", "Sharpness", "Sharpness V damages mobs", "锋利 V", "First Strike Damage: +50%")) {
            var source=Component.literal(text);
            assertSame(source,translate(source),text);
        }
        var localized=Component.translatable("item.minecraft.diamond");
        assertSame(localized,translate(localized));
    }
    @Test void reloadAndLorePriorityWorkWithoutAnItemVariant() throws Exception {
        Path dict=root.resolve("translations/enchantments.json");
        Files.createDirectories(dict.getParent());
        Files.writeString(dict,"{\"Sharpness\":\"锋利\"}");
        var manager=new TranslationManager();manager.load(root);
        var source=Component.literal("§9Sharpness VII");
        var result=LoreTranslationEngine.translate(List.of(source),manager,null,(s,lines)->List.of(s),true);
        assertEquals("锋利 VII",result.lines().getFirst().getString());
        assertSame(source,result.lines().getLast());
        assertTrue(result.missing().isEmpty());
        Files.writeString(dict,"{broken");
        assertThrows(Exception.class,()->manager.load(root));
        assertEquals("锋利",manager.enchantment("Sharpness"));
        Files.writeString(dict,"{\"Sharpness\":\"测试译名\"}");manager.load(root);
        assertEquals("测试译名 VII",EnchantmentTranslationEngine.translate(source,manager::enchantment).getString());
    }
    @Test void combinedBooksChooseTheDescriptionForEachHeader() throws Exception {
        Path translations=root.resolve("translations");Files.createDirectories(translations.resolve("items"));
        Files.writeString(translations.resolve("enchantments.json"),"{\"Sharpness\":\"锋利\",\"Looting\":\"掠夺\"}");
        Files.writeString(translations.resolve("enchantment-ids.json"),"{\"Sharpness\":\"SHARPNESS\",\"Looting\":\"LOOTING\"}");
        Files.writeString(translations.resolve("items/SHARPNESS;7.json"),"""
            {"id":"SHARPNESS;7","_original_name":"Enchanted Book","loreParagraphs":[
                {"source":["Deal {{N0}} damage."],"translation":"造成 {{N0}} 伤害."}]}
            """);
        Files.writeString(translations.resolve("items/LOOTING;5.json"),"""
            {"id":"LOOTING;5","_original_name":"Enchanted Book","loreParagraphs":[
                {"source":["Gain {{N0}} drops."],"translation":"获得 {{N0}} 掉落物."}]}
            """);
        var manager=new TranslationManager();manager.load(root);
        var source=List.<Component>of(Component.literal("§9Sharpness VII"),Component.literal("§7Deal §a65%§7 damage."),
            Component.literal(""),Component.literal("§9Looting V"),Component.literal("§7Gain §a75%§7 drops."));
        var result=LoreTranslationEngine.translate(source,manager,null,(s,lines)->List.of(s),false,true);
        assertEquals(List.of("锋利 VII","造成 65% 伤害.","","掠夺 V","获得 75% 掉落物."),result.lines().stream().map(Component::getString).toList());
        StyledLoreTest.color(result.lines().get(1),"65%",ChatFormatting.GREEN);
        StyledLoreTest.color(result.lines().get(4),"75%",ChatFormatting.GREEN);
        assertEquals("§7Deal §a65%§7 damage.",source.get(1).getString());
    }
    @Test void installedBookCorpusUsesApprovedNamesAndValidDescriptions() throws Exception {
        Path defaults=Path.of("src/main/resources/defaults");
        var manager=new TranslationManager();manager.load(defaults);
        var catalog=TranslationManager.readObject(Path.of("translation-review/enchantments/review-catalog.json"));
        assertEquals(156,catalog.getAsJsonArray("entries").size());
        int variants=0;
        for(var entry:catalog.getAsJsonArray("entries")) {
            var row=entry.getAsJsonObject();String english=row.get("english").getAsString(),chinese=row.get("proposedChinese").getAsString();
            assertEquals(chinese,manager.enchantment(english));
            for(var variant:row.getAsJsonArray("bookVariants")) {
                String id=variant.getAsString();variants++;
                var data=TranslationManager.readObject(defaults.resolve("translations/items/"+id+".json"));
                String header=null;
                for(var line:data.getAsJsonArray("_original_lore")) if(line.getAsString().startsWith(english+" ")){header=line.getAsString();break;}
                assertNotNull(header,id);
                var line=Component.literal(header).withStyle(ChatFormatting.BLUE);
                var result=LoreTranslationEngine.translate(List.of(line),manager,id,(s,lines)->List.of(s),false);
                assertEquals(chinese+header.substring(english.length()),result.lines().getFirst().getString(),id);
                StyledLoreTest.color(result.lines().getFirst(),chinese,ChatFormatting.BLUE);
                assertNotNull(manager.enchantmentBook(EnchantmentTranslationEngine.header(header)),id);
                for(var p:manager.item(id).paragraphs())
                    assertFalse(p.source().size()>1 && p.source().stream().anyMatch(s->s.startsWith(english+" ")),"Title must be separated: "+id);
            }
        }
        assertEquals(767,variants);
        assertEquals("近战战斗专精",manager.enchantment("Champion"));
        assertEquals("远程战斗专精",manager.enchantment("Toxophilite"));
        assertEquals("采伐专精",manager.enchantment("Absorb"));
        assertEquals("冰盾",manager.enchantment("Refrigerate"));
        assertEquals("超级大脑",manager.enchantment("Big Brain"));
        var prosecute=manager.item("PROSECUTE;5").paragraphs().stream()
            .filter(p->p.source().contains("your target has.")).findFirst().orElseThrow();
        assertTrue(prosecute.translation().contains("每剩余百分之一"));
        assertFalse(prosecute.translation().contains("损失"));
    }
}
