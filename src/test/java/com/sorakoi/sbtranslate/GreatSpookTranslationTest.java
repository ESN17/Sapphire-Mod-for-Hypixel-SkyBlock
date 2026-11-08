package com.sorakoi.sbtranslate;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GreatSpookTranslationTest {
 static TranslationManager manager;
 @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
 static Component chat(Component source){var result=SystemTranslationEngine.translate(source,"chat",manager);return result==source?CommunityTranslationEngine.translate(source,"chat",manager):result;}
 @Test void allSixTransformationsUseExistingNamesAndPreserveStyles(){
  for(String mob:List.of("Commitment Phobia","Darkness Shade","Deadline","Math Teacher","Public Speaking Demon","Vegan Crawler")){
   var source=Component.literal("§eThe Primal Fear has turned into a §c"+mob+"§e!");var before=source.copy();var out=chat(source);
   assertEquals("原初恐惧变成了"+manager.scope("entity-names").get(mob)+"!",out.getString());
   StyledLoreTest.color(out,manager.scope("entity-names").get(mob),ChatFormatting.RED);assertEquals(before,source);
  }
 }
 @Test void livePlayersReplaceWikiNameExample(){
  var click=new ClickEvent.RunCommand("/msg Player_123");
  var player=Component.literal("Player_123").withStyle(s->s.withColor(ChatFormatting.AQUA).withClickEvent(click));
  var source=Component.literal("[FEAR] Public Speaking Demon: Speak ").append(player).append("!");
  var out=chat(source);assertTrue(out.getString().contains("Player_123"));assertFalse(out.getString().contains("Speak"));
  StyledLoreTest.color(out,"Player_123",ChatFormatting.AQUA);
  out.visit((style,text)->{if(text.contains("Player_123"))assertEquals(click,style.getClickEvent());return Optional.empty();},Style.EMPTY);
  assertFalse(chat(Component.literal("[FEAR] Public Speaking Demon: Say something interesting OtherPlayer!")).getString().contains("Say"));
 }
 @Test void candyAmountsAndSpawnNotifications(){
  for(String color:List.of("Green","Purple"))for(String count:List.of("1","23","1,234")){
   var out=chat(Component.literal("You got "+count+" "+color+" Candy from the Great Spook Tree!"));
   assertTrue(out.getString().contains(count));assertFalse(out.getString().contains("Candy"));
  }
  assertEquals("一个原初恐惧出现了!",chat(Component.literal("A Primal Fear has been summoned!")).getString());
 }
 @Test void existingExactFearDialogueAlreadyWorks() throws Exception {
  for(var file:CommunityTranslationEngine.corpusFiles(Path.of("src/main/resources/defaults/translations/community-wiki.json")))
   for(var value:TranslationManager.readObject(file).getAsJsonArray("entries")){
    var row=value.getAsJsonObject();String source=row.get("source").getAsString();
    if(source.startsWith("[FEAR] ") && !source.contains("[name]"))assertEquals(row.get("translation").getAsString(),chat(Component.literal(source)).getString(),source);
   }
 }
 @Test void playerChatAndUnrelatedMathRemainUntouched(){
  for(String text:List.of("Player_123: The Primal Fear has turned into a Public Speaking Demon!","What is 12 + 34?","12 + 34 = 46")){
   var source=Component.literal(text);assertSame(source,chat(source));
  }
 }
}
