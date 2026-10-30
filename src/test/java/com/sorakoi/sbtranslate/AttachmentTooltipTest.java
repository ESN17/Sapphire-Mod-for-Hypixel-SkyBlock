package com.sorakoi.sbtranslate;

import com.google.gson.*;
import com.sorakoi.sbtranslate.item.ItemIdResolver;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class AttachmentTooltipTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    static List<Component> components(String... lines){return Arrays.stream(lines).<Component>map(Component::literal).toList();}
    static LoreTranslationEngine.Result translate(String id,List<Component> lines){return LoreTranslationEngine.translate(lines,manager,id,(text,source)->List.of(text),false);}
    static String text(List<Component> lines){return String.join("\n",lines.stream().map(Component::getString).toList());}
    static List<JsonObject> fixtures(String file) throws Exception {
        try(var reader=Files.newBufferedReader(Path.of("src/test/resources/"+file))) {
            var result=new ArrayList<JsonObject>();JsonParser.parseReader(reader).getAsJsonArray().forEach(x->result.add(x.getAsJsonObject()));return result;
        }
    }
    static List<Component> lines(JsonObject row){var list=new ArrayList<Component>();row.getAsJsonArray("lore").forEach(x->list.add(Component.literal(x.getAsString())));return list;}
    @Test void requestedConsumableNamesRetainTheirColors() {
        for(var row:List.of(new String[]{"SUPERBOOM_TNT","超级爆炸TNT"},
            new String[]{"DWARVEN_OS_BLOCK_BRAN","矮人的方块麦麸"},new String[]{"DWARVEN_OS_GEMSTONE_GRAHAMS","矮人的宝石全麦饼干"},
            new String[]{"DWARVEN_OS_METALLIC_MINIS","矮人的金属迷你酒"},new String[]{"DWARVEN_OS_ORE_OATS","矮人的矿石燕麦"})) {
            var item=manager.item(row[0]);assertEquals(row[1],item.name());
            var name=TextTranslationEngine.itemName(Component.literal("§a"+item.originalName()),item.originalName(),item.name(),manager::reforge,item.styleMappings());
            assertEquals(row[1],name.getString());StyledLoreTest.color(name,row[1],ChatFormatting.GREEN);
        }
        var reference=translate("EXPLOSIVE_BOW",components("§7Acts as §9Superboom TNT§7!")).lines().getFirst();
        assertEquals("可当作超级爆炸TNT使用!",reference.getString());StyledLoreTest.color(reference,"超级爆炸TNT",ChatFormatting.BLUE);
    }
    @Test void installedDrillPartsKeepHeaderEffectsAndHighlightColors() {
        var source=components("§aPerfectly-Cut Fuel Tank","§a100,000 Max Fuel Capacity","§a-10% Pickaxe Ability Cooldown","",
            "§aAmber-Polished Drill Engine","§6+600 Mining Speed","§6+100 Mining Fortune","",
            "§aBlue Cheese Goblin Omelette Part","§7Adds §a+1 Level §7to all of your unlocked §5Heart of",
            "§5the Mountain §7perks.");
        var originals=source.stream().map(Component::copy).toList();var result=translate("DIVAN_DRILL",source);
        assertTrue(result.missing().isEmpty(),result.missing().toString());
        String out=text(result.lines());
        for(String expected:List.of("完美切割燃料罐","100,000 最大燃料容量","-10% 镐子技能冷却","琥珀抛光钻头引擎","+600 挖掘速度","+100 挖掘时运","蓝纹奶酪哥布林煎蛋卷 配件","山峰之心技能增加 +1 等级"))assertTrue(out.contains(expected),out);
        StyledLoreTest.color(result.lines().get(0),"完美切割燃料罐",ChatFormatting.GREEN);
        StyledLoreTest.color(result.lines().get(5),"挖掘速度",ChatFormatting.GOLD);
        StyledLoreTest.color(result.lines().getLast(),"等级",ChatFormatting.GREEN);
        StyledLoreTest.color(result.lines().getLast(),"山峰之心",ChatFormatting.DARK_PURPLE);
        assertEquals(originals,source);
    }
    @Test void witherImpactWorksOnAllFiveBladesWithLiveNumbersAndWrapping() {
        for(String id:List.of("NECRON_BLADE","HYPERION","ASTRAEA","SCYLLA","VALKYRIE"))for(String damage:List.of("13,208","1,234,567.8","9876543.21")) {
            var source=components("§aScroll Abilities:","§6Ability: Wither Impact  §e§lRIGHT CLICK",
                "§7Teleport §a10 blocks §7ahead of you","§7dealing §c"+damage+" §7damage to nearby",
                "§7enemies. Also reduces your damage","§7taken and grants an absorption",
                "§7shield for §e5 seconds§7.","§8Mana Cost: §b300");
            var copy=source.stream().map(Component::copy).toList();var r=translate(id,source);
            assertTrue(r.missing().isEmpty(),id+": "+r.missing());
            assertEquals("卷轴技能:",r.lines().getFirst().getString());
            var effect=r.lines().get(2);assertTrue(effect.getString().contains(damage));
            StyledLoreTest.color(effect,damage,ChatFormatting.RED);StyledLoreTest.color(effect,"格",ChatFormatting.GREEN);
            StyledLoreTest.color(effect,"秒",ChatFormatting.YELLOW);StyledLoreTest.color(effect,"伤害",ChatFormatting.GRAY);
            StyledLoreTest.color(r.lines().get(1),"右键",ChatFormatting.YELLOW);assertTrue(StyledLoreTest.styleAt(r.lines().get(1),"右键").isBold());
            assertEquals(copy,source);
            var bilingual=LoreTranslationEngine.translate(source,manager,id,(t,s)->List.of(t),true);
            assertTrue(bilingual.lines().containsAll(source));
        }
    }
    @TestFactory Stream<DynamicTest> dynamicDamageWeaponBodies() throws Exception {
        return fixtures("dynamic-ability-fixtures.json").stream().flatMap(row->Stream.of(false,true).map(mutate->DynamicTest.dynamicTest(row.get("id").getAsString()+" changed="+mutate,()->{
            String id=row.get("id").getAsString();var source=lines(row);
            if(mutate)source=source.stream().<Component>map(x->Component.literal(x.getString().replaceAll("(§c)([0-9][0-9,.]*)","$1"+"9876543.21"))).toList();
            var copy=source.stream().map(Component::copy).toList();var result=translate(id,source);
            assertTrue(result.missing().isEmpty(),id+": "+result.missing());
            if(text(source).contains("9876543.21"))assertTrue(text(result.lines()).contains("9876543.21"),id);
            assertEquals(copy,source);
        })));
    }
    @TestFactory Stream<DynamicTest> allInstalledPartsAndIndividualScrolls() throws Exception {
        return fixtures("installed-attachment-fixtures.json").stream().map(row->DynamicTest.dynamicTest(row.get("id").getAsString(),()->{
            String id=row.get("id").getAsString();var source=lines(row);
            var result=translate(id.endsWith("SCROLL")?"HYPERION":"DIVAN_DRILL",source);
            assertTrue(result.missing().isEmpty(),id+": "+result.missing());
            for(var line:result.lines())if(!line.getString().isBlank())assertTrue(line.getString().codePoints().anyMatch(c->c>=0x4e00 && c<=0x9fff),line.getString());
        }));
    }
    @TestFactory Stream<DynamicTest> everyStandaloneRuneResolvesAndTranslates() throws Exception {
        return fixtures("rune-fixtures.json").stream().map(row->DynamicTest.dynamicTest(row.get("id").getAsString(),()->{
            var extra=new CompoundTag();extra.putString("id","RUNE");var runes=new CompoundTag();runes.putInt(row.get("kind").getAsString(),row.get("level").getAsInt());extra.put("runes",runes);
            String id=ItemIdResolver.resolve(extra).variantId();assertEquals(row.get("id").getAsString(),id);
            var item=manager.item(id);assertNotNull(item);
            var source=Component.literal(row.get("name").getAsString());
            var name=TextTranslationEngine.itemName(source,item.originalName(),item.name(),manager::reforge,item.styleMappings());
            assertNotSame(source,name,id+" name: "+source.getString());
            var result=translate(id,lines(row));assertTrue(result.missing().isEmpty(),id+": "+result.missing());
            assertTrue(text(result.lines()).codePoints().anyMatch(c->c>=0x4e00 && c<=0x9fff));
        }));
    }
}
