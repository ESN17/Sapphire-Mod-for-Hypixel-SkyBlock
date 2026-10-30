package com.sorakoi.sbtranslate;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TranslationTest {
    @TempDir Path root;
    void file(String name,String json)throws Exception {Path p=root.resolve("translations/"+name);Files.createDirectories(p.getParent());Files.writeString(p,json);}
    @Test void priorityAndBlankFallback()throws Exception {
        file("items/TEST.json","""
{"id":"TEST","name":"测试","_original_name":"Test","lore":{"Damage: +100":"专属伤害","Missing":""}}
""");
        file("global.json","""
{"Damage: +100":"全局伤害"}
""");
        file("templates.json","""
{"Damage: {value}":"伤害：{value}"}
""");
        var m=new TranslationManager();m.load(root);
        assertEquals("专属伤害",m.lore("TEST","Damage: +100"));
        assertEquals("全局伤害",m.lore(null,"Damage: +100"));
        assertEquals("伤害：-1,234.50%",m.lore(null,"Damage: -1,234.50%"));
        assertEquals("Damage: somebody",m.lore(null,"Damage: somebody"));
        assertEquals("Missing",m.lore("TEST","Missing"));
        assertEquals("Unknown",m.name("OTHER","Unknown"));
    }
    @Test void failedReloadRetainsSnapshotAndSuccessfulReloadInvalidatesCache()throws Exception {
        file("global.json","""
{"Hello":"你好"}
""");var m=new TranslationManager();m.load(root);assertEquals("你好",m.lore(null,"Hello"));
        file("global.json","{broken");assertThrows(Exception.class,()->m.load(root));assertEquals("你好",m.lore(null,"Hello"));
        file("global.json","""
{"Hello":"您好"}
""");m.load(root);assertEquals("您好",m.lore(null,"Hello"));
    }
    @Test void duplicateKeysAndVariablesAreRejected()throws Exception {
        file("global.json","""
{"A":"甲","A":"乙"}
""");assertThrows(Exception.class,()->new TranslationManager().load(root));
        assertThrows(IllegalArgumentException.class,()->new TranslationManager.Template("Damage: {value}","伤害：{amount}"));
        assertThrows(IllegalArgumentException.class,()->new TranslationManager.Template("{name}{text}","{name}{text}"));
    }
    @Test void cachedHighFrequencyInputsStayBounded()throws Exception {
        file("templates.json","""
{"Purse: {value}":"钱袋：{value}"}
""");var m=new TranslationManager();m.load(root);
        assertTimeout(java.time.Duration.ofSeconds(5),()->{for(int i=0;i<20000;i++)assertEquals("钱袋："+i,m.lore(null,"Purse: "+i));});
        assertTrue(m.cacheSize()<=4096);
    }
    @Test void variableStyleAndInteractiveEventsRemainUnchanged() {
        var click=new ClickEvent.RunCommand("/test"); var hover=new HoverEvent.ShowText(Component.literal("original hover"));
        var source=Component.literal("Damage: ").withStyle(ChatFormatting.GRAY).withStyle(s->s.withClickEvent(click).withHoverEvent(hover))
            .append(Component.literal("+1,234.5").withStyle(ChatFormatting.RED,ChatFormatting.BOLD));
        var translated=TextTranslationEngine.apply(source,new TranslationManager.Template("Damage: {value}","伤害：{value}").apply(source.getString()));
        assertEquals("伤害：+1,234.5",translated.getString());assertEquals("Damage: +1,234.5",source.getString());
        List<Style> styles=new ArrayList<>();translated.visit((style,text)->{if(!text.isEmpty())styles.add(style);return Optional.empty();},Style.EMPTY);
        assertEquals(ChatFormatting.RED.getColor(),styles.getLast().getColor().getValue());assertTrue(styles.getLast().isBold());
        assertEquals(click,styles.getLast().getClickEvent());assertEquals(hover,styles.getLast().getHoverEvent());
    }
    @Test void constantSignRetainsNumberColor() {
        var source=Component.literal("Damage: ").withStyle(ChatFormatting.GRAY).append(Component.literal("+100").withStyle(ChatFormatting.RED));
        var translated=TextTranslationEngine.apply(source,new TranslationManager.Template("Damage: +{value}","伤害：+{value}").apply(source.getString()));
        assertEquals("伤害：+100",translated.getString());
        assertEquals(ChatFormatting.RED.getColor(),translated.getSiblings().getLast().getStyle().getColor().getValue());
    }
    @Test void legacyFormattingAndDecoratedNames() {
        var source=Component.literal("§9Heroic Aspect of the End §6✪✪");
        String text=TranslationManager.plain(source.getString());int start=text.indexOf("Aspect");
        var translated=TextTranslationEngine.rangeReplacement(source,start,start+"Aspect of the End".length(),"末影之刃");
        assertEquals("Heroic 末影之刃 ✪✪",translated.getString());
        assertEquals(ChatFormatting.GOLD.getColor(),translated.getSiblings().getLast().getStyle().getColor().getValue());
    }
    @Test void ambiguousStylesAndNonliteralComponentsFallBack() {
        var source=Component.literal("Hello ").withStyle(ChatFormatting.RED).append(Component.literal("world").withStyle(ChatFormatting.BLUE));
        assertSame(source,TextTranslationEngine.apply(source,TranslationManager.replacement("test",source.getString(),"你好世界")));
        var localized=Component.translatable("item.minecraft.diamond");
        assertSame(localized,TextTranslationEngine.apply(localized,TranslationManager.replacement("test",localized.getString(),"钻石")));
    }
    @Test void translatedTextIsNotTranslatedAgain()throws Exception {
        file("global.json","""
{"Hello":"你好"}
""");var m=new TranslationManager();m.load(root);
        assertNull(m.match("item",null,m.lore(null,"Hello")));assertNull(m.match("chat",null,"Hello"));
    }
    @Test void nestedFragmentsAndNewlinesPreserveInheritedStyles() {
        var source=Component.literal("Hello").withStyle(ChatFormatting.RED).append(Component.literal("world").withStyle(ChatFormatting.BLUE));
        java.util.function.Function<String,TranslationManager.Match> lookup=s -> switch(s){
            case "Hello" -> TranslationManager.replacement("test",s,"你好");
            case "world" -> TranslationManager.replacement("test",s,"世界");default -> null;
        };
        var translated=TextTranslationEngine.translate(source,lookup);
        assertEquals("你好世界",translated.getString());
        List<Integer> colors=new ArrayList<>();translated.visit((style,text)->{if(!text.isEmpty())colors.add(style.getColor().getValue());return Optional.empty();},Style.EMPTY);
        assertEquals(List.of(ChatFormatting.RED.getColor(),ChatFormatting.BLUE.getColor()),colors);
        assertEquals("你好\n世界",TextTranslationEngine.translate(Component.literal("Hello\nworld"),lookup).getString());
        assertEquals("Helloworld",source.getString());
    }
}
