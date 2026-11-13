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
    private int selected,scope,searchVersion;private String query="",target="",source="",status="正在加载词条...";private boolean loaded,creating,busy,preview;
    private List<String> searchIndex=List.of();
    private record SearchData(List<TranslationEditorStore.Entry> entries,List<String> index) {}
    private Component previewText=Component.empty();
    private TranslationEditorStore.Entry current;
    public TranslationEditorScreen(Screen parent){super(Component.literal("修改本地翻译"));this.parent=parent;}
    private int left(){return (width-Math.min(520,width-16))/2;}
    private int size(){return Math.min(520,width-16);}
    @Override protected void init() {
        int x=left(),w=size();
        var search=new EditBox(font,x,30,w-102,18,Component.literal("搜索物品 ID、英文或中文"));search.setMaxLength(256);search.setValue(query);search.setResponder(s->query=s);addRenderableWidget(search);
        button("搜索",x+w-98,30,46,()->{if(busy)return;creating=false;preview=false;filter();});button("新建",x+w-48,30,48,()->{if(busy)return;searchVersion++;creating=true;preview=false;current=null;source="";target="";rebuildWidgets();});
        if(creating) {
            button("范围: "+TranslationEditorStore.SCOPES.get(scope),x,54,w,()->{scope=(scope+1)%TranslationEditorStore.SCOPES.size();rebuildWidgets();});
            var input=new EditBox(font,x,80,w,18,Component.literal("完整英文原文"));input.setMaxLength(2048);input.setValue(source);input.setResponder(s->source=s);addRenderableWidget(input);
        } else {
            button("上一条",x,54,70,()->move(-1));button("下一条",x+w-70,54,70,()->move(1));
            button(preview?"编辑译文":"预览效果",x+w/2-40,54,80,this::preview);
            var original=MultiLineEditBox.builder().setX(x).setY(88).build(font,w,Math.max(30,(height-156)/2),Component.literal("英文原文"));
            original.setValue(current==null?"搜索后选择需要修改的词条":current.source());original.active=false;addRenderableWidget(original);
        }
        int y=creating?112:90+Math.max(30,(height-156)/2)+12;
        if(preview)addRenderableWidget(new FittingMultiLineTextWidget(x,y,w,Math.max(24,height-y-55),previewText,font));
        else {
            var edit=MultiLineEditBox.builder().setX(x).setY(y).build(font,w,Math.max(24,height-y-55),Component.literal("中文译文"));
            edit.setCharacterLimit(2048);edit.setValue(target);edit.setValueListener(s->target=s);addRenderableWidget(edit);
        }
        button("保存并生效",x,height-28,(w-4)/2,this::save);button("返回",x+(w+4)/2,height-28,(w-4)/2,this::onClose);
        if(!loaded && Sapphire.dictionariesReady()){loaded=true;load();}
    }
    private void load() {
        CompletableFuture.supplyAsync(()->{try{return TranslationEditorStore.entries(Sapphire.ROOT);}catch(Exception e){return List.<TranslationEditorStore.Entry>of();}})
            .thenApply(entries->new SearchData(entries,entries.stream().map(e->(e.file()+" "+e.id()+" "+e.source()+" "+e.target()).toLowerCase(Locale.ROOT)).toList()))
            .thenAccept(data->minecraft.execute(()->{all=data.entries();searchIndex=data.index();if(all.isEmpty())status="未能加载词典";else status="已加载 "+all.size()+" 条，可搜索文件名 / ID / 中英文";if(minecraft.screen==this && !creating)filter();}));
    }
    private void filter(){String q=query.strip().toLowerCase(Locale.ROOT);int version=++searchVersion;var entries=all;var index=searchIndex;
        CompletableFuture.supplyAsync(()->{var matches=new ArrayList<TranslationEditorStore.Entry>();for(int i=0;i<entries.size();i++)if(q.isEmpty() || index.get(i).contains(q))matches.add(entries.get(i));return List.copyOf(matches);})
            .thenAccept(matches->minecraft.execute(()->{if(version!=searchVersion || creating)return;found=matches;selected=0;choose();}));}
    private void move(int delta){if(busy || found.isEmpty())return;preview=false;selected=Math.floorMod(selected+delta,found.size());choose();}
    private void choose(){current=found.isEmpty()?null:found.get(selected);target=current==null?"":current.target();rebuildWidgets();}
    private void save() {
        if(!Sapphire.dictionariesReady()){status=Sapphire.lastStatus;return;}
        if(busy)return;
        if(creating && source.isBlank()){status="请填写完整原文";return;}
        var entry=creating?new TranslationEditorStore.Entry(TranslationEditorStore.SCOPES.get(scope)+".json","text",-1,TranslationEditorStore.SCOPES.get(scope),source,""):current;
        if(entry==null){status="请先选择词条";return;}
        busy=true;status="正在保存并后台重载...";String value=target.replace('\n',' ');
        CompletableFuture.runAsync(()->{try{TranslationEditorStore.save(Sapphire.ROOT,entry,value);}catch(Exception e){throw new java.util.concurrent.CompletionException(e);}})
            .whenComplete((ignored,error)->minecraft.execute(()->{
                if(error!=null){busy=false;status=error.getCause()==null?"保存失败":error.getCause().getMessage();return;}
                Sapphire.reloadAsync().thenAccept(message->{busy=false;status=message.startsWith("重载失败")?message:"已保存并生效，旧文件已备份";load();});
            }));
    }
    private void preview(){
        if(busy || current==null)return;
        if(preview){preview=false;rebuildWidgets();return;}
        busy=true;status="正在生成样例预览...";var entry=current;String value=target.replace('\n',' ');
        CompletableFuture.supplyAsync(()->{try{return TranslationEditorStore.preview(Sapphire.ROOT,entry,value);}catch(Exception e){return Component.literal("无法预览：请检查变量是否完整");}})
            .thenAccept(text->minecraft.execute(()->{busy=false;preview=true;previewText=text;status="样例预览：实际颜色、数值与换行取决于游戏文本";if(minecraft.screen==this)rebuildWidgets();}));
    }
    private void button(String text,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,w,18).build());}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta) {
        if(!loaded) {
            if(Sapphire.dictionariesReady()){loaded=true;load();}
            else status=Sapphire.lastStatus;
        }
        super.extractRenderState(g,x,y,delta);g.centeredText(font,title,width/2,10,0xffffffff);
        if(!creating && current!=null)g.centeredText(font,Component.literal(font.plainSubstrByWidth((selected+1)+"/"+found.size()+" "+current.file()+" · "+current.id(),size())),width/2,76,0xffcccccc);
        g.centeredText(font,SapphireNotifications.message(SapphireNotifications.tone(status), font.plainSubstrByWidth(status,Math.max(0,size()-font.width(SapphireNotifications.PREFIX)))),width/2,height-42,0xffffffff);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
