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
            ClassLoader loader=helper.getClassLoader();
            if(loader.getResource("com/odtheking/odin/utils/ui/rendering/NVGRenderer.class")!=null) {
                var renderer=Class.forName("com.odtheking.odin.utils.ui.rendering.NVGRenderer");
                var instance=renderer.getField("INSTANCE").get(null);
                var face=renderer.getMethod("getDefaultFont").invoke(instance);
                var width=renderer.getMethod("textWidth",String.class,float.class,face.getClass());
                var value=(float)width.invoke(instance,"Text Scale",16f,face);
                Sapphire.CONFIG.odinTranslation=false;
                var chinese=(float)width.invoke(instance,"文字缩放",16f,face);
                if(value<=0 || value!=chinese || value==(float)width.invoke(instance,"Text Scale",16f,face))throw new IllegalStateException("Legacy Odin label width");
                if(System.getProperty("os.name").startsWith("Windows")){
                    long vg=(long)renderer.getMethod("access$getVg$p").invoke(null);
                    var api=Class.forName("org.lwjgl.nanovg.NanoVG");
                    if((int)api.getMethod("nvgFindFont",long.class,CharSequence.class).invoke(null,vg,"sapphire-odin-cjk")<0)throw new IllegalStateException("Legacy Odin CJK font missing");
                }
                Sapphire.LOGGER.info("SAPPHIRE_ODIN_NANOVG_OK");
            } else if(loader.getResource("com/odtheking/odin/utils/ui/compose/LayoutsKt$Text$2$1.class")!=null) {
                var measure=Class.forName("com.odtheking.odin.utils.ui.compose.LayoutsKt$Text$2$1");
                var function=Class.forName("kotlin.jvm.functions.Function0");
                var supplier=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{function},(p,m,a)->"Text Scale");
                var ctor=measure.getDeclaredConstructor(function);ctor.setAccessible(true);
                var method=java.util.Arrays.stream(measure.getDeclaredMethods()).filter(m->m.getName().equals("invoke") && m.getReturnType().getName().equals("kotlin.Pair")).findFirst().orElseThrow();
                method.setAccessible(true);
                var pair=method.invoke(ctor.newInstance(supplier));
                if((int)pair.getClass().getMethod("getFirst").invoke(pair)!=font.width(translated))throw new IllegalStateException("Compose label width outside render scope");
                Sapphire.LOGGER.info("SAPPHIRE_ODIN_COMPOSE_OK");
            }
            Sapphire.CONFIG.odinTranslation=false;
            if((int)helper.getMethod("getStringWidth",String.class).invoke(null,"Text Scale")!=original)throw new IllegalStateException("Odin disable failed");
            Sapphire.LOGGER.info("SAPPHIRE_ODIN_SMOKE_OK: official mod, exact dictionary, actual width hook, UI scope and disable");
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Odin integration smoke failed",e);}
        finally{Sapphire.CONFIG.odinTranslation=enabled;}
    }
}
