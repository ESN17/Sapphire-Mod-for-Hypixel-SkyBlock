package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.BundledDictionaryInstaller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BundledInstallPerformanceTest {
    @TempDir Path root;
    private static final String FILE="translations/items/TEST.json";
    private static String hash(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
    private void local(byte[] data) throws IOException {
        Files.createDirectories(root.resolve(FILE).getParent());Files.write(root.resolve(FILE),data);
    }

    @Test void repeatLaunchDoesNotOpenBundledDataEvenIfCurrentHashIsInUpgradeList() throws Exception {
        byte[] data="current".getBytes();local(data);var reads=new AtomicInteger();
        for(int i=0;i<20;i++)assertFalse(BundledDictionaryInstaller.install(root,FILE,()->{reads.incrementAndGet();return data;},hash(data),hash(data)));
        assertEquals(0,reads.get());
    }

    @Test void manualChangesWinEvenWithIdenticalLengthAndTimestamp() throws Exception {
        byte[] old="old-data".getBytes(),edited="my-edits".getBytes(),next="new-data".getBytes();
        local(old);var stamp=Files.getLastModifiedTime(root.resolve(FILE));local(edited);Files.setLastModifiedTime(root.resolve(FILE),stamp);
        assertFalse(BundledDictionaryInstaller.install(root,FILE,()->{fail("Must not open bundled data for user edits");return next;},hash(old),hash(next)));
        assertArrayEquals(edited,Files.readAllBytes(root.resolve(FILE)));
    }

    @Test void missingAndKnownOldFilesStillInstallAndBackUp() throws Exception {
        byte[] old="old".getBytes(),next="new".getBytes();
        assertTrue(BundledDictionaryInstaller.install(root,FILE,()->old,null,hash(old)));
        assertTrue(BundledDictionaryInstaller.install(root,FILE,()->next,hash(old),hash(next)));
        assertArrayEquals(old,Files.readAllBytes(root.resolve("dictionary-backups").resolve(hash(old)).resolve(FILE)));
        assertArrayEquals(next,Files.readAllBytes(root.resolve(FILE)));
        Files.delete(root.resolve(FILE));
        assertTrue(BundledDictionaryInstaller.install(root,FILE,()->next,hash(old),hash(next)));
    }

    @Test void invalidBundleOrFailedReadNeverReplacesExistingFile() throws Exception {
        byte[] old="old".getBytes(),next="new".getBytes();local(old);
        assertThrows(IOException.class,()->BundledDictionaryInstaller.install(root,FILE,()->next,hash(old),hash("other".getBytes())));
        assertThrows(IOException.class,()->BundledDictionaryInstaller.install(root,FILE,()->{throw new IOException("fixture failure");},hash(old),hash(next)));
        assertArrayEquals(old,Files.readAllBytes(root.resolve(FILE)));
    }

    @Test void generatedManifestMatchesActualPackagedResources() throws Exception {
        try(var input=getClass().getResourceAsStream("/defaults/bundle-hashes.json")) {
            assertNotNull(input);
            var manifest=com.google.gson.JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(manifest.size()>8900);
            for(String path:new String[]{"neu-index.json","translations/items/ASPECT_OF_THE_END.json","translations/community-wiki.json"})
                try(var resource=getClass().getResourceAsStream("/defaults/"+path)) {
                    assertNotNull(resource);assertEquals(hash(resource.readAllBytes()),manifest.get(path).getAsString());
                }
        }
    }
}
