package com.sorakoi.sbtranslate;

import com.google.gson.*;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Offline audit of every staged public NEU display component, not server/account data. */
@EnabledIfEnvironmentVariable(named="SAPPHIRE_BULK_AUDIT",matches="true")
class BulkTranslationAuditTest {
    static String dictionaryHash(Path root) throws Exception {
        var digest=java.security.MessageDigest.getInstance("SHA-256");
        try(var files=Files.list(root.resolve("staged/translations/items"))) {
            for(var file:files.filter(p->p.toString().endsWith(".json")).sorted().toList()) {
                digest.update(file.getFileName().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                digest.update(Files.readAllBytes(file));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    static String live(String text) {return text.replaceAll("\\{\\{[A-Za-z][A-Za-z0-9_]*}}|\\{[A-Za-z0-9_]+}","50");}
    @Test void auditStagedUnitsAndWriteRepairQueue() throws Exception {
        Path root=Path.of("translation-state");String hash=dictionaryHash(root);var manager=new TranslationManager();manager.load(root.resolve("staged"));
        JsonObject failed=new JsonObject();JsonArray passedItems=new JsonArray();int checked=0,units=0;
        try(var files=Files.list(root.resolve("audit"))) {
            for(Path path:files.filter(p->p.getFileName().toString().startsWith("part-")).sorted().toList()) {
                for(var element:TranslationManager.readObject(path).getAsJsonArray("items")) {
                    var item=element.getAsJsonObject();String id=item.get("id").getAsString();var entry=manager.item(id);
                    assertNotNull(entry,id);boolean passed=true;checked++;
                    var n=item.getAsJsonObject("name");String nameKey=n.get("job").getAsString();
                    try {
                        Component original=Component.literal(live(n.getAsJsonArray("raw").get(0).getAsString())).withStyle(net.minecraft.ChatFormatting.WHITE);
                        var result=TextTranslationEngine.itemName(original,entry.originalName(),entry.name(),manager::reforge,entry.styleMappings());
                        if(!TranslationManager.plain(result.getString()).equals(live(entry.name()))) {
                            failed.addProperty(nameKey,"Name style alignment or variable template did not render");passed=false;
                        }
                    } catch(RuntimeException e) {failed.addProperty(nameKey,"Name render error: "+e.getClass().getSimpleName());passed=false;}
                    for(var ge:item.getAsJsonArray("groups")) {
                        var g=ge.getAsJsonObject();String key=g.get("job").getAsString();List<String> source=new ArrayList<>();
                        for(var line:g.getAsJsonArray("source"))source.add(line.getAsString());
                        var paragraph=entry.paragraphs().stream().filter(p->p.source().equals(source)).findFirst().orElse(null);
                        if(paragraph==null)continue; // Pure numeric/symbol groups are intentionally unchanged.
                        units++;
                        try {
                            MutableComponent original=Component.empty();
                            for(var line:g.getAsJsonArray("raw")) {if(!original.getSiblings().isEmpty())original.append(" ");original.append(Component.literal(live(line.getAsString())).withStyle(net.minecraft.ChatFormatting.WHITE));}
                            var before=original.copy();var match=paragraph.template().apply(TranslationManager.plain(original.getString()));
                            if(match==null){failed.addProperty(key,"Numeric source template did not match");passed=false;continue;}
                            List<String> diagnostics=new ArrayList<>();
                            var result=TextTranslationEngine.apply(original,new TranslationManager.Match(match.rule(),match.pieces(),paragraph.styleMappings()),diagnostics::add);
                            assertEquals(before,original,"Source component mutated: "+id);
                            if(!TranslationManager.plain(result.getString()).equals(match.text())) {
                                failed.addProperty(key,String.join("; ",diagnostics));passed=false;
                            }
                        } catch(RuntimeException e) {failed.addProperty(key,"Lore render error: "+e.getClass().getSimpleName());passed=false;}
                    }
                    if(passed)passedItems.add(id);
                }
            }
        }
        var output=new JsonObject();output.addProperty("checkedItems",checked);output.addProperty("checkedLoreUnits",units);
        assertEquals(hash,dictionaryHash(root),"Staged dictionaries changed while being audited");
        output.addProperty("dictionarySha256",hash);
        output.addProperty("passedItems",passedItems.size());output.add("itemIds",passedItems);output.add("failedJobs",failed);
        Files.writeString(root.resolve("audit-result.json"),new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(output));
        Files.writeString(root.resolve("repair-jobs.json"),new Gson().toJson(failed.keySet()));
        assertEquals(manager.itemCount(),checked,"Every staged item must be audited");
        System.out.println("BULK_AUDIT checked="+checked+" passed="+passedItems.size()+" failedJobs="+failed.size());
    }
}
