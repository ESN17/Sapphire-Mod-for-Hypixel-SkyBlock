package com.sorakoi.sbtranslate.collection;

import com.sorakoi.sbtranslate.Sapphire;
import com.sorakoi.sbtranslate.translation.GuiTranslationEngine;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.Items;
import com.sorakoi.sbtranslate.translation.TranslationManager;

public final class UiCapture {
    private static final Pattern PRIVATE_MENU = Pattern.compile("(?i)auction|trade request|profile|visitor|guest|social|friend|guild|party finder|storage|backpack|chest|wardrobe|museum|search|sign|player|co-op|island management");
    private static final Pattern PRIVATE_LINE = Pattern.compile("(?i)https?://|www\\.|@|minecraft:|skyblock:|\\b(?:seller|buyer|bidder|owner|donated by|obtained by|crafted by|username|password|api key|token|last played by)\\b|[a-z0-9_-]{32,}");
    private static final Pattern ENGLISH = Pattern.compile("[a-zA-Z]{2,}");
    private static final Pattern CHINESE = Pattern.compile("[\\p{IsHan}]");
    private static final Pattern NUMBER = Pattern.compile("[0-9]+(?:[,.][0-9]+)*");
    // These screens also contain player-supplied names. Only retain public controls and numeric market rows.
    private static final Pattern LIMITED_MENU = Pattern.compile("(?i)(?:\\([0-9]+/[0-9]+\\) )?(?:Auction House|Auctions Browser|Manage Auctions|Create (?:BIN )?Auction|Auction View|BIN Auction View|Your Bids|Pets|Pets Menu)(?: \\([0-9]+/[0-9]+\\))?");
    private static final Pattern CONTROL = Pattern.compile("(?i)(?:(?:(?:shift[- ]?)?(?:left[- ]|right[- ])?click to )?(?:view details|view bids|place (?:a )?bid|buy (?:it )?now|confirm(?: purchase)?|cancel(?: auction)?|collect(?: all)?|claim(?: all)?|sort(?: by rarity)?|go back|close|summon(?: pet)?|despawn(?: pet)?|convert to (?:an )?item)[!.]?|(?:price|buy price|sell price|starting bid|top bid|highest bid|current bid|lowest BIN|cost): [0-9][0-9,.]*[kmbt]?(?: coins?)?[!.]?)");
    private static java.lang.ref.WeakReference<Object> checkedScreen = new java.lang.ref.WeakReference<>(null);
    private static long recheckAfter;
    private static boolean navigationFound;
    private UiCapture() {}

    public static boolean publicMenu(String title) {
        return publicMenu(title,Sapphire.CONTEXT.publicLocation());
    }
    public static boolean publicMenu(String title,boolean publicLocation) {
        if (title == null || title.length() > 128) return false;
        if (LIMITED_MENU.matcher(title).matches()) return true;
        if (PRIVATE_MENU.matcher(title).find()) return false;
        if (publicLocation) return true;
        var dictionary = Sapphire.TRANSLATIONS;
        if (!dictionary.wikiButtons(title).isEmpty()) return true;
        var menu = GuiTranslationEngine.menu(title, dictionary);
        return (menu != null && menu.hasFixedTitle(title)) || hasServerNavigation(title);
    }

    // A new server menu can precede its wiki page. Wait until its navigation slots arrive.
    public static boolean hasServerNavigation(String title) {
        if (title == null || title.length() > 128) return false;
        if (LIMITED_MENU.matcher(title).matches()) return true;
        if (PRIVATE_MENU.matcher(title).find() || !eligible(title)) return false;
        var client = Minecraft.getInstance();
        if (client == null || client.player == null || !(client.screen instanceof AbstractContainerScreen<?> screen)
                || !screen.getTitle().getString().equals(title)) return false;
        long now = System.nanoTime();
        if (screen == checkedScreen.get() && now < recheckAfter) return navigationFound;
        checkedScreen = new java.lang.ref.WeakReference<>(screen);
        recheckAfter = now + java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
        boolean close = false, back = false;
        for (var slot : screen.getMenu().slots) {
            if (slot.container == client.player.getInventory()) continue;
            var item = slot.getItem();
            if (item.getItem() != Items.BARRIER && item.getItem() != Items.ARROW) continue;
            String label = TranslationManager.plain(item.getHoverName().getString());
            close |= item.getItem() == Items.BARRIER && label.equals("Close");
            back |= item.getItem() == Items.ARROW && label.equals("Go Back");
        }
        navigationFound = close && back;
        return navigationFound;
    }

    static boolean eligible(String text) {
        return !text.isEmpty() && text.length() <= 512 && !text.startsWith("/")
                && text.chars().noneMatch(Character::isISOControl) && !PRIVATE_LINE.matcher(text).find()
                && ENGLISH.matcher(text).find() && (!CHINESE.matcher(text).find() || mixedEnglish(text));
    }

    private static final Pattern MIXED = Pattern.compile("(?i)^(?:[a-z]+(?:[ -][a-z]+)*\\s*[:：]|(?:.*\\p{IsHan}.*?)[a-z]{2,}\\s+[a-z]{2,}\\b)");
    public static boolean mixedEnglish(String text) {
        return CHINESE.matcher(text).find() && MIXED.matcher(text.strip()).find();
    }

    static String dedupKey(String text) {
        return NUMBER.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("#");
    }
    public static boolean allowMenuLine(String title,String line) {
        return !LIMITED_MENU.matcher(title).matches() || CONTROL.matcher(TranslationManager.plain(line).strip()).matches();
    }
}
