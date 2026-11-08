package com.sorakoi.sbtranslate;

import com.google.gson.*;
import com.sorakoi.sbtranslate.item.ItemIdResolver;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AttributeShardTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;}
    static CompoundTag shard(String key,int level) {
        var extra=new CompoundTag();extra.putString("id","ATTRIBUTE_SHARD");
        var attributes=new CompoundTag();attributes.putInt(key,level);extra.put("attributes",attributes);
        var data=new CompoundTag();data.put("ExtraAttributes",extra);return data;
    }
    @Test void allRepositoryShardsResolveAndTranslate() throws Exception {
        var rows=JsonParser.parseString(Files.readString(Path.of("src/test/resources/fixtures/attribute-shards-20261104.json"))).getAsJsonArray();
        var failures=new JsonArray();
        for(var element:rows) {
            var row=element.getAsJsonObject();String id=row.get("id").getAsString();
            String key=id.substring("ATTRIBUTE_SHARD_".length(),id.indexOf(';')).toLowerCase(Locale.ROOT);
            var tag=shard(key,1);var copy=tag.copy();assertEquals(id,ItemIdResolver.resolve(tag).variantId());assertEquals(copy,tag);
            var expanded=new CompoundTag();expanded.putString("id",id);assertEquals(id,ItemIdResolver.resolve(expanded).variantId());
            var entry=manager.item(id);assertNotNull(entry,id);
            var source=Component.literal(row.get("name").getAsString());
            var translated=TextTranslationEngine.itemName(source,entry.originalName(),entry.name(),manager::reforge,entry.styleMappings());
            assertEquals(entry.name(),translated.getString(),id);
            var lore=new ArrayList<Component>();row.getAsJsonArray("lore").forEach(x->lore.add(Component.literal(x.getAsString())));
            var originals=lore.stream().map(Component::copy).toList();
            var result=LoreTranslationEngine.translate(lore,manager,id,(t,s)->List.of(t),false);
            assertEquals(originals,lore);
            if(!result.missing().isEmpty()) {var fail=new JsonObject();fail.addProperty("id",id);fail.add("missing",new Gson().toJsonTree(result.missing()));fail.add("output",new Gson().toJsonTree(result.lines().stream().map(Component::getString).toList()));failures.add(fail);}
        }
        Files.writeString(Path.of("build/attribute-shard-audit.json"),new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(failures));
        assertEquals(0,failures.size(),"See build/attribute-shard-audit.json");
    }
    @Test void strongarmScreenshotAndVariableDefenseKeepStyles() {
        for(String value:List.of("+4","+17.5","+1,234")) {
            String id=ItemIdResolver.resolve(shard("frozen_resistance",1)).variantId();
            var source=List.<Component>of(Component.literal("§7Grants §a"+value+"  Defense §7against §f"),Component.literal("§fFrozen §7mobs."));
            var result=LoreTranslationEngine.translate(source,manager,id,(t,s)->List.of(t),false);
            assertTrue(result.missing().isEmpty(),result.missing().toString());
            var line=result.lines().getFirst();assertTrue(line.getString().contains("冰冻"));
            StyledLoreTest.color(line,value,ChatFormatting.GREEN);
            StyledLoreTest.color(line,"防御",ChatFormatting.GREEN);
            StyledLoreTest.color(line,"冰冻",ChatFormatting.WHITE);
        }
    }
    @Test void invalidOrAmbiguousMetadataDoesNotSelectAnArbitraryShard() {
        for(int level:List.of(-1,0,101))assertNull(ItemIdResolver.resolve(shard("frozen_resistance",level)).variantId());
        for(String key:List.of("../PET","a;b","a b",""))assertNull(ItemIdResolver.resolve(shard(key,1)).variantId());
        var data=shard("frozen_resistance",1);data.getCompoundOrEmpty("ExtraAttributes").getCompoundOrEmpty("attributes").putInt("lotus_trophy",1);
        assertNull(ItemIdResolver.resolve(data).variantId());
        var missing=new CompoundTag();missing.putString("id","ATTRIBUTE_SHARD");assertNull(ItemIdResolver.resolve(missing).variantId());
        assertEquals("ATTRIBUTE_SHARD_FROZEN_RESISTANCE;7",ItemIdResolver.resolve(shard("frozen_resistance",7)).variantId());
        var other=shard("frozen_resistance",1).getCompoundOrEmpty("ExtraAttributes");other.putString("id","ROGUE_SWORD");
        assertEquals("ROGUE_SWORD",ItemIdResolver.resolve(other).variantId());
    }
}
