package com.sorakoi.sbtranslate.translation;

import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Optional exact-match corpus. Local dictionaries are always tried first. */
public final class CommunityTranslationEngine {
    private static final Map<String,java.lang.ref.SoftReference<Index>> CACHE=new LinkedHashMap<>(16,0.75f,true);
    public record Entry(String translation,Map<String,String> styles,TranslationManager.Template numericTemplate) {}
    // Retain both immutable inputs while their merged view is in use. Otherwise
    // GC could clear the soft cache's wrapper while callers still retain its
    // entries, causing a later load to duplicate the same large object graph.
    public record Index(Map<String,Entry> entries,Map<String,List<TranslationManager.Paragraph>> paragraphs,Index preferred,Index supplement) {
        public Entry get(String scope,String npc,String source){
            if(preferred!=null) {
                var exact=preferred.get(scope,npc,source);if(exact!=null)return exact;
                // A reviewed numeric template also outranks an exact Wiki sample.
                // Otherwise examples such as "Duration: 6 Hours" would replace
                // the established translation for "Duration: {{N0}} Hours".
                for(var paragraph:preferred.candidates(scope.equals("npc")?"npc:"+npc:scope,source)) {
                    var match=paragraph.template().apply(source);
                    if(match!=null)return new Entry(match.text(),paragraph.styleMappings(),paragraph.template());
                }
            }
            return entries.get(scope+'\0'+npc+'\0'+source);
        }
        public List<TranslationManager.Paragraph> candidates(String scope,String firstLine) {
            var exact=paragraphs.getOrDefault(scope+'\0'+firstWord(firstLine),List.of());
            var variablePrefix=paragraphs.getOrDefault(scope+'\0'+"*",List.of());
            if(exact.isEmpty())return variablePrefix;
            if(variablePrefix.isEmpty())return exact;
            return java.util.stream.Stream.concat(exact.stream(),variablePrefix.stream()).toList();
        }
    }
    private static String firstWord(String source){String text=source.strip();int space=text.indexOf(' ');String word=space<0?text:text.substring(0,space);return word.contains("{")?"*":word;}
    public static Index empty(){return new Index(Map.of(),Map.of(),null,null);}
    private static boolean forbidden(String text){return text.indexOf('§')>=0 || text.indexOf('\r')>=0 || text.indexOf('\n')>=0 || text.indexOf('\0')>=0;}
    /** Keep reviewed/local corpus entries ahead of supplementary Wiki translations. */
    public static Index merge(Index primary,Index supplement) {
        var entries=new HashMap<>(primary.entries());
        var paragraphs=new HashMap<String,List<TranslationManager.Paragraph>>();
        primary.paragraphs().forEach((k,v)->paragraphs.put(k,new ArrayList<>(v)));
        supplement.entries().forEach(entries::putIfAbsent);
        supplement.paragraphs().forEach((key,values)->{
            String scope=key.substring(0,key.indexOf('\0'));
            for(var paragraph:values) {
                String source=String.join(" ",paragraph.source());
                if(primary.get(scope,"",source)==null)
                    paragraphs.computeIfAbsent(key,k->new ArrayList<>()).add(paragraph);
            }
        });
        paragraphs.replaceAll((k,v)->List.copyOf(v));
        return new Index(Map.copyOf(entries),Map.copyOf(paragraphs),primary,supplement);
    }
    public static Index load(Path path) throws IOException {
        if(!Files.exists(path))return empty();
        try {
            var files=corpusFiles(path);
            // Managers and reloads can share this immutable index. Hash actual
            // bytes so editing a dictionary always invalidates it, even when
            // timestamps or file lengths happen to stay unchanged.
            var digest=java.security.MessageDigest.getInstance("SHA-256");
            for(var file:files)try(var input=Files.newInputStream(file)) {
                byte[] buffer=new byte[65536];int count;
                while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);
            }
            String hash=java.util.HexFormat.of().formatHex(digest.digest());
            synchronized(CACHE) {
                var cached=CACHE.get(hash);var index=cached==null?null:cached.get();
                if(index!=null)return index;
            }
            var result=new HashMap<String,Entry>();
            var paragraphs=new HashMap<String,List<TranslationManager.Paragraph>>();
            for(var file:files) {
            int unusedStyles=0;
            var data=TranslationManager.readObject(file);
            if(data.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Unsupported community schema");
            for(var value:data.getAsJsonArray("entries")) {
                var row=value.getAsJsonObject();String scope=row.get("scope").getAsString(),npc=row.get("npc").getAsString();
                String source=row.get("source").getAsString(),target=row.get("translation").getAsString();
                if(!Set.of("npc","gui","tooltip","system").contains(scope) || scope.equals("npc")!=!npc.isEmpty()
                    || source.isBlank() || target.isBlank() || source.length()>2048 || target.length()>2048
                    || forbidden(source) || forbidden(target) || forbidden(npc))throw new IllegalArgumentException("Invalid community entry");
                var styles=new LinkedHashMap<String,String>();
                TranslationManager.Template numeric=null;
                String styleSource=source,styleTarget=target;
                if(row.has("numericSource")) {
                    styleSource=row.get("numericSource").getAsString();styleTarget=row.get("numericTranslation").getAsString();
                }
                String playerExample=null;
                if(scope.equals("npc")) {
                    var player=java.util.regex.Pattern.compile("\\[(?:[Pp]layer|[Uu]sername)\\]|<(?:[Pp]layer|[Uu]sername)>").matcher(styleSource);
                    if(player.find()) {
                        String token=player.group();
                        if(!player.find() && styleSource.replace(token,"").length()>=2 && styleTarget.indexOf(token)>=0 && styleTarget.indexOf(token)==styleTarget.lastIndexOf(token)) {
                            playerExample=token;styleSource=styleSource.replace(token,"{player}");styleTarget=styleTarget.replace(token,"{player}");
                        }
                    }
                }
                if(row.has("numericSource") || playerExample!=null) {
                    numeric=new TranslationManager.Template(styleSource,styleTarget);
                    var example=numeric.apply(source);
                    // Generic rows themselves contain tokens; exact rows must reproduce their declared translation.
                    if(!source.equals(styleSource) && (example==null || !example.text().equals(target)))
                        throw new IllegalArgumentException("Numeric example does not match entry: "+source);
                }
                for(var style:row.getAsJsonObject("styleMappings").entrySet()) {
                    String from=style.getKey(),to=style.getValue().getAsString();
                    if(playerExample!=null){from=from.replace(playerExample,"{player}");to=to.replace(playerExample,"{player}");}
                    if(TranslationManager.VARIABLE.matcher(from).matches() && from.equals(to))continue;
                    // An edited translation can outlive its color hints. Keep the text and its other valid styles.
                    if(from.isBlank() || to.isBlank() || !styleSource.contains(from) || !styleTarget.contains(to)){
                        unusedStyles++;
                        continue;
                    }
                    styles.put(from,to);
                }
                if(result.putIfAbsent(scope+'\0'+npc+'\0'+source,new Entry(target,Map.copyOf(styles),numeric))!=null)
                    throw new IllegalArgumentException("Duplicate community entry");
                if(!scope.equals("npc"))paragraphs.computeIfAbsent(scope+'\0'+firstWord(source),k->new ArrayList<>())
                    .add(new TranslationManager.Paragraph(List.of(source),target,new TranslationManager.Template(source,target),Map.copyOf(styles)));
                else if(numeric!=null) {
                    var bucket=paragraphs.computeIfAbsent("npc:"+npc+'\0'+firstWord(styleSource),k->new ArrayList<>());
                    String templateSource=styleSource;
                    if(bucket.stream().noneMatch(p->p.template().source.equals(templateSource)))
                        bucket.add(new TranslationManager.Paragraph(List.of(styleSource),styleTarget,numeric,Map.copyOf(styles)));
                }
            }
            if(unusedStyles>0)org.slf4j.LoggerFactory.getLogger("sapphire").warn("Ignored {} unmatched community style mappings in {}",unusedStyles,file.getFileName());
            }
            paragraphs.replaceAll((k,v)->v.stream().sorted(Comparator.comparingInt((TranslationManager.Paragraph p)->p.template().specificity).reversed()).toList());
            var index=new Index(Map.copyOf(result),Map.copyOf(paragraphs),null,null);
            synchronized(CACHE) {
                CACHE.put(hash,new java.lang.ref.SoftReference<>(index));
                while(CACHE.size()>32)CACHE.remove(CACHE.keySet().iterator().next());
            }
            return index;
        } catch(RuntimeException | java.security.NoSuchAlgorithmException ex){throw new IOException(path+": "+ex.getMessage(),ex);}
    }
    /** Bounded flat parts keep the existing per-file JSON size limit intact. */
    public static List<Path> corpusFiles(Path path) throws IOException {
        var manifest=TranslationManager.readObject(path);var files=new ArrayList<Path>();files.add(path);
        long total=Files.size(path);
        if(manifest.has("parts")) {
            var parts=manifest.getAsJsonArray("parts");
            if(parts.size()>64)throw new IOException("Too many community parts");
            var seen=new HashSet<String>();
            String base=path.getFileName().toString().replaceFirst("\\.json$","");
            for(var part:parts) {
                String name=part.getAsString();
                if(!name.matches("[a-z0-9-]+\\.json") || !seen.add(name))throw new IOException("Invalid community part name");
                Path file=path.resolveSibling(base).resolve(name);
                total+=Files.size(file);
                if(total>128L*1024*1024)throw new IOException("Community corpus too large");
                files.add(file);
            }
        }
        return List.copyOf(files);
    }
    public static Component translate(Component source,String scope,TranslationManager manager) {
        return TextTranslationEngine.padded(source,inner->translateExact(inner,scope,manager));
    }
    private static Component translateExact(Component source,String scope,TranslationManager manager) {
        String text=TranslationManager.plain(source.getString()),npc="";int start=0;
        if(scope.equals("chat")) {
            // Only server-formatted, named NPC dialogue. Never replace a matching
            // phrase inside player chat, party messages or an unknown NPC's text.
            if(text.startsWith("[NPC] ")) {
                int colon=text.indexOf(": ",6);if(colon<0 || colon>86)return source;
                npc=text.substring(6,colon);start=colon+2;scope="npc";
                while(start<text.length() && Character.isWhitespace(text.charAt(start)))start++;
            } else scope="system";
        }
        Component display=scope.equals("npc") && source.getStyle().getColor()==null
            ?source.copy().withStyle(s->s.withColor(net.minecraft.ChatFormatting.WHITE)
                .withBold(s.isBold()).withItalic(s.isItalic()).withUnderlined(s.isUnderlined())
                .withStrikethrough(s.isStrikethrough()).withObfuscated(s.isObfuscated())):source;
        var entry=manager.community().get(scope,npc,text.substring(start));
        if(entry==null) {
            if(scope.equals("npc")) {
                for(var paragraph:manager.community().candidates("npc:"+npc,text.substring(start))) {
                    var match=paragraph.template().apply(text.substring(start));if(match==null)continue;
                    var out=TextTranslationEngine.rangeMatch(display,start,text.length(),new TranslationManager.Match(match.rule(),match.pieces(),paragraph.styleMappings()));
                    if(out!=display)return out;
                }
                return source;
            }
            for(var paragraph:manager.community().candidates(scope,text)) {
                var match=paragraph.template().apply(text);if(match==null)continue;
                var out=TextTranslationEngine.apply(source,new TranslationManager.Match(match.rule(),match.pieces(),paragraph.styleMappings()));
                if(out!=source)return out;
            }
            return source;
        }
        // Chat's default text color is white. Resolve reset codes against that base
        // on a display copy, while preserving server components and explicit colors.
        Component out;
        if(entry.numericTemplate()!=null) {
            var match=entry.numericTemplate().apply(text.substring(start));
            if(match==null)return source;
            out=TextTranslationEngine.rangeMatch(display,start,text.length(),new TranslationManager.Match(match.rule(),match.pieces(),entry.styles()));
        } else out=TextTranslationEngine.rangeReplacement(display,start,text.length(),entry.translation(),entry.styles());
        return out==display?source:out;
    }
}
