package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UpgradeTooltipTest {
    static TranslationManager manager;
    @BeforeAll static void dictionaries() throws Exception {
        manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));
    }
    static List<Component> lore(String id,String... text) {
        return LoreTranslationEngine.translate(Arrays.stream(text).<Component>map(Component::literal).toList(),manager,id,(s,lines)->List.of(s),false).lines();
    }
    static String joined(List<Component> lines){return String.join("\n",lines.stream().map(Component::getString).toList());}
    @Test void starredAndDyedNamesRetainDecorationsAndReforge() {
        for(var row:List.of(new String[]{"STARRED_MIDAS_SWORD","§d\ue068 Gilded Midas' Sword §6✪✪✪✪✪§c➎","\ue068 鎏金的米达斯之剑 ✪✪✪✪✪➎"},
                            new String[]{"STARRED_SHADOW_ASSASSIN_CHESTPLATE","§c✿ §d\ue068 Ancient Shadow Assassin Chestplate §6✪✪✪✪✪","✿ \ue068 远古的暗影刺客胸甲 ✪✪✪✪✪"},
                            new String[]{"STARRED_SHADOW_ASSASSIN_HELMET","§c✿ §d\ue068 Ancient Shadow Assassin Helmet §6✪✪✪✪✪","✿ \ue068 远古的暗影刺客头盔 ✪✪✪✪✪"})) {
            var item=manager.item(row[0]);var original=Component.literal(row[1]);
            var result=TextTranslationEngine.itemName(original,item.originalName(),item.name(),manager::reforge,item.styleMappings());
            assertEquals(row[2],result.getString());assertEquals(row[1],original.getString());
            StyledLoreTest.color(result,"✪",ChatFormatting.GOLD);
            StyledLoreTest.color(result,row[0].contains("MIDAS")?"米达斯":"暗影",ChatFormatting.LIGHT_PURPLE);
            if(row[0].contains("SHADOW"))StyledLoreTest.color(result,"✿",ChatFormatting.RED);
        }
    }
    @Test void stackedStatBonusesKeepNumbersSignsBracketsAndSeparateColors() {
        var original=Component.literal("§7Strength: §c+315 §e(+30) §6[+5] §b(+170) §8(+488)");
        var result=lore("DARK_CLAYMORE",original.getString()).getFirst();
        assertEquals("力量: +315 (+30) [+5] (+170) (+488)",result.getString());
        StyledLoreTest.color(result,"力量",ChatFormatting.GRAY);StyledLoreTest.color(result,"+315",ChatFormatting.RED);
        StyledLoreTest.color(result,"(+30)",ChatFormatting.YELLOW);StyledLoreTest.color(result,"[+5]",ChatFormatting.GOLD);
        StyledLoreTest.color(result,"(+170)",ChatFormatting.AQUA);StyledLoreTest.color(result,"(+488)",ChatFormatting.DARK_GRAY);
        assertEquals("§7Strength: §c+315 §e(+30) §6[+5] §b(+170) §8(+488)",original.getString());
        var lines=lore("DARK_CLAYMORE","Gear Score: 1269 (1766)","Damage: +583 (+30) (+852.8)",
            "Crit Damage: +134% (+24%) (+198.4%)","Magic Find: +6 (+9)","Swing Range: +2 (+2)",
            "True Defence: +15 (+5)","True Defense: +15 (+5)","Ability Damage: +10% (+10%) (+15%)",
            "Price paid: 250,520,000 Coins","Gifted coins: 184.7k");
        assertEquals(List.of("装备评分: 1269 (1766)","伤害: +583 (+30) (+852.8)","暴击伤害: +134% (+24%) (+198.4%)",
            "稀有物品概率加成: +6 (+9)","挥击范围: +2 (+2)","真实防御: +15 (+5)","真实防御: +15 (+5)",
            "技能伤害: +10% (+10%) (+15%)","支付价格: 250,520,000 硬币","赠送硬币: 184.7k"),lines.stream().map(Component::getString).toList());
    }
    @Test void recombobulatedFooterAndSoulboundKeepDecorations() {
        var result=lore("DARK_CLAYMORE","§d§ka§r §d§lMYTHIC DUNGEON LONGSWORD §d§ka","§8* Co-op Soulbound *");
        assertEquals("a 神话 地牢 长剑 a",result.getFirst().getString());
        assertEquals("* 合作档灵魂绑定 *",result.getLast().getString());
        StyledLoreTest.color(result.getFirst(),"神话",ChatFormatting.LIGHT_PURPLE);
        result.getFirst().visit((style,text)->{if(text.contains("神话"))assertTrue(style.isBold());if(text.equals("a"))assertTrue(style.isObfuscated());return Optional.empty();},Style.EMPTY);
        assertEquals("灵魂绑定",lore(null,"Soulbound").getFirst().getString());
    }
    @Test void upgradedCloakAndSpiritMaskFollowLiveRarity() {
        for(var row:List.of(new String[]{"SHADOW_ASSASSIN_CLOAK","Shadow Assassin Cloak","暗影刺客披风","CLOAK","披风"},
                            new String[]{"STARRED_SHADOW_ASSASSIN_CLOAK","Shadow Assassin Cloak","暗影刺客披风","CLOAK","披风"},
                            new String[]{"SPIRIT_MASK","Spirit Mask","灵魂面具","HELMET","头盔"},
                            new String[]{"STARRED_SPIRIT_MASK","Spirit Mask","灵魂面具","HELMET","头盔"})) {
            var entry=manager.item(row[0]);assertNotNull(entry,row[0]);
            for(var rarity:List.of(new String[]{"EPIC","史诗","§5"},new String[]{"LEGENDARY","传说","§6"},new String[]{"MYTHIC","神话","§d"})) {
                var footer=lore(row[0],rarity[2]+rarity[0]+" DUNGEON "+row[3]).getFirst();
                assertEquals(rarity[1]+" 地牢 "+row[4],footer.getString(),row[0]);
                StyledLoreTest.color(footer,rarity[1],ChatFormatting.getByCode(rarity[2].charAt(1)));
            }
            String decorated="§d\ue068 Ancient "+row[1]+" §6✪✪✪✪✪";
            var name=TextTranslationEngine.itemName(Component.literal(decorated),entry.originalName(),entry.name(),manager::reforge,entry.styleMappings());
            assertEquals("\ue068 远古的"+row[2]+" ✪✪✪✪✪",name.getString());
            StyledLoreTest.color(name,row[2],ChatFormatting.LIGHT_PURPLE);
            StyledLoreTest.color(name,"✪",ChatFormatting.GOLD);
        }
    }
    @Test void appliedRuneAndDyeNamesAreIndependentOfEquipmentId() {
        var lines=lore("STARRED_SHADOW_ASSASSIN_CHESTPLATE","§c✿ Red Tulip Dyed","§c◆ Blood Rune §6III");
        assertEquals("✿ 红色郁金香 染色",lines.getFirst().getString());
        assertEquals("◆ 血符文 III",lines.getLast().getString());
        StyledLoreTest.color(lines.getFirst(),"✿",ChatFormatting.RED);StyledLoreTest.color(lines.getFirst(),"染色",ChatFormatting.RED);
        StyledLoreTest.color(lines.getLast(),"血符文",ChatFormatting.RED);StyledLoreTest.color(lines.getLast(),"III",ChatFormatting.GOLD);
    }
    @Test void warningsRequirementsAndMasterFloorsTolerateDifferentWrapping() {
        var lines=lore("DARK_CLAYMORE","§cSome of your enchantments require","§ca higher Enchanting level!","",
            "§c❣ Requires Catacombs Skill 17 while in The Catacombs.","",
            "§c❣ Requires Master Mode The Catacombs Floor VII","§cCompletion.","",
            "§c❣ Requires Enchanting Skill §a25§c.");
        String text=joined(lines);
        assertTrue(text.contains("你的部分附魔需要更高的附魔等级!"),text);
        assertTrue(text.contains("❣ 在地牢中需要地牢技能 17."),text);
        assertTrue(text.contains("❣ 需要完成大师模式地牢第 VII 层."),text);
        assertTrue(text.contains("❣ 需要附魔技能 25."),text);
        assertFalse(text.contains("Requires"),text);assertFalse(text.contains("Completion"),text);
        StyledLoreTest.color(lines.getLast(),"25",ChatFormatting.GREEN);
    }
    @Test void reforgeBonusesTranslateOnEquipmentAndOnStones() {
        for(String id:List.of("STARRED_SHADOW_ASSASSIN_CHESTPLATE","PRECURSOR_GEAR")) {
            var lines=lore(id,"§9Ancient Bonus","§7Grants §b+1\ue007 Crit Damage §7per","§cCatacombs §7skill level you have.");
            assertEquals("远古 加成\n每级地牢技能等级提供 +1\ue007 暴击伤害.",joined(lines));
            StyledLoreTest.color(lines.get(1),"地牢",ChatFormatting.RED);
            StyledLoreTest.color(lines.get(1),"暴击伤害",ChatFormatting.AQUA);
            StyledLoreTest.color(lines.get(1),"+1",ChatFormatting.AQUA);
        }
        var withered=lore("DARK_CLAYMORE","§9Withered Bonus","§7Grants §c+1\ue00d Strength §7per §cCatacombs","§7skill level you have.");
        assertEquals("凋零 加成\n每级地牢技能等级提供 +1\ue00d 力量.",joined(withered));
        var gilded=lore("STARRED_MIDAS_SWORD","§9Byron's Compassion §8(Gilded)",
            "§7Killing a mob has a rare chance to","§7grant §6Coins §7to a nearby player.");
        assertEquals("Byron 的怜悯 (鎏金)\n击杀生物时,有小概率赠送硬币给附近的一名玩家.",joined(gilded));
        StyledLoreTest.color(gilded.getLast(),"硬币",ChatFormatting.GOLD);
    }
    @Test void attachedSmiteEffectMatchesLiveOrderIconsAndLineBreaks() {
        var lines=lore("UNDEAD_SWORD","§9Smite V","§7Increases damage dealt to §2\ue084",
            "§2Undead§7, §f\ue085 Skeletal §7and §8\ue086 Wither","§7mobs by §a30%§7.");
        assertEquals("亡灵杀手 V",lines.getFirst().getString());
        String description=lines.get(1).getString();
        assertEquals("对 \ue084 亡灵, \ue085 骷髅和 \ue086 凋灵生物的伤害提高 30%.",description);
        StyledLoreTest.color(lines.get(1),"亡灵",ChatFormatting.DARK_GREEN);
        StyledLoreTest.color(lines.get(1),"骷髅",ChatFormatting.WHITE);
        StyledLoreTest.color(lines.get(1),"凋灵",ChatFormatting.DARK_GRAY);
        StyledLoreTest.color(lines.get(1),"30%",ChatFormatting.GREEN);
        assertEquals("飞行生物杀手 VI",lore(null,"Gravity VI").getFirst().getString());
    }
    @Test void unknownNumericProseAndItemTypesAreNotGuessed() {
        for(String text:List.of("Damage: several", "MYTHIC UNKNOWN_TYPE", "A story about Soulbound", "Unknown Dye Dyed")) {
            var source=Component.literal(text);assertSame(source,TooltipTranslationEngine.translate(source,manager));
        }
    }
}
