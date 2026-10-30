# Bazaar 聊天提示覆盖

2026年10月17日核对。用户当前截图确认卖单成交通知仍在使用。翻译保持服务器原有数量、金额、缩写和物品名称匹配，保留颜色、点击事件，不操作订单。

## 核实来源

- [SkyHanni BazaarApi 的服务器交易消息样例](https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/features/inventory/bazaar/BazaarApi.kt)：即时买卖、下单、转为卖单。
- [SkyHanni BazaarOrderApi](https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/features/inventory/bazaar/BazaarOrderApi.kt)：领取买单、退回买单硬币。
- [SkyHanni ChatFilter](https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/features/chat/ChatFilter.kt)：交易处理中提示及旧版大小写。
- [卖单成交通知和领取失败的原始日志](https://hypixel.net/threads/report-bazaar-lag.2765251/post-19117201)。日志为历史证据，当前卖单句式另由用户截图核实。
- [买单成交通知的玩家实录](https://www.reddit.com/r/HypixelSkyblock/comments/1i9hjkl)。
- [领取卖单收益的交易日志](https://git.nea.moe/money-ledger.git/diff/?id=ee5d205578117d6fab9b2f89871e5442e480644f)。
- [取消失败原始反馈](https://hypixel.net/threads/after-refunding-sell-offer-i-didnt-got-my-items-back.5209847/)。
- [订单数量上限原文](https://hypixel.net/threads/you-have-reached-your-maximum-of-14-bazaar-orders.5299497/)、[每日下单金额上限原文](https://hypixel.net/threads/you-reached-the-daily-limit-of-coins-you-may-create-orders-for-on-the-bazaar.5279211/)。不把历史上限数值写死进译文。
- [报价限制原文](https://hypixel.net/threads/new-bazaar-price-limits-%E2%80%94-how-do-they-work.4980356/)。
- [聊天改写建议中的原有错误消息](https://hypixel.net/threads/some-chat-changes-almost-every-of-them-are-errors.5913691/)：只采用 Before 中既有的原文，未把作者建议的 After 当成服务器实际消息。

## 已覆盖的类别

| 类别 | 内容 |
|---|---|
| 成交 | 买单全部成交、卖单全部成交 |
| 创建与交易 | 提交买卖单、创建成功、即时买卖进行中与成功 |
| 领取 | 领取订单、领取物品及买入单价、领取卖出收益及卖出单价 |
| 取消 | 取消中、退回物品、退回硬币、取消失败 |
| 转卖 | 买单转为卖单、预计总利润 |
| 托管 | 物品与硬币托管 |
| 限制 | 订单数量上限、每日下单金额上限、出价过高或过低 |
| 异常 | 正在领取、没有可领取内容、稍后重试、报价不匹配、卖单变动过大、市场波动、服务器过于卡顿 |

生成源为 `tools/bazaar_chat_data.py` 和 `tools/gui_menu_data.py`，同步内置词典、编辑词典及开发运行配置。新增句式使用完整匹配，数字字段不接受任意文字；动态物品名称仍查本地物品词典。没有前缀的固定旧版消息放在精确聊天词典，订单限制消息要求句式明确含 Bazaar。玩家带名字/频道前缀的聊天不匹配。

固定提示同时兼容带 `[Bazaar]` 前缀的形式；部分形式属于兼容变体，不等于逐一在当前服务器复现。公开 Wiki、源码和历史日志没有提供可证明完整的现行消息全集，因此这是一份已核实类别覆盖表，不宣称已覆盖未来更新或所有罕见报错。未核实的完整英文句式保留原样，可由现有未翻译采集机制继续记录。
