package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeathAndCreatureTranslationTest {
    static TranslationManager manager;
    @TempDir Path root;
    @BeforeAll static void load() throws Exception {manager=new TranslationManager();manager.load(Path.of("src/main/resources/defaults"));}
    @AfterAll static void release(){manager=null;}
    private static Component chat(String text){return SystemTranslationEngine.translate(Component.literal(text),"chat",manager);}
    @Test void realDeathFormatsPreserveVictimsTiersAndStyles() {
        var click=new ClickEvent.RunCommand("/viewprofile SamplePlayer");
        var raw=Component.literal(" §c☠ §bSamplePlayer §7was killed by §cRevenant Horror §6III§7.").withStyle(s->s.withClickEvent(click));
        var snapshot=raw.copy();var out=SystemTranslationEngine.translate(raw,"chat",manager);
        assertEquals(" ☠ SamplePlayer 被 亡魂恐魔 III 击杀了。",out.getString());
        StyledLoreTest.color(out,"SamplePlayer",ChatFormatting.AQUA);StyledLoreTest.color(out,"亡魂恐魔",ChatFormatting.RED);
        StyledLoreTest.color(out,"III",ChatFormatting.GOLD);
        assertEquals(click,StyledLoreTest.styleAt(out,"SamplePlayer").getClickEvent());assertEquals(snapshot,raw);
        assertEquals(" ☠ SamplePlayer 被 地穴食尸鬼 击杀了。",chat(" ☠ SamplePlayer was killed by Crypt Ghoul.").getString());
    }
    @Test void dungeonSuffixAndEnvironmentalCauses() {
        assertEquals("☠ 你被 暗影刺客 击杀了，并变成了幽灵。",chat("☠ You were killed by Shadow Assassin and became a ghost.").getString());
        for(String cause:List.of("fell into the void","fell into a deep hole","were crushed","suffocated","died to a trap","burnt to death","disconnected from the Dungeon")) {
            String source="☠ You "+cause+" and became a ghost.";
            String out=chat(source).getString();assertTrue(out.endsWith("并变成了幽灵。"),out);assertFalse(out.contains(cause),out);
        }
        assertEquals("☠ SamplePlayer 因 地穴食尸鬼 的攻击摔死了，并变成了幽灵。",chat("☠ SamplePlayer fell to their death with help from Crypt Ghoul and became a ghost.").getString());
    }
    @Test void coinsPiggyBankAndRevival() {
        var out=chat("§cYou died and lost §61,234,567.8§c coins!");
        assertEquals("你死亡了，损失了 1,234,567.8 硬币！",out.getString());StyledLoreTest.color(out,"1,234,567.8",ChatFormatting.GOLD);
        assertEquals("你死亡了，小猪存钱罐出现了裂痕！",chat("You died and your piggy bank cracked!").getString());
        assertEquals("❣ SamplePlayer 被 OtherPlayer 复活了！",chat("❣ SamplePlayer was revived by OtherPlayer!").getString());
        assertEquals("你将在 12 秒后复活！",chat("You will be revived in 12 seconds!").getString());
        assertEquals("你死亡了！",SystemTranslationEngine.translate(Component.literal("YOU DIED!"),"title",manager).getString());
        var broken=chat("§cYou died, lost §650,000 §ccoins and your piggy bank broke!");
        assertEquals("你死亡了，损失了 50,000 硬币，小猪存钱罐也碎了！",broken.getString());StyledLoreTest.color(broken,"50,000",ChatFormatting.GOLD);
        var mask=chat("§aYour §9Bonzo's Mask §asaved your life!");
        assertEquals("你的邦佐面具救了你一命！",mask.getString());StyledLoreTest.color(mask,"邦佐面具",ChatFormatting.BLUE);
        var spirit=chat("§6Second Wind Activated§a! Your Spirit Mask saved your life!");
        assertEquals("重生之风已触发！你的灵魂面具救了你一命！",spirit.getString());StyledLoreTest.color(spirit,"重生之风已触发",ChatFormatting.GOLD);
    }
    @Test void preservesUnregisteredKillersAndNpcProperNames() {
        assertEquals("☠ 你被 NewMonster 击杀了。",chat("☠ You were killed by NewMonster.").getString());
        assertEquals("☠ 你被 Bonzo 击杀了。",chat("☠ You were killed by Bonzo.").getString());
        assertEquals("☠ 你被 [Lv100] 地穴食尸鬼 击杀了。",chat("☠ You were killed by [Lv100] Crypt Ghoul.").getString());
        assertEquals("☠ 你被 野蛮人公爵 X 击杀了。",chat("☠ You were killed by Barbarian Duke X.").getString());
        var fallback=Component.translatableWithFallback("sapphire.test.death","☠ You were killed by Crypt Ghoul.").withStyle(ChatFormatting.RED);
        assertEquals("☠ 你被 地穴食尸鬼 击杀了。",SystemTranslationEngine.translate(fallback,"chat",manager).getString());
    }
    @Test void refusesPlayerChatIncludingSeparateComponentSiblings() {
        for(String s:List.of("[MVP+] SamplePlayer: ☠ You died.","[Party] SamplePlayer: You caught a Squid!","SamplePlayer was killed by Crypt Ghoul.","☠ [MVP+] SamplePlayer: You died.")) {
            var raw=Component.literal(s);assertSame(raw,SystemTranslationEngine.translate(raw,"chat",manager));
        }
        var raw=Component.literal("[MVP+] SamplePlayer: ").append(Component.literal("☠ You died."));
        assertSame(raw,SystemTranslationEngine.translate(raw,"chat",manager));
        var gui=Component.literal("☠ You were killed by Crypt Ghoul.");assertSame(gui,SystemTranslationEngine.translate(gui,"gui",manager));
    }
    @Test void fishingMessagesUseSameNamesAsNameplates() throws Exception {
        assertEquals("你钓到了一只 颚骨领主！",chat("You caught a Lord Jawbus!").getString());
        assertEquals("你钓到了一只 深渊矿工！",chat("You caught an Abyssal Miner!").getString());
        var file=TranslationManager.readObject(Path.of("src/main/resources/defaults/translations/creature-messages.json"));
        var checks=new ArrayList<org.junit.jupiter.api.function.Executable>();
        for(var element:file.getAsJsonArray("rules")) {
            var row=element.getAsJsonObject();if(!row.has("sampleRaw"))continue;
            String source=row.get("sampleRaw").getAsString();var out=chat(source);
            String expected=row.get("translation").getAsString().replace("{player}","SamplePlayer");
            checks.add(()->assertEquals(expected,out.getString(),source));
        }
        assertAll(checks);
    }
    @Test void deathDictionaryIsEditableAndKeepsMetadata() throws Exception {
        Files.createDirectories(root.resolve("translations"));
        Files.copy(Path.of("src/main/resources/defaults/translations/death-messages.json"),root.resolve("translations/death-messages.json"));
        var entry=TranslationEditorStore.entries(root).stream().filter(e->e.source().equals("☠ You died.")).findFirst().orElseThrow();
        TranslationEditorStore.save(root,entry,"☠ 你倒下了。");
        var reloaded=new TranslationManager();reloaded.load(root);
        assertEquals("☠ 你倒下了。",SystemTranslationEngine.translate(Component.literal("☠ You died."),"chat",reloaded).getString());
        assertTrue(TranslationManager.readObject(root.resolve("translations/death-messages.json")).get("wholeMessageOnly").getAsBoolean());
    }
    @Test void everyDeathTemplateAcceptsDynamicValuesAndPreservesItsVariables() throws Exception {
        var document=TranslationManager.readObject(Path.of("src/main/resources/defaults/translations/death-messages.json"));
        var values=Map.of("{mob}","Crypt Ghoul","{deathVictim}","SamplePlayer","{reviver}","OtherPlayer","{market}","1,234.5","{value}","12");
        for(var element:document.getAsJsonArray("rules")) {
            var row=element.getAsJsonObject();String source=row.get("source").getAsString(),target=row.get("translation").getAsString();
            for(var value:values.entrySet()) {source=source.replace(value.getKey(),value.getValue());target=target.replace(value.getKey(),value.getKey().equals("{mob}")?"地穴食尸鬼":value.getValue());}
            String scope=row.getAsJsonArray("scopes").get(0).getAsString();
            assertEquals(target,SystemTranslationEngine.translate(Component.literal(source),scope,manager).getString(),source);
        }
    }
}
