package com.sorakoi.sbtranslate.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.IntPredicate;

public final class SkyCoflCompat {
    private SkyCoflCompat() {}
    public static boolean installed() { return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("skycofl"); }
    public static String joinNotice(boolean englishDefault) {
        return englishDefault ? "检测到SkyCofl，物品默认显示英语原文，按住Alt查看中文翻译。" : "检测到SkyCofl，Sapphire已经自动改为按住Alt查看物品英语原文。";
    }
    public static boolean sellButton(String name) {
        return name.contains("Sell Instantly") || name.contains("Sell Sacks Now") || name.contains("Sell Inventory Now");
    }
    public static boolean previewKey(boolean installed, IntPredicate down) {
        return installed ? down.test(InputConstants.KEY_LALT) || down.test(InputConstants.KEY_RALT)
                : down.test(InputConstants.KEY_LCONTROL) || down.test(InputConstants.KEY_RCONTROL);
    }
    public static int warningStart(List<Component> source) {
        for (int i = 1; i < source.size(); i++)
            if (source.get(i).getString().contains("Sell Protection")) return i;
        return source.size();
    }
}
