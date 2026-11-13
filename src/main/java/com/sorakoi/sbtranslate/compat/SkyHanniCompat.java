package com.sorakoi.sbtranslate.compat;

import com.sorakoi.sbtranslate.Sapphire;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.Map;

/** No references to SkyHanni classes; absent mod means no dictionary IO or hooks. */
public final class SkyHanniCompat {
    private static volatile SkyHanniDictionary dictionary=new SkyHanniDictionary(Map.of());
    private static final SkyHanniLiveTranslation live=new SkyHanniLiveTranslation();
    private static final SkyHanniLiveTranslation hud=new SkyHanniLiveTranslation();
    private static int hudScopes(){return (Sapphire.CONFIG.tablist?1:0)|(Sapphire.CONFIG.scoreboard?2:0);}
    public static void load(Path root) {
        if(!FabricLoader.getInstance().isModLoaded("skyhanni"))return;
        try {
            var next=SkyHanniDictionary.load(root);dictionary=next;
            Sapphire.LOGGER.info("SkyHanni optional translation dictionary: {} entries",next.size());
        }catch(Exception e){Sapphire.LOGGER.warn("SkyHanni translation unavailable; keeping original display",e);}
    }
    public static Component display(Component source) {
        if(!Sapphire.CONFIG.enabled || !Sapphire.CONFIG.skyHanniTranslation)return source;
        if(source==null || onlinePlayer(source.getString()))return source;
        return Sapphire.originalMode(hud.translate(source,Sapphire.TRANSLATIONS,dictionary,hudScopes()),source);
    }
    public static String display(String source) {
        if(!Sapphire.CONFIG.enabled || !Sapphire.CONFIG.skyHanniTranslation)return source;
        if(source==null || onlinePlayer(source))return source;
        String translated=hud.translate(source,Sapphire.TRANSLATIONS,dictionary,hudScopes());
        return Sapphire.CONFIG.showOriginal && !translated.equals(source)?translated+" ["+source+"]":translated;
    }
    public static Component chat(Component source) {
        if(!Sapphire.CONFIG.enabled || !Sapphire.CONFIG.chat || !Sapphire.CONFIG.skyHanniTranslation)return source;
        return com.sorakoi.sbtranslate.client.ChatOriginalHover.attach(live.translate(source,Sapphire.TRANSLATIONS,dictionary),source);
    }
    private static boolean onlinePlayer(String source) {
        String text=com.sorakoi.sbtranslate.translation.TranslationManager.plain(source);
        if(text.isEmpty() || text.length()>16)return false;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);if(!(c>='A' && c<='Z' || c>='a' && c<='z' || c>='0' && c<='9' || c=='_'))return false;
        }
        var client=net.minecraft.client.Minecraft.getInstance();
        return client!=null && client.getConnection()!=null && client.getConnection().getPlayerInfo(text)!=null;
    }
}
