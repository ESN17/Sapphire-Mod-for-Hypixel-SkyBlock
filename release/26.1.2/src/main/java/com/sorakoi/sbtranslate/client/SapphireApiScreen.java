package com.sorakoi.sbtranslate.client;

import com.sorakoi.sbtranslate.Sapphire;
import com.sorakoi.sbtranslate.llm.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;
import java.util.concurrent.CompletableFuture;

public final class SapphireApiScreen extends Screen {
    private final Screen parent;private final LlmConfig draft;
    private int page,promptKind,left,top,panelWidth,panelHeight,bodyTop,bodyBottom,fieldStep;
    private String status="";private boolean testing;
    public SapphireApiScreen(Screen parent){super(Component.literal("Sapphire · API 与提示词"));this.parent=parent;this.draft=Sapphire.LLM_CONFIG.copy();}
    @Override protected void init() {
        panelWidth=Math.min(580,width-24);panelHeight=Math.min(440,height-16);
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        int x=left+12,w=panelWidth-24,third=(w-8)/3;
        bodyTop=top+72;bodyBottom=top+panelHeight-42;
        String[] pages={"服务商","提示词","翻译范围"};
        for(int i=0;i<3;i++){final int index=i;addRenderableWidget(SettingsTheme.navigation(pages[i],x+i*(third+4),top+43,third,20,()->{page=index;rebuildWidgets();},()->page==index));}
        if(page==0) {
            int pw=(w-16)/5;String[] names={"ChatGPT","Claude","DeepSeek","Kimi","GLM"};
            for(var provider:LlmConfig.Provider.values())addRenderableWidget(SettingsTheme.navigation(names[provider.ordinal()],x+provider.ordinal()*(pw+4),bodyTop,pw,18,()->{draft.provider=provider;status="";rebuildWidgets();},()->draft.provider==provider));
            fieldStep=Math.min(44,(bodyBottom-bodyTop-23)/3);
            field(x,bodyTop+34,w,"API 请求地址",draft.profile().endpoint,1024,s->draft.profile().endpoint=s,false);
            field(x,bodyTop+34+fieldStep,w,"模型 ID",draft.profile().model,160,s->draft.profile().model=s,false);
            field(x,bodyTop+34+2*fieldStep,w,"API Key",draft.key(),4096,s->draft.keys.put(draft.provider,s),true);
        }else if(page==1) {
            String[] prompts={"收到消息 → 中文","发送聊天 → 英文","游戏教程 /sahelp"};
            button("正在编辑："+prompts[promptKind],x,bodyTop,w,()->{promptKind=(promptKind+1)%3;rebuildWidgets();});
            final int editing=promptKind;
            var edit=MultiLineEditBox.builder().setX(x).setY(bodyTop+25).setPlaceholder(Component.literal("填写要求，原文由程序单独传入"))
                .build(font,w,Math.max(40,bodyBottom-bodyTop-25),Component.literal("翻译提示词"));
            edit.setCharacterLimit(8000);edit.setValue(editing==2?draft.helpPrompt:editing==1?draft.outgoingPrompt:draft.prompt);
            edit.setValueListener(s->{if(editing==2)draft.helpPrompt=s;else if(editing==1)draft.outgoingPrompt=s;else draft.prompt=s;});addRenderableWidget(edit);
        }else {
            int half=(w-4)/2,step=Math.min(29,(bodyBottom-bodyTop-28)/4);
            toggle("在线翻译",draft.enabled,x,bodyTop,half,()->draft.enabled=!draft.enabled);
            toggle("NPC 对话",draft.npc,x+half+4,bodyTop,half,()->draft.npc=!draft.npc);
            toggle("玩家聊天",draft.players,x,bodyTop+step,half,()->draft.players=!draft.players).setTooltip(Tooltip.create(Component.literal("包括公开聊天和私聊；开启后会将所选内容发送至 API。")));
            toggle("未翻译系统消息",draft.system,x+half+4,bodyTop+step,half,()->draft.system=!draft.system);
            toggle("中文聊天自动英译并发送",draft.outgoing,x,bodyTop+2*step,w,()->draft.outgoing=!draft.outgoing);
            toggle("Tab 列表漏译",draft.tablist,x,bodyTop+3*step,half,()->draft.tablist=!draft.tablist);
            toggle("Boss 血条漏译",draft.bossbar,x+half+4,bodyTop+3*step,half,()->draft.bossbar=!draft.bossbar);
            fieldStep=step;
            field(x,bodyTop+4*step+10,half,"超时秒数",Integer.toString(draft.timeoutSeconds),2,s->{try{draft.timeoutSeconds=Integer.parseInt(s);}catch(NumberFormatException e){draft.timeoutSeconds=0;}},false);
        }
        var test=button("测试连接",x,top+panelHeight-26,third,this::test);test.active=!testing;
        test.setTooltip(Tooltip.create(Component.literal("发送固定测试句子，不读取聊天；可能产生少量 API 费用")));
        addRenderableWidget(SettingsTheme.primary("保存",x+third+4,top+panelHeight-26,third,18,this::save));
        button("取消",x+2*(third+4),top+panelHeight-26,third,this::onClose);
    }
    private Button button(String text,int x,int y,int w,Runnable action){return addRenderableWidget(SettingsTheme.button(text,x,y,w,18,action));}
    private Button toggle(String text,boolean value,int x,int y,int w,Runnable action){return addRenderableWidget(SettingsTheme.navigation(text+"："+(value?"开":"关"),x,y,w,18,()->{action.run();rebuildWidgets();},()->value));}
    private void field(int x,int y,int w,String label,String value,int limit,java.util.function.Consumer<String> changed,boolean secret) {
        EditBox edit=secret?new EditBox(font,x,y,w,16,Component.literal(label)) {
            @Override protected MutableComponent createNarrationMessage(){return Component.literal("API Key，已隐藏");}
        }:new EditBox(font,x,y,w,16,Component.literal(label));
        SettingsTheme.searchBox(edit);
        if(secret)edit.addFormatter((text,offset)->FormattedCharSequence.forward("*".repeat(text.length()),Style.EMPTY));
        edit.setMaxLength(limit);edit.setValue(value);edit.setResponder(changed);addRenderableWidget(edit);
    }
    private void save() {
        if(draft.enabled && !draft.validation().isEmpty()){status=draft.validation();return;}
        try{draft.save(Sapphire.ROOT);Sapphire.LLM_CONFIG=draft.copy();Sapphire.LLM.configure(draft);minecraft.setScreen(parent);}
        catch(Exception e){status="保存失败，请检查配置目录和填写内容";}
    }
    private void test() {
        if(testing)return;
        String error=draft.validation();if(!error.isEmpty()){status=error;return;}
        testing=true;status="正在发送固定测试句子…";LlmConfig snapshot=draft.copy();rebuildWidgets();
        CompletableFuture.supplyAsync(()->{try{LlmApi.translate(snapshot,"Hello, welcome to SkyBlock!");return "连接成功，已收到翻译结果";}catch(Exception e){return LlmTranslationService.error(e);}})
            .thenAccept(result->minecraft.execute(()->{testing=false;status=result;if(minecraft.screen==this)rebuildWidgets();}));
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta) {
        super.extractBackground(g,mx,my,delta);
        SettingsTheme.frame(g,left,top,panelWidth,panelHeight,"API 与提示词","按需启用在线翻译 · 修改后点击保存");
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        int x=left+12,w=panelWidth-24;
        if(page==0) {
            label(g,"完整请求地址",x,bodyTop+23);
            label(g,"模型 ID",x,bodyTop+23+fieldStep);
            label(g,"API Key · 仅本机保存，输入已隐藏",x,bodyTop+23+2*fieldStep);
        } else if(page==2) {
            label(g,"超时秒数（5–60）",x,bodyTop+4*fieldStep);
            label(g,"请求次数不限",x+(w+4)/2,bodyTop+4*fieldStep+13);
        }
        String message=status.isBlank()?(page==2?"所选内容会发送至 API，可能产生费用。":page==1?"三种用途分别保存提示词；点击上方切换。":"每个服务商独立保存地址、模型和密钥。"):status;
        g.text(font,font.plainSubstrByWidth(message,w),x,top+panelHeight-38,SettingsTheme.MUTED,false);
        super.extractRenderState(g,mouseX,mouseY,delta);
    }
    private void label(GuiGraphicsExtractor g,String text,int x,int y){g.text(font,Component.literal(text),x,y,SettingsTheme.MUTED,false);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
