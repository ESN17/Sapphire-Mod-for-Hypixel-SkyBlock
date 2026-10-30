package com.sorakoi.sbtranslate;
import com.sorakoi.sbtranslate.collection.UntranslatedCollector.Observation;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class DisplayTranslationHooks {
    public static Component text(Component source,String scope) {
        if(source==null || !Sapphire.active(scope)) return source;
        if(scope.equals("scoreboard") || scope.equals("tablist")) {
            Component translated=HudTranslationEngine.translate(source,scope,Sapphire.TRANSLATIONS);
            if(translated==source)collect(scope,TranslationManager.plain(source.getString()));
            return Sapphire.originalMode(translated,source);
        }
        String plain=TranslationManager.plain(source.getString());
        Component system=SystemTranslationEngine.translate(source,scope,Sapphire.TRANSLATIONS);
        if(system!=source)return Sapphire.originalMode(system,source);
        if(List.of("gui","title","subtitle","actionbar","bossbar").contains(scope)) {
            Component translated=SidebarTranslationEngine.location(source,Sapphire.TRANSLATIONS);
            if(translated!=source)return Sapphire.originalMode(translated,source);
        }
        if(scope.equals("gui")) {
            Component translated=GuiTranslationEngine.title(source,Sapphire.TRANSLATIONS);
            if(translated==source && Sapphire.TRANSLATIONS.exact("gui",plain))
                translated=TextTranslationEngine.translate(source,s->Sapphire.TRANSLATIONS.match("gui",null,s));
            if(translated==source)collect(scope,plain);
            return Sapphire.originalMode(translated,source);
        }
        if(scope.equals("chat") && !Sapphire.TRANSLATIONS.exact("chat",plain)) {
            Component bazaar=BazaarTranslationEngine.systemMessage(source,Sapphire.TRANSLATIONS);
            if(bazaar!=source)return Sapphire.originalMode(bazaar,source);
            Component community=CommunityTranslationEngine.translate(source,"chat",Sapphire.TRANSLATIONS);
            if(community!=source)return Sapphire.originalMode(community,source);
            collect(scope,plain); return source;
        }
        Component translated=TextTranslationEngine.translate(source,s -> Sapphire.TRANSLATIONS.match(scope,null,s));
        if(Sapphire.CONFIG.debug) {
            var rule=Sapphire.TRANSLATIONS.match(scope,null,plain);
            com.sorakoi.sbtranslate.client.Diagnostics.text(scope,rule==null?null:rule.rule(),translated!=source);
        }
        if(translated==source) collect(scope,plain);
        return Sapphire.originalMode(translated,source);
    }
    private static void collect(String scope,String text) {
        if(Sapphire.canCollect() && Sapphire.POLICY.allows(scope,text))
            Sapphire.COLLECTOR.observe(new Observation(scope,scope,null,null,"",List.of(text),false),true,true);
    }
}
