package com.sorakoi.sbtranslate.sync;

import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/** Supports a JSON index or a normal web-server directory listing. */
public final class DictionaryIndex {
    public record Release(DictionaryVersion version,URI uri,String sha256,long size) {}
    private static final Pattern LINK=Pattern.compile("(?i)href\\s*=\\s*[\"']([^\"'<>\\s]+)[\"']");
    public static URI endpoint(String url) throws IOException {
        try {
            var uri=URI.create(url.strip());
            if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
                throw new IllegalArgumentException();
            return uri;
        }catch(RuntimeException e){throw new IOException("Invalid dictionary endpoint",e);}
    }
    public static URI directory(URI endpoint){return URI.create(endpoint.toString().replaceAll("/+$","")+"/");}
    public static Release latest(String body,URI endpoint) throws IOException {
        if(body.length()>1024*1024)throw new IOException("Index too large");
        var releases=new ArrayList<Release>();String text=body.strip();
        try {
            if(text.startsWith("{") || text.startsWith("[")) {
                var json=JsonParser.parseString(text);JsonArray files;
                if(json.isJsonArray())files=json.getAsJsonArray();
                else {
                    var object=json.getAsJsonObject();
                    if(object.has("schemaVersion") && object.get("schemaVersion").getAsInt()!=1)throw new IOException("Unsupported index schema");
                    files=object.getAsJsonArray("files");
                }
                if(files==null || files.size()>10000)throw new IOException("Invalid index file list");
                for(var entry:files) {
                    String name,sha="";long size=-1;
                    if(entry.isJsonPrimitive())name=entry.getAsString();
                    else {
                        var row=entry.getAsJsonObject();name=row.get("name").getAsString();
                        if(row.has("sha256"))sha=row.get("sha256").getAsString().toLowerCase(Locale.ROOT);
                        if(row.has("size"))size=row.get("size").getAsLong();
                    }
                    DictionaryVersion version;
                    try{version=DictionaryVersion.filename(name);}catch(RuntimeException invalid){continue;}
                    if(!sha.isEmpty() && !sha.matches("[0-9a-f]{64}") || size==0 || size>DictionaryArchive.MAX_ZIP_BYTES || size< -1)throw new IOException("Invalid release metadata");
                    releases.add(new Release(version,directory(endpoint).resolve(name),sha,size));
                }
            } else {
                var links=LINK.matcher(text);var base=directory(endpoint);
                while(links.find()) {
                    try {
                        var uri=base.resolve(links.group(1)).normalize();
                        String path=uri.getRawPath();String name=path.substring(path.lastIndexOf('/')+1);
                        var version=DictionaryVersion.filename(name);
                        if(uri.equals(base.resolve(name)))releases.add(new Release(version,uri,"",-1));
                    }catch(RuntimeException ignored){}
                }
            }
        }catch(RuntimeException e){throw new IOException("Malformed dictionary index",e);}
        return releases.stream().max(Comparator.comparing(Release::version)).orElseThrow(()->new IOException("No published dictionary ZIP in index"));
    }
}
