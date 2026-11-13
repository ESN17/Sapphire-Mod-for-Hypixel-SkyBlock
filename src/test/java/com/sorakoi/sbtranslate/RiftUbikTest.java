package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RiftUbikTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;}
    static Component translated(String raw) {
        var source=Component.literal(raw);var copy=source.copy();
        var out=SystemTranslationEngine.translate(source,"chat",manager);
        if(out==source)TextTranslationEngine.apply(TextTranslationEngine.resolvedDisplay(source),SystemTranslationEngine.match(TranslationManager.plain(raw),"chat",manager),System.out::println);
        assertEquals(copy,source);assertNotSame(source,out,raw);return out;
    }
    @Test void allRoundsBothChoicesAndFinalRetainAmountsAndColors() {
        for(int round:List.of(1,5,9,12))for(String choice:List.of("STEAL","SPLIT"))
            for(String amount:List.of("0","1,000","3,000","5,000","123,456.75"))
                for(String currency:List.of("Motes","裂隙尘埃"))for(boolean last:List.of(false,true)) {
                    String color=choice.equals("STEAL")?"§c":"§a",label=choice.equals("STEAL")?"独吞":"平分";
                    var out=translated("§6§lROUND "+round+(last?" (FINAL)":"")+": §eYou chose "+color+"§l"+choice+" §eand gained §5"+amount+" "+currency+"§e!");
                    assertEquals("第 "+round+" 回合"+(last?"（最终回合）":"")+"：你选择了"+label+"，获得 "+amount+" 裂隙尘埃！",out.getString());
                    StyledLoreTest.color(out,label,choice.equals("STEAL")?ChatFormatting.RED:ChatFormatting.GREEN);
                    assertTrue(StyledLoreTest.styleAt(out,label).isBold());StyledLoreTest.color(out,amount,ChatFormatting.DARK_PURPLE);
                }
    }
    @Test void capturedFinalMarkerHasPlainParenthesesAndBoldWordAndColon(){
        for(String choice:List.of("STEAL","SPLIT")){
            var line="§6§lROUND 7 §6(§6§lFINAL§6)§6§l: §eYou chose "+(choice.equals("STEAL")?"§c":"§a")+"§l"+choice+" §eand gained §55,000 Motes§e!";
            var out=translated(line);
            String label=choice.equals("STEAL")?"独吞":"平分";
            assertEquals("第 7 回合（最终回合）：你选择了"+label+"，获得 5,000 裂隙尘埃！",out.getString());
            assertTrue(StyledLoreTest.styleAt(out,"最终回合").isBold());
            assertFalse(StyledLoreTest.styleAt(out,"（").isBold());
        }
    }
    @Test void bothPlayersTotalsAreDynamic() {
        for(String subject:List.of("You","Your opponent"))for(String n:List.of("0","25,000","45,000","1,234,567")) {
            var out=translated("§e"+subject+" earned §5"+n+" Motes §ein this match!");
            assertEquals((subject.equals("You")?"你":"你的对手")+"在本场对局中获得了 "+n+" 裂隙尘埃！",out.getString());
            StyledLoreTest.color(out,n,ChatFormatting.DARK_PURPLE);
        }
    }
    @Test void riftTimersTeleportAndOrbHaveNoFixedValues() {
        for(String time:List.of("1s","21m41s","44m48s","1h 6m 29s"))for(String icon:List.of("","ф","⏳","⌛")) {
            var out=translated("§7You have §a"+time+icon+" §7left before the rift collapses!");
            assertTrue(out.getString().contains(time+icon));StyledLoreTest.color(out,time,ChatFormatting.GREEN);
            out=translated("§eYou used §a"+time+" "+icon+" Rift Time §eto teleport to §fMountaintop§e!");
            assertTrue(out.getString().contains("山巅"),out.getString());assertTrue(out.getString().contains(time));
            StyledLoreTest.color(out,"山巅",ChatFormatting.WHITE);StyledLoreTest.color(out,"裂隙时间",ChatFormatting.GREEN);
            var cooldown=translated("§b§lSPLIT! §cYou need to wait "+time+" before you can play again.");
            assertTrue(cooldown.getString().contains(time));StyledLoreTest.color(cooldown,"平分或独吞！",ChatFormatting.AQUA);
        }
        for(String currency:List.of("Motes","裂隙尘埃"))for(String amount:List.of("25","1,234"))for(String seconds:List.of("2","15.5")) {
            var out=translated("§5§lORB! §dPicked up §5+"+amount+" "+currency+"§d, recovered §a+"+seconds+" Rift Time§d!");
            assertTrue(out.getString().contains("+"+amount+" 裂隙尘埃"));assertTrue(out.getString().contains("+"+seconds+" 裂隙时间"));
            StyledLoreTest.color(out,"光球！",ChatFormatting.DARK_PURPLE);StyledLoreTest.color(out,"裂隙时间",ChatFormatting.GREEN);
        }
    }
    @Test void riftTranslationKeepsInteractiveEvents() {
        var style=Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/test"))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal("original")));
        var source=Component.literal("§6ROUND 3: §eYou chose §aSPLIT §eand gained §53,000 Motes§e!").setStyle(style);
        var out=SystemTranslationEngine.translate(source,"chat",manager);
        assertEquals(style.getClickEvent(),StyledLoreTest.styleAt(out,"平分").getClickEvent());
        assertEquals(style.getHoverEvent(),StyledLoreTest.styleAt(out,"平分").getHoverEvent());
    }
    @Test void finalRoundInMultilineComponentKeepsEveryStyledSibling() {
        var source=Component.empty()
            .append(Component.literal("ROUND 5 (FINAL): ").withStyle(ChatFormatting.GOLD,ChatFormatting.BOLD))
            .append(Component.literal("You chose ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("STEAL").withStyle(ChatFormatting.RED,ChatFormatting.BOLD))
            .append(Component.literal(" and gained ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("5,000 Motes").withStyle(ChatFormatting.DARK_PURPLE))
            .append(Component.literal("!\nYou earned ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("25,000 Motes").withStyle(ChatFormatting.DARK_PURPLE))
            .append(Component.literal(" in this match!").withStyle(ChatFormatting.YELLOW));
        var copy=source.copy();
        var out=SystemTranslationEngine.translate(source,"chat",manager);
        assertEquals("第 5 回合（最终回合）：你选择了独吞，获得 5,000 裂隙尘埃！\n你在本场对局中获得了 25,000 裂隙尘埃！",out.getString());
        StyledLoreTest.color(out,"独吞",ChatFormatting.RED);
        StyledLoreTest.color(out,"5,000",ChatFormatting.DARK_PURPLE);
        assertEquals(copy,source);
    }
    @Test void splitOrStealMenuIsScopedAndKeepsChoicesAndDynamicValues() {
        var menu=GuiTranslationEngine.menu("Split or Steal",manager);
        assertNotNull(menu);
        assertEquals("平分或独吞",GuiTranslationEngine.title(Component.literal("Split or Steal"),manager).getString());
        for(String choice:List.of("SPLIT","STEAL")) {
            var color=choice.equals("SPLIT")?ChatFormatting.GREEN:ChatFormatting.RED;
            String label=choice.equals("SPLIT")?"平分":"独吞";
            var source=Component.literal(choice).withStyle(color).withStyle(s->s.withClickEvent(new ClickEvent.RunCommand("/unchanged")));
            var copy=source.copy();var out=GuiTranslationEngine.button(source,menu);
            assertEquals(label,out.getString());StyledLoreTest.color(out,label,color);
            assertEquals(source.getStyle().getClickEvent(),StyledLoreTest.styleAt(out,label).getClickEvent());
            assertEquals(copy,source);assertSame(source,GuiTranslationEngine.button(source,null));
            for(String amount:List.of("0","1,000","5,000","123,456.75")) {
                var line=Component.literal("§6ROUND 9 (FINAL): §eYou chose "+(choice.equals("SPLIT")?"§a":"§c")+choice+" §eand gained §5"+amount+" Motes§e!");
                out=GuiTranslationEngine.button(line,menu);
                assertEquals("第 9 回合（最终回合）：你选择了"+label+"，获得 "+amount+" 裂隙尘埃！",out.getString());
                StyledLoreTest.color(out,label,color);StyledLoreTest.color(out,amount,ChatFormatting.DARK_PURPLE);
            }
        }
        for(String time:List.of("1s","30s","1m 5s"))
            assertEquals("剩余时间："+(time.matches("[0-9]+s")?time.replace("s","秒"):time),GuiTranslationEngine.button(Component.literal("Time Left: "+time),menu).getString());
        assertEquals("对手：Ubik Von Neumann",GuiTranslationEngine.button(Component.literal("Opponent: Ubik Von Neumann"),menu).getString());
        assertSame(menu,GuiTranslationEngine.menu("Split or Steal",manager));
    }
    @Test void unknownMultilineMessagesAndNamesStayUntouched() {
        var unknown=Component.literal("Player_123: STEAL\nUnrelated text").withStyle(ChatFormatting.BLUE);
        assertSame(unknown,SystemTranslationEngine.translate(unknown,"chat",manager));
        var mixed=Component.empty().withStyle(s->s.withHoverEvent(new HoverEvent.ShowText(Component.literal("original"))))
            .append(Component.literal("Player_123: STEAL\n").withStyle(ChatFormatting.BLUE))
            .append(Component.literal("ROUND 7 (FINAL): You chose SPLIT and gained 3,000 Motes!").withStyle(ChatFormatting.GREEN));
        var out=SystemTranslationEngine.translate(mixed,"chat",manager);
        assertTrue(out.getString().startsWith("Player_123: STEAL\n第 7 回合（最终回合）"));
        StyledLoreTest.color(out,"Player_123",ChatFormatting.BLUE);
        assertEquals(mixed.getStyle().getHoverEvent(),StyledLoreTest.styleAt(out,"平分").getHoverEvent());
    }
}
