# Sapphire — Hypixel SkyBlock 中文显示翻译

Minecraft **26.1.2**，Java **25**，Fabric Loader **0.19.5+**，Fabric API **0.155.3+26.1.2**。

当前版本已实现客户端显示钩子、JSON 词典、重载命令、设置页、NEU 元数据索引、受控的未翻译收集和 Python 词典工具。全量词典覆盖当前 NEU 快照的 8,937 个物品 ID，按照用户指定术语翻译。文本经 DeepSeek 批量生成、已有译文复用和部分人工修订；自动校验不能替代逐条人工语言审校或 Hypixel 实机验收。

## 安装与使用

1. 使用 Java 25 运行 `.\gradlew.bat build`。
2. 将 `build/libs/sapphire-1.0.0.jar` 和对应版本 Fabric API 放到游戏实例的 `mods` 目录。
3. 首次启动生成 `config/sapphire/`；已有配置与词典不会被示例覆盖。
4. 进入游戏后执行 `/sapphire status`，查看“翻译生效”和 SkyBlock 检测状态。新配置默认强制翻译，即使检测为 false 也能显示词典中的译文。
5. `/sapphire` 打开设置界面；`/sapphire toggle` 总开关；`/sapphire reload` 重载。
6. 鼠标悬停物品时，按住左 Ctrl 或右 Ctrl 临时查看英文名称与描述，松开恢复翻译。原有颜色和格式保留；开启“附带原文”时，按住 Ctrl 仍只显示原始提示框。

开发客户端正版登录：先运行 `.\gradlew.bat microsoftLogin` 完成微软授权，再运行 `.\gradlew.bat runClient`；也可以运行 `tools\login-dev-client.cmd`。IDEA 配置、离线运行和缓存说明见 [开发客户端正版登录](docs/开发客户端正版登录.md)。

其他命令：`/sapphire debug`、`/sapphire untranslated`、`/sapphire untranslated on|off|export|clear`。均为客户端命令。

`/sapphire force on` 忽略环境限制，`/sapphire force off` 恢复仅在确认 SkyBlock 时翻译。总开关和各分类开关仍然有效；强制翻译也会在其他服务器或单人游戏中匹配本地词典。已有配置保留原选择，可通过命令或设置页的“忽略环境限制”调整。自动收集仍要求确认 SkyBlock。

## 填写中文

可以在 [dictionary-workspace](dictionary-workspace/README.md) 中继续审校译文。名称在 `name`，整段描述在 `loreParagraphs[].translation`，额外的动态规则在 `lore`。保留英文参考和占位符。

详细教程与 JSON 结构：[词典填写指南](docs/词典填写指南.md)。

100 个常用物品试译覆盖 18 件武器、12 件龙甲、6 件工具与治疗用品、10 件仆从用品、6 件饰品、18 件强化与消耗品、24 件材料、6 种传说宠物。清单及验证范围见 [试译记录](docs/pilot-100-items.json)。同套护甲的四个部位按四个物品 ID 计数；本批宠物仅涵盖传说品质。已有 10 个示例不计入本批 100 个。Kat 等 NPC 名称保留英文。

全量词典安装至开发客户端的 `run/config/sapphire/translations/items`。本次新增双大括号数字模板、段落独立颜色映射、单色说明模板、宝石槽和重复单位样式处理，需要重启客户端加载新代码；后续仅修改 JSON 可执行 `/sapphire reload`。发布包通过 `defaults/item-dictionaries.txt` 安装词典，只创建不存在的文件。旧的 100 个物品清单保留作回归测试。

本次用户授权修订不符合标准的既有译文；修订前文件保存在 `translation-state/pre-standard/` 和 `translation-state/pre-bulk/`。普通 `merge` 和游戏启动仍不覆盖已有中文。

支持整段描述翻译、中文自动换行、动态数字与样式对应。末影之刃示例包含传送距离、速度和魔力消耗占位符；宠物名称支持动态等级。洗练前缀独立存放在 `translations/reforges.json`，已提供 136 个当前快照名称与历史名称/特殊别名的中文，见 [洗练词典说明](docs/重铸词典说明.md)。

附魔译名独立存放在 `translations/enchantments.json`，涵盖用户审核的 156 类附魔及 11 个旧英文名别名；`enchantment-ids.json` 将英文名关联到 NEU 附魔书 ID。名称、等级和逗号分别保留原有样式，支持装备上的多附魔列表、累计升级等级以及单附魔/多附魔书。累计附魔统一为经验类型加“专精”，Champion 为“近战战斗专精”，Toxophilite 为“远程战斗专精”。确认清单见 `translation-review/enchantments/附魔译名已确认.txt`，修订前词典备份位于 `translation-state/enchantments/backup`。本次包含 Java 代码变更，开发客户端需重启；独立游戏实例需更新 JAR，并同步本次审核的词典，已有文件不会被启动流程覆盖。

升级后的提示框使用独立的 `tooltip-labels.json`、`tooltip-paragraphs.json`、`runes.json` 与 `dyes.json`：覆盖属性后的多段加成、动态稀有度/物品类型、灵魂绑定、等级需求、附魔等级警告、56 种洗练加成及当前词典中的 68 种符文和 68 种染料。物品名称按内部 ID 对应的基础名称匹配，保留升级/染色标记、星级和洗练前缀。段落可适应服务器不同换行；装备上展开的附魔效果使用对应附魔书词典，亡灵杀手另支持生物顺序和图标变化。Gravity 统一为“飞行生物杀手”；True Defense/True Defence 统一为“真实防御”。未知格式保留原文；本次修改前文件保存在 `translation-state/upgrade-tooltips/backup`。

已安装的钻头配件和 Necron 系列卷轴技能也使用共享提示框词典。独立符文按 `runes` 中的类型与等级解析，支持当前词典中的 68 类、142 个等级版本；装备上的符文不改变装备本身的身份。动态技能数值取自实际物品提示框。检查范围与资料见 [动态技能与符文验证](docs/动态技能与符文验证.md)。

翻译 API 只用于开发阶段生成本地 JSON，游戏运行不需要 API Key，也不联网调用 DeepSeek。Key 只放在已忽略的 `deepseek.local.json`，不会进入 Mod 包。批处理及恢复命令见 [全量翻译流程](docs/全量翻译流程.md)。

```powershell
python tools/dictionary_tool.py --root dictionary-workspace validate
python tools/dictionary_tool.py --root "你的游戏实例/config/sapphire" merge dictionary-workspace
```

`merge` 只补充新词条和空值，已有中文冲突会保留并报告。回游戏 `/sapphire reload` 生效。

## 运行时目录

```text
config/sapphire/
  config.json
  neu-index.json
  NEU-LICENSE.txt
  collection-rules.json
  translations/
    items/*.json
    global.json
    templates.json
    enchantments.json
    enchantment-ids.json
    tooltip-labels.json
    tooltip-paragraphs.json
    runes.json
    dyes.json
    gui.json
    gui-menus.json
    name-modifiers.json
    chat.json
    scoreboard.json
    actionbar.json
    title.json
    subtitle.json
    bossbar.json
  untranslated/*.json
```

默认不开启自动收集。仅在确认 SkyBlock 时，根据 NEU 已知文本与人工审核规则收集；不会保存任意玩家聊天、物品自定义文本或账号信息。记录后台批量保存，不联网上传。

正式运行不需要安装 NEU Mod；内置英文索引来自 [NotEnoughUpdates-REPO](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO)，其 MIT 许可证与数据一起保留。修改本地 `neu-index.json` 可以更新英文参考，中文词典独立存储。

## 验证与当前边界

```powershell
.\gradlew.bat build
python -m unittest discover -s tools -p "test_*.py" -v
.\gradlew.bat runClient -PsapphireSmoke
```

[开发与验收](docs/开发与验收.md) 记录版本核对、注入点、测试覆盖和待实机验证清单。特殊变体、复杂多样式句和未知消息在无法确定时回退原文。尚未声称与其他 SkyBlock Mod 完成共存测试。

## 游戏菜单汉化（开发中）

主菜单、拍卖行和 Bazaar 使用 `translations/gui-menus.json`，按原始菜单标题选择各自的按钮和说明规则，支持动态价格、手续费、分页、倒计时及订单进度。公开样本与验证范围见 [开发与验收](docs/开发与验收.md)。本阶段仍是开发验证，不表示所有 SkyBlock UI 已完成或已最终发布。

“菜单”开关可独立控制菜单标题和按钮说明，不依赖物品翻译或 Lore 开关。玩家背包中的槽位不会因为打开了拍卖行就套用菜单规则；服务器原始物品、容器标题和点击事件保持不变。Ctrl 同样可临时查看菜单按钮英文。

未知标题、未收录文本与不确定的多色格式保留原文。搜索词、玩家名和未知商品名称不猜译，不会根据显示名猜测内部物品 ID。菜单文案和服务器换行更新仍需实机反馈校准。
