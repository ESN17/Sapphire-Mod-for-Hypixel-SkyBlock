package com.sorakoi.sbtranslate;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ItemNameDeduplicationTest {
    static TranslationManager manager;
    static final Path BASE=Path.of("src/main/resources/defaults");
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(BASE);}
    @AfterAll static void release(){manager=null;}
    @Test void distinctEnglishNamesDoNotCollideExceptRabbits() throws Exception {
        Map<String,Set<String>> groups=new TreeMap<>();
        try(var files=Files.list(BASE.resolve("translations/items"))) {
            for(var p:files.filter(p->p.toString().endsWith(".json")).toList()) {
                var d=TranslationManager.readObject(p);String id=d.get("id").getAsString();
                if(id.startsWith("FACTION_RABBIT_"))continue;
                groups.computeIfAbsent(TranslationManager.plain(d.get("name").getAsString()).strip(),k->new TreeSet<>())
                    .add(TranslationManager.plain(d.get("_original_name").getAsString()).strip());
            }
        }
        groups.entrySet().removeIf(e->e.getValue().size()<2);
        assertTrue(groups.isEmpty(),groups.toString());
    }
    @Test void allMirroredNamesFollowReversalAndSingleCharacterQuoteRule() {
        Map<String,String> bases=new HashMap<>(Map.of("MIRRORED_BOW","弓","MIRRORED_STRING","线","MIRRORED_WOOD","橡木木板",
            "MIRRORED_FISHING_ROD","钓鱼竿","MIRRORED_LEASH","拴绳","MIRRORED_SLIME_BALL","黏液球",
            "MIRRORED_STICK","木棍","RIFT_TROPHY_MIRRORED","镜像世界时间护符","ETALPTSEHC_NORI","铁胸甲"));
        bases.put("EPOCH_CAKE_MAGENTA","裂隙世纪蛋糕");bases.put("GORF_THE_FISH","青蛙之鱼");
        bases.put("REVERSE_REFORGE_STONE_CORE","重铸石核心");bases.put("TINY_HAMMER","微型锤");
        bases.forEach((id,base)->assertEquals(base.length()==1?"“"+base+"”":new StringBuilder(base).reverse().toString(),manager.item(id).name(),id));
        assertEquals("橡木木板",manager.item("WOOD").name());assertEquals("弓",manager.item("BOW").name());
        assertEquals("线",manager.item("STRING").name());
        assertEquals("近乎连贯的竿鱼钓",manager.item("NEARLY_COHERENT_ROD").name());
        assertEquals("蛙青 (海怪)",manager.item("GORF_SC").name());
        assertEquals("蛙青",manager.scope("entity-names").get("gorF"));
        assertEquals("圆石",manager.item("FACTION_RABBIT_COBBLE").name());
        assertEquals("干草捆",manager.item("FACTION_RABBIT_HAYBALE").name());
    }
    @Test void catalogChatUsesNamesWithoutMutatingOriginals() {
        for(String[] row:List.of(new String[]{"Chum","诱鱼料"},new String[]{"Fish Bait","鱼饵"},new String[]{"Power Crystal","动力水晶"},
            new String[]{"Energy Crystal","能量水晶"},new String[]{"woB","“弓”"},new String[]{"Colossal Experience Bottle","超巨型经验瓶"})) {
            assertEquals(row[1],manager.knownItemName(row[0]));
            var source=Component.literal("§aYou sold §6"+row[0]+" §ax1 for §650,000 §aCoins!");var copy=source.copy();
            var out=SystemTranslationEngine.translate(source,"chat",manager);
            assertTrue(out.getString().contains(row[1]),out.getString());assertEquals(copy,source);
            StyledLoreTest.color(out,row[1],ChatFormatting.GOLD);
        }
    }
    @Test void listRowsKeepDifferentBaitsAndBottlesDistinct() throws Exception {
        var entries=TranslationManager.readObject(BASE.resolve("translations/community-wiki/wiki-011.json")).getAsJsonArray("entries");
        int checked=0;
        for(var element:entries) {
            var row=element.getAsJsonObject();String source=row.get("source").getAsString();
            if(!source.startsWith("▶ "))continue;
            String target=row.get("translation").getAsString();
            if(source.contains("Light Bait") && source.contains("Glowy Chum Bait")) {
                assertTrue(target.contains("发光鱼饵"));assertTrue(target.contains("荧光诱鱼饵"));checked++;
            }
            if(source.contains("Titanic Experience Bottle") && source.contains("Colossal Experience Bottle")) {
                assertTrue(target.contains("▶ 巨型经验瓶"));assertTrue(target.contains("▶ 超巨型经验瓶"));checked++;
            }
        }
        assertTrue(checked>=2);
    }
    @Test void renamedWordsDoNotRewriteOtherItemsOrBreakNumericStyles() throws Exception {
        var entries=TranslationManager.readObject(BASE.resolve("translations/community-wiki/wiki-007.json")).getAsJsonArray("entries");
        boolean found=false;
        for(var element:entries) {
            var row=element.getAsJsonObject();
            if(!row.get("source").getAsString().equals("Cost Hunk of Ice x32 Hunk of Blue Ice x8"))continue;
            found=true;
            assertEquals("花费 大块冰 x32 蓝冰块 x8",row.get("translation").getAsString());
            var template=new TranslationManager.Template(row.get("numericSource").getAsString(),row.get("numericTranslation").getAsString());
            assertEquals("花费 大块冰 x99 蓝冰块 x17",template.apply("Cost Hunk of Ice x99 Hunk of Blue Ice x17").text());
            assertEquals("蓝冰块",row.getAsJsonObject("styleMappings").get("Hunk of Blue Ice").getAsString());
        }
        assertTrue(found);
        var original=Component.literal("[NPC] Moby: So just bring me 19 Glowing Mushrooms and I'll show you how to chum, alright buddy?");
        var out=CommunityTranslationEngine.translate(original,"chat",manager);
        assertTrue(out.getString().contains("19 个发光蘑菇"),out.getString());
        assertTrue(out.getString().contains("诱鱼料"),out.getString());
    }
}
