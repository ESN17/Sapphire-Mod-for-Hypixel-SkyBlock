package com.sorakoi.sbtranslate.translation;

import net.minecraft.network.chat.Component;

/** Scoped HUD fields; never used for player chat or inventory descriptions. */
public final class HudTranslationEngine {
    public static Component translate(Component source,String scope,TranslationManager manager) {
        Component display=TextTranslationEngine.resolvedDisplay(source);
        Component result=TextTranslationEngine.lines(display,line->{
            Component clean=scope.equals("bossbar")?line:TextTranslationEngine.cleanHud(line);
            Component translated=TextTranslationEngine.padded(clean,inner->field(inner,scope,manager));
            return translated==clean?line:translated;
        });
        return result==display?source:result;
    }
    private static Component field(Component source,String scope,TranslationManager manager) {
        Component translated=SystemTranslationEngine.translate(source,scope,manager);
        if(translated!=source)return translated;
        if(scope.equals("bossbar")) {
            translated=com.sorakoi.sbtranslate.client.EntityNameDisplay.translate(source,manager);
            if(translated!=source)return translated;
        }
        translated=scope.equals("scoreboard")?SidebarTranslationEngine.line(source,manager):SidebarTranslationEngine.location(source,manager);
        if(translated!=source)return translated;
        String text=TranslationManager.plain(source.getString());
        String exact=manager.scope(scope).get(text);
        if(exact!=null)return TextTranslationEngine.rangeReplacement(source,0,text.length(),exact);
        // Only reviewed field labels at the start of a full row qualify. Copy the
        // complete value so icons, profile/player/NPC names and numbers survive.
        for(var entry:manager.scope(scope).entrySet())if(entry.getKey().endsWith(":") && text.startsWith(entry.getKey()+" "))
            return TextTranslationEngine.rangeReplacement(source,0,entry.getKey().length(),entry.getValue());
        return scope.equals("scoreboard") || scope.equals("bossbar")?TextTranslationEngine.translate(source,s->manager.match(scope,null,s)):source;
    }
}
