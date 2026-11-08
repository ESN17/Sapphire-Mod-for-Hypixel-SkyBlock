package com.sorakoi.sbtranslate.llm;

import com.sorakoi.sbtranslate.translation.TranslationManager;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.regex.Pattern;

/** Protect live HUD values and already-reviewed Chinese before translating remaining English. */
public final class HudLlmText {
    private static final Pattern LITERALS=Pattern.compile(
        "[\\p{IsHan}]+|[\\p{IsGreek}]+|[\\p{S}\\p{Co}]+|\\b[IVXLCDM]{1,8}\\b|(?:[0-9]+(?:\\.[0-9]+)?(?:ms|[dhms]))+|(?i:https?://\\S+|\\b(?:[a-z0-9-]+\\.)+[a-z]{2,}\\b)|\\b[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}\\b");
    private static final Pattern IDENTIFIER_FIELD=Pattern.compile(
        "(?im)^\\s*(?:Profile(?: ID)?|Server|Mayor|Minister|档案|存档(?:信息| ID)?|服务器|市长|部长)[:：]\\s*(.+)$");
    private static final Pattern ENGLISH=Pattern.compile("[A-Za-z]{2}");

    public static LlmStyledText.Request prepare(Component source,Collection<String> playerNames) {
        String text=TranslationManager.plain(source.getString());
        if(text.isBlank() || text.length()>2000 || !ENGLISH.matcher(text).find())return null;
        var literals=new ArrayList<>(List.of("Hypixel","SkyBlock","Sapphire","SkyHanni","Skyblocker","Shen",
            "VIP","MVP","YOUTUBE","ADMIN","MOD","HELPER","SAPPHIRE","SAPPDEV"));
        var values=LITERALS.matcher(text);while(values.find())literals.add(values.group());
        var identifiers=IDENTIFIER_FIELD.matcher(text);while(identifiers.find())literals.add(identifiers.group(1));
        var request=LlmStyledText.prepare(source,new LlmText.Selection(LlmText.Kind.SYSTEM,"",text),playerNames,literals);
        if(request==null)return null;
        boolean untranslated=request.segments().stream().anyMatch(segment -> ENGLISH.matcher(
            segment.protectedText().text().replaceAll("⟦S[0-9]+⟧","")).find());
        return untranslated?request:null;
    }
    private HudLlmText() {}
}
