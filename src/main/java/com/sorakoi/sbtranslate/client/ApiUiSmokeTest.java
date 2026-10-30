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
    private static int ticks,stage;private static boolean capturing;
    public static void start(Minecraft client) {
        Sapphire.LLM_CONFIG.enabled=false;
        Sapphire.LLM_CONFIG.keys.put(LlmConfig.Provider.OPENAI,"test-only-not-a-real-key");
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(c.getOverlay()!=null){ticks=0;return;}
            if(capturing || ++ticks<40)return;
            try {
                if(stage==0) {
                    c.setScreen(new SapphireScreen(null));press(c.screen,"API 与提示词");
                    require(c.screen instanceof SapphireApiScreen,"API settings entry");
                    require(c.screen.children().stream().filter(e->e instanceof EditBox).count()==3,"provider fields");
                    var model=(EditBox)c.screen.children().stream().filter(e->e instanceof EditBox).toList().get(1);model.setValue("custom-model");
                    press(c.screen,"Kimi");press(c.screen,"ChatGPT");
                    model=(EditBox)c.screen.children().stream().filter(e->e instanceof EditBox).toList().get(1);require(model.getValue().equals("custom-model"),"provider drafts survive switching");
                    stage=1;ticks=0;return;
                }
                if(stage<=3)require(c.screen instanceof SapphireApiScreen,"API screen remains open");
                else {
                    require(c.screen instanceof TranslationEditorScreen,"translation editor remains open");
                    var source=(MultiLineEditBox)c.screen.children().stream().filter(e->e instanceof MultiLineEditBox).findFirst().orElseThrow();
                    if(source.getValue().contains("搜索后")){require(ticks<600,"editor async load timeout");return;}
                }
                for(var child:c.screen.children())if(child instanceof AbstractWidget w)require(w.getX()>=0 && w.getY()>=0 && w.getX()+w.getWidth()<=c.screen.width && w.getY()+w.getHeight()<=c.screen.height,"widget bounds");
                capturing=true;int current=stage;
                Screenshot.takeScreenshot(c.getMainRenderTarget(),image->{
                    try(image) {
                        Path folder=c.gameDirectory.toPath().resolve("api-ui-smoke");Files.createDirectories(folder);image.writeToFile(folder.resolve("page-"+current+".png"));
                    }catch(Exception e){throw new IllegalStateException("API UI screenshot failed");}
                    c.execute(()->{
                        if(current==1)press(c.screen,"提示词");
                        else if(current==2)press(c.screen,"范围与限速");
                        else if(current==3) {
                            press(c.screen,"在线翻译：关");press(c.screen,"取消");
                            require(!Sapphire.LLM_CONFIG.enabled,"cancel does not enable translation");
                            require(!Sapphire.LLM_CONFIG.profile().model.equals("custom-model"),"cancel does not save draft");
                            press(c.screen,"修改翻译");
                            var search=(EditBox)c.screen.children().stream().filter(e->e instanceof EditBox).findFirst().orElseThrow();search.setValue("RECOMBOBULATOR_3000");press(c.screen,"搜索");
                        } else {
                            press(c.screen,"新建");
                            var input=(EditBox)c.screen.children().stream().filter(e->e instanceof EditBox).toList().get(1);input.setValue("Sapphire editor smoke sentence.");
                            var target=(MultiLineEditBox)c.screen.children().stream().filter(e->e instanceof MultiLineEditBox).findFirst().orElseThrow();target.setValue("蓝宝石编辑器测试语句.");
                            press(c.screen,"保存并生效");
                            require(Sapphire.TRANSLATIONS.match("global",null,"Sapphire editor smoke sentence.").text().equals("蓝宝石编辑器测试语句."),"editor saves and reloads");
                            Sapphire.LOGGER.info("SAPPHIRE_API_UI_SMOKE_OK: API pages, editor search/save/reload, bounds and screenshots verified; no API requests");c.stop();
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
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
