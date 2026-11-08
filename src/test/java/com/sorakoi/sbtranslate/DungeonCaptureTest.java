package com.sorakoi.sbtranslate;
import com.sorakoi.sbtranslate.collection.DungeonCapture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DungeonCaptureTest {
 @TempDir Path root;
 @Test void filtersAndContext(){
  assertFalse(DungeonCapture.candidate("§e[SkyHanni] Test message","Test message"));
  assertFalse(DungeonCapture.candidate("[SkyBlocker] Test message","Test message"));
  assertFalse(DungeonCapture.candidate("Party > [MVP+] Bob: hello","hello"));
  assertFalse(DungeonCapture.candidate("Hello","你好"));
  assertTrue(DungeonCapture.candidate("Countdown: Starting soon!","Countdown: Starting soon!"));
  assertTrue(DungeonCapture.candidate("[BOSS] Necron: You shall die!","[BOSS] Necron: You shall die!"));
  assertTrue(DungeonCapture.dungeonEvidence(List.of("§7The Catacombs (F7)")));
  assertTrue(DungeonCapture.dungeonEvidence(List.of("Cleared: 95%")));
  assertFalse(DungeonCapture.dungeonEvidence(List.of("Dungeon Hub","Purse: 100")));
 }
 @Test void appendsDeduplicatesAndFlushes() throws Exception {
  Path file=root.resolve("dungeon.txt");Files.writeString(file,"previous\n");
  try(var capture=new DungeonCapture(file)){
   capture.observe(false,"chat","","Not in dungeon","Not in dungeon");
   capture.observe(true,"chat","","Door opened!","Door opened!");
   capture.observe(true,"chat","","Door opened!","Door opened!");
   capture.observe(true,"chat","","[SkyHanni] ignored","ignored");
   capture.enabled(false);capture.observe(true,"chat","","disabled","disabled");
  }
  var lines=Files.readAllLines(file);assertEquals(2,lines.size());assertEquals("previous",lines.getFirst());
  assertTrue(lines.getLast().contains("Door opened!"));assertTrue(lines.getLast().contains("original"));
 }
}
