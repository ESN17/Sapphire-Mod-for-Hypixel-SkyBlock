package com.sorakoi.sbtranslate;
import com.sorakoi.sbtranslate.compat.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class OdinCompatibilityTest {
    @TempDir Path root;
    @Test void absentOdinIsInert(){
        assertFalse(new OdinMixinPlugin().shouldApplyMixin("com.odtheking.odin.Missing","test"));
        assertDoesNotThrow(()->OdinCompat.load(root));
        assertEquals("Text Scale",OdinCompat.text("Text Scale"));
        assertFalse(OdinCompat.scoped());
    }
    @Test void fullCatalogLoadsAndUnknownNamesStayUnchanged()throws Exception{
        var d=SkyHanniDictionary.load(root,"odin-zh_cn.json");
        assertTrue(d.size()>1000);assertEquals("文字缩放",d.translate("Text Scale"));
        assertEquals("ESN17_OwO",d.translate("ESN17_OwO"));
    }
    @Test void serverDictionaryThenUserOverride()throws Exception{
        Files.createDirectories(root.resolve("translations"));Files.createDirectories(root.resolve("compat"));
        Files.writeString(root.resolve("translations/compat-odin-zh-cn.json"),"{\"translations\":{\"Text Scale\":\"官方新译\"}}");
        assertEquals("官方新译",SkyHanniDictionary.load(root,"odin-zh_cn.json").translate("Text Scale"));
        Files.writeString(root.resolve("compat/odin-zh_cn.json"),"{\"translations\":{\"Text Scale\":\"手动译文\"}}");
        assertEquals("手动译文",SkyHanniDictionary.load(root,"odin-zh_cn.json").translate("Text Scale"));
    }
    @Test void newerDynamicTemplatesReplaceOlderVersions(){
        var first=SkyHanniDictionary.Dynamic.compile("Selected player: {sh0}","旧译文：{sh0}");
        var second=SkyHanniDictionary.Dynamic.compile("Selected player: {sh0}","新译文：{sh0}");
        var d=new SkyHanniDictionary(java.util.Map.of(),java.util.List.of(first,second));
        assertEquals("新译文：ESN17_OwO",d.translate("Selected player: ESN17_OwO"));
    }
}
