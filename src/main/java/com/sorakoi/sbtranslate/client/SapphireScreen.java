package com.sorakoi.sbtranslate.client;
import com.sorakoi.sbtranslate.Sapphire;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.lang.reflect.Field;

public final class SapphireScreen extends Screen {
    private final Screen parent;
    public SapphireScreen(Screen parent) {super(Component.literal("Sapphire 设置"));this.parent=parent;}
    @Override protected void init() {
        String[][] toggles={{"enabled","总开关"},{"items","物品名称"},{"lore","物品描述"},{"gui","菜单"},{"chat","系统聊天"},
            {"scoreboard","计分板"},{"tablist","玩家列表"},{"actionbar","动作栏"},{"title","标题"},{"subtitle","副标题"},{"bossbar","首领栏"},{"showOriginal","附带原文"},{"debug","调试诊断"},{"collectUntranslated","收集词条"},{"forceTranslation","忽略环境限制"}};
        for(int i=0;i<toggles.length;i++) {
            String field=toggles[i][0],label=toggles[i][1];
            addRenderableWidget(Button.builder(label(field,label),b -> {
                try {Field f=Sapphire.CONFIG.getClass().getField(field);f.setBoolean(Sapphire.CONFIG,!f.getBoolean(Sapphire.CONFIG));Sapphire.saveConfig();b.setMessage(label(field,label));}
                catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            }).bounds(width/2-155+(i%2)*160,42+(i/2)*18,150,17).build());
        }
        addRenderableWidget(Button.builder(Component.literal("重新加载词典"),b -> {Sapphire.reload();b.setMessage(Component.literal(Sapphire.lastStatus));}).bounds(width/2-155,height-52,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("API 与提示词"),b -> minecraft.setScreen(new SapphireApiScreen(this))).bounds(width/2-50,height-52,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("修改翻译"),b -> minecraft.setScreen(new TranslationEditorScreen(this))).bounds(width/2+55,height-52,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("翻译状态"),b -> minecraft.setScreen(new TranslationStatusScreen(this))).bounds(width/2-155,height-28,150,20).build());
        addRenderableWidget(Button.builder(Component.literal("完成"),b -> onClose()).bounds(width/2+5,height-28,150,20).build());
    }
    private Component label(String field,String label) {
        try{return Component.literal(label+"："+(Sapphire.CONFIG.getClass().getField(field).getBoolean(Sapphire.CONFIG)?"开":"关"));}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float delta) {
        super.extractRenderState(graphics,x,y,delta);
        graphics.centeredText(font,title,width/2,12,0xffffffff);
        graphics.centeredText(font,Component.literal("物品词条 "+Sapphire.TRANSLATIONS.itemCount()+" | SkyBlock "+Sapphire.CONTEXT.confirmed()),width/2,28,0xffaaaaaa);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
