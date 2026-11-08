package com.sorakoi.sbtranslate.client;

import com.sorakoi.sbtranslate.Sapphire;
import com.sorakoi.sbtranslate.llm.*;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class OnlineChatTranslation {
    private static long lastFailureNotice;
    public static void receive(Component original,boolean player,String sender,Consumer<Supplier<Component>> complete) {
        if(!Sapphire.active("chat") || !Sapphire.LLM_CONFIG.enabled)return;
        var client=Minecraft.getInstance();if(client.getConnection()==null)return;
        String plain=TranslationManager.plain(original.getString());
        var selected=LlmText.select(plain,player,Sapphire.LLM_CONFIG);if(selected==null)return;
        if(!player && usesLocalDictionary(original,Sapphire.TRANSLATIONS))return;
        var names=new ArrayList<String>();
        for(var info:client.getConnection().getOnlinePlayers())names.add(info.getProfile().name());
        var prepared=LlmStyledText.prepare(original,selected,names);
        if(prepared==null){failure("消息格式无法安全保留，已保留原文");return;}
        long generation=Sapphire.LLM.generation();
        boolean accepted=Sapphire.LLM.submit(prepared.text(),prepared::valid,result->client.execute(()->{
            if(!Sapphire.LLM.current(result.generation()))return;
            complete.accept(() -> {
                if(!Sapphire.LLM.current(result.generation()) || !Sapphire.active("chat") || client.getConnection()==null)return null;
                Component line=prepared.restore(result.text());if(line==null || line.getString().equals(plain))return null;
                if(selected.prefix().isBlank() && player && sender!=null && !sender.isBlank())
                    line=Component.empty().append(Component.literal(sender+": ").withStyle(ChatFormatting.GRAY)).append(line);
                return line;
            });
        }),error->client.execute(()->{if(Sapphire.LLM.current(generation) && client.getConnection()!=null)failure(error);}));
        if(!accepted)failure(Sapphire.LLM.status());
    }
    static void failure(String error) {
        long now=System.currentTimeMillis();if(now-lastFailureNotice<15000)return;lastFailureNotice=now;
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(
            SapphireNotifications.message(SapphireNotifications.Tone.WARNING,"在线翻译："+error));
    }
    /** In particular, do not ask an API to invent names for unlisted Fire Sale items. */
    public static boolean usesLocalDictionary(Component original,TranslationManager manager) {
        String plain=TranslationManager.plain(original.getString());
        return manager.exact("chat",plain) || SystemTranslationEngine.match(plain,"chat",manager)!=null ||
            BazaarTranslationEngine.systemMessage(original,manager)!=original || CommunityTranslationEngine.translate(original,"chat",manager)!=original;
    }
}
