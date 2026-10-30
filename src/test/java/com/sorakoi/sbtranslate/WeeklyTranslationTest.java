package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WeeklyTranslationTest {
    static TranslationManager manager;
    @TempDir Path root;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;}
    @Test void auctionPreservesBuyerPriceAndClick() {
        var click=new ClickEvent.RunCommand("/viewauction unchanged");
        var raw=Component.literal("§6[Auction] §aSomePlayer §ebought §5Recombobulator 3000 §efor §62,650,000 coins §e§lCLICK").withStyle(s->s.withClickEvent(click));
        var copy=raw.copy();var result=SystemTranslationEngine.translate(raw,"chat",manager);
        assertEquals("[拍卖] SomePlayer 花费 2,650,000 硬币购买了 "+manager.name("RECOMBOBULATOR_3000","")+" 点击查看",result.getString());
        StyledLoreTest.color(result,"SomePlayer",ChatFormatting.GREEN);StyledLoreTest.color(result,"2,650,000",ChatFormatting.GOLD);
        assertEquals(click,StyledLoreTest.styleAt(result,"SomePlayer").getClickEvent());assertEquals(copy,raw);
    }
    @Test void outbidAndBazaarClaimUseDynamicNames() {
        var outbid=SystemTranslationEngine.translate(Component.literal("§6[Auction] §aMrBaiacu §eoutbid you by §659,083 coins §efor §fFiredust Dagger §e§lCLICK"),"chat",manager);
        assertTrue(outbid.getString().startsWith("[拍卖] MrBaiacu 对 "),outbid.getString());assertFalse(outbid.getString().contains("Firedust"));
        var claimed=SystemTranslationEngine.translate(Component.literal("[Bazaar] Claimed 24x Nessie Shard worth 4.8M coins bought for 202k each!"),"chat",manager);
        assertTrue(claimed.getString().startsWith("[集市] 已领取 24 个 "));assertTrue(claimed.getString().contains("4.8M"));assertTrue(claimed.getString().contains("202k"));
    }
    @Test void miningBossbarPreservesEventLocationCountdownStyles() {
        var source=Component.literal("§e§lEVENT §6§lRAFFLE §e§lACTIVE IN §b§lFAR RESERVE §e§lfor §a§l01:03");
        var out=SystemTranslationEngine.translate(source,"bossbar",manager);
        assertEquals("活动 抽奖 正在 "+manager.scope("locations").get("Far Reserve")+" 进行,剩余 01:03",out.getString());
        StyledLoreTest.color(out,"抽奖",ChatFormatting.GOLD);StyledLoreTest.color(out,"01:03",ChatFormatting.GREEN);
        var padded=SystemTranslationEngine.translate(Component.literal("    §dBETTER TOGETHER STARTED!"),"chat",manager);
        assertEquals("    齐心协力开始了!",padded.getString());
    }
    @Test void tabFieldsTranslateButUnrelatedPlayerChatDoesNot() {
        assertEquals("区域: "+manager.scope("locations").get("Glacite Tunnels"),SystemTranslationEngine.translate(Component.literal("Area: Glacite Tunnels"),"tablist",manager).getString());
        assertEquals("玩家 (32)",SystemTranslationEngine.translate(Component.literal("Players (32)"),"tablist",manager).getString());
        assertEquals("秘银矿工: 50%",SystemTranslationEngine.translate(Component.literal(" Mithril Miner: 50% "),"tablist",manager).getString().strip());
        for(String text:List.of("[MVP+] Player: You purchased Coal for 10 coins!","Players (32)","[Party] Player: EVENT RAFFLE")) {
            var raw=Component.literal(text);assertSame(raw,SystemTranslationEngine.translate(raw,"chat",manager));
        }
    }
    @Test void extraLocationGlyphAndUppercaseAreHandled() {
        for(String prefix:List.of("⏣ ","♨ ","⚽⏣ ","⏣\u2060 ")) {
            var out=SidebarTranslationEngine.location(Component.literal(prefix+"BAZAAR ALLEY"),manager);
            assertTrue(out.getString().contains("集市巷"),out.getString());
        }
    }
    @Test void translatedUnitsKeepTheCapturedNumbersColor() {
        var raw=Component.literal("§7Cooldown: §a12s");
        var match=new TranslationManager.Template("Cooldown: {{N0}}s","冷却时间: {{N0}}秒").apply(TranslationManager.plain(raw.getString()));
        var out=TextTranslationEngine.apply(raw,new TranslationManager.Match(match.rule(),match.pieces(),Map.of("{{N0}}s","{{N0}}秒")));
        assertEquals("冷却时间: 12秒",out.getString());StyledLoreTest.color(out,"秒",ChatFormatting.GREEN);
        assertThrows(IllegalArgumentException.class,()->new TranslationManager.Template("{value}s","{value}秒"));
        raw=Component.literal("§7Gain after §a5s§7, lose after §c5s§7.");
        match=new TranslationManager.Template("Gain after {{N0}}s, lose after {{N1}}s.","{{N0}}秒后获得,{{N1}}秒后失去.").apply(TranslationManager.plain(raw.getString()));
        out=TextTranslationEngine.apply(raw,new TranslationManager.Match(match.rule(),match.pieces(),Map.of("{{N0}}s","{{N0}}秒","{{N1}}s","{{N1}}秒")));
        assertEquals("5秒后获得,5秒后失去.",out.getString());StyledLoreTest.color(out,"5秒后获得",ChatFormatting.GREEN);
        StyledLoreTest.color(out,"5秒后失去",ChatFormatting.RED);
    }
    @Test void equippedPetItemReusesItsDescriptions() {
        List<Component> raw=List.of(Component.literal("§6Held Item: §6Quick Claw"),
            Component.literal("§7Every §a2 §7pet levels, you gain §a+1"),Component.literal("§6Mining Speed §7and §a+1 §6Mining Fortune."));
        var copies=raw.stream().map(Component::copy).toList();
        var result=LoreTranslationEngine.translate(raw,manager,"ENDERMAN;4",(line,original)->List.of(line),false);
        assertTrue(result.lines().getFirst().getString().startsWith("携带物品: "));assertEquals(copies,raw);
        assertTrue(result.lines().stream().noneMatch(c->c.getString().contains("Mining Speed")),result.lines().toString());
        var combined=Component.empty();result.lines().forEach(combined::append);StyledLoreTest.color(combined,"+1",ChatFormatting.GREEN);
    }
    @Test void redundantAliasesDoNotHideColorsAndStructuredRaritiesStayDistinct() {
        var raw=Component.literal("§7Gain §aPower§7 and power.");
        var match=TranslationManager.replacement("test","Gain Power and power.","获得能量和力量.");
        var output=TextTranslationEngine.apply(raw,new TranslationManager.Match(match.rule(),match.pieces(),Map.of("Power","能量","power","力量","and","和")));
        assertEquals("获得能量和力量.",output.getString());StyledLoreTest.color(output,"能量",ChatFormatting.GREEN);
        var unsafe=TranslationManager.replacement("test","Gain Power and power.","获得能量.");
        assertSame(raw,TextTranslationEngine.apply(raw,unsafe));
        raw=Component.literal("§8[§9Griffin§8/§5Griffin§8]");
        match=TranslationManager.replacement("test","[Griffin/Griffin]","[狮鹫/狮鹫]");
        output=TextTranslationEngine.apply(raw,new TranslationManager.Match(match.rule(),match.pieces(),Map.of("Griffin","狮鹫")));
        assertEquals("[狮鹫/狮鹫]",output.getString());
        var styles=new ArrayList<Integer>();output.visit((style,text)->{if(text.equals("狮鹫"))styles.add(style.getColor().getValue());return Optional.empty();},net.minecraft.network.chat.Style.EMPTY);
        assertEquals(List.of(ChatFormatting.BLUE.getColor(),ChatFormatting.DARK_PURPLE.getColor()),styles);
    }
    @Test void editorSavesBacksUpReloadsAndRejectsBadVariables() throws Exception {
        Files.createDirectories(root.resolve("translations/items"));Files.writeString(root.resolve("translations/chat.json"),"{\"Hello!\":\"你好!\",\"Other\":\"其他\"}");
        var entry=TranslationEditorStore.entries(root).stream().filter(e->e.source().equals("Hello!")).findFirst().orElseThrow();
        TranslationEditorStore.save(root,entry,"您好!");var reload=new TranslationManager();reload.load(root);
        assertEquals("您好!",reload.match("chat",null,"Hello!").text());assertEquals("其他",reload.match("chat",null,"Other").text());
        try(var files=Files.walk(root.resolve("translation-edits"))){assertEquals(1,files.filter(Files::isRegularFile).count());}
        var bad=new TranslationEditorStore.Entry("chat.json","text",-1,"chat","Value {value}","");
        assertThrows(java.io.IOException.class,()->TranslationEditorStore.save(root,bad,"数值"));
        assertThrows(java.io.IOException.class,()->TranslationEditorStore.save(root,new TranslationEditorStore.Entry("../escape.json","text",-1,"","X",""),"错误"));
    }
    @Test void dictionaryUpgradePreservesUserEditsAndBacksUpOriginals() throws Exception {
        byte[] old="old".getBytes(java.nio.charset.StandardCharsets.UTF_8),next="next".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(old));
        assertTrue(BundledDictionaryInstaller.install(root,"translations/chat.json",old,null));
        assertTrue(BundledDictionaryInstaller.install(root,"translations/chat.json",next,hash));
        assertArrayEquals(old,Files.readAllBytes(root.resolve("dictionary-backups/"+hash+"/translations/chat.json")));
        Files.writeString(root.resolve("translations/chat.json"),"user");assertFalse(BundledDictionaryInstaller.install(root,"translations/chat.json",next,hash));
        assertEquals("user",Files.readString(root.resolve("translations/chat.json")));
    }
    @Test void dictionaryUpgradeRecognizesBothShippedVersions() throws Exception {
        byte[] first="first".getBytes(),second="second".getBytes(),latest="latest".getBytes();
        var digest=java.security.MessageDigest.getInstance("SHA-256");
        String h1=HexFormat.of().formatHex(digest.digest(first)),h2=HexFormat.of().formatHex(digest.digest(second));
        for(String file:List.of("translations/tablist.json","translations/scoreboard.json")) {
            byte[] old=file.contains("tablist")?first:second;
            BundledDictionaryInstaller.install(root,file,old,null);
            assertTrue(BundledDictionaryInstaller.install(root,file,latest,h1+","+h2));
            assertArrayEquals(latest,Files.readAllBytes(root.resolve(file)));
            Files.writeString(root.resolve(file),"player-edited");
            assertFalse(BundledDictionaryInstaller.install(root,file,latest,h1+","+h2));
            assertEquals("player-edited",Files.readString(root.resolve(file)));
        }
    }
    @Test void savedParagraphOverridesBundledSharedWording() throws Exception {
        Files.createDirectories(root.resolve("translations/items"));
        Files.writeString(root.resolve("translations/items/TEST.json"),"""
            {"id":"TEST","name":"测试","_original_name":"Test","lore":{},"loreParagraphs":[{"source":["Test description."],"translation":"旧物品说明."}]}
            """);
        Files.writeString(root.resolve("translations/tooltip-paragraphs.json"),"""
            {"loreParagraphs":[{"source":["Test description."],"translation":"通用说明."}]}
            """);
        var entry=TranslationEditorStore.entries(root).stream().filter(e->e.kind().equals("paragraph")).findFirst().orElseThrow();
        TranslationEditorStore.save(root,entry,"玩家修改的说明.");
        var local=new TranslationManager();local.load(root);
        var result=LoreTranslationEngine.translate(List.of(Component.literal("Test description.")),local,"TEST",(line,original)->List.of(line),false);
        assertEquals("玩家修改的说明.",result.lines().getFirst().getString());
    }
}
