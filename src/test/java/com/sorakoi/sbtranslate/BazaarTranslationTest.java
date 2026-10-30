package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BazaarTranslationTest {
    static TranslationManager manager;
    @TempDir Path temp;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release() {manager=null;}
    static List<Component> lore(String title,String... lines) {
        var menu=GuiTranslationEngine.menu(title,manager);assertNotNull(menu,title);
        var raw=Arrays.stream(lines).<Component>map(Component::literal).toList();var copy=raw.stream().map(Component::copy).toList();
        var result=LoreTranslationEngine.translate(raw,manager,null,(s,o)->List.of(s),false,false,false,menu.paragraphs(),s->BazaarTranslationEngine.line(s,menu));
        assertEquals(copy,raw);assertTrue(result.missing().isEmpty(),result.missing().toString());return result.lines();
    }
    static String name(String text) {return GuiTranslationEngine.button(Component.literal(text),GuiTranslationEngine.menu("Bazaar",manager)).getString();}
    @Test void gabagoolPreviewNamesKeepPricesAndStyles() {
        for(var pair:List.of(new String[]{"Crude Gabagool","粗制加巴古尔"},new String[]{"Very Crude Gabagool","极粗劣加巴古尔"},new String[]{"Fuel Gabagool","燃料加巴古尔"},new String[]{"Heavy Gabagool","重型加巴古尔"},new String[]{"Hypergolic Gabagool","超燃加巴古尔"},new String[]{"Gabagool Distillate","加巴古尔蒸馏液"},new String[]{"Gabagool the Fish","鱼加巴古尔"})) {
            assertEquals(pair[1],name(pair[0]));
            var raw=Component.literal("§a▸ §7"+pair[0]+" §c303 §7| §a550");var before=raw.copy();
            var result=BazaarTranslationEngine.line(raw,GuiTranslationEngine.menu("Bazaar",manager));
            assertEquals("▸ "+pair[1]+" 303 | 550",result.getString());assertEquals(before,raw);
            StyledLoreTest.color(result,pair[1],ChatFormatting.GRAY);StyledLoreTest.color(result,"303",ChatFormatting.RED);StyledLoreTest.color(result,"550",ChatFormatting.GREEN);
        }
    }
    @Test void nestedNavigationAndGroupNames() {
        assertEquals("红石",name("Redstone"));assertEquals("绿宝石",name("Emerald"));
        for(var row:List.of(new String[]{"Mining ➜ End Stone","采矿 ➜ 末地石"},new String[]{"Sand ➜ Enchanted Sand","沙子 ➜ 附魔沙子"},new String[]{"Bazaar ➜ Mining","集市 ➜ 采矿"})) {
            var source=Component.literal("§7"+row[0]);assertEquals(row[1],GuiTranslationEngine.title(source,manager).getString());
        }
        for(String title:List.of("Player_123 ➜ End Stone","Mining ➜ Player_123","Custom Chest ➜ Storage"))assertNull(GuiTranslationEngine.menu(title,manager));
        assertEquals("集市 ➜ My search",GuiTranslationEngine.title(Component.literal("Bazaar ➜ My search"),manager).getString());
    }
    @Test void latestCategoryDescriptionsAndExplicitItemNames() {
        for(var pair:List.of(new String[]{"Power Stones","能量石"},new String[]{"Reforge Stones","重铸石"},new String[]{"Shards","碎片"},new String[]{"Minion Upgrades","仆从升级"},new String[]{"Minion Storage","仆从储物箱"},new String[]{"Compactors","压缩器"},new String[]{"Slayers","杀手"})) {
            assertEquals(pair[1],name(pair[0]));assertNotNull(GuiTranslationEngine.menu("Oddities ➜ "+pair[0],manager));
        }
        assertEquals(List.of("可在奇术师处学习能量的特殊物品."),lore("Bazaar","Unique items that allow you to learn","Powers at the Thaumaturgist.").stream().map(Component::getString).toList());
        assertEquals(List.of("可为物品赋予铁匠铺无法提供的重铸效果的强大石头."),lore("Bazaar","Powerful stones which allow you to","modify items with reforges not found","at the Blacksmith.").stream().map(Component::getString).toList());
        assertEquals(List.of("可汲取碎片来提升你的属性等级."),lore("Bazaar","Shards can be syphoned to increase","the levels of your Attributes.").stream().map(Component::getString).toList());
        assertEquals(List.of("所有等级的终极附魔.","当前查看中!"),lore("Bazaar","All Ultimate Enchantment levels.","§aCurrently viewing!").stream().map(Component::getString).toList());
        assertEquals(List.of("▸ 仆从储物箱","▸ 压缩器"),lore("Bazaar","§b▸ §7Minion Storage","§b▸ §7Compactors").stream().map(Component::getString).toList());
        for(var row:List.of(new String[]{"VITAMIN_DEATH","Vitamin Death","亡灵维生素"},new String[]{"ECCENTRIC_PAINTING","Eccentric Painting","古怪油画"},new String[]{"ECCENTRIC_PAINTING_BUNDLE","Eccentric Painting Bundle","古怪油画礼包"})) {
            assertEquals(row[2],manager.item(row[0]).name());assertEquals(row[2],name(row[1]));
        }
        for(var pair:List.of(new String[]{"Wolf Slayer","狼杀手","POOCH_SWORD"},new String[]{"Enderman Slayer","末影人杀手","ATOMSPLIT_KATANA"},new String[]{"Blaze Slayer","烈焰人杀手","FIREDUST_DAGGER"})) {
            var header="☠ Requires "+pair[0]+" 6.";
            var result=LoreTranslationEngine.translate(List.of(Component.literal(header)),manager,pair[2],(s,o)->List.of(s),false);
            assertTrue(result.lines().getFirst().getString().contains(pair[1]),result.lines().getFirst().getString());
        }
    }
    @Test void paginatedAndServerTruncatedNavigationEnablesAllMenuRules() {
        for(var row:List.of(
                new String[]{"(1/5) Enchantments ➜ Normal Enc","(1/5) 附魔 ➜ 普通附魔"},
                new String[]{"(5/5) Enchantments ➜ Normal Enchantments","(5/5) 附魔 ➜ 普通附魔"},
                new String[]{"Bane of Arthropods ➜ Bane of Ar","节肢杀手 ➜ 节肢杀手…"},
                new String[]{"Combat ➜ Slayers","战斗 ➜ 杀手"},
                new String[]{"Mining ➜ Glacite Tunnels","采矿 ➜ 冰川矿道"},
                new String[]{"Fossils ➜ Spine Fossil","化石 ➜ "+name("Spine Fossil")})) {
            var title=Component.literal(row[0]).withStyle(ChatFormatting.GRAY);var copy=title.copy();
            assertEquals(row[1],GuiTranslationEngine.title(title,manager).getString());assertEquals(copy,title);
            assertEquals("返回",GuiTranslationEngine.button(Component.literal("§aGo Back"),GuiTranslationEngine.menu(row[0],manager)).getString());
            assertEquals("返回集市",lore(row[0],"§7To Bazaar").getFirst().getString());
        }
        for(String title:List.of("(1/5) Player_123 ➜ Normal Enc","(0/5) Enchantments ➜ Normal Enchantments",
                "Mining ➜ Glacit","Bane of Arthropods ➜ Bane","Mining ➜ Player_123"))assertNull(GuiTranslationEngine.menu(title,manager),title);
    }
    @Test void enchantmentRangeGroupsPreserveTierStylesAndPrices() {
        var title="(1/5) Enchantments ➜ Normal Enc";
        var menu=GuiTranslationEngine.menu(title,manager);
        var source=Component.literal("Chance ").withStyle(ChatFormatting.LIGHT_PURPLE).append(Component.literal("4-5").withStyle(ChatFormatting.BLUE));
        var result=GuiTranslationEngine.button(source,menu);
        assertEquals("机缘 4-5",result.getString());StyledLoreTest.color(result,"机缘",ChatFormatting.LIGHT_PURPLE);StyledLoreTest.color(result,"4-5",ChatFormatting.BLUE);
        assertEquals(List.of("2 种商品","▸ 机缘 IV 58.2k | 209.5k","▸ 机缘 V 55.6M | 67.7M","点击查看商品!"),
                lore(title,"2 products","§f▸ §7Chance IV §c58.2k §7| §a209.5k","§b▸ §7Chance V §c55.6M §7| §a67.7M","Click to view products!").stream().map(Component::getString).toList());
        assertEquals("机缘 4-5 ➜ 机缘 V",GuiTranslationEngine.title(Component.literal("Chance 4-5 ➜ Chance V"),manager).getString());
        for(String unknown:List.of("Chance 5-4","Chance 4-99","Player 4-5")) {
            var raw=Component.literal(unknown);assertSame(raw,BazaarTranslationEngine.line(raw,menu));
        }
        assertEquals("普通附魔",name("Normal Enchantments"));
        assertEquals(List.of("113 种商品","高等级附魔,以及无法在附魔台中获得的附魔.","点击查看商品!"),
                lore("Oddities ➜ Enchantments","113 products","High-level Enchantments and","Enchantments that cannot be found","in the Enchantment Table.","Click to view products!").stream().map(Component::getString).toList());
    }
    @Test void baneOfArthropodsBookDescriptionInTruncatedMenu() {
        var menu=GuiTranslationEngine.menu("Bane of Arthropods ➜ Bane of Ar",manager);assertNotNull(menu);
        var heading=Component.literal("§9Bane of Arthropods VI");
        assertEquals("节肢杀手 VI",GuiTranslationEngine.button(heading,menu).getString());
        var raw=List.<Component>of(Component.literal("§7Increases damage dealt to §c"),Component.literal("§cArthropod §7mobs by §a40%§7."));var before=raw.stream().map(Component::copy).toList();
        var result=LoreTranslationEngine.translate(raw,manager,null,(s,o)->List.of(s),false,false,false,GuiTranslationEngine.tooltipRules(menu,heading,manager),s->BazaarTranslationEngine.line(s,menu));
        assertTrue(result.missing().isEmpty(),result.missing().toString());assertEquals(before,raw);
        assertEquals("对  节肢动物造成的伤害提高 40%.",result.lines().getFirst().getString());
        StyledLoreTest.color(result.lines().getFirst(),"",ChatFormatting.RED);StyledLoreTest.color(result.lines().getFirst(),"节肢动物",ChatFormatting.RED);StyledLoreTest.color(result.lines().getFirst(),"40%",ChatFormatting.GREEN);
    }
    @Test void glaciteFossilAndSlayerScreenshotsTranslateWholeTooltips() {
        var commodity=lore("Mining ➜ Glacite Tunnels","Rare commodity","§7Buy price: §611,141,219 coins","237 in 25 offers","5.1k insta-buys in 7d","§7Sell price: §610,182,017 coins","1.4k in 109 orders","2.3k insta-sells in 7d","§77d Avg. price: §612,153,661 coins","Click to view details!");
        assertEquals(List.of("稀有商品","买入价: 11,141,219 硬币","25 个卖单,共 237 个","7 天内即时买入 5.1k 个","卖出价: 10,182,017 硬币","109 个买单,共 1.4k 个","7 天内即时卖出 2.3k 个","7 天均价: 12,153,661 硬币","点击查看详情!"),commodity.stream().map(Component::getString).toList());
        var fossil=GuiTranslationEngine.menu("Fossils ➜ Spine Fossil",manager);assertNotNull(fossil);
        assertEquals("立即卖出",GuiTranslationEngine.button(Component.literal("Sell Instantly"),fossil).getString());
        assertEquals(List.of(name("Spine Fossil"),"背包中: 无!","单价: 80,002 硬币","背包中没有可出售的物品!"),lore("Fossils ➜ Spine Fossil","Spine Fossil","§7Inventory: §cNone!","§7Price per unit: §680,002 coins","None to sell in your inventory!").stream().map(Component::getString).toList());
        assertEquals("狼蛛育母",GuiTranslationEngine.button(Component.literal("Tarantula Broodfather"),GuiTranslationEngine.menu("Combat ➜ Slayers",manager)).getString());
        var products=List.of("Tarantula Web","Tarantula Silk","Spider Catalyst","Tarantula Catalyst","Fly Swatter","Toxic Arrow Poison");
        var prices=List.of("1k | 1.2k","223.9k | 231.4k","50k | 160k","42.5k | 83.6k","472.7k | 501.5k","1.9k | 2.3k");
        var input=new ArrayList<String>();input.add("6 products");
        for(int i=0;i<products.size();i++)input.add("▸ "+products.get(i)+" "+prices.get(i));
        input.add("Click to view products!");
        var rows=lore("Combat ➜ Slayers",input.toArray(String[]::new));assertEquals(8,rows.size());
        for(int i=0;i<products.size();i++) {
            assertNotEquals(products.get(i),name(products.get(i)));
            assertEquals("▸ "+name(products.get(i))+" "+prices.get(i),rows.get(i+1).getString());
        }
    }
    @Test void productPreviewRowsKeepBothPricesAndBulletColors() {
        var result=lore("Bazaar ➜ Mining","§83 products","§f▸ §7Emerald §c1 §7| §a6","§a▸ §7Enchanted Emerald §c731 §7| §a971","§b▸ §7Enchanted Emerald Block §c101k §7| §a103.3k","§eClick to view products!");
        assertEquals(List.of("3 种商品","▸ 绿宝石 1 | 6","▸ 附魔绿宝石 731 | 971","▸ 附魔绿宝石块 101k | 103.3k","点击查看商品!"),result.stream().map(Component::getString).toList());
        StyledLoreTest.color(result.get(3),"▸",ChatFormatting.AQUA);StyledLoreTest.color(result.get(3),"101k",ChatFormatting.RED);StyledLoreTest.color(result.get(3),"103.3k",ChatFormatting.GREEN);StyledLoreTest.color(result.get(3),"附魔绿宝石块",ChatFormatting.GRAY);
        assertEquals("▸ 附魔红石粉 629 | 696",lore("Bazaar","§a▸ §7Enchanted Redstone Dust §c629 §7| §a696").getFirst().getString());
        assertEquals("▸ 附魔红石块 24.9k | 25.4k",lore("Bazaar","§b▸ §7Enchanted Redstone Block §c24.9k §7| §a25.4k").getFirst().getString());
    }
    @Test void settingsSearchAndEmptyOrders() {
        assertEquals("集市设置",name("Bazaar Settings"));assertEquals("管理订单",name("Manage Orders"));assertEquals("创建买单",name("Create Buy Order"));
        assertEquals(List.of("查看和修改你的集市设置.","点击查看!"),lore("Bazaar","View and edit your Bazaar settings.","Click to view!").stream().map(Component::getString).toList());
        assertEquals(List.of("按名称搜索商品!","点击搜索!"),lore("Bazaar","Find products by name!","Click to search!").stream().map(Component::getString).toList());
        assertEquals(List.of("你目前没有这些商品的未完成订单。","点击管理!"),lore("Mining ➜ End Stone","You don't have any ongoing orders","for these products.","Click to manage!").stream().map(Component::getString).toList());
    }
    @Test void sellSummaryUsesExistingItemNamesAndPreservesGemIcon() {
        var result=lore("Bazaar","§7Instantly sell any items in your","§7inventory that can be sold on the","§7Bazaar.","§a1x §6Recombobulator 3000 §7for §69,051,712 coins","§a1x §5Potato Spreading §7for §6225,354 coins","§a1x §9 Fine Sapphire Gemstone §7for §626,774 coins","§a2x §9Dragon Scale §7for §63,200.1 coins","§7You earn: §69,202,336 coins");
        assertTrue(result.get(0).getString().contains("立即出售背包"));
        for(int i=1;i<=4;i++)assertFalse(result.get(i).getString().contains("for"));
        assertTrue(result.get(1).getString().contains(manager.item("RECOMBOBULATOR_3000").name()));
        assertTrue(result.get(3).getString().contains(manager.item("FINE_SAPPHIRE_GEM").name()));
        StyledLoreTest.color(result.get(3),"",ChatFormatting.BLUE);StyledLoreTest.color(result.get(3),"26,774",ChatFormatting.GOLD);StyledLoreTest.color(result.get(3),"硬币",ChatFormatting.GOLD);StyledLoreTest.color(result.get(3),"1x",ChatFormatting.GREEN);
        assertEquals("你将获得: 9,202,336 硬币",result.getLast().getString());
    }
    @Test void commodityVolumeAndPriceAbbreviations() {
        var result=lore("Mining ➜ End Stone","Common commodity","§7Buy price: §63.2 coins","2.3M in 92 offers","2M insta-buys in 7d","§7Sell price: §60.1 coins","24.6k in 1 order","25.9M insta-sells in 7d","§77d Avg. price: §69.8 coins","Click to view details!");
        assertEquals(List.of("常见商品","买入价: 3.2 硬币","92 个卖单,共 2.3M 个","7 天内即时买入 2M 个","卖出价: 0.1 硬币","1 个买单,共 24.6k 个","7 天内即时卖出 25.9M 个","7 天均价: 9.8 硬币","点击查看详情!"),result.stream().map(Component::getString).toList());
        assertEquals("买入价: 9.2M 硬币",lore("Bazaar","§7Buy price: §69.2M coins").getFirst().getString());
    }
    @Test void orderBookAllSevenLiveRowsAndProductHeading() {
        assertEquals("附魔沙子",lore("Sand ➜ Enchanted Sand","§8Enchanted Sand").getFirst().getString());
        for(var values:List.of(new String[]{"500.0","26,958"},new String[]{"442.4","12,136"},new String[]{"442.0","357"},new String[]{"401.0","50,000"},new String[]{"217.5","128"},new String[]{"160.0","4,768"},new String[]{"137.0","1,024"})) {
            var line=lore("Sand ➜ Enchanted Sand","§7- §6"+values[0]+" coins §7each | §a"+values[1]+"x §7in 1 order").getFirst();
            assertEquals("- 单价 "+values[0]+" 硬币 | 1 个买单,共 "+values[1]+"x",line.getString());
            StyledLoreTest.color(line,values[0],ChatFormatting.GOLD);StyledLoreTest.color(line,values[1]+"x",ChatFormatting.GREEN);StyledLoreTest.color(line,"硬币",ChatFormatting.GOLD);
        }
        assertEquals(List.of("最高买单:","点击设置买单!"),lore("Sand ➜ Enchanted Sand","Top Orders:","Click to setup Buy Order!").stream().map(Component::getString).toList());
    }
    @Test void unknownMapAndUnrelatedTextStayUntouched() {
        var menu=GuiTranslationEngine.menu("Bazaar",manager);
        for(var raw:List.<Component>of(Component.literal("未知地图"),Component.literal("Player Emerald"),Component.literal("Find Emerald in my chest"),Component.translatable("filled_map.unknown")))assertSame(raw,BazaarTranslationEngine.line(raw,menu));
        var raw=Component.literal("Emerald");assertSame(raw,BazaarTranslationEngine.line(raw,null));assertSame(raw,BazaarTranslationEngine.line(raw,GuiTranslationEngine.menu("Auction House",manager)));
    }
    @Test void ambiguityAndReloadAreSafe() throws Exception {
        var dir=temp.resolve("translations/items");Files.createDirectories(dir);
        Files.copy(Path.of("src/main/resources/defaults/translations/gui-menus.json"),temp.resolve("translations/gui-menus.json"));
        Files.writeString(dir.resolve("A.json"),"{\"id\":\"A\",\"_original_name\":\"Ambiguous Product\",\"name\":\"译名甲\"}");
        Files.writeString(dir.resolve("B.json"),"{\"id\":\"B\",\"_original_name\":\"Ambiguous Product\",\"name\":\"译名乙\"}");
        var local=new TranslationManager();local.load(temp);var raw=Component.literal("Ambiguous Product");
        assertSame(raw,GuiTranslationEngine.button(raw,GuiTranslationEngine.menu("Bazaar",local)));
        Files.writeString(dir.resolve("B.json"),"{\"id\":\"B\",\"_original_name\":\"Ambiguous Product\",\"name\":\"译名甲\"}");local.load(temp);
        assertEquals("译名甲",GuiTranslationEngine.button(raw,GuiTranslationEngine.menu("Bazaar",local)).getString());
    }
    @Test void showOriginalRetainsRawProductRowOnce() {
        var menu=GuiTranslationEngine.menu("Bazaar",manager);var source=Component.literal("§a▸ §7Emerald §c1 §7| §a6");
        var result=LoreTranslationEngine.translate(List.of(source),manager,null,(s,o)->List.of(s),true,false,false,menu.paragraphs(),s->BazaarTranslationEngine.line(s,menu));
        assertEquals(2,result.lines().size());assertSame(source,result.lines().get(1));
    }
    @Test void sellOfferSetupScreenshot() {
        assertEquals("与最低卖价相同",name("Same as Best Offer"));
        var lines=lore("At what price are you selling?","§8Sell Offer Setup","§7Use the same price as the lowest","§7Sell Offer for this item.","§7Selling: §a1x","§7Unit price: §69,141,131.8 coins","§7Total: §69,038,294 coins","§eClick to proceed!");
        assertEquals(List.of("卖单设置","按此物品当前最低卖价挂单。","出售数量: 1x","单价: 9,141,131.8 硬币","合计: 9,038,294 硬币","点击继续!"),lines.stream().map(Component::getString).toList());
        StyledLoreTest.color(lines.get(2),"1x",ChatFormatting.GREEN);StyledLoreTest.color(lines.get(3),"9,141,131.8",ChatFormatting.GOLD);
    }
    @Test void everyKnownEnchantmentBookHasBazaarLabelsAndNavigation() {
        int count=0;
        for(var enchant:manager.scope("enchantment-ids").entrySet()) {
            String translated=manager.enchantment(enchant.getKey());if(translated==null)continue;
            assertEquals(translated,name(enchant.getKey()));
            for(int level=1;level<=100;level++)if(manager.item(enchant.getValue()+";"+level)!=null) {
                count++;String english=enchant.getKey()+" "+level;
                assertEquals(translated+" "+level,name(english));
                assertEquals(translated+" ➜ "+translated+" "+level,GuiTranslationEngine.title(Component.literal(enchant.getKey()+" ➜ "+english),manager).getString());
            }
        }
        assertTrue(count>500,"complete book catalog checked: "+count);
        assertEquals("炼狱 ➜ 炼狱 I",GuiTranslationEngine.title(Component.literal("Inferno ➜ Inferno I"),manager).getString());
        var heading=Component.literal("§d§lInferno §9I");var translated=GuiTranslationEngine.button(heading,GuiTranslationEngine.menu("Inferno ➜ Inferno I",manager));
        StyledLoreTest.color(translated,"炼狱",ChatFormatting.LIGHT_PURPLE);StyledLoreTest.color(translated,"I",ChatFormatting.BLUE);assertTrue(StyledLoreTest.styleAt(translated,"炼狱").isBold());
    }
    @Test void infernoLiveDescriptionAndExistingBookDescriptions() {
        var lines=lore("Inferno ➜ Inferno I","§7Every §c10§7th hit on a mob traps it for","§c5s §7and deals §c1.25x §7of that hit's","§7damage over the trap duration.");
        assertEquals("每第 10 次命中生物将其困住 5s,并在此期间造成该次命中伤害的 1.25x.",lines.getFirst().getString());
        for(String value:List.of("10","5s","1.25x"))StyledLoreTest.color(lines.getFirst(),value,ChatFormatting.RED);
        var menu=GuiTranslationEngine.menu("Inferno ➜ Inferno I",manager);
        var rules=GuiTranslationEngine.tooltipRules(menu,Component.literal("Inferno I"),manager);
        var old=List.<Component>of(Component.literal("Every 10th hit on a mob"),Component.literal("spawns an inferno which traps"),Component.literal("the mob and deals 125% of"),Component.literal("your last hit."));
        var result=LoreTranslationEngine.translate(old,manager,null,(s,o)->List.of(s),false,false,false,rules,s->BazaarTranslationEngine.line(s,menu));
        assertTrue(result.missing().isEmpty());assertTrue(result.lines().getFirst().getString().contains("炼狱"));
        assertSame(menu.paragraphs(),GuiTranslationEngine.tooltipRules(menu,Component.literal("Player Inferno I"),manager));
    }
    @Test void bazaarChatKeepsPricesEnchantmentLevelsAndEvents() {
        assertEquals("[集市] 正在托管物品...",BazaarTranslationEngine.systemMessage(Component.literal("§6[Bazaar] §7Putting goods in escrow..."),manager).getString());
        assertEquals("[集市] 正在提交卖单...",BazaarTranslationEngine.systemMessage(Component.literal("§6[Bazaar] §7Submitting sell offer..."),manager).getString());
        var click=new ClickEvent.RunCommand("/bz");
        var source=Component.literal("§6[Bazaar] §eSell Offer Setup! §a4x §fVicious III §7for §691,359,448 coins.").withStyle(s->s.withClickEvent(click));var copy=source.copy();
        var target=BazaarTranslationEngine.systemMessage(source,manager);
        assertEquals("[集市] 卖单已创建! 4x 凶残 III,总价 91,359,448 硬币.",target.getString());
        assertEquals(copy,source);StyledLoreTest.color(target,"集市",ChatFormatting.GOLD);StyledLoreTest.color(target,"卖单已创建!",ChatFormatting.YELLOW);StyledLoreTest.color(target,"4x",ChatFormatting.GREEN);StyledLoreTest.color(target,"凶残",ChatFormatting.WHITE);StyledLoreTest.color(target,"91,359,448",ChatFormatting.GOLD);assertEquals(click,StyledLoreTest.styleAt(target,"凶残").getClickEvent());
        assertEquals("[集市] 卖单已创建! 1x 反击 V,总价 49,519,918 硬币.",BazaarTranslationEngine.systemMessage(Component.literal("§6[Bazaar] §eSell Offer Setup! §a1x Counter-Strike V §7for §649,519,918 coins."),manager).getString());
        for(String unknown:List.of("Player: [Bazaar] Putting goods in escrow...","[Bazaar] Player private message","[Bazaar] Sell Offer Setup! words Vicious III for secret coins.")) {var raw=Component.literal(unknown);assertSame(raw,BazaarTranslationEngine.systemMessage(raw,manager));}
    }
    @Test void filledOrdersClaimsRefundsAndLimitsTranslate() {
        var cases=List.of(
            new String[]{"[Bazaar] Your Sell Offer for 32x Seeds was filled!","[集市] 你的 32x 种子 卖单已全部成交!"},
            new String[]{"[Bazaar] Your Buy Order for 1,024x Seeds was filled!","[集市] 你的 1,024x 种子 买单已全部成交!"},
            new String[]{"[Bazaar] Sold 32x Seeds for 318.5 coins!","[集市] 已出售 32x 种子,获得 318.5 硬币!"},
            new String[]{"[Bazaar] Order Flipped! 5x Seeds for 13.0 coins of total expected profit.","[集市] 已转为卖单! 5x 种子,预计总利润 13.0 硬币."},
            new String[]{"[Bazaar] Claimed 24x Seeds worth 4.8M coins bought for 202k each!","[集市] 已领取 24 个 种子，总价 4.8M 硬币（买入单价 202k 硬币）！"},
            new String[]{"[Bazaar] Claimed 34,236,799 coins from selling 176x Seeds at 196,741 each!","[集市] 已领取出售 176 个 种子 所得的 34,236,799 硬币（卖出单价 196,741 硬币）！"},
            new String[]{"[Bazaar] Cancelled! Refunded 12,345 coins from cancelling Buy Order!","[集市] 已取消买单! 已退回 12,345 硬币!"},
            new String[]{"[Bazaar] There was an error cancelling your order!","[集市] 取消订单时发生错误!"},
            new String[]{"[Bazaar] Couldn't match the quoted price!","[集市] 无法按报价成交!"},
            new String[]{"[Bazaar] Claiming order...","[集市] 正在领取订单所得..."},
            new String[]{"You have reached your maximum of 14 Bazaar orders!","你已达到 14 个集市订单的上限!"},
            new String[]{"You reached the daily limit of coins you may create orders for on the Bazaar!","你已达到今日集市下单金额上限!"});
        for(var pair:cases) {
            var source=Component.literal(pair[0]);var before=source.copy();
            assertEquals(pair[1],BazaarTranslationEngine.systemMessage(source,manager).getString());assertEquals(before,source);
            var player=Component.literal("Player: "+pair[0]);assertSame(player,BazaarTranslationEngine.systemMessage(player,manager));
        }
        for(String invalid:List.of("[Bazaar] Your Sell Offer for words Seeds was filled!","[Bazaar] Claimed 24x Seeds worth secret coins bought for 202k each!","You have reached your maximum of secret Bazaar orders!","[Bazaar] Unknown future notice!")) {
            var source=Component.literal(invalid);assertSame(source,BazaarTranslationEngine.systemMessage(source,manager));
        }
        assertEquals("正在领取订单所得...",TextTranslationEngine.translate(Component.literal("Claiming order..."),s->manager.match("chat",null,s)).getString());
        assertEquals("市场价格波动较大!",TextTranslationEngine.translate(Component.literal("Seems like a volatile market!"),s->manager.match("chat",null,s)).getString());
    }
    @Test void filledScreenshotAndClaimReorderingKeepColorsAndClickEvents() {
        var click=new ClickEvent.RunCommand("/bz");
        var raw=Component.literal("§6[Bazaar] §eYour Sell Offer for §a32x §fSeeds §ewas filled!").withStyle(s->s.withClickEvent(click));var before=raw.copy();
        var result=BazaarTranslationEngine.systemMessage(raw,manager);
        assertEquals("[集市] 你的 32x 种子 卖单已全部成交!",result.getString());assertEquals(before,raw);
        StyledLoreTest.color(result,"集市",ChatFormatting.GOLD);StyledLoreTest.color(result,"32x",ChatFormatting.GREEN);StyledLoreTest.color(result,"种子",ChatFormatting.WHITE);StyledLoreTest.color(result,"已全部成交!",ChatFormatting.YELLOW);
        assertEquals(click,StyledLoreTest.styleAt(result,"种子").getClickEvent());
        raw=Component.literal("§6[Bazaar] §7Claimed §634,236,799 §7coins from selling §a176x §fSeeds §7at §6196,741 §7each!");before=raw.copy();
        result=BazaarTranslationEngine.systemMessage(raw,manager);
        assertEquals("[集市] 已领取出售 176 个 种子 所得的 34,236,799 硬币（卖出单价 196,741 硬币）！",result.getString());
        StyledLoreTest.color(result,"34,236,799",ChatFormatting.GOLD);StyledLoreTest.color(result,"176",ChatFormatting.GREEN);StyledLoreTest.color(result,"个",ChatFormatting.GREEN);StyledLoreTest.color(result,"种子",ChatFormatting.WHITE);StyledLoreTest.color(result,"196,741",ChatFormatting.GOLD);assertEquals(before,raw);
    }
    @Test void filledOrderWithSeparatelyColoredOrderTypeAndQuantitySuffix() {
        var click=new ClickEvent.RunCommand("/bz");
        for(String type:List.of("Buy Order","Sell Offer"))for(String item:List.of("Iron Ingot","铁锭")) {
            String order=type.equals("Buy Order")?"买单":"卖单";
            var raw=Component.literal("§6[Bazaar] §eYour §a"+type+" §efor §a64§7x §f"+item+" §ewas filled!").withStyle(s->s.withClickEvent(click));
            var before=raw.copy();var result=BazaarTranslationEngine.systemMessage(raw,manager);
            assertEquals("[集市] 你的 64x 铁锭 "+order+"已全部成交!",result.getString());
            StyledLoreTest.color(result,order,ChatFormatting.GREEN);
            StyledLoreTest.color(result,"64",ChatFormatting.GREEN);
            StyledLoreTest.color(result,"x",ChatFormatting.GRAY);
            StyledLoreTest.color(result,"铁锭",ChatFormatting.WHITE);
            StyledLoreTest.color(result,"已全部成交!",ChatFormatting.YELLOW);
            assertEquals(click,StyledLoreTest.styleAt(result,order).getClickEvent());assertEquals(before,raw);
        }
    }
    @Test void glossyGemstoneCurrentServerLoreKeepsSemanticColors() {
        var source=List.<Component>of(Component.literal("§7Drops rarely from §dGemstones §7while"),Component.literal("§bMayor Cole's §6Mining Fiesta §7is active."));
        var result=LoreTranslationEngine.translate(source,manager,"GLOSSY_GEMSTONE",(s,o)->List.of(s),false);
        assertTrue(result.missing().isEmpty(),result.missing().toString());assertEquals("在 Cole 市长的采矿狂欢期间,挖掘宝石有较低概率掉落.",result.lines().getFirst().getString());
        StyledLoreTest.color(result.lines().getFirst(),"Cole 市长的",ChatFormatting.AQUA);StyledLoreTest.color(result.lines().getFirst(),"采矿狂欢",ChatFormatting.GOLD);StyledLoreTest.color(result.lines().getFirst(),"宝石",ChatFormatting.LIGHT_PURPLE);
    }
    @Test void cancelledSellOfferRefundScreenshotKeepsLiveQuantityAndColors() {
        var pending=Component.literal("§6[Bazaar] §7Cancelling order...");
        var translated=BazaarTranslationEngine.systemMessage(pending,manager);
        assertEquals("[集市] 正在取消订单...",translated.getString());
        StyledLoreTest.color(translated,"集市",ChatFormatting.GOLD);
        StyledLoreTest.color(translated,"正在取消订单",ChatFormatting.GRAY);
        for(String amount:List.of("4","1","1,024")) {
            var click=new ClickEvent.RunCommand("/bz");
            var raw=Component.literal("§6[Bazaar] §cCancelled! §7Refunded §a"+amount+"x §fVicious III §7from cancelling Sell Offer!").withStyle(s->s.withClickEvent(click));
            var before=raw.copy();var result=BazaarTranslationEngine.systemMessage(raw,manager);
            assertEquals("[集市] 已取消卖单! 已退回 "+amount+"x 凶残 III!",result.getString());
            assertEquals(before,raw);
            StyledLoreTest.color(result,"已取消卖单!",ChatFormatting.RED);
            StyledLoreTest.color(result,"已退回",ChatFormatting.GRAY);
            StyledLoreTest.color(result,amount+"x",ChatFormatting.GREEN);
            StyledLoreTest.color(result,"凶残 III",ChatFormatting.WHITE);
            assertEquals(click,StyledLoreTest.styleAt(result,"凶残").getClickEvent());
        }
        var unknown=Component.literal("Player: [Bazaar] Cancelling order...");
        assertSame(unknown,BazaarTranslationEngine.systemMessage(unknown,manager));
    }
    @Test void customTitlesAndOptionalChatStylesRemainSupported() throws Exception {
        Files.createDirectories(temp.resolve("translations"));
        Files.writeString(temp.resolve("translations/gui-menus.json"),"""
            {"schemaVersion":1,"menus":[{"id":"bazaar","titles":{"Mining ➜ End Stone":"我的菜单"},
            "labels":{"Mining":"采矿","End Stone":"末地石"},"loreParagraphs":[
            {"source":["[Bazaar] Putting goods in escrow..."],"translation":"[集市] 正在托管物品..."}]}]}
            """);
        var local=new TranslationManager();local.load(temp);
        assertEquals("我的菜单",GuiTranslationEngine.title(Component.literal("Mining ➜ End Stone"),local).getString());
        assertEquals("[集市] 正在托管物品...",BazaarTranslationEngine.systemMessage(Component.literal("[Bazaar] Putting goods in escrow..."),local).getString());
    }
    @Test void woodGroupsAndInstantBuyNavigation() {
        for(var row:List.of(new String[]{"Woods & Fishes ➜ Wood","木材与鱼类 ➜ 木材"},
                new String[]{"Wood ➜ Spruce","木材 ➜ 云杉"},
                new String[]{"Spruce ➜ Spruce Log","云杉 ➜ 云杉原木"},
                new String[]{"Spruce Log ➜ Instant Buy","云杉原木 ➜ 立即购买"},
                new String[]{"Bazaar ➜ Settings","集市 ➜ 设置"})) {
            assertNotNull(GuiTranslationEngine.menu(row[0],manager));
            assertEquals(row[1],GuiTranslationEngine.title(Component.literal(row[0]),manager).getString());
        }
        var rows=lore("Bazaar ➜ Woods & Fishes","§a▸ §7Oak","§b▸ §7Spruce","§f▸ §7Helix §c22k §7| §a38.5k");
        assertEquals(List.of("▸ 橡木","▸ 云杉","▸ 螺旋木 22k | 38.5k"),rows.stream().map(Component::getString).toList());
        StyledLoreTest.color(rows.getFirst(),"▸",ChatFormatting.GREEN);
        StyledLoreTest.color(rows.getLast(),"22k",ChatFormatting.RED);
        for(String group:List.of("Fish","Cod","Salmon","Flowers","Baits","Mob Drops","Lava Fishing","Water Orbs","Chum Fishing")) {
            assertNotEquals(group,name(group));
            assertNotNull(GuiTranslationEngine.menu("Woods & Fishes ➜ "+group,manager));
            assertFalse(GuiTranslationEngine.title(Component.literal("Woods & Fishes ➜ "+group),manager).getString().contains(group));
        }
    }
    @Test void unknownCategoryRowsCannotBeJoinedIntoAPriceRow() {
        var menu=GuiTranslationEngine.menu("Bazaar",manager);
        var source=List.<Component>of(Component.literal("§a▸ §7Future Wood"),Component.literal("§b▸ §7Other Wood"),Component.literal("§f▸ §7Unknown Wood §c22k §7| §a38.5k"));
        var result=LoreTranslationEngine.translate(source,manager,null,(s,o)->List.of(s),false,false,false,menu.paragraphs(),s->BazaarTranslationEngine.line(s,menu));
        assertEquals(source,result.lines());assertFalse(result.changed());
    }
    @Test void instantPurchaseChatScreenshotKeepsNamesPricesAndStyles() {
        assertEquals("[集市] 正在买入物品...",BazaarTranslationEngine.systemMessage(Component.literal("§6[Bazaar] §7Executing instant buy..."),manager).getString());
        for(String amount:List.of("1","64","1,024")) {
            var source=Component.literal("§6[Bazaar] §7Bought §a"+amount+"x §fOak Log §7for §631.8 coins!");var copy=source.copy();
            var result=BazaarTranslationEngine.systemMessage(source,manager);
            assertEquals("[集市] 已购买 "+amount+"x 橡木原木,花费 31.8 硬币!",result.getString());
            assertEquals(copy,source);StyledLoreTest.color(result,"集市",ChatFormatting.GOLD);
            StyledLoreTest.color(result,amount+"x",ChatFormatting.GREEN);StyledLoreTest.color(result,"橡木原木",ChatFormatting.WHITE);
            StyledLoreTest.color(result,"31.8",ChatFormatting.GOLD);StyledLoreTest.color(result,"硬币",ChatFormatting.GOLD);
        }
        var player=Component.literal("Player: [Bazaar] Bought 1x Oak Log for 31.8 coins!");
        assertSame(player,BazaarTranslationEngine.systemMessage(player,manager));
    }
    @Test void livePurchaseButtonsAndSettingsDescriptions() {
        var rows=lore("Spruce Log ➜ Instant Buy","§aBuy only §eone§a!","§7Amount: §a1x","§7Price: §629.1 coins","§eClick to buy now!");
        assertEquals(List.of("只购买一个!","数量: 1x","价格: 29.1 硬币","点击立即购买!"),rows.stream().map(Component::getString).toList());
        StyledLoreTest.color(rows.getFirst(),"一个",ChatFormatting.YELLOW);
        var button=GuiTranslationEngine.button(Component.literal("Buy only ").withStyle(ChatFormatting.GREEN).append(Component.literal("one").withStyle(ChatFormatting.YELLOW)).append(Component.literal("!").withStyle(ChatFormatting.GREEN)),GuiTranslationEngine.menu("Spruce Log ➜ Instant Buy",manager));
        assertEquals("只购买一个!",button.getString());StyledLoreTest.color(button,"一个",ChatFormatting.YELLOW);
        assertEquals(List.of("装满我的背包!","买单数量","最多购买 71,680x.","点击填写!"),lore("Spruce Log ➜ Instant Buy","Fill my inventory!","Buy Order Quantity","Buy up to 71,680x.","Click to specify!").stream().map(Component::getString).toList());
        assertEquals("购买一组!",name("Buy a stack!"));
        var inventory=lore("Spruce ➜ Spruce Log","§7Inventory: §cNone!","§7Stack price: §61,792 coins","§8None to sell in your inventory!");
        assertEquals(List.of("背包中: 无!","整组价格: 1,792 硬币","背包中没有可出售的物品!"),inventory.stream().map(Component::getString).toList());
        StyledLoreTest.color(inventory.getFirst(),"无!",ChatFormatting.RED);
        var settings=lore("Bazaar ➜ Settings","Instasell Ignore List","Manage products which are ignored","from bulk instasells.","§7Ignored: §aNone!","Find the instasell ignore toggle on","each individual product menu.");
        assertEquals(List.of("批量出售排除列表","管理批量出售时跳过的商品.","已排除: 无!","可在各商品页面设置是否排除批量出售."),settings.stream().map(Component::getString).toList());
        StyledLoreTest.color(settings.get(2),"无!",ChatFormatting.GREEN);
        var advanced=lore("Bazaar ➜ Settings","Bazaar View","View prices, orders volume, recent","transactions and graphs for each","product.","Front page data accounts for","enchanted recipes.","Click to toggle view!");
        assertEquals(List.of("集市显示模式","查看各商品的价格、订单量、近期成交和交易图表.","首页数据已考虑附魔物品的合成配方.","点击切换显示模式!"),advanced.stream().map(Component::getString).toList());
        var product=lore("Spruce ➜ Spruce Log","Instasell Ignore","Toggle whether this product is","ignored when bulk instaselling from","your inventory or sacks.","§7Bulk Instasell: §aAllowed!","Click to toggle!");
        assertEquals(List.of("批量出售排除设置","设置批量出售背包或储物袋物品时,是否跳过此商品.","批量出售: 允许!","点击切换!"),product.stream().map(Component::getString).toList());
        StyledLoreTest.color(product.get(2),"允许!",ChatFormatting.GREEN);
    }
}
