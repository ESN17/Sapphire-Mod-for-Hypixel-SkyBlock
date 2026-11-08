package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.compat.*;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

class SkyHanniHandoffTest {
    static TranslationManager manager;
    static SkyHanniDictionary dictionary;
    @BeforeAll static void load() throws Exception {
        manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));
        dictionary=SkyHanniDictionary.load(Path.of("src/main/resources/defaults"));
    }
    @Test void customHudUsesExistingDictionariesWithoutAffectingChatOrData() {
        var live=new SkyHanniLiveTranslation();
        for(String text:List.of("Area: The Rift","Bank: 49.9M","⏣ Village","Purse: 1,234")) {
            var source=Component.literal(text);var before=source.copy();
            var translated=live.translate(source,manager,dictionary,3);
            assertNotEquals(source.getString(),translated.getString(),text);assertEquals(before,source);
        }
        var source=Component.literal("Area: The Rift");
        assertSame(source,live.translate(source,manager,dictionary,0));
        assertTrue(live.translate(source,manager,dictionary,1).getString().contains("裂隙"));
        assertSame(source,live.translate(source,manager,dictionary,0));
    }
    @Test void customHudCacheKeepsDifferentColorsActionsAndCurrentValues() {
        var live=new SkyHanniLiveTranslation();
        for(int color:List.of(0x123456,0xaa0022))for(String amount:List.of("49.9M","51.2M")) {
            var action=new ClickEvent.SuggestCommand("/bank "+amount);
            var source=Component.literal("Bank: ").append(Component.literal(amount).withStyle(s->s.withColor(color).withClickEvent(action)));
            var translated=live.translate(source,manager,dictionary,3);
            translated.visit((s,t)->{if(t.contains(amount)){assertEquals(color,s.getColor().getValue());assertEquals(action,s.getClickEvent());}return Optional.empty();},Style.EMPTY);
            assertTrue(translated.getString().contains(amount));
            ((MutableComponent)translated).append("changed");
            assertFalse(live.translate(source,manager,dictionary,3).getString().contains("changed"));
        }
    }
    @Test void shardRuntimeLabelsUseDynamicValuesAndKeepButtonActions() {
        var live=new SkyHanniLiveTranslation();
        for(String value:List.of("1","27","3,288"))for(String source:List.of(
            "Found Shards: "+value+"/324","Maxed Shards: "+value+"/324",
            "Total Shard Levels: "+value+"/3240","Missing shard data for "+value+" shards",
            "Total Price to Max All Shards: "+value+"M")) {
            var input=Component.literal(source);var result=live.translate(input,manager,dictionary,3);
            assertNotEquals(source,result.getString());assertTrue(result.getString().contains(value));assertEquals(source,input.getString());
        }
        for(String label:List.of("Attribute Shard Overlay","Hide Maxed","Show Maxed","Only Not Unlocked","Include Hunting Box","Include Bazaar Orders","Price to Maxed")) {
            var click=new ClickEvent.SuggestCommand("/sh fixture");
            var input=Component.literal(label).withStyle(s->s.withColor(0x33aaee).withClickEvent(click));
            var result=live.translate(input,manager,dictionary,3);assertNotEquals(label,result.getString());
            result.visit((s,t)->{if(!t.isEmpty()){assertEquals(0x33aaee,s.getColor().getValue());assertEquals(click,s.getClickEvent());}return Optional.empty();},Style.EMPTY);
        }
    }
    @Test void gradientPrefixAndProfitInputsStayIntact() {
        var live=new SkyHanniLiveTranslation();var input=Component.empty();String prefix="[SkyHanni]";
        for(int i=0;i<prefix.length();i++)input.append(Component.literal(prefix.substring(i,i+1)).withColor(0x110000+i*1024));
        input.append(Component.literal(" You have gained ").withColor(0xffff55));
        input.append(Component.literal("523").withColor(0x55ffff)).append(Component.literal(" Bits.").withColor(0xffff55));
        var before=input.copy();var result=live.translate(input,manager,dictionary);
        assertEquals(before,input);assertTrue(result.getString().contains("比特"));
        List<Integer> colors=new ArrayList<>();result.visit((s,t)->{for(int i=0;i<t.length();i++)colors.add(s.getColor()==null?0:s.getColor().getValue());return Optional.empty();},Style.EMPTY);
        for(int i=0;i<prefix.length();i++)assertEquals(0x110000+i*1024,colors.get(i));
        for(String text:List.of("Voidgloom Seraph IV Profit Tracker","Slayer Spawn Costs: -3.4M","Bosses killed: 288","Total Profit: 195,494,974 coins")) {
            var source=Component.literal(text);assertNotEquals(text,live.translate(source,manager,dictionary,3).getString());assertEquals(text,source.getString());
        }
    }
    @Test void actualOptionalBytecodeMatchesAndFutureApiDriftDisablesHooks() throws Exception {
        Path file=Path.of("build/skyhanni-reference/SkyHanni-9.3.0-mc26.2.jar");Assumptions.assumeTrue(Files.exists(file));
        try(var jar=new JarFile(file.toFile())) {
            for(String name:List.of("utils/GuiRenderUtils","features/misc/compacttablist/TabLine","features/misc/compacttablist/TabListRenderer")) {
                var node=new ClassNode();try(var in=jar.getInputStream(jar.getJarEntry("at/hannibal2/skyhanni/"+name+".class"))){new ClassReader(in).accept(node,0);}
                if(name.endsWith("GuiRenderUtils")){assertTrue(SkyHanniMixinPlugin.drawCompatible(node));node.methods.clear();assertFalse(SkyHanniMixinPlugin.drawCompatible(node));}
                else {assertTrue(SkyHanniMixinPlugin.tabMeasureCompatible(node));node.methods.clear();assertFalse(SkyHanniMixinPlugin.tabMeasureCompatible(node));}
            }
        }
    }
}
