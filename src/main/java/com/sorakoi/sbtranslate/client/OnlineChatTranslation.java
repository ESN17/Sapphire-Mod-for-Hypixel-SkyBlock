package com.sorakoi.sbtranslate.client;

import com.sorakoi.sbtranslate.Sapphire;
import com.sorakoi.sbtranslate.llm.*;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;
import java.util.*;

public final class OnlineChatTranslation {
    public static void receive(Component original,boolean player,String sender) {
        if(!Sapphire.active("chat") || !Sapphire.LLM_CONFIG.enabled)return;
        String plain=TranslationManager.plain(original.getString());
        var selected=LlmText.select(plain,player,Sapphire.LLM_CONFIG);if(selected==null)return;
        if(!player && (Sapphire.TRANSLATIONS.exact("chat",plain) || BazaarTranslationEngine.systemMessage(original,Sapphire.TRANSLATIONS)!=original))return;
        var client=Minecraft.getInstance();if(client.getConnection()==null)return;
        var names=new ArrayList<String>();
        for(var info:client.getConnection().getOnlinePlayers())names.add(info.getProfile().name());
        var protectedText=LlmText.protect(selected.body(),names);if(protectedText==null)return;
        Component saved=original.copy();String prefix=!selected.prefix().isBlank()?selected.prefix():player && sender!=null && !sender.isBlank()?sender+": ":"";
        Sapphire.LLM.submit(protectedText.text(),result->client.execute(()->{
            if(!Sapphire.LLM.current(result.generation()) || !Sapphire.active("chat") || client.getConnection()==null)return;
            String translated=protectedText.restore(result.text());if(translated==null || translated.equals(selected.body()))return;
            var line=Component.literal("[译] ").withStyle(ChatFormatting.AQUA);
            if(!prefix.isBlank()) {
                if(!selected.prefix().isBlank())line.append(TextTranslationEngine.structured(saved,List.of(new TranslationManager.Piece(prefix,0,prefix.length(),true))));
                else line.append(Component.literal(prefix).withStyle(ChatFormatting.GRAY));
            }
            line.append(Component.literal(translated.replace('\n',' ').replace('\t',' ')).withStyle(ChatFormatting.WHITE));
            line.withStyle(style->style.withHoverEvent(new HoverEvent.ShowText(saved)));
            // Client-only HUD append: never sends chat or invokes commands.
            client.gui.getChat().addClientSystemMessage(line);
        }));
    }
}
