package com.sorakoi.sbtranslate;

import com.google.gson.*;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises captured formatted text with the real display engine before installation. */
class WikiCorpusAuditTest {
    @TempDir Path directory;

    @Test void stagedTranslationsRenderWithoutMutatingSource() throws Exception {
        Path staged=Path.of("translation-state/wiki-20261023/staged/community-wiki.json");
        Assumptions.assumeTrue(Files.exists(staged),"Wiki staging corpus is optional outside translation work");
        Files.createDirectories(directory.resolve("translations"));
        Files.copy(staged,directory.resolve("translations/community-wiki.json"));
        Files.createDirectories(directory.resolve("translations/community-wiki"));
        for(var part:CommunityTranslationEngine.corpusFiles(staged))if(!part.equals(staged))
            Files.copy(part,directory.resolve("translations/community-wiki").resolve(part.getFileName()));
        var manager=new TranslationManager();manager.load(directory);
        var failures=new JsonArray();int checked=0;
        for(var file:CommunityTranslationEngine.corpusFiles(staged)) {
        var data=TranslationManager.readObject(file);
        for(var value:data.getAsJsonArray("entries")) {
            var row=value.getAsJsonObject();String scope=row.get("scope").getAsString();
            String original=row.get("source").getAsString(),target=row.get("translation").getAsString();
            String raw=row.get("sampleRaw").getAsString();
            if(row.has("templateExamples"))for(var example:row.getAsJsonObject("templateExamples").entrySet()) {
                original=original.replace(example.getKey(),example.getValue().getAsString());
                target=target.replace(example.getKey(),example.getValue().getAsString());
                raw=raw.replace(example.getKey(),example.getValue().getAsString());
            }
            assertEquals(original,TranslationManager.plain(raw).strip());
            String plainRaw=TranslationManager.plain(raw);
            target=plainRaw.substring(0,plainRaw.indexOf(original))+target+plainRaw.substring(plainRaw.indexOf(original)+original.length());
            String bodyRaw=raw;
            if(scope.equals("npc")) {
                String prefix="[NPC] "+row.get("npc").getAsString()+": ";
                raw="§e"+prefix+"§f"+raw;target=prefix+target;scope="chat";
            } else if(scope.equals("system"))scope="chat";
            var source=Component.literal(raw);var before=source.copy();
            var result=CommunityTranslationEngine.translate(source,scope,manager);checked++;
            assertEquals(before,source);
            if(!TranslationManager.plain(result.getString()).strip().equals(target.strip())) {
                var failure=new JsonObject();
                failure.addProperty("sourceId",row.get("sourceId").getAsString());
                failure.addProperty("source",row.get("source").getAsString());
                failure.addProperty("actual",TranslationManager.plain(result.getString()));
                failure.addProperty("expected",target);
                var styles=new java.util.LinkedHashMap<String,String>();
                row.getAsJsonObject("styleMappings").entrySet().forEach(e->styles.put(e.getKey(),e.getValue().getAsString()));
                var match=new TranslationManager.Template(row.get(row.has("numericSource")?"numericSource":"source").getAsString(),row.get(row.has("numericTranslation")?"numericTranslation":"translation").getAsString()).apply(original);
                var diagnosticSource=Component.literal(bodyRaw);
                if(row.get("scope").getAsString().equals("npc"))diagnosticSource.withStyle(s->s.withColor(net.minecraft.ChatFormatting.WHITE)
                    .withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false));
                if(match!=null)TextTranslationEngine.apply(diagnosticSource,new TranslationManager.Match(match.rule(),match.pieces(),styles),reason->failure.addProperty("reason",reason));
                failures.add(failure);
            }
        }
        }
        Path report=Path.of("build/reports/wiki-style-failures.json");Files.createDirectories(report.getParent());
        Files.writeString(report,new GsonBuilder().setPrettyPrinting().create().toJson(failures));
        var digest=java.security.MessageDigest.getInstance("SHA-256");
        for(var file:CommunityTranslationEngine.corpusFiles(staged))digest.update(Files.readAllBytes(file));
        var summary=new JsonObject();summary.addProperty("checked",checked);summary.addProperty("failures",failures.size());
        summary.addProperty("sha256",java.util.HexFormat.of().formatHex(digest.digest()));
        Files.writeString(Path.of("build/reports/wiki-audit-summary.json"),summary.toString());
        assertTrue(checked>0);assertEquals(0,failures.size(),"See "+report);
    }
}
