package com.sorakoi.sbtranslate.client;

import com.google.gson.*;
import com.sorakoi.sbtranslate.Sapphire;
import com.sorakoi.sbtranslate.sync.*;
import net.minecraft.client.Minecraft;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.zip.ZipFile;

/** Explicit development-only launch: exercise the real startup updater and rendered tooltip hooks. */
public final class DictionarySyncSmokeTest {
    public static void start(Minecraft client) {
        var before=Sapphire.TRANSLATIONS;long start=System.nanoTime();
        Sapphire.updateDictionaryAsync().orTimeout(180,TimeUnit.SECONDS).thenApplyAsync(message->{
            try {
                if(!message.equals("译名库已更新至最新版！"))throw new IllegalStateException(message);
                String expected=System.getProperty("sapphire.expectedDictionaryVersion","20261030_1");
                if(!DictionaryUpdater.installedVersion(Sapphire.ROOT).equals(expected))throw new IllegalStateException("Unexpected installed version");
                Path fixture=Path.of(System.getProperty("sapphire.dictionaryFixture"));int count=0;
                try(var zip=new ZipFile(fixture.toFile())) {
                    var entries=zip.entries();while(entries.hasMoreElements()) {
                        var entry=entries.nextElement();if(!entry.getName().startsWith("translations/") || entry.isDirectory())continue;
                        Path file=Sapphire.ROOT.resolve(entry.getName()).normalize();if(!file.startsWith(Sapphire.ROOT.toAbsolutePath().normalize()))throw new IllegalStateException("Unsafe fixture path");
                        try(var in=zip.getInputStream(entry)) {
                            if(!DictionaryArchive.sha256(in.readAllBytes()).equals(DictionaryArchive.sha256(file)))throw new IllegalStateException("Installed file mismatch: "+entry.getName());
                        }count++;
                    }
                }
                if(count<8000 || Sapphire.TRANSLATIONS.itemCount()<8000)throw new IllegalStateException("Incomplete dictionary installation");
                var report=new JsonObject();report.addProperty("version",expected);report.addProperty("verifiedFiles",count);
                report.addProperty("itemCount",Sapphire.TRANSLATIONS.itemCount());report.addProperty("snapshotReplaced",before!=Sapphire.TRANSLATIONS);
                report.addProperty("milliseconds",(System.nanoTime()-start)/1_000_000);report.addProperty("endpoint",Sapphire.CONFIG.dictionaryUpdateUrl);
                return report;
            }catch(Exception e){throw new CompletionException(e);}
        }).whenComplete((report,error)->client.execute(()->{
            try {
                if(error!=null)throw new IllegalStateException("Live dictionary update failed",error);
                ClientSmokeTest.run();report.addProperty("realTooltipChecks",true);
                Files.writeString(Sapphire.ROOT.resolve("dictionary-sync-smoke.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
                Sapphire.LOGGER.info("SAPPHIRE_DICTIONARY_SMOKE_OK: {}",report);
            }catch(Exception e){Sapphire.LOGGER.error("SAPPHIRE_DICTIONARY_SMOKE_FAILED",e);}
            finally{client.stop();}
        }));
    }
}
