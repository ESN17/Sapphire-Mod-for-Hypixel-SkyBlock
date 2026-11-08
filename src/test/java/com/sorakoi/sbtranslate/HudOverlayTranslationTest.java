package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.llm.*;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class HudOverlayTranslationTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;}

    @Test void localTabHeaderFooterAndBossRemainDisplayCopies() {
        var raw=Component.literal("§aPlayers §f(21)\n§bArea: §7The Rift\n§fRift Time Left: §a44m48s");
        var before=raw.copy();var out=HudTranslationEngine.translate(raw,"tablist",manager);
        assertTrue(out.getString().contains("玩家 (21)"),out.getString());
        assertTrue(out.getString().contains("裂隙维度"),out.getString());
        assertTrue(out.getString().contains("44m48s"),out.getString());assertEquals(before,raw);
        var boss=Component.literal("§e§lEVENT §6§lRAFFLE §e§lACTIVE IN §b§lFAR RESERVE §e§lfor §a§l01:03");
        var saved=boss.copy();out=HudTranslationEngine.translate(boss,"bossbar",manager);
        assertTrue(out.getString().contains("抽奖"));assertTrue(out.getString().contains("01:03"));
        StyledLoreTest.color(out,"01:03",ChatFormatting.GREEN);assertEquals(saved,boss);
    }

    @Test void protectNamesBadgesKnownChineseDomainsAndDynamicDurations() {
        var raw=Component.literal("§7[§d410§7] §6α §b[MVP+] ESN17_OwO §eArcher XXXVII");
        var request=HudLlmText.prepare(raw,Set.of("ESN17_OwO"));assertNotNull(request);
        for(String literal:List.of("410","α","ESN17_OwO","MVP","XXXVII"))assertFalse(request.text().contains(literal));
        var out=request.restore(request.text().replace("Archer","弓箭手"));assertNotNull(out);
        assertEquals("[410] α [MVP+] ESN17_OwO 弓箭手 XXXVII",out.getString());
        StyledLoreTest.color(out,"α",ChatFormatting.GOLD);StyledLoreTest.color(out,"ESN17_OwO",ChatFormatting.AQUA);
        var header=HudLlmText.prepare(Component.literal("You are playing on MC.HYPIXEL.NET"),Set.of());
        assertNotNull(header);assertFalse(header.text().contains("MC.HYPIXEL.NET"));
        out=header.restore(header.text().replace("You are playing on","你正在游玩"));
        assertNotNull(out);assertTrue(out.getString().endsWith("MC.HYPIXEL.NET"));
        var duration=HudLlmText.prepare(Component.literal("§eNew timer: §a21m41s §b裂隙尘埃"),Set.of());
        assertNotNull(duration);assertFalse(duration.text().contains("21m41s"));assertFalse(duration.text().contains("裂隙尘埃"));
        out=duration.restore(duration.text().replace("New timer","新倒计时"));
        assertEquals("新倒计时: 21m41s 裂隙尘埃",out.getString());
    }

    @Test void skipAlreadyTranslatedRowsAndIdentifiers() {
        for(String text:List.of("属性: 12%","§7[§d410§7] §6α §b[MVP+] ESN17_OwO","档案: Peach",
                "服务器: mini123F","市长: Aatrox","MC.HYPIXEL.NET","44m48s","12/100 XP".replace("XP","经验")))
            assertNull(HudLlmText.prepare(Component.literal(text),Set.of("ESN17_OwO")),text);
    }

    @Test void multilineTranslatableComponentsUseDisplayCopiesAndKeepNewlines() {
        var source=Component.translatableWithFallback("sapphire.test.hud.timer","New timer: %s",Component.literal("42s"))
            .append(Component.literal("\n\nNew timer: 31s").withStyle(ChatFormatting.GREEN));
        var before=source.copy();var queue=new HudTranslationQueue();
        assertSame(source,queue.displayLines(source,"tablist",Set.of()));
        queue.tick((text,valid,success,failure)->{success.accept(text.replace("New timer","新倒计时"));return true;});
        var out=queue.displayLines(source,"tablist",Set.of());
        assertEquals("新倒计时: 42s\n\n新倒计时: 31s",out.getString());
        StyledLoreTest.color(out,"31s",ChatFormatting.GREEN);assertEquals(before,source);
        var unknownBoss=Component.literal("⚽ Unknown boss");
        assertSame(unknownBoss,HudTranslationEngine.translate(unknownBoss,"bossbar",manager));
    }

    @Test void oneTemplateRebindsLatestNumbersStylesAndActionsWithoutMoreApiCalls() {
        var queue=new HudTranslationQueue();var calls=new AtomicInteger();
        var complete=new AtomicReference<Consumer<String>>();
        var first=Component.literal("§eNew countdown: §a30 seconds");var snapshot=first.copy();
        assertSame(first,queue.display(first,"bossbar",Set.of()));
        for(int i=0;i<100;i++)queue.display(first,"bossbar",Set.of());
        assertEquals(1,queue.pendingCount());assertEquals(0,calls.get());
        final String[] sent={null};
        queue.tick((text,valid,success,failure)->{calls.incrementAndGet();sent[0]=text;complete.set(success);return true;});
        var second=Component.literal("§eNew countdown: §a29 seconds");
        assertSame(second,queue.display(second,"bossbar",Set.of()));assertEquals(1,queue.pendingCount());
        complete.get().accept(sent[0].replace("New countdown","新倒计时").replace("seconds","秒"));
        var out=queue.display(second,"bossbar",Set.of());assertEquals("新倒计时: 29 秒",out.getString());
        StyledLoreTest.color(out,"29",ChatFormatting.GREEN);
        var click=new ClickEvent.RunCommand("/unchanged");
        var third=Component.literal("§bNew countdown: §c28 seconds").withStyle(s->s.withClickEvent(click));
        out=queue.display(third,"tablist",Set.of());assertEquals("新倒计时: 28 秒",out.getString());
        StyledLoreTest.color(out,"28",ChatFormatting.RED);assertEquals(click,StyledLoreTest.styleAt(out,"28").getClickEvent());
        assertEquals(1,calls.get());assertEquals(snapshot,first);
    }

    @Test void clearDiscardsLateApiResponsesAndFailureBackoffAvoidsFrameRetries() {
        var time=new AtomicLong(100);var queue=new HudTranslationQueue(time::get);
        var source=Component.literal("Unknown timer: 12s");queue.display(source,"bossbar",Set.of());
        var completed=new AtomicReference<Consumer<String>>();var text=new AtomicReference<String>();
        queue.tick((t,v,s,f)->{text.set(t);completed.set(s);return true;});
        queue.clear();completed.get().accept(text.get().replace("Unknown timer","未知倒计时"));
        assertSame(source,queue.display(source,"bossbar",Set.of()));
        queue.tick((t,v,s,f)->{f.accept("offline");return true;});
        for(int i=0;i<100;i++)assertSame(source,queue.display(source,"bossbar",Set.of()));
        assertEquals(0,queue.pendingCount());time.addAndGet(60001);
        queue.display(source,"bossbar",Set.of());assertEquals(1,queue.pendingCount());
    }

    @Test void boundHudBacklogAndRejectMalformedResponses() {
        var queue=new HudTranslationQueue();
        for(int i=0;i<700;i++)queue.display(Component.literal("Unknown field "+Integer.toString(i,36)+": 1"),"tablist",Set.of());
        assertTrue(queue.viewCount()<=512);assertTrue(queue.pendingCount()<=128);
        var request=HudLlmText.prepare(Component.literal("New countdown: 12s"),Set.of());assertNotNull(request);
        assertNull(request.restore("倒计时: 999 秒"));assertNull(request.restore("<t0>倒计时</t0>"));
        assertNotNull(request.restore(request.text().replace("New countdown","新倒计时")));
    }

    @Test void newApiScopeSettingsCopyAndPersistIndependently() throws Exception {
        var config=LlmConfig.defaults();assertTrue(config.tablist);assertTrue(config.bossbar);assertFalse(config.enabled);
        config.tablist=false;var copy=config.copy();assertFalse(copy.tablist);assertTrue(copy.bossbar);
        copy.bossbar=false;assertTrue(config.bossbar);
    }
}
