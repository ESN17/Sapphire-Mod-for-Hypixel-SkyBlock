package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LiveFeedbackTest {
    static TranslationManager manager;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;}
    @Test void scoreboardScreenshotWithInvisibleTeamFillers() {
        Map<String,String> rows=Map.of("§fSummer 30th","仲夏30日","§b⏣ Village","⏣ 村庄",
            "§fPurse: §622,657,461","钱袋: 22,657,461","§eTalk to the Bartender","与酒保交谈");
        for(var row:rows.entrySet())for(String filler:List.of("","⚽","\u200b","\u2060")) {
            var raw=Component.empty().append(Component.literal(row.getKey())).append(Component.literal(filler));var copy=raw.copy();
            var out=HudTranslationEngine.translate(raw,"scoreboard",manager);
            assertEquals(row.getValue(),out.getString(),row.getKey()+" / "+(int)(filler.isEmpty()?0:filler.charAt(0)));assertEquals(copy,raw);
        }
        var place=HudTranslationEngine.translate(Component.literal("§b⏣ Vil⚽lage"),"scoreboard",manager);
        assertEquals("⏣ 村庄",place.getString());StyledLoreTest.color(place,"村庄",ChatFormatting.AQUA);
        for(String text:List.of("6:30am","www.hypixel.net","Player Village","SKYBLOCK")) {
            var raw=Component.literal(text);assertSame(raw,HudTranslationEngine.translate(raw,"scoreboard",manager));
        }
    }
    @Test void tabScreenshotDynamicValuesAndIcons() {
        var rows=new LinkedHashMap<String,String>();
        rows.put("§fSB Level: §8[§99§8] §37/100 XP","空岛等级: [9] 7/100 经验");
        rows.put("§fInterest: §e10 Hours §6(250k)","利息: 10 小时后发放 (250k)");
        rows.put("§eElection: §cOver!","选举: 已结束!");
        rows.put("§fMayor: §aJerry","市长: Jerry");rows.put("Year: 521","年份: 521");
        rows.put("§fEnds In: §e12d","距结束: 12天");
        rows.put("§7[Lvl 12] §9Enderman","[12级] 末影人");
        rows.put("§e646.1/800 XP §6(80.8%)","646.1/800 经验 (80.8%)");
        rows.put("§fFarming 9: §a80.8%","耕作 9: 80.8%");
        rows.put("§fMining 14: §a58.6%","采矿 14: 58.6%");
        rows.put("§fSpeed: §f✦167","速度: ✦167");
        rows.put("§fCrit Chance: §9☣55","暴击率: ☣55");
        rows.put("§fAttack Speed: §e⚔6","攻击速度: ⚔6");
        rows.put("§eCoins Allowance V §f20 Days","硬币津贴 V 剩余 20 天");
        rows.put("§eAccessory Bag V §f9 Days","饰品袋 V 剩余 9 天");
        for(var row:rows.entrySet()) {
            var raw=Component.literal("  "+row.getKey()+"  ");var out=HudTranslationEngine.translate(raw,"tablist",manager);
            assertEquals("  "+row.getValue()+"  ",out.getString(),row.getKey());
        }
        assertEquals("活动: 惊魂万圣节",HudTranslationEngine.translate(Component.literal("§eEvent: §5The Great Spook"),"tablist",manager).getString());
        for(String text:List.of("[575] PlayerVillage","[MVP+] Speed","SomePlayer")) {
            var raw=Component.literal(text);assertSame(raw,HudTranslationEngine.translate(raw,"tablist",manager));
        }
        assertEquals("档案: Peach",HudTranslationEngine.translate(Component.literal("Profile: Peach"),"tablist",manager).getString());
    }
    @Test void scoreboardResolvesServerComponentsBeforeMatchingLocationAndDates() {
        var click=new ClickEvent.RunCommand("/unchanged");
        for(String location:List.of("Village","village","VILLAGE")) {
            var raw=Component.literal("⏣ ").withStyle(ChatFormatting.AQUA)
                .append(Component.translatableWithFallback("sapphire.test.location",location).withStyle(s->s.withColor(ChatFormatting.AQUA).withClickEvent(click)));
            var before=raw.copy();var result=HudTranslationEngine.translate(raw,"scoreboard",manager);
            assertEquals("⏣ 村庄",result.getString());assertEquals(before,raw);
            StyledLoreTest.color(result,"村庄",ChatFormatting.AQUA);
            assertEquals(click,StyledLoreTest.styleAt(result,"村庄").getClickEvent());
        }
        var date=Component.translatableWithFallback("sapphire.test.date","Late Summer %s",Component.literal("1st").withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.WHITE);
        assertEquals("暮夏1日",HudTranslationEngine.translate(date,"scoreboard",manager).getString());
        var objective=Component.translatableWithFallback("sapphire.test.objective","Talk to the Bartender").withStyle(ChatFormatting.YELLOW);
        assertEquals("与酒保交谈",HudTranslationEngine.translate(objective,"scoreboard",manager).getString());
        var time=Component.translatableWithFallback("sapphire.test.time","3:20am");
        assertSame(time,HudTranslationEngine.translate(time,"scoreboard",manager));
    }
    @Test void liveSidebarHiddenLegacyOwnerMarkersDoNotBlockMatching() {
        // Captured from the running client's actual scoreboard team components.
        var live=Component.empty().withStyle(ChatFormatting.WHITE)
            .append(Component.literal(" Late Summer 2nd").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("§v")).append(Component.empty());
        assertEquals(" 暮夏2日",HudTranslationEngine.translate(live,"scoreboard",manager).getString());
        for(String marker:List.of("§v","§z","§x","§q","§")) {
            var place=Component.literal("§7⏣ §eBazaar Alley"+marker);
            var copy=place.copy();var out=HudTranslationEngine.translate(place,"scoreboard",manager);
            assertEquals("⏣ 集市巷",out.getString());assertEquals(copy,place);
            StyledLoreTest.color(out,"集市巷",ChatFormatting.YELLOW);StyledLoreTest.color(out,"⏣",ChatFormatting.GRAY);
            var quest=Component.literal("§eTalk to the Bartender"+marker);
            assertEquals("与酒保交谈",HudTranslationEngine.translate(quest,"scoreboard",manager).getString());
        }
        var split=Component.literal("§bVil§vlage");
        assertEquals("村庄",HudTranslationEngine.translate(split,"scoreboard",manager).getString());
    }
    @Test void footerLineCanCrossStyledComponentBoundaries() {
        var raw=Component.literal("Active Effects\n").withStyle(ChatFormatting.GREEN)
            .append(Component.literal("No effects active. Drink Potions or splash them on the\nground to buff yourself!\n").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("Cookie Buff\n").withStyle(ChatFormatting.LIGHT_PURPLE))
            .append(Component.literal("Not active! Obtain booster cookies from the community\nshop in the hub.\nUpgrades").withStyle(ChatFormatting.GRAY));
        var out=HudTranslationEngine.translate(raw,"tablist",manager);
        assertFalse(out.getString().matches("(?s).*[A-Za-z].*"),out.getString());
        assertEquals(raw.getString().chars().filter(c->c=='\n').count(),out.getString().chars().filter(c->c=='\n').count());
        StyledLoreTest.color(out,"饼干增益",ChatFormatting.LIGHT_PURPLE);
    }
    @Test void auctionFilterOptionsTranslateAtEveryIndentAndSelectionState() {
        var menu=GuiTranslationEngine.menu("Auctions Browser",manager);assertNotNull(menu);
        for(String option:List.of("Common","Uncommon","Rare","Epic","Legendary","Mythic","Divine","And 3 more options"))
            for(String pad:List.of(""," ","  ","   ")) {
                var raw=Component.literal("§7"+pad+option);var copy=raw.copy();
                var out=GuiTranslationEngine.button(raw,menu);
                assertNotSame(raw,out,option);assertFalse(out.getString().matches(".*[A-Za-z].*"),out.getString());
                assertTrue(out.getString().startsWith(pad));assertEquals(copy,raw);
                StyledLoreTest.color(out,out.getString().strip(),ChatFormatting.GRAY);
            }
        var source=List.<Component>of(Component.literal("  Common"),Component.literal("  Rare"),Component.literal("  And 3 more options"));
        var out=LoreTranslationEngine.translate(source,manager,null,(s,o)->List.of(s),false,false,false,menu.paragraphs(),s->GuiTranslationEngine.button(s,menu));
        assertEquals(List.of("  普通","  稀有","  另有 3 个选项"),out.lines().stream().map(Component::getString).toList());
    }
    @Test void equippedSharpnessAcceptsLiveFinalPeriodAndWraps() {
        for(boolean wrap:List.of(false,true)) {
            var raw=new ArrayList<Component>();raw.add(Component.literal("§9Sharpness V"));
            if(wrap){raw.add(Component.literal("§7Increases melee damage dealt by"));raw.add(Component.literal("§a30%§7."));}
            else raw.add(Component.literal("§7Increases melee damage dealt by §a30%§7."));
            var copy=raw.stream().map(Component::copy).toList();
            var out=LoreTranslationEngine.translate(raw,manager,"ROGUE_SWORD",(s,o)->List.of(s),false);
            assertEquals("锋利 V",out.lines().getFirst().getString());
            assertEquals("增加 30% 近战伤害.",out.lines().get(1).getString());StyledLoreTest.color(out.lines().get(1),"30%",ChatFormatting.GREEN);assertEquals(copy,raw);
        }
    }
    @Test void auctionCreationAndProceedsTranslateItemButPreserveBuyer() {
        var start=Component.literal("§eBIN Auction started for §bAncestral Spade§e!");
        assertEquals("已为 "+manager.name("ANCESTRAL_SPADE","")+" 创建一口价拍卖！",SystemTranslationEngine.translate(start,"chat",manager).getString());
        var raw=Component.literal("§eYou collected §6440,000 coins §efrom selling §bAncestral Spade §eto §b[MVP+] Ponyt0 §ein an auction!");
        var copy=raw.copy();var out=SystemTranslationEngine.translate(raw,"chat",manager);
        assertEquals("你领取了拍卖 "+manager.name("ANCESTRAL_SPADE","")+" 所得的 440,000 硬币（买家：[MVP+] Ponyt0）！",out.getString());
        StyledLoreTest.color(out,"440,000",ChatFormatting.GOLD);StyledLoreTest.color(out,"Ponyt0",ChatFormatting.AQUA);assertEquals(copy,raw);
        var chat=Component.literal("[MVP+] Player: BIN Auction started for Ancestral Spade!");assertSame(chat,SystemTranslationEngine.translate(chat,"chat",manager));
    }
    @Test void bazaarClaimsTranslateTheWholeSentenceWithColoredUnits() {
        for(String unitColor:List.of("§6","§7")) {
            var raw=Component.literal("§6[Bazaar] §7Claimed §634,236,799 "+unitColor+"coins §7from selling §a176x §fSeeds §7at §6196,741 §7each!");
            for(var out:List.of(SystemTranslationEngine.translate(raw,"chat",manager),BazaarTranslationEngine.systemMessage(raw,manager))) {
                assertEquals("[集市] 已领取出售 176 个 种子 所得的 34,236,799 硬币（卖出单价 196,741 硬币）！",out.getString());
                StyledLoreTest.color(out,"个",ChatFormatting.GREEN);StyledLoreTest.color(out,"196,741",ChatFormatting.GOLD);
                StyledLoreTest.color(out,"34,236,799",ChatFormatting.GOLD);
                StyledLoreTest.color(out,"硬币",unitColor.equals("§6")?ChatFormatting.GOLD:ChatFormatting.GRAY);
            }
        }
    }
    @Test void claimedBuyItemsWithGoldTotalUnitsAndGrayQuantitySuffix() {
        for(String item:List.of("Iron Ingot","铁锭"))for(String unitColor:List.of("§6","§7")) {
            var click=new ClickEvent.RunCommand("/bz");
            var source=Component.literal("§6[Bazaar] §7Claimed §a64§7x §f"+item+" §7worth §619.2 "+unitColor+"coins §7bought for §60.3 §7each!").withStyle(s->s.withClickEvent(click));
            var copy=source.copy();
            for(var out:List.of(SystemTranslationEngine.translate(source,"chat",manager),BazaarTranslationEngine.systemMessage(source,manager))) {
                assertEquals("[集市] 已领取 64 个 铁锭，总价 19.2 硬币（买入单价 0.3 硬币）！",out.getString());
                StyledLoreTest.color(out,"64",ChatFormatting.GREEN);StyledLoreTest.color(out,"个",ChatFormatting.GRAY);
                StyledLoreTest.color(out,"铁锭",ChatFormatting.WHITE);StyledLoreTest.color(out,"硬币",unitColor.equals("§6")?ChatFormatting.GOLD:ChatFormatting.GRAY);
                StyledLoreTest.color(out,"0.3",ChatFormatting.GOLD);assertEquals(click,StyledLoreTest.styleAt(out,"铁锭").getClickEvent());
            }
            assertEquals(copy,source);
        }
    }
    @Test void cancelledBuyOrderRefundKeepsRedStatusAndGoldCoins() {
        for(String type:List.of("Buy Order","buy order"))for(String coins:List.of("1,701.8","19.2","0.3")) {
            var raw=Component.literal("§6[Bazaar] §cCancelled! §7Refunded §6"+coins+" coins §7from cancelling "+type+"!");
            var copy=raw.copy();var out=BazaarTranslationEngine.systemMessage(raw,manager);
            assertEquals("[集市] 已取消买单! 已退回 "+coins+" 硬币!",out.getString());
            StyledLoreTest.color(out,"已取消买单!",ChatFormatting.RED);StyledLoreTest.color(out,"已退回",ChatFormatting.GRAY);
            StyledLoreTest.color(out,coins,ChatFormatting.GOLD);StyledLoreTest.color(out,"硬币",ChatFormatting.GOLD);assertEquals(copy,raw);
        }
    }
}
