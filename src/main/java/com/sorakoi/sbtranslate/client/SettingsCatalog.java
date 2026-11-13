package com.sorakoi.sbtranslate.client;

import com.sorakoi.sbtranslate.config.SapphireConfig;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Explicit bindings keep the settings menu independent of field reflection. */
final class SettingsCatalog {
    record Category(String name,String description) {}
    record Option(int category,String key,String title,String description,Predicate<SapphireConfig> read,BiConsumer<SapphireConfig,Boolean> write) {
        boolean matches(String query) {
            String text=(title+" "+description+" "+key+" "+CATEGORIES.get(category).name()).toLowerCase(Locale.ROOT);
            return java.util.Arrays.stream(query.toLowerCase(Locale.ROOT).strip().split("\\s+")).allMatch(text::contains);
        }
    }
    static final List<Category> CATEGORIES=List.of(
        new Category("常用设置","先选显示方式，再按需要调整翻译范围。"),
        new Category("物品与界面","分别控制物品、菜单和各处游戏信息。"),
        new Category("聊天与原文","保留聊天操作，随时查看英文原文。"),
        new Category("模组与称号","兼容其他模组，管理 Sapphire 用户标识。"),
        new Category("词典与工具","维护译名库，预览和修改本地译文。"),
        new Category("高级设置","收集和诊断选项，排查问题时使用。"));
    static final List<Option> OPTIONS=List.of(
        new Option(0,"modAutoUpdate","Sapphire 模组自动更新","从官网下载匹配当前 Minecraft 的正式版，正常退出后安装，下次启动生效。",c->c.modAutoUpdate,(c,v)->c.modAutoUpdate=v),
        new Option(0,"enabled","翻译总开关","控制 Sapphire 的游戏文本翻译。",c->c.enabled,(c,v)->c.enabled=v),
        new Option(0,"englishByDefault","按键预览中文","物品提示默认显示英文，按住 Ctrl 切换中文。装有 SkyCofl 时，所有物品用 Alt 预览，Ctrl 仍用于放行交易。",c->c.englishByDefault,(c,v)->c.englishByDefault=v),
        new Option(0,"showOriginal","译文附带原文","在支持的位置同时显示中文译文与英文原文。",c->c.showOriginal,(c,v)->c.showOriginal=v),
        new Option(1,"items","物品名称","使用物品库中的中文名称。",c->c.items,(c,v)->c.items=v),
        new Option(1,"lore","物品描述","翻译属性、附魔、技能与物品说明。",c->c.lore,(c,v)->c.lore=v),
        new Option(1,"gui","菜单界面","翻译 SkyBlock 菜单标题和按钮说明。",c->c.gui,(c,v)->c.gui=v),
        new Option(1,"scoreboard","右侧计分板","翻译地点、任务和计分板信息。",c->c.scoreboard,(c,v)->c.scoreboard=v),
        new Option(1,"tablist","Tab 玩家列表","翻译 Tab 中的信息，保留玩家名称和数值。",c->c.tablist,(c,v)->c.tablist=v),
        new Option(1,"actionbar","动作栏","翻译屏幕下方的动作栏提示。",c->c.actionbar,(c,v)->c.actionbar=v),
        new Option(1,"title","屏幕大标题","翻译屏幕中央的大标题提示。",c->c.title,(c,v)->c.title=v),
        new Option(1,"subtitle","屏幕副标题","翻译大标题下方的提示。",c->c.subtitle,(c,v)->c.subtitle=v),
        new Option(1,"bossbar","Boss 血条","翻译屏幕顶部血条或进度条的文字。",c->c.bossbar,(c,v)->c.bossbar=v),
        new Option(1,"entities","生物与 NPC 标签","翻译头顶显示的生物名称和交互提示。",c->c.entities,(c,v)->c.entities=v),
        new Option(2,"chat","系统聊天","使用本地词典翻译聊天栏中的系统通知。",c->c.chat,(c,v)->c.chat=v),
        new Option(2,"apiChatReplace","API 译文原位覆盖","在线翻译完成后，直接替换对应消息的显示内容。",c->c.apiChatReplace,(c,v)->c.apiChatReplace=v),
        new Option(2,"chatOriginalHover","聊天悬停原文","鼠标停在已翻译的消息上时查看英文原文。",c->c.chatOriginalHover,(c,v)->c.chatOriginalHover=v),
        new Option(2,"chatCopyOriginal","右键复制聊天原文","右键译文复制英文；左键保留举报、展示物品等原有操作。",c->c.chatCopyOriginal,(c,v)->c.chatCopyOriginal=v),
        new Option(3,"skyHanniTranslation","SkyHanni 汉化","翻译支持的 SkyHanni 页面和提示；未安装时不生效。",c->c.skyHanniTranslation,(c,v)->c.skyHanniTranslation=v),
        new Option(3,"odinTranslation","Odin 汉化","翻译支持的 Odin 页面和提示；未安装时不生效。",c->c.odinTranslation,(c,v)->c.odinTranslation=v),
        new Option(3,"sapphireRanks","Sapphire 用户称号","启用用户称号。首次进入 Hypixel 登记用户名，之后获取称号名册。",c->c.sapphireRanks,(c,v)->c.sapphireRanks=v),
        new Option(4,"dictionaryAutoUpdate","译名库自动更新","启动时检查译名库，后台下载可用更新。",c->c.dictionaryAutoUpdate,(c,v)->c.dictionaryAutoUpdate=v),
        new Option(5,"collectUntranslated","收集未翻译文本","确认处于 SkyBlock 后，把遇到的漏译文本（物品、菜单等）记录到本地 config/sapphire/untranslated；只存本机，不上传。",c->c.collectUntranslated,(c,v)->c.collectUntranslated=v),
        new Option(5,"collectChatCapture","聊天漏译采集","进入 SkyBlock 后，把仍为英文的聊天行（保留颜色）写入本地文件；每十分钟及离开时上传，自己的岛屿不收集 NPC 对话，成功后删除本地文件。",c->c.collectChatCapture,(c,v)->c.collectChatCapture=v),
        new Option(5,"collectUiCapture","菜单漏译采集","将已识别的 SkyBlock 公共菜单中漏译的标题、按钮和悬浮说明保留颜色，随漏译记录上传。",c->c.collectUiCapture,(c,v)->c.collectUiCapture=v),
        new Option(5,"uploadNotifications","上传提示","上传未翻译文本时，在聊天栏提示正在上传。",c->c.uploadNotifications,(c,v)->c.uploadNotifications=v),
        new Option(5,"debug","调试诊断","启用排查翻译问题所需的诊断信息。",c->c.debug,(c,v)->c.debug=v),
        new Option(5,"forceTranslation","忽略环境限制","未确认 SkyBlock 时也允许翻译；可能影响其他服务器的显示。",c->c.forceTranslation,(c,v)->c.forceTranslation=v));
    private SettingsCatalog() {}
}
