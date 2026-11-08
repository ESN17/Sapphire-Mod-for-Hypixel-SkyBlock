package com.sorakoi.sbtranslate.compat;
import com.sorakoi.sbtranslate.Sapphire;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.*;
import java.nio.file.Path;
import java.util.Map;

public final class OdinCompat {
    private static final ThreadLocal<Integer> DEPTH=ThreadLocal.withInitial(()->0);
    private static volatile boolean loaded;
    private static volatile SkyHanniDictionary dictionary=new SkyHanniDictionary(Map.of());
    private static final SkyHanniLiveTranslation LIVE=new SkyHanniLiveTranslation();
    public static void enter(){DEPTH.set(DEPTH.get()+1);}
    public static void exit(){int depth=DEPTH.get()-1;if(depth<=0)DEPTH.remove();else DEPTH.set(depth);}
    public static boolean scoped(){return loaded && DEPTH.get()>0 && Sapphire.CONFIG.enabled && Sapphire.CONFIG.odinTranslation;}
    public static void load(Path root){
        loaded=FabricLoader.getInstance().isModLoaded("odin");if(!loaded)return;
        try{dictionary=SkyHanniDictionary.load(root,"odin-zh_cn.json");}
        catch(Exception ex){Sapphire.LOGGER.warn("Odin dictionary unavailable; retaining original display");}
    }
    public static String text(String source){return loaded && Sapphire.CONFIG.enabled && Sapphire.CONFIG.odinTranslation?LIVE.translate(source,Sapphire.TRANSLATIONS,dictionary):source;}
    public static Component text(Component source){return loaded && Sapphire.CONFIG.enabled && Sapphire.CONFIG.odinTranslation?LIVE.translate(source,Sapphire.TRANSLATIONS,dictionary):source;}
    public static Component chat(Component source){
        String plain=com.sorakoi.sbtranslate.translation.TranslationManager.plain(source.getString());
        if(!loaded || !Sapphire.CONFIG.odinTranslation || !plain.startsWith("Odin »"))return source;
        int start=plain.indexOf('»')+1;while(start<plain.length() && plain.charAt(start)==' ')start++;
        var prefix=com.sorakoi.sbtranslate.translation.TextTranslationEngine.structured(source,java.util.List.of(new com.sorakoi.sbtranslate.translation.TranslationManager.Piece(plain.substring(0,start),0,start,true)));
        var body=com.sorakoi.sbtranslate.translation.TextTranslationEngine.structured(source,java.util.List.of(new com.sorakoi.sbtranslate.translation.TranslationManager.Piece(plain.substring(start),start,plain.length(),true)));
        var translated=text(body);return translated==body?source:com.sorakoi.sbtranslate.client.ChatOriginalHover.attach(Component.empty().append(prefix).append(translated),source);
    }
}
