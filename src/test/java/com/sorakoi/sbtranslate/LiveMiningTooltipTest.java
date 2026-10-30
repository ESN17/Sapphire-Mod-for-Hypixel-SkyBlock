package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LiveMiningTooltipTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    static List<Component> lore(String... text) {
        return LoreTranslationEngine.translate(Arrays.stream(text).<Component>map(Component::literal).toList(),manager,"DIVAN_DRILL",(s,lines)->List.of(s),false).lines();
    }
    @Test void livePartAliasesAndExtraStatsTranslate() {
        var lines=lore("§aSunny Side Goblin Egg Part","§aTitanium Drill Engine","§7Pristine: §a+2 §b(+2)","§7Block Fortune: §6+65 §b(+65)");
        assertEquals(List.of("单面煎哥布林煎蛋卷 配件","镀钛钻头引擎","提纯: +2 (+2)","方块时运: +65 (+65)"),lines.stream().map(Component::getString).toList());
        StyledLoreTest.color(lines.get(0),"单面煎",ChatFormatting.GREEN);
        StyledLoreTest.color(lines.get(2),"+2",ChatFormatting.GREEN);StyledLoreTest.color(lines.get(2),"(+2)",ChatFormatting.AQUA);
        StyledLoreTest.color(lines.get(3),"+65",ChatFormatting.GOLD);
        assertEquals("Goblin Egg",lore("Goblin Egg").getFirst().getString());
    }
    @Test void accumulatedEnchantsKeepProgressAndItsColor() {
        for(String name:List.of("Compact","Expertise","Cultivating","Champion","Toxophilite","Hecatomb","Absorb")) {
            var source=Component.literal("§9"+name+" §fVIII §8239,157");var snapshot=source.copy();
            var translated=EnchantmentTranslationEngine.translate(source,manager::enchantment);
            assertEquals(manager.enchantment(name)+" VIII 239,157",translated.getString());
            StyledLoreTest.color(translated,manager.enchantment(name),ChatFormatting.BLUE);
            StyledLoreTest.color(translated,"VIII",ChatFormatting.WHITE);StyledLoreTest.color(translated,"239,157",ChatFormatting.DARK_GRAY);
            assertEquals(new EnchantmentTranslationEngine.Header(name,8),EnchantmentTranslationEngine.header(source.getString()));
            assertEquals(snapshot,source);
        }
        assertEquals("采矿专精 VIII 239,157, 效率 V",lore("Compact VIII 239,157, Efficiency V").getFirst().getString());
        assertEquals("近战战斗专精 X 1.2M",lore("Champion X 1.2M").getFirst().getString());
        for(String bad:List.of("Compact VIII 239,15","Compact VIII 239,157 blocks","Gain Compact VIII 239,157 damage")) {
            var source=Component.literal(bad);assertSame(source,EnchantmentTranslationEngine.translate(source,manager::enchantment));
        }
    }
    @Test void fortuneAndFleetEffectsAcceptNewIconsAndOldSpacing() {
        for(String icon:List.of("\ue053","☘"))for(String space:List.of(""," ")) {
            var lines=lore("§9Fortune III","§7Grants §6+30"+space+"§e"+icon+"§6 Mining Fortune§7, which",
                "§7increases your chance for multiple","§7drops.");
            assertEquals("时运 III",lines.getFirst().getString());
            assertEquals("提供 +30"+space+icon+" 挖掘时运,提高获得多份掉落物的概率.",lines.getLast().getString());
            StyledLoreTest.color(lines.getLast(),"+30",ChatFormatting.GOLD);StyledLoreTest.color(lines.getLast(),icon,ChatFormatting.YELLOW);
            StyledLoreTest.color(lines.getLast(),"挖掘时运",ChatFormatting.GOLD);
        }
        var fleet=lore("§9Fleet Bonus","§7Grants §6+50\ue053 Mining Spread §7towards","§bBlocks§7.");
        assertEquals("疾行 加成",fleet.getFirst().getString());
        assertEquals("挖掘方块时提供 +50\ue053 采矿扩散.",fleet.getLast().getString());
        StyledLoreTest.color(fleet.getLast(),"方块",ChatFormatting.AQUA);
        StyledLoreTest.color(fleet.getLast(),"采矿扩散",ChatFormatting.GOLD);
    }
    @Test void compactHeaderWithProgressStillSelectsItsDescription() {
        var lines=lore("§9Compact VIII §8239,157","§7Gain §3+8☯ Mining Wisdom §7and a §a0.5%",
            "§7chance to drop an enchanted item.");
        assertEquals("采矿专精 VIII 239,157",lines.getFirst().getString());
        assertEquals("获得 +8☯ 采矿智慧,并有 0.5% 概率掉落附魔物品.",lines.getLast().getString());
        StyledLoreTest.color(lines.getLast(),"采矿智慧",ChatFormatting.DARK_AQUA);
        StyledLoreTest.color(lines.getLast(),"0.5%",ChatFormatting.GREEN);
    }
    @Test void combinedStatCapturesRejectProseAndCopyExactSpan() {
        var rule=new TranslationManager.Template("Grants {statIcon0} Mining Fortune.","提供 {statIcon0} 挖掘时运.");
        for(String value:List.of("+1,234.5\ue053","-10% ☘","0  ☯","30⸕")) {
            var match=rule.apply("Grants "+value+" Mining Fortune.");assertNotNull(match);
            assertEquals(value,match.pieces().stream().filter(TranslationManager.Piece::original).findFirst().orElseThrow().text());
        }
        for(String value:List.of("+30","many ☘","+30 word","+1,23☘","+30   ☘"))assertNull(rule.apply("Grants "+value+" Mining Fortune."));
    }
    @Test void classificationTagsKeepBothLegacyAndModernIcons() {
        for(var row:List.of(new String[]{"Undead","亡灵类","\ue084"},new String[]{"Undead","亡灵类","༕"},
            new String[]{"Arachnal","蛛类","\ue074"},new String[]{"Arachnal","蛛类","Ж"})) {
            var lines=lore("§aThis item is §2"+row[0]+" §b"+row[2]+"§a!");
            assertEquals("此物品属于"+row[1]+" "+row[2]+"!",lines.getFirst().getString());
            StyledLoreTest.color(lines.getFirst(),row[1],ChatFormatting.DARK_GREEN);
            StyledLoreTest.color(lines.getFirst(),row[2],ChatFormatting.AQUA);
            StyledLoreTest.color(lines.getFirst(),"此物品属于",ChatFormatting.GREEN);
        }
        assertEquals("This item is FutureType !",lore("This item is FutureType !").getFirst().getString());
        assertEquals("此物品使用后消耗.",lore("This item is consumed on use.").getFirst().getString());
        assertEquals("此物品已达最终形态!",lore("This item is in its final form!").getFirst().getString());
    }
    @TestFactory Collection<DynamicTest> renamedSlayerWeaponsRetainReforgesAndStars() throws Exception {
        var tests=new ArrayList<DynamicTest>();
        for(var row:AttachmentTooltipTest.fixtures("slayer-name-fixtures.json"))tests.add(DynamicTest.dynamicTest(row.get("id").getAsString(),()->{
            var item=manager.item(row.get("id").getAsString());String target=row.get("chinese").getAsString();
            assertEquals(target,item.name());
            var source=Component.literal("§dHeroic "+row.get("english").getAsString()+" §6✪✪✪");var copy=source.copy();
            var result=TextTranslationEngine.itemName(source,item.originalName(),item.name(),manager::reforge,item.styleMappings());
            assertEquals("英雄的"+target+" ✪✪✪",result.getString());
            StyledLoreTest.color(result,target,ChatFormatting.LIGHT_PURPLE);StyledLoreTest.color(result,"✪",ChatFormatting.GOLD);
            assertEquals(copy,source);
        }));return tests;
    }
    @Test void shinyNamesAndRaritiesPreserveEachIndependentStyle() {
        var ids=new ArrayList<>(List.of("NECRON_HANDLE","NECRON_BLADE","HYPERION","ASTRAEA","SCYLLA","VALKYRIE"));
        for(String set:List.of("WITHER","WISE_WITHER","TANK_WITHER","SPEED_WITHER","POWER_WITHER"))
            for(String slot:List.of("HELMET","CHESTPLATE","LEGGINGS","BOOTS"))ids.add(set+"_"+slot);
        for(String id:ids)for(String reforge:List.of("","Ancient ")) {
            var item=manager.item(id);assertNotNull(item,id);
            var source=Component.literal("§c✿ §eShiny §b"+reforge+"§d"+item.originalName()+" §6✪✪✪✪✪§c➎");var before=source.copy();
            var result=TextTranslationEngine.itemName(source,item.originalName(),item.name(),manager::reforge,item.styleMappings(),manager.scope("name-modifiers"));
            assertEquals("✿ 闪耀 "+(reforge.isEmpty()?"":"远古的")+item.name()+" ✪✪✪✪✪➎",result.getString(),id);
            StyledLoreTest.color(result,"闪耀",ChatFormatting.YELLOW);
            if(!reforge.isEmpty())StyledLoreTest.color(result,"远古的",ChatFormatting.AQUA);
            StyledLoreTest.color(result,"✪",ChatFormatting.GOLD);assertEquals(before,source);
        }
        var footer=lore("§d§ka§r §d§lSHINY MYTHIC DUNGEON HELMET §d§ka").getFirst();
        assertEquals("a 闪耀 神话 地牢 头盔 a",footer.getString());
        StyledLoreTest.color(footer,"闪耀",ChatFormatting.LIGHT_PURPLE);
        assertEquals("SHINY MYTHIC UNKNOWN_TYPE",lore("SHINY MYTHIC UNKNOWN_TYPE").getFirst().getString());
        assertEquals("未完型的奈克隆之刃",manager.item("NECRON_BLADE").name());
        assertEquals("米达斯法杖",manager.item("MIDAS_STAFF").name());
    }
}
