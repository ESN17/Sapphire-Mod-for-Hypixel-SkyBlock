package com.sorakoi.sbtranslate;
import com.sorakoi.sbtranslate.llm.*;
import com.sorakoi.sbtranslate.translation.*;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class HelpNamesTest {
    static TranslationManager manager;
    @BeforeAll static void load()throws Exception{manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;}
    @Test void canonicalDictionaryControlsNames(){
        assertEquals("使用狂欢之瓶（Fiesta Flask）",HelpNames.render("使用[[Fiesta Flask]]",manager));
        assertEquals("狂欢之瓶（Fiesta Flask）",HelpNames.render("[[Festia Flask]]",manager));
        assertEquals(manager.knownItemName("Scatha Shard")+"（Scatha Shard）",HelpNames.render("[[Scatha Shard]]",manager));
        assertEquals("Unlisted Example",HelpNames.render("[[Unlisted Example]]",manager));
    }
    @Test void configuredPromptSurvivesCopyAndIsIndependent()throws Exception{
        var c=LlmConfig.defaults();assertTrue(c.helpPrompt.contains("2.3"));assertTrue(c.helpPrompt.contains("不编造"));
        c.helpPrompt="自定义教程提示";var copy=c.copy();assertEquals(c.helpPrompt,copy.helpPrompt);assertNotEquals(copy.prompt,copy.helpPrompt);
    }
    @Test void providersWithoutMarkupStillUseCanonicalBilingualNames(){
        assertEquals("使用狂欢之瓶（Fiesta Flask）。",HelpNames.render("使用狂欢之瓶。",manager));
        assertEquals("使用狂欢之瓶（Fiesta Flask）。",HelpNames.render("使用Fiesta Flask。",manager));
        assertEquals("使用狂欢之瓶（Fiesta Flask）。",HelpNames.render("使用狂欢之瓶（Fiesta Flask）。",manager));
        assertEquals("狂欢之瓶（Fiesta Flask）",HelpNames.render("[[Fiesta Flask]]（Fiesta Flask）",manager));
        assertEquals("狂欢之瓶（Fiesta Flask）",HelpNames.render("狂欢之瓶（[[Fiesta Flask]]）",manager));
        assertEquals("狂欢之瓶（Fiesta Flask）（+100%）",HelpNames.render("Fiesta Flask（+100%）",manager));
        assertEquals("狂欢之瓶（Fiesta Flask）（+100%）",HelpNames.render("狂欢之瓶（+100%）",manager));
    }
}
