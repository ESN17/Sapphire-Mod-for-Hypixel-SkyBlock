package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import com.sorakoi.sbtranslate.compat.*;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LiveFeedbackNovemberTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    static Component chat(String text){return SystemTranslationEngine.translate(Component.literal(text),"chat",manager);}
    static void translated(String text){assertNotEquals(TranslationManager.plain(text),chat(text).getString(),text);}
    @Test void sackVariantsKeepSignedCountsTimeAndStyles() {
        for(String time:List.of("5s","30s","16s","1m 5s"))for(String counts:List.of("§a+1 item.","§c-1,344 items.","§a+1,392 items.","§a+17 §eitems, §c-1,408 §eitems.")) {
            var source=Component.literal("§6[Sacks] "+counts+" §8(Last "+time+".)");var copy=source.copy();
            var result=SystemTranslationEngine.translate(source,"chat",manager);
            assertTrue(result.getString().startsWith("[储物袋]"),result.getString());assertFalse(result.getString().contains("items"));
            assertTrue(result.getString().contains(time));assertEquals(copy,source);
            StyledLoreTest.color(result,time,ChatFormatting.DARK_GRAY);
            if(counts.contains("-1,408"))StyledLoreTest.color(result,"-1,408",ChatFormatting.RED);
        }
    }
    @Test void autopetPreservesSkinPrestigeAndRuleLink() {
        for(String pet:List.of("§6Golden Dragon","§5Enderman§d ✦","§8[§6335§4✦§8] §6Golden Dragon")) {
            var click=new ClickEvent.RunCommand("/autopet");
            var source=Component.literal("§cAutopet §eequipped your §7[Lvl 200] "+pet+"§e! ")
                .append(Component.literal("VIEW RULE").withStyle(s->s.withColor(ChatFormatting.GREEN).withBold(true).withClickEvent(click)));
            var copy=source.copy();var result=SystemTranslationEngine.translate(source,"chat",manager);
            assertTrue(result.getString().contains("已装备"),result.getString());
            assertFalse(result.getString().contains("Golden Dragon"));assertFalse(result.getString().contains("Enderman"));
            if(pet.contains("✦"))assertTrue(result.getString().contains("✦"));
            if(pet.contains("335"))assertTrue(result.getString().contains("335"));
            result.visit((s,t)->{if(t.contains("查看规则"))assertEquals(click,s.getClickEvent());return Optional.empty();},Style.EMPTY);
            assertEquals(copy,source);
        }
    }
    @Test void slayerNotificationsRespectActualColorBoundaries() {
        var boss=chat("§c§lSLAYER BOSS! §7The §5Voidgloom Seraph IV §7spawned!");
        assertEquals("杀手首领! 虚空炽天使 IV 出现了!",boss.getString());
        StyledLoreTest.color(boss,"IV",ChatFormatting.DARK_PURPLE);
        var heal=chat("§eYour Slayer Kill gave you §c160 HP §ehealing for §a10 §eseconds!");
        assertTrue(heal.getString().contains("每秒恢复"),heal.getString());
        StyledLoreTest.color(heal,"160",ChatFormatting.RED);StyledLoreTest.color(heal,"10",ChatFormatting.GREEN);
        var progress=chat("§e   Enderman Slayer LVL 8 §7- Next LVL in §d403,087 XP!");
        assertEquals("   末影人杀手 等级 8 - 距下一级还需 403,087 经验!",progress.getString());
        StyledLoreTest.color(progress,"403,087",ChatFormatting.LIGHT_PURPLE);
    }
    @Test void dragonFightMessagesAndNames() {
        for(String text:List.of("§5☬ §7Rav64M §dplaced a Summoning Eye! §e(1/8)","§5☬ §7Rav64M §dplaced a Summoning Eye! Brace yourselves! §e(8/8)",
            "§5☬ §d§lThe §c§lUnstable Dragon§d§l has spawned!","§5☬ §7jendi2 §ddestroyed an Ender Crystal!",
            "§6§l       UNSTABLE DRAGON DOWN!","§7  Rav64M dealt the final blow.",
            "§e1st Damager §7- §6[MVP++] Honokar §7- §e11,769,157","§eYour Damage: §a0 §7(Position #20)"))translated(text);
        assertTrue(chat("☬ Rav64M placed a Summoning Eye! (7/8)").getString().contains("Rav64M"));
        assertEquals("Bob: The Unstable Dragon has spawned!",chat("Bob: The Unstable Dragon has spawned!").getString());
    }
    @Test void profileJerryBestiaryTuningAndLoadouts() {
        for(String text:List.of("§aYou are playing on profile: §ePomegranate §b(Co-op)",
            "§8Profile ID: b403bb9e-198c-42ea-901c-79daa07ce65",
            "§6You have §e25 §6unclaimed deliveries!","§eClick here or talk to Jerry to claim them!",
            "§aYou equipped Loadout 12!","§6§lBESTIARY §bBat §8III ➜ §bIV",
            "§aYour §eTuning Points §awere auto-assigned as convenience!"))translated(text);
        assertTrue(chat("Profile ID: b403bb9e-198c-42ea-901c-79daa07ce65").getString().endsWith("b403bb9e-198c-42ea-901c-79daa07ce65"));
        assertEquals("生物图鉴 蝙蝠 III ➜ IV",chat("BESTIARY Bat III ➜ IV").getString());
        var click=new ClickEvent.RunCommand("/jerry");
        var source=Component.literal("Click here").withStyle(s->s.withColor(ChatFormatting.YELLOW).withClickEvent(click)).append(Component.literal(" or talk to Jerry to claim them!"));
        var result=SystemTranslationEngine.translate(source,"chat",manager);
        assertTrue(result.getString().contains("点击这里"));
        result.visit((s,t)->{if(t.contains("点击这里"))assertEquals(click,s.getClickEvent());return Optional.empty();},Style.EMPTY);
    }
    @Test void bestiaryNestedMenusProgressAndMobNames() {
        for(String title:List.of("Bestiary","Bestiary ➜ Your Island","(1/2) Bestiary ➜ The Catacombs","Bestiary ➜ Dwarven Mines","Fishing ➜ Spooky","Bestiary Milestones")) {
            var menu=GuiTranslationEngine.menu(title,manager);assertNotNull(menu,title);
            assertNotEquals(title,GuiTranslationEngine.title(Component.literal(title),manager).getString());
            for(String text:List.of("Kills: 9,876,543","Progress to Tier XV: 57.1%","Overall Progress: 100% (MAX!)","Families Found 9/10","Families Completed 6/10","You haven't unlocked this Family yet!","Click to claim rewards!")) {
                var result=GuiTranslationEngine.detailLine(Component.literal(text),menu,manager);
                assertNotEquals(text,result.getString(),title+": "+text);
            }
            assertFalse(GuiTranslationEngine.detailLine(Component.literal("Cave Spider IV"),menu,manager).getString().contains("Spider"));
        }
        assertEquals("生物图鉴 ➜ 洞穴蜘蛛",GuiTranslationEngine.title(Component.literal("Bestiary ➜ Cave Spider"),manager).getString());
    }
    @Test void submergedStoneAndGearAgreeAndKeepEnglishMetadata() {
        var source=List.<Component>of(Component.literal("§7Applies the §9Submerged §7reforge when"),Component.literal("§7combined with §cArmor§7."));
        var result=LoreTranslationEngine.translate(source,manager,"DEEP_SEA_ORB",(s,l)->List.of(s),false).lines();
        String text=result.stream().map(Component::getString).reduce("",String::concat);
        assertTrue(text.contains(manager.scope("reforges").get("Submerged")),text);
        assertFalse(text.contains("潮没"));assertEquals("§7Applies the §9Submerged §7reforge when",source.getFirst().getString());
        assertTrue(manager.item("DEEP_SEA_ORB").originalName().equals("Deep Sea Orb"));
    }
    @Test void requestedNamesAndAllFishIds() throws Exception {
        assertEquals("闪钓",manager.enchantment("Flash"));
        for(var entry:Map.of("Juju Shortbow","唤魔短弓","Burnt Texts","烧书","Golden Dragon","金龙","Reaper Orb","死神宝珠","Warden Helmet","监守者头盔").entrySet())assertEquals(entry.getValue(),manager.knownItemName(entry.getKey()));
        try(var files=Files.list(Path.of("src/main/resources/defaults/translations/items"))) {
            for(var file:files.filter(p->p.getFileName().toString().contains("_THE_FISH")).toList()) {
                var row=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if(row.get("id").getAsString().equals("GORF_THE_FISH"))assertEquals("鱼之蛙青",row.get("name").getAsString());
                else assertTrue(row.get("name").getAsString().endsWith("之鱼"),file.toString());
                assertEquals(row.get("name").getAsString(),manager.knownItemName(row.get("_original_name").getAsString()));
            }
        }
    }
    @Test void skyHanniChatAndProfitTrackerUseLiveCatalog() {
        var live=new SkyHanniLiveTranslation();var dict=new SkyHanniDictionary(Map.of("Visitor Timer","访客计时器"));
        for(String text:List.of("§e[SkyHanni] Copied message to clipboard!","§e[SkyHanni] You have gained §b523 §eBits.",
            "§e§lVoidgloom Seraph IV Profit Tracker","§7Slayer Spawn Costs: §c-3.4M","§7Bosses killed: §e288",
            "§eTotal Profit: §6195,494,974 coins","§eSession Profit: §61,691,602 coins","§eProfit Per Hour: §65,803,979 coins",
            "§7Price Source §e[BZ Instant Buy]","§7Display Mode §e[Session]","§cReset session!","§aResume session!","§eTotal Time: §b17m 28s §c(Paused!)","§eVisitor Timer")) {
            var result=live.translate(Component.literal(text),manager,dict);
            assertNotEquals(TranslationManager.plain(text),TranslationManager.plain(result.getString()),text);
            assertEquals(result.getString(),TranslationManager.plain(live.translate(text,manager,dict)));
        }
        for(String name:List.of("Judgement Core","End Stone Idol","Summoning Eye","Null Sphere","Twilight Arrow Poison","Paragon Shard","Ender Pearl","Null Atom","Etherwarp Merger")) {
            assertEquals(manager.knownItemName(name),live.translate(Component.literal("§6"+name),manager,dict).getString());
        }
        assertEquals(manager.knownItemName("Enchanted Ender Pearl"),live.translate(Component.literal("Ench Ender Pearl"),manager,dict).getString());
        var untouched=Component.literal("ESN17_OwO");assertEquals(untouched,live.translate(untouched,manager,dict));
    }
}
