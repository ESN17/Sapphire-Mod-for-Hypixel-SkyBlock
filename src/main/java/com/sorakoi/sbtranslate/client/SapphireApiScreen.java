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
    private int page;private String status="";private boolean testing;
    public SapphireApiScreen(Screen parent){super(Component.literal("Sapphire · API 翻译"));this.parent=parent;this.draft=Sapphire.LLM_CONFIG.copy();}
    private int left(){return (width-panelWidth())/2;}
    private int panelWidth(){return Math.min(420,width-16);}
    @Override protected void init() {
        int x=left(),w=panelWidth(),third=(w-8)/3;
        String[] pages={"服务商","提示词","范围与限速"};
        for(int i=0;i<3;i++){final int index=i;var b=button(pages[i],x+i*(third+4),28,third,()->{page=index;rebuildWidgets();});b.active=page!=i;}
        if(page==0) {
            int pw=(w-16)/5;String[] names={"ChatGPT","Claude","DeepSeek","Kimi","GLM"};
            for(var p:LlmConfig.Provider.values()){var b=button(names[p.ordinal()],x+p.ordinal()*(pw+4),52,pw,()->{draft.provider=p;status="";rebuildWidgets();});b.active=draft.provider!=p;}
            field(x,86,w,"API 请求地址",draft.profile().endpoint,1024,s->draft.profile().endpoint=s,false);
            field(x,118,w,"模型 ID",draft.profile().model,160,s->draft.profile().model=s,false);
            field(x,150,w,"API Key",draft.key(),4096,s->draft.keys.put(draft.provider,s),true);
        }else if(page==1) {
            var edit=MultiLineEditBox.builder().setX(x).setY(58).setPlaceholder(Component.literal("填写翻译要求，原文由程序单独传入"))
                .build(font,w,Math.max(40,height-112),Component.literal("翻译提示词"));
            edit.setCharacterLimit(8000);edit.setValue(draft.prompt);edit.setValueListener(s->draft.prompt=s);addRenderableWidget(edit);
        }else {
            int half=(w-4)/2;
            toggle("在线翻译",draft.enabled,x,54,half,()->draft.enabled=!draft.enabled);
            toggle("NPC 对话",draft.npc,x+half+4,54,half,()->draft.npc=!draft.npc);
            toggle("玩家聊天（含私聊）",draft.players,x,78,half,()->draft.players=!draft.players);
            toggle("未翻译系统消息",draft.system,x+half+4,78,half,()->draft.system=!draft.system);
            field(x,120,half,"超时秒数",Integer.toString(draft.timeoutSeconds),2,s->{try{draft.timeoutSeconds=Integer.parseInt(s);}catch(NumberFormatException e){draft.timeoutSeconds=0;}},false);
            field(x+half+4,120,half,"每分钟请求数",Integer.toString(draft.requestsPerMinute),2,s->{try{draft.requestsPerMinute=Integer.parseInt(s);}catch(NumberFormatException e){draft.requestsPerMinute=0;}},false);
        }
        var test=button("测试连接",x,height-28,third,this::test);test.active=!testing;
        test.setTooltip(Tooltip.create(Component.literal("发送固定测试句子，不读取聊天；可能产生少量 API 费用")));
        button("保存",x+third+4,height-28,third,this::save);
        button("取消",x+2*(third+4),height-28,third,this::onClose);
    }
    private Button button(String text,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,w,18).build());}
    private void toggle(String text,boolean value,int x,int y,int w,Runnable action){button(text+"："+(value?"开":"关"),x,y,w,()->{action.run();rebuildWidgets();});}
    private void field(int x,int y,int w,String label,String value,int limit,java.util.function.Consumer<String> changed,boolean secret) {
        EditBox edit=secret?new EditBox(font,x,y,w,18,Component.literal(label)) {
            @Override protected MutableComponent createNarrationMessage(){return Component.literal("API Key，已隐藏");}
        }:new EditBox(font,x,y,w,18,Component.literal(label));
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
        testing=true;status="正在发送固定测试句子...";LlmConfig snapshot=draft.copy();rebuildWidgets();
        CompletableFuture.supplyAsync(()->{try{LlmApi.translate(snapshot,"Hello, welcome to SkyBlock!");return "连接成功，已收到翻译结果";}catch(Exception e){return LlmTranslationService.error(e);}})
            .thenAccept(result->minecraft.execute(()->{testing=false;status=result;if(minecraft.screen==this)rebuildWidgets();}));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        super.extractRenderState(g,mouseX,mouseY,delta);int x=left(),w=panelWidth();
        g.centeredText(font,title,width/2,10,0xffffffff);
        if(page==0) {label(g,"完整请求地址（可编辑）",x,76);label(g,"模型 ID（按账号可用模型填写）",x,108);label(g,"API Key（本机保存，输入已隐藏）",x,140);}
        else if(page==1)label(g,"翻译提示词（支持多行，最多 8000 字符）",x,47);
        else {
            label(g,"超时：5–60 秒",x,108);label(g,"频率：1–60 次/分钟",x+(w+4)/2,108);
            label(g,"启用后，所选聊天内容会发送至配置的 API。",x,147);
            label(g,"原文先显示，译文随后补充；请求可能产生费用。",x,160);
        }
        g.centeredText(font,Component.literal(font.plainSubstrByWidth(status.isBlank()?Sapphire.LLM.status():status,w)),width/2,height-43,0xffaaaaaa);
    }
    private void label(GuiGraphicsExtractor g,String text,int x,int y){g.text(font,Component.literal(text),x,y,0xffcccccc);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
