package com.sorakoi.sbtranslate;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DungeonSupplementTest {
 static TranslationManager manager;
 @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
 @AfterAll static void release(){manager=null;}
 @Test void sacksWithSeparateAddedRemovedHovers() {
  for(String time:List.of("5s","8s","30s","1m 5s"))for(int mode=0;mode<3;mode++) {
   var add=new HoverEvent.ShowText(Component.literal("Added 17 items"));
   var remove=new HoverEvent.ShowText(Component.literal("Removed 1,408 items"));
   var click=new ClickEvent.RunCommand("/sacks");
   var source=Component.empty().append(Component.literal("[Sacks] ").withStyle(ChatFormatting.GOLD));
   if(mode!=1)source.append(Component.literal("+17").withStyle(s->s.withColor(ChatFormatting.GREEN).withHoverEvent(add).withClickEvent(click)))
      .append(Component.literal(" items").withStyle(s->s.withColor(ChatFormatting.YELLOW).withHoverEvent(add).withClickEvent(click)))
      .append(Component.literal(mode==2?",":".").withStyle(ChatFormatting.YELLOW));
   if(mode==2)source.append(" ");
   if(mode!=0)source.append(Component.literal("-1,408").withStyle(s->s.withColor(ChatFormatting.RED).withHoverEvent(remove).withClickEvent(click)))
      .append(Component.literal(" items").withStyle(s->s.withColor(ChatFormatting.YELLOW).withHoverEvent(remove).withClickEvent(click)))
      .append(Component.literal(".").withStyle(ChatFormatting.YELLOW));
   source.append(Component.literal(" (Last "+time+".)").withStyle(ChatFormatting.DARK_GRAY));
   var copy=source.copy();var out=SystemTranslationEngine.translate(source,"chat",manager);
   if(out==source)TextTranslationEngine.apply(source,SystemTranslationEngine.match(source.getString(),"chat",manager),System.out::println);
   assertTrue(out.getString().startsWith("[储物袋]"),out.getString());assertEquals(copy,source);
   assertTrue(out.getString().contains(time));
   if(mode!=1){assertEquals(add,StyledLoreTest.styleAt(out,"+17").getHoverEvent());assertEquals(click,StyledLoreTest.styleAt(out,"+17").getClickEvent());}
   if(mode!=0){assertEquals(remove,StyledLoreTest.styleAt(out,"-1,408").getHoverEvent());StyledLoreTest.color(out,"-1,408",ChatFormatting.RED);}
  }
 }
 @Test void puzzleBankKeepsNpcNamesAndDynamicAnswers() throws Exception {
  var fixture=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/test/resources/fixtures/dungeon-supplement-20261103.json"))).getAsJsonArray();
  int seen=0;
  for(var value:fixture){
   var row=value.getAsJsonObject();if(!row.getAsJsonArray("scopes").toString().contains("chat"))continue;
   String source=row.get("source").getAsString().replace("{prefix}","Benson").replace("{player}","Ramsey").replace("{count}","1,273");
   var input=Component.literal("   §e"+source);var copy=input.copy();var out=SystemTranslationEngine.translate(input,"chat",manager);
   assertNotEquals(input.getString(),out.getString(),source);
   assertFalse(out.getString().equals("   "+source),source);
   assertEquals(copy,input);if(source.contains("1,273"))assertTrue(out.getString().contains("1,273"));seen++;
  }
  assertTrue(seen>100);
  var input=Component.literal("§e[NPC] §bVirginia§f: Both of them are telling the truth. Also, §dRamsey§f has the reward in their chest!");
  var out=SystemTranslationEngine.translate(input,"chat",manager);
  assertTrue(out.getString().contains("奖励"),out.getString());
  StyledLoreTest.color(out,"Virginia",ChatFormatting.AQUA);StyledLoreTest.color(out,"Ramsey",ChatFormatting.LIGHT_PURPLE);
  String playerChat="Someone: My chest has the reward.";
  assertEquals(playerChat,SystemTranslationEngine.translate(Component.literal(playerChat),"chat",manager).getString());
 }
 @Test void archerWrappedSkillLoreAndFeedback() {
  for(String count:List.of("3","7")) {
   var shot=List.<Component>of(Component.literal("§7Shoots a volley of §a"+count+" §7Arrows that"),
     Component.literal("§7explodes on impact, dealing the same"),Component.literal("§7damage as your highest bow hit in the"),
     Component.literal("§7last minute in a §a9 §7block radius."));
   var out=join(shot);assertTrue(out.getString().contains("箭矢"),out.getString());StyledLoreTest.color(out,count,ChatFormatting.GREEN);
   var rapid=join(List.of(Component.literal("§7Shoots §a"+count+" §7Arrows per second for §a11"),
     Component.literal("§7seconds! Arrows deal §a83.5% §7of your"),Component.literal("§7highest Bow hit in the last minute.")));
   assertTrue(rapid.getString().contains("箭矢"),rapid.getString());
   for(String token:List.of(count,"11","83.5%"))StyledLoreTest.color(rapid,token,ChatFormatting.GREEN);
  }
  for(String skill:List.of("Explosive Shot","Rapid Fire")) {
   for(String line:List.of("Used "+skill+"!",skill+" is now available!")) {
    var out=SystemTranslationEngine.translate(Component.literal(line),"chat",manager);assertNotEquals(line,out.getString());
   }
  }
 }
 static Component join(List<Component> lines) {
  var out=Component.empty();LoreTranslationEngine.translate(lines,manager,"DUNGEON_STONE",(s,l)->List.of(s),false).lines().forEach(out::append);return out;
 }
 @Test void batTalismanDropUsesCanonicalMagicFind() {
  for(String number:List.of("211","98.5","1,234")) {
   var input=Component.literal("§6RARE DROP! §9Bat Talisman §b(+"+number+" ✯ Magic Find)");var copy=input.copy();
   var out=SystemTranslationEngine.translate(input,"chat",manager);
   assertTrue(out.getString().contains("稀有物品概率加成"),out.getString());
   assertTrue(out.getString().contains(manager.knownItemName("Bat Talisman")));
   StyledLoreTest.color(out,"稀有物品概率加成",ChatFormatting.AQUA);StyledLoreTest.color(out,number,ChatFormatting.AQUA);assertEquals(copy,input);
  }
 }
 @Test void puzzleFailuresKeepPlayersNpcsAndRainbowSuffix() {
  String rainbow="§4Y§ci§6k§ee§as§2!";
  for(String line:List.of("§c§lPUZZLE FAIL! §bESN17_OwO §elost Tic Tac Toe! "+rainbow,
    "§c§lPUZZLE FAIL! §bESN17_OwO §ekilled a Blaze in the wrong order! "+rainbow,
    "§c§lPUZZLE FAIL! §bESN17_OwO §ewas fooled by §dBenson§e! "+rainbow)) {
   var input=Component.literal(line);var out=SystemTranslationEngine.translate(input,"chat",manager);
   if(out==input)TextTranslationEngine.apply(input,SystemTranslationEngine.match(TranslationManager.plain(line),"chat",manager),System.out::println);
   assertTrue(out.getString().contains("糟糕"),out.getString());assertFalse(out.getString().contains("PUZZLE"),out.getString());
   StyledLoreTest.color(out,"ESN17_OwO",ChatFormatting.AQUA);
   StyledLoreTest.color(out,"糟",ChatFormatting.DARK_RED);StyledLoreTest.color(out,"糕",ChatFormatting.GREEN);
   if(line.contains("Benson"))StyledLoreTest.color(out,"Benson",ChatFormatting.LIGHT_PURPLE);
  }
  var quiz=Component.literal("§4[STATUE] Oruo the Omniscient: §bESN17_OwO §cchose the wrong answer!");
  var out=SystemTranslationEngine.translate(quiz,"chat",manager);
  assertTrue(out.getString().contains("选错"),out.getString());StyledLoreTest.color(out,"ESN17_OwO",ChatFormatting.AQUA);
 }
}
