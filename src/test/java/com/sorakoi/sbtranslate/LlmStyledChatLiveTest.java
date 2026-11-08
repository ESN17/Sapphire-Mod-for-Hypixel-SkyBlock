package com.sorakoi.sbtranslate;

import com.google.gson.JsonParser;
import com.sorakoi.sbtranslate.llm.*;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in; three synthetic messages only, no account or chat logs are transmitted. */
@EnabledIfEnvironmentVariable(named="SAPPHIRE_CHAT_LIVE",matches="true")
class LlmStyledChatLiveTest {
    @Test void configuredProviderPreservesFormatForRealChatResponses() throws Exception {
        var secret=JsonParser.parseString(Files.readString(Path.of("deepseek.local.json"))).getAsJsonObject();
        var config=LlmConfig.defaults();config.provider=LlmConfig.Provider.DEEPSEEK;config.timeoutSeconds=60;
        config.keys.put(config.provider,secret.get("api_key").getAsString());config.profile().model=secret.get("model").getAsString();
        var report=new ClickEvent.RunCommand("/report ExampleUser");var block=new ClickEvent.SuggestCommand("/ignore add ExampleUser");
        var cases=List.<Component>of(
            Component.literal("§7[§d567§7] §6α §b[MVP§c++§b] GreatCatch§f: I need sbxp quickly"),
            Component.literal("§aA §bStranger §aappears on your island at §f8, 100, 41§a! You should go talk to them."),
            Component.empty().append(Component.literal("ExampleUser ")).append(Component.literal("[Report Player]").withStyle(s->s.withClickEvent(report).withColor(0xff5555)))
                .append(Component.literal(" [Block Player]").withStyle(s->s.withClickEvent(block).withColor(0xffff55))));
        var log=new StringBuilder();
        for(var original:cases) {
            var prepared=LlmStyledChatTest.prepare(original);
            String response=LlmApi.translate(config,prepared.text());var result=prepared.restore(response);
            Files.writeString(Path.of("build/chat-api-live-protocol.txt"),prepared.text()+"\nRESPONSE\n"+response+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            assertNotNull(result,"Provider omitted or altered required span markers");
            assertTrue(result.getString().codePoints().anyMatch(c->c>=0x4e00 && c<=0x9fff),"Expected a Chinese response");
            for(var segment:prepared.segments()) {
                if(segment.style().getClickEvent()!=null) {
                    var found=new java.util.concurrent.atomic.AtomicBoolean();
                    result.visit((style,text)->{if(segment.style().getClickEvent().equals(style.getClickEvent()))found.set(true);return Optional.empty();},Style.EMPTY);
                    assertTrue(found.get());
                }
            }
            log.append(result.getString()).append('\n');
        }
        var path=Path.of("build/chat-api-live-results.txt");Files.writeString(path,log.toString());
    }
}
