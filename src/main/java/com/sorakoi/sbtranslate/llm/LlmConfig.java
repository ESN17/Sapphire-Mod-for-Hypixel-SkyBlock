package com.sorakoi.sbtranslate.llm;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** API credentials are deliberately stored outside dictionaries and bundled resources. */
public final class LlmConfig {
    public enum Provider {
        OPENAI("ChatGPT / OpenAI"), CLAUDE("Claude"), DEEPSEEK("DeepSeek"), KIMI("Kimi"), GLM("GLM / 智谱");
        public final String label;
        Provider(String label) {this.label=label;}
    }
    public static final class Profile {
        public String endpoint="", model="";
    }
    public boolean enabled=false, npc=true, players=false, system=false, outgoing=false;
    public boolean tablist=true, bossbar=true;
    public Provider provider=Provider.OPENAI;
    public int timeoutSeconds=20, requestsPerMinute=0; // 0: unlimited; legacy field retained for migration.
    public Map<Provider,Profile> profiles=new EnumMap<>(Provider.class);
    public transient Map<Provider,String> keys=new EnumMap<>(Provider.class);
    public transient String prompt="";
    public transient String outgoingPrompt="";
    public transient String helpPrompt="";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();

    public static LlmConfig defaults() {
        LlmConfig c=new LlmConfig();
        var data=JsonParser.parseString(resource("llm-providers.json")).getAsJsonObject();
        for(Provider p:Provider.values())c.profiles.put(p,JSON.fromJson(data.get(p.name()),Profile.class));
        c.prompt=resource("translation-prompt.txt");c.outgoingPrompt=resource("outgoing-translation-prompt.txt");c.helpPrompt=resource("help-prompt.txt");return c;
    }
    private static String resource(String name) {
        try(var in=LlmConfig.class.getResourceAsStream("/defaults/"+name)) {
            if(in==null)throw new IOException("Missing API defaults");
            return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }catch(IOException e){throw new UncheckedIOException("Cannot read bundled API defaults: "+name,e);}
    }
    public static LlmConfig load(Path root) throws IOException {
        try {
            LlmConfig defaults=defaults(), c=Files.exists(root.resolve("api.json"))
                ? JSON.fromJson(Files.readString(root.resolve("api.json")),LlmConfig.class):defaults;
            if(c==null)throw new IOException("Invalid API configuration");
            if(c.provider==null)c.provider=Provider.OPENAI;
            if(c.profiles==null)c.profiles=new EnumMap<>(Provider.class);
            for(Provider p:Provider.values()) {
                Profile profile=c.profiles.get(p);
                if(profile==null)c.profiles.put(p,defaults.profiles.get(p));
                else {if(profile.endpoint==null)profile.endpoint=defaults.profiles.get(p).endpoint;if(profile.model==null)profile.model="";}
            }
            c.keys=new EnumMap<>(Provider.class);
            Path secrets=root.resolve("api-keys.local.json");
            if(Files.exists(secrets)) {
                var object=JsonParser.parseString(Files.readString(secrets)).getAsJsonObject();
                for(Provider p:Provider.values())if(object.has(p.name()))c.keys.put(p,object.get(p.name()).getAsString());
            }
            c.prompt=Files.exists(root.resolve("translation-prompt.txt"))?Files.readString(root.resolve("translation-prompt.txt")):defaults.prompt;
            c.outgoingPrompt=Files.exists(root.resolve("outgoing-translation-prompt.txt"))?Files.readString(root.resolve("outgoing-translation-prompt.txt")):defaults.outgoingPrompt;
            c.helpPrompt=Files.exists(root.resolve("help-prompt.txt"))?Files.readString(root.resolve("help-prompt.txt")):defaults.helpPrompt;
            c.helpPrompt=upgradeHelpPrompt(c.helpPrompt,defaults.helpPrompt);
            c.timeoutSeconds=Math.clamp(c.timeoutSeconds,5,60);c.requestsPerMinute=0;
            if(c.prompt.length()>8000)throw new IOException("Prompt is too long");
            if(!Files.exists(root.resolve("api.json")))write(root.resolve("api.json"),JSON.toJson(c));
            if(!Files.exists(root.resolve("translation-prompt.txt")))write(root.resolve("translation-prompt.txt"),c.prompt);
            return c;
        }catch(RuntimeException e){throw new IOException("Invalid API configuration");}
    }
    public LlmConfig copy() {
        LlmConfig c=JSON.fromJson(JSON.toJson(this),LlmConfig.class);
        c.keys=new EnumMap<>(Provider.class);c.keys.putAll(keys);c.prompt=prompt;c.outgoingPrompt=outgoingPrompt;c.helpPrompt=helpPrompt;return c;
    }
    /** Upgrade only the exact previously shipped prompt; never overwrite a user's edits. */
    public static String upgradeHelpPrompt(String value,String replacement) {
        try {
            String digest=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.replace("\r\n","\n").strip().getBytes(StandardCharsets.UTF_8)));
            return digest.equals("bea9bdfff694636eb97a9d8f5364e48a1debfe2ccad3dd6131a5a1c09b425530")?replacement:value;
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public String key(){return keys.getOrDefault(provider,"").strip();}
    public Profile profile(){return profiles.get(provider);}
    public String validation() {
        if(provider==null || profile()==null)return "请选择服务商";
        try{endpoint(profile().endpoint);}catch(IllegalArgumentException e){return "API 地址须为完整 HTTPS 地址（本机服务可用 HTTP）";}
        if(profile().model==null || !profile().model.matches("[A-Za-z0-9_.:/-]{1,160}"))return "请填写有效的模型 ID";
        if(key().isEmpty() || key().length()>4096 || key().chars().anyMatch(Character::isISOControl))return "请填写有效的 API Key";
        if(prompt==null || prompt.isBlank() || prompt.length()>8000)return "提示词须为 1–8000 个字符";
        if(outgoing && (outgoingPrompt==null || outgoingPrompt.isBlank() || outgoingPrompt.length()>8000))return "聊天英译提示词须为 1–8000 个字符";
        if(timeoutSeconds<5 || timeoutSeconds>60)return "超时超出允许范围";
        return "";
    }
    public static URI endpoint(String text) {
        try {
            URI uri=URI.create(text.strip());String host=uri.getHost();
            boolean loopback=Set.of("localhost","127.0.0.1","[::1]").contains(host==null?"":host);
            if(host==null || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && loopback)) ||
                uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || uri.getPath().isBlank() || uri.getPath().equals("/"))throw new IllegalArgumentException();
            return uri;
        }catch(RuntimeException e){throw new IllegalArgumentException("Invalid API endpoint");}
    }
    public void save(Path root) throws IOException {
        // Never echo secrets or raw parsing/network errors in UI/logs.
        if(enabled && !validation().isEmpty())throw new IOException("Invalid API configuration");
        var secrets=new JsonObject();for(var p:keys.entrySet())secrets.addProperty(p.getKey().name(),p.getValue());
        write(root.resolve("api-keys.local.json"),JSON.toJson(secrets));
        write(root.resolve("translation-prompt.txt"),prompt);
        write(root.resolve("outgoing-translation-prompt.txt"),outgoingPrompt);
        write(root.resolve("help-prompt.txt"),helpPrompt);
        write(root.resolve("api.json"),JSON.toJson(this));
    }
    private static void write(Path path,String value) throws IOException {
        Files.createDirectories(path.getParent());Path tmp=path.resolveSibling(path.getFileName()+".tmp");
        Files.writeString(tmp,value.endsWith("\n")?value:value+"\n",StandardCharsets.UTF_8);
        try {Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
    }
}
