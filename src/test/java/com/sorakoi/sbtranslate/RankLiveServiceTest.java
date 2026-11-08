package com.sorakoi.sbtranslate;
import com.sorakoi.sbtranslate.rank.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class RankLiveServiceTest {
    @TempDir Path root;
    @Test void officialHttpsRosterAndKnownNameReceipt()throws Exception {
        Assumptions.assumeTrue("true".equals(System.getenv("SAPPHIRE_RANK_LIVE")));
        var client=new RankClient(root,new RankClient.Http());
        assertEquals("SAPPDEV",client.refresh("ESN17_OwO").find("ESN17_OwO").tag());
        assertTrue(Files.readString(root.resolve("rank-registration.json")).contains("esn17_owo"));
        assertEquals(0xff5555,client.refresh("ESN17_OwO").find("BoostingDetected").nameColor());
    }
}
