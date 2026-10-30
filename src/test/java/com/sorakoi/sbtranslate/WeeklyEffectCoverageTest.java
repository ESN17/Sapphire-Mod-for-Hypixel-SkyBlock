package com.sorakoi.sbtranslate;

import com.google.gson.*;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WeeklyEffectCoverageTest {
    @Test void allPublicNeuEnchantmentsAndEquippedPetItemsRender() throws Exception {
        var manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));
        var fixtures=TranslationManager.readObject(Path.of("src/test/resources/weekly-effect-fixtures.json"));var failures=new JsonArray();
        for(var fixture:fixtures.getAsJsonArray("enchantments")) {
            var f=fixture.getAsJsonObject();List<Component> source=new ArrayList<>();source.add(Component.literal(f.get("header").getAsString()));
            for(var line:f.getAsJsonArray("effect"))source.add(Component.literal(line.getAsString()));
            check(manager,f.get("id").getAsString(),source,failures);
        }
        for(var fixture:fixtures.getAsJsonArray("petItems")) {
            var f=fixture.getAsJsonObject();
            for(var effect:f.getAsJsonArray("effects")) {
                List<Component> source=new ArrayList<>();source.add(Component.literal("§6Held Item: "+f.get("name").getAsString()));
                for(var line:effect.getAsJsonArray())source.add(Component.literal(line.getAsString()));
                check(manager,"ENDERMAN;4",source,failures);
            }
        }
        Files.createDirectories(Path.of("build/weekly-audit"));Files.writeString(Path.of("build/weekly-audit/effect-gaps.json"),new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(failures));
        assertEquals(0,failures.size(),"See build/weekly-audit/effect-gaps.json");
    }
    private static void check(TranslationManager manager,String id,List<Component> source,JsonArray failures) {
        var copy=source.stream().map(Component::copy).toList();
        var result=LoreTranslationEngine.translate(source,manager,id,(line,original)->List.of(line),false);
        assertEquals(copy,source,id);
        var missing=result.lines().stream().skip(1).filter(c->c.getString().matches(".*[A-Za-z]{3,}.*") && source.stream().anyMatch(s->TranslationManager.plain(s.getString()).equals(TranslationManager.plain(c.getString())))).toList();
        if(!missing.isEmpty()) {
            var f=new JsonObject();f.addProperty("id",id);f.add("source",new Gson().toJsonTree(source.stream().map(Component::getString).toList()));f.add("missing",new Gson().toJsonTree(missing.stream().map(Component::getString).toList()));failures.add(f);
        }
    }
}
