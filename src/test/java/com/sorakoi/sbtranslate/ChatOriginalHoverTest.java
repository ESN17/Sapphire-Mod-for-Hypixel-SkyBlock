package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.client.ChatOriginalHover;
import com.sorakoi.sbtranslate.client.SapphireNotifications;
import com.sorakoi.sbtranslate.translation.TextTranslationEngine;
import com.sorakoi.sbtranslate.translation.TranslationManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class ChatOriginalHoverTest {
    @Test void repeatedLinesTrackTheActualDisplayedHoverForCopying() {
        var source = Component.literal("Dungeon reward");
        var first = StyledLoreTest.styleAt(ChatOriginalHover.attach(Component.literal("地牢奖励"), source), "奖励");
        var second = StyledLoreTest.styleAt(ChatOriginalHover.attach(Component.literal("地牢奖励"), source), "奖励");
        var hover = assertInstanceOf(HoverEvent.ShowText.class, first.getHoverEvent());
        assertEquals(hover, second.getHoverEvent());
        assertNotSame(hover, second.getHoverEvent());
        assertEquals(source.getString(), ChatOriginalHover.original(first));
        assertEquals(source.getString(), ChatOriginalHover.original(second));
        var unregistered = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(hover.value()));
        assertNull(ChatOriginalHover.original(unregistered));
    }
    private static String tip(Component message, String word) {
        return assertInstanceOf(HoverEvent.ShowText.class, StyledLoreTest.styleAt(message, word).getHoverEvent()).value().getString();
    }

    @Test void unchangedMessagesAndFormattingOnlyChangesHaveNoTooltip() {
        var original = Component.literal("Player: hello");
        assertSame(original, ChatOriginalHover.attach(original, original));
        var recolored = original.copy().withStyle(ChatFormatting.RED);
        assertSame(recolored, ChatOriginalHover.attach(recolored, original));
        var legacy = Component.literal("§aPlayer: hello");
        assertSame(legacy, ChatOriginalHover.attach(legacy, original));
    }

    @Test void translatedDynamicMessagesKeepClickColorAndOriginalNumbers() {
        var click = new ClickEvent.RunCommand("/viewauction example");
        var source = Component.literal("Found ").withStyle(ChatFormatting.GREEN)
            .append(Component.literal("23").withStyle(s -> s.withColor(ChatFormatting.GOLD).withBold(true).withClickEvent(click)))
            .append(" items!");
        var originalCopy = source.copy();
        var translated = TextTranslationEngine.apply(source,
            new TranslationManager.Template("Found {count} items!", "找到了 {count} 个物品！").apply(source.getString()));
        var translatedCopy = translated.copy();
        var result = ChatOriginalHover.attach(translated, source);
        assertEquals(translated.getString(), result.getString());
        assertEquals("英文原文\nFound 23 items!", tip(result, "找到"));
        assertEquals(tip(result, "找到"), tip(result, "23"));
        var numberStyle = StyledLoreTest.styleAt(result, "23");
        assertEquals(click, numberStyle.getClickEvent());
        assertTrue(numberStyle.isBold());
        StyledLoreTest.color(result, "23", ChatFormatting.GOLD);
        assertEquals(originalCopy, source);
        assertEquals(translatedCopy, translated);
    }

    @Test void inheritedAndChildTextHoversAreCombinedPerSpan() {
        var source = Component.literal("Talk to Maddox!");
        var translated = Component.literal("与 ").withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal("父级提示"))))
            .append(Component.literal("Maddox").withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal("角色说明")))))
            .append(" 对话！");
        var result = ChatOriginalHover.attach(translated, source);
        assertEquals("父级提示\n\n英文原文\nTalk to Maddox!", tip(result, "与"));
        assertEquals("角色说明\n\n英文原文\nTalk to Maddox!", tip(result, "Maddox"));
        assertTrue(tip(result, "对话").startsWith("父级提示"));
    }

    @Test void nativeItemAndEntityActionsRemainAvailableWithOriginalEntry() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var item = new HoverEvent.ShowItem(new net.minecraft.world.item.ItemStackTemplate(net.minecraft.world.item.Items.DIAMOND));
        var entity = new HoverEvent.ShowEntity(new HoverEvent.EntityTooltipInfo(
            net.minecraft.world.entity.EntityTypes.ZOMBIE, new java.util.UUID(0, 1), Component.literal("Zombie")));
        for (HoverEvent nativeHover : new HoverEvent[]{item, entity}) {
            var click = new ClickEvent.SuggestCommand("/view item");
            var translated = Component.literal("物品详情").withStyle(s -> s.withHoverEvent(nativeHover).withClickEvent(click));
            var result = ChatOriginalHover.attach(translated, Component.literal("Item details"));
            assertSame(nativeHover, StyledLoreTest.styleAt(result, "物品").getHoverEvent());
            assertEquals(click, StyledLoreTest.styleAt(result, "物品").getClickEvent());
            assertEquals("物品详情 [原文]", result.getString());
            assertEquals("英文原文\nItem details", tip(result, "[原文]"));
            assertNull(StyledLoreTest.styleAt(result, "[原文]").getClickEvent());
        }
    }

    @Test void snapshotDoesNotRetainMutableSiblingsOrNestedHoverChains() {
        var child = Component.literal("42").withStyle(s -> s.withColor(ChatFormatting.RED)
            .withHoverEvent(new HoverEvent.ShowText(Component.literal("original hover"))));
        var source = Component.literal("Amount: ").append(child).append("\nSecond line");
        var result = ChatOriginalHover.attach(Component.literal("数量：42\n第二行"), source);
        child.append(" changed");
        assertEquals("英文原文\nAmount: 42\nSecond line", tip(result, "数量"));
        var tooltip = ((HoverEvent.ShowText)StyledLoreTest.styleAt(result, "数量").getHoverEvent()).value();
        StyledLoreTest.color(tooltip, "42", ChatFormatting.RED);
        tooltip.visit((style, text) -> { assertNull(style.getHoverEvent()); assertNull(style.getClickEvent()); return Optional.empty(); }, Style.EMPTY);
    }

    @Test void onlineNoticePrefixAndBodyBothExposeOriginalWithoutChangingBrandColors() {
        var message = SapphireNotifications.message(SapphireNotifications.Tone.INFO,
            Component.literal("[译] 你好！").withStyle(ChatFormatting.WHITE));
        var result = ChatOriginalHover.attach(message, Component.literal("Player_123: Hello!"));
        assertEquals(message.getString(), result.getString());
        assertEquals(tip(result, "Sapphire"), tip(result, "你好"));
        assertTrue(tip(result, "你好").contains("Player_123: Hello!"));
        StyledLoreTest.color(result, "Sapphire", ChatFormatting.BLUE);
    }
    @Test void onlyOurHoverExposesCopyableOriginal(){
        var click=new ClickEvent.SuggestCommand("/original-action");
        var result=ChatOriginalHover.attach(Component.literal("译文").withStyle(s->s.withClickEvent(click)),Component.literal("§aOriginal 123"));
        var style=StyledLoreTest.styleAt(result,"译文");
        assertEquals("Original 123",ChatOriginalHover.original(style));assertEquals(click,style.getClickEvent());
        assertNull(ChatOriginalHover.original(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal("ordinary tip")))));
    }
}
