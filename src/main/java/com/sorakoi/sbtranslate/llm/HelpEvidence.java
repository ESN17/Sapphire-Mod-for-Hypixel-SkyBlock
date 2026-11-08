package com.sorakoi.sbtranslate.llm;

import com.google.gson.*;
import java.net.URI;
import java.util.*;

/** Bounded, source-labelled retrieval. Web pages are evidence, never model instructions. */
public final class HelpEvidence {
    private HelpEvidence() {}
    public static boolean needsShortening(String answer,String question){
        if(question.matches("(?s).*(详细|完整推导|逐项解释|长篇|in detail).*"))return false;
        return answer.codePoints().filter(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN).count()>260 || answer.length()>700;
    }
    public static String visibleAnswer(String answer){
        return answer.replaceAll("\\s*\\[(?:W|F|S)[0-9]+\\]","").replaceAll(" +([，。；！？，])","$1").strip();
    }
    public record Plan(List<String> wiki,List<String> forum,List<String> web) {}
    @FunctionalInterface public interface Search { JsonObject fetch(String source,String query,String focus)throws Exception; }
    public static Plan plan(String json) {
        JsonElement parsed=JsonParser.parseString(json.strip().replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$",""));
        JsonObject root=parsed.isJsonObject()?parsed.getAsJsonObject():new JsonObject();
        List<String> wiki=queries(parsed.isJsonArray()?parsed:root.get("wiki"),3);
        List<String> forum=queries(root.get("forum"),1),web=queries(root.get("web"),2);
        // An old planner response must not accidentally keep the assistant Wiki-only.
        String focus=wiki.isEmpty()?"Hypixel SkyBlock guide":wiki.getFirst();
        if(forum.isEmpty())forum=List.of(focus+" guide");
        if(web.isEmpty())web=List.of("Hypixel SkyBlock "+focus);
        return new Plan(wiki,forum,web);
    }
    private static List<String> queries(JsonElement value,int limit) {
        if(value==null || !value.isJsonArray())return List.of();
        LinkedHashSet<String> result=new LinkedHashSet<>();
        for(JsonElement e:value.getAsJsonArray()) {
            if(!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString())continue;
            String q=e.getAsString().strip();
            if(q.isEmpty() || q.length()>180 || q.chars().anyMatch(Character::isISOControl))continue;
            result.add(q);if(result.size()>=limit)break;
        }return List.copyOf(result);
    }
    public static boolean approved(String source,String url) {
        try {
            URI u=URI.create(url);String host=u.getHost();
            if(!"https".equals(u.getScheme()) || host==null || u.getRawUserInfo()!=null || u.getPort()!=-1 || url.length()>2048)return false;
            return switch(source) {
                case "wiki" -> host.equals("hypixelskyblock.minecraft.wiki") && u.getPath().startsWith("/w/");
                case "forum" -> host.equals("hypixel.net") && u.getPath().matches("/threads/[^/]+\\.[0-9]+/.*");
                case "web" -> host.matches("(?i)(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,63}") && !host.matches("(?i).*\\.(local|localhost|internal|test|invalid)");
                default -> false;
            };
        }catch(RuntimeException invalid){return false;}
    }
    public static final class Collection {
        private final Map<String,JsonObject> docs=new LinkedHashMap<>();
        private final Set<String> attempted=new HashSet<>(),notes=new LinkedHashSet<>();
        public void search(Search search,String source,List<String> queries,String focus) {
            for(String query:queries) {
                if(!attempted.add(source+":"+query) || docs.size()>=16)continue;
                try {
                    JsonObject response=search.fetch(source,query,focus);
                    String status=string(response,"status");
                    if(Set.of("unavailable","busy","stale","cached","invalid","empty").contains(status))notes.add(source+": "+status);
                    JsonElement entries=response.get("documents");if(entries==null || !entries.isJsonArray())continue;
                    int count=0;
                    for(JsonElement entry:entries.getAsJsonArray()) {
                        if(!entry.isJsonObject())continue;
                        JsonObject d=entry.getAsJsonObject();String url=string(d,"url"),body=string(d,"text");
                        if(!approved(source,url) || body.isBlank())continue;
                        JsonObject clean=new JsonObject();
                        for(String field:List.of("title","url","published","retrieved","revision","license","coverage","sourceNote")) {
                            String s=string(d,field);if(!s.isEmpty())clean.addProperty(field,s.substring(0,Math.min(2048,s.length())));
                        }
                        clean.addProperty("text",body.substring(0,Math.min(8000,body.length())));
                        clean.addProperty("kind",source);clean.addProperty("id",(source.equals("wiki")?"W":source.equals("forum")?"F":"S")+(docs.size()+1));
                        if(response.has("snapshot"))clean.addProperty("snapshot",string(response,"snapshot"));
                        docs.putIfAbsent(url,clean);if(++count>=2 || docs.size()>=16)break;
                    }
                }catch(Exception unavailable){notes.add(source+": unavailable");}
            }
        }
        public boolean isEmpty(){return docs.isEmpty();}
        public boolean hasSource(String source){return docs.values().stream().anyMatch(d->string(d,"kind").equals(source));}
        public boolean emptySearch(String source){return notes.contains(source+": empty");}
        public JsonArray documents(){
            JsonArray result=new JsonArray();int budget=64000;
            for(JsonObject d:docs.values()){
                if(budget<=0)break;JsonObject copy=d.deepCopy();String text=string(d,"text");int length=Math.min(text.length(),budget);
                copy.addProperty("text",text.substring(0,length));result.add(copy);budget-=length;
            }return result;
        }
        public JsonArray notes(){JsonArray result=new JsonArray();notes.forEach(result::add);return result;}
        public String appendix(String answer){
            var used=new ArrayList<>(docs.values());
            used.sort(Comparator.comparing(d->!answer.contains("["+string(d,"id")+"]")));
            StringBuilder out=new StringBuilder("\n本次检索资料（含未引用条目）：");
            for(JsonObject d:used){
                String kind=string(d,"kind"),date=string(d,"published");
                out.append("\n[").append(string(d,"id")).append("] ").append(string(d,"title"));
                if(kind.equals("wiki"))out.append("（Wiki 快照；CC BY-NC-SA 3.0）");
                else out.append(kind.equals("forum")?"（论坛经验":"（网页资料").append(date.isEmpty()?"；日期未知）":"；"+date+"）");
                if(string(d,"coverage").equals("search-snippet"))out.append("〔仅搜索摘要〕");
                out.append(" ").append(string(d,"url"));
            }
            if(notes.stream().anyMatch(n->n.endsWith("unavailable") || n.endsWith("busy")))out.append("\n部分网络检索暂不可用，本次仅使用实际取得的资料。");
            if(notes.stream().anyMatch(n->n.endsWith("stale") || n.endsWith("cached")))out.append("\n部分来源使用缓存；请留意资料日期。");
            if(notes.stream().anyMatch(n->n.endsWith("empty")))out.append("\n部分检索未找到匹配条目。");
            return out.toString();
        }
    }
    private static String string(JsonObject o,String key){JsonElement e=o.get(key);return e!=null && e.isJsonPrimitive()?e.getAsString():"";}
}
