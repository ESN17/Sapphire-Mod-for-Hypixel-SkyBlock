package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.compat.*;
import com.sorakoi.sbtranslate.translation.TranslationManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

class SkyHanniCompatibilityTest {
    @TempDir Path root;
    @Test void noSkyHanniDependencyAndNoOptionalClassLoading() {
        var plugin=new SkyHanniMixinPlugin();
        assertFalse(plugin.shouldApplyMixin(SkyHanniMixinPlugin.BASE+"MissingClass","test"));
        assertDoesNotThrow(()->SkyHanniCompat.load(root));
        assertFalse(Files.exists(root.resolve("compat")));
    }
    @Test void configDisplayPreservesFormattingAndDoesNotMutateSource() {
        var dict=new SkyHanniDictionary(Map.of("§eVisitor Timer\n§7Enabled","§e访客计时器\n§7已启用"));
        var click=new ClickEvent.SuggestCommand("/sh");
        Component source=Component.literal("§eVisitor Timer\n§7Enabled").withStyle(s->s.withClickEvent(click));
        Component before=source.copy(),result=dict.translate(source);
        assertEquals("访客计时器\n已启用",result.getString());
        assertEquals(before,source);
        var colors=new ArrayList<Integer>();
        result.visit((s,t)->{if(!t.isEmpty()){colors.add(s.getColor().getValue());assertEquals(click,s.getClickEvent());}return Optional.empty();},Style.EMPTY);
        assertEquals(List.of(0xffff55,0xaaaaaa),colors);
    }
    @Test void cachedResultIsDefensiveAndUnknownTextIsUntouched() {
        var dict=new SkyHanniDictionary(Map.of("Visitor Timer","访客计时器"));
        var source=Component.literal("Visitor Timer");dict.translate(source);
        var copy=dict.translate(source);((MutableComponent)copy).append("changed");
        assertEquals("访客计时器",dict.translate(source).getString());
        source.append(" edited");assertSame(source,dict.translate(source));
        var unknown=Component.literal("ESN17_OwO");assertSame(unknown,dict.translate(unknown));
    }
    @Test void dynamicCapturePreservesPlayerStyleAndEvents() {
        var rule=SkyHanniDictionary.Dynamic.compile("Selected player: {sh0} ({sh1})","已选择玩家：{sh0}（{sh1}）");
        var dict=new SkyHanniDictionary(Map.of(),List.of(rule));
        var click=new ClickEvent.SuggestCommand("/msg ESN17_OwO ");
        var source=Component.literal("Selected player: ").append(Component.literal("ESN17_OwO").withStyle(s->s.withColor(ChatFormatting.RED).withClickEvent(click))).append(" (123)");
        var before=source.copy();var result=dict.translate(source);
        assertEquals("已选择玩家：ESN17_OwO（123）",result.getString());assertEquals(before,source);
        result.visit((s,t)->{if(t.equals("ESN17_OwO")){assertEquals(0xff5555,s.getColor().getValue());assertEquals(click,s.getClickEvent());}return Optional.empty();},Style.EMPTY);
        assertSame(source, new SkyHanniDictionary(Map.of()).translate(source));
        assertThrows(IllegalArgumentException.class,()->SkyHanniDictionary.Dynamic.compile("{sh0}: {sh1}","{sh0}：{sh1}"));
        assertThrows(IllegalArgumentException.class,()->SkyHanniDictionary.Dynamic.compile("Selected: {sh0}","已选择"));
    }
    @Test void localOverrideWinsWithoutModifyingBundledData() throws Exception {
        Files.createDirectories(root.resolve("compat"));
        Files.writeString(root.resolve("compat/skyhanni-zh_cn.json"),"{\"translations\":{\"Visitor Timer\":\"自定计时器\"}}");
        var dictionary=SkyHanniDictionary.load(root);
        assertEquals("自定计时器",dictionary.translate("Visitor Timer"));
        assertTrue(dictionary.size()>7000);
        assertFalse(dictionary.translate("Sixth Visitor Estimate").equals("Sixth Visitor Estimate"));
    }
    @Test void officialReleaseBytecodeMatchesAndApiDriftIsRejected() throws Exception {
        Path jar=Path.of("build/skyhanni-reference/SkyHanni-9.3.0-mc26.2.jar");
        Assumptions.assumeTrue(Files.exists(jar),"Optional official reference JAR not downloaded");
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        assertEquals("eaa54e58958f211464a204f31cc2754da378e7b2ce88753ecc41b97dfdad9467",hash);
        try(var zip=new JarFile(jar.toFile())) {
            var font=node(zip,"MoulConfigFontRenderer");var render=node(zip,"MoulConfigRenderContext");
            assertTrue(SkyHanniMixinPlugin.compatible(font,render));
            font.methods.removeIf(m->m.name.equals("splitText"));
            assertFalse(SkyHanniMixinPlugin.compatible(font,render));
        }
    }
    private static ClassNode node(JarFile zip,String simple) throws Exception {
        var node=new ClassNode();try(var in=zip.getInputStream(zip.getJarEntry((SkyHanniMixinPlugin.BASE+simple).replace('.','/')+".class"))) {
            new ClassReader(in).accept(node,0);
        }return node;
    }
}
