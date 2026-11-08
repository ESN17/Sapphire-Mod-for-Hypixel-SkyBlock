package com.sorakoi.sbtranslate.client;

import com.sorakoi.sbtranslate.Sapphire;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Counts describe loaded rules, not an estimated percentage of all server text. */
public final class TranslationStatusScreen extends Screen {
    private final Screen parent;
    public TranslationStatusScreen(Screen parent){super(Component.literal("翻译状态"));this.parent=parent;}
    @Override protected void init() {
        var counts=new HashMap<String,Integer>();
        Sapphire.TRANSLATIONS.community().entries().keySet().forEach(key->{
            int end=key.indexOf('\0');counts.merge(key.substring(0,end),1,Integer::sum);
        });
        var lines=new ArrayList<String>();
        lines.add("总开关: "+(Sapphire.CONFIG.enabled?"开启":"关闭"));
        lines.add("当前环境: "+(Sapphire.CONTEXT.confirmed()?"已确认 SkyBlock":"尚未确认 SkyBlock")
            +(Sapphire.CONFIG.forceTranslation?"（已启用强制翻译）":""));
        lines.add("物品词典: "+Sapphire.TRANSLATIONS.itemCount()+" 个物品");
        lines.add("译名库更新: "+Sapphire.dictionaryStatus());
        lines.add("Sapphire 称号: "+(Sapphire.CONFIG.sapphireRanks?"开启":"关闭"));
        lines.add("称号开启时，首次进入 Hypixel 会向 Sapphire 网站登记用户名；成功后不重复上传，每次登录获取公开称号名册。");
        lines.add("NPC 对话补充规则: "+counts.getOrDefault("npc",0));
        lines.add("界面标题补充规则: "+counts.getOrDefault("gui",0));
        lines.add("提示说明补充规则: "+counts.getOrDefault("tooltip",0));
        lines.add("系统通知补充规则: "+counts.getOrDefault("system",0));
        lines.add("其中 Wiki 词库: "+Sapphire.TRANSLATIONS.wikiRuleCount()+" 条规则");
        lines.add("Wiki 格式待核对: "+Sapphire.TRANSLATIONS.wikiPendingReviewCount()+" 条（暂未启用）");
        lines.add("未翻译文本收集: "+(Sapphire.CONFIG.collectUntranslated?"开启":"关闭"));
        lines.add("");lines.add("最近加载结果:");lines.add(Sapphire.lastStatus);
        lines.add("");lines.add("以上为已加载规则数量，包含数值模板，不代表全服文本覆盖率。");
        lines.add("现有译文优先于 Wiki 补充译文。词库修改后可在此重新加载。");
        int w=Math.min(520,width-24),x=(width-w)/2;
        addRenderableWidget(new FittingMultiLineTextWidget(x,34,w,Math.max(40,height-72),Component.literal(String.join("\n",lines)),font));
        addRenderableWidget(Button.builder(Component.literal("重新加载并刷新"),b->{b.active=false;b.setMessage(Component.literal("后台加载中..."));Sapphire.reloadAsync().thenAccept(result->rebuildWidgets());}).bounds(x,height-28,(w-8)/2,20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds(x+(w+8)/2,height-28,(w-8)/2,20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){
        super.extractRenderState(g,x,y,delta);g.centeredText(font,title,width/2,12,0xffffffff);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
