package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.SystemTranslationEngine;
import com.sorakoi.sbtranslate.translation.TranslationManager;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CapturedChatRulesTest {
    @Test void capturedNumbersAndItemNamesRenderThroughChatEngine() throws Exception {
        var dictionary=new TranslationManager();
        dictionary.load(Path.of("src/main/resources/defaults"));
        for(int count:new int[]{2,137}){
            var source=Component.literal("HIVE! You found Honeycomb x"+count+" and Honeybuzz Shard!");
            var rendered=SystemTranslationEngine.translate(source,"chat",dictionary);
            assertEquals("蜂巢！你找到了 "+count+" 个蜜脾和蜜鸣碎片！",rendered.getString());
        }
        for(var mode:java.util.Map.of("§8§lASHEN","灰烬","§f§lSPIRIT","灵魂","§e§lAURIC","金光","§b§lCRYSTAL","水晶").entrySet()){
            var raw=Component.literal("§cStrike using the "+mode.getKey()+" §cattunement on your dagger!");
            assertEquals("使用匕首上的"+mode.getValue()+"调谐进行攻击！",SystemTranslationEngine.translate(raw,"chat",dictionary).getString());
        }
        var progress=Component.literal("   §eBlaze Slayer LVL 7 §5- §7Next LVL in §d280,421 XP§7!");
        assertEquals("   烈焰人杀手 等级 7 - 距下一级还需 280,421 经验!",SystemTranslationEngine.translate(progress,"chat",dictionary).getString());
        for(String time:new String[]{"1m 42s","57s"}){
            var raw=Component.literal("§e[§eS§ek§ey§eH§ea§6n§6n§6i§6]§6 §eSlayer quest took §b"+time+"§e to complete.");
            assertEquals("[SkyHanni] 杀手任务耗时 "+time+"。",SystemTranslationEngine.translate(raw,"chat",dictionary).getString());
        }
    }
}
