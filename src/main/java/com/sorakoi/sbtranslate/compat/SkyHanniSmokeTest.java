package com.sorakoi.sbtranslate.compat;

import com.sorakoi.sbtranslate.Sapphire;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Explicit offline development smoke only; not run in ordinary gameplay. */
public final class SkyHanniSmokeTest {
    public static void run() {
        if(!FabricLoader.getInstance().isModLoaded("skyhanni"))return;
        boolean enabled=Sapphire.CONFIG.skyHanniTranslation,original=Sapphire.CONFIG.showOriginal,gui=Sapphire.CONFIG.gui;
        try {
            Sapphire.CONFIG.gui=false;Sapphire.CONFIG.skyHanniTranslation=true;Sapphire.CONFIG.showOriginal=false;
            String base="at.hannibal2.skyhanni.deps.moulconfig.";
            Class<?> textType=Class.forName(base+"common.text.StructuredText");
            Object text=textType.getMethod("of",String.class).invoke(null,"Visitor Timer");
            Class<?> fontType=Class.forName(base+"platform.MoulConfigFontRenderer");
            Object renderer=fontType.getConstructor(net.minecraft.client.gui.Font.class).newInstance(Minecraft.getInstance().font);
            int actual=(int)fontType.getMethod("getStringWidth",textType).invoke(renderer,text);
            Component translated=SkyHanniCompat.display(Component.literal("Visitor Timer"));
            if(translated.getString().equals("Visitor Timer"))throw new IllegalStateException("SkyHanni dictionary did not load");
            int expected=Minecraft.getInstance().font.width(translated);
            if(actual!=expected)throw new IllegalStateException("SkyHanni measured original text: "+actual+" / "+expected);
            if(!textType.getMethod("getText").invoke(text).equals("Visitor Timer"))throw new IllegalStateException("SkyHanni source changed");
            Sapphire.CONFIG.skyHanniTranslation=false;
            actual=(int)fontType.getMethod("getStringWidth",textType).invoke(renderer,text);
            if(actual!=Minecraft.getInstance().font.width("Visitor Timer"))throw new IllegalStateException("SkyHanni disable failed");
            // Force load every optional target for Mixin verification.
            Class.forName(base+"platform.MoulConfigRenderContext");
            Class.forName("at.hannibal2.skyhanni.utils.renderables.primitives.StringRenderable");
            Class.forName("at.hannibal2.skyhanni.utils.renderables.primitives.TextRenderable");
            Class.forName("at.hannibal2.skyhanni.utils.ChatUtils");
            Class.forName("at.hannibal2.skyhanni.utils.compat.TextCompatKt");
            Sapphire.CONFIG.skyHanniTranslation=true;
            String tabBase="at.hannibal2.skyhanni.features.misc.compacttablist.";
            Class<?> type=Class.forName(tabBase+"TabStringType"),lineClass=Class.forName(tabBase+"TabLine");
            var raw=Component.literal("Area: The Rift");
            Object line=lineClass.getConstructor(Component.class,type,Component.class).newInstance(raw,type.getField("TITLE").get(null),null);
            actual=(int)lineClass.getMethod("getWidth").invoke(line);
            if(actual!=Minecraft.getInstance().font.width(SkyHanniCompat.display(raw)))throw new IllegalStateException("Compact Tab measured English");
            if(lineClass.getMethod("getComponent").invoke(line)!=raw)throw new IllegalStateException("Compact Tab parser data changed");
            Class.forName(tabBase+"TabListRenderer");
            Class<?> drawClass=Class.forName("at.hannibal2.skyhanni.utils.GuiRenderUtils");
            var hook=java.util.Arrays.stream(drawClass.getDeclaredMethods()).filter(m->m.getName().endsWith("sapphire$drawComponent")).findFirst().orElseThrow();
            hook.setAccessible(true);
            var display=(Component)hook.invoke(drawClass.getField("INSTANCE").get(null),raw);
            if(!display.getString().contains("裂隙") || !raw.getString().equals("Area: The Rift"))throw new IllegalStateException("SkyHanni direct drawing boundary");
            Sapphire.LOGGER.info("SAPPHIRE_SKYHANNI_SMOKE_OK: actual MoulConfig width, dictionary, disable, immutable source and optional target loading");
            Sapphire.LOGGER.info("SAPPHIRE_SKYHANNI_HANDOFF_SMOKE_OK: compact Tab display/width hooks preserve original parser inputs");
        }catch(ReflectiveOperationException e){throw new IllegalStateException("SkyHanni integration smoke failed",e);}
        finally{Sapphire.CONFIG.skyHanniTranslation=enabled;Sapphire.CONFIG.showOriginal=original;Sapphire.CONFIG.gui=gui;}
    }
}
