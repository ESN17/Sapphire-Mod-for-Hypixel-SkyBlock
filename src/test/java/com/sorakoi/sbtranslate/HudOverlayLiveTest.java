package com.sorakoi.sbtranslate;

import com.google.gson.JsonParser;
import com.sorakoi.sbtranslate.llm.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: two synthetic HUD lines only; never send live player data or logs. */
@EnabledIfEnvironmentVariable(named="SAPPHIRE_CHAT_LIVE",matches="true")
class HudOverlayLiveTest {
    @Test void providerTranslatesHudAndSameReplyUsesCurrentValues() throws Exception {
        var secret=JsonParser.parseString(Files.readString(Path.of("deepseek.local.json"))).getAsJsonObject();
        var config=LlmConfig.defaults();config.provider=LlmConfig.Provider.DEEPSEEK;config.timeoutSeconds=60;
        config.keys.put(config.provider,secret.get("api_key").getAsString());config.profile().model=secret.get("model").getAsString();
        var report=new StringBuilder();
        for(String english:new String[]{"§eObjective: §bReach the summit §7before §a21m41s", "§bNext event: §6Fishing Festival §7in §a21m41s §f(裂隙尘埃: 123)"}) {
            var source=Component.literal(english);var request=HudLlmText.prepare(source,Set.of());assertNotNull(request);
            String response=null;Component result=null;
            // Match the service's three-attempt format-validation policy.
            for(int attempt=0;attempt<3 && result==null;attempt++) {
                response=LlmApi.translate(config,request.text());result=request.restore(response);
                Files.writeString(Path.of("build/hud-api-live-protocol.txt"),request.text()+"\nRESPONSE\n"+response+"\n",
                    StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            }
            assertNotNull(result,"Provider must retain all style markers and protected values");
            assertFalse(result.getString().contains("Objective"));assertFalse(result.getString().contains("Next event"));
            assertTrue(result.getString().contains("21m41s"));StyledLoreTest.color(result,"21m41s",ChatFormatting.GREEN);
            var current=HudLlmText.prepare(Component.literal(english.replace("21m41s","18m12s").replace("123","456")),Set.of());
            assertNotNull(current);assertEquals(request.text(),current.text());
            var currentResult=current.restore(response);assertNotNull(currentResult);
            assertTrue(currentResult.getString().contains("18m12s"));assertFalse(currentResult.getString().contains("21m41s"));
            if(english.contains("裂隙尘埃"))assertTrue(currentResult.getString().contains("裂隙尘埃: 456"));
            report.append(result.getString()).append('\n').append(currentResult.getString()).append('\n');
        }
        Files.writeString(Path.of("build/hud-api-live-results.txt"),report.toString());
    }
}
