package com.sorakoi.sbtranslate.client;

import com.sorakoi.sbtranslate.Sapphire;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SapphireScreen extends Screen {
    private final Screen parent;
    private int category,offset,left,top,panelWidth,panelHeight,navWidth,contentX,contentWidth,listTop,listBottom,capacity,rowStep;
    private String query="",feedback="";
    private boolean loading,draggingScroll;
    private EditBox search;
    private final List<AbstractWidget> rows=new ArrayList<>();
    private List<Entry> entries=List.of();
    private Button previous,next;
    private record Entry(String title,String description,SettingsCatalog.Option option,Runnable action) {}
    public SapphireScreen(Screen parent) {super(Component.literal("Sapphire 设置"));this.parent=parent;}

    @Override protected void init() {
        panelWidth=Math.min(780,width-16);panelHeight=Math.min(500,height-16);
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;navWidth=panelWidth<460?88:112;
        contentX=left+navWidth+14;contentWidth=panelWidth-navWidth-28;
        boolean compact=panelHeight<300;
        rowStep=compact?38:49;
        listTop=top+(compact?80:85);listBottom=top+panelHeight-(compact?30:45);
        capacity=Math.max(1,(listBottom-listTop)/rowStep);
        rows.clear();
        search=new EditBox(font,contentX,top+16,contentWidth-25,20,Component.literal("搜索设置"));
        SettingsTheme.searchBox(search);search.setMaxLength(80);search.setHint(Component.literal("搜索设置 / Search"));
        search.setValue(query);search.setResponder(value->{query=value;offset=0;refreshRows();});addRenderableWidget(search);
        addRenderableWidget(SettingsTheme.button("×",contentX+contentWidth-21,top+16,21,20,()->{search.setValue("");setFocused(search);}));
        int step=Math.min(29,(panelHeight-84)/6);
        for(int i=0;i<SettingsCatalog.CATEGORIES.size();i++) {
            final int index=i;
            addRenderableWidget(SettingsTheme.navigation(SettingsCatalog.CATEGORIES.get(i).name(),left+8,top+57+i*step,navWidth-8,step-3,
                ()->{category=index;offset=0;query="";search.setValue("");refreshRows();},()->query.isBlank() && category==index));
        }
        previous=addRenderableWidget(SettingsTheme.button("↑",contentX+contentWidth-48,top+52,22,23,()->scroll(-capacity)));
        next=addRenderableWidget(SettingsTheme.button("↓",contentX+contentWidth-22,top+52,22,23,()->scroll(capacity)));
        previous.setTooltip(Tooltip.create(Component.literal("上一页；也可用鼠标滚轮")));
        next.setTooltip(Tooltip.create(Component.literal("下一页；也可用鼠标滚轮")));
        addRenderableWidget(SettingsTheme.primary("完成",left+panelWidth-82,top+panelHeight-28,68,20,this::onClose));
        refreshRows();
    }
    private void refreshRows() {
        // Replace result cards only: searching does not recreate the input or lose its cursor.
        if(rows.contains(getFocused()))setFocused(search);
        rows.forEach(this::removeWidget);rows.clear();
        var result=new ArrayList<Entry>();
        addAction(result,0,"模组公告","查看维护者发布的最新公告。",()->minecraft.setScreen(new AnnouncementScreen(this)));
        for(var option:SettingsCatalog.OPTIONS)if(query.isBlank()?option.category()==category:option.matches(query))
            result.add(new Entry(option.title(),option.description(),option,()->{
                option.write().accept(Sapphire.CONFIG,!option.read().test(Sapphire.CONFIG));Sapphire.saveConfig();feedback="设置已应用";
            }));
        addAction(result,0,"API 与提示词","配置在线翻译、服务商和 /sahelp 教程提示词。",()->minecraft.setScreen(new SapphireApiScreen(this)));
        addAction(result,0,"反馈译文问题","发现译文错误、前后不一致或漏译时，写一句说明匿名发给译名库维护者。",()->minecraft.setScreen(new FeedbackScreen(this)));
        addAction(result,4,"修改翻译","搜索全部词条，编辑译文并预览效果。",()->minecraft.setScreen(new TranslationEditorScreen(this)));
        addAction(result,4,"翻译状态","查看已加载词条、运行环境与词典更新状态。",()->minecraft.setScreen(new TranslationStatusScreen(this)));
        addAction(result,4,"重新加载词典","修改本地词典后，在后台重新加载并生效。",this::reload);
        entries=List.copyOf(result);offset=Math.clamp(offset,0,Math.max(0,entries.size()-capacity));
        for(int i=offset;i<Math.min(entries.size(),offset+capacity);i++) {
            var entry=entries.get(i);
            var card=new SettingsTheme.SettingCard(entry.title,entry.description,contentX,listTop+(i-offset)*rowStep,contentWidth-9,rowStep-5,
                entry.option==null?null:()->entry.option.read().test(Sapphire.CONFIG),entry.action);
            if(entry.title.equals("重新加载词典"))card.active=!loading;
            rows.add(addRenderableWidget(card));
        }
        if(previous!=null){previous.active=offset>0;next.active=offset+capacity<entries.size();}
    }
    private void addAction(List<Entry> result,int group,String title,String description,Runnable action) {
        String text=(title+" "+description).toLowerCase(Locale.ROOT);
        boolean match=java.util.Arrays.stream(query.toLowerCase(Locale.ROOT).strip().split("\\s+")).allMatch(text::contains);
        if(query.isBlank()?category==group:match)result.add(new Entry(title,description,null,action));
    }
    private void reload() {
        if(loading)return;loading=true;feedback="正在后台加载词典…";refreshRows();
        Sapphire.reloadAsync().whenComplete((result,error)->minecraft.execute(()->{
            loading=false;feedback=error==null?result:"加载失败，请查看翻译状态";
            if(minecraft.screen==this)refreshRows();
        }));
    }
    private void scroll(int delta){int before=offset;offset=Math.clamp(offset+delta,0,Math.max(0,entries.size()-capacity));if(before!=offset)refreshRows();}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(x>=contentX && x<=contentX+contentWidth && y>=listTop && y<=listBottom){scroll(vertical>0?-1:vertical<0?1:0);return true;}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    private void dragScroll(double y) {
        int max=Math.max(0,entries.size()-capacity);
        offset=(int)Math.round(Math.clamp((y-listTop)/(listBottom-listTop),0,1)*max);refreshRows();
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        if(event.button()==0 && entries.size()>capacity && event.x()>=contentX+contentWidth-6 && event.x()<=contentX+contentWidth
            && event.y()>=listTop && event.y()<=listBottom){draggingScroll=true;dragScroll(event.y());return true;}
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {
        if(draggingScroll){dragScroll(event.y());return true;}return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){draggingScroll=false;return super.mouseReleased(event);}
    @Override public boolean keyPressed(KeyEvent key) {
        if(!search.isFocused() && (key.key()==266 || key.key()==267)){scroll(key.key()==266?-capacity:capacity);return true;}
        return super.keyPressed(key);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta) {
        super.extractBackground(g,x,y,delta);
        SettingsTheme.frame(g,left,top,panelWidth,panelHeight,"","");
        SettingsTheme.box(g,left+1,top+2,navWidth+3,panelHeight-4,SettingsTheme.SIDEBAR);
        g.fill(left+navWidth+4,top+49,left+panelWidth-12,top+50,SettingsTheme.LINE);
        g.text(font,"Sapphire",left+12,top+16,SettingsTheme.ACCENT,false);
        g.text(font,"中文 · 随你设置",left+12,top+31,SettingsTheme.MUTED,false);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta) {
        String heading=query.isBlank()?SettingsCatalog.CATEGORIES.get(category).name():"搜索结果";
        g.text(font,heading,contentX,top+54,SettingsTheme.TEXT,false);
        String count=entries.isEmpty()?"没有匹配项":(offset+1)+"–"+Math.min(offset+capacity,entries.size())+" / "+entries.size()+" 项";
        g.text(font,count,contentX,top+68,SettingsTheme.MUTED,false);
        if(entries.isEmpty())g.text(font,font.plainSubstrByWidth("试试「聊天」「原文」或 API",contentWidth),contentX,listTop+16,SettingsTheme.MUTED,false);
        if(entries.size()>capacity) {
            int track=listBottom-listTop,thumb=Math.max(14,track*capacity/entries.size());
            int sy=listTop+(track-thumb)*offset/(entries.size()-capacity);
            SettingsTheme.box(g,contentX+contentWidth-4,listTop,3,track,SettingsTheme.LINE);
            SettingsTheme.box(g,contentX+contentWidth-4,sy,3,thumb,SettingsTheme.ACCENT);
        }
        String status=feedback.isBlank()?"修改立即保存 · Esc 返回":feedback;
        g.text(font,font.plainSubstrByWidth(status,panelWidth-108),left+14,top+panelHeight-21,SettingsTheme.MUTED,false);
        super.extractRenderState(g,x,y,delta);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
