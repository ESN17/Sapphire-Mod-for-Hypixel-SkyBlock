package com.sorakoi.sbtranslate.translation;

import net.minecraft.network.chat.*;
import java.util.*;
import java.util.function.BiFunction;

/** Builds display-only components. Source lines and item components are never mutated. */
public final class LoreTranslationEngine {
    public record Result(List<Component> lines, List<String> missing, boolean changed) {}

    public static Result translate(List<Component> source, TranslationManager manager, String id,
                                   BiFunction<Component,List<Component>,List<Component>> wrap, boolean showOriginal) {
        return translate(source,manager,id,wrap,showOriginal,false);
    }
    public static Result translate(List<Component> source, TranslationManager manager, String id,
                                   BiFunction<Component,List<Component>,List<Component>> wrap, boolean showOriginal, boolean bookTooltip) {
        return translate(source,manager,id,wrap,showOriginal,bookTooltip,true,List.of());
    }
    public static Result translate(List<Component> source, TranslationManager manager, String id,
                                   BiFunction<Component,List<Component>,List<Component>> wrap, boolean showOriginal, boolean bookTooltip,
                                   boolean itemRules,List<TranslationManager.Paragraph> contextRules) {
        return translate(source,manager,id,wrap,showOriginal,bookTooltip,itemRules,contextRules,s->s);
    }
    public static Result translate(List<Component> source,TranslationManager manager,String id,
                                   BiFunction<Component,List<Component>,List<Component>> wrap,boolean showOriginal,boolean bookTooltip,
                                   boolean itemRules,List<TranslationManager.Paragraph> contextRules,java.util.function.UnaryOperator<Component> contextLine) {
        return translate(source,manager,id,wrap,showOriginal,bookTooltip,itemRules,contextRules,contextLine,s->s);
    }
    public static Result translate(List<Component> source,TranslationManager manager,String id,
                                   BiFunction<Component,List<Component>,List<Component>> wrap,boolean showOriginal,boolean bookTooltip,
                                   boolean itemRules,List<TranslationManager.Paragraph> contextRules,java.util.function.UnaryOperator<Component> contextLine,
                                   java.util.function.UnaryOperator<Component> fallbackLine) {
        return translate(source,manager,id,wrap,showOriginal,bookTooltip,itemRules,contextRules,contextLine,fallbackLine,line->List.of());
    }
    public static Result translate(List<Component> source,TranslationManager manager,String id,
                                   BiFunction<Component,List<Component>,List<Component>> wrap,boolean showOriginal,boolean bookTooltip,
                                   boolean itemRules,List<TranslationManager.Paragraph> contextRules,java.util.function.UnaryOperator<Component> contextLine,
                                   java.util.function.UnaryOperator<Component> fallbackLine,
                                   java.util.function.Function<Component,List<TranslationManager.Paragraph>> fallbackParagraphs) {
        var item = manager.item(id);
        boolean book = bookTooltip || item != null && item.originalName().equals("Enchanted Book");
        TranslationManager.ItemEntry enchantment=null;
        TranslationManager.ItemEntry heldItem=null;
        List<Component> result = new ArrayList<>(); List<String> missing = new ArrayList<>(); boolean changed=false;
        for (int index=0;index<source.size();) {
            Component enchantLine = source.get(index);
            String plain=TranslationManager.plain(enchantLine.getString());
            if(itemRules && plain.startsWith("Held Item: ")) {
                heldItem=manager.petItem(plain.substring(11));
                if(heldItem!=null) {
                    String label=manager.scope("tooltip-labels").getOrDefault("Held Item","Held Item");
                    var pieces=List.of(new TranslationManager.Piece(label+": ",0,11,false),new TranslationManager.Piece(heldItem.name(),11,plain.length(),false));
                    result.add(TextTranslationEngine.structured(enchantLine,pieces));
                    if(showOriginal)result.add(enchantLine);index++;changed=true;continue;
                }
            } else if(plain.isBlank())heldItem=null;
            Component contextual=contextLine.apply(enchantLine);
            if(contextual!=enchantLine) {
                result.addAll(wrap.apply(contextual,List.of(enchantLine)));
                if(showOriginal)result.add(enchantLine);
                index++;changed=true;continue;
            }
            var header = EnchantmentTranslationEngine.header(enchantLine.getString());
            var headerBook=header==null?null:manager.enchantmentBook(header);
            if(headerBook!=null)enchantment=headerBook;
            else if(enchantLine.getString().isBlank())enchantment=null;
            if (book && headerBook != null) item = headerBook;
            Component enchants = EnchantmentTranslationEngine.translate(enchantLine,manager::enchantment);
            if (itemRules && enchants != enchantLine) {
                result.add(enchants);
                if (showOriginal) result.add(enchantLine);
                index++; changed=true; continue;
            }
            boolean matched=false;
            var candidates=new ArrayList<>(contextRules);
            if(itemRules)candidates.addAll(manager.tooltipParagraphs());
            if(itemRules && heldItem!=null)candidates.addAll(heldItem.paragraphs());
            if(itemRules && enchantment!=null && enchantment!=item)for(var p:enchantment.paragraphs()) {
                var styles=p.styleMappings()==null?enchantment.styleMappings():p.styleMappings();
                candidates.add(new TranslationManager.Paragraph(p.source(),p.translation(),p.template(),styles,p.userEdited()));
                // NEU book descriptions omit a final period that live equipped
                // items include. Keep the book rule and add just that variant.
                if(!p.template().source.endsWith(".") && !p.template().source.endsWith("!") && !p.template().source.endsWith(":")) {
                    var lines=new ArrayList<>(p.source());lines.set(lines.size()-1,lines.getLast()+".");
                    String target=p.translation().endsWith(".")?p.translation():p.translation()+".";
                    candidates.add(new TranslationManager.Paragraph(lines,target,new TranslationManager.Template(String.join(" ",lines),target),styles,p.userEdited()));
                }
            }
            if(itemRules && item!=null)candidates.addAll(item.paragraphs());
            candidates.addAll(fallbackParagraphs.apply(enchantLine));
            // Rune dictionaries combine their level requirement and applicable
            // equipment type. Keep that paragraph ahead of the shared level line.
            // Other gear retains the reviewed shared reforge/upgrade overrides.
            if(id!=null && id.matches("[A-Z0-9_]+_RUNE;[1-3]"))
                candidates.sort(Comparator.comparingInt((TranslationManager.Paragraph p)->p.source().size()).reversed());
            // A saved in-game edit must take precedence over the bundled shared
            // tooltip wording for the same source paragraph.
            candidates.sort(Comparator.comparing(TranslationManager.Paragraph::userEdited).reversed());
            String firstLine=plain.strip();
            var window=new ParagraphWindow(source,index);
            for (var paragraph : candidates) {
                // Product-name identity templates are single structured GUI rows.
                // contextLine handles them; joining can swallow category rows.
                if(paragraph.template().variables.contains("name") && paragraph.template().source.equals(paragraph.template().target)
                        && contextRules.contains(paragraph))continue;
                var found=paragraph(window,paragraph,item==null?Map.of():item.styleMappings(),firstLine);
                if(found==null)continue;
                int count=found.count();
                var originals=source.subList(index,index+count);
                result.addAll(wrap.apply(found.text(),originals));
                if (showOriginal) result.addAll(originals);
                index+=count; matched=true; changed=true; break;
            }
            if (matched) continue;
            Component line=source.get(index++);
            Component translated=itemRules ? TextTranslationEngine.translate(line,text -> manager.match("item",id,text)) : line;
            if(itemRules && translated==line && heldItem!=null) {
                var held=heldItem;
                translated=TextTranslationEngine.translate(line,text->{String value=held.lore().get(text);return value==null?null:TranslationManager.replacement("pet-item",text,value);});
            }
            if(itemRules && translated==line)translated=TooltipTranslationEngine.translate(line,manager);
            if(translated==line) {
                translated=fallbackLine.apply(line);
                if(translated==line)result.add(line);else result.addAll(wrap.apply(translated,List.of(line)));
            } else result.add(translated);
            if (translated != line) { changed=true; if (showOriginal) result.add(line); }
            else if (!TranslationManager.plain(line.getString()).isBlank()) missing.add(TranslationManager.plain(line.getString()));
        }
        return new Result(List.copyOf(result),List.copyOf(missing),changed);
    }
    private record ParagraphResult(Component text,int count) {}
    // All candidates for this row inspect the same wrapped text. Build each length once;
    // retain the original styled components until a candidate actually matches.
    private static final class ParagraphWindow {
        final List<Component> lines;
        final int index;
        final List<String> joined=new ArrayList<>();
        boolean exhausted;
        ParagraphWindow(List<Component> lines,int index){this.lines=lines;this.index=index;}
        String text(int count) {
            if(count<=joined.size())return joined.get(count-1);
            if(exhausted || index+count>lines.size())return null;
            String line=TranslationManager.plain(lines.get(index+count-1).getString());
            String previous=count==1?"":joined.get(count-2);
            if(line.isBlank() || previous.length()+line.length()+(count==1?0:1)>2048){exhausted=true;return null;}
            String text=count==1?line:previous+" "+line;
            joined.add(text);return text;
        }
        Component styled(int count) {
            var result=Component.empty();
            for(int i=0;i<count;i++){if(i>0)result.append(" ");result.append(lines.get(index+i));}
            return result;
        }
    }
    private static ParagraphResult paragraph(ParagraphWindow window,TranslationManager.Paragraph paragraph,Map<String,String> fallback,String first) {
        String prefix=paragraph.leadingLiteral(),word=paragraph.requiredWord();
        if(!word.isEmpty() && !first.contains(word))return null;
        if(first.isEmpty() || !first.startsWith(prefix) && !prefix.startsWith(first))return null;
        for(int count=1;count<=16;count++) {
            String plain=window.text(count);
            if(plain==null)break;
            // An exact paragraph cannot recover after the joined text diverges from it.
            if(paragraph.template().variables.isEmpty() && !paragraph.template().source.startsWith(plain)
                    && !paragraph.template().source.startsWith(plain.strip()))break;
            var match=paragraph.template().apply(plain);
            if(match==null) {
                String stripped=plain.strip();
                var trimmed=stripped.equals(plain)?null:paragraph.template().apply(stripped);
                if(trimmed!=null) {
                    var combined=window.styled(count);
                    var padded=TextTranslationEngine.padded(combined,inner->TextTranslationEngine.apply(inner,new TranslationManager.Match(trimmed.rule(),trimmed.pieces(),paragraph.styleMappings()==null?fallback:paragraph.styleMappings())));
                    if(padded!=combined)return new ParagraphResult(padded,count);
                }
                continue;
            }
            var combined=window.styled(count);
            var translated=TextTranslationEngine.apply(combined,new TranslationManager.Match(match.rule(),match.pieces(),paragraph.styleMappings()==null?fallback:paragraph.styleMappings()));
            if(translated!=combined)return new ParagraphResult(translated,count);
        }
        return null;
    }
}
