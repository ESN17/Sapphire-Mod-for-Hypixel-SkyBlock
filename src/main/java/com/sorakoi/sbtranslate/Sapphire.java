package com.sorakoi.sbtranslate;
import com.sorakoi.sbtranslate.translation.*;
import com.sorakoi.sbtranslate.neu.NeuRepositoryManager;
import com.sorakoi.sbtranslate.config.SapphireConfig;
import com.sorakoi.sbtranslate.collection.*;
import com.sorakoi.sbtranslate.environment.SkyBlockContext;
import com.sorakoi.sbtranslate.item.ItemTranslationService;
import com.sorakoi.sbtranslate.client.SapphireScreen;
import com.sorakoi.sbtranslate.client.SapphireApiScreen;
import com.sorakoi.sbtranslate.client.TranslationEditorScreen;
import com.sorakoi.sbtranslate.client.OnlineChatTranslation;
import com.sorakoi.sbtranslate.llm.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.*;
import java.nio.file.*;
import java.io.*;
import java.util.List;

public final class Sapphire implements ClientModInitializer {
    public static final Logger LOGGER=LoggerFactory.getLogger("sapphire");
    public static final TranslationManager TRANSLATIONS=new TranslationManager();
    public static final NeuRepositoryManager NEU=new NeuRepositoryManager();
    public static final SkyBlockContext CONTEXT=new SkyBlockContext();
    public static final CollectionPolicy POLICY=new CollectionPolicy();
    public static SapphireConfig CONFIG=new SapphireConfig();
    public static LlmConfig LLM_CONFIG=new LlmConfig();
    public static final LlmTranslationService LLM=new LlmTranslationService();
    public static UntranslatedCollector COLLECTOR;
    public static Path ROOT;
    public static String lastStatus="尚未加载";
    private int ticks;
    private static boolean initialized;
    private java.util.Map<String,String> upgradeHashes=java.util.Map.of();
    @Override public void onInitializeClient() {
        ROOT=FabricLoader.getInstance().getConfigDir().resolve("sapphire");
        try {installDefaults(); CONFIG=SapphireConfig.load(ROOT);} catch(Exception e){LOGGER.error("Sapphire configuration failed: {}",e.getMessage()); CONFIG.enabled=false;}
        reload(); COLLECTOR=new UntranslatedCollector(ROOT.resolve("untranslated"),CONFIG.maxRecords,CONFIG.maxQueue,CONFIG.maxDiskMegabytes*1024L*1024L);
        COLLECTOR.enabled(CONFIG.collectUntranslated); initialized=true;
        ItemTooltipCallback.EVENT.register((stack,context,flag,lines)->ItemTranslationService.tooltip(stack,lines));
        ClientReceiveMessageEvents.MODIFY_GAME.register((text,overlay)->{
            if(overlay)return text;
            var translated=DisplayTranslationHooks.text(text,"chat");
            if(translated==text)OnlineChatTranslation.receive(text,false,"");
            return translated;
        });
        ClientReceiveMessageEvents.CHAT.register((text,signed,sender,bound,time)->OnlineChatTranslation.receive(text,true,sender==null?"":sender.name()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{CONTEXT.reset();LLM.reset();});
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->{CONTEXT.reset();LLM.reset();});
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            CONTEXT.tick(client);
            if(++ticks%20==0 && canCollect() && client.player!=null && CONFIG.items) {
                var slots=client.player.containerMenu.slots;
                for(int i=0;i<Math.min(slots.size(),180);i++) ItemTranslationService.collectStack(slots.get(i).getItem());
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->{LLM.close();COLLECTOR.close(); if(!COLLECTOR.error().isEmpty()) LOGGER.warn("{}",COLLECTOR.error());});
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(literal("sapphire")
            .executes(c->{Minecraft.getInstance().schedule(()->Minecraft.getInstance().setScreen(new SapphireScreen(Minecraft.getInstance().screen)));return 1;})
            .then(literal("api").executes(c->{Minecraft.getInstance().schedule(()->Minecraft.getInstance().setScreen(new SapphireApiScreen(Minecraft.getInstance().screen)));return 1;}))
            .then(literal("edit").executes(c->{Minecraft.getInstance().schedule(()->Minecraft.getInstance().setScreen(new TranslationEditorScreen(Minecraft.getInstance().screen)));return 1;}))
            .then(literal("reload").executes(c->{reload();c.getSource().sendFeedback(Component.literal(lastStatus));return 1;}))
            .then(literal("status").executes(c->{c.getSource().sendFeedback(Component.literal(status()));return 1;}))
            .then(literal("toggle").executes(c->{CONFIG.enabled=!CONFIG.enabled;saveConfig();c.getSource().sendFeedback(Component.literal(status()));return 1;}))
            .then(literal("force")
                .then(literal("on").executes(c->{CONFIG.forceTranslation=true;saveConfig();c.getSource().sendFeedback(Component.literal(status()));return 1;}))
                .then(literal("off").executes(c->{CONFIG.forceTranslation=false;saveConfig();c.getSource().sendFeedback(Component.literal(status()));return 1;})))
            .then(literal("debug").executes(c->{CONFIG.debug=!CONFIG.debug;saveConfig();c.getSource().sendFeedback(Component.literal("调试："+CONFIG.debug+"；"+status()));return 1;}))
            .then(literal("untranslated").executes(c->{c.getSource().sendFeedback(Component.literal("收集："+CONFIG.collectUntranslated+"，记录："+COLLECTOR.size()+"，丢弃："+COLLECTOR.dropped()+" "+COLLECTOR.error()));return 1;})
                .then(literal("on").executes(c->{CONFIG.collectUntranslated=true;saveConfig();c.getSource().sendFeedback(Component.literal("收集已开启，仅在确认 SkyBlock 时运行"));return 1;}))
                .then(literal("off").executes(c->{CONFIG.collectUntranslated=false;saveConfig();return 1;}))
                .then(literal("export").executes(c->{COLLECTOR.flush().thenRun(()->Minecraft.getInstance().execute(()->c.getSource().sendFeedback(Component.literal("保存完成。"+COLLECTOR.error()))));return 1;}))
                .then(literal("clear").executes(c->{CONFIG.collectUntranslated=false;saveConfig();COLLECTOR.clear();c.getSource().sendFeedback(Component.literal("收集已关闭，正在清除本地收集记录"));return 1;})))));
        ClientLifecycleEvents.CLIENT_STARTED.register(client->{
            if(Boolean.getBoolean("sapphire.apiSmoke")){com.sorakoi.sbtranslate.client.ApiUiSmokeTest.start(client);return;}
            if(Boolean.getBoolean("sapphire.smoke")) {
                org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
                com.sorakoi.sbtranslate.client.ClientSmokeTest.run();
                LOGGER.info("SAPPHIRE_SMOKE_OK: mixin audit, real tooltip translation, fallback and component immutability verified"); client.stop();
            }
        });
    }
    public static boolean active(String scope){return initialized && CONFIG.scope(scope) && CONFIG.translationAllowed(CONTEXT.confirmed());}
    public static boolean canCollect(){return initialized && CONFIG.enabled && CONFIG.collectUntranslated && CONTEXT.confirmed() && COLLECTOR!=null;}
    public static Component originalMode(Component translated,Component original){return translated!=original && CONFIG.showOriginal ? Component.empty().append(translated).append(" [").append(original.copy()).append("]") : translated;}
    public static String itemContext(){
        var client=Minecraft.getInstance();
        if(client.player==null || client.player.containerMenu==client.player.inventoryMenu) return "INVENTORY";
        String title=client.screen==null?"":client.screen.getTitle().getString();
        return switch(title){case "Bazaar"->"BAZAAR";case "Auction House"->"AUCTION_HOUSE";case "SkyBlock Menu"->"SKYBLOCK_MENU";default->"CONTAINER";};
    }
    public static String status(){return "Sapphire 开启="+CONFIG.enabled+"，翻译生效="+(initialized && CONFIG.translationAllowed(CONTEXT.confirmed()))+"，强制翻译="+CONFIG.forceTranslation+"，SkyBlock="+CONTEXT.confirmed()+"，物品词条="+TRANSLATIONS.itemCount()+"，NEU="+NEU.size()+"，缓存="+TRANSLATIONS.cacheSize()+(CONFIG.debug?"；"+com.sorakoi.sbtranslate.client.Diagnostics.last:"");}
    public static void reload(){
        try{LLM_CONFIG=LlmConfig.load(ROOT);LLM.configure(LLM_CONFIG);}
        catch(Exception e){
            try{LLM_CONFIG=LlmConfig.defaults();}catch(IOException ignored){LLM_CONFIG.enabled=false;}
            LLM_CONFIG.enabled=false;LLM.configure(LLM_CONFIG);LOGGER.warn("Sapphire API 配置加载失败，在线翻译已关闭");
        }
        try {SapphireConfig next=SapphireConfig.load(ROOT); NEU.load(ROOT); POLICY.load(ROOT); TRANSLATIONS.load(ROOT); CONFIG=next;
            if(COLLECTOR!=null)COLLECTOR.enabled(CONFIG.collectUntranslated); lastStatus="词典已加载："+TRANSLATIONS.itemCount()+" 个物品";
        }catch(Exception e){lastStatus="重载失败，保留词典："+e.getMessage();LOGGER.warn("{}",lastStatus);}
    }
    public static void saveConfig(){
        if(!CONFIG.enabled || !CONFIG.chat || !CONFIG.translationAllowed(CONTEXT.confirmed()))LLM.reset();
        try{CONFIG.save(ROOT);COLLECTOR.enabled(CONFIG.collectUntranslated);}catch(IOException e){lastStatus="配置保存失败";LOGGER.warn("{}",lastStatus);}
    }
    private void installDefaults() throws IOException {
        try(var in=Sapphire.class.getResourceAsStream("/defaults/upgrade-hashes.json")) {
            if(in!=null) {
                var object=com.google.gson.JsonParser.parseReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                var hashes=new java.util.HashMap<String,String>();object.entrySet().forEach(e->hashes.put(e.getKey(),e.getValue().getAsString()));upgradeHashes=java.util.Map.copyOf(hashes);
            }
        }
        for(String name:List.of("gui-menus","name-modifiers","tooltip-labels","tooltip-paragraphs","runes","dyes","locations","skyblock-months","sidebar-formats","bazaar-events","tablist","mining-events","system-messages","community-skyzh","community-wiki"))installDefault("translations/"+name+".json");
        installDefault("third-party/skyzh-LICENSE.txt");
        installDefault("third-party/wiki-NOTICE.txt");
        try(var in=Sapphire.class.getResourceAsStream("/defaults/translations/community-wiki.json")) {
            var manifest=com.google.gson.JsonParser.parseReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if(manifest.has("parts"))for(var part:manifest.getAsJsonArray("parts")) {
                String name=part.getAsString();
                if(!name.matches("[a-z0-9-]+\\.json"))throw new IOException("Invalid bundled Wiki part name");
                installDefault("translations/community-wiki/"+name);
            }
        }
        for(String file:List.of("translations/global.json","translations/templates.json","translations/items/ASPECT_OF_THE_END.json",
                "translations/items/RECOMBOBULATOR_3000.json","translations/items/DRAGON_SCALE.json","translations/items/PET_ITEM_QUICK_CLAW.json",
                "translations/items/ENDERMAN;0.json","translations/items/ENDERMAN;1.json","translations/items/ENDERMAN;2.json","translations/items/ENDERMAN;3.json","translations/items/ENDERMAN;4.json","translations/items/ENDERMAN;5.json",
                "translations/gui.json","translations/chat.json","translations/scoreboard.json","translations/actionbar.json","translations/title.json","translations/subtitle.json","translations/bossbar.json","translations/reforges.json","translations/enchantments.json","translations/enchantment-ids.json","collection-rules.json","neu-index.json","NEU-LICENSE.txt")) {
            installDefault(file);
        }
        try (InputStream in=Sapphire.class.getResourceAsStream("/defaults/item-dictionaries.txt")) {
            if(in==null)throw new IOException("Missing bundled item dictionary manifest");
            try(var reader=new BufferedReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8))) {
                for(String id:reader.lines().toList()) {
                    if(!id.matches("[A-Za-z0-9_;.-]+"))throw new IOException("Invalid bundled dictionary ID");
                    installDefault("translations/items/"+id+".json");
                }
            }
        }
    }
    private void installDefault(String file) throws IOException {
        try(InputStream in=Sapphire.class.getResourceAsStream("/defaults/"+file)) {
            if(in==null)throw new IOException("Missing bundled dictionary: "+file);
            BundledDictionaryInstaller.install(ROOT,file,in.readAllBytes(),upgradeHashes.get(file));
        }
    }
}
