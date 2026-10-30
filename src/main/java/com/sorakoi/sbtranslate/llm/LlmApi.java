package com.sorakoi.sbtranslate.llm;

import com.google.gson.*;
import java.io.IOException;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** One request, no automatic retry, redirects or tool execution. */
public final class LlmApi {
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    public static HttpRequest request(LlmConfig c,String text) {
        if(!c.validation().isEmpty())throw new IllegalArgumentException("Invalid API configuration");
        var body=new JsonObject();body.addProperty("model",c.profile().model);body.addProperty("stream",false);
        var builder=HttpRequest.newBuilder(LlmConfig.endpoint(c.profile().endpoint)).timeout(Duration.ofSeconds(c.timeoutSeconds)).header("Content-Type","application/json");
        switch(c.provider) {
            case OPENAI -> {
                body.addProperty("instructions",c.prompt);body.addProperty("input",text);body.addProperty("store",false);body.addProperty("max_output_tokens",2048);
                builder.header("Authorization","Bearer "+c.key());
            }
            case CLAUDE -> {
                body.addProperty("system",c.prompt);body.addProperty("max_tokens",2048);
                var messages=new JsonArray();messages.add(message("user",text));body.add("messages",messages);
                builder.header("x-api-key",c.key()).header("anthropic-version","2023-06-01");
            }
            default -> {
                var messages=new JsonArray();messages.add(message("system",c.prompt));messages.add(message("user",text));body.add("messages",messages);body.addProperty("max_tokens",2048);
                if(c.provider==LlmConfig.Provider.DEEPSEEK || (c.provider==LlmConfig.Provider.KIMI && c.profile().model.startsWith("kimi-k2."))) {
                    var thinking=new JsonObject();thinking.addProperty("type","disabled");body.add("thinking",thinking);
                }
                builder.header("Authorization","Bearer "+c.key());
            }
        }
        return builder.POST(HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8)).build();
    }
    private static JsonObject message(String role,String text){var m=new JsonObject();m.addProperty("role",role);m.addProperty("content",text);return m;}
    public static String translate(LlmConfig c,String text) throws Exception {
        // Byte-array subscriber bounds memory while the request timeout includes the body.
        var response=HTTP.send(request(c,text),info->new LimitedBodySubscriber(128*1024));
        if(response.statusCode()!=200)throw new ApiFailure(response.statusCode());
        return response(c.provider,new String(response.body(),StandardCharsets.UTF_8));
    }
    public static String response(LlmConfig.Provider provider,String json) throws IOException {
        try {
            var root=JsonParser.parseString(json).getAsJsonObject();String result;
            if(root.has("error"))throw new IOException("API response rejected");
            if(provider==LlmConfig.Provider.OPENAI) {
                if(!"completed".equals(root.get("status").getAsString()))throw new IOException("Incomplete API response");
                var text=new StringBuilder();
                for(var item:root.getAsJsonArray("output")) {
                    var obj=item.getAsJsonObject();if(!"message".equals(obj.get("type").getAsString()))continue;
                    for(var part:obj.getAsJsonArray("content"))if("output_text".equals(part.getAsJsonObject().get("type").getAsString()))text.append(part.getAsJsonObject().get("text").getAsString());
                }
                result=text.toString();
            } else if(provider==LlmConfig.Provider.CLAUDE) {
                if(!"end_turn".equals(root.get("stop_reason").getAsString()))throw new IOException("Incomplete API response");
                var text=new StringBuilder();for(var part:root.getAsJsonArray("content"))if("text".equals(part.getAsJsonObject().get("type").getAsString()))text.append(part.getAsJsonObject().get("text").getAsString());result=text.toString();
            } else {
                var choice=root.getAsJsonArray("choices").get(0).getAsJsonObject();
                if(!"stop".equals(choice.get("finish_reason").getAsString()))throw new IOException("Incomplete API response");
                result=choice.getAsJsonObject("message").get("content").getAsString();
            }
            result=result.strip();if(result.isBlank() || result.length()>4096 || result.contains("§") || result.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\t'))throw new IOException("Invalid translation");
            return result;
        }catch(RuntimeException e){throw new IOException("Invalid API response");}
    }
    public static final class ApiFailure extends IOException {
        public final int status;public ApiFailure(int status){super("API HTTP "+status);this.status=status;}
    }
    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;private final java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        private final java.util.concurrent.CompletableFuture<byte[]> body=new java.util.concurrent.CompletableFuture<>();
        private java.util.concurrent.Flow.Subscription subscription;
        LimitedBodySubscriber(int limit){this.limit=limit;}
        public java.util.concurrent.CompletionStage<byte[]> getBody(){return body;}
        public void onSubscribe(java.util.concurrent.Flow.Subscription s){subscription=s;s.request(1);}
        public void onNext(java.util.List<java.nio.ByteBuffer> buffers){
            for(var buffer:buffers) {
                if(out.size()+buffer.remaining()>limit){subscription.cancel();body.completeExceptionally(new IOException("API response too large"));return;}
                byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);out.writeBytes(bytes);
            }
            subscription.request(1);
        }
        public void onError(Throwable e){body.completeExceptionally(new IOException("API connection failed"));}
        public void onComplete(){body.complete(out.toByteArray());}
    }
}
