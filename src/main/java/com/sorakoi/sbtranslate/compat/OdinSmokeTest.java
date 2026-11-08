package com.sorakoi.sbtranslate.compat;
import com.sorakoi.sbtranslate.Sapphire;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
public final class OdinSmokeTest {
    public static void run(){
        if(!FabricLoader.getInstance().isModLoaded("odin"))return;
        boolean enabled=Sapphire.CONFIG.odinTranslation;
        try{
            Sapphire.CONFIG.odinTranslation=true;
            var font=Minecraft.getInstance().font;int original=font.width("Text Scale");
            String translated=OdinCompat.text("Text Scale");if(!com.sorakoi.sbtranslate.translation.TranslationManager.plain(translated).equals("文字缩放"))throw new IllegalStateException("Odin dictionary unavailable");
            Class<?> helper=Class.forName("com.odtheking.odin.utils.render.DrawContextUtilsKt");
            int measured=(int)helper.getMethod("getStringWidth",String.class).invoke(null,"Text Scale");
            if(measured!=font.width(translated))throw new IllegalStateException("Odin measurement not translated");
            OdinCompat.enter();try{if(font.width("Text Scale")!=font.width(translated))throw new IllegalStateException("Odin UI scope failed");}finally{OdinCompat.exit();}
            if(font.width("Text Scale")!=original)throw new IllegalStateException("Odin scope escaped");
            Sapphire.CONFIG.odinTranslation=false;
            if((int)helper.getMethod("getStringWidth",String.class).invoke(null,"Text Scale")!=original)throw new IllegalStateException("Odin disable failed");
            Class.forName("com.odtheking.odin.utils.ui.compose.UiHost");
            Sapphire.LOGGER.info("SAPPHIRE_ODIN_SMOKE_OK: official mod, exact dictionary, actual width hook, UI scope and disable");
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Odin integration smoke failed",e);}
        finally{Sapphire.CONFIG.odinTranslation=enabled;}
    }
}
