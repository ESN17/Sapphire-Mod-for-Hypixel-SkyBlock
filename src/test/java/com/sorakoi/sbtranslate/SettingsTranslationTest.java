package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SettingsTranslationTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {
        manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));
    }
    static List<Component> lore(String title,List<Component> source) {
        var menu=GuiTranslationEngine.menu(title,manager);assertNotNull(menu,title);
        return LoreTranslationEngine.translate(source,manager,null,(s,l)->List.of(s),false,false,false,
            menu.paragraphs(),line->GuiTranslationEngine.detailLine(line,menu,manager),
            line->CommunityTranslationEngine.translate(line,"tooltip",manager),
            line->manager.community().candidates("tooltip",TranslationManager.plain(line.getString()))).lines();
    }

    @Test void screenshotTitlesAndFishingToggleAreRecognized() {
        assertEquals("钓鱼设置",GuiTranslationEngine.title(Component.literal("Fishing Settings"),manager).getString());
        var menu=GuiTranslationEngine.menu("Fishing Settings",manager);
        var name=Component.literal("Fishing Timer").withStyle(ChatFormatting.GREEN);
        var translated=GuiTranslationEngine.button(name,menu);
        assertEquals("钓鱼计时器",translated.getString());StyledLoreTest.color(translated,"钓鱼计时器",ChatFormatting.GREEN);
        for(String action:List.of("enable","disable")) {
            var source=Component.literal("Click to "+action+"!").withStyle(ChatFormatting.YELLOW);
            var result=lore("Fishing Settings",List.of(source)).getFirst();
            assertEquals(action.equals("enable")?"点击启用！":"点击以禁用！",result.getString());
            StyledLoreTest.color(result,action.equals("enable")?"启用":"禁用",ChatFormatting.YELLOW);
        }
        assertEquals("音效设置",GuiTranslationEngine.button(Component.literal("Sounds Settings"),GuiTranslationEngine.menu("Settings",manager)).getString());
    }

    @Test void everySoundToggleCombinationKeepsSymbolsColorsAndClickActions() {
        String[] english={"Play Music","Ability Cooldown Sounds","Rare Drop Sounds","Menu Sounds","Ferocity Sounds"};
        String[] chinese={"播放音乐","技能冷却音效","稀有掉落音效","菜单音效","残暴值音效"};
        var click=new ClickEvent.SuggestCommand("/settings");
        for(int mask=0;mask<32;mask++) {
            var source=new ArrayList<Component>();source.add(Component.literal("Includes:"));
            for(int i=0;i<english.length;i++)source.add(Component.literal((mask&(1<<i))==0?"✖ ":"✔ ")
                .withStyle((mask&(1<<i))==0?ChatFormatting.RED:ChatFormatting.GREEN)
                .append(Component.literal(english[i]).withStyle(s->s.withColor(ChatFormatting.GRAY).withClickEvent(click))));
            var before=source.stream().map(Component::copy).toList();
            var result=lore("Settings",source);assertEquals(6,result.size());
            for(int i=0;i<english.length;i++) {
                boolean enabled=(mask&(1<<i))!=0;var line=result.get(i+1);
                assertEquals((enabled?"✔ ":"✖ ")+chinese[i],line.getString());
                StyledLoreTest.color(line,enabled?"✔":"✖",enabled?ChatFormatting.GREEN:ChatFormatting.RED);
                assertEquals(click,StyledLoreTest.styleAt(line,chinese[i]).getClickEvent());
            }
            assertEquals(before,source);
        }
    }

    @Test void fishingSummaryWorksWithMixedStatesAndDescriptionsAcrossLines() {
        var lines=List.<Component>of(Component.literal("Includes:"),Component.literal("§c✖ §7Sea Creature Chat"),
            Component.literal("§a✔ §7Fishing Status Holograms"),Component.literal("§c✖ §7Fishing Timer"),Component.literal("§a✔ §7Fishing Sound"));
        for(var line:lore("Settings",lines))assertFalse(line.getString().matches(".*[A-Za-z].*"),line.getString());
        var explanation=lore("Fishing Settings",List.of(Component.literal("Tweak settings related to"),Component.literal("fishing.")));
        assertEquals("调整与钓鱼相关的设置。",String.join("",explanation.stream().map(Component::getString).toList()));
    }

    @Test void relatedSubmenusWorkWithoutOpeningUnrelatedContainers() {
        for(String title:List.of("Sounds Settings","Sound Settings","Personal Settings","Comms Settings","User Interface Settings", "Chat Feedback Settings","API Settings","Island Settings","Garden Settings",
            "Settings - Personal","Settings - Sounds","Settings - User Interface","Settings - Chat Feedback","Settings - Fishing Settings","Settings - Comms","Settings - API")) {
            var source=Component.literal(title);assertNotEquals(title,GuiTranslationEngine.title(source,manager).getString(),title);
            assertEquals("点击以禁用！",lore(title,List.of(Component.literal("Click to disable!"))).getFirst().getString());
        }
        assertNull(GuiTranslationEngine.menu("Player Custom Settings",manager));
        var untouched=Component.literal("✔ Play Music");
        assertSame(untouched,GuiTranslationEngine.button(untouched,null));
    }
}
