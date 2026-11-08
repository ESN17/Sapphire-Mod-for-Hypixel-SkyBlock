package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.client.EntityNameDisplay;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EntityNameTranslationTest {
    static TranslationManager manager;
    @TempDir Path root;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;EntityNameDisplay.clear();}
    @BeforeEach void clear(){EntityNameDisplay.clear();}
    private static Component name(String text){return EntityNameDisplay.translate(Component.literal(text),manager);}
    @Test void armorStandNamesKeepIconsHealthLevelAndStyle() {
        var raw=Component.literal("§8[§7Lv33§8] §cCrypt Ghoul §a1,234§f/§a2,000§c❤");var snapshot=raw.copy();
        var out=EntityNameDisplay.translate(raw,manager);
        assertEquals("[Lv33] 地穴食尸鬼 1,234/2,000❤",out.getString());
        StyledLoreTest.color(out,"地穴食尸鬼",ChatFormatting.RED);StyledLoreTest.color(out,"1,234",ChatFormatting.GREEN);
        StyledLoreTest.color(out,"/",ChatFormatting.WHITE);StyledLoreTest.color(out,"Lv33",ChatFormatting.GRAY);assertEquals(snapshot,raw);
        assertEquals("✯ [Lv597] 颚骨领主 100M❤",name("✯ [Lv597] Lord Jawbus 100M❤").getString());
        assertEquals("[Lv100] 腐化的 海行者 5000/5000❤",name("[Lv100] Corrupted Sea Walker 5000/5000❤").getString());
    }
    @Test void rolesTranslateButPlayerNamesNpcNamesAndProseStayOriginal() {
        assertEquals("点击交互",name("CLICK").getString());assertEquals("拍卖管理员",name("Auction Master").getString());
        for(String text:List.of("SamplePlayer","[MVP+] SamplePlayer","Jerry","Bonzo","Necron","I like the Sea Walker pet","Unknown Monster 20❤")) {
            var raw=Component.literal(text);assertSame(raw,EntityNameDisplay.translate(raw,manager),text);
        }
        assertEquals("Jerry\n点击交互",name("Jerry\nCLICK").getString());
    }
    @Test void everyRegisteredTranslatedNameMatchesAsAWholeName() {
        for(var entry:manager.scope("entity-names").entrySet()) {
            assertEquals(entry.getValue(),name(entry.getKey()).getString(),entry.getKey());
        }
    }
    @Test void creatureMenusReuseNamesWithoutTouchingOrdinaryMenus() {
        var menu=GuiTranslationEngine.menu("Sea Creature Guide",manager);assertNotNull(menu);
        assertEquals("海怪图鉴",GuiTranslationEngine.title(Component.literal("Sea Creature Guide"),manager).getString());
        assertEquals("深渊矿工",GuiTranslationEngine.creatureName(Component.literal("Abyssal Miner"),menu,manager).getString());
        assertEquals("族群：海行者",GuiTranslationEngine.detailLine(Component.literal("Family: Sea Walker"),menu,manager).getString());
        assertEquals("地穴食尸鬼",GuiTranslationEngine.title(Component.literal("Crypt Ghoul"),manager).getString());
        var name=Component.literal("Abyssal Miner");assertSame(name,GuiTranslationEngine.creatureName(name,null,manager));
    }
    @Test void cacheHitsDoNotRepeatTranslationOrLeakMutableComponents() {
        var raw=Component.literal("Crypt Ghoul").withStyle(ChatFormatting.RED);
        var first=EntityNameDisplay.translate(raw,manager);long misses=EntityNameDisplay.CACHE.misses(),hits=EntityNameDisplay.CACHE.hits();
        for(int i=0;i<500;i++)assertEquals(first,EntityNameDisplay.translate(raw,manager));
        assertEquals(misses,EntityNameDisplay.CACHE.misses());assertEquals(hits+500,EntityNameDisplay.CACHE.hits());
        ((MutableComponent)first).append(" changed");
        assertEquals("地穴食尸鬼",EntityNameDisplay.translate(raw,manager).getString());
        var blue=EntityNameDisplay.translate(Component.literal("Crypt Ghoul").withStyle(ChatFormatting.BLUE),manager);
        StyledLoreTest.color(blue,"地穴食尸鬼",ChatFormatting.BLUE);
        for(int i=0;i<700;i++)name("[Lv33] Crypt Ghoul "+i+"/2,000❤");assertTrue(EntityNameDisplay.CACHE.size()<=512);
    }
    @Test void reloadInvalidatesNameplateAndNegativeCaches() throws Exception {
        Files.createDirectories(root.resolve("translations"));
        Files.writeString(root.resolve("translations/entity-names.json"),"{\"Crypt Ghoul\":\"地穴食尸鬼\"}");
        var local=new TranslationManager();local.load(root);
        assertEquals("地穴食尸鬼",EntityNameDisplay.translate(Component.literal("Crypt Ghoul"),local).getString());
        var newMob=Component.literal("New Mob");assertSame(newMob,EntityNameDisplay.translate(newMob,local));
        Files.writeString(root.resolve("translations/entity-names.json"),"{\"Crypt Ghoul\":\"自定义名称\",\"New Mob\":\"新怪物\"}");local.load(root);
        assertEquals("自定义名称",EntityNameDisplay.translate(Component.literal("Crypt Ghoul"),local).getString());
        assertEquals("新怪物",EntityNameDisplay.translate(newMob,local).getString());
    }
}
