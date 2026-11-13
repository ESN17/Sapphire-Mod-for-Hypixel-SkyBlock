package com.sorakoi.sbtranslate.compat;

import com.sorakoi.sbtranslate.Sapphire;
import java.nio.file.*;
import java.util.List;

/** The NanoVG UI uses its own Latin font. Add a system CJK fallback to each new Odin face. */
public final class OdinLegacyFont {
    private static final String FACE="sapphire-odin-cjk";
    private static boolean warned;

    public static void attach(long context,int base){
        if(context==0 || base<0)return;
        try{
            Class<?> nvg=Class.forName("org.lwjgl.nanovg.NanoVG");
            int font=(int)nvg.getMethod("nvgFindFont",long.class,CharSequence.class).invoke(null,context,FACE);
            if(font<0){
                for(Path path:List.of(Path.of(System.getenv().getOrDefault("WINDIR","C:/Windows"),"Fonts","msyh.ttc"),
                    Path.of("/System/Library/Fonts/PingFang.ttc"),Path.of("/System/Library/Fonts/STHeiti Light.ttc"),
                    Path.of("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
                    Path.of("/usr/share/fonts/google-noto-cjk/NotoSansCJK-Regular.ttc"),
                    Path.of("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"))){
                    if(!Files.isRegularFile(path))continue;
                    font=(int)nvg.getMethod("nvgCreateFont",long.class,CharSequence.class,CharSequence.class)
                        .invoke(null,context,FACE,path.toString());
                    if(font>=0)break;
                }
            }
            if(font>=0)nvg.getMethod("nvgAddFallbackFontId",long.class,int.class,int.class).invoke(null,context,base,font);
            else warn("no supported system CJK font");
        }catch(ReflectiveOperationException | LinkageError e){warn(e.toString());}
    }
    private static void warn(String why){if(!warned){warned=true;Sapphire.LOGGER.warn("Odin legacy font fallback unavailable: {}",why);}}
}
