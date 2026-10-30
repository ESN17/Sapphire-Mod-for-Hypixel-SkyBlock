package com.sorakoi.sbtranslate.client;

import com.sorakoi.sbtranslate.Sapphire;
import com.sorakoi.sbtranslate.translation.TranslationEditorStore;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class TranslationEditorScreen extends Screen {
    private final Screen parent;private List<TranslationEditorStore.Entry> all=List.of(),found=List.of();
    private int selected,scope;private String query="",target="",source="",status="正在加载词条...";private boolean loaded,creating;
    private TranslationEditorStore.Entry current;
    public TranslationEditorScreen(Screen parent){super(Component.literal("修改本地翻译"));this.parent=parent;}
    private int left(){return (width-Math.min(520,width-16))/2;}
    private int size(){return Math.min(520,width-16);}
    @Override protected void init() {
        int x=left(),w=size();
        var search=new EditBox(font,x,30,w-102,18,Component.literal("搜索物品 ID、英文或中文"));search.setMaxLength(256);search.setValue(query);search.setResponder(s->query=s);addRenderableWidget(search);
        button("搜索",x+w-98,30,46,()->{creating=false;filter();});button("新建",x+w-48,30,48,()->{creating=true;current=null;source="";target="";rebuildWidgets();});
        if(creating) {
            button("范围: "+TranslationEditorStore.SCOPES.get(scope),x,54,w,()->{scope=(scope+1)%TranslationEditorStore.SCOPES.size();rebuildWidgets();});
            var input=new EditBox(font,x,80,w,18,Component.literal("完整英文原文"));input.setMaxLength(2048);input.setValue(source);input.setResponder(s->source=s);addRenderableWidget(input);
        } else {
            button("上一条",x,54,70,()->move(-1));button("下一条",x+w-70,54,70,()->move(1));
            var original=MultiLineEditBox.builder().setX(x).setY(78).build(font,w,Math.max(30,(height-146)/2),Component.literal("英文原文"));
            original.setValue(current==null?"搜索后选择需要修改的词条":current.source());original.active=false;addRenderableWidget(original);
        }
        int y=creating?112:80+Math.max(30,(height-146)/2)+12;
        var edit=MultiLineEditBox.builder().setX(x).setY(y).build(font,w,Math.max(24,height-y-55),Component.literal("中文译文"));
        edit.setCharacterLimit(2048);edit.setValue(target);edit.setValueListener(s->target=s);addRenderableWidget(edit);
        button("保存并生效",x,height-28,(w-4)/2,this::save);button("返回",x+(w+4)/2,height-28,(w-4)/2,this::onClose);
        if(!loaded){loaded=true;load();}
    }
    private void load() {
        CompletableFuture.supplyAsync(()->{try{return TranslationEditorStore.entries(Sapphire.ROOT);}catch(Exception e){return List.<TranslationEditorStore.Entry>of();}})
            .thenAccept(entries->minecraft.execute(()->{all=entries;if(entries.isEmpty())status="未能加载词典";else if(status.startsWith("正在加载"))status="输入 ID、英文或中文，再点搜索";if(minecraft.screen==this && !creating)filter();}));
    }
    private void filter(){String q=query.strip().toLowerCase(Locale.ROOT);found=all.stream().filter(e->q.isEmpty() || (e.id()+" "+e.source()+" "+e.target()).toLowerCase(Locale.ROOT).contains(q)).toList();selected=0;choose();}
    private void move(int delta){if(found.isEmpty())return;selected=Math.floorMod(selected+delta,found.size());choose();}
    private void choose(){current=found.isEmpty()?null:found.get(selected);target=current==null?"":current.target();rebuildWidgets();}
    private void save() {
        if(creating && source.isBlank()){status="请填写完整原文";return;}
        var entry=creating?new TranslationEditorStore.Entry(TranslationEditorStore.SCOPES.get(scope)+".json","text",-1,TranslationEditorStore.SCOPES.get(scope),source,""):current;
        if(entry==null){status="请先选择词条";return;}
        try{TranslationEditorStore.save(Sapphire.ROOT,entry,target.replace('\n',' '));Sapphire.reload();status=Sapphire.lastStatus.startsWith("重载失败")?Sapphire.lastStatus:"已保存并重新加载，旧文件已备份";load();}
        catch(Exception e){status=e.getMessage()==null?"保存失败":e.getMessage();}
    }
    private void button(String text,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,w,18).build());}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta) {
        super.extractRenderState(g,x,y,delta);g.centeredText(font,title,width/2,10,0xffffffff);
        if(!creating && current!=null)g.centeredText(font,Component.literal(font.plainSubstrByWidth((selected+1)+"/"+found.size()+" "+current.id(),size()-148)),width/2,58,0xffcccccc);
        g.centeredText(font,Component.literal(font.plainSubstrByWidth(status,size())),width/2,height-42,0xffaaaaaa);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
