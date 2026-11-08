package com.sorakoi.sbtranslate.item;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import com.google.gson.JsonParser;

public final class ItemIdResolver {
    public record Identity(String skyblockId, String variantId) {}
    public static Identity resolve(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA); if (data == null) return new Identity(null,null);
        return resolve(data.copyTag());
    }
    public static Identity resolve(CompoundTag data) {
        CompoundTag extra = data.getCompound("ExtraAttributes").orElse(data);
        String id = extra.getStringOr("id", "");
        // NEU expands the shared server ID into an attribute-and-level identity.
        // Accept that representation too (e.g. repository preview stacks).
        if (id.matches("ATTRIBUTE_SHARD_[A-Z0-9_]{1,100};(?:[1-9][0-9]?|100)")) return new Identity(id,id);
        // NEU menu stacks can carry an already expanded rune ID.
        if (id.matches("[A-Z0-9_]{1,100}_RUNE;[1-3]")) return new Identity(id,id);
        if (!id.matches("[A-Z0-9_]{1,120}")) return new Identity(null,null);
        String variant = id;
        if (id.equals("ATTRIBUTE_SHARD")) {
            CompoundTag attributes = extra.getCompoundOrEmpty("attributes");
            if (attributes.size() != 1) return new Identity(id,null);
            String attribute = attributes.keySet().iterator().next();
            int level = attributes.getIntOr(attribute,0);
            if (!attribute.matches("[a-zA-Z0-9_]{1,100}") || level < 1 || level > 100)
                return new Identity(id,null);
            variant = "ATTRIBUTE_SHARD_" + attribute.toUpperCase(java.util.Locale.ROOT) + ";" + level;
        } else if (id.equals("PET")) {
            try {
                var pet = JsonParser.parseString(extra.getStringOr("petInfo", "")).getAsJsonObject();
                String type = pet.get("type").getAsString(), tier = pet.get("tier").getAsString();
                int rarity = java.util.List.of("COMMON","UNCOMMON","RARE","EPIC","LEGENDARY","MYTHIC").indexOf(tier);
                if (type.matches("[A-Z0-9_]{1,100}") && rarity >= 0) variant = type + ";" + rarity;
                else return new Identity(id,null);
            } catch (RuntimeException e) { return new Identity(id,null); }
        } else if (id.equals("RUNE") || id.equals("UNIQUE_RUNE")) {
            CompoundTag runes=extra.getCompoundOrEmpty("runes");
            if(runes.size()!=1)return new Identity(id,null);
            String kind=runes.keySet().iterator().next();int level=runes.getIntOr(kind,0);
            if(!kind.matches("[A-Za-z0-9_]{1,100}") || level<1 || level>3)return new Identity(id,null);
            variant=kind.toUpperCase(java.util.Locale.ROOT)+"_RUNE;"+level;
        } else if (id.equals("ENCHANTED_BOOK")) {
            CompoundTag enchants = extra.getCompoundOrEmpty("enchantments");
            if (enchants.size() != 1) return new Identity(id,null);
            String enchant = enchants.keySet().iterator().next(); int level = enchants.getIntOr(enchant,0);
            if (!enchant.matches("[a-z_]{1,100}") || level < 1 || level > 100) return new Identity(id,null);
            variant = enchant.toUpperCase(java.util.Locale.ROOT) + ";" + level;
        }
        return new Identity(id,variant);
    }
}
