package com.sorakoi.sbtranslate.client;
import com.sorakoi.sbtranslate.Sapphire;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import java.util.List;

/** Runs only in the explicitly requested development smoke launch, never in normal gameplay. */
public final class ClientSmokeTest {
    private static int previewL() { return com.sorakoi.sbtranslate.compat.SkyCoflCompat.installed() ? com.mojang.blaze3d.platform.InputConstants.KEY_LALT : com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL; }
    private static int previewR() { return com.sorakoi.sbtranslate.compat.SkyCoflCompat.installed() ? com.mojang.blaze3d.platform.InputConstants.KEY_RALT : com.mojang.blaze3d.platform.InputConstants.KEY_RCONTROL; }
    public static void run() {
        checkModLanguages();
        com.sorakoi.sbtranslate.compat.SkyHanniSmokeTest.run();
        com.sorakoi.sbtranslate.compat.OdinSmokeTest.run();
        require(Sapphire.TRANSLATIONS.itemCount()==Sapphire.NEU.size() && Sapphire.TRANSLATIONS.itemCount()>8000,"full bundled dictionary installation");
        Sapphire.CONFIG.forceTranslation=true;
        ItemStack stack=new ItemStack(net.minecraft.core.Holder.direct(Items.DIAMOND_SWORD)); CompoundTag extra=new CompoundTag();extra.putString("id","ASPECT_OF_THE_END");
        CompoundTag custom=new CompoundTag();custom.put("ExtraAttributes",extra);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("§9Heroic Aspect of the End §6✪"));
        stack.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("§7Damage: §c+100"),Component.literal("Untranslated sentinel"))));
        var original=stack.copy();var tooltip=displayTooltip(stack);
        require(tooltip.getFirst().getString().contains("末影之刃"),"tooltip name");
        require(tooltip.getFirst().getString().contains("英雄的末影之刃"),"reforge prefix");
        require(tooltip.stream().anyMatch(c->c.getString().replace(" ","").equals("伤害:+100")),"tooltip lore: "+tooltip.stream().map(Component::getString).toList());
        require(tooltip.stream().anyMatch(c->c.getString().equals("Untranslated sentinel")),"fallback");
        require(ItemStack.isSameItemSameComponents(original,stack),"server-owned components unchanged");
        require(stack.getHoverName().getString().contains("Aspect of the End"),"raw item name unchanged");
        com.sorakoi.sbtranslate.compat.TooltipCompatSmoke.run(stack);
        checkControlPreview(stack);
        checkTooltipCache(stack);
        checkNameplates();
        var rankRoster=new com.sorakoi.sbtranslate.rank.RankRoster(java.util.Map.of("aaa",new com.sorakoi.sbtranslate.rank.RankRoster.Entry("aaa","SAPPHIRE",0x0000aa,0xffffff)));
        var rankMessage=Component.literal("[MVP+] aaa: hello [VIP] bbb");var rankOriginal=rankMessage.copy();
        require(com.sorakoi.sbtranslate.rank.RankDisplay.chat(rankMessage,rankRoster).getString().equals("[SAPPHIRE] aaa: hello [VIP] bbb"),"rank sender display");
        require(rankMessage.equals(rankOriginal),"rank source component immutability");
        checkTabRows();
        HudOverlaySmokeTest.run();
        checkChatBoundary();
        checkChatHistoryRestore();
        ChatBurstSmokeTest.run();
        checkOnlineChatButtons();
        ChatClickSmokeTest.run();
        net.minecraft.client.Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal("Sapphire rank Mixin smoke check"));
        checkEnchantments(stack);
        checkUpgradedTooltips();
        checkStandaloneRunes();
        checkGuiTooltips();
        var wikiButton = new java.util.ArrayList<Component>(List.of(Component.literal("Crop Guide")));
        com.sorakoi.sbtranslate.item.ItemTranslationService.tooltip(new ItemStack(net.minecraft.core.Holder.direct(Items.WHEAT_SEEDS)),wikiButton,key->false,"Carpenter");
        require(wikiButton.getFirst().getString().equals("作物指南"),"Wiki menu-scoped button");
        Sapphire.LOGGER.info("SAPPHIRE_WIKI_UI_BUTTON_OK");
        checkMenuCapture();
        checkLiveFeedback();
        for (int distance=8;distance<=12;distance++) {
            stack.set(DataComponents.LORE,new ItemLore(List.of(
                Component.literal("§6Ability: Instant Transmission  §e§lRIGHT CLICK"),
                Component.literal("§7Teleport §a"+distance+" blocks§7 ahead of you and"),
                Component.literal("§7gain §a+50 §f Speed§7 for §a3 seconds§7."),
                Component.literal("§8Mana Cost: §b25"))));
            var snapshot=stack.copy();
            var translated=displayTooltip(stack);
            String text=translated.stream().map(Component::getString).reduce("",String::concat).replace(" ","");
            require(text.contains("向前传送"+distance+"格,并获得+50速度,持续3秒."),"dynamic paragraph and font wrapping");
            require(text.contains("魔力消耗:25"),"dynamic mana");
            require(text.contains("技能:瞬间传送右键点击"),"ability heading");
            require(ItemStack.isSameItemSameComponents(snapshot,stack),"paragraph data unchanged");
            for (var line : translated) line.visit((style,part)->{
                if (part.contains("右键点击")) require(style.isBold() && style.getColor().getValue()==0xffff55,"right-click style");
                if (part.contains("格") || part.equals("秒")) require(style.getColor().getValue()==0x55ff55,"translated unit style after wrapping");
                return java.util.Optional.empty();
            },net.minecraft.network.chat.Style.EMPTY);
        }
        ItemStack pet=new ItemStack(net.minecraft.core.Holder.direct(Items.PLAYER_HEAD));
        CompoundTag petExtra=new CompoundTag();petExtra.putString("id","PET");
        petExtra.putString("petInfo","{\"type\":\"ENDERMAN\",\"tier\":\"LEGENDARY\"}");
        CompoundTag petData=new CompoundTag();petData.put("ExtraAttributes",petExtra);
        pet.set(DataComponents.CUSTOM_DATA,CustomData.of(petData));
        pet.set(DataComponents.CUSTOM_NAME,Component.literal("§7[Lvl 100] §6Enderman"));
        pet.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("§7Crit Damage: §9+75%"))));
        var petSnapshot=pet.copy();var petTooltip=displayTooltip(pet);
        require(petTooltip.getFirst().getString().equals("[等级 100] 末影人"),"pet variant identity and level template");
        require(petTooltip.stream().anyMatch(c->c.getString().equals("暴击伤害: +75%")),"pet numeric stats: "+petTooltip.stream().map(Component::getString).toList());
        for (var line : petTooltip) line.visit((style,part)->{
            if (part.contains("75")) require(style.getColor()!=null && style.getColor().getValue()==0x5555ff,"pet stat numeric color");
            return java.util.Optional.empty();
        },net.minecraft.network.chat.Style.EMPTY);
        require(ItemStack.isSameItemSameComponents(petSnapshot,pet),"pet data unchanged");
        ItemStack ball=new ItemStack(net.minecraft.core.Holder.direct(Items.PLAYER_HEAD));
        CompoundTag ballExtra=new CompoundTag();ballExtra.putString("id","MAGIC_8_BALL");
        CompoundTag ballData=new CompoundTag();ballData.put("ExtraAttributes",ballExtra);
        ball.set(DataComponents.CUSTOM_DATA,CustomData.of(ballData));
        ball.set(DataComponents.CUSTOM_NAME,Component.literal("§5Magic 8 Ball"));
        ball.set(DataComponents.LORE,new ItemLore(List.of(
            Component.literal("§a✦ It is certain."),
            Component.literal("§7    §6+25 Farming Fortune"),
            Component.literal("§7    §3+1☯ Farming Wisdom"),
            Component.literal("§7✧ Reply hazy, try again."))));
        var ballSnapshot=ball.copy();var ballTooltip=displayTooltip(ball);
        require(ballTooltip.stream().anyMatch(c->c.getString().contains("✦ 肯定如此.")),"magic ball response");
        require(ballTooltip.stream().anyMatch(c->c.getString().contains("+25")&&c.getString().contains("农业幸运")),"magic ball separate fortune line");
        require(ballTooltip.stream().anyMatch(c->c.getString().contains("+1☯")&&c.getString().contains("农业智慧")),"magic ball separate wisdom line");
        require(ballTooltip.stream().noneMatch(c->c.getString().contains("✧")&&c.getString().contains("+1")),"magic ball response does not join previous stat");
        require(ItemStack.isSameItemSameComponents(ballSnapshot,ball),"magic ball data unchanged");
        Sapphire.CONFIG.enabled=false;
        require(displayTooltip(stack).getFirst().getString().contains("Aspect of the End"),"disabled fallback");
        Sapphire.CONFIG.enabled=true;Sapphire.CONFIG.forceTranslation=false;
    }
    private static void checkMenuCapture() {
        var capture=new com.sorakoi.sbtranslate.config.SapphireConfig();
        capture.collectChatCapture=false; capture.collectUiCapture=true; capture.privacyAccepted=true;
        capture.chatCaptureUploadUrl=""; capture.uploadNotifications=false;
        var previous=Sapphire.CONFIG.showOriginal;
        try {
            var directory=java.nio.file.Files.createTempDirectory("sapphire-ui-smoke");
            capture.chatCaptureDirectory=directory.toString();
            com.sorakoi.sbtranslate.collection.ChatCapture.configure(capture);
            com.sorakoi.sbtranslate.collection.ChatCapture.startSession("UiSmoke");
            Sapphire.CONFIG.showOriginal=true;
            var stack=new ItemStack(net.minecraft.core.Holder.direct(Items.PAPER));
            for(int i=0;i<2;i++) {
                var lines=new java.util.ArrayList<Component>(List.of(Component.literal("Crop Guide"),Component.literal("§aSample missing menu line"),Component.literal("已经汉化")));
                com.sorakoi.sbtranslate.item.ItemTranslationService.tooltip(stack,lines,key->false,"Carpenter");
                require(lines.getFirst().getString().contains("作物指南"),"capture preserves the rendered translation");
            }
            Sapphire.CONFIG.showOriginal=false;
            var unknown=new java.util.ArrayList<Component>(List.of(Component.literal("Luverne"),Component.literal("§aUnseen merchant instruction"),Component.literal("Playstyle: 100% SPLIT")));
            com.sorakoi.sbtranslate.item.ItemTranslationService.tooltip(stack,unknown,key->false,"",true);
            require(unknown.getFirst().getString().contains("卢韦恩"),"unknown public menu button");
            com.sorakoi.sbtranslate.collection.ChatCapture.stop();
            try(var files=java.nio.file.Files.list(directory)) {
                var file=files.findFirst().orElseThrow();
                require(java.nio.file.Files.readAllLines(file).equals(List.of("[Sapphire:UI:gui-tooltip] §aSample missing menu line","[Sapphire:UI:gui-tooltip] §aUnseen merchant instruction")),"only the untranslated styled lore is queued once");
                java.nio.file.Files.delete(file);
            }
            java.nio.file.Files.delete(directory);
            Sapphire.LOGGER.info("SAPPHIRE_UI_CAPTURE_OK");
        } catch(java.io.IOException e){throw new IllegalStateException(e);}
        finally {
            com.sorakoi.sbtranslate.collection.ChatCapture.stop();
            com.sorakoi.sbtranslate.collection.ChatCapture.configure(Sapphire.CONFIG);
            Sapphire.CONFIG.showOriginal=previous;
        }
    }
    private static void checkModLanguages() {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sbo")) return;
        try {
            Class<?> screen = Class.forName("net.sbo.mod.config.ConfigGuiKt");
            if (!net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected().equals("zh_cn")) return;
            var label = java.util.Arrays.stream(screen.getDeclaredMethods())
                    .filter(m -> m.getName().endsWith("sapphire$label")).findFirst().orElseThrow();
            label.setAccessible(true);
            require("清除搜索".equals(label.invoke(null,"Clear the search")), "SBO transformed Chinese UI label");
            require("任意 X".equals(Component.translatable("jei.tooltip.recipe.any","X").getString()), "JEI Chinese argument");
            require(Component.translatable("text.rei.recipes.not.full.desc", "X").getString().contains("运行 X"), "REI Chinese argument");
            Sapphire.LOGGER.info("SAPPHIRE_MOD_LANGUAGES_OK");
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static void checkTabRows() {
        var client=net.minecraft.client.Minecraft.getInstance();
        var overlay=new net.minecraft.client.gui.components.PlayerTabOverlay(client,client.gui.hud);
        var rows=java.util.Map.of(
            "§aPlayers §f(21)","玩家 (21)",
            "§bInfo","信息",
            "§bArea: §7Hub","区域: 中心岛",
            "§fSB Level: §7[9] §b12/100 XP","空岛等级: [9] 12/100 经验",
            "§eProfile: §aPeach","档案: Peach",
            "§fFarming 9: §a80.8%","耕作 9: 80.8%",
            "§eEvent: §5The Great Spook","活动: 惊魂万圣节");
        for(var row:rows.entrySet()) {
            // Deliberately let the display text equal the fake profile name for short info rows.
            String profile=com.sorakoi.sbtranslate.translation.TranslationManager.plain(row.getKey());
            if(profile.length()>16)profile="!tab-info";
            var info=new net.minecraft.client.multiplayer.PlayerInfo(new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),profile),false);
            var original=Component.literal(row.getKey());info.setTabListDisplayName(original);
            var raw=overlay.getNameForDisplay(info);
            require(raw.getString().equals(original.getString()),"Tab parsing API remains English");
            Component result;
            try {
                var method=java.util.Arrays.stream(overlay.getClass().getDeclaredMethods()).filter(m->m.getName().endsWith("sapphire$row")).findFirst().orElseThrow();
                method.setAccessible(true);result=(Component)method.invoke(overlay,raw,info);
            }catch(Exception e){throw new IllegalStateException("Actual Tab render hook missing",e);}
            require(result.getString().equals(row.getValue()),"26.3 actual Tab hook: "+row.getKey()+" => "+result.getString());
            require(info.getTabListDisplayName()==original,"Tab server-owned component unchanged");
        }
    }
    private static void checkEnchantments(ItemStack weapon) {
        var originalLore=weapon.get(DataComponents.LORE);
        try {
            weapon.set(DataComponents.LORE,new ItemLore(List.of(
                Component.literal("§d§lUltimate Wise V§9, Champion §6X§9, First Strike IV, Looting V"))));
            var before=weapon.copy();
            var lines=displayTooltip(weapon);
            require(lines.stream().anyMatch(c->c.getString().equals("终极智慧 V, 近战战斗专精 X, 一次打击 IV, 掠夺 V")),"gear enchantment list");
            require(ItemStack.isSameItemSameComponents(before,weapon),"enchantments never mutate gear");
            checkControlPreview(weapon);
        } finally { weapon.set(DataComponents.LORE,originalLore); }

        for(boolean combined:List.of(false,true)) {
            ItemStack book=new ItemStack(net.minecraft.core.Holder.direct(Items.ENCHANTED_BOOK));
            CompoundTag extra=new CompoundTag();extra.putString("id","ENCHANTED_BOOK");
            CompoundTag enchants=new CompoundTag();enchants.putInt("sharpness",7);
            if(combined)enchants.putInt("looting",5);
            extra.put("enchantments",enchants);CompoundTag custom=new CompoundTag();custom.put("ExtraAttributes",extra);
            book.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));
            book.set(DataComponents.CUSTOM_NAME,Component.literal("§5Enchanted Book"));
            book.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("§9Sharpness VII"),
                Component.literal("§7Increases melee damage dealt by"),Component.literal("§a65%"),
                Component.literal(""),Component.literal("§9Looting V"))));
            var before=book.copy();var lines=displayTooltip(book);
            require(lines.getFirst().getString().equals("附魔书"),"single and combined book name");
            require(lines.stream().anyMatch(c->c.getString().equals("锋利 VII")),"book enchantment title");
            require(lines.stream().anyMatch(c->c.getString().equals("掠夺 V")),"combined book second enchantment");
            String text=lines.stream().map(Component::getString).reduce("",String::concat).replace(" ","");
            require(text.contains("增加65%近战伤害"),"book description stays translated after title separation");
            for(var line:lines)line.visit((style,part)->{
                if(part.contains("锋利"))require(style.getColor()!=null && style.getColor().getValue()==0x5555ff,"book title color");
                if(part.contains("65%"))require(style.getColor()!=null && style.getColor().getValue()==0x55ff55,"book live number color");
                return java.util.Optional.empty();
            },net.minecraft.network.chat.Style.EMPTY);
            require(ItemStack.isSameItemSameComponents(before,book),"book NBT and components unchanged");
            boolean items=Sapphire.CONFIG.items;
            try {
                Sapphire.CONFIG.items=false;
                var english=displayTooltip(book);
                Sapphire.CONFIG.items=items;
                var controlled=new java.util.ArrayList<>(english);
                com.sorakoi.sbtranslate.item.ItemTranslationService.tooltip(book,controlled,key->true);
                require(controlled.equals(english),"book Ctrl English preview");
            } finally {Sapphire.CONFIG.items=items;}
        }
    }
    private static void checkTooltipCache(ItemStack stack) {
        var service=com.sorakoi.sbtranslate.item.ItemTranslationService.DISPLAY_CACHE;
        Sapphire.CONFIG.items=false;var raw=displayTooltip(stack);Sapphire.CONFIG.items=true;
        var expected=new java.util.ArrayList<>(raw);
        service.clear();long start=System.nanoTime();
        com.sorakoi.sbtranslate.item.ItemTranslationService.tooltip(stack,expected,key->false);
        long cold=System.nanoTime()-start,hits=service.hits();start=System.nanoTime();
        for(int i=0;i<500;i++) {
            var lines=new java.util.ArrayList<>(raw);com.sorakoi.sbtranslate.item.ItemTranslationService.tooltip(stack,lines,key->false);
            require(lines.equals(expected),"cache preserves full component tree, styles and contents");
        }
        long warm=System.nanoTime()-start;require(service.hits()-hits==500,"unchanged tooltips reuse translation cache");
        service.clear();var changed=new java.util.ArrayList<>(raw);com.sorakoi.sbtranslate.item.ItemTranslationService.tooltip(stack,changed,key->false);require(changed.equals(expected),"resource reload rebuild matches cached tooltip");
        Sapphire.LOGGER.info("SAPPHIRE_TOOLTIP_CACHE: cold {} us; warm average {} us over 500 frames; 500 cache hits",cold/1000,warm/500/1000);
    }
    private static void checkNameplates() {
        var source=Component.literal("§8[§7Lv33§8] §cCrypt Ghoul §a2,000§c❤");
        var snapshot=source.copy();EntityNameDisplay.clear();long start=System.nanoTime();
        var first=EntityNameDisplay.translate(source);long cold=System.nanoTime()-start;
        require(first.getString().equals("[Lv33] 地穴食尸鬼 2,000❤"),"mob nameplate translation");
        long hits=EntityNameDisplay.CACHE.hits();
        for(int i=0;i<1000;i++)EntityNameDisplay.translate(source);
        start=System.nanoTime();
        for(int i=0;i<10000;i++)EntityNameDisplay.translate(source);
        long warm=System.nanoTime()-start;
        require(EntityNameDisplay.CACHE.hits()-hits==11000,"nameplate cache hits");
        require(source.equals(snapshot),"server-owned name remains original");
        var font=net.minecraft.client.Minecraft.getInstance().font;
        var text=Component.literal("Sea Walker\nCLICK");
        var state=new net.minecraft.world.entity.Display.TextDisplay.TextRenderState(text,200,t->255,t->0,(byte)0);
        var original=new net.minecraft.world.entity.Display.TextDisplay.CachedInfo(List.of(),0);
        var layout=EntityNameDisplay.layout(state,original,font);
        require(layout!=original && layout.lines().size()==2,"text display translated layout");
        require(EntityNameDisplay.layout(state,original,font)==layout,"text display layout cache hit");
        require(text.getString().equals("Sea Walker\nCLICK"),"server-owned text display remains original");
        Sapphire.CONFIG.entities=false;
        require(EntityNameDisplay.translate(source)==source && EntityNameDisplay.layout(state,original,font)==original,"disabled names restore originals immediately");
        Sapphire.CONFIG.entities=true;EntityNameDisplay.clear();
        require(EntityNameDisplay.layout(state,original,font)!=layout,"resource reload invalidates layout");
        Sapphire.LOGGER.info("SAPPHIRE_NAMEPLATE_CACHE: cold {} us; warm average {} ns over 10000 calls; 11000 cache hits",cold/1000,warm/10000);
    }
    private static void checkUpgradedTooltips() {
        record Case(String id,String name,List<String> lore,List<String> expected) {}
        var cases=List.of(
            new Case("DIVAN_DRILL","§bHeated Divan's Drill",List.of(
                "§7Pristine: §a+2 §b(+2)","§7Block Fortune: §6+65 §b(+65)","",
                "§9Compact VIII §8239,157","","§aTitanium Drill Engine","§aSunny Side Goblin Egg Part","",
                "§9Fleet Bonus","§7Grants §6+50\ue053 Mining Spread §7towards","§bBlocks§7.","",
                "§9Fortune III","§7Grants §6+30\ue053 Mining Fortune§7, which","§7increases your chance for multiple","§7drops."),
                List.of("提纯:+2(+2)","方块时运:+65(+65)","采矿专精VIII239,157","镀钛钻头引擎","单面煎哥布林煎蛋卷配件",
                    "挖掘方块时提供+50\ue053采矿扩散.","提供+30\ue053挖掘时运,提高获得多份掉落物的概率.")),
            new Case("WISE_WITHER_HELMET","§dShiny Ancient Storm's Helmet §6✪✪✪✪✪§c➎",List.of(
                "§d§ka§r §d§lSHINY MYTHIC DUNGEON HELMET §d§ka"),List.of("闪耀远古的风暴头盔","闪耀神话地牢头盔")),
            new Case("SKELETON_MASTER_CHESTPLATE","§dAncient Skeleton Master Chestplate",List.of(
                "§aThis item is Undead \ue084!","§aThis item is Arachnal \ue074!"),List.of("此物品属于亡灵类\ue084!","此物品属于蛛类\ue074!")),
            new Case("DIVAN_DRILL","§bHeated Divan's Drill",List.of(
                "§aPerfectly-Cut Fuel Tank","§a100,000 Max Fuel Capacity","§a-10% Pickaxe Ability Cooldown","",
                "§aAmber-Polished Drill Engine","§6+600 Mining Speed","§6+100 Mining Fortune","",
                "§aBlue Cheese Goblin Omelette Part","§7Adds §a+1 Level §7to all of your unlocked §5Heart of","§5the Mountain §7perks."),
                List.of("完美切割燃料罐","100,000最大燃料容量","-10%镐子技能冷却","琥珀抛光钻头引擎","+600挖掘速度","+100挖掘时运","蓝纹奶酪哥布林煎蛋卷配件","山峰之心技能增加+1等级")),
            new Case("HYPERION","§dHeroic Hyperion §6✪✪✪✪✪",List.of("§aScroll Abilities:",
                "§6Ability: Wither Impact  §e§lRIGHT CLICK","§7Teleport §a10 blocks §7ahead of you",
                "§7dealing §c13,208 §7damage to nearby","§7enemies. Also reduces your damage",
                "§7taken and grants an absorption","§7shield for §e5 seconds§7.","§8Mana Cost: §b300"),
                List.of("英雄的海伯利安神剑","卷轴技能:","技能:凋零冲击右键点击","向前传送10格","13,208伤害","伤害吸收护盾","5秒")),
            new Case("STARRED_MIDAS_SWORD","§d\ue068 Gilded Midas' Sword §6✪✪✪✪✪§c➎",List.of(
                "§7Damage: §c+557.5 §e(+30) §b(+90) §8(+828.8)",
                "§7True Defence: §f+15 §8(+5)",
                "§cSome of your enchantments require","§ca higher Enchanting level!","",
                "§9Byron's Compassion §8(Gilded)","§7Killing a mob has a rare chance to","§7grant §6Coins §7to a nearby player.","",
                "§c❣ Requires Catacombs Skill 17 while in The Catacombs.","§d§ka§r §d§lMYTHIC DUNGEON SWORD §d§ka"),
                List.of("鎏金的米达斯之剑","伤害:+557.5(+30)(+90)(+828.8)","真实防御:+15(+5)","你的部分附魔需要更高的附魔等级!","Byron的怜悯(鎏金)","有小概率赠送硬币","在地牢中需要地牢技能17.","神话地牢剑")),
            new Case("STARRED_SHADOW_ASSASSIN_CHESTPLATE","§c✿ §d\ue068 Ancient Shadow Assassin Chestplate §6✪✪✪✪✪",List.of(
                "§7Health: §c+412 §e(+40) §b(+7) §8(+619.2)","",
                "§9Ancient Bonus","§7Grants §b+1\ue007 Crit Damage §7per","§cCatacombs §7skill level you have.","",
                "§c✿ Red Tulip Dyed","§c◆ Blood Rune §6III","§d§ka§r §d§lMYTHIC DUNGEON CHESTPLATE §d§ka"),
                List.of("远古的暗影刺客胸甲","生命值:+412(+40)(+7)(+619.2)","远古的加成","每级地牢技能等级提供+1\ue007暴击伤害.","✿红色郁金香染色","◆血符文III","神话地牢胸甲")),
            new Case("UNDEAD_SWORD","§fSharp Undead Sword",List.of("§9Smite V","§7Increases damage dealt to §2\ue084",
                "§2Undead§7, §f\ue085 Skeletal §7and §8\ue086 Wither","§7mobs by §a30%§7.","","§8* Co-op Soulbound *"),
                List.of("亡灵剑","亡灵杀手V","对\ue084亡灵,\ue085骷髅和\ue086凋灵生物的伤害提高30%.","*合作档灵魂绑定*")));
        for(var sample:cases) {
            ItemStack item=new ItemStack(net.minecraft.core.Holder.direct(Items.DIAMOND_SWORD));
            CompoundTag extra=new CompoundTag();extra.putString("id",sample.id());CompoundTag custom=new CompoundTag();custom.put("ExtraAttributes",extra);
            item.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));item.set(DataComponents.CUSTOM_NAME,Component.literal(sample.name()));
            item.set(DataComponents.LORE,new ItemLore(sample.lore().stream().<Component>map(Component::literal).toList()));
            var before=item.copy();var lines=displayTooltip(item);
            String translated=lines.stream().map(Component::getString).reduce("",String::concat).replace(" ","");
            for(String expected:sample.expected())require(translated.contains(expected),"upgraded tooltip "+sample.id()+" missing "+expected+": "+translated);
            require(ItemStack.isSameItemSameComponents(before,item),"upgraded tooltip preserves original components");
        }
    }
    private static void checkStandaloneRunes() {
        for(String rawId:List.of("RUNE","UNIQUE_RUNE","CLOUDS_RUNE;1")) {
            ItemStack rune=new ItemStack(net.minecraft.core.Holder.direct(Items.PLAYER_HEAD));
            var extra=new CompoundTag();extra.putString("id",rawId);var runes=new CompoundTag();runes.putInt("CLOUDS",1);extra.put("runes",runes);
            var custom=new CompoundTag();custom.put("ExtraAttributes",extra);rune.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));
            rune.set(DataComponents.CUSTOM_NAME,Component.literal("§f◆ Clouds Rune I"));
            rune.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("§8Requires level 16"),Component.literal("§8Boots"),Component.empty(),
                Component.literal("§7Feels like you're walking in the"),Component.literal("§7clouds!"),Component.empty(),
                Component.literal("§7Apply this rune to boots or fuse two"),Component.literal("§7together at the Runic Pedestal!"))));
            var before=rune.copy();var lines=displayTooltip(rune);
            String text=lines.stream().map(Component::getString).reduce("",String::concat).replace(" ","");
            for(String expected:List.of("◆云朵符文I","需要等级16靴子","感觉就像在云端漫步!","符文基座"))require(text.contains(expected),"rune "+rawId+" missing "+expected+": "+text);
            require(ItemStack.isSameItemSameComponents(before,rune),"rune original components preserved");
            checkControlPreview(rune);
        }
    }
    private static void checkGuiTooltips() {
        boolean items=Sapphire.CONFIG.items,gui=Sapphire.CONFIG.gui,lore=Sapphire.CONFIG.lore,bilingual=Sapphire.CONFIG.showOriginal;
        try {
            Sapphire.CONFIG.items=false;Sapphire.CONFIG.lore=false;Sapphire.CONFIG.gui=true;Sapphire.CONFIG.showOriginal=false;
            record Case(String title,String name,List<String> lore,List<String> expected) {}
            for(var row:List.of(
                new Case("SkyBlock Menu","§aYour Skills",List.of("§7View your Skill progression and","§7rewards.","","§60.0 Skill Avg. §8(non-cosmetic)","§8Also accessible via /skills"),List.of("技能","查看你的技能进度与奖励.","技能平均等级0.0(不含装饰技能)","/skills")),
                new Case("Auction View","§aSubmit Bid",List.of("§7Seller: §b[MVP+] Player_123","§7New bid: §61,234,567.8 coins","§eClick to bid!"),List.of("提交出价","卖家:[MVP+]Player_123","新出价:1,234,567.8硬币","点击出价!")),
                new Case("Bazaar ➜ Enchanted Diamond","§aCreate Buy Order",List.of("§7Amount: §a71,680x","§7Price per unit: §6123.45 coins","§7Filled: §a35,840§7/§a71,680 §8(50%)","§eClick to create buy order!"),List.of("创建买单","数量:71,680x","单价:123.45硬币","已成交:35,840/71,680(50%)")),
                new Case("Bazaar ➜ Mining","§bRedstone",List.of("§83 products","§a▸ §7Enchanted Redstone Dust §c629 §7| §a696","§b▸ §7Enchanted Redstone Block §c24.9k §7| §a25.4k","§eClick to view products!"),List.of("红石","3种商品","附魔红石粉629|696","附魔红石块24.9k|25.4k","点击查看商品!")),
                new Case("Bazaar ➜ Mining","§aSell Inventory Now",List.of("§7Instantly sell any items in your","§7inventory that can be sold on the","§7Bazaar.","§a1x §9 Fine Sapphire Gemstone §7for §626,774 coins","§7You earn: §69,202,336 coins"),List.of("立即出售背包物品","立即出售背包中所有可在集市交易的物品.","精致蓝宝石售价26,774硬币","你将获得:9,202,336硬币")),
                new Case("Mining ➜ End Stone","§fEnd Stone",List.of("Common commodity","2.3M in 92 offers","2M insta-buys in 7d","§77d Avg. price: §69.8 coins"),List.of("末地石","常见商品","92个卖单,共2.3M个","7天内即时买入2M个","7天均价:9.8硬币")),
                new Case("Ultimate Enchantments ➜ Bobbin'","§d§lBobbin' Time V",List.of("Uncommon commodity","Buy price: 261,997,999 coins","103 in 23 offers","6 insta-buys in 7d","7d Avg. price: 261,999,998 coins","Click to view details!"),List.of("浮漂时刻V","罕见商品","买入价:261,997,999硬币","23个卖单,共103个","7天内即时买入6个","7天均价:261,999,998硬币","点击查看详情!")),
                new Case("(1/2) Garden ➜ Mutations","§aAfterglow",List.of("Uncommon commodity","Buy price: 38,489 coins","3.6k in 24 offers","19.9k insta-buys in 7d","You haven't analyzed this mutation!"),List.of("罕见商品","买入价:38,489硬币","24个卖单,共3.6k个","7天内即时买入19.9k个","你尚未分析这种变异作物!")),
                new Case("Farming ➜ Garden","§eGarden Chips",List.of("10 products","§b▸ §7Vermin Vaporizer Chip §c113k §7| §a151.3k","§b▸ §7Synthesis Chip §c136.2k §7| §a151.5k","Click to view products!"),List.of("花园芯片","10种商品","害虫汽化芯片113k|151.3k","合成芯片136.2k|151.5k")),
                new Case("Combat ➜ Essences","§dIce Essence",List.of("18M in 274 offers","3.7M in 66 orders","7d Avg. price: 822.3 coins","You must have Catacombs Skill 20!"),List.of("冰精华","274个卖单,共18M个","66个买单,共3.7M个","7天均价:822.3硬币","需要地下墓穴等级20!")),
                new Case("Sand ➜ Enchanted Sand","§aCreate Buy Order",List.of("未知地图","§8Enchanted Sand","§aTop Orders:","§7- §6500.0 coins §7each | §a26,958x §7in 1 order","§eClick to setup Buy Order!"),List.of("创建买单","未知地图","附魔沙子","最高买单:","单价500.0硬币|1个买单,共26,958x","点击设置买单!")),
                new Case("At what price are you selling?","§6Same as Best Offer",List.of("§8Sell Offer Setup","§7Use the same price as the lowest","§7Sell Offer for this item.","§7Selling: §a1x","§7Unit price: §69,141,131.8 coins","§eClick to proceed!"),List.of("与最低卖价相同","卖单设置","按此物品当前最低卖价挂单。","出售数量:1x","单价:9,141,131.8硬币","点击继续!")),
                new Case("Inferno ➜ Inferno I","§d§lInferno I",List.of("§7Every §c10§7th hit on a mob traps it for","§c5s §7and deals §c1.25x §7of that hit's","§7damage over the trap duration."),List.of("炼狱I","每第10次命中生物将其困住5s,并在此期间造成该次命中伤害的1.25x.")))) {
                ItemStack button=new ItemStack(net.minecraft.core.Holder.direct(Items.PAPER));
                button.set(DataComponents.CUSTOM_NAME,Component.literal(row.name()));
                button.set(DataComponents.LORE,new ItemLore(row.lore().stream().<Component>map(Component::literal).toList()));
                if(row.name().contains("Same as Best Offer")) {
                    CompoundTag metadata=new CompoundTag(),extra=new CompoundTag();extra.putString("id","RECOMBOBULATOR_3000");metadata.put("ExtraAttributes",extra);
                    button.set(DataComponents.CUSTOM_DATA,CustomData.of(metadata));
                }
                var before=button.copy();var raw=new java.util.ArrayList<Component>();raw.add(Component.literal(row.name()));
                row.lore().forEach(s->raw.add(Component.literal(s)));
                var translated=new java.util.ArrayList<>(raw);
                com.sorakoi.sbtranslate.item.ItemTranslationService.tooltip(button,translated,key->false,row.title());
                String text=translated.stream().map(Component::getString).reduce("",String::concat).replace(" ","");
                for(String expected:row.expected())require(text.contains(expected),"GUI "+row.title()+" missing "+expected+": "+text);
                var outside=new java.util.ArrayList<>(raw);
                com.sorakoi.sbtranslate.item.ItemTranslationService.tooltip(button,outside,key->false,"Large Chest");
                require(outside.equals(raw),"unrecognized container remains unchanged");
                var control=new java.util.ArrayList<>(raw);
                com.sorakoi.sbtranslate.item.ItemTranslationService.tooltip(button,control,key->key==previewL(),row.title());
                require(control.equals(raw),"GUI Ctrl shows complete raw English");
                Sapphire.CONFIG.gui=false;var disabled=new java.util.ArrayList<>(raw);
                com.sorakoi.sbtranslate.item.ItemTranslationService.tooltip(button,disabled,key->false,row.title());
                require(disabled.equals(raw),"GUI toggle independent from items");Sapphire.CONFIG.gui=true;
                require(ItemStack.isSameItemSameComponents(before,button),"GUI source data and click identity unchanged");
            }
            var title=com.sorakoi.sbtranslate.DisplayTranslationHooks.text(Component.literal("§aAuctions: \"Player_123 Query\""),"gui");
            require(title.getString().equals("拍卖: \"Player_123 Query\""),"GUI title preserves search query");
            boolean chat=Sapphire.CONFIG.chat;
            try {
                Sapphire.CONFIG.chat=true;Sapphire.CONFIG.gui=false;
                var message=Component.literal("§6[Bazaar] §eSell Offer Setup! §a4x §fVicious III §7for §691,359,448 coins.");
                var translated=com.sorakoi.sbtranslate.DisplayTranslationHooks.text(message,"chat");
                require(translated.getString().equals("[集市] 卖单已创建! 4x 凶残 III,总价 91,359,448 硬币."),"Bazaar chat translated independently of GUI");
                Sapphire.CONFIG.chat=false;
                require(com.sorakoi.sbtranslate.DisplayTranslationHooks.text(message,"chat")==message,"chat toggle preserves original");
            } finally {Sapphire.CONFIG.chat=chat;}
        } finally {
            Sapphire.CONFIG.items=items;Sapphire.CONFIG.gui=gui;Sapphire.CONFIG.lore=lore;Sapphire.CONFIG.showOriginal=bilingual;
        }
    }
    private static void checkControlPreview(ItemStack stack) {
        boolean items=Sapphire.CONFIG.items, bilingual=Sapphire.CONFIG.showOriginal;
        var snapshot=stack.copy();
        try {
            Sapphire.CONFIG.items=false;
            var english=displayTooltip(stack);
            Sapphire.CONFIG.items=items;
            for(boolean showOriginal:List.of(false,true)) {
                Sapphire.CONFIG.showOriginal=showOriginal;
                var normal=new java.util.ArrayList<>(english);
                com.sorakoi.sbtranslate.item.ItemTranslationService.tooltip(stack,normal,key->false);
                require(!normal.getFirst().equals(english.getFirst()),"Ctrl preview starts translated");
                require(!normal.equals(english),"Ctrl preview has a translated tooltip");
                for(int held:List.of(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT,com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT)) {
                    var shifted=new java.util.ArrayList<>(english);
                    com.sorakoi.sbtranslate.item.ItemTranslationService.tooltip(stack,shifted,key->key==held);
                    require(shifted.equals(normal),"Shift alone keeps Chinese and is available to other mods");
                }
                for(int held:List.of(previewL(),previewR())) {
                    var pressed=new java.util.ArrayList<>(english);
                    com.sorakoi.sbtranslate.item.ItemTranslationService.tooltip(stack,pressed,key->key==held);
                    require(pressed.equals(english),"Ctrl preserves complete English tooltip and styles");
                    for(int i=0;i<english.size();i++)require(pressed.get(i)==english.get(i),"Ctrl keeps original components");
                    var released=new java.util.ArrayList<>(english);
                    com.sorakoi.sbtranslate.item.ItemTranslationService.tooltip(stack,released,key->false);
                    require(released.equals(normal),"releasing either Ctrl restores normal translation");
                }
                var both=new java.util.ArrayList<>(english);
                com.sorakoi.sbtranslate.item.ItemTranslationService.tooltip(stack,both,key->true);
                require(both.equals(english),"both Ctrl keys show English");
            }
            Sapphire.CONFIG.englishByDefault=true;
            var defaultEnglish=new java.util.ArrayList<>(english);
            com.sorakoi.sbtranslate.item.ItemTranslationService.tooltip(stack,defaultEnglish,key->false);
            require(defaultEnglish.equals(english),"English default preserves original tooltip");
            for(int held:List.of(previewL(),previewR())) {
                var chinese=new java.util.ArrayList<>(english);
                com.sorakoi.sbtranslate.item.ItemTranslationService.tooltip(stack,chinese,key->key==held);
                require(!chinese.getFirst().getString().equals(english.getFirst().getString()),"English default Ctrl shows Chinese");
            }
            require(ItemStack.isSameItemSameComponents(snapshot,stack),"Ctrl preview never changes server-owned item data");
        } finally {
            Sapphire.CONFIG.items=items;Sapphire.CONFIG.showOriginal=bilingual;Sapphire.CONFIG.englishByDefault=false;
        }
    }
    private static void checkLiveFeedback() {
        var location=Component.literal("⏣ ").withStyle(net.minecraft.ChatFormatting.AQUA)
            .append(Component.translatableWithFallback("sapphire.test.location","village"));
        require(com.sorakoi.sbtranslate.DisplayTranslationHooks.text(location,"scoreboard").getString().equals("⏣ 村庄"),"resolved sidebar location");
        var date=Component.translatableWithFallback("sapphire.test.date","Late Summer %s",Component.literal("1st"));
        require(com.sorakoi.sbtranslate.DisplayTranslationHooks.text(date,"scoreboard").getString().equals("暮夏1日"),"resolved sidebar date");
        for(var pair:List.of(new String[]{"scoreboard","§b⏣ Vil⚽lage","⏣ 村庄"},
                new String[]{"scoreboard","§fSummer 30th⚽","仲夏30日"},
                new String[]{"scoreboard","§fPurse: §622,657,461⚽","钱袋: 22,657,461"},
                new String[]{"tablist","§fSB Level: §8[§99§8] §37/100 XP","空岛等级: [9] 7/100 经验"},
                new String[]{"tablist","§fInterest: §e10 Hours §6(250k)","利息: 10 小时后发放 (250k)"}))
            require(com.sorakoi.sbtranslate.DisplayTranslationHooks.text(Component.literal(pair[1]),pair[0]).getString().equals(pair[2]),"live HUD field "+pair[1]);
        ItemStack button=new ItemStack(net.minecraft.core.Holder.direct(Items.HOPPER));
        var lines=new java.util.ArrayList<Component>(List.of(Component.literal("Item Tier"),Component.literal("§7  Common"),Component.literal("§7  Rare"),Component.literal("§e  And 3 more options")));
        com.sorakoi.sbtranslate.item.ItemTranslationService.tooltip(button,lines,key->false,"Auctions Browser");
        require(lines.stream().noneMatch(c->c.getString().matches(".*[A-Za-z].*")),"unselected auction rarity options: "+lines);
        ItemStack sword=new ItemStack(net.minecraft.core.Holder.direct(Items.GOLDEN_SWORD));
        CompoundTag extra=new CompoundTag();extra.putString("id","ROGUE_SWORD");CompoundTag data=new CompoundTag();data.put("ExtraAttributes",extra);
        sword.set(DataComponents.CUSTOM_DATA,CustomData.of(data));sword.set(DataComponents.CUSTOM_NAME,Component.literal("Rogue Sword"));
        sword.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("§9Sharpness V"),Component.literal("§7Increases melee damage dealt by §a30%§7."))));
        var before=sword.copy();var tooltip=displayTooltip(sword);
        require(tooltip.stream().noneMatch(c->c.getString().contains("Increases")),"equipped Sharpness live effect");
        require(ItemStack.isSameItemSameComponents(before,sword),"live feedback item data unchanged");
    }
    private static void checkChatBoundary(){
        var mc=net.minecraft.client.Minecraft.getInstance();
        var chat=new net.minecraft.client.gui.components.ChatComponent(mc);
        var original=Component.literal("Starting in 1 second.");chat.addServerSystemMessage(original);
        try{
            var rawField=chat.getClass().getDeclaredField("allMessages");rawField.setAccessible(true);
            var messages=(java.util.List<?>)rawField.get(chat);
            require(((net.minecraft.client.multiplayer.chat.GuiMessage)messages.getFirst()).content()==original,"raw chat history remains English and identical");
            var displayField=chat.getClass().getDeclaredField("trimmedMessages");displayField.setAccessible(true);
            var line=(net.minecraft.client.multiplayer.chat.GuiMessage.Line)((java.util.List<?>)displayField.get(chat)).getFirst();
            var visible=new StringBuilder();line.content().accept((i,s,c)->{visible.appendCodePoint(c);return true;});
            require(!visible.toString().contains("Starting") && visible.toString().contains("1"),"chat display is translated");
            int height=mc.getWindow().getGuiScaledHeight();boolean hit=false;
            for(int y=Math.max(0,height-180);y<height && !hit;y+=2)for(int x=3;x<180 && !hit;x+=8){
                var finder=ChatOriginalHover.finder(mc.font,x,y);
                chat.captureClickableText(finder,height,mc.gui.hud.getGuiTicks(),net.minecraft.client.gui.components.ChatComponent.DisplayMode.FOREGROUND);
                hit=original.getString().equals(ChatOriginalHover.original(finder.result()));
            }
            require(hit,"copy-original hit testing works on rendered chat without changing clipboard");
            Sapphire.LOGGER.info("SAPPHIRE_CHAT_SMOKE_OK: raw history, translated display and original-text mouse hit testing");
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Chat display boundary smoke failed",e);}
    }
    private static void checkChatHistoryRestore(){
        var mc=net.minecraft.client.Minecraft.getInstance();
        var original=Component.literal("Starting in §v1 second.").withStyle(s->s.withColor(0x55ff55)
            .withClickEvent(new net.minecraft.network.chat.ClickEvent.SuggestCommand("/original-history-action")));
        var before=original.copy();
        var chat=new net.minecraft.client.gui.components.ChatComponent(mc);
        chat.addServerSystemMessage(original);
        for(int i=0;i<5;i++) {
            var restored=new net.minecraft.client.gui.components.ChatComponent(mc);
            restored.restoreState(chat.storeState());chat=restored;
        }
        try {
            var rawField=chat.getClass().getDeclaredField("allMessages");rawField.setAccessible(true);
            var messages=(java.util.List<?>)rawField.get(chat);
            require(((net.minecraft.client.multiplayer.chat.GuiMessage)messages.getFirst()).content()==original,"restored history keeps original component identity");
            var displayField=chat.getClass().getDeclaredField("trimmedMessages");displayField.setAccessible(true);
            var line=(net.minecraft.client.multiplayer.chat.GuiMessage.Line)((java.util.List<?>)displayField.get(chat)).getFirst();
            var visible=new StringBuilder();var actions=new java.util.HashSet<net.minecraft.network.chat.ClickEvent>();
            line.content().accept((i,s,c)->{visible.appendCodePoint(c);if(s.getClickEvent()!=null)actions.add(s.getClickEvent());return true;});
            require(visible.toString().equals("将在 1 秒后开始。"),"restored chat display translates normalized text");
            require(actions.contains(original.getStyle().getClickEvent()),"restored chat retains click action");
            require(before.equals(original),"history restoration never rewrites source");
            Sapphire.LOGGER.info("SAPPHIRE_CHAT_RESTORE_SMOKE_OK: five native history restores preserved source, translated display and click action");
        } catch(ReflectiveOperationException e){throw new IllegalStateException("Chat history restore smoke failed",e);}
    }
    private static void checkOnlineChatButtons(){
        var mc=net.minecraft.client.Minecraft.getInstance();
        var report=new net.minecraft.network.chat.ClickEvent.RunCommand("/report SapphireTest");
        var block=new net.minecraft.network.chat.ClickEvent.SuggestCommand("/ignore add SapphireTest");
        var original=Component.empty().append(Component.literal("SapphireTest "))
            .append(Component.literal("[Report Player]").withStyle(s->s.withClickEvent(report).withColor(0xff5555)))
            .append(Component.literal(" [Block Player]").withStyle(s->s.withClickEvent(block).withColor(0xffff55)));
        var before=original.copy();
        var selected=new com.sorakoi.sbtranslate.llm.LlmText.Selection(com.sorakoi.sbtranslate.llm.LlmText.Kind.SYSTEM,"",original.getString());
        var request=com.sorakoi.sbtranslate.llm.LlmStyledText.prepare(original,selected,List.of("SapphireTest"));
        require(request!=null,"prepare styled online notification");
        var translated=request.restore(request.text().replace("Report Player","举报玩家").replace("Block Player","屏蔽玩家"));
        require(translated!=null,"restore styled online notification");
        var chat=new net.minecraft.client.gui.components.ChatComponent(mc);
        chat.addClientSystemMessage(ChatOriginalHover.attach(translated,original));
        var found=new java.util.HashSet<net.minecraft.network.chat.ClickEvent>();int height=mc.getWindow().getGuiScaledHeight();
        for(int y=Math.max(0,height-180);y<height && found.size()<2;y+=2)
            for(int x=3;x<mc.getWindow().getGuiScaledWidth() && found.size()<2;x+=3){
                var finder=new net.minecraft.client.gui.ActiveTextCollector.ClickableStyleFinder(mc.font,x,y);
                chat.captureClickableText(finder,height,mc.gui.hud.getGuiTicks(),net.minecraft.client.gui.components.ChatComponent.DisplayMode.FOREGROUND);
                if(finder.result()!=null && finder.result().getClickEvent()!=null)found.add(finder.result().getClickEvent());
            }
        require(found.contains(report) && found.contains(block),"native mouse hit testing retains both original chat actions");
        require(before.equals(original),"API chat source remains untouched");
        Sapphire.LOGGER.info("SAPPHIRE_API_CHAT_SMOKE_OK: translated report/block labels retain native click targets; no command was sent");
    }
    private static List<Component> displayTooltip(ItemStack stack){
        var raw=stack.getTooltipLines(Item.TooltipContext.EMPTY,null,TooltipFlag.NORMAL);
        var name=stack.getHoverName().getString();
        require(raw.isEmpty() || raw.getFirst().getString().contains(name),"raw tooltip remains parser-facing");
        try {Class.forName("net.minecraft.client.gui.screens.inventory.AbstractContainerScreen");}
        catch(ClassNotFoundException e){throw new IllegalStateException(e);}
        return com.sorakoi.sbtranslate.DisplayTranslationHooks.tooltip(stack,raw);
    }
    private static void require(boolean success,String check){if(!success)throw new IllegalStateException("Sapphire smoke check failed: "+check);}
}
