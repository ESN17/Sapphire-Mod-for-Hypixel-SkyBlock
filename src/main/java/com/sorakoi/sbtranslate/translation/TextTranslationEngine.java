package com.sorakoi.sbtranslate.translation;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.ChatFormatting;
import java.util.*;
import java.util.regex.Pattern;

/** Display copies use semantic spans, never proportional Chinese character offsets. */
public final class TextTranslationEngine {
    /** Snapshot resolved display text without modifying server-owned components.
     * HUD packets may use translatable/fallback contents alongside literal text.
     * Keep inherited styles and click/hover events on each resolved span.
     */
    public static Component resolvedDisplay(Component source) {
        if(literalTree(source) && TranslationManager.plain(source.getString()).indexOf('§')<0)return source;
        var result=Component.empty();
        var chunk=new StringBuilder();Style[] current={null};
        // Use the renderer's parser: it consumes every section-sign/code pair,
        // including unknown scoreboard owner markers such as §v. Filtering only
        // recognized colors leaves invisible characters in dictionary keys.
        net.minecraft.util.StringDecomposer.iterateFormatted(source,Style.EMPTY,(index,style,point)->{
            if(current[0]!=null && !current[0].equals(style) && !chunk.isEmpty()) {
                result.append(Component.literal(chunk.toString()).setStyle(current[0]));chunk.setLength(0);
            }
            current[0]=style;chunk.appendCodePoint(point);return true;
        });
        if(!chunk.isEmpty())result.append(Component.literal(chunk.toString()).setStyle(current[0]));
        return result;
    }
    /** Work on complete visual lines, even when a newline crosses component siblings. */
    public static Component lines(Component source,java.util.function.UnaryOperator<Component> mapper) {
        if(!literalTree(source))return source;
        String text=TranslationManager.plain(source.getString());
        if(!text.contains("\n"))return mapper.apply(source);
        var spans=spans(source);var result=Component.empty();boolean changed=false;
        for(int start=0;start<=text.length();) {
            int end=text.indexOf('\n',start);if(end<0)end=text.length();
            var line=Component.empty();appendOriginal(line,spans,start,end);
            Component translated=mapper.apply(line);changed|=translated!=line;result.append(translated);
            if(end==text.length())break;
            appendOriginal(result,spans,end,end+1);start=end+1;
        }
        return changed?result:source;
    }
    /** Remove display-only scoreboard fillers, retaining every visible character's style. */
    public static Component cleanHud(Component source) {
        if(!literalTree(source))return source;
        var result=Component.empty();boolean changed=false;
        for(var span:spans(source)) {
            String clean=span.text.codePoints().filter(c->c!='⚽' && Character.getType(c)!=Character.FORMAT)
                .collect(StringBuilder::new,(b,c)->b.appendCodePoint(Character.isSpaceChar(c)?' ':c),StringBuilder::append).toString();
            changed|=!clean.equals(span.text);result.append(Component.literal(clean).setStyle(span.style));
        }
        return changed?result:source;
    }
    public static Component padded(Component source,java.util.function.UnaryOperator<Component> mapper) {
        if(!literalTree(source))return source;
        String text=TranslationManager.plain(source.getString());int start=0,end=text.length();
        while(start<end && Character.isWhitespace(text.charAt(start)))start++;
        while(end>start && Character.isWhitespace(text.charAt(end-1)))end--;
        if(start==0 && end==text.length())return mapper.apply(source);
        if(start==end)return source;
        var spans=spans(source);var inner=Component.empty();appendOriginal(inner,spans,start,end);
        Component translated=mapper.apply(inner);if(translated==inner)return source;
        var result=Component.empty();appendOriginal(result,spans,0,start);result.append(translated);appendOriginal(result,spans,end,text.length());return result;
    }
    public static Component translate(Component source, java.util.function.Function<String,TranslationManager.Match> lookup) {
        Component whole = apply(source,lookup.apply(TranslationManager.plain(source.getString())));
        if (whole != source || !(source.getContents() instanceof PlainTextContents literal)) return whole;
        MutableComponent result = source.plainCopy().setStyle(source.getStyle()); boolean changed = false;
        if (!source.getSiblings().isEmpty()) {
            Component own = apply(source.plainCopy().setStyle(source.getStyle()),lookup.apply(TranslationManager.plain(literal.text())));
            if (!own.getString().equals(literal.text())) { result = own.copy(); changed = true; }
            for (Component sibling : source.getSiblings()) {
                Component translated = translate(sibling,lookup); changed |= translated != sibling; result.append(translated);
            }
        } else if (literal.text().contains("\n")) {
            result = Component.empty().setStyle(source.getStyle()); String[] lines = literal.text().split("\n",-1);
            for (int i=0;i<lines.length;i++) {
                if(i>0)result.append("\n"); Component line=Component.literal(lines[i]).setStyle(source.getStyle());
                Component translated=apply(line,lookup.apply(TranslationManager.plain(lines[i])));changed|=translated!=line;result.append(translated);
            }
        }
        return changed ? result : source;
    }
    private record Span(String text, Style style, int start, int end) {}
    private static boolean literalTree(Component c) {
        return c.getContents() instanceof PlainTextContents && c.getSiblings().stream().allMatch(TextTranslationEngine::literalTree);
    }
    private static List<Span> spans(Component c) {
        List<Span> spans = new ArrayList<>(); int[] offset = {0};
        c.visit((style, text) -> {
            Style current = style; StringBuilder chunk = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '§' && i + 1 < text.length()) {
                    ChatFormatting f = ChatFormatting.getByCode(text.charAt(i + 1));
                    if (f != null) {
                        if (!chunk.isEmpty()) { String s = chunk.toString(); spans.add(new Span(s,current,offset[0],offset[0]+s.length())); offset[0]+=s.length(); chunk.setLength(0); }
                        current = f == ChatFormatting.RESET ? style : current.applyLegacyFormat(f); i++; continue;
                    }
                }
                chunk.append(text.charAt(i));
            }
            if (!chunk.isEmpty()) { String s = chunk.toString(); spans.add(new Span(s,current,offset[0],offset[0]+s.length())); offset[0]+=s.length(); }
            return Optional.empty();
        }, Style.EMPTY);
        return spans;
    }
    private record Anchor(int sourceStart, int sourceEnd, int targetStart, int targetEnd, boolean copy) {}
    private record Position(int start, int end) {}
    private static final Pattern TOKENS = Pattern.compile("(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?|[IVXLCDM]+(?:-[IVXLCDM]+)+|[A-Za-z]+(?:'[A-Za-z]+)?|[\\p{Co}\\p{S}\\p{P}\\p{No}\\p{IsGreek}\\p{IsCyrillic}ℏඞ४]");

    private static Map<String,List<Position>> tokens(String text) {
        Map<String,List<Position>> result = new LinkedHashMap<>(); var matcher = TOKENS.matcher(text);
        while (matcher.find()) result.computeIfAbsent(matcher.group(), key -> new ArrayList<>()).add(new Position(matcher.start(),matcher.end()));
        return result;
    }
    private static boolean overlaps(Anchor a, Anchor b) {
        return a.sourceStart < b.sourceEnd && b.sourceStart < a.sourceEnd || a.targetStart < b.targetEnd && b.targetStart < a.targetEnd;
    }
    private static Style uniformStyle(List<Span> spans, int start, int end) {
        Style result = null;
        for (Span span : spans) {
            int a = Math.max(start,span.start), b = Math.min(end,span.end);
            if (b <= a || span.text.substring(a-span.start,b-span.start).isBlank()) continue;
            if (result != null && !result.equals(span.style)) return null;
            result = span.style;
        }
        return result;
    }
    private static void appendOriginal(MutableComponent result, List<Span> spans, int start, int end) {
        for (Span span : spans) {
            int a = Math.max(start,span.start), b = Math.min(end,span.end);
            if (b > a) result.append(Component.literal(span.text.substring(a-span.start,b-span.start)).setStyle(span.style));
        }
    }
    private static boolean appendRainbowWord(MutableComponent result,List<Span> spans,int start,int end,String text,String translated) {
        // Only explicitly mapped, letter-by-letter decorative rainbow words qualify.
        // Semantic multicolor phrases still require individual word mappings.
        if(!text.substring(start,end).matches("[A-Za-z]{3,}"))return false;
        List<Style> palette=new ArrayList<>();
        for(var span:spans)if(span.start<end && start<span.end) {
            if(span.style.getColor()==null)return false;
            if(!palette.isEmpty() && !palette.getFirst().withColor(span.style.getColor()).equals(span.style))return false;
            if(palette.isEmpty() || !palette.getLast().equals(span.style))palette.add(span.style);
        }
        if(palette.stream().map(Style::getColor).distinct().count()<3)return false;
        int[] codePoints=translated.codePoints().toArray();
        for(int i=0;i<codePoints.length;i++) {
            int color=codePoints.length<=1?0:(int)Math.round((double)i*(palette.size()-1)/(codePoints.length-1));
            result.append(Component.literal(new String(Character.toChars(codePoints[i]))).setStyle(palette.get(color)));
        }
        return true;
    }
    private static List<Position> positions(String text, String fragment, boolean wordBoundaries, List<Anchor> anchors, boolean sourceSide,List<Span> spans) {
        if(fragment.isEmpty())return List.of();
        List<Position> found=new ArrayList<>();
        for (int from=0;from<=text.length()-fragment.length();) {
            int index=text.indexOf(fragment,from); if(index<0) break;
            from=index+1; int end=index+fragment.length();
            boolean numericUnit=fragment.matches("[smxMkKB](?:[^A-Za-z].*)?") && index>0 && Character.isDigit(text.charAt(index-1));
            boolean styledStart=sourceSide && spans.stream().anyMatch(s->s.start==index);
            boolean styledEnd=sourceSide && spans.stream().anyMatch(s->s.end==end);
            if (wordBoundaries && (Character.isLetterOrDigit(fragment.charAt(0)) && index>0 && Character.isLetterOrDigit(text.charAt(index-1)) && !numericUnit && !styledStart
                || Character.isLetterOrDigit(fragment.charAt(fragment.length()-1)) && end<text.length() && Character.isLetterOrDigit(text.charAt(end)) && !styledEnd)) continue;
            // A longer semantic phrase owns its contained words on both sides.
            if(anchors.stream().anyMatch(a -> (sourceSide ? a.sourceStart : a.targetStart)<=index
                && end<=(sourceSide ? a.sourceEnd : a.targetEnd)))continue;
            found.add(new Position(index,end));
        }
        return found;
    }
    public static Component apply(Component source, TranslationManager.Match match) {
        return apply(source,match,reason->{});
    }
    private static Component fallback(Component source,java.util.function.Consumer<String> diagnostic,String reason) {
        diagnostic.accept(reason);return source;
    }
    public static Component apply(Component source, TranslationManager.Match match,java.util.function.Consumer<String> diagnostic) {
        if (match == null || !literalTree(source)) return source;
        List<Span> spans = spans(source); if (spans.isEmpty()) return source;
        String original = TranslationManager.plain(source.getString()), target = match.text();
        // Match offsets refer to one exact UTF-16 snapshot, not to whichever
        // string happens to be displayed later. Reject stale captures before
        // accessing characters or copying interactive spans. Never clamp them:
        // doing so could attach a command/color to the wrong translated word.
        if(!original.equals(spans.stream().map(Span::text).collect(java.util.stream.Collectors.joining())))
            return fallback(source,diagnostic,"Display span offsets do not match source text");
        for(var piece:match.pieces())if(piece.original()
            && (piece.start()<0 || piece.end()<piece.start() || piece.end()>original.length()
                || piece.end()-piece.start()!=piece.text().length()
                || !original.regionMatches(piece.start(),piece.text(),0,piece.text().length())))
            return fallback(source,diagnostic,"Capture offsets do not match source text");
        // Tables sometimes repeat the same label in several rarity colors. Only
        // when literal substitutions reproduce the entire target exactly can we
        // prove that each occurrence stayed in its original position.
        var literals=match.styleMappings().entrySet().stream()
            .filter(e->!e.getKey().isEmpty() && !TranslationManager.VARIABLE.matcher(e.getKey()).find())
            .sorted(Comparator.<Map.Entry<String,String>>comparingInt(e->e.getKey().length()).reversed()).toList();
        record Substitution(int start,int end,String text,Style style) {}
        var substitutions=new ArrayList<Substitution>();var substitutedText=new StringBuilder();int cursor=0;boolean validSubstitution=true;
        while(cursor<original.length()) {
            Map.Entry<String,String> selected=null;
            for(var e:literals)if(original.startsWith(e.getKey(),cursor)){selected=e;break;}
            if(selected==null){substitutedText.append(original.charAt(cursor++));continue;}
            int end=cursor+selected.getKey().length();Style style=uniformStyle(spans,cursor,end);
            if(style==null){validSubstitution=false;break;}
            substitutedText.append(selected.getValue());substitutions.add(new Substitution(cursor,end,selected.getValue(),style));cursor=end;
        }
        if(validSubstitution && !substitutions.isEmpty() && substitutedText.toString().equals(target)) {
            MutableComponent substituted=Component.empty();cursor=0;
            for(var replacement:substitutions) {
                appendOriginal(substituted,spans,cursor,replacement.start());
                substituted.append(Component.literal(replacement.text()).setStyle(replacement.style()));cursor=replacement.end();
            }
            appendOriginal(substituted,spans,cursor,original.length());return substituted;
        }
        List<Anchor> anchors = new ArrayList<>(); int offset = 0;
        for (var piece : match.pieces()) {
            if (piece.original()) anchors.add(new Anchor(piece.start(),piece.end(),offset,offset+piece.text().length(),true));
            offset += piece.text().length();
        }
        // English ordinal suffixes have no separate Chinese word (6th anniversary -> 6周年).
        // Their style belongs to the copied number, not to the surrounding sentence's base style.
        for(var a:List.copyOf(anchors)) {
            if(a.sourceEnd>0 && Character.isDigit(original.charAt(a.sourceEnd-1))) {
                var suffix=Pattern.compile("(?:st|nd|rd|th)\\b").matcher(original.substring(a.sourceEnd));
                if(suffix.lookingAt() && !target.contains(suffix.group()))
                    anchors.add(new Anchor(a.sourceEnd,a.sourceEnd+suffix.end(),a.targetEnd,a.targetEnd,false));
            }
            // Unchanged units adjacent to a proven capture follow that capture even
            // when another occurrence of the same unit has a different color.
            if(a.sourceEnd<original.length() && a.targetEnd<target.length()) {
                char unit=original.charAt(a.sourceEnd);
                if("smx%+".indexOf(unit)>=0 && target.charAt(a.targetEnd)==unit
                    && (a.sourceEnd+1==original.length() || !Character.isLetter(original.charAt(a.sourceEnd+1))))
                    anchors.add(new Anchor(a.sourceEnd,a.sourceEnd+1,a.targetEnd,a.targetEnd+1,true));
            }
        }
        // Gemstone slot lists are structured display data. If the entire trailing
        // bracket list is unchanged, copy it as one span, including filled/empty colors.
        var slots=Pattern.compile("(?:\\[[^\\[\\]\\r\\n]*\\]\\s*)+$").matcher(original);
        if(slots.find() && target.endsWith(slots.group())) {
            Anchor tail=new Anchor(slots.start(),slots.end(),target.length()-slots.group().length(),target.length(),true);
            if(anchors.stream().noneMatch(a->overlaps(tail,a)))anchors.add(tail);
        }
        // A uniform explanatory clause may contain live numeric slots. Match the
        // complete clause, and collapse its captures only when their source and
        // destination ranges are both wholly inside the same proven style span.
        var captures=List.copyOf(anchors);var namedRanges=new HashMap<String,Position>();
        if(match.rule().startsWith("template:")) {
            String patternSource=match.rule().substring("template:".length());
            var full=new TranslationManager.Template(patternSource,patternSource);var values=full.pattern.matcher(original);
            if(values.matches())for(int i=0;i<full.variables.size();i++)namedRanges.put(full.variables.get(i),new Position(values.start(i+1),values.end(i+1)));
        }
        for(var entry:match.styleMappings().entrySet().stream().filter(e->TranslationManager.VARIABLE.matcher(e.getKey()).find())
                .sorted(Comparator.<Map.Entry<String,String>>comparingInt(e->e.getKey().length()).reversed()).toList()) {
            TranslationManager.Template template;
            try {template=new TranslationManager.Template(entry.getKey(),entry.getValue(),true);}
            catch(IllegalArgumentException invalid){return fallback(source,diagnostic,"Invalid style template: "+entry.getKey());}
            String regex=template.pattern.pattern();var finder=Pattern.compile(regex.substring(2,regex.length()-2)).matcher(original);
            var candidates=new ArrayList<java.util.regex.MatchResult>();
            while(finder.find()) {
                boolean bound=true;
                for(int i=0;i<template.variables.size();i++) {
                    var range=namedRanges.get(template.variables.get(i));
                    if(range!=null && (range.start!=finder.start(i+1) || range.end!=finder.end(i+1)))bound=false;
                }
                if(bound)candidates.add(finder.toMatchResult());
            }
            if(candidates.isEmpty())continue;
            if(candidates.size()!=1)return fallback(source,diagnostic,"Ambiguous style template source: "+entry.getKey());
            var found=candidates.getFirst();int start=found.start(),end=found.end();var local=template.apply(found.group());String fragment=local.text();
            Integer pinned=null;int localOffset=0;
            for(var piece:local.pieces()) {
                if(piece.original())for(var anchor:captures)if(anchor.copy && anchor.sourceStart==start+piece.start() && anchor.sourceEnd==start+piece.end()) {
                    int proposed=anchor.targetStart-localOffset;
                    if(pinned!=null && pinned!=proposed)return fallback(source,diagnostic,"Style template changes capture order: "+entry.getKey());
                    pinned=proposed;
                }
                localOffset+=piece.text().length();
            }
            int at=pinned==null?target.indexOf(fragment):pinned;
            if(at<0 || at+fragment.length()>target.length() || !target.startsWith(fragment,at))continue;
            boolean unchanged=fragment.equals(original.substring(start,end));
            if(pinned==null && target.indexOf(fragment,at+1)>=0)return fallback(source,diagnostic,"Ambiguous style template: "+entry.getKey());
            Anchor region=new Anchor(start,end,at,at+fragment.length(),unchanged);
            var regions=new ArrayList<Anchor>();regions.add(region);
            if(!unchanged && uniformStyle(spans,start,end)==null) {
                // An amount followed by a translated unit has an exact boundary:
                // preserve the capture and map only its suffix, even when their
                // colors differ. Do not flatten arbitrary multicolored clauses.
                var pieces=local.pieces();var first=pieces.getFirst();
                if(pinned==null || pieces.size()!=2 || !first.original() || first.start()!=0 || pieces.get(1).original()
                    || uniformStyle(spans,start+first.end(),end)==null)
                    return fallback(source,diagnostic,"Nonuniform style template: "+entry.getKey());
                regions.clear();
                regions.add(new Anchor(start,start+first.end(),at,at+first.text().length(),true));
                regions.add(new Anchor(start+first.end(),end,at+first.text().length(),at+fragment.length(),false));
            }
            if(anchors.stream().anyMatch(a->!a.copy && a.sourceStart<=region.sourceStart && region.sourceEnd<=a.sourceEnd
                && a.targetStart<=region.targetStart && region.targetEnd<=a.targetEnd))continue;
            for(var anchor:anchors)if(overlaps(region,anchor)
                && !(start<=anchor.sourceStart && anchor.sourceEnd<=end && at<=anchor.targetStart && anchor.targetEnd<=at+fragment.length()))return fallback(source,diagnostic,"Style template overlaps capture: "+entry.getKey());
            anchors.removeIf(a->overlaps(region,a));anchors.addAll(regions);
        }
        // Unchanged unique names can bound a nearby translated word, including
        // when its Chinese spelling also appears elsewhere in the sentence.
        var earlySourceTokens=tokens(original);var earlyTargetTokens=tokens(target);
        for(var e:earlySourceTokens.entrySet()) {
            if(!e.getKey().matches("[A-Za-z]+(?:'[A-Za-z]+)?"))continue;
            if(match.styleMappings().keySet().stream().anyMatch(key->key.contains(e.getKey())))continue;
            var destinations=earlyTargetTokens.get(e.getKey());
            if(e.getValue().size()!=1 || destinations==null || destinations.size()!=1)continue;
            var a=e.getValue().getFirst();var b=destinations.getFirst();
            var anchor=new Anchor(a.start,a.end,b.start,b.end,true);
            if(anchors.stream().noneMatch(other->overlaps(anchor,other)))anchors.add(anchor);
        }
        // Explicit bilingual anchors let translated words keep their source style even after reordering.
        for (var entry : match.styleMappings().entrySet().stream().sorted(Comparator.<Map.Entry<String,String>>comparingInt(e -> e.getValue().length()).reversed().thenComparing(Comparator.<Map.Entry<String,String>>comparingInt(e -> e.getKey().length()).reversed()).thenComparing(Map.Entry::getKey)).toList()) {
            if(TranslationManager.VARIABLE.matcher(entry.getKey()).find())continue;
            var sources=positions(original,entry.getKey(),true,anchors,true,spans);
            var targets=positions(target,entry.getValue(),false,anchors,false,spans);
            if(sources.isEmpty() || targets.isEmpty())continue;
            if(sources.size()!=targets.size()) {
                // Singular/plural spellings can share one Chinese label. Their order
                // is irrelevant only if all occurrences have the same complete Style.
                List<Position> aliases=new ArrayList<>();
                for(var alias:match.styleMappings().entrySet())if(alias.getValue().equals(entry.getValue())
                    && !TranslationManager.VARIABLE.matcher(alias.getKey()).find())
                    aliases.addAll(positions(original,alias.getKey(),true,anchors,true,spans));
                sources=aliases.stream().distinct().filter(a->aliases.stream().noneMatch(b->!a.equals(b)
                    && b.start<=a.start && a.end<=b.end)).sorted(Comparator.comparingInt(Position::start)).toList();
            }
            // Dictionaries may carry reusable aliases that are redundant in this
            // sentence. Ignore an unprovable alias; the final unmapped-style check
            // still rejects any loss of a distinct source style.
            if(sources.size()!=targets.size() && sources.size()==1) {
                var position=sources.getFirst();Anchor left=null,right=null;
                for(var a:anchors) {
                    if(a.sourceEnd<=position.start && (left==null || a.sourceEnd>left.sourceEnd))left=a;
                    if(a.sourceStart>=position.end && (right==null || a.sourceStart<right.sourceStart))right=a;
                }
                int low=left==null?0:left.targetEnd,high=right==null?target.length():right.targetStart;
                var bounded=targets.stream().filter(p->low<=p.start && p.end<=high).toList();
                if(bounded.size()==1)targets=bounded;
            }
            if(sources.size()!=targets.size())continue;
            if(sources.size()>1) {
                Style style=uniformStyle(spans,sources.getFirst().start,sources.getFirst().end);
                if(style==null || sources.stream().anyMatch(p->!style.equals(uniformStyle(spans,p.start,p.end)))) {
                    var ordered=new ArrayList<Position>();boolean proven=true;
                    for(var sourcePosition:sources) {
                        Anchor left=null,right=null;
                        for(var a:anchors) {
                            if(a.sourceEnd<=sourcePosition.start && (left==null || a.sourceEnd>left.sourceEnd))left=a;
                            if(a.sourceStart>=sourcePosition.end && (right==null || a.sourceStart<right.sourceStart))right=a;
                        }
                        int low=left==null?0:left.targetEnd,high=right==null?target.length():right.targetStart;
                        var candidates=targets.stream().filter(p->low<=p.start && p.end<=high).toList();
                        if(candidates.size()!=1 || ordered.contains(candidates.getFirst()) || uniformStyle(spans,sourcePosition.start,sourcePosition.end)==null){proven=false;break;}
                        ordered.add(candidates.getFirst());
                    }
                    if(!proven)continue;
                    targets=ordered;
                }
            }
            for(int i=0;i<sources.size();i++) {
                var a=sources.get(i);var b=targets.get(i);
                Anchor anchor = new Anchor(a.start,a.end,b.start,b.end,entry.getKey().equals(entry.getValue()));
                if (anchors.stream().anyMatch(other -> overlaps(anchor,other))) continue;
                if(!anchor.copy && uniformStyle(spans,a.start,a.end)==null
                    && !appendRainbowWord(Component.empty(),spans,a.start,a.end,original,entry.getValue()))continue;
                anchors.add(anchor);
            }
        }
        // A unique unchanged decorative run (rating stars, box borders) is one
        // proven span even when its individual symbols have different colors.
        var decorations=Pattern.compile("[\\p{Co}\\p{S}\\p{P}]{2,}").matcher(original);
        while(decorations.find()) {
            String run=decorations.group();int at=target.indexOf(run);
            if(at<0)continue;
            if(target.indexOf(run,at+1)>=0 || original.indexOf(run)!=decorations.start()
                || original.indexOf(run,decorations.start()+1)>=0) {
                Anchor left=null,right=null;
                for(var a:anchors) {
                    if(a.sourceEnd<=decorations.start() && (left==null || a.sourceEnd>left.sourceEnd))left=a;
                    if(a.sourceStart>=decorations.end() && (right==null || a.sourceStart<right.sourceStart))right=a;
                }
                int low=left==null?0:left.targetEnd,high=right==null?target.length():right.targetStart;
                int sourceLow=left==null?0:left.sourceEnd,sourceHigh=right==null?original.length():right.sourceStart;
                if(low>high || original.indexOf(run,sourceLow)!=decorations.start()
                    || original.indexOf(run,decorations.start()+1)>=0 && original.indexOf(run,decorations.start()+1)<sourceHigh)continue;
                at=target.indexOf(run,low);int next=at<0?-1:target.indexOf(run,at+1);
                if(at<0 || at+run.length()>high || next>=0 && next<high)continue;
            }
            Anchor anchor=new Anchor(decorations.start(),decorations.end(),at,at+run.length(),true);
            if(anchors.stream().noneMatch(a->overlaps(anchor,a)))anchors.add(anchor);
        }
        // Unchanged numbers and glyphs are copied with their complete original Style (including font).
        var originalTokens = tokens(original); var targetTokens = tokens(target);
        for (var entry : originalTokens.entrySet()) {
            var allDestinations = targetTokens.get(entry.getKey());
            if(allDestinations==null)continue;
            // Numeric captures and semantic anchors already account for some
            // occurrences. They must not make a remaining unique glyph ambiguous.
            var sources=entry.getValue().stream().filter(p->anchors.stream().noneMatch(a->a.sourceStart<p.end && p.start<a.sourceEnd)).toList();
            var destinations=allDestinations.stream().filter(p->anchors.stream().noneMatch(a->a.targetStart<p.end && p.start<a.targetEnd)).toList();
            if(sources.isEmpty() || destinations.size()!=sources.size())continue;
            if(entry.getValue().size()>1 && entry.getKey().codePoints().anyMatch(Character::isDigit))continue;
            if(sources.size()>1) {
                // Repeated glyphs are safe only when every occurrence has the same full Style.
                // Repeated numbers still require explicit named captures to avoid guessing.
                Style style=uniformStyle(spans,sources.getFirst().start,sources.getFirst().end);
                if(style==null || sources.stream().anyMatch(p -> !style.equals(uniformStyle(spans,p.start,p.end))))continue;
            }
            for(int i=0;i<destinations.size();i++) {
                var a = sources.get(i); var b = destinations.get(i);
                Anchor anchor = new Anchor(a.start,a.end,b.start,b.end,true);
                if (anchors.stream().noneMatch(other -> overlaps(anchor,other))) anchors.add(anchor);
            }
        }
        // Identical punctuation can have different colors in separate clauses. Match it
        // only inside a unique interval bounded by already proven semantic anchors.
        // Never use this inference for repeated numbers, icons, or reordered intervals.
        for (var entry : originalTokens.entrySet()) {
            if (!entry.getKey().matches("[.,:;!?-]")) continue;
            var destinations=targetTokens.get(entry.getKey()); if(destinations==null)continue;
            for(var position:entry.getValue()) {
                if(anchors.stream().anyMatch(a->a.sourceStart<position.end && position.start<a.sourceEnd))continue;
                Anchor left=null,right=null;
                for(var a:anchors) {
                    if(a.targetStart==a.targetEnd)continue;
                    if(a.sourceEnd<=position.start && (left==null || a.sourceEnd>left.sourceEnd))left=a;
                    if(a.sourceStart>=position.end && (right==null || a.sourceStart<right.sourceStart))right=a;
                }
                int sourceLow=left==null?0:left.sourceEnd, sourceHigh=right==null?original.length():right.sourceStart;
                int low=left==null?0:left.targetEnd, high=right==null?target.length():right.targetStart;
                if(low>high || entry.getValue().stream().filter(p->p.start>=sourceLow && p.end<=sourceHigh).count()!=1)continue;
                var matches=destinations.stream().filter(p->p.start>=low && p.end<=high
                    && anchors.stream().noneMatch(a->a.targetStart<p.end && p.start<a.targetEnd)).toList();
                if(matches.size()==1)anchors.add(new Anchor(position.start,position.end,matches.getFirst().start,matches.getFirst().end,true));
            }
        }
        Style literalStyle = null;
        for (Span span : spans) for (int index=span.start;index<span.end;index++) {
            final int position=index;
            if (Character.isWhitespace(original.charAt(index)) || anchors.stream().anyMatch(a -> a.sourceStart <= position && position < a.sourceEnd)) continue;
            if (literalStyle != null && !literalStyle.equals(span.style)) return fallback(source,diagnostic,"Unmapped mixed style: "+span.text);
            literalStyle = span.style;
        }
        if (literalStyle == null) literalStyle = spans.getFirst().style;
        anchors.sort(Comparator.comparingInt(Anchor::targetStart));
        MutableComponent result = Component.empty(); offset=0;
        for (Anchor anchor : anchors) {
            if (anchor.targetStart > offset) result.append(Component.literal(target.substring(offset,anchor.targetStart)).setStyle(literalStyle));
            if (anchor.copy) appendOriginal(result,spans,anchor.sourceStart,anchor.sourceEnd);
            else {
                Style style = uniformStyle(spans,anchor.sourceStart,anchor.sourceEnd);
                String translated=target.substring(anchor.targetStart,anchor.targetEnd);
                if (style == null) {
                    if(!appendRainbowWord(result,spans,anchor.sourceStart,anchor.sourceEnd,original,translated))return fallback(source,diagnostic,"Nonuniform anchor: "+original.substring(anchor.sourceStart,anchor.sourceEnd));
                } else result.append(Component.literal(translated).setStyle(style));
            }
            offset=anchor.targetEnd;
        }
        if (offset < target.length()) result.append(Component.literal(target.substring(offset)).setStyle(literalStyle));
        return result;
    }
    public static Component rangeReplacement(Component source, int start, int end, String target) {
        return rangeReplacement(source,start,end,target,Map.of());
    }
    /** Exact structured fields (dates) with explicit source ranges and no style inference. */
    public static Component structured(Component source,List<TranslationManager.Piece> pieces) {
        if(!literalTree(source))return source;
        var spans=spans(source);String plain=TranslationManager.plain(source.getString());
        var result=Component.empty();
        for(var piece:pieces) {
            if(piece.start()<0 || piece.end()>plain.length() || piece.start()>=piece.end())return source;
            if(piece.original()) {
                if(!piece.text().equals(plain.substring(piece.start(),piece.end())))return source;
                appendOriginal(result,spans,piece.start(),piece.end());
            } else {
                var style=uniformStyle(spans,piece.start(),piece.end());if(style==null)return source;
                result.append(Component.literal(piece.text()).setStyle(style));
            }
        }
        return result;
    }
    static Component rangeReplacement(Component source, int start, int end, String target, Map<String,String> styles) {
        String text = TranslationManager.plain(source.getString()); List<TranslationManager.Piece> p = new ArrayList<>();
        if (start > 0) p.add(new TranslationManager.Piece(text.substring(0,start),0,start,true));
        p.add(new TranslationManager.Piece(target,start,end,false));
        if (end < text.length()) p.add(new TranslationManager.Piece(text.substring(end),end,text.length(),true));
        return apply(source,new TranslationManager.Match("item-name",p,styles));
    }
    /** Apply proven numeric captures inside an NPC body without consuming its prefix. */
    static Component rangeMatch(Component source,int start,int end,TranslationManager.Match match) {
        String text=TranslationManager.plain(source.getString());var pieces=new ArrayList<TranslationManager.Piece>();
        if(start>0)pieces.add(new TranslationManager.Piece(text.substring(0,start),0,start,true));
        for(var piece:match.pieces())pieces.add(new TranslationManager.Piece(piece.text(),piece.start()+start,piece.end()+start,piece.original()));
        if(end<text.length())pieces.add(new TranslationManager.Piece(text.substring(end),end,text.length(),true));
        String rule=match.rule().startsWith("template:")?"template:"+text.substring(0,start)+match.rule().substring(9)+text.substring(end):match.rule();
        return apply(source,new TranslationManager.Match(rule,List.copyOf(pieces),match.styleMappings()));
    }
    public static Component itemName(Component source, String english, String chinese, java.util.function.Function<String,String> reforge) {
        return itemName(source,english,chinese,reforge,Map.of());
    }
    public static Component itemName(Component source, String english, String chinese, java.util.function.Function<String,String> reforge, Map<String,String> styles) {
        return itemName(source,english,chinese,reforge,styles,Map.of());
    }
    public static Component itemName(Component source, String english, String chinese, java.util.function.Function<String,String> reforge, Map<String,String> styles, Map<String,String> modifiers) {
        String text=TranslationManager.plain(source.getString());
        if (english.isBlank()) return source;
        if (TranslationManager.VARIABLE.matcher(english).find()) {
            if (chinese.isBlank()) return source;
            try {
                var match=new TranslationManager.Template(english,chinese).applyNamePrefix(text);
                return match==null ? source : apply(source,new TranslationManager.Match(match.rule(),match.pieces(),styles));
            } catch (IllegalArgumentException e) { return source; }
        }
        // NEU's upgraded item names include a leading icon. In live names the
        // reforge is inserted between that icon and the base name; match the
        // ID-selected base name and keep all live decorations on copied spans.
        int decoration=0;
        while(decoration<english.length() && !Character.isLetterOrDigit(english.codePointAt(decoration)))
            decoration+=Character.charCount(english.codePointAt(decoration));
        if(decoration>0 && decoration<english.length()) {
            String prefix=english.substring(0,decoration);
            english=english.substring(decoration);
            if(chinese.startsWith(prefix)) chinese=chinese.substring(prefix.length());
            Map<String,String> baseStyles=new LinkedHashMap<>();
            for(var entry:styles.entrySet()) {
                String key=entry.getKey(),value=entry.getValue();
                if(key.startsWith(prefix))key=key.substring(prefix.length());
                if(value.startsWith(prefix))value=value.substring(prefix.length());
                if(!key.isBlank() && !value.isBlank())baseStyles.put(key,value);
            }
            styles=baseStyles;
        }
        int start=text.indexOf(english), end=start+english.length();
        if (start<0 || text.indexOf(english,start+english.length())>=0 || start>0 && Character.isLetterOrDigit(text.charAt(start-1)) || end<text.length() && Character.isLetterOrDigit(text.charAt(end))) return source;
        Component translated=chinese.isBlank() || chinese.equals(english) ? source : rangeReplacement(source,start,end,chinese,styles);
        if (start>0) {
            int prefixStart=0;
            while (prefixStart<start && !Character.isLetterOrDigit(text.codePointAt(prefixStart))) prefixStart+=Character.charCount(text.codePointAt(prefixStart));
            int modifierStart=prefixStart, modifierEnd=prefixStart;
            String modifier=null;
            for(var entry:modifiers.entrySet()) {
                String token=entry.getKey()+" ";
                if(text.startsWith(token,prefixStart) && prefixStart+token.length()<=start && !entry.getValue().isBlank()) {
                    modifier=entry.getValue();modifierEnd=prefixStart+entry.getKey().length();
                    prefixStart+=token.length();break;
                }
            }
            String prefix=reforge.apply(text.substring(prefixStart,start).strip());
            if (prefix != null && !prefix.isBlank()) translated=rangeReplacement(translated,prefixStart,start,prefix);
            // Reforge replacement changes only text to the right of this span.
            if(modifier!=null)translated=rangeReplacement(translated,modifierStart,modifierEnd,modifier);
        }
        return translated;
    }
}
