package com.sorakoi.sbtranslate.rank;

import com.google.gson.*;
import java.util.*;

/** Cosmetic, public username registry; never grants any server/account permissions. */
public record RankRoster(Map<String,Entry> entries) {
    public record Entry(String name,String tag,int tagColor,int nameColor) {}
    public RankRoster { entries=Map.copyOf(entries); }
    public static RankRoster empty(){return new RankRoster(Map.of());}
    public Entry find(String name){return entries.get(name.toLowerCase(Locale.ROOT));}
    public static boolean username(String name){return name!=null && name.matches("[A-Za-z0-9_]{1,16}");}
    public static RankRoster parse(String json) {
        var root=JsonParser.parseString(json).getAsJsonObject();
        if(root.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Unknown roster schema");
        var users=root.getAsJsonArray("users");if(users.size()>20000)throw new IllegalArgumentException("Roster too large");
        var result=new HashMap<String,Entry>();
        for(var value:users) {
            var u=value.getAsJsonObject();String name=u.get("name").getAsString(),tag=u.get("tag").getAsString();
            if(!username(name) || !tag.matches("[A-Z0-9_]{1,16}"))throw new IllegalArgumentException("Invalid roster entry");
            var entry=new Entry(name,tag,color(u.get("tagColor").getAsString()),color(u.get("nameColor").getAsString()));
            if(result.put(name.toLowerCase(Locale.ROOT),entry)!=null)throw new IllegalArgumentException("Duplicate username");
        }
        return new RankRoster(result);
    }
    private static int color(String value){if(!value.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Invalid color");return Integer.parseInt(value.substring(1),16);}
}
