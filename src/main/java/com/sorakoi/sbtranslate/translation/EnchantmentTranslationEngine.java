package com.sorakoi.sbtranslate.translation;

import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Recognizes complete enchantment lists, not incidental words in item descriptions. */
public final class EnchantmentTranslationEngine {
    private static final Pattern ENTRY = Pattern.compile(
        "[ \\t]*([A-Za-z][A-Za-z' -]*?) +([IVXLCDM]+|[0-9]+)"
        + "(?: +(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?[kKmMbB]?)?[ \\t]*(,|$)");
    private record Replacement(int start, int end, String text) {}
    public record Header(String name, int level) {}

    public static Header header(String text) {
        var matcher = ENTRY.matcher(TranslationManager.plain(text));
        if (!matcher.matches() || !matcher.group(3).isEmpty()) return null;
        String token = matcher.group(2);
        int level = 0;
        if (token.matches("[0-9]+")) {
            try { level = Integer.parseInt(token); } catch (NumberFormatException invalid) { return null; }
        } else {
            int previous = 0;
            for (int i=token.length()-1;i>=0;i--) {
                int value = switch(token.charAt(i)) { case 'I'->1; case 'V'->5; case 'X'->10; case 'L'->50; case 'C'->100; case 'D'->500; default->1000; };
                level += value < previous ? -value : value; previous = value;
            }
        }
        return level > 0 && level <= 100 ? new Header(matcher.group(1),level) : null;
    }

    public static Component translate(Component source, Function<String,String> names) {
        String text = TranslationManager.plain(source.getString());
        if (text.isBlank() || text.length() > 2048) return source;
        var matcher = ENTRY.matcher(text);
        var replacements = new ArrayList<Replacement>();
        int end = 0;
        while (end < text.length()) {
            matcher.region(end,text.length());
            if (!matcher.lookingAt()) {
                // A trailing comma and whitespace are common on wrapped gear lists.
                if (text.substring(end).isBlank()) break;
                return source;
            }
            String translated = names.apply(matcher.group(1));
            if (translated != null && !translated.isBlank() && !translated.equals(matcher.group(1)))
                replacements.add(new Replacement(matcher.start(1),matcher.end(1),translated));
            end = matcher.end();
        }
        Component result = source;
        // Replace from the right so source offsets remain valid. Levels, punctuation,
        // unknown enchants, hover/click events and legacy styles stay on copied spans.
        for (int i=replacements.size()-1;i>=0;i--) {
            var r = replacements.get(i);
            result = TextTranslationEngine.rangeReplacement(result,r.start(),r.end(),r.text());
        }
        return result;
    }
}
