package com.sorakoi.sbtranslate.translation;

import com.google.gson.*;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Explicit menu contexts; free-form values are copied, never used as item IDs. */
public final class GuiTranslationEngine {
    public record Menu(String id,List<TranslationManager.Template> titles,List<TranslationManager.Paragraph> paragraphs,Map<String,String> labels) {}
    public static List<Menu> load(Path path) throws IOException {
        return load(path,Map.of(),Map.of());
    }
    public static List<Menu> load(Path path,Map<String,TranslationManager.ItemEntry> items,Map<String,Map<String,String>> scopes) throws IOException {
        if(!Files.exists(path))return List.of();
        try {
            var data=TranslationManager.readObject(path);
            if(data.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Unsupported GUI schema");
            var common=TranslationManager.paragraphs(data,1024);
            var result=new ArrayList<Menu>();var ids=new HashSet<String>();
            for(var entry:data.getAsJsonArray("menus")) {
                var row=entry.getAsJsonObject();String id=row.get("id").getAsString();
                if(!ids.add(id))throw new IllegalArgumentException("Duplicate GUI context: "+id);
                var titles=new ArrayList<TranslationManager.Template>();
                for(var title:row.getAsJsonObject("titles").entrySet())titles.add(new TranslationManager.Template(title.getKey(),title.getValue().getAsString()));
                titles.sort(Comparator.comparingInt((TranslationManager.Template t)->t.specificity).reversed());
                var rules=new ArrayList<>(TranslationManager.paragraphs(row,1024));rules.addAll(common);
                // A reviewed exact status wins over a generic copied-text tail.
                rules.sort(Comparator.comparingInt((TranslationManager.Paragraph p)->p.template().specificity).reversed());
                var labels=new HashMap<String,String>();
                if(id.equals("bazaar")) {
                    // Display labels only: never assign an ID or change an ItemStack.
                    // Conflicting translations for the same metadata name are omitted.
                    var ambiguous=new HashSet<String>();
                    for(var item:items.values()) {
                        String en=BazaarTranslationEngine.bareLabel(item.originalName());
                        String zh=BazaarTranslationEngine.bareLabel(item.name());
                        if(en.isBlank() || zh.isBlank() || TranslationManager.VARIABLE.matcher(en).find())continue;
                        String previous=labels.putIfAbsent(en,zh);
                        if(previous!=null && !previous.equals(zh))ambiguous.add(en);
                    }
                    ambiguous.forEach(labels::remove);
                    for(var enchant:scopes.getOrDefault("enchantment-ids",Map.of()).entrySet()) {
                        String translated=scopes.getOrDefault("enchantments",Map.of()).get(enchant.getKey());
                        if(translated==null || translated.isBlank())continue;
                        labels.put(enchant.getKey(),translated);
                        for(int level=1;level<=100;level++)if(items.containsKey(enchant.getValue()+";"+level)) {
                            labels.put(enchant.getKey()+" "+level,translated+" "+level);
                            String roman=BazaarTranslationEngine.roman(level);
                            labels.put(enchant.getKey()+" "+roman,translated+" "+roman);
                        }
                    }
                }
                if(row.has("labels"))for(var label:row.getAsJsonObject("labels").entrySet()) {
                    if(!label.getValue().isJsonPrimitive() || !label.getValue().getAsJsonPrimitive().isString())throw new IllegalArgumentException("Invalid GUI label");
                    String en=label.getKey(),zh=label.getValue().getAsString();
                    if(en.isBlank() || zh.isBlank() || en.length()>256 || zh.length()>256 || (en+zh).matches("(?s).*[§\\r\\n\\x00].*"))throw new IllegalArgumentException("Invalid GUI label");
                    labels.put(en,zh);
                }
                result.add(new Menu(id,List.copyOf(titles),List.copyOf(rules),Map.copyOf(labels)));
            }
            return List.copyOf(result);
        } catch(RuntimeException ex){throw new IOException(path+": "+ex.getMessage(),ex);}
    }
    public static Menu menu(String title,TranslationManager manager) {
        if(title==null)return null;
        String plain=TranslationManager.plain(title);
        if(plain.length()>256)return null;
        // Events category names have user-defined meanings only within this directory.
        for(var menu:manager.guiMenus())if(menu.id().equals("bazaar") &&
                plain.matches("^(?:\\([1-9][0-9]{0,2}/[1-9][0-9]{0,2}\\) )?(?:Oddities (?:➜|➔|→|➤) Events|Events (?:➜|➔|→|➤) .+)$")) {
            var events=tooltipContext(menu,Component.literal("Events"),manager);
            if(BazaarTranslationEngine.navigation(plain,events))return events;
        }
        for(var menu:manager.guiMenus())for(var rule:menu.titles())if(rule.apply(plain)!=null)return menu;
        for(var menu:manager.guiMenus())if(BazaarTranslationEngine.navigation(plain,menu))return menu;
        var community=manager.community().get("gui","",plain);
        if(community!=null)return new Menu("community",List.of(new TranslationManager.Template(plain,community.translation())),manager.commonGuiParagraphs(),Map.of());
        for(var paragraph:manager.community().candidates("gui",plain))if(paragraph.template().apply(plain)!=null)
            return new Menu("community",List.of(paragraph.template()),manager.commonGuiParagraphs(),Map.of());
        return null;
    }
    public static Component title(Component source,TranslationManager manager) {
        var menu=menu(source.getString(),manager);if(menu==null)return source;
        if(menu.id().equals("community")) {
            if(manager.exact("gui",TranslationManager.plain(source.getString())))
                return TextTranslationEngine.translate(source,s->manager.match("gui",null,s));
            return CommunityTranslationEngine.translate(source,"gui",manager);
        }
        // Keep an explicitly configured title ahead of derived product labels.
        for(var rule:menu.titles())if(rule.variables.isEmpty()) {
            var match=rule.apply(TranslationManager.plain(source.getString()));
            if(match!=null)return TextTranslationEngine.apply(source,match);
        }
        if(BazaarTranslationEngine.navigation(TranslationManager.plain(source.getString()),menu))return BazaarTranslationEngine.title(source,menu);
        for(var rule:menu.titles()) {
            var match=rule.apply(TranslationManager.plain(source.getString()));
            if(match!=null)return TextTranslationEngine.apply(source,match);
        }
        return source;
    }
    public static Component button(Component source,Menu menu) {
        return TextTranslationEngine.padded(source,inner->unpaddedButton(inner,menu));
    }
    public static Component detailLine(Component source,Menu menu,TranslationManager manager) {
        Component local=button(source,menu);
        return local!=source || menu==null?local:SystemTranslationEngine.translate(source,"gui-tooltip",manager);
    }
    private static Component unpaddedButton(Component source,Menu menu) {
        if(menu==null)return source;
        Component product=BazaarTranslationEngine.line(source,menu);
        if(product!=source)return product;
        for(var rule:menu.paragraphs()) {
            var match=rule.template().apply(TranslationManager.plain(source.getString()));
            if(match!=null) {
                var translated=TextTranslationEngine.apply(source,new TranslationManager.Match(match.rule(),match.pieces(),rule.styleMappings()==null?Map.of():rule.styleMappings()));
                if(translated!=source)return translated;
            }
        }
        return BazaarTranslationEngine.line(source,menu);
    }
    public static List<TranslationManager.Paragraph> tooltipRules(Menu menu,Component heading,TranslationManager manager) {
        if(menu==null)return List.of();
        if(!menu.id().equals("bazaar"))return menu.paragraphs();
        var header=EnchantmentTranslationEngine.header(heading.getString());
        // A Bazaar product label identifies a display description, not the
        // underlying stack ID. Restrict this to a complete known name + level.
        if(header==null || !menu.labels().containsKey(TranslationManager.plain(heading.getString())))return menu.paragraphs();
        var book=manager.enchantmentBook(header);if(book==null)return menu.paragraphs();
        var rules=new ArrayList<>(menu.paragraphs());
        for(var paragraph:book.paragraphs())rules.add(new TranslationManager.Paragraph(paragraph.source(),paragraph.translation(),paragraph.template(),paragraph.styleMappings()==null?book.styleMappings():paragraph.styleMappings()));
        for(var line:book.lore().entrySet())rules.add(new TranslationManager.Paragraph(List.of(line.getKey()),line.getValue(),new TranslationManager.Template(line.getKey(),line.getValue()),book.styleMappings()));
        return List.copyOf(rules);
    }
    public static Menu tooltipContext(Menu menu,Component heading,TranslationManager manager) {
        if(menu==null || !menu.id().equals("bazaar"))return menu;
        String title=TranslationManager.plain(heading.getString());
        if(!title.equals("Events") && !title.equals(menu.labels().get("Events")))return menu;
        var labels=new HashMap<>(menu.labels());labels.putAll(manager.scope("bazaar-events"));
        return new Menu(menu.id(),menu.titles(),menu.paragraphs(),Map.copyOf(labels));
    }
}
