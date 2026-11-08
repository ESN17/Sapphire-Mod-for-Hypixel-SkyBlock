package com.sorakoi.sbtranslate;
import com.sorakoi.sbtranslate.translation.*;
import com.sorakoi.sbtranslate.neu.NeuRepositoryManager;
import com.sorakoi.sbtranslate.config.SapphireConfig;
import com.sorakoi.sbtranslate.collection.*;
import com.sorakoi.sbtranslate.environment.SkyBlockContext;
import com.sorakoi.sbtranslate.item.ItemTranslationService;
import com.sorakoi.sbtranslate.client.SapphireScreen;
import com.sorakoi.sbtranslate.client.ChatDisplay;
import com.sorakoi.sbtranslate.client.SapphireNotifications;
import com.sorakoi.sbtranslate.client.SapphireApiScreen;
import com.sorakoi.sbtranslate.client.TranslationEditorScreen;
import com.sorakoi.sbtranslate.client.OnlineChatTranslation;
import com.sorakoi.sbtranslate.llm.*;
import com.sorakoi.sbtranslate.sync.*;
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
    public static volatile TranslationManager TRANSLATIONS=new TranslationManager();
    public static volatile NeuRepositoryManager NEU=new NeuRepositoryManager();
    public static final SkyBlockContext CONTEXT=new SkyBlockContext();
    public static volatile CollectionPolicy POLICY=new CollectionPolicy();
    public static SapphireConfig CONFIG=new SapphireConfig();
    public static LlmConfig LLM_CONFIG=LlmConfig.defaults();
    public static final LlmTranslationService LLM=new LlmTranslationService();
    public static final OutgoingChatTranslationService OUTGOING=new OutgoingChatTranslationService();
    private static final java.util.concurrent.ExecutorService RELOAD_WORKER=java.util.concurrent.Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Sapphire dictionaries");t.setDaemon(true);t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    private static java.util.concurrent.CompletableFuture<String> reloading;
    private static java.util.concurrent.CompletableFuture<String> updating;
    private static final DictionaryUpdateNotice UPDATE_NOTICE=new DictionaryUpdateNotice();
    private static String dictionaryStatus="尚未检查";
    private static boolean remoteDictionary;
    private static java.util.concurrent.CompletableFuture<String> startup;
    private static long configRevision;
    public static UntranslatedCollector COLLECTOR;
    public static Path ROOT;
    public static String lastStatus="尚未加载";
    private int ticks;
    private static boolean initialized;
    private static java.util.Map<String,String> upgradeHashes=java.util.Map.of();
    private static java.util.Map<String,String> bundleHashes=java.util.Map.of();
    @Override public void onInitializeClient() {
        long started=System.nanoTime();
        ROOT=FabricLoader.getInstance().getConfigDir().resolve("sapphire");
        try {CONFIG=SapphireConfig.load(ROOT);}
        catch(Exception e){LOGGER.error("Sapphire configuration failed: {}",e.getMessage());CONFIG.enabled=false;}
        loadApiSettings();
        COLLECTOR=new UntranslatedCollector(ROOT.resolve("untranslated"),CONFIG.maxRecords,CONFIG.maxQueue,CONFIG.maxDiskMegabytes*1024L*1024L);
        COLLECTOR.enabled(CONFIG.collectUntranslated);
        lastStatus="词典正在后台加载，完成前暂时显示原文";
        startup=loadAsync(true);
        com.sorakoi.sbtranslate.client.OutgoingChatTranslation.register();
        com.sorakoi.sbtranslate.client.SapphireHelp.register();
        net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
            .registerReloadListener(net.minecraft.resources.Identifier.fromNamespaceAndPath("sapphire","tooltip_cache"),
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener)resources->{ItemTranslationService.DISPLAY_CACHE.clear();com.sorakoi.sbtranslate.client.EntityNameDisplay.clear();ChatDisplay.invalidateLayouts();});
        // Message events and ItemStack tooltip access remain raw for other mods.
        // Translation is applied only to HUD/container display copies.
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{com.sorakoi.sbtranslate.client.SapphireRanks.disconnect();com.sorakoi.sbtranslate.client.ChatDisplay.clear();UPDATE_NOTICE.disconnect();CONTEXT.reset();LLM.reset();OUTGOING.reset();ItemTranslationService.clearCaches();com.sorakoi.sbtranslate.client.EntityNameDisplay.clear();});
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->{CONTEXT.reset();LLM.reset();ChatDisplay.clear();OUTGOING.reset();com.sorakoi.sbtranslate.client.SapphireRanks.join(client);});
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            com.sorakoi.sbtranslate.client.ChatDisplay.tick();
            com.sorakoi.sbtranslate.client.OnlineHudTranslation.tick();
            com.sorakoi.sbtranslate.client.SapphireRanks.tick(client);
            CONTEXT.tick(client);
            var connection=client.getConnection();
            if(client.player!=null && connection!=null && connection.getServerData()!=null) {
                String notice=UPDATE_NOTICE.poll(connection.getConnection(),connection.getServerData().ip);
                if(notice!=null)client.gui.hud.getChat().addClientSystemMessage(SapphireNotifications.status(notice));
            }
            if(++ticks%20==0 && canCollect() && client.player!=null && CONFIG.items) {
                var slots=client.player.containerMenu.slots;
                for(int i=0;i<Math.min(slots.size(),180);i++) ItemTranslationService.collectStack(slots.get(i).getItem());
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->{if(com.sorakoi.sbtranslate.collection.DungeonCapture.INSTANCE!=null)com.sorakoi.sbtranslate.collection.DungeonCapture.INSTANCE.close();com.sorakoi.sbtranslate.client.SapphireRanks.close();RELOAD_WORKER.shutdownNow();OUTGOING.close();LLM.close();COLLECTOR.close(); if(!COLLECTOR.error().isEmpty()) LOGGER.warn("{}",COLLECTOR.error());});
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(literal("sapphire")
            .executes(c->{Minecraft.getInstance().schedule(()->Minecraft.getInstance().gui.setScreen(new SapphireScreen(Minecraft.getInstance().gui.screen())));return 1;})
            .then(literal("api").executes(c->{Minecraft.getInstance().schedule(()->Minecraft.getInstance().gui.setScreen(new SapphireApiScreen(Minecraft.getInstance().gui.screen())));return 1;}))
            .then(literal("edit").executes(c->{Minecraft.getInstance().schedule(()->Minecraft.getInstance().gui.setScreen(new TranslationEditorScreen(Minecraft.getInstance().gui.screen())));return 1;}))
            .then(literal("reload").executes(c->{reloadAsync().thenAccept(s->c.getSource().sendFeedback(SapphireNotifications.status(s)));c.getSource().sendFeedback(SapphireNotifications.status("正在后台加载词典..."));return 1;}))
            .then(literal("update").executes(c->{updateDictionaryAsync().thenAccept(s->c.getSource().sendFeedback(SapphireNotifications.status(s)));c.getSource().sendFeedback(SapphireNotifications.status("正在后台检查译名库更新..."));return 1;}))
            .then(literal("status").executes(c->{c.getSource().sendFeedback(SapphireNotifications.status(status()));return 1;}))
            .then(literal("toggle").executes(c->{CONFIG.enabled=!CONFIG.enabled;saveConfig();c.getSource().sendFeedback(SapphireNotifications.status(status()));return 1;}))
            .then(literal("force")
                .then(literal("on").executes(c->{CONFIG.forceTranslation=true;saveConfig();c.getSource().sendFeedback(SapphireNotifications.status(status()));return 1;}))
                .then(literal("off").executes(c->{CONFIG.forceTranslation=false;saveConfig();c.getSource().sendFeedback(SapphireNotifications.status(status()));return 1;})))
            .then(literal("debug").executes(c->{CONFIG.debug=!CONFIG.debug;saveConfig();c.getSource().sendFeedback(SapphireNotifications.status("调试："+CONFIG.debug+"；"+status()));return 1;}))
            .then(literal("dungeon").executes(c->{var capture=com.sorakoi.sbtranslate.collection.DungeonCapture.INSTANCE;c.getSource().sendFeedback(SapphireNotifications.status(capture==null?"此版本未启用地牢采集":capture.status()));return 1;})
                .then(literal("on").executes(c->{var capture=com.sorakoi.sbtranslate.collection.DungeonCapture.INSTANCE;if(capture!=null)capture.enabled(true);c.getSource().sendFeedback(SapphireNotifications.status(capture==null?"此版本未启用地牢采集":capture.status()));return 1;}))
                .then(literal("off").executes(c->{var capture=com.sorakoi.sbtranslate.collection.DungeonCapture.INSTANCE;if(capture!=null)capture.enabled(false);c.getSource().sendFeedback(SapphireNotifications.status(capture==null?"此版本未启用地牢采集":capture.status()));return 1;})))
            .then(literal("untranslated").executes(c->{c.getSource().sendFeedback(SapphireNotifications.status("收集："+CONFIG.collectUntranslated+"，记录："+COLLECTOR.size()+"，丢弃："+COLLECTOR.dropped()+" "+COLLECTOR.error()));return 1;})
                .then(literal("on").executes(c->{CONFIG.collectUntranslated=true;saveConfig();c.getSource().sendFeedback(SapphireNotifications.status("收集已开启，仅在确认 SkyBlock 时运行"));return 1;}))
                .then(literal("off").executes(c->{CONFIG.collectUntranslated=false;saveConfig();c.getSource().sendFeedback(SapphireNotifications.status("收集已关闭"));return 1;}))
                .then(literal("export").executes(c->{COLLECTOR.flush().thenRun(()->Minecraft.getInstance().execute(()->c.getSource().sendFeedback(SapphireNotifications.status(COLLECTOR.error().isEmpty()?"保存完成。":"保存失败："+COLLECTOR.error()))));return 1;}))
                .then(literal("clear").executes(c->{CONFIG.collectUntranslated=false;saveConfig();COLLECTOR.clear();c.getSource().sendFeedback(SapphireNotifications.status("收集已关闭，正在清除本地收集记录"));return 1;})))));
        ClientLifecycleEvents.CLIENT_STARTED.register(client->startup.thenAccept(result->client.execute(()->{
            if(!initialized){LOGGER.error("Sapphire startup dictionaries unavailable: {}",result);
                if(Boolean.getBoolean("sapphire.smoke") || Boolean.getBoolean("sapphire.apiSmoke") || Boolean.getBoolean("sapphire.dictionarySmoke"))client.stop();
                return;
            }
            if(Boolean.getBoolean("sapphire.dictionarySmoke")){com.sorakoi.sbtranslate.client.DictionarySyncSmokeTest.start(client);return;}
            if(Boolean.getBoolean("sapphire.apiSmoke")){com.sorakoi.sbtranslate.client.ApiUiSmokeTest.start(client);return;}
            if(Boolean.getBoolean("sapphire.smoke")) {
                org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
                com.sorakoi.sbtranslate.client.ClientSmokeTest.run();
                LOGGER.info("SAPPHIRE_SMOKE_OK: mixin audit, real tooltip translation, fallback and component immutability verified"); client.stop();
            } else if(CONFIG.dictionaryAutoUpdate)updateDictionaryAsync();
        })));
        LOGGER.info("Sapphire startup main-thread setup: {} ms; dictionaries loading in background",(System.nanoTime()-started)/1_000_000);
    }
    public static boolean active(String scope){return initialized && CONFIG.scope(scope) && CONFIG.translationAllowed(CONTEXT.confirmed());}
    public static boolean dictionariesReady(){return initialized;}
    public static boolean canCollect(){return initialized && CONFIG.enabled && CONFIG.collectUntranslated && CONTEXT.confirmed() && COLLECTOR!=null;}
    public static String dictionaryStatus(){return dictionaryStatus;}
    public static Component originalMode(Component translated,Component original){return translated!=original && CONFIG.showOriginal ? Component.empty().append(translated).append(" [").append(original.copy()).append("]") : translated;}
    public static String itemContext(){
        var client=Minecraft.getInstance();
        if(client.player==null || client.player.containerMenu==client.player.inventoryMenu) return "INVENTORY";
        String title=client.gui.screen()==null?"":client.gui.screen().getTitle().getString();
        return switch(title){case "Bazaar"->"BAZAAR";case "Auction House"->"AUCTION_HOUSE";case "SkyBlock Menu"->"SKYBLOCK_MENU";default->"CONTAINER";};
    }
    public static String status(){return "Sapphire 开启="+CONFIG.enabled+"，翻译生效="+(initialized && CONFIG.translationAllowed(CONTEXT.confirmed()))+"，强制翻译="+CONFIG.forceTranslation+"，SkyBlock="+CONTEXT.confirmed()+"，物品词条="+TRANSLATIONS.itemCount()+"，NEU="+NEU.size()+"，缓存="+TRANSLATIONS.cacheSize()+"，译名库="+dictionaryStatus+(CONFIG.debug?"；"+com.sorakoi.sbtranslate.client.Diagnostics.last:"");}
    public static synchronized java.util.concurrent.CompletableFuture<String> updateDictionaryAsync() {
        if(updating!=null && !updating.isDone())return updating;
        var done=new java.util.concurrent.CompletableFuture<String>();updating=done;
        String url=CONFIG.dictionaryUpdateUrl;dictionaryStatus="检查中";UPDATE_NOTICE.checking();
        RELOAD_WORKER.execute(()->{
            var result=new DictionaryUpdater(ROOT,new DictionaryTransport.Http()).update(url,version->Minecraft.getInstance().execute(()->{
                dictionaryStatus="已检测到新版译名库（"+version+"），正在下载中…";
                UPDATE_NOTICE.downloading(version);
            }));
            if(Thread.currentThread().isInterrupted())return;
            if(result.translations()!=null){
                com.sorakoi.sbtranslate.compat.SkyHanniCompat.load(ROOT);
                com.sorakoi.sbtranslate.compat.OdinCompat.load(ROOT);
            }
            Minecraft.getInstance().execute(()->{
                if(result.translations()!=null){TRANSLATIONS=result.translations();ItemTranslationService.clearCaches();com.sorakoi.sbtranslate.client.EntityNameDisplay.clear();ChatDisplay.invalidateLayouts();}
                dictionaryStatus=(result.version().isEmpty()?"内置词典":result.version())+"；"+result.message();
                UPDATE_NOTICE.result(result);
                if(!result.detail().isEmpty())LOGGER.warn("Dictionary update {}: {}",result.status(),result.detail());
                done.complete(result.message());
            });
        });return done;
    }
    public static void saveConfig(){
        configRevision++;
        ChatDisplay.invalidateLayouts();
        var client=Minecraft.getInstance();if(client!=null && client.gui!=null)client.gui.hud.getChat().rescaleChat();
        if(!CONFIG.enabled || !CONFIG.chat || !CONFIG.translationAllowed(CONTEXT.confirmed()))LLM.reset();
        try{CONFIG.save(ROOT);COLLECTOR.enabled(CONFIG.collectUntranslated);}catch(IOException e){lastStatus="配置保存失败";LOGGER.warn("{}",lastStatus);}
    }
    private static void loadApiSettings() {
        try {
            LLM_CONFIG=LlmConfig.load(ROOT);
        } catch(IOException e) {
            LOGGER.warn("Unable to read API settings; keeping the current configuration");
        }
        LLM.configure(LLM_CONFIG);
    }
    public static synchronized java.util.concurrent.CompletableFuture<String> reloadAsync(){
        if(reloading!=null && !reloading.isDone())return reloading.thenCompose(ignored->reloadAsync());
        loadApiSettings();
        return loadAsync(!initialized);
    }
    /** Build new indexes off-thread; rendering keeps using the old snapshot until commit. */
    private static synchronized java.util.concurrent.CompletableFuture<String> loadAsync(boolean prepareDefaults){
        var done=new java.util.concurrent.CompletableFuture<String>();reloading=done;
        long requestedConfig=configRevision;
        RELOAD_WORKER.execute(()->{
            long begin=System.nanoTime();
            try {
                if(prepareDefaults) {
                    new DictionaryUpdater(ROOT,new DictionaryTransport.Http()).recover();
                    remoteDictionary=DictionaryUpdater.installed(ROOT);
                    installDefaults();
                }
                long installed=System.nanoTime();
                var translations=new TranslationManager();var neu=new NeuRepositoryManager();var policy=new CollectionPolicy();
                translations.load(ROOT);long translated=System.nanoTime();
                neu.load(ROOT);policy.load(ROOT);long indexed=System.nanoTime();
                com.sorakoi.sbtranslate.compat.SkyHanniCompat.load(ROOT);
                com.sorakoi.sbtranslate.compat.OdinCompat.load(ROOT);
                String installedVersion=remoteDictionary?DictionaryUpdater.installedVersion(ROOT):"尚未检查";
                var config=SapphireConfig.load(ROOT);
                Minecraft.getInstance().execute(()->{
                    TRANSLATIONS=translations;NEU=neu;POLICY=policy;ItemTranslationService.clearCaches();com.sorakoi.sbtranslate.client.EntityNameDisplay.clear();ChatDisplay.invalidateLayouts();
                    // Do not undo settings changed in the UI while loading.
                    if(configRevision==requestedConfig){CONFIG=config;if(COLLECTOR!=null)COLLECTOR.enabled(config.collectUntranslated);}
                    if(prepareDefaults)dictionaryStatus=installedVersion;
                    initialized=true;
                    lastStatus="词典已加载："+translations.itemCount()+" 个物品";
                    LOGGER.info("Sapphire dictionary load: install={} ms, translations={} ms, metadata={} ms, total={} ms, items={}",
                        (installed-begin)/1_000_000,(translated-installed)/1_000_000,(indexed-translated)/1_000_000,
                        (System.nanoTime()-begin)/1_000_000,translations.itemCount());
                    done.complete(lastStatus);
                });
            }catch(Exception e){Minecraft.getInstance().execute(()->{lastStatus=initialized?"重载失败，保留原词典":"词典加载失败，暂时显示原文；可使用 /sapphire reload 重试";LOGGER.warn("Dictionary reload failed",e);done.complete(lastStatus);});}
        });return done;
    }
    private static void installDefaults() throws IOException {
        try(var in=Sapphire.class.getResourceAsStream("/defaults/bundle-hashes.json")) {
            if(in!=null) {
                var object=com.google.gson.JsonParser.parseReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                var hashes=new java.util.HashMap<String,String>();object.entrySet().forEach(e->hashes.put(e.getKey(),e.getValue().getAsString()));bundleHashes=java.util.Map.copyOf(hashes);
            }
        }
        try(var in=Sapphire.class.getResourceAsStream("/defaults/upgrade-hashes.json")) {
            if(in!=null) {
                var object=com.google.gson.JsonParser.parseReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                var hashes=new java.util.HashMap<String,String>();object.entrySet().forEach(e->hashes.put(e.getKey(),e.getValue().getAsString()));upgradeHashes=java.util.Map.copyOf(hashes);
            }
        }
        for(String name:List.of("gui-menus","name-modifiers","tooltip-labels","tooltip-paragraphs","runes","dyes","locations","skyblock-months","sidebar-formats","bazaar-events","tablist","mining-events","system-messages","death-messages","creature-messages","entity-names","entity-labels","entity-menus","community-skyzh","community-wiki"))installDefault("translations/"+name+".json");
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
    private static void installDefault(String file) throws IOException {
        if(remoteDictionary && file.startsWith("translations/"))return;
        BundledDictionaryInstaller.install(ROOT,file,()->{
            try(InputStream in=Sapphire.class.getResourceAsStream("/defaults/"+file)) {
                if(in==null)throw new IOException("Missing bundled dictionary: "+file);
                return in.readAllBytes();
            }
        },upgradeHashes.get(file),bundleHashes.get(file));
    }
}
