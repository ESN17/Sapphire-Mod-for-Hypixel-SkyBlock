package com.sorakoi.sbtranslate.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModLanguageTest {
    private JsonObject language(String namespace) throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/" + namespace + "/lang/zh_cn.json")) {
            assertNotNull(stream);
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void wikiButtonLabelsStayInTheirMenus() throws Exception {
        try (var in=getClass().getResourceAsStream("/defaults/translations/wiki-ui-labels.json")) {
            var menus=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("作物指南",menus.getAsJsonObject("Carpenter").get("Crop Guide").getAsString());
            assertFalse(menus.getAsJsonObject("Your Skills").has("Crop Guide"));
        }
    }
    @Test void runtimeKeysResolveToChineseAndKeepArguments() throws Exception {
        assertEquals("任意 %s",language("jei").get("jei.tooltip.recipe.any").getAsString());
        assertTrue(language("roughlyenoughitems").get("text.rei.recipes.not.full.desc").getAsString().contains("%s"));
        var sbo=language("sbo");
        assertEquals("清除搜索",sbo.get(SboText.key("Clear the search")).getAsString());
        assertTrue(sbo.has(SboText.key("Spade Guess")));
        assertTrue(sbo.has("key.sbo-kotlin.send_coords"));
    }
}
