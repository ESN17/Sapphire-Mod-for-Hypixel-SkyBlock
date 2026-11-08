package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import com.sorakoi.sbtranslate.item.ItemTranslationService;
import com.sorakoi.sbtranslate.rank.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VisitorSaleGardenTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;}
    static Component chat(Component source){return SystemTranslationEngine.translate(source,"chat",manager);}
    @Test void visitorKeepsPlayerClickAndUsesNewRankPalette() {
        var click=new ClickEvent.RunCommand("/visit ajjaya");
        var source=Component.literal("[SkyBlock] ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal("[VIP] ajjaya").withStyle(s->s.withColor(ChatFormatting.GREEN).withClickEvent(click)))
            .append(Component.literal(" is visiting ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("Your Island!").withStyle(ChatFormatting.GREEN));
        var before=source.copy();var translated=chat(source);
        assertEquals("[空岛] [VIP] ajjaya 正在访问你的岛屿！",translated.getString());
        StyledLoreTest.color(translated,"正在访问",ChatFormatting.YELLOW);
        StyledLoreTest.color(translated,"你的岛屿",ChatFormatting.GREEN);
        var roster=new RankRoster(Map.of("ajjaya",new RankRoster.Entry("ajjaya","SAPPHIRE",0x0000aa,0xffffff)));
        var displayed=RankDisplay.chat(translated,roster);
        assertEquals("[空岛] [SAPPHIRE] ajjaya 正在访问你的岛屿！",displayed.getString());
        StyledLoreTest.color(displayed,"SAPPHIRE",ChatFormatting.WHITE);StyledLoreTest.color(displayed,"ajjaya",ChatFormatting.BLUE);
        displayed.visit((s,t)->{if(t.equals("ajjaya"))assertEquals(click,s.getClickEvent());return Optional.empty();},Style.EMPTY);
        assertEquals(before,source);
        var playerChat=Component.literal("Other: [SkyBlock] ajjaya is visiting Your Island!");assertSame(playerChat,chat(playerChat));
    }
    @Test void fireSaleKeepsUnlistedProductAndVariableStock() {
        for(String count:List.of("1","2,420","999,999")) {
            var source=Component.literal("§c♨ §6Prancin' Pumpkin Rune §e("+count+" left)");var before=source.copy();var result=chat(source);
            assertEquals("♨ Prancin' Pumpkin Rune（剩余 "+count+" 件）",result.getString());
            StyledLoreTest.color(result,"Prancin' Pumpkin Rune",ChatFormatting.GOLD);
            StyledLoreTest.color(result,count,ChatFormatting.YELLOW);assertEquals(before,source);
            assertTrue(com.sorakoi.sbtranslate.client.OnlineChatTranslation.usesLocalDictionary(source,manager));
        }
        assertNull(manager.knownItemName("Brand New Unlisted Rune"));
        assertTrue(ItemTranslationService.preserveNewCosmetic("RUNE","BRAND_NEW_RUNE;1",false,false));
        assertTrue(ItemTranslationService.preserveNewCosmetic("PET_SKIN_NEW","PET_SKIN_NEW",false,false));
        assertFalse(ItemTranslationService.preserveNewCosmetic("RUNE","KNOWN_RUNE;1",true,true));
        assertFalse(ItemTranslationService.preserveNewCosmetic("HYPERION","HYPERION",true,true));
    }
    @Test void fireSaleCountdownAndWarpKeepClickEvent() {
        assertEquals("⚑ 限时特卖 ⚑",chat(Component.literal("⚑ FIRE SALE ⚑")).getString());
        assertEquals("♨ 限时出售商品！",chat(Component.literal("♨ Selling an item for a limited time!")).getString());
        var click=new ClickEvent.RunCommand("/warp community");
        var source=Component.literal("♨ ").withStyle(ChatFormatting.RED)
            .append(Component.literal("[WARP]").withStyle(s->s.withColor(ChatFormatting.GREEN).withClickEvent(click)))
            .append(Component.literal(" To ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("Elizabeth").withStyle(ChatFormatting.LIGHT_PURPLE))
            .append(Component.literal(" in the next ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("2d 9h 13m").withStyle(ChatFormatting.RED))
            .append(Component.literal(" to grab yours!").withStyle(ChatFormatting.YELLOW));
        var result=chat(source);
        assertEquals("♨ [传送] 前往 Elizabeth 购买，剩余时间：2天 9小时 13分！",result.getString());
        result.visit((s,t)->{if(t.contains("传送"))assertEquals(click,s.getClickEvent());return Optional.empty();},Style.EMPTY);
        StyledLoreTest.color(result,"Elizabeth",ChatFormatting.LIGHT_PURPLE);
        StyledLoreTest.color(result,"13",ChatFormatting.RED);
    }
    @Test void gardenFortuneIsVariableAndNeverBecomesMagicFind() {
        for(String amount:List.of("0","1","87.75","123.456","1,234.5"))for(String icon:List.of("☘","\ue019")) {
            var source=Component.literal("§6RARE DROP! §aBotroot §e(+"+amount+icon+")");var before=source.copy();var result=chat(source);
            assertTrue(result.getString().contains("稀有掉落"),result.getString());
            assertTrue(result.getString().contains("+"+amount+icon),result.getString());
            assertFalse(result.getString().contains("Magic Find"));assertFalse(result.getString().contains("稀有物品概率加成"));
            StyledLoreTest.color(result,"+"+amount+icon,ChatFormatting.YELLOW);assertEquals(before,source);
        }
    }
    @Test void npcSalesUseTheItemCatalogAndPreserveQuantityPriceAndStyles() {
        for(var item:Map.of("Feastfungus","FEASTFUNGUS","Pretty Fly Vinyl","VINYL_PRETTY_FLY").entrySet())
            for(String count:List.of("1","64","1,234"))for(String price:List.of("50,000","1,234.5","0.1")) {
                String expectedName=manager.item(item.getValue()).name();assertNotNull(expectedName);
                var source=Component.literal("§aYou sold "+item.getKey()+" §7x"+count+" §afor §6"+price+" Coins§a!");
                var before=source.copy();var result=chat(source);
                var diagnostic=new ArrayList<String>();
                TextTranslationEngine.apply(source,SystemTranslationEngine.match(TranslationManager.plain(source.getString()),"chat",manager),diagnostic::add);
                assertEquals("你卖出了 "+count+" 个 "+expectedName+",获得 "+price+" 硬币!",result.getString(),diagnostic.toString());
                StyledLoreTest.color(result,expectedName,ChatFormatting.GREEN);
                StyledLoreTest.color(result,count+" 个",ChatFormatting.GRAY);
                StyledLoreTest.color(result,price,ChatFormatting.GOLD);assertEquals(before,source);
            }
        assertEquals("你卖出了 1 个 Brand New Unlisted Item,获得 5 硬币!",chat(Component.literal("You sold Brand New Unlisted Item x1 for 5 Coins!")).getString());
    }
    @Test void versionNoticesKeepChangingVersionsAndClickableLink() {
        for(String version:List.of("26.3","26.1.2","27.0-pre1","26w12a")) {
            var source=Component.literal("We recommend updating to the latest version of Minecraft, "+version+", as soon as possible.").withStyle(ChatFormatting.YELLOW);
            assertEquals("建议尽快将 Minecraft 更新至最新版本 "+version+"。",chat(source).getString());
            StyledLoreTest.color(chat(source),version,ChatFormatting.YELLOW);
        }
        assertEquals("你当前使用的 Minecraft 版本即将停止支持最新游戏内容。",chat(Component.literal("Support for your Minecraft version on latest content will be ending in the near future.")).getString());
        var click=new ClickEvent.OpenUrl(java.net.URI.create("https://hypixel.net/"));
        var link=Component.literal("Click here to learn more!").withStyle(s->s.withColor(ChatFormatting.AQUA).withClickEvent(click));
        var before=link.copy();var result=chat(link);assertEquals("点击此处了解详情！",result.getString());
        result.visit((s,t)->{if(!t.isEmpty())assertEquals(click,s.getClickEvent());return Optional.empty();},Style.EMPTY);
        assertEquals(before,link);
    }
}
