package com.sorakoi.sbtranslate;
import com.sorakoi.sbtranslate.item.ItemIdResolver;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ItemIdTest {
    @Test void extraAttributesAndDirectCustomDataAreReadWithoutMutation() {
        var extra=new CompoundTag();extra.putString("id","ASPECT_OF_THE_END");var tag=new CompoundTag();tag.put("ExtraAttributes",extra);
        var before=tag.copy();assertEquals("ASPECT_OF_THE_END",ItemIdResolver.resolve(tag).variantId());assertEquals(before,tag);
        assertEquals("ASPECT_OF_THE_END",ItemIdResolver.resolve(extra).variantId());
        assertNull(ItemIdResolver.resolve(new CompoundTag()).skyblockId());
    }
    @Test void petsAndBooksResolveVariantsWithoutGuessing() {
        var tag=new CompoundTag();tag.putString("id","PET");tag.putString("petInfo","{\"type\":\"SHEEP\",\"tier\":\"LEGENDARY\"}");
        assertEquals("SHEEP;4",ItemIdResolver.resolve(tag).variantId());
        tag.putString("petInfo","invalid");assertNull(ItemIdResolver.resolve(tag).variantId());
        tag.putString("id","ENCHANTED_BOOK");var enchants=new CompoundTag();enchants.putInt("sharpness",6);tag.put("enchantments",enchants);
        assertEquals("SHARPNESS;6",ItemIdResolver.resolve(tag).variantId());
        enchants.putInt("smite",5);assertNull(ItemIdResolver.resolve(tag).variantId());
    }
    @Test void standaloneRunesResolveLevelsButAppliedRunesDoNotChangeEquipmentIdentity() {
        var tag=new CompoundTag();var runes=new CompoundTag();runes.putInt("CLOUDS",1);tag.put("runes",runes);
        for(String id:java.util.List.of("RUNE","UNIQUE_RUNE")) {
            tag.putString("id",id);var before=tag.copy();
            assertEquals("CLOUDS_RUNE;1",ItemIdResolver.resolve(tag).variantId());assertEquals(before,tag);
        }
        tag.putString("id","HYPERION");assertEquals("HYPERION",ItemIdResolver.resolve(tag).variantId());
        tag.putString("id","CLOUDS_RUNE;1");assertEquals("CLOUDS_RUNE;1",ItemIdResolver.resolve(tag).variantId());
        tag.putString("id","RUNE");runes.putInt("CLOUDS",4);assertNull(ItemIdResolver.resolve(tag).variantId());
        runes.putInt("CLOUDS",1);runes.putInt("BLOOD_2",1);assertNull(ItemIdResolver.resolve(tag).variantId());
        runes.remove("CLOUDS");runes.putString("BLOOD_2","1");assertNull(ItemIdResolver.resolve(tag).variantId());
        tag.putString("id","../CLOUDS_RUNE;1");assertNull(ItemIdResolver.resolve(tag).variantId());
    }
}
