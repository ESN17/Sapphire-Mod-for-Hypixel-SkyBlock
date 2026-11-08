package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeployableNoticeTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;}
    @Test void replacingDeployablesUsesCatalogNamesAndKeepsTheirColor() {
        for(String id:List.of("GLACITE_LANTERN","RADIANT_POWER_ORB","MANA_FLUX_POWER_ORB","OVERFLUX_POWER_ORB","PLASMAFLUX_POWER_ORB","SOS_FLARE","ALERT_FLARE","WARNING_FLARE")) {
            var item=manager.item(id);assertNotNull(item,id);
            var raw=Component.literal("§eYour previous §5"+item.originalName()+" §ewas removed!");var before=raw.copy();
            var out=SystemTranslationEngine.translate(raw,"chat",manager);
            assertNotSame(raw,out,id);assertTrue(out.getString().contains(item.name()),id+": "+out.getString());
            StyledLoreTest.color(out,item.name(),ChatFormatting.DARK_PURPLE);assertEquals(before,raw);
        }
        var raw=Component.literal("Your previous Uncatalogued Beacon was removed!");
        assertTrue(SystemTranslationEngine.translate(raw,"chat",manager).getString().contains("Uncatalogued Beacon"));
    }
    @Test void allPickaxeAvailabilityNoticesUseExistingSkillNames() {
        for(var entry:Map.of("Mining Speed Boost","挖掘速度提升","Pickobulus","镐爆","Maniac Miner","疯狂矿工","Gemstone Infusion","宝石灌注","Sheer Force","蛮力").entrySet()) {
            var raw=Component.literal("§6"+entry.getKey()+" §ais now available!");
            var out=SystemTranslationEngine.translate(raw,"chat",manager);
            assertEquals(entry.getValue()+"现已可以使用！",out.getString());
            StyledLoreTest.color(out,entry.getValue(),ChatFormatting.GOLD);
            StyledLoreTest.color(out,"现已可以使用！",ChatFormatting.GREEN);
        }
    }
}
