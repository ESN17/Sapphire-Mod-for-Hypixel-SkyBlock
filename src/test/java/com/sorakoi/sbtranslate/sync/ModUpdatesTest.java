package com.sorakoi.sbtranslate.sync;

import java.nio.file.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ModUpdatesTest {
    @TempDir Path folder;
    @Test void releaseOrderAndOptIn() throws Exception {
        assertFalse(new com.sorakoi.sbtranslate.config.SapphireConfig().modAutoUpdate);
        assertTrue(ModUpdates.newer("1.10.0", "1.2.0"));
        assertFalse(ModUpdates.newer("1.2.0", "1.2.0"));
        assertFalse(ModUpdates.newer("1.2.0-beta", "1.1.0"));
        assertFalse(ModUpdates.newer("1.2.0", "1.2.1"));
    }
    @Test void damagedOrChangedFilesLeaveInstallationIntact() throws Exception {
        Path target=Files.writeString(folder.resolve("renamed.jar"),"original");
        Path staged=Files.writeString(folder.resolve("update.pending"),"updated");
        String old=InstallModUpdate.digest(target), next=InstallModUpdate.digest(staged);
        assertThrows(IOException.class,()->InstallModUpdate.replace(staged,target,old,old));
        assertThrows(IOException.class,()->InstallModUpdate.replace(staged,target,next,next));
        assertEquals("original",Files.readString(target));
        InstallModUpdate.replace(staged,target,next,old);
        assertEquals("updated",Files.readString(target));
        assertFalse(Files.exists(staged));
    }
}
