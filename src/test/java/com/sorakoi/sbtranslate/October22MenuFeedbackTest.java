package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class October22MenuFeedbackTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;}
    static List<Component> menu(String title,String... raw) {
        var context=GuiTranslationEngine.menu(title,manager);assertNotNull(context,title);
        var lines=Arrays.stream(raw).<Component>map(Component::literal).toList();var before=lines.stream().map(Component::copy).toList();
        var result=LoreTranslationEngine.translate(lines,manager,null,(s,o)->List.of(s),false,false,false,context.paragraphs(),
            s->GuiTranslationEngine.detailLine(s,context,manager),s->CommunityTranslationEngine.translate(s,"tooltip",manager),
            s->manager.community().candidates("tooltip",TranslationManager.plain(s.getString())));
        assertEquals(before,lines);return result.lines();
    }
    @Test void vanillaNamesAndSeaLumiesAgreeAcrossDisplayContexts() throws Exception {
        var audit=TranslationManager.readObject(Path.of("docs/vanilla-name-audit.json"));
        for(var entry:audit.getAsJsonArray("differences")) {
            var row=entry.getAsJsonObject();String id=row.get("id").getAsString(),name=row.get("new").getAsString();
            var item=manager.item(id);assertEquals(name,item.name(),id);
            var source=Component.literal(item.originalName()).withStyle(ChatFormatting.GREEN);
            var out=TextTranslationEngine.itemName(source,item.originalName(),item.name(),manager::reforge,item.styleMappings());
            assertEquals(name,out.getString(),id);StyledLoreTest.color(out,name,ChatFormatting.GREEN);
        }
        for(String en:List.of("Sea Lumies","Enchanted Sea Lumies","Mangrove Log","Enchanted Mangrove Log")) {
            String expected=Map.of("Sea Lumies","海泡菜","Enchanted Sea Lumies","附魔海泡菜","Mangrove Log","红树原木","Enchanted Mangrove Log","附魔红树原木").get(en);
            assertEquals(expected,manager.translatedName(en));
            assertEquals(expected,GuiTranslationEngine.button(Component.literal(en),GuiTranslationEngine.menu("Bazaar",manager)).getString());
        }
        var source=List.<Component>of(Component.literal("§7Grants a §a+10%§7 chance to drop"),Component.literal("§7double Berries and Sea Lumies."));
        var line=LoreTranslationEngine.translate(source,manager,"ATTRIBUTE_SHARD_YUMMY;1",(s,o)->List.of(s),false).lines().getFirst();
        assertEquals("有 +10% 概率掉落双倍浆果和海泡菜.",line.getString());StyledLoreTest.color(line,"+10%",ChatFormatting.GREEN);
        for(int n=1;n<=5;n++)assertTrue(manager.item("SCUBA;"+n).paragraphs().stream().anyMatch(p->p.translation().contains("海泡菜收藏")));
        assertEquals("海光 (动物)",manager.item("SEASHINE_ANIMAL").name());
        // The standalone Brick correction must not rewrite longer block names.
        for(var entry:Map.of("NETHER_BRICK","下界砖块","NETHER_BRICK_STAIRS","下界砖楼梯",
                "SMOOTH_STAIRS","石砖楼梯","STEP-5","石砖台阶","STEP-6","下界砖台阶").entrySet())
            assertEquals(entry.getValue(),manager.item(entry.getKey()).name());
    }
    @Test void everyPetItemClassificationIsDiscoverableEvenWithoutIdPrefix() throws Exception {
        var index=TranslationManager.readObject(Path.of("src/main/resources/defaults/neu-index.json"));int checked=0;
        for(var entry:index.getAsJsonObject("items").entrySet()) {
            var row=entry.getValue().getAsJsonObject();boolean pet=false;
            for(var line:row.getAsJsonArray("lore"))if(line.getAsString().matches(".* PET ITEM"))pet=true;
            if(!pet)continue;
            var found=manager.petItem(row.get("name").getAsString());assertNotNull(found,entry.getKey());checked++;
        }
        assertTrue(checked>30);assertNull(manager.petItem("Diamond Sword"));
    }
    @Test void equippedPetItemsFromScreenshotsKeepNamesEffectsAndColors() {
        var cases=List.of(
            new String[]{"Antique Remedies","§7Increases the pet's §c Strength §7by","§a80%§7."},
            new String[]{"Washed-up Souvenir","§7Grants §3+5 Sea Creature Chance§7."},
            new String[]{"Minos Relic","§7Increases all pet stats by §a33.3%§7."});
        for(var row:cases) {
            var lines=new ArrayList<Component>();lines.add(Component.literal("§6Held Item: §5"+row[0]));
            for(int i=1;i<row.length;i++)lines.add(Component.literal(row[i]));
            var before=lines.stream().map(Component::copy).toList();
            var result=LoreTranslationEngine.translate(lines,manager,"GLACITE_GOLEM;4",(s,o)->List.of(s),false);
            assertEquals("携带物品: "+manager.petItem(row[0]).name(),result.lines().getFirst().getString());
            StyledLoreTest.color(result.lines().getFirst(),manager.petItem(row[0]).name(),ChatFormatting.DARK_PURPLE);
            assertEquals(before,lines);assertTrue(result.lines().stream().noneMatch(s->s.getString().matches(".*[A-Za-z].*")),result.lines().toString());
            var effect=result.lines().getLast();
            if(row[0].equals("Antique Remedies")){StyledLoreTest.color(effect,"80%",ChatFormatting.GREEN);StyledLoreTest.color(effect,"力量",ChatFormatting.RED);}
            if(row[0].equals("Minos Relic"))StyledLoreTest.color(effect,"33.3%",ChatFormatting.GREEN);
        }
    }
    @Test void auctionScreenshotParagraphsRetainDynamicValues() {
        var out=menu("Co-op Auction House","§7Find items for sale by players","§7across Hypixel SkyBlock!","",
            "§7Items offered here are for §6auction§7,","§7meaning you have to place the top","§7bid to acquire them!","",
            "§7You own §e2 auctions §7in progress or","§7which recently ended.","","§bWith 0 auctions from co-op!","",
            "§7Your auctions have §a12 bids §7totaling","§613,440,888 coins§7.");
        assertFalse(out.stream().anyMatch(s->s.getString().contains("Find items") || s.getString().contains("Your auctions") || s.getString().contains("You own")),out.toString());
        var bid=out.stream().filter(s->s.getString().contains("13,440,888")).findFirst().orElseThrow();
        StyledLoreTest.color(bid,"12",ChatFormatting.GREEN);StyledLoreTest.color(bid,"13,440,888",ChatFormatting.GOLD);
        assertTrue(bid.getString().contains("次出价"));
    }
    @Test void skyblockMenuDescriptionsSelectedPetAndStatsTranslate() {
        var out=menu("SkyBlock Menu","§7Level up your pets faster by gaining","§7XP in their favorite skill!","",
            "§7Selected pet: §6Glacite Golem","","§8Also accessible via /pets.","",
            "§7View and edit preset armor and","§7equipment sets with other settings to","§7make switching activities easy.","",
            "§8Also accessible via /loadouts","","§bFishing Stats","§7Stats that influence what you catch","§7and how quickly you catch it while","§7fishing.","",
            "§b Fishing Speed §f74","§3 Sea Creature Chance §f35.3%","§6 Trophy Chance §f26%","§6 Treasure Chance §f6.2%");
        assertFalse(out.stream().map(Component::getString).anyMatch(s->s.replace("/pets","").replace("/loadouts","").matches(".*[A-Za-z].*")),out.toString());
        var selected=out.stream().filter(s->s.getString().startsWith("当前宠物:")).findFirst().orElseThrow();
        StyledLoreTest.color(selected,"冰晶傀儡",ChatFormatting.GOLD);
        var speed=out.stream().filter(s->s.getString().contains("74")).findFirst().orElseThrow();
        StyledLoreTest.color(speed,"钓鱼速度",ChatFormatting.AQUA);StyledLoreTest.color(speed,"74",ChatFormatting.WHITE);
    }
    @Test void trophyFishFrogsAndDiscoveriesPreserveQualityStyles() {
        for(String heading:List.of("TROPHY FISH","TROPHY FROG","TROPHY"))for(String article:List.of("","a ","an ")) {
            String name=heading.equals("TROPHY FISH")?"Blobfish":"Common Frog";
            var raw=Component.literal("§6 §r§6§l"+heading+"! §r§fYou caught "+article+"§r§f"+name+" §r§8§lBRONZE§r§f!");
            var before=raw.copy();var result=SystemTranslationEngine.translate(raw,"chat",manager);
            assertTrue(result.getString().contains("你钓到了 "+manager.translatedName(name)+" 青铜!"),result.toString());
            StyledLoreTest.color(result,"青铜",ChatFormatting.DARK_GRAY);assertTrue(StyledLoreTest.styleAt(result,"青铜").isBold());assertEquals(before,raw);
        }
        var discovery=SystemTranslationEngine.translate(Component.literal("§a§lNEW DISCOVERY: §fCommon Frog §7§lSILVER"),"chat",manager);
        assertEquals("新发现: 普通青蛙 白银",discovery.getString());StyledLoreTest.color(discovery,"白银",ChatFormatting.GRAY);
        var player=Component.literal("[MVP+] Player: TROPHY FROG! You caught Common Frog BRONZE!");
        assertSame(player,SystemTranslationEngine.translate(player,"chat",manager));
        assertSame(player,CommunityTranslationEngine.translate(player,"chat",manager));
    }
}
