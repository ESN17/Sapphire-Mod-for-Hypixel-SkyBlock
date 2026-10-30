package com.sorakoi.sbtranslate;

import com.google.gson.*;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SAPPHIRE_WEEKLY_AUDIT",matches="true")
class WeeklyStyleAuditTest {
    @Test void allRevisedUnitsRenderWithStyles() throws Exception {
        Path root=Path.of("translation-state/weekly-20261021");var failed=new JsonObject();int count=0;
        try(var paths=Files.list(root.resolve("audit"))) {
            for(var path:paths.sorted().toList())for(var element:TranslationManager.readObject(path).getAsJsonArray("units")) {
                var row=element.getAsJsonObject();String source=row.get("source").getAsString(),target=row.get("text").getAsString();
                var raw=Component.literal(BulkTranslationAuditTest.live(row.get("raw").getAsString())) .withStyle(net.minecraft.ChatFormatting.GRAY);var copy=raw.copy();
                var match=new TranslationManager.Template(source,target).apply(TranslationManager.plain(raw.getString()));
                var mappings=new HashMap<String,String>();row.getAsJsonObject("styleMappings").entrySet().forEach(e->mappings.put(e.getKey(),e.getValue().getAsString()));
                List<String> diagnostics=new ArrayList<>();var out=match==null?raw:TextTranslationEngine.apply(raw,new TranslationManager.Match(match.rule(),match.pieces(),mappings),diagnostics::add);
                assertEquals(copy,raw);count++;
                if(match==null || !TranslationManager.plain(out.getString()).equals(match.text()))failed.addProperty(row.get("id").getAsString(),match==null?"source mismatch":String.join("; ",diagnostics));
            }
        }
        Files.writeString(root.resolve("style-failures.json"),new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(failed));
        System.out.println("WEEKLY_STYLE_AUDIT units="+count+" failed="+failed.size());
        assertEquals(0,failed.size(),"See translation-state/weekly-20261021/style-failures.json");
    }
}
