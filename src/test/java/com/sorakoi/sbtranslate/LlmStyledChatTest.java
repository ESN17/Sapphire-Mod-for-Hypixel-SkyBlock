package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.llm.*;
import com.sorakoi.sbtranslate.client.ChatOriginalHover;
import com.sorakoi.sbtranslate.translation.TranslationManager;
import com.sorakoi.sbtranslate.translation.TextTranslationEngine;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LlmStyledChatTest {
    static LlmStyledText.Request prepare(Component original) throws Exception {
        var config=LlmTranslationTest.config();config.players=true;config.system=true;
        var selected=LlmText.select(TranslationManager.plain(original.getString()),false,config);
        assertNotNull(selected);
        var prepared=LlmStyledText.prepare(original,selected,List.of("GreatCatch","lDuck"));
        assertNotNull(prepared);return prepared;
    }
    @Test void skyblockLevelBadgeRankNameAndActionsStayOutsideTranslation() throws Exception {
        var click=new ClickEvent.RunCommand("/viewprofile GreatCatch");
        var original=Component.literal("§7[§d567§7] §6α §b[MVP§c++§b] GreatCatch§f: I need sbxp quickly")
            .withStyle(s->s.withClickEvent(click).withInsertion("GreatCatch"));
        var copy=original.copy();var request=prepare(original);
        assertFalse(request.text().contains("GreatCatch"));assertFalse(request.text().contains("567"));assertFalse(request.text().contains("α"));
        var result=request.restore("<t0>我需要快速获得空岛经验</t0>");assertNotNull(result);
        assertEquals("[567] α [MVP++] GreatCatch: 我需要快速获得空岛经验",result.getString());
        StyledLoreTest.color(result,"567",ChatFormatting.LIGHT_PURPLE);
        StyledLoreTest.color(result,"α",ChatFormatting.GOLD);
        StyledLoreTest.color(result,"GreatCatch",ChatFormatting.AQUA);
        StyledLoreTest.color(result,"++",ChatFormatting.RED);
        assertEquals(click,StyledLoreTest.styleAt(result,"GreatCatch").getClickEvent());
        assertEquals("GreatCatch",StyledLoreTest.styleAt(result,"GreatCatch").getInsertion());
        assertEquals(copy,original);
    }
    @Test void playerOptionsRemainSeparatedClickableAndColoredAfterOriginalHover() throws Exception {
        var report=new ClickEvent.RunCommand("/report lDuck");var block=new ClickEvent.SuggestCommand("/ignore add lDuck");
        var nativeHover=new HoverEvent.ShowText(Component.literal("Report this player"));
        var original=Component.empty().append(Component.literal("lDuck ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("[Report Player]").withStyle(s->s.withColor(ChatFormatting.RED).withClickEvent(report).withHoverEvent(nativeHover)))
            .append(Component.literal(" [Block Player]").withStyle(s->s.withColor(ChatFormatting.YELLOW).withClickEvent(block)));
        var request=prepare(original);var response=request.text().replace("Report Player","举报玩家").replace("Block Player","屏蔽玩家");
        var translated=request.restore(response);assertNotNull(translated);
        assertEquals(nativeHover,StyledLoreTest.styleAt(translated,"举报玩家").getHoverEvent());
        var displayed=ChatOriginalHover.attach(translated,original);
        assertEquals(report,StyledLoreTest.styleAt(displayed,"举报玩家").getClickEvent());
        assertEquals(block,StyledLoreTest.styleAt(displayed,"屏蔽玩家").getClickEvent());
        assertNull(StyledLoreTest.styleAt(displayed,"lDuck").getClickEvent());
        StyledLoreTest.color(displayed,"举报玩家",ChatFormatting.RED);StyledLoreTest.color(displayed,"屏蔽玩家",ChatFormatting.YELLOW);
        assertEquals(original.getString(),ChatOriginalHover.original(StyledLoreTest.styleAt(displayed,"举报玩家")));
        assertTrue(((HoverEvent.ShowText)StyledLoreTest.styleAt(displayed,"举报玩家").getHoverEvent()).value().getString().startsWith("Report this player"));
    }
    @Test void islandNotificationKeepsGreenTextAquaNameAndWhiteCoordinates() throws Exception {
        var original=Component.literal("§aA §bStranger §aappears on your island at §f8, 100, 41§a! You should go talk to them.");
        var request=prepare(original);
        var translated=request.restore(request.text().replace("A ","一位").replace("Stranger ","陌生人")
            .replace("appears on your island at ","出现在你的岛上，坐标为 ").replace("! You should go talk to them.","！你应该去和他交谈。"));
        assertNotNull(translated);
        StyledLoreTest.color(translated,"陌生人",ChatFormatting.AQUA);
        StyledLoreTest.color(translated,"出现在你的岛上",ChatFormatting.GREEN);
        StyledLoreTest.color(translated,"8, 100, 41",ChatFormatting.WHITE);
        assertFalse(translated.getString().contains("⟦"));assertFalse(translated.getString().contains("<t"));
    }
    @Test void movingWholeSegmentsKeepsTheirOwnActionsAndBadOutputIsRejected() throws Exception {
        var raw=Component.literal("Hello ").withStyle(ChatFormatting.GREEN).append(Component.literal("world").withStyle(ChatFormatting.AQUA));
        var request=prepare(raw);var reordered=request.restore("<t1>世界</t1><t0>你好</t0>");
        assertNotNull(reordered);StyledLoreTest.color(reordered,"世界",ChatFormatting.AQUA);
        for(String invalid:List.of("你好世界","<t0>你好</t0>","<t0>你好</t0><t0>重复</t0>",
            "<t0>你好</t0><t99>世界</t99>",request.text()+" extra",request.text().replace("world","§cworld")))assertNull(request.restore(invalid),invalid);
    }
    @Test void protectionUsesUniqueMarkersAcrossStyleBoundaries() throws Exception {
        var request=prepare(Component.literal("§aEarn 10 coins and §b20 bits"));
        assertTrue(request.text().contains("⟦S0⟧"));assertTrue(request.text().contains("⟦S1⟧"));
        assertNull(request.restore(request.text().replace("⟦S0⟧","⟦S1⟧")));
        assertNull(request.restore(request.text().replace("⟦S0⟧","999")));
        assertNull(request.restore(request.text().replace("⟦S0⟧","")));
        assertNotNull(request.restore(request.text()));
    }
    @Test void aChineseArticleMayBeEmptyButClickableButtonsMustNeverDisappear() throws Exception {
        var request=prepare(Component.literal("§aA §bStranger appears!"));
        assertNotNull(request.restore("<t0></t0><t1>陌生人出现了！</t1>"));
        var clicked=prepare(Component.empty().append(Component.literal("A ").withStyle(s->s.withClickEvent(new ClickEvent.RunCommand("/test"))))
            .append(Component.literal("Stranger appears!")));
        assertNull(clicked.restore("<t0></t0><t1>陌生人出现了！</t1>"));
        var normal=prepare(Component.literal("§aHello §bworld"));
        assertNull(normal.restore("<t0></t0><t1>世界</t1>"));
    }
    @Test void badgePrefixesArePlayerChatEvenWithoutSignedPlayerSource() throws Exception {
        var c=LlmTranslationTest.config();c.system=true;
        for(String prefix:List.of("[410] α [MVP++] ESN17_OwO: ","[458] ϕ [MVP++] lDuck: ","[21] Player123: ","[410] [MVP+] ESN17_OwO α: ")) {
            assertNull(LlmText.select(prefix+"hello",false,c));c.players=true;
            var selected=LlmText.select(prefix+"hello",false,c);assertEquals(LlmText.Kind.PLAYER,selected.kind());assertEquals(prefix,selected.prefix());c.players=false;
        }
    }
    @Test void allProvidersReceiveTheStyleProtocolWithoutChangingUserPrompt() throws Exception {
        var c=LlmTranslationTest.config();String before=c.prompt;
        for(var provider:LlmConfig.Provider.values()) {
            c.provider=provider;c.keys.put(provider,"test-only");
            var body=LlmTranslationTest.body(LlmApi.request(c,"<t0>hello</t0>"));
            assertTrue(body.toString().contains("点击区域"));assertEquals(before,c.prompt);
        }
    }
    @Test void allClickTargetsSurviveBothLocalAndApiTranslationUnchanged() throws Exception {
        for(ClickEvent action:List.of(new ClickEvent.RunCommand("/viewauction 1234-original-uuid"),
                new ClickEvent.SuggestCommand("/msg ExampleUser keep these arguments "),
                new ClickEvent.OpenUrl(java.net.URI.create("https://hypixel.net/threads/example?original=1")),
                new ClickEvent.CopyToClipboard("original clipboard payload"))) {
            var original=Component.literal("Click here").withStyle(s->s.withColor(ChatFormatting.GREEN).withClickEvent(action)
                .withInsertion("original insertion").withHoverEvent(new HoverEvent.ShowText(Component.literal("Original hint"))));
            var before=original.copy();
            var local=TextTranslationEngine.apply(original,new TranslationManager.Template("Click here","点击这里").apply(original.getString()));
            var request=prepare(original);var online=request.restore("<t0>点击这里</t0>");assertNotNull(online);
            for(var translated:List.of(local,online)) {
                var displayed=ChatOriginalHover.attach(translated,original);
                var style=StyledLoreTest.styleAt(displayed,"点击这里");
                assertSame(action,style.getClickEvent());assertEquals("original insertion",style.getInsertion());
                StyledLoreTest.color(displayed,"点击这里",ChatFormatting.GREEN);
            }
            assertEquals(before,original);
            assertNull(request.restore("<t0>点击这里 /invented-command</t0>"));
        }
    }
}
