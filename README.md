# Sapphire

一站式 Hypixel SkyBlock 中文汉化 mod。

作者与贡献者：**ESN17_OwO**

Sapphire 在客户端显示时翻译 Hypixel SkyBlock 的物品和界面文字，使用独立的本地 JSON 词典，保留颜色、数值和样式，不修改服务器发送的原始物品数据。

## 功能

- 物品名称与描述：内置当前快照的 8,937 个物品 ID，包含装备、宠物、宠物携带物品、附魔书等。
- 菜单汉化：覆盖部分 SkyBlock 菜单、拍卖行、Bazaar 和属性页面。
- 界面与提示：支持计分板、Tab 列表、地名、NPC 对话及系统提示的本地词典匹配。
- 按住左 Ctrl 或右 Ctrl 临时查看物品提示框英文，松开恢复中文。
- `/sapphire` 设置菜单：控制翻译范围、查看状态、修改部分本地翻译并重载词典。
- 可选 API 翻译：支持 OpenAI、Claude、DeepSeek、Kimi、GLM，可设置服务商、模型、提示词与请求范围。默认关闭；使用时需自行配置 API Key，可能产生服务商费用。
- 受控的未翻译文本收集，便于补充本地词典。

词典包含社区资料与辅助翻译成果，仍可能存在漏译或不准确的译文；这里不声称覆盖全部服务器文本。API 功能用于收到的消息补充翻译。

## 安装

1. 安装 Minecraft、Fabric Loader **0.19.5 或更新的兼容版本**、对应的 Fabric API（本项目构建版本为 **0.155.3+26.1.2**）。
2. 将 Sapphire JAR 与 Fabric API 放入游戏实例的 `mods` 文件夹。
3. 使用 Java 25 启动游戏，输入 `/sapphire` 打开设置。

首次运行会生成 `config/sapphire/`。本地词典翻译不需要 API Key；不需要另外安装 NEU 模组。修改 JSON 后可执行 `/sapphire reload`。更新前请备份自己的词典。

## 构建

需要 Java 25。Gradle Wrapper 会下载所需构建依赖。

```powershell
# Windows
.\gradlew.bat build
```

```sh
# Linux / macOS
chmod +x gradlew
./gradlew build
```

输出文件：`build/libs/sapphire-dev 0.0.4.jar`。

本目录是 dev 0.0.4 的 **26.1.2 源码快照**。开发阶段的大型翻译中间产物和本地游戏实例不随源码分发；相应的可选批量审计会跳过，正常构建与其余测试不依赖这些本地工作目录。

## 目录

| 路径 | 内容 |
| --- | --- |
| `src/main/java/` | 客户端、显示钩子与翻译引擎 |
| `src/main/resources/defaults/` | 随模组安装的词典、英文索引和第三方许可 |
| `src/test/` | 翻译与格式保留等回归测试 |
| `tools/` | 词典处理、资料导入和辅助审校脚本 |
| `docs/` | 使用、词典格式与开发记录 |
| `translation-review/enchantments/` | 附魔审校资料与测试数据 |

更多说明：[词典填写指南](docs/词典填写指南.md)、[开发与验收](docs/开发与验收.md)、[历史开发记录](README-SAPPHIRE.md)。历史记录中的本地工作目录可能不包含在此发布快照中。

## 反馈

反馈漏译时，请提供英文原文、出现位置、相关物品名称或内部 ID，以及截图。请不要提交 API Key、登录令牌、完整的本地配置或含私人聊天的日志。

## 许可证与来源

这是包含不同许可组件的项目，各组件保留原有许可：

| 组件 | 许可证 | 声明 |
| --- | --- | --- |
| Sapphire 原创代码 | CC0-1.0 | [LICENSE](LICENSE) |
| NEU 英文元数据 | MIT | [NEU-LICENSE.txt](src/main/resources/defaults/NEU-LICENSE.txt) |
| SkyBlockZH 衍生翻译数据 | GPL-3.0-only | [skyzh-LICENSE.txt](src/main/resources/defaults/third-party/skyzh-LICENSE.txt) |
| Wiki 衍生补充数据 | CC BY-NC-SA 3.0 | [wiki-NOTICE.txt](src/main/resources/defaults/third-party/wiki-NOTICE.txt) |

各组件的来源、改动与适用范围见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。CC0 不覆盖全部第三方数据；分发时须保留对应声明，并遵循各组件的许可条款。

感谢 [NotEnoughUpdates-REPO](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO)、[SkyBlockZH](https://github.com/BingKKni/SkyBlockZH) 与 [Hypixel SkyBlock Wiki](https://hypixelskyblock.minecraft.wiki/) 的贡献者。本项目不代表 Hypixel、Mojang 或上述项目。

Modrinth：https://modrinth.com/mod/sapphire-cn
