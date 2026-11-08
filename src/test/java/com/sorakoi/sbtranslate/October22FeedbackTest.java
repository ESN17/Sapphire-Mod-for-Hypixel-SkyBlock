package com.sorakoi.sbtranslate;

import com.google.gson.*;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class October22FeedbackTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;}

    @Test void bitsKeepsItsValueAndStyleInHud() {
        for(String scope:List.of("tablist","scoreboard")) {
            var raw=Component.literal("  §bBits: §f1,234 / 9,876  ");var copy=raw.copy();
            var result=HudTranslationEngine.translate(raw,scope,manager);
            assertEquals("  比特: 1,234 / 9,876  ",result.getString());
            StyledLoreTest.color(result,"比特:",ChatFormatting.AQUA);
            StyledLoreTest.color(result,"1,234 / 9,876",ChatFormatting.WHITE);assertEquals(copy,raw);
        }
    }

    @Test void equipmentCodesSurviveNamesAndReforgePrefixes() {
        var names=Map.ofEntries(Map.entry("TITANIUM_DRILL_4","钛钻头 DR-X655"),
            Map.entry("TITANIUM_DRILL_1","钛钻头 DR-X355"),Map.entry("MITHRIL_DRILL_2","秘银钻头 SX-R326"),
            Map.entry("GEMSTONE_DRILL_1","红宝石钻头 TX-15"),Map.entry("GEMSTONE_DRILL_2","宝石钻头 LT-522"),
            Map.entry("GEMSTONE_DRILL_3","黄玉钻头 KGR-12"),Map.entry("FTX_3070","FTX 3070"),
            Map.entry("ABICASE_SUMSUNG_1","三松© G3 手机壳"),Map.entry("ABICASE_SUMSUNG_2","三松© GG 手机壳"),
            Map.entry("SYNTHESIZER_V1","合成器 v1"));
        for(var row:names.entrySet()) {
            var item=manager.item(row.getKey());assertEquals(row.getValue(),item.name());
            var source=Component.literal("§d"+item.originalName());var before=source.copy();
            var result=TextTranslationEngine.itemName(source,item.originalName(),item.name(),manager::reforge,item.styleMappings());
            assertEquals(row.getValue(),TranslationManager.plain(result.getString()));assertEquals(before,source);
        }
        var item=manager.item("TITANIUM_DRILL_4");
        var source=Component.literal("§dAuspicious Titanium Drill DR-X655");
        var out=TextTranslationEngine.itemName(source,item.originalName(),item.name(),manager::reforge,item.styleMappings());
        assertEquals(manager.reforge("Auspicious")+"钛钻头 DR-X655",out.getString());
        StyledLoreTest.color(out,"DR-X655",ChatFormatting.LIGHT_PURPLE);
    }

    @Test void allGemstoneNamesAndRawLoreTranslateWithoutDuplicatedGlyphs() throws Exception {
        try(var reader=new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/gemstone-lore.json")),StandardCharsets.UTF_8)) {
            var fixtures=JsonParser.parseReader(reader).getAsJsonObject();assertEquals(60,fixtures.size());
            for(var fixture:fixtures.entrySet()) {
                var data=fixture.getValue().getAsJsonObject();var item=manager.item(fixture.getKey());
                var source=Component.literal(data.get("name").getAsString());
                var name=TextTranslationEngine.itemName(source,item.originalName(),item.name(),manager::reforge,item.styleMappings());
                assertEquals(item.name(),TranslationManager.plain(name.getString()),fixture.getKey());
                assertFalse(item.name().contains("宝石宝石"));assertFalse(item.name().contains("  "));
                var lines=new ArrayList<Component>();data.getAsJsonArray("lore").forEach(s->lines.add(Component.literal(s.getAsString())));
                var before=lines.stream().map(Component::copy).toList();
                var out=LoreTranslationEngine.translate(lines,manager,fixture.getKey(),(s,o)->List.of(s),false);
                assertEquals(before,lines);
                assertFalse(out.lines().stream().map(Component::getString).anyMatch(s->s.matches(".*[A-Za-z].*")),fixture.getKey()+" "+out.lines());
            }
        }
    }

    @Test void pristineMessageTranslatesEveryGemstoneAndPreservesLiveCount() {
        for(String gem:List.of("AMBER","AMETHYST","AQUAMARINE","CITRINE","JADE","JASPER","ONYX","OPAL","PERIDOT","RUBY","SAPPHIRE","TOPAZ")) {
            var item=manager.item("FLAWED_"+gem+"_GEM");var click=new ClickEvent.RunCommand("/viewrecipe FLAWED_"+gem+"_GEM");
            String glyph=item.originalName().substring(0,1),bare=item.originalName().substring(2);
            var raw=Component.literal("§d§lPRISTINE! §r§fYou found §a"+glyph+" "+bare+" §ex26§f!").withStyle(s->s.withClickEvent(click));
            var copy=raw.copy();var out=SystemTranslationEngine.translate(raw,"chat",manager);
            assertEquals("提纯！ 你获得了 "+item.name()+" x26!",out.getString(),gem);
            StyledLoreTest.color(out,"26",ChatFormatting.YELLOW);StyledLoreTest.color(out,glyph,ChatFormatting.GREEN);
            assertEquals(click,StyledLoreTest.styleAt(out,glyph).getClickEvent());assertEquals(copy,raw);
        }
        var player=Component.literal("[MVP+] Player: PRISTINE! You found  Flawed Onyx Gemstone x26!");
        assertSame(player,SystemTranslationEngine.translate(player,"chat",manager));
    }

    @Test void pristineAliasesAndAllFiveBookEffectsUseUserTerm() {
        for(String alias:List.of("Pristine","Prismatic")) {
            var header=Component.literal("§9"+alias+" §fV");
            var result=EnchantmentTranslationEngine.translate(header,manager::enchantment);
            assertEquals("提纯 V",result.getString());
            StyledLoreTest.color(result,"提纯",ChatFormatting.BLUE);
            StyledLoreTest.color(result,"V",ChatFormatting.WHITE);
        }
        for(int level=1;level<=5;level++) {
            String amount="+"+(level*0.5);
            var input=List.<Component>of(Component.literal("§7Grants §5"+amount+"  Pristine§7, which"),
                Component.literal("§7increases the chance to improve the"),
                Component.literal("§7quality of dropped §dGemstones§7."));
            var before=input.stream().map(Component::copy).toList();
            var result=LoreTranslationEngine.translate(input,manager,"PRISTINE;"+level,(s,o)->List.of(s),false);
            assertEquals(1,result.lines().size());
            var line=result.lines().getFirst();
            assertEquals("提供 "+amount+"  提纯,可提高掉落宝石提升品质的概率.",line.getString());
            StyledLoreTest.color(line,"提纯",ChatFormatting.DARK_PURPLE);
            StyledLoreTest.color(line,amount,ChatFormatting.DARK_PURPLE);
            StyledLoreTest.color(line,"宝石",ChatFormatting.LIGHT_PURPLE);
            assertEquals(before,input);
        }
    }
}
