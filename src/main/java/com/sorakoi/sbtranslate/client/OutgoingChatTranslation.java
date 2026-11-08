package com.sorakoi.sbtranslate.client;

import com.sorakoi.sbtranslate.Sapphire;
import com.sorakoi.sbtranslate.llm.OutgoingChatTranslationService;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;

public final class OutgoingChatTranslation {
    private static boolean sending;
    public static void register(){ClientSendMessageEvents.ALLOW_CHAT.register(OutgoingChatTranslation::allow);}
    private static boolean allow(String text) {
        if(sending || !Sapphire.active("chat") || !Sapphire.LLM_CONFIG.enabled || !Sapphire.LLM_CONFIG.outgoing || !OutgoingChatTranslationService.hasChinese(text))return true;
        var client=Minecraft.getInstance();var connection=client.getConnection();if(connection==null)return true;
        client.gui.hud.getChat().addClientSystemMessage(SapphireNotifications.message(SapphireNotifications.Tone.INFO,"正在翻译，成功后自动发送英文..."));
        Sapphire.OUTGOING.submit(Sapphire.LLM_CONFIG,text,result->client.execute(()->{
            if(!Sapphire.OUTGOING.current(result.generation()) || client.getConnection()!=connection)return;
            if(result.translated()==null || !Sapphire.active("chat") || !Sapphire.LLM_CONFIG.enabled || !Sapphire.LLM_CONFIG.outgoing) {
                recover(result.original(),result.error().isBlank()?"设置已变化，未发送":result.error());return;
            }
            sending=true;
            try{connection.sendChat(result.translated());}finally{sending=false;}
        }));
        return false;
    }
    private static void recover(String original,String error) {
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(SapphireNotifications.message(SapphireNotifications.Tone.WARNING, Component.literal(error+"。点击恢复原文（尚未发送）")
            .withStyle(s->s.withColor(ChatFormatting.YELLOW).withClickEvent(new ClickEvent.SuggestCommand(original)).withHoverEvent(new HoverEvent.ShowText(Component.literal(original))))));
    }
}
