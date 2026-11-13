package com.sorakoi.sbtranslate;

import com.sorakoi.sbtranslate.translation.*;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionDisplayWorkloadTest {
    @Test void translatePagesWithChangingAuctions() throws Exception {
        var manager=new TranslationManager();
        manager.load(Path.of(System.getProperty("sapphire.workload.dictionary","src/main/resources/defaults")));
        for(int page=0;page<3;page++){
            long start=System.nanoTime();
            for(int slot=0;slot<54;slot++){
                int number=(page==0?0:54)+slot;
                var menu=GuiTranslationEngine.menu("Auctions Browser",manager);
                assertNotNull(menu);
                var rows=List.<Component>of(Component.literal("Seller: [MVP+] Player_"+number),
                    Component.literal("Starting bid: "+(1000000+number)+" coins"),
                    Component.literal("Ends in: 12h "+slot+"m"),
                    Component.literal("Unrecognized auction line "+number));
                var result=LoreTranslationEngine.translate(rows,manager,"ASPECT_OF_THE_END",(s,raw)->List.of(s),
                    false,false,true,menu.paragraphs(),r->GuiTranslationEngine.detailLine(r,menu,manager),
                    r->CommunityTranslationEngine.translate(r,"tooltip",manager),
                    r->manager.community().candidates("tooltip",r.getString()));
                assertTrue(result.lines().stream().anyMatch(r->r.getString().contains("Player_"+number)));
                assertTrue(result.lines().stream().anyMatch(r->r.getString().contains("Unrecognized auction line "+number)));
            }
            System.out.printf(Locale.ROOT,"AUCTION_PAGE_%d_MS=%.2f%n",page,(System.nanoTime()-start)/1e6);
        }
    }
}
