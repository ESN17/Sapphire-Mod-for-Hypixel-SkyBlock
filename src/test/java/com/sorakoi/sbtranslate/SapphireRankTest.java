package com.sorakoi.sbtranslate;
import com.sorakoi.sbtranslate.rank.*;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SapphireRankTest {
    @TempDir Path root;
    static final String JSON="""
        {"schemaVersion":1,"users":[
        {"name":"aaa","tag":"SAPPHIRE","tagColor":"#0000AA","nameColor":"#FFFFFF"},
        {"name":"ESN17_OwO","tag":"SAPPDEV","tagColor":"#FF5555","nameColor":"#FF5555"},
        {"name":"BoostingDetected","tag":"SAPPDEV","tagColor":"#FF5555","nameColor":"#FF5555"}]}
        """;
    final RankRoster roster=RankRoster.parse(JSON);
    @Test void completeMessageAndPreservedChannel(){
        for(String rank:List.of("[VIP] ","[VIP+] ","[MVP+] ","[MVP++] ","")) {
            Component c=Component.literal("Party > [251] "+rank+"aaa: hello [MVP+] aaa!");var saved=c.copy();
            assertEquals("Party > [251] [SAPPHIRE] aaa: hello [SAPPHIRE] aaa!",RankDisplay.chat(c,roster).getString());assertEquals(saved,c);
        }
        assertEquals("<[SAPPHIRE] aaa> hello",RankDisplay.chat(Component.literal("<aaa> hello"),roster).getString());
    }
    @Test void noOtherUsersCommandsOrNpcLabels(){for(String s:List.of("[NPC] aaa: hello","[MVP+] bbb: abc","aaa_2: hi","/msg/aaa","https://example.com/aaa")){var c=Component.literal(s);assertSame(c,RankDisplay.chat(c,roster));}}
    @Test void devsAndColors(){for(String name:List.of("ESN17_OwO","BoostingDetected")){var result=RankDisplay.chat(Component.literal("[MVP++] "+name+": hi"),roster);assertEquals("[SAPPDEV] "+name+": hi",result.getString());assertEquals(0xff5555,result.getSiblings().getFirst().getStyle().getColor().getValue());}}
    @Test void keepBodyStyleAndClickEvents(){
        var click=new ClickEvent.SuggestCommand("/msg aaa ");
        var original=Component.literal("[VIP] aaa").withStyle(s->s.withColor(ChatFormatting.GREEN).withClickEvent(click))
            .append(Component.literal(": hi").withStyle(ChatFormatting.GOLD));
        var result=RankDisplay.chat(original,roster);
        assertEquals(click,result.getSiblings().get(3).getStyle().getClickEvent());
        assertEquals(0x0000aa,result.getSiblings().get(0).getStyle().getColor().getValue());
        assertEquals(0xffffff,result.getSiblings().get(1).getStyle().getColor().getValue());
        assertEquals(0x0000aa,result.getSiblings().get(2).getStyle().getColor().getValue());
        assertEquals(0x5555ff,result.getSiblings().get(3).getStyle().getColor().getValue());
        assertEquals(0xffaa00,result.getSiblings().getLast().getStyle().getColor().getValue());
    }
    @Test void tabRowsAndLegacyColors(){
        var c=Component.literal("§7[50] §b[MVP§c+§b] aaa §6♲");
        assertEquals("[50] [SAPPHIRE] aaa ♲",RankDisplay.tab(c,"aaa",roster).getString());
        assertEquals("[50] [SAPPHIRE] aaa ♲",RankDisplay.tab(c,"synthetic",roster).getString());
        assertEquals("[SAPPHIRE] aaa",RankDisplay.tab(Component.literal("aaa"),"aaa",roster).getString());
    }
    @Test void levelsKeepHypixelColorsAcrossSplitComponents(){
        for(var pair:Map.of("aaa",ChatFormatting.BLUE,"BoostingDetected",ChatFormatting.RED).entrySet()) {
            var name=pair.getKey();var color=pair.getValue();
            var source=Component.literal("§7[§b9§7] §a[VIP] ")
                .append(Component.literal(name.substring(0,2)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(name.substring(2)).withStyle(ChatFormatting.BLUE))
                .append(Component.literal(": ?").withStyle(ChatFormatting.YELLOW));
            var before=source.copy();var display=RankDisplay.chat(source,roster);
            StyledLoreTest.color(display,"9",ChatFormatting.AQUA);
            StyledLoreTest.color(display,name,color);
            assertEquals(0xaaaaaa,display.getSiblings().getFirst().getStyle().getColor().getValue());
            StyledLoreTest.color(display,": ?",ChatFormatting.YELLOW);assertEquals(before,source);
        }
    }
    @Test void wholeGuildListMultipleNamesAndStatusColors(){
        var raw=Component.literal("§b[MVP+] biggest_god §c●   §a[VIP] Boosting")
            .append(Component.literal("Detected §a●\n§b[MVP+] aaa §c●   Summer_12 §c●"));
        var before=raw.copy();var result=RankDisplay.text(raw,roster);
        assertEquals("[MVP+] biggest_god ●   [SAPPDEV] BoostingDetected ●\n[SAPPHIRE] aaa ●   Summer_12 ●",result.getString());
        StyledLoreTest.color(result,"BoostingDetected",ChatFormatting.RED);
        StyledLoreTest.color(result,"aaa",ChatFormatting.BLUE);
        StyledLoreTest.color(result,"SAPPHIRE",ChatFormatting.WHITE);
        assertEquals(before,raw);
        assertEquals(result,RankDisplay.text(result,roster));
    }
    @Test void systemMentionsAndRepeatingThePassDoNotDuplicateRank(){
        var raw=Component.literal("You sold an item to aaa. aaa joined the guild!");
        var result=RankDisplay.text(raw,roster);
        assertEquals("You sold an item to [SAPPHIRE] aaa. [SAPPHIRE] aaa joined the guild!",result.getString());
        assertEquals(result,RankDisplay.text(result,roster));
    }
    @Test void personalSkyblockLevelKeepsXpStyleAndValue(){
        for(String label:List.of("SB Level:","空岛等级:")) {
            var source=Component.literal(label+" §7[9] §b12/100 XP");
            var result=RankDisplay.selfLevel(source,"BoostingDetected",roster);
            assertSame(source,result);
        }
    }
    static class Transport implements RankClient.Transport {int uploads,downloads;boolean fail;String json="{\"schemaVersion\":1,\"users\":[]}";
        public String roster(){downloads++;return json;}
        public void register(String name)throws IOException{uploads++;if(fail)throw new IOException("offline");json=JSON;}
    }
    @Test void registerOnceAcrossClientRestarts()throws Exception{var t=new Transport();new RankClient(root,t).refresh("aaa");new RankClient(root,t).refresh("aaa");assertEquals(1,t.uploads);assertEquals(3,t.downloads);assertTrue(Files.exists(root.resolve("rank-registration.json")));}
    @Test void periodicRefreshNeverUploadsNames()throws Exception{var t=new Transport();new RankClient(root,t).refreshRoster();new RankClient(root,t).refreshRoster();assertEquals(0,t.uploads);assertEquals(2,t.downloads);assertFalse(Files.exists(root.resolve("rank-registration.json")));}
    @Test void freshInstallKnownNameDoesNotUpload()throws Exception{var t=new Transport();t.json=JSON;new RankClient(root,t).refresh("aaa");assertEquals(0,t.uploads);assertEquals(1,t.downloads);}
    @Test void failedRegistrationRetriesNextLogin()throws Exception{var t=new Transport();t.fail=true;assertThrows(IOException.class,()->new RankClient(root,t).refresh("aaa"));assertFalse(Files.exists(root.resolve("rank-registration.json")));t.fail=false;new RankClient(root,t).refresh("aaa");assertEquals(2,t.uploads);}
    @Test void receiptSurvivesRosterRemoval()throws Exception{var t=new Transport();var client=new RankClient(root,t);client.refresh("aaa");t.json="{\"schemaVersion\":1,\"users\":[]}";client.refresh("aaa");assertEquals(1,t.uploads);assertNull(client.cached().find("aaa"));}
    @Test void acknowledgementSavedBeforeFollowupFetch()throws Exception{
        var t=new Transport(){public String roster(){if(++downloads==2)throw new IllegalStateException("offline after successful POST");return json;}};
        assertThrows(IllegalStateException.class,()->new RankClient(root,t).refresh("aaa"));
        assertTrue(Files.exists(root.resolve("rank-registration.json")));
        t.json="{\"schemaVersion\":1,\"users\":[]}";new RankClient(root,t).refresh("aaa");assertEquals(1,t.uploads);
    }
    @Test void deadAndPrivateChatPrefixes(){for(String prefix:List.of("(DEAD) ","[DEAD] ","☠ ","From ","To ","Guild > "))assertEquals(prefix+"[SAPPHIRE] aaa: hi",RankDisplay.chat(Component.literal(prefix+"[MVP++] aaa: hi"),roster).getString());}
    @Test void channelWordsDoNotMatchInsideUsername(){for(String name:List.of("Toaaa","Fromaaa","Guildaaa")){var c=Component.literal(name+": hi");assertSame(c,RankDisplay.chat(c,roster));}}
    @Test void rejectsMalformedRemoteData(){assertThrows(RuntimeException.class,()->RankRoster.parse(JSON.replace("#FFFFFF","bad")));assertThrows(RuntimeException.class,()->RankRoster.parse(JSON.replace("SAPPHIRE","bad tag")));}
    @Test void onlyHypixelConnectionsActivateRegistration(){
        assertTrue(com.sorakoi.sbtranslate.client.SapphireRanks.hypixel("MC.HYPIXEL.NET:25565"));
        for(String host:List.of("localhost","hypixel.net.example.com","fakehypixel.net","127.0.0.1"))assertFalse(com.sorakoi.sbtranslate.client.SapphireRanks.hypixel(host));
        assertFalse(com.sorakoi.sbtranslate.client.SapphireRanks.hypixel(null));
    }
}
