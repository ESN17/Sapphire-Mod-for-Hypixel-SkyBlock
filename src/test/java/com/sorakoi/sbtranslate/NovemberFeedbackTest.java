package com.sorakoi.sbtranslate;
import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class NovemberFeedbackTest {
 static TranslationManager manager;
 @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
 static String chat(String s){return SystemTranslationEngine.translate(Component.literal(s),"chat",manager).getString();}
 static List<Component> lore(String title,String... lines){var menu=GuiTranslationEngine.menu(title,manager);assertNotNull(menu);return LoreTranslationEngine.translate(Arrays.stream(lines).<Component>map(Component::literal).toList(),manager,null,(s,l)->List.of(s),false,false,false,menu.paragraphs()).lines();}
 @Test void dropsKeepSlayerParenthesesAndOrdinaryShape(){
  for(String head:List.of("RARE DROP!","VERY RARE DROP!"))for(String wrap:List.of("Mana Steal I","(Mana Steal I)")){
   var source=Component.literal("§b"+head+" §f"+wrap+" §b(+259% ✯ Magic Find)");var copy=source.copy();
   var result=SystemTranslationEngine.translate(source,"chat",manager);
   assertFalse(result.getString().contains("Magic Find"));assertFalse(result.getString().contains("Mana Steal"));
   assertTrue(result.getString().contains("稀有物品概率加成"));
   assertEquals(wrap.startsWith("("),result.getString().contains("("+manager.enchantment("Mana Steal")));
   StyledLoreTest.color(result,"+259%",ChatFormatting.AQUA);assertEquals(copy,source);
  }
  assertTrue(chat("RARE DROP! Enchanted Ender Pearl (+269 ✯ Magic Find)").contains("附魔末影珍珠"));
 }
 @Test void activityComboSacksElection(){
  for(String s:List.of("You earned 1,239 Event EXP from playing SkyBlock!","Your Slayer kill gave you 160 HP healing for 10 seconds!","+5 Kill Combo +3% ✯ Magic Find","+10 Kill Combo +10 coins per kill","[Sacks] +10 items. (Last 16s.)","You cast 87 votes for Paul in the Year 524 Elections!","Paul now has 41% of votes with 140,506 votes!","Attaché Fame Rank +25 votes","Supreme Voter's Badge +12 votes","SLAYER MINIBOSS! The Voidling Radical spawned!","Bob opened a WITHER door!"))assertNotEquals(s,chat(s),s);
  assertEquals("Bob: RARE DROP! Mana Steal I",chat("Bob: RARE DROP! Mana Steal I"));
  assertTrue(chat("You cast 87 votes for Paul in the Year 524 Elections!").contains("Paul"));
 }
 @Test void magicFindComboUsesCanonicalTermAndKeepsStyles(){
  for(String value:List.of("+3%","+12.5%","+100%")) {
   var source=Component.literal("§a+5 Kill Combo §b"+value+" ✯ Magic Find");var copy=source.copy();
   var result=SystemTranslationEngine.translate(source,"chat",manager);
   assertEquals("+5 连杀 "+value+" ✯ 稀有物品概率加成",result.getString());
   StyledLoreTest.color(result,"稀有物品概率加成",ChatFormatting.AQUA);
   StyledLoreTest.color(result,value,ChatFormatting.AQUA);assertEquals(copy,source);
  }
 }
 @Test void importedMagicFindUsesCanonicalTerm() throws Exception {
  var corpus=CommunityTranslationEngine.load(Path.of("src/main/resources/defaults/translations/community-skyzh.json"));
  for(String source:List.of("Magic Find","+ +20✯ Magic Find")) {
   var entry=corpus.get("tooltip","",source);
   assertNotNull(entry,source);
   assertTrue(entry.translation().contains("稀有物品概率加成"),entry.translation());
  }
  var tab=HudTranslationEngine.translate(Component.literal("§bMagic Find: §f123"),"tablist",manager);
  assertEquals("稀有物品概率加成: 123",tab.getString());
  StyledLoreTest.color(tab,"稀有物品概率加成",ChatFormatting.AQUA);
  StyledLoreTest.color(tab,"123",ChatFormatting.WHITE);
 }
 @Test void miningAndWidgets(){
  assertEquals("山峦之心",GuiTranslationEngine.title(Component.literal("Heart of the Mountain"),manager).getString());
  assertEquals("森林之心",GuiTranslationEngine.title(Component.literal("Heart of the Forest"),manager).getString());
  for(var row:lore("Heart of the Mountain","Mining Master","Level 5/10","Powder Spent: 0/4,705,857 (0.0%)","Grants +0.5 ✧ Pristine.","=====[ UPGRADE ]=====","371,293 Glacite Powder","ENABLED","Right-click to disable!","You don't have enough Glacite","Powder!"))assertFalse(row.getString().matches(".*[A-Za-z].*"),row.getString());
  for(var row:lore("Widgets in Dwarven Mines","✔ Stats Widget","Currently: ENABLED","Shows your stats when in the","Dwarven Mines.","Left-click to disable this widget!","Right-click to change settings!"))assertFalse(row.getString().matches(".*[A-Za-z].*"),row.getString());
 }
 @Test void summonsFlaresLootshareAndSlayerProgress(){
  for(int count:List.of(1,3,12,100))assertEquals("炽天使收回了你的 "+count+" 个召唤盟友!",chat("The Seraph recalled your "+count+" summoned allies!"));
  for(String flare:List.of("SOS","Alert","Warning"))assertFalse(chat("Your previous "+flare+" Flare was removed!").contains("Flare"));
  assertEquals("你的信号弹因距离过远而消失了!",chat("Your flare disappeared because you were too far away!"));
  var source=Component.literal("§aYou have spawned your Tank Zombie soul! §d(100 Mana)");var copy=source.copy();
  var result=SystemTranslationEngine.translate(source,"chat",manager);
  assertTrue(result.getString().contains("坦克僵尸"));StyledLoreTest.color(result,"100",ChatFormatting.LIGHT_PURPLE);assertEquals(copy,source);
  for(String end:List.of("","!","."))assertEquals("战利品共享 你因协助 LYZ_Bill 获得了战利品"+end,chat("LOOT SHARE You received loot for assisting LYZ_Bill"+end));
  for(String s:List.of("SLAYER QUEST COMPLETE!","SLAYER QUEST STARTED!","Enderman Slayer LVL 8 - Next LVL in 468,712 XP!","» Slay 22,000 Combat XP worth of Endermen."))assertFalse(chat(s).matches(".*[A-Za-z].*"),chat(s));
 }
 @Test void drillDynamicAbilityAndSlayerParagraphs(){
  var lines=LoreTranslationEngine.translate(List.of(Component.literal("Ability: Mining Speed Boost RIGHT CLICK"),Component.literal("Grants +250% ⸕ Mining Speed for"),Component.literal("15s.")),manager,"TITANIUM_DRILL_4",(s,l)->List.of(s),false).lines();
  assertTrue(lines.getFirst().getString().contains("挖掘速度提升"));assertTrue(lines.getLast().getString().contains("15秒"));
  for(var row:lore("Slayer Quests","Health: 150,000,000❤","Damage: 100 per second","Deals 985/575/955 True Damage at","100%/66%/33% Boss Health respectively","every second.","Match the attunement on your","dagger to damage the boss.","DDR Apocalypse","Reward: 625 Blaze Slayer XP","You already have a Slayer Quest active!","Cancel it first!"))assertFalse(row.getString().matches(".*[A-Za-z].*"),row.getString());
 }
}
