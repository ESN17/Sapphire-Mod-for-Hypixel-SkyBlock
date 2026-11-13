package com.sorakoi.sbtranslate.client;

import com.sorakoi.sbtranslate.Sapphire;
import com.sorakoi.sbtranslate.llm.LlmConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import java.nio.file.*;

/** Isolated offline UI smoke run, explicitly enabled by a development property. */
public final class ApiUiSmokeTest {
    private static int ticks,stage=-3;private static boolean capturing;
    public static void start(Minecraft client) {
        Sapphire.LLM_CONFIG.enabled=false;
        Sapphire.LLM_CONFIG.keys.put(LlmConfig.Provider.OPENAI,"test-only-not-a-real-key");
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(c.getOverlay()!=null){ticks=0;return;}
            if(capturing || ++ticks<40)return;
            try {
                if(stage==-3) {
                    c.setScreen(new SapphireScreen(null));
                    Screen root=c.screen;int nativeWidth=root.width,nativeHeight=root.height;
                    for(int[] size:new int[][]{{800,500},{427,240},{320,240}}) {
                        root.resize(size[0],size[1]);
                        for(var category:SettingsCatalog.CATEGORIES){press(root,category.name());bounds(root);}
                    }
                    root.resize(nativeWidth,nativeHeight);
                    for(var option:SettingsCatalog.OPTIONS) {
                        search(root,option.key());
                        boolean before=option.read().test(Sapphire.CONFIG);
                        press(root,option.title());require(option.read().test(Sapphire.CONFIG)!=before,"toggle binding: "+option.key());
                        var saved=com.sorakoi.sbtranslate.config.SapphireConfig.load(Sapphire.ROOT);
                        require(option.read().test(saved)!=before,"toggle saved: "+option.key());
                        press(root,option.title());require(option.read().test(Sapphire.CONFIG)==before,"toggle restored");
                    }
                    press(root,"常用设置");root.setFocused(null);stage=-2;ticks=0;return;
                }
                if(stage==6) {
                    var result=Sapphire.TRANSLATIONS.match("global",null,"Sapphire editor smoke sentence.");
                    if(result==null){require(ticks<2400,"editor asynchronous reload timeout");return;}
                    require(result.text().equals("蓝宝石编辑器测试语句."),"editor saves and reloads");
                    Sapphire.LOGGER.info("SAPPHIRE_API_UI_SMOKE_OK: searchable settings, 24 persisted toggles, compact bounds, API drafts, editor preview/save verified; no API requests");c.stop();return;
                }
                if(stage==0) {
                    c.setScreen(new SapphireScreen(null));search(c.screen,"API 与提示词");press(c.screen,"API 与提示词");
                    require(c.screen instanceof SapphireApiScreen,"API settings entry");
                    require(c.screen.children().stream().filter(e->e instanceof EditBox).count()==3,"provider fields");
                    var model=(EditBox)c.screen.children().stream().filter(e->e instanceof EditBox).toList().get(1);model.setValue("custom-model");
                    for(String provider:new String[]{"Claude","DeepSeek","Kimi","GLM","ChatGPT"}) {
                        press(c.screen,provider);
                        var fields=c.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
                        require(fields.size()==3 && fields.stream().allMatch(field->field.visible && field.active),"visible API inputs: "+provider);
                        require(!fields.getFirst().getValue().isBlank() && !fields.get(1).getValue().isBlank(),"populated API profile: "+provider);
                    }
                    model=(EditBox)c.screen.children().stream().filter(e->e instanceof EditBox).toList().get(1);require(model.getValue().equals("custom-model"),"provider drafts survive switching");
                    stage=1;ticks=0;return;
                }
                if(stage<0)require(c.screen instanceof SapphireScreen,"settings screen remains open");
                else if(stage<=3)require(c.screen instanceof SapphireApiScreen,"API screen remains open");
                else {
                    require(c.screen instanceof TranslationEditorScreen,"translation editor remains open");
                    var source=(MultiLineEditBox)c.screen.children().stream().filter(e->e instanceof MultiLineEditBox).findFirst().orElseThrow();
                    if(source.getValue().contains("搜索后")){require(ticks<600,"editor async load timeout");return;}
                }
                if(stage>=1 && stage<=3) {
                    Screen api=c.screen;int w=api.width,h=api.height;
                    api.resize(320,240);bounds(api);api.resize(w,h);
                }
                bounds(c.screen);
                capturing=true;int current=stage;
                Screenshot.takeScreenshot(c.getMainRenderTarget(),image->{
                    try(image) {
                        Path folder=c.gameDirectory.toPath().resolve("api-ui-smoke");Files.createDirectories(folder);image.writeToFile(folder.resolve("page-"+current+".png"));
                    }catch(Exception e){throw new IllegalStateException("API UI screenshot failed");}
                    c.execute(()->{
                        if(current==-2)search(c.screen,"聊天");
                        else if(current==-1)search(c.screen,"");
                        else if(current==1)press(c.screen,"提示词");
                        else if(current==2){
                            press(c.screen,"正在编辑：收到消息 → 中文");press(c.screen,"正在编辑：发送聊天 → 英文");
                            var box=(MultiLineEditBox)c.screen.children().stream().filter(e->e instanceof MultiLineEditBox).findFirst().orElseThrow();
                            require(box.getValue().contains("教程助手"),"independent tutorial prompt");
                            box.setValue("教程草稿测试");press(c.screen,"正在编辑：游戏教程 /sahelp");
                            box=(MultiLineEditBox)c.screen.children().stream().filter(e->e instanceof MultiLineEditBox).findFirst().orElseThrow();
                            require(!box.getValue().equals("教程草稿测试"),"tutorial prompt does not overwrite chat prompt");
                            press(c.screen,"翻译范围");
                        }
                        else if(current==3) {
                            press(c.screen,"在线翻译：关");press(c.screen,"取消");
                            require(!Sapphire.LLM_CONFIG.enabled,"cancel does not enable translation");
                            require(!Sapphire.LLM_CONFIG.profile().model.equals("custom-model"),"cancel does not save draft");
                            press(c.screen,"词典与工具");press(c.screen,"修改翻译");
                            var search=(EditBox)c.screen.children().stream().filter(e->e instanceof EditBox).findFirst().orElseThrow();search.setValue("RECOMBOBULATOR_3000");press(c.screen,"搜索");
                        } else if(current==4) {
                            press(c.screen,"预览效果");
                        } else {
                            press(c.screen,"编辑译文");
                            press(c.screen,"新建");
                            var input=(EditBox)c.screen.children().stream().filter(e->e instanceof EditBox).toList().get(1);input.setValue("Sapphire editor smoke sentence.");
                            var target=(MultiLineEditBox)c.screen.children().stream().filter(e->e instanceof MultiLineEditBox).findFirst().orElseThrow();target.setValue("蓝宝石编辑器测试语句.");
                            press(c.screen,"保存并生效");
                        }
                        stage++;ticks=0;capturing=false;
                    });
                });
            }catch(Exception e){Sapphire.LOGGER.error("API UI smoke failed",e);c.stop();throw new IllegalStateException("API UI smoke failed",e);}
        });
    }
    private static void press(Screen screen,String text){
        var button=screen.children().stream().filter(e->e instanceof Button b && b.getMessage().getString().equals(text)).map(e->(Button)e).findFirst().orElseThrow();
        require(button.active,"button enabled");button.onPress(null);
    }
    private static void search(Screen screen,String text){
        EditBox box=(EditBox)screen.children().stream().filter(e->e instanceof EditBox).findFirst().orElseThrow();
        screen.setFocused(box);box.setValue(text);
        require(screen.getFocused()==box,"search keeps focus");
    }
    private static void bounds(Screen screen){
        for(var child:screen.children())if(child instanceof AbstractWidget w)
            require(w.getX()>=0 && w.getY()>=0 && w.getX()+w.getWidth()<=screen.width && w.getY()+w.getHeight()<=screen.height,"widget bounds: "+w.getMessage().getString());
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
