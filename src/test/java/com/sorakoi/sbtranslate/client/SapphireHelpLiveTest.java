package com.sorakoi.sbtranslate.client;
import com.google.gson.JsonParser;
import com.sorakoi.sbtranslate.llm.*;
import com.sorakoi.sbtranslate.translation.TranslationManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="SAPPHIRE_HELP_LIVE",matches="true")
class SapphireHelpLiveTest {
    @Test void practicalAdviceUsesForumEvidenceWithoutDumpingLinks()throws Exception{
        var local=JsonParser.parseString(Files.readString(Path.of("deepseek.local.json"))).getAsJsonObject();
        var config=LlmConfig.defaults();config.provider=LlmConfig.Provider.DEEPSEEK;config.timeoutSeconds=60;
        config.keys.put(config.provider,local.get("api_key").getAsString());config.profile().model=local.get("model").getAsString();
        var manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));
        var result=SapphireHelp.answerDetailed("我打 Voidgloom Seraph T4 很慢，预算有限。请参考 Voidgloom guide 之类的玩家攻略，给出三条优先提升建议，不要只复述 Boss 属性。",config,manager);
        assertFalse(result.text().contains("https://"));assertFalse(result.text().contains("[["));
        assertTrue(result.text().codePoints().filter(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN).count()<=350,result.text());
        var path=Path.of("translation-state/help-search-20261106/live-practical-answer.txt");Files.createDirectories(path.getParent());
        Files.writeString(path,result.text()+"\n\n单独查询来源：\n"+result.sources());
        assertTrue(result.sources().contains("https://hypixel.net/threads/"),result.sources());
    }
    @Test void publishedWikiAndConfiguredProviderAnswerTheExample()throws Exception{
        var local=JsonParser.parseString(Files.readString(Path.of("deepseek.local.json"))).getAsJsonObject();
        var config=LlmConfig.defaults();config.provider=LlmConfig.Provider.DEEPSEEK;config.timeoutSeconds=60;
        config.keys.put(config.provider,local.get("api_key").getAsString());config.profile().model=local.get("model").getAsString();
        var manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));
        var result=SapphireHelp.answerDetailed("如何提高光泽宝石的掉落概率？请比较狂欢之瓶、史卡沙碎片和嘉年华的加成，并说明来源和生效条件。",config,manager);
        String answer=result.text();assertTrue(result.sources().contains("https://hypixelskyblock.minecraft.wiki/w/"));assertTrue(answer.contains("狂欢之瓶（Fiesta Flask）"));
        assertFalse(answer.contains("https://"));assertTrue(answer.length()<900,answer);
        assertTrue(answer.codePoints().filter(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN).count()<=350,answer);
        assertFalse(answer.contains("[["));assertFalse(answer.contains(config.key()));
        var path=Path.of("translation-state/help-search-20261106/live-answer.txt");Files.createDirectories(path.getParent());Files.writeString(path,answer+"\n\n单独查询来源：\n"+result.sources());
    }
}
