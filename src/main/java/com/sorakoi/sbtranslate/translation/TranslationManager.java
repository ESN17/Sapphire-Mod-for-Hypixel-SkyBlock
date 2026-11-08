package com.sorakoi.sbtranslate.translation;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Atomic dictionary snapshots, independent of the Minecraft lifecycle. */
public final class TranslationManager {
    private volatile long revision;
    private volatile EntityNameTranslationEngine entityNames=new EntityNameTranslationEngine(Map.of(),Map.of());
    public EntityNameTranslationEngine entityNames(){return entityNames;}
    public long revision(){return revision;}
    private volatile Map<String,String> nameIndex=Map.of();
    private volatile Map<String,ItemEntry> petItems=Map.of();
    private volatile List<SystemTranslationEngine.Rule> systemRules=List.of();
    private volatile CommunityTranslationEngine.Index community=CommunityTranslationEngine.empty();
    private volatile int wikiRuleCount;
    public int wikiRuleCount(){return wikiRuleCount;}
    private volatile int wikiPendingReviewCount;
    public int wikiPendingReviewCount(){return wikiPendingReviewCount;}
    public CommunityTranslationEngine.Index community(){return community;}
    /** Exact imported item names only; new sale products must never use fuzzy fallbacks. */
    public String knownItemName(String english){return nameIndex.get(english);}
    public List<SystemTranslationEngine.Rule> systemRules(){return systemRules;}
    public String translatedName(String english) {
        String exact=nameIndex.get(english);if(exact!=null)return exact;
        exact=scope("mining-events").get(english);if(exact!=null)return exact;
        exact=scope("tooltip-labels").get(english);if(exact!=null)return exact;
        exact=scope("entity-names").get(english);if(exact!=null)return exact;
        int prefix=0;
        while(prefix<english.length() && !Character.isLetterOrDigit(english.codePointAt(prefix)) && english.charAt(prefix)!='[')
            prefix+=Character.charCount(english.codePointAt(prefix));
        if(prefix>0 && prefix<english.length()) {
            String nested=translatedName(english.substring(prefix));if(nested!=null)return english.substring(0,prefix)+nested;
        }
        var header=EnchantmentTranslationEngine.header(english);
        if(header!=null && enchantment(header.name())!=null)return enchantment(header.name())+english.substring(header.name().length());
        return null;
    }
    public record Piece(String text, int start, int end, boolean original) {}
    public record Match(String rule, List<Piece> pieces, Map<String,String> styleMappings) {
        public Match(String rule, List<Piece> pieces) { this(rule, pieces, Map.of()); }
        public String text() { return pieces.stream().map(Piece::text).reduce("", String::concat); }
    }
    public record Paragraph(List<String> source, String translation, Template template, Map<String,String> styleMappings,boolean userEdited,String leadingLiteral,String requiredWord) {
        public Paragraph(List<String> source,String translation,Template template,Map<String,String> styleMappings,boolean userEdited){this(source,translation,template,styleMappings,userEdited,prefix(source),word(source));}
        public Paragraph(List<String> source,String translation,Template template,Map<String,String> styleMappings){this(source,translation,template,styleMappings,false);}
        private static String prefix(List<String> source){String first=source.getFirst();var m=VARIABLE.matcher(first);return (m.find()?first.substring(0,m.start()):first).strip();}
        private static String word(List<String> source){String first=source.getFirst();var m=VARIABLE.matcher(first);return m.find() && m.start()==0?first.substring(m.end()).strip().split("[ {]",2)[0]:"";}
    }
    public record ItemEntry(String name, String originalName, Map<String,String> lore,
                            Map<String,String> styleMappings, List<Paragraph> paragraphs, List<Template> loreTemplates) {}
    private record Snapshot(Map<String,ItemEntry> items, Map<String,Map<String,String>> scopes, List<Template> templates, List<Paragraph> tooltipParagraphs, List<GuiTranslationEngine.Menu> guiMenus, List<Paragraph> commonGuiParagraphs) {}
    private volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of());
    private final Map<String,Optional<Match>> cache = new LinkedHashMap<>(256, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String,Optional<Match>> e) { return size() > 4096; }
    };
    private final Map<String,Optional<Match>> systemCache=new LinkedHashMap<>(256,.75f,true) {
        protected boolean removeEldestEntry(Map.Entry<String,Optional<Match>> e){return size()>4096;}
    };
    synchronized Match systemMatch(String scope,String text,java.util.function.Supplier<Match> compute) {
        String key=scope+'\0'+text;var hit=systemCache.get(key);if(hit!=null)return hit.orElse(null);
        var result=compute.get();systemCache.put(key,Optional.ofNullable(result));return result;
    }
    public static final Pattern VARIABLE = Pattern.compile("(?<!\\{)(?:\\{\\{([A-Za-z][A-Za-z0-9_]*)\\}\\}|\\{([A-Za-z][A-Za-z0-9_]*)\\})(?!\\})");
    private static String variable(Matcher matcher) {return matcher.group(1)!=null ? matcher.group(1) : matcher.group(2);}
    public static final Pattern FORMAT = Pattern.compile("(?i)§[0-9a-fk-or]");
    private static final Pattern ITEM_ID = Pattern.compile("[A-Za-z0-9_;:.-]{1,160}");
    private static final Pattern GEM_ITEM_ID = Pattern.compile("(?:ROUGH|FLAWED|FINE|FLAWLESS|PERFECT)_[A-Z]+_GEM");
    private static final Pattern PET_ITEM_RARITY = Pattern.compile("(?:COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|VERY SPECIAL) PET ITEM");
    public static String plain(String s) { return s.indexOf('§')<0?s:FORMAT.matcher(s).replaceAll(""); }
    public static final class Template {
        final String source, target;
        final Pattern pattern;
        final List<String> variables = new ArrayList<>();
        final int specificity;
        public Template(String source, String target) {
            this(source,target,false);
        }
        Template(String source,String target,boolean styleAnchor) {
            this.source = source; this.target = target;
            Matcher m = VARIABLE.matcher(source); List<String> sourceTokens=new ArrayList<>();
            StringBuilder regex = new StringBuilder("\\A"); int end = 0, literals = 0;
            while (m.find()) {
                String literal = source.substring(end, m.start());
                if (literal.isEmpty() && end != 0) throw new IllegalArgumentException("Adjacent variables: " + source);
                regex.append(Pattern.quote(literal)); literals += literal.length();
                String name = variable(m);
                if (variables.contains(name)) throw new IllegalArgumentException("Duplicate variable: " + name);
                variables.add(name);
                sourceTokens.add(m.group());
                // Numeric stat + icon is one copied span; tolerate old/new glyphs
                // and the server's optional space without accepting arbitrary prose.
                regex.append(name.startsWith("statIcon") ? "([+-]?(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?%?[ \\t]{0,2}[\\p{Co}\\p{S}☘⸕☯]{1,4})"
                    : name.startsWith("icon") ? "([^\\p{L}\\p{N}\\s]{1,8})" : switch (name) {
                    case "roman", "romanFrom", "romanTo" -> "([IVXLCDM]{1,8}|[0-9]{1,3})";
                    case "rankedPlayer" -> "((?:\\[[A-Z][A-Z0-9_+]{0,24}\\] )?[A-Za-z0-9_]{1,16})";
                    case "version" -> "([0-9][A-Za-z0-9._+\\-]{0,47})";
                    case "name", "text", "prefix", "suffix", "player", "location", "event", "mob", "fireSaleItem", "catalogItem" -> "([^\\r\\n]{1,128})";
                    case "deathVictim", "reviver" -> "([A-Za-z0-9_]{1,16})";
                    case "time" -> "([0-9][0-9:., hmsd]{0,31})";
                    case "market", "marketBuy", "marketSell", "marketCount" -> "([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?[kKmMbBtT]?)";
                    default -> "([+-]?(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?%?)";
                });
                end = m.end();
            }
            literals += source.length() - end;
            if (literals < (styleAnchor?1:2) && !variables.isEmpty()) throw new IllegalArgumentException("Template needs literal context: " + source);
            List<String> targetTokens = new ArrayList<>(); m = VARIABLE.matcher(target);
            while (m.find()) targetTokens.add(m.group());
            sourceTokens.sort(String::compareTo); targetTokens.sort(String::compareTo);
            if (!targetTokens.equals(sourceTokens)) throw new IllegalArgumentException("Template variables differ: " + source);
            regex.append(Pattern.quote(source.substring(end))).append("\\z");
            pattern = Pattern.compile(regex.toString()); specificity = literals;
        }
        public Match apply(String input) {
            Matcher m = pattern.matcher(input); if (!m.matches()) return null;
            List<Piece> pieces = new ArrayList<>(); Matcher out = VARIABLE.matcher(target); int end = 0;
            while (out.find()) {
                int group = variables.indexOf(variable(out)) + 1;
                String literal = target.substring(end, out.start()); int start = m.start(group);
                // A constant sign adjacent to a captured number belongs to that number's style span.
                if (!literal.isEmpty() && start > 0 && "+-".indexOf(input.charAt(start-1)) >= 0 && literal.charAt(literal.length()-1)==input.charAt(start-1)) {
                    literal=literal.substring(0,literal.length()-1); start--;
                }
                if (!literal.isEmpty()) pieces.add(new Piece(literal, 0, input.length(), false));
                pieces.add(new Piece(input.substring(start,m.end(group)), start, m.end(group), true)); end = out.end();
            }
            if (end < target.length()) pieces.add(new Piece(target.substring(end), 0, input.length(), false));
            return new Match("template:" + source, List.copyOf(pieces));
        }
        public Match applyNamePrefix(String input) {
            String regex=pattern.pattern();
            Matcher prefix=Pattern.compile(regex.substring(0,regex.length()-2)).matcher(input);
            if (!prefix.lookingAt() || prefix.end()<input.length() && Character.isLetterOrDigit(input.charAt(prefix.end()))) return null;
            Match match=apply(input.substring(0,prefix.end()));
            if (prefix.end()==input.length()) return match;
            List<Piece> pieces=new ArrayList<>(match.pieces());
            pieces.add(new Piece(input.substring(prefix.end()),prefix.end(),input.length(),true));
            return new Match(match.rule(),List.copyOf(pieces));
        }
    }
    public static JsonObject readObject(Path path) throws IOException {
        if (Files.size(path) > 8 * 1024 * 1024) throw new IOException("JSON too large: " + path);
        try (var reader = new com.google.gson.stream.JsonReader(Files.newBufferedReader(path))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement value = readStrict(reader);
            if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) throw new IOException("Trailing JSON: " + path);
            return value.getAsJsonObject();
        } catch (IOException | RuntimeException e) { throw new IOException(path + ": " + e.getMessage(), e); }
    }
    private static JsonElement readStrict(com.google.gson.stream.JsonReader r) throws IOException {
        return switch (r.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject o = new JsonObject(); r.beginObject();
                while (r.hasNext()) { String k = r.nextName(); if (o.has(k)) throw new IOException("Duplicate key at " + r.getPath()); o.add(k, readStrict(r)); }
                r.endObject(); yield o;
            }
            case BEGIN_ARRAY -> { JsonArray a = new JsonArray(); r.beginArray(); while (r.hasNext()) a.add(readStrict(r)); r.endArray(); yield a; }
            case STRING -> new JsonPrimitive(r.nextString());
            case NUMBER -> new JsonPrimitive(new java.math.BigDecimal(r.nextString()));
            case BOOLEAN -> new JsonPrimitive(r.nextBoolean());
            case NULL -> { r.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IOException("Invalid JSON at " + r.getPath());
        };
    }
    private static Map<String,String> strings(JsonObject object) {
        Map<String,String> result = new LinkedHashMap<>();
        for (var e : object.entrySet()) {
            if (!e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected string: " + e.getKey());
            String value = e.getValue().getAsString();
            if (e.getKey().length() > 2048 || value.length() > 2048 || value.contains("\u0000")) throw new IllegalArgumentException("Invalid text: " + e.getKey());
            if (plain(value).contains("§")) throw new IllegalArgumentException("Invalid format code: " + e.getKey());
            if (!value.isBlank() && result.putIfAbsent(plain(e.getKey()), value) != null) throw new IllegalArgumentException("Duplicate normalized key: " + e.getKey());
        }
        return Collections.unmodifiableMap(result);
    }
    private static List<Paragraph> paragraphs(JsonObject object) {
        return paragraphs(object,128);
    }
    static List<Paragraph> paragraphs(JsonObject object, int limit) {
        if (!object.has("loreParagraphs")) return List.of();
        List<Paragraph> result = new ArrayList<>(); Set<List<String>> seen = new HashSet<>();
        var entries = object.getAsJsonArray("loreParagraphs");
        if (entries.size() > limit) throw new IllegalArgumentException("Too many lore paragraphs");
        for (var entry : entries) {
            var p = entry.getAsJsonObject(); List<String> source = new ArrayList<>();
            for (var line : p.getAsJsonArray("source")) {
                if (!line.isJsonPrimitive() || !line.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Paragraph source must be strings");
                String text = plain(line.getAsString());
                if (text.isBlank() || text.length() > 2048 || text.contains("\n") || text.contains("\r")) throw new IllegalArgumentException("Invalid paragraph source line");
                source.add(text);
            }
            if (source.isEmpty() || source.size() > 16 || String.join(" ",source).length() > 2048 || !seen.add(List.copyOf(source))) throw new IllegalArgumentException("Invalid or duplicate paragraph source");
            var target = p.get("translation");
            if (target == null || !target.isJsonPrimitive() || !target.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Paragraph translation must be a string");
            String text = target.getAsString();
            if (text.length() > 2048 || text.contains("§") || text.contains("\n") || text.contains("\r") || text.contains("\u0000")) throw new IllegalArgumentException("Invalid paragraph translation");
            Map<String,String> mappings=p.has("styleMappings") ? strings(p.getAsJsonObject("styleMappings")) : null;
            if (!text.isBlank()) result.add(new Paragraph(List.copyOf(source), text, new Template(String.join(" ",source),text),mappings,p.has("userEdited") && p.get("userEdited").getAsBoolean()));
        }
        result.sort(Comparator.comparingInt((Paragraph p) -> p.source().size()).reversed());
        return List.copyOf(result);
    }
    public synchronized void load(Path configDir) throws IOException {
        Map<String,ItemEntry> items = new HashMap<>(); Map<String,Map<String,String>> scopes = new HashMap<>();
        Map<String,ItemEntry> newPetItems=new HashMap<>();
        List<Template> templates = new ArrayList<>(); Path root = configDir.resolve("translations");
        Files.createDirectories(root.resolve("items"));
        try (var files = Files.list(root.resolve("items"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                try {
                    JsonObject o = readObject(p);
                    if (o.has("schemaVersion") && o.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("Unsupported schema");
                    String id = o.get("id").getAsString();
                    if (!ITEM_ID.matcher(id).matches()) throw new IllegalArgumentException("Invalid item ID");
                    Map<String,String> lore = o.has("lore") ? strings(o.getAsJsonObject("lore")) : Map.of();
                    List<Template> itemTemplates = lore.entrySet().stream().filter(e -> VARIABLE.matcher(e.getKey()).find())
                        .map(e -> new Template(e.getKey(),e.getValue())).sorted(Comparator.comparingInt((Template t) -> t.specificity).reversed().thenComparing(t -> t.source)).toList();
                    ItemEntry item = new ItemEntry(o.has("name") ? o.get("name").getAsString() : "",
                        o.has("_original_name") ? plain(o.get("_original_name").getAsString()) : "",
                        lore, o.has("styleMappings") ? strings(o.getAsJsonObject("styleMappings")) : Map.of(), paragraphs(o), itemTemplates);
                    for (var mapping : item.styleMappings().entrySet()) {
                        if (mapping.getKey().isBlank() || mapping.getValue().contains("§") || mapping.getKey().contains("\n") || mapping.getValue().contains("\n")) throw new IllegalArgumentException("Invalid style mapping");
                    }
                    if (items.putIfAbsent(id, item) != null) throw new IllegalArgumentException("Duplicate ID: " + id);
                    boolean petItem=id.startsWith("PET_ITEM_");
                    if(!petItem && o.has("_original_lore"))for(var line:o.getAsJsonArray("_original_lore"))
                        if(PET_ITEM_RARITY.matcher(plain(line.getAsString()).strip()).matches()){petItem=true;break;}
                    if(petItem && !item.originalName().isBlank())newPetItems.putIfAbsent(item.originalName(),item);
                } catch (RuntimeException e) { throw new IOException(p + ": " + e.getMessage(), e); }
            }
        }
        for (String scope : List.of("global", "gui", "chat", "scoreboard", "tablist", "mining-events", "actionbar", "title", "subtitle", "bossbar", "reforges", "enchantments", "enchantment-ids", "name-modifiers","tooltip-labels", "runes", "dyes", "locations", "skyblock-months", "sidebar-formats", "bazaar-events", "entity-names", "entity-labels")) {
            Path p = root.resolve(scope + ".json");
            try { scopes.put(scope, Files.exists(p) ? strings(readObject(p)) : Map.of()); }
            catch (RuntimeException e) { throw new IOException(p + ": " + e.getMessage(), e); }
        }
        Path p = root.resolve("templates.json");
        if (Files.exists(p)) try {
            for (var e : strings(readObject(p)).entrySet()) templates.add(new Template(e.getKey(), e.getValue()));
        } catch (RuntimeException e) { throw new IOException(p + ": " + e.getMessage(), e); }
        templates.sort(Comparator.comparingInt((Template t) -> t.specificity).reversed().thenComparing(t -> t.source));
        Path sharedPath=root.resolve("tooltip-paragraphs.json");
        List<Paragraph> shared;
        try {shared=Files.exists(sharedPath) ? paragraphs(readObject(sharedPath),1024) : List.of();}
        catch(RuntimeException e){throw new IOException(sharedPath+": "+e.getMessage(),e);}
        var menus=new ArrayList<>(GuiTranslationEngine.load(root.resolve("gui-menus.json"),items,scopes));
        menus.addAll(GuiTranslationEngine.load(root.resolve("entity-menus.json"),items,scopes));
        var guiPath=root.resolve("gui-menus.json");
        var commonGui=Files.exists(guiPath)?paragraphs(readObject(guiPath),1024):List.<Paragraph>of();
        var newSystemRules=new ArrayList<>(SystemTranslationEngine.load(root.resolve("system-messages.json")));
        newSystemRules.addAll(SystemTranslationEngine.load(root.resolve("death-messages.json")));
        newSystemRules.addAll(SystemTranslationEngine.load(root.resolve("creature-messages.json")));
        // Stable ordering leaves existing translations ahead of equal-specificity additions.
        newSystemRules.sort(Comparator.comparingInt((SystemTranslationEngine.Rule r)->r.template().specificity).reversed());
        var newWiki=CommunityTranslationEngine.load(root.resolve("community-wiki.json"));
        var wikiPath=root.resolve("community-wiki.json");int newWikiPending=0;
        if(Files.exists(wikiPath)) {
            var manifest=readObject(wikiPath);
            try {if(manifest.has("heldReviewRows"))newWikiPending=manifest.get("heldReviewRows").getAsInt();}
            catch(RuntimeException invalid){throw new IOException("Invalid Wiki review count",invalid);}
            if(newWikiPending<0 || newWikiPending>1000000)throw new IOException("Invalid Wiki review count");
        }
        var newCommunity=CommunityTranslationEngine.merge(
            CommunityTranslationEngine.load(root.resolve("community-skyzh.json")),
            newWiki);
        Map<String,String> names=new HashMap<>();
        for(var entry:items.values())if(!entry.originalName().isBlank() && !entry.name().isBlank())names.putIfAbsent(entry.originalName(),entry.name());
        // Mining messages carry the gemstone glyph separately from the item name.
        // Register the same known item without that glyph, without adding an icon
        // to a message which did not contain one in the first place.
        for(var entry:items.entrySet())if(GEM_ITEM_ID.matcher(entry.getKey()).matches()) {
            var item=entry.getValue();String original=item.originalName(),translated=item.name();
            if(!original.isEmpty() && !translated.isEmpty() && Character.getType(original.codePointAt(0))==Character.PRIVATE_USE
                && original.codePointAt(0)==translated.codePointAt(0))
                names.putIfAbsent(original.substring(Character.charCount(original.codePointAt(0))).stripLeading(),
                    translated.substring(Character.charCount(translated.codePointAt(0))).stripLeading());
        }
        for(var entry:items.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var item=entry.getValue();String en=item.originalName(),zh=item.name();
            if(en.startsWith("[Lvl {LVL}] ") && zh.startsWith("[等级 {LVL}] "))
                names.putIfAbsent(en.substring(en.indexOf("] ")+2),zh.substring(zh.indexOf("] ")+2));
            for(String quality:List.of("BRONZE","SILVER","GOLD","DIAMOND")) {
                String translated=item.styleMappings().get(quality);
                if(entry.getKey().endsWith("_"+quality) && en.endsWith(" "+quality) && translated!=null && zh.endsWith(" "+translated))
                    names.putIfAbsent(en.substring(0,en.length()-quality.length()-1),zh.substring(0,zh.length()-translated.length()-1));
            }
        }
        snapshot = new Snapshot(Map.copyOf(items), Map.copyOf(scopes), List.copyOf(templates), shared, menus, commonGui);
        entityNames=new EntityNameTranslationEngine(scopes.get("entity-names"),scopes.get("entity-labels"));
        nameIndex=Map.copyOf(names);petItems=Map.copyOf(newPetItems);systemRules=newSystemRules;community=newCommunity;wikiRuleCount=newWiki.entries().size();wikiPendingReviewCount=newWikiPending;cache.clear();systemCache.clear();revision++;
    }
    public int itemCount() { return snapshot.items.size(); }
    public java.util.Collection<ItemEntry> itemEntries(){return snapshot.items.values();}
    public ItemEntry item(String id) { return id == null ? null : snapshot.items.get(id); }
    public ItemEntry petItem(String english) {
        return petItems.get(english);
    }
    public boolean exact(String scope, String text) { return snapshot.scopes.getOrDefault(scope, Map.of()).containsKey(text); }
    public String reforge(String prefix) { return snapshot.scopes.getOrDefault("reforges",Map.of()).get(prefix); }
    public String enchantment(String name) { return snapshot.scopes.getOrDefault("enchantments",Map.of()).get(name); }
    public Map<String,String> scope(String name) { return snapshot.scopes.getOrDefault(name,Map.of()); }
    public List<Paragraph> tooltipParagraphs() { return snapshot.tooltipParagraphs; }
    public List<GuiTranslationEngine.Menu> guiMenus() { return snapshot.guiMenus; }
    public List<Paragraph> commonGuiParagraphs() { return snapshot.commonGuiParagraphs; }
    public ItemEntry enchantmentBook(EnchantmentTranslationEngine.Header header) {
        if (header == null) return null;
        var current = snapshot;
        String id = current.scopes.getOrDefault("enchantment-ids",Map.of()).get(header.name());
        if (id == null) return null;
        var exact = current.items.get(id + ";" + header.level());
        // Progression enchantments only have a tier-I book; numeric descriptions
        // still use live captures when the enchantment has leveled up on gear.
        return exact != null ? exact : current.items.get(id + ";1");
    }
    public synchronized Match match(String scope, String id, String english) {
        if (english.isBlank() || english.length() > 2048) return null;
        String key = scope + '\0' + id + '\0' + english;
        Optional<Match> cached = cache.get(key); if (cached != null) return cached.orElse(null);
        ItemEntry item = item(id); String translated = item == null ? null : item.lore.get(english); String rule = "item:" + id;
        if (translated == null && item != null && !scope.equals("chat")) for (Template template : item.loreTemplates()) {
            Match match = template.apply(english);
            if (match != null) {
                match = new Match(match.rule(),match.pieces(),item.styleMappings());
                cache.put(key,Optional.of(match)); return match;
            }
        }
        if (translated == null) { translated = snapshot.scopes.getOrDefault(scope, Map.of()).get(english); rule = scope; }
        if (translated == null && !scope.equals("chat")) { translated = snapshot.scopes.getOrDefault("global", Map.of()).get(english); rule = "global"; }
        Match match = translated == null || translated.equals(english) ? null : replacement(rule, english, translated);
        if (match == null && !scope.equals("chat")) for (Template t : snapshot.templates) { match = t.apply(english); if (match != null) break; }
        if (match != null && item != null && !item.styleMappings().isEmpty()) match = new Match(match.rule(),match.pieces(),item.styleMappings());
        cache.put(key, Optional.ofNullable(match)); return match;
    }
    public static Match replacement(String rule, String source, String target) { return new Match(rule, List.of(new Piece(target, 0, source.length(), false))); }
    public String name(String id, String fallback) { ItemEntry e = item(id); return e == null || e.name.isBlank() ? fallback : e.name; }
    public String lore(String id, String english) { Match m = match("item", id, english); return m == null ? english : m.text(); }
    public synchronized int cacheSize() { return cache.size(); }
}
