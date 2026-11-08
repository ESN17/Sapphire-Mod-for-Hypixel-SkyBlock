package com.sorakoi.sbtranslate.rank;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Called only on a background worker. Acknowledgements survive dictionary replacement. */
public final class RankClient {
    public interface Transport { String roster() throws IOException; void register(String name) throws IOException; }
    public static final class Http implements Transport {
        private String request(String suffix,String body) throws IOException {
            var c=(HttpURLConnection)URI.create("https://sapphire.esn17cabin.club/customrank/"+suffix).toURL().openConnection();
            c.setConnectTimeout(8000);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);
            try {
                if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(var out=c.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}}
                if(c.getResponseCode()!=200)throw new IOException("Rank service HTTP "+c.getResponseCode());
                try(var in=c.getInputStream()){byte[] b=in.readNBytes(4*1024*1024+1);if(b.length>4*1024*1024)throw new IOException("Rank response too large");return new String(b,StandardCharsets.UTF_8);}
            } finally {c.disconnect();}
        }
        public String roster()throws IOException{return request("roster",null);}
        public void register(String name)throws IOException {
            var body=new JsonObject();body.addProperty("name",name);
            var reply=JsonParser.parseString(request("register",body.toString())).getAsJsonObject();
            if(!reply.has("registered") || !reply.get("registered").getAsBoolean())throw new IOException("Registration not acknowledged");
        }
    }
    private final Path root;private final Transport transport;
    public RankClient(Path root,Transport transport){this.root=root;this.transport=transport;}
    public synchronized RankRoster refresh(String name)throws IOException {
        if(!RankRoster.username(name))throw new IOException("Invalid username");
        Path receipts=root.resolve("rank-registration.json"),cache=root.resolve("rank-roster.json");
        var known=new HashSet<String>();
        if(Files.exists(receipts)) for(var n:JsonParser.parseString(Files.readString(receipts)).getAsJsonArray())known.add(n.getAsString());
        String json=transport.roster();RankRoster roster=RankRoster.parse(json);String key=name.toLowerCase(Locale.ROOT);
        if(!known.contains(key)) {
            // Also avoid uploading on a fresh installation when the server already knows this name.
            boolean registered=roster.find(name)==null;
            if(registered)transport.register(name);
            known.add(key);write(receipts,new Gson().toJson(new TreeSet<>(known)));
            if(registered){json=transport.roster();roster=RankRoster.parse(json);}
        }
        write(cache,json);return roster;
    }
    public RankRoster cached(){try{return RankRoster.parse(Files.readString(root.resolve("rank-roster.json")));}catch(Exception e){return RankRoster.empty();}}
    public synchronized RankRoster refreshRoster()throws IOException {
        String json=transport.roster();var roster=RankRoster.parse(json);write(root.resolve("rank-roster.json"),json);return roster;
    }
    private static void write(Path path,String text)throws IOException {
        Files.createDirectories(path.getParent());Path tmp=Files.createTempFile(path.getParent(),"rank-",".tmp");
        try{Files.writeString(tmp,text);try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}
    }
}
