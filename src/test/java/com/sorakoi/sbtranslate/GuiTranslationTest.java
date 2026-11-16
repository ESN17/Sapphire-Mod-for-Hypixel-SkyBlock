package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GuiTranslationTest {
    static TranslationManager manager;
    @TempDir Path root;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release() {manager=null;}
    @Test void winterChatWithStyledEllipsesAndGiftProgress() {
        var click = new ClickEvent.OpenUrl(java.net.URI.create("https://example.org/"));
        for (boolean twice : List.of(false,true)) {
            for (var phrase : List.of(
                    List.of("Frozen Steve fell into the pond long ago, never to ","resurface...until"," now!","浮出水面……直到"),
                    List.of("The Grinch stole Jerry's ","Gifts...get"," them back!","礼物……把"))) {
                var original = Component.empty().withStyle(ChatFormatting.GREEN);
                if (twice) original.append(Component.literal("DOUBLE HOOK! ").withStyle(ChatFormatting.YELLOW,ChatFormatting.BOLD));
                original.append(phrase.get(0)).append(Component.literal(phrase.get(1)).withStyle(s -> s.withClickEvent(click))).append(phrase.get(2));
                var result = SystemTranslationEngine.translate(original,"chat",manager);
                assertFalse(result.getString().matches(".*[A-Za-z].*"),result.getString());
                assertEquals(twice,result.getString().startsWith("一竿双钓！"));
                var linked = new StringBuilder();
                result.visit((style,text)->{if(click.equals(style.getClickEvent())) linked.append(text);return Optional.empty();},Style.EMPTY);
                assertEquals(phrase.get(3),linked.toString());
            }
        }
        for (var color : List.of("White","Green","Red")) {
            var gift = Component.empty().withStyle(ChatFormatting.GREEN)
                .append(Component.literal("GIFT!").withStyle(ChatFormatting.BOLD))
                .append(" You found a ").append(Component.literal(color+" Gift").withStyle(ChatFormatting.WHITE))
                .append("! ").append(Component.literal("(").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("3").withStyle(ChatFormatting.WHITE)).append(Component.literal("/").withStyle(ChatFormatting.GRAY))
                .append("20").append(Component.literal(")").withStyle(ChatFormatting.GRAY));
            assertEquals("发现礼物！你找到了"+manager.knownItemName(color+" Gift")+"！ (3/20)",
                SystemTranslationEngine.translate(gift,"chat",manager).getString());
        }
    }
    @Test void apiFallbackRemainsAvailableWhenAnOfflineMatchCannotRender() {
        // This split has no equivalent literal style mapping in the Chinese sentence.
        var original = Component.literal("Frozen Steve fell into the ").withStyle(ChatFormatting.GREEN)
            .append(Component.literal("pond").withStyle(ChatFormatting.RED))
            .append(" long ago, never to resurface...until now!");
        assertNotNull(SystemTranslationEngine.match(original.getString(),"chat",manager));
        assertSame(original,SystemTranslationEngine.translate(original,"chat",manager));
        assertFalse(com.sorakoi.sbtranslate.client.OnlineChatTranslation.usesLocalDictionary(original,manager));
    }
    @Test void installedFishingPartsUseTheRodTooltip() throws Exception {
        var ids = List.of("COMMON_HOOK","TREASURE_HOOK","PUDDLE_JUMPER_HOOK","PHANTOM_HOOK","HOTSPOT_HOOK",
                "SHREDDED_LINE","TROPHY_LINE","TITAN_LINE","SPEEDY_LINE","FESTIVE_SINKER","ICY_SINKER",
                "HOTSPOT_SINKER","JUNK_SINKER","PRISMARINE_SINKER","CHUM_SINKER","STINGY_SINKER","SPONGE_SINKER","LOTUS_SINKER");
        for (var id : ids) {
            var data = com.google.gson.JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/defaults/translations/items",id+".json"))).getAsJsonObject();
            var en = data.get("_original_name").getAsString();
            var glyph = en.endsWith("Hook") ? "ථ" : en.endsWith("Line") ? "ꨃ" : "࿉";
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(glyph+" "+en));
            for (var line : data.getAsJsonArray("_original_lore")) {
                if (line.getAsString().isBlank()) break;
                lines.add(Component.literal(line.getAsString()));
            }
            var out = LoreTranslationEngine.translate(lines,manager,"ROD_OF_THE_SEA",(s,l)->List.of(s),false);
            assertEquals(glyph+" "+data.get("name").getAsString(),out.lines().getFirst().getString(),id);
            assertTrue(out.missing().isEmpty(),id+" "+out.missing());
            assertTrue(out.lines().size()>=2,id);
        }
    }
    @Test void bestiaryAndStatDetailsFromReleaseScreenshots() {
        var menu = new GuiTranslationEngine.Menu("server-navigation", List.of(), List.of(), Map.of());
        var examples = Map.ofEntries(
            Map.entry("✎ Category: Basic", "✎ 类别：基础"),
            Map.entry("Value: +397✎", "数值：+397✎"),
            Map.entry("+350✎ Base Value", "+350✎ 基础数值"),
            Map.entry("+35✎ Stars", "+35✎ 星级"),
            Map.entry("+5✎ Beastslayer's Codex Passive", "+5✎ 屠兽者法典被动加成"),
            Map.entry("Stats which are built into each item.", "这些属性由物品本身提供。"),
            Map.entry("Mob Type: ✰ Humanoid", "生物类型：✰ 人形"),
            Map.entry("Your [Lv62] Executive Wendy Stats:", "你的[等级 62] 主管温蒂统计："),
            Map.entry("Mob Types: ⛏ Subterranean, ⛨ Shielded", "生物类型: ⛏ 地下, ⛨ 护盾"),
            Map.entry("Stoneworm Bonuses", "石虫加成"),
            Map.entry("Tier XII Rewards", "XII 级奖励"),
            Map.entry("+4 Stoneworm ✯ Magic Find", "+4 石虫 ✯ 稀有物品概率加成"),
            Map.entry("Families Completed: 17.1%", "已完成的生物家族：17.1%"));
        examples.forEach((english, chinese) -> assertEquals(chinese,
            GuiTranslationEngine.detailLine(Component.literal(english), menu, manager).getString(), english));
        var rendered = lore("Bestiary", "View all of the mobs that you've", "found and killed in The Catacombs.");
        assertTrue(rendered.lines().stream().anyMatch(line -> line.getString().contains("地牢")));
        var styled = Component.literal("+350✎").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" Base Value").withStyle(ChatFormatting.WHITE));
        var translated = GuiTranslationEngine.detailLine(styled, menu, manager);
        var spans = new LinkedHashMap<String, Integer>();
        translated.visit((style, value) -> {
            if (!value.isBlank() && style.getColor() != null) spans.put(value.trim(), style.getColor().getValue());
            return Optional.empty();
        }, Style.EMPTY);
        assertEquals(0x55FFFF, spans.get("+350✎"));
        assertEquals(0xFFFFFF, spans.get("基础数值"));
    }
    @Test void huntingBoxMenuAndSkyHanniUseCatalogNames() {
        var menu = GuiTranslationEngine.menu("(4/12) Hunting Box", manager);
        assertNotNull(menu);
        assertEquals("狩猎箱", GuiTranslationEngine.title(Component.literal("Hunting Box"), manager).getString());
        assertEquals("莲鱼", GuiTranslationEngine.button(Component.literal("Lotusfish"), menu).getString());
        for (var example : Map.of("Tropical Fish Family", "热带鱼家族", "Owned: 2,170 Shards", "持有：2,170 个碎片",
                "Attribute Maxed!", "属性已满级！", "COMMON WATER SHARD (ID C26)", "普通水生碎片 (ID C26)",
                "Lotus Trophy X (Fishing)", "莲花奖杯 X（钓鱼）", "Shards Sell Price: 429,301.0 Coins","碎片卖价：429,301.0 硬币").entrySet())
            assertEquals(example.getValue(), GuiTranslationEngine.detailLine(Component.literal(example.getKey()),menu,manager).getString());
        var description = lore("Hunting Box","Lotusfish follow a strict diet","consisting only of the various","lotuses found around the Atoll.",
                "Grants +10 ♔ Trophy Chance while","in the Lotus Atoll.");
        assertEquals(List.of("莲鱼只以环礁周围的各类莲花为食。","在莲花环礁，♔ 奖杯概率提高 +10。"),
                description.lines().stream().map(Component::getString).toList());
        var live = new com.sorakoi.sbtranslate.compat.SkyHanniLiveTranslation();
        var dictionary = new com.sorakoi.sbtranslate.compat.SkyHanniDictionary(Map.of());
        for (String shard : List.of("Bogged Shard","Haggard Shard","Lotusfish Shard","Tank Zombie Shard")) {
            String name = manager.knownItemName(shard);
            assertNotNull(name,shard);
            var line = Component.literal(shard).append(Component.literal(" x5530").withStyle(ChatFormatting.GRAY));
            assertEquals(name+" x5530",live.translate(line,manager,dictionary).getString());
        }
        assertEquals("立即卖出总价：13,842,412",live.translate(Component.literal("Total Instant Sell Value: 13,842,412"),manager,dictionary).getString());
    }
    @Test void bestiaryLootUsesCatalogAndKeepsCounts(){
        var menu=new GuiTranslationEngine.Menu("server-navigation",List.of(),List.of(),Map.of());
        for(String row:List.of("▪ Enchanted Glacite x16-32","▪ Glacite Powder x10,000","▪ Glacite Amalgamation (2%)","▪ Littlefoot Fluff (0.2%)","Mob Types: ❄ Glacial, ✦ Humanoid, ⚲ Elusive","Health: 25,000,000❤","Your [Lv508] Littlefoot Stats:")){
            String zh=GuiTranslationEngine.detailLine(Component.literal(row),menu,manager).getString();
            assertFalse(zh.matches(".*[A-Za-z]{2}.*"),row+" -> "+zh);
            assertEquals(row.replaceAll("[^0-9.%]",""),zh.replaceAll("[^0-9.%]",""));
        }
    }
    @Test void splitHistoryLoadoutsAndWatchdog() {
        for(var row:List.of(new String[]{"R7     STEAL | SPLIT","独吞 | 平分"},
                new String[]{"5,000 Motes if they SPLIT too","5,000 裂隙尘埃"},
                new String[]{"Helmet: Jaded Helmet of Divan","头盔"},
                new String[]{"Gloves/Bracelet: Blazing Dwarven Handwarmers","手套/手镯"},
                new String[]{"Pet: [Lvl 100] Scatha","等级 100"},
                new String[]{"HOTF: None","森林之心：无"})){
            String out=SystemTranslationEngine.translate(Component.literal(row[0]),"gui-tooltip",manager).getString();
            assertTrue(out.contains(row[1]),out);assertFalse(out.contains("Jaded") || out.contains("Blazing"),out);
        }
        String out=SystemTranslationEngine.translate(Component.literal("Watchdog has banned §c2,848§r players in the last 7 days.\nStaff have banned an additional §c10,954§r in the last 7 days."),"chat",manager).getString();
        assertTrue(out.contains("2,848") && out.contains("10,954") && !out.contains("banned"),out);
    }
    @Test void riftMenuAndFramedSystemMessagesUseLiveValues() {
        var vendor=new GuiTranslationEngine.Menu("server-navigation",List.of(),List.of(),Map.of());
        for(var pair:List.of(new String[]{"Average Motes per round: 1,252","1,252"},
                new String[]{"Playstyle: 100% SPLIT","平分"},new String[]{"Rounds played: 4,987,053","轮"},
                new String[]{"Permanent: +70 ⌛ Rift Time","裂隙时间"},new String[]{"Use cost: 21m41s ⌛ Rift Time","21分41秒"},
                new String[]{"Created at: 周四 12.11.2026 21:56:33","周四"},new String[]{"Obtained: November 12, 2026","2026年11月12日"},
                new String[]{"Puff Crux x64","x64"},new String[]{"Disinfestor Gloves","手套"})) {
            var source=Component.literal(pair[0]);var out=GuiTranslationEngine.detailLine(source,vendor,manager);
            assertNotSame(source,out,pair[0]);assertTrue(out.getString().contains(pair[1]),out.getString());
        }
        var ring=List.<Component>of(Component.literal("Monster melee attacks reduce 1 less"),
            Component.literal("⌛ Rift Time if there is another"),Component.literal("player within 50 blocks."));
        var result=LoreTranslationEngine.translate(ring,manager,"BLUETOOTH_RING",(s,l)->List.of(s),false);
        assertTrue(result.missing().isEmpty(),result.missing().toString());
        assertTrue(result.lines().getFirst().getString().contains("50 格"));
        var sky=SystemTranslationEngine.translate(Component.literal("[SkyHanni] Gained 50 motes this Rift session! (831/h)"),"skyhanni",manager);
        assertTrue(sky.getString().contains("每小时 831"),sky.getString());
        var notice=Component.literal("---------\n§c[MVP+] ESN17_OwO created a BIN auction for Combat Exp Boost at 1,000,000 coins!\n---------");
        var zh=SystemTranslationEngine.translate(notice,"chat",manager).getString();
        assertTrue(zh.contains("1,000,000") && zh.contains("ESN17_OwO") && !zh.contains("created a BIN"),zh);
    }
    static LoreTranslationEngine.Result lore(String title,String... lines) {
        var menu=GuiTranslationEngine.menu(title,manager);assertNotNull(menu,title);
        return LoreTranslationEngine.translate(Arrays.stream(lines).<Component>map(Component::literal).toList(),manager,null,(s,original)->List.of(s),false,false,false,menu.paragraphs());
    }
    @Test void titlesPreserveUntranslatedSearchAndProductValues() {
        for(var row:List.of(new String[]{"SkyBlock Menu","SkyBlock 菜单"},new String[]{"Collections","收藏"},
            new String[]{"Auction House","拍卖行"},new String[]{"Bazaar ➜ Enchanted Diamond","集市 ➜ 附魔钻石"},
            new String[]{"Bazaar ➜ Mining","集市 ➜ 采矿"},
            new String[]{"Auctions: \"Storm Search_User123\"","拍卖: \"Storm Search_User123\""},
            new String[]{"Auctions Browser (12/30)","拍卖列表 (12/30)"})) {
            var source=Component.literal("§a"+row[0]);var copy=source.copy();
            var target=GuiTranslationEngine.title(source,manager);assertEquals(row[1],target.getString());assertEquals(copy,source);
            StyledLoreTest.color(target,row[1],ChatFormatting.GREEN);
        }
        for(String text:List.of("Large Chest","My Bazaar Chest","Auction House secrets","Auction House\nUser")) {
            var source=Component.literal(text);assertSame(source,GuiTranslationEngine.title(source,manager));
        }
    }
    @Test void pricesFeesAndTimersUseLiveValuesAndColors() {
        var lines=lore("Auction View","§7Seller: §b[MVP+] Player_123","§7Starting bid: §61,234,567.8 coins",
            "§7Extra fee: §6+25 coins §8(5%)","§7Ends in: §e12h 3m 4s").lines();
        assertEquals(List.of("卖家: [MVP+] Player_123","起拍价: 1,234,567.8 硬币","额外手续费: +25 硬币 (5%)","剩余时间: 12h 3m 4s"),lines.stream().map(Component::getString).toList());
        StyledLoreTest.color(lines.get(0),"[MVP+] Player_123",ChatFormatting.AQUA);
        StyledLoreTest.color(lines.get(1),"1,234,567.8",ChatFormatting.GOLD);StyledLoreTest.color(lines.get(1),"硬币",ChatFormatting.GOLD);
        StyledLoreTest.color(lines.get(2),"5%",ChatFormatting.DARK_GRAY);StyledLoreTest.color(lines.get(3),"12h 3m 4s",ChatFormatting.YELLOW);
    }
    @Test void bazaarProgressAndPricesDoNotUseHardcodedNumbers() {
        var result=lore("Bazaar ➜ Enchanted Diamond","§7Amount: §a71,680x","§7Price per unit: §6123.45 coins",
            "§7Filled: §a35,840§7/§a71,680 §8(50%)","§eClick to create buy order!");
        assertTrue(result.missing().isEmpty(),result.missing().toString());
        assertEquals(List.of("数量: 71,680x","单价: 123.45 硬币","已成交: 35,840/71,680 (50%)","点击创建买单!"),result.lines().stream().map(Component::getString).toList());
        StyledLoreTest.color(result.lines().get(2),"50%",ChatFormatting.DARK_GRAY);
        assertEquals("Seller: Player_123",lore("Bazaar","Seller: Player_123").lines().getFirst().getString());
        assertEquals("Mining",lore("Auction House","Mining").lines().getFirst().getString());
        assertEquals("状态: 已完成!",lore("Bazaar","Status: Filled!").lines().getFirst().getString());
    }
    @Test void selectedFilterArrowAndRarityRemainDistinct() {
        var lines=lore("Auctions Browser","§b▶ Highest Bid","§7 Lowest Bid","§b▶ Legendary").lines();
        assertEquals(List.of("▶ 最高出价"," 最低出价","▶ 传说"),lines.stream().map(Component::getString).toList());
        StyledLoreTest.color(lines.get(0),"▶",ChatFormatting.AQUA);StyledLoreTest.color(lines.get(1),"最低出价",ChatFormatting.GRAY);
    }
    @Test void buttonsRetainClickHoverAndOriginalComponents() {
        var click=new ClickEvent.RunCommand("/original_command_123");var hover=new HoverEvent.ShowText(Component.literal("private original hover"));
        var source=Component.literal("Manage Orders").withStyle(s->s.withColor(ChatFormatting.GREEN).withClickEvent(click).withHoverEvent(hover));
        var before=source.copy();var result=GuiTranslationEngine.button(source,GuiTranslationEngine.menu("Bazaar",manager));
        assertEquals("管理订单",result.getString());
        result.visit((style,text)->{if(!text.isBlank()){assertEquals(click,style.getClickEvent());assertEquals(hover,style.getHoverEvent());}return Optional.empty();},Style.EMPTY);
        assertEquals(before,source);assertSame(source,GuiTranslationEngine.button(source,null));
    }
    @Test void guiOnlyDoesNotTranslateItemStatsOrEnchantments() {
        assertEquals(List.of("Damage: +100","Sharpness V","点击查看!"),lore("SkyBlock Menu","Damage: +100","Sharpness V","Click to view!").lines().stream().map(Component::getString).toList());
    }
    @Test void damagedGuiDictionaryDoesNotReplaceWorkingSnapshot() throws Exception {
        Files.createDirectories(root.resolve("translations"));
        Files.copy(Path.of("src/main/resources/defaults/translations/gui-menus.json"),root.resolve("translations/gui-menus.json"));
        var local=new TranslationManager();local.load(root);
        Files.writeString(root.resolve("translations/gui-menus.json"),"{broken");
        assertThrows(Exception.class,()->local.load(root));
        assertEquals("拍卖行",GuiTranslationEngine.title(Component.literal("Auction House"),local).getString());
    }
    @TestFactory Collection<DynamicTest> publicMenuSamplesKeepDescriptionsAndCommands() throws Exception {
        var tests=new ArrayList<DynamicTest>();
        for(var row:AttachmentTooltipTest.fixtures("gui-tooltip-fixtures.json"))tests.add(DynamicTest.dynamicTest(row.get("menu").getAsString()+" / "+row.get("name").getAsString(),()->{
            var menu=GuiTranslationEngine.menu(row.get("menu").getAsString(),manager);
            var original=Component.literal(row.get("name").getAsString());
            var name=GuiTranslationEngine.button(original,menu);
            assertEquals(row.get("expected").getAsString(),name.getString());
            var source=AttachmentTooltipTest.lines(row);var copy=source.stream().map(Component::copy).toList();
            var result=LoreTranslationEngine.translate(source,manager,null,(s,lines)->List.of(s),false,false,false,menu.paragraphs());
            assertTrue(result.missing().isEmpty(),result.missing().toString());
            assertEquals(copy,source);
        }));return tests;
    }
}
