package com.sorakoi.sbtranslate.client;

import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AnnouncementScreen extends Screen {
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final URI URL=URI.create("https://sapphire.esn17cabin.club/?announcement=json");
    private final Screen parent;
    private String body="正在读取公告…";
    private String lastGood;
    private boolean loading;
    private long nextPoll;
    public AnnouncementScreen(Screen parent){super(Component.literal("模组公告"));this.parent=parent;}
    @Override protected void init(){
        int w=Math.min(520,width-24);
        addRenderableWidget(new FittingMultiLineTextWidget((width-w)/2,36,w,Math.max(40,height-74),Component.literal(body),font));
        addRenderableWidget(SettingsTheme.button("返回",(width-80)/2,height-28,80,20,this::onClose));
    }
    @Override public void tick(){
        if(loading || System.nanoTime()<nextPoll)return;
        loading=true;
        var request=HttpRequest.newBuilder(URL).timeout(Duration.ofSeconds(8)).GET().build();
        HTTP.sendAsync(request,HttpResponse.BodyHandlers.ofString()).thenApply(response->{
            if(response.statusCode()!=200 || response.body().length()>32768)throw new IllegalStateException();
            String value=JsonParser.parseString(response.body()).getAsJsonObject().get("body").getAsString();
            return value.isBlank()?"暂无公告":value;
        }).whenComplete((value,error)->minecraft.execute(()->{
            loading=false;nextPoll=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            if(error==null)lastGood=value;
            String next=error==null?value:lastGood==null?"暂时无法读取公告，稍后自动重试。":lastGood+"\n\n暂时无法刷新，当前显示上次读取的公告。";
            if(!next.equals(body)){body=next;if(minecraft.screen==this)rebuildWidgets();}
        }));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){
        super.extractRenderState(g,x,y,delta);g.centeredText(font,title,width/2,14,0xffffffff);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
}
