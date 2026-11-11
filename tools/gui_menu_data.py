"""Reviewed Chinese GUI strings; UI values and actions stay owned by the server."""
import json
from pathlib import Path
from attachment_tooltip_data import ROOT,ROOTS,read

def pairs(text):
    return dict(line.split(' => ',1) for line in text.strip().splitlines() if line.strip())

COMMON=pairs('''
Close => 关闭
Go Back => 返回
Next Page => 下一页
Previous Page => 上一页
First Page => 首页
Last Page => 末页
Search => 搜索
Sort => 排序
Confirm => 确认
Cancel => 取消
Click to open! => 点击打开!
Click to view! => 点击查看!
Click to manage! => 点击管理!
Click to browse! => 点击浏览!
Click to edit! => 点击修改!
Click to pick! => 点击选择!
Click to switch! => 点击切换!
Click to inspect! => 点击查看详情!
Click to purchase! => 点击购买!
Click to buy now! => 点击立即购买!
Click to view items! => 点击查看物品!
Click to turn page! => 点击翻页!
Right-Click to skip! => 右键跳页!
Right-click to go backwards! => 右键反向切换!
Right-Click to go backwards! => 右键反向切换!
Right-Click to clear! => 右键清除!
Click to edit filter! => 点击修改筛选!
Click to switch sort! => 点击切换排序!
Click to switch rarity! => 点击切换稀有度!
Click to switch filter! => 点击切换筛选!
Click to claim! => 点击领取!
Click to collect! => 点击领取!
Click to cancel! => 点击取消!
Page {value} => 第 {value} 页
Page {value}/{total} => 第 {value}/{total} 页
({value}/{total}) => ({value}/{total})
Category => 分类
Examples: => 包括:
Currently browsing! => 当前分类!
Currently viewing! => 当前查看中!
''')
MAIN=pairs('''
SkyBlock Menu (Click) => SkyBlock 菜单 (点击)
Stats & Equipment => 属性与装备
Your Equipment and Stats => 装备与属性
Your SkyBlock Profile => SkyBlock 档案
Your Skills => 技能
Collections => 收藏
Recipe Book => 配方书
SkyBlock Leveling => SkyBlock 等级
Quests & Chapters => 任务与章节
Quest Log => 任务日志
Calendar and Events => 日历与活动
Storage => 仓库
Your Bags => 随身袋
Pets => 宠物
Crafting Table => 工作台
Wardrobe => 衣柜
Personal Bank => 个人银行
Fast Travel => 快速传送
Profile Management => 档案管理
Settings => 设置
Booster Cookie => 增益曲奇
Sack of Sacks => 储物袋总袋
Fishing Bag => 钓鱼袋
Potion Bag => 药水袋
Quiver => 箭袋
Accessory Bag => 饰品袋
Time Pocket => 时光口袋
Garden Desk => 花园书桌
Heart of the Mountain => 山峰之心
Heart of the Forest => 森林之心
Report Island Name => 举报岛屿名称
View all of your SkyBlock progress, including your Skills, Collections, Recipes, and more! => 查看你的 SkyBlock 进度,包括技能、收藏、配方等!
You can report this island if you think it has an inappropriate name. => 如果你认为此岛屿名称不合适,可以进行举报.
Click to report it! => 点击举报!
Access the Garden's Desk to customize your plot, purchase items and unlock unique farming progression. => 打开花园书桌,管理地块、购买物品并解锁农业成长内容.
Your Heart of the Mountain contains your progression for the deeper layers of the Deep Caverns! => 山峰之心记录了你在深层矿洞中的探索进度!
Unlock Tiers by completing Commissions and upgrade perks using Powder. => 完成委托解锁层级,使用粉末升级技能.
Your Heart of the Forest contains your progression for the Foraging Islands. => 森林之心记录了你在采伐群岛中的成长进度.
Unlock Tiers by participating in Starlyn Contests and upgrade perks using Forest Whispers. => 参加 Starlyn 竞赛解锁层级,使用森林低语升级技能.
View your equipment, stats, achievements, and more! => 查看你的装备、属性、成就等!
View your Skill progression and rewards. => 查看你的技能进度与奖励.
{value} Skill Avg. (non-cosmetic) => 技能平均等级 {value} (不含装饰技能)
View all of the items available in SkyBlock. Collect more of an item to unlock rewards on your way to becoming a master of SkyBlock! => 查看 SkyBlock 中的物品.收集更多同类物品,解锁收藏奖励,逐步精通 SkyBlock!
Through your adventure, you will unlock recipes for all kinds of special items! You can view how to craft these items here. => 冒险中可以解锁各类特殊物品的配方!在这里查看制作方法.
Your SkyBlock level: [{value}] => 你的 SkyBlock 等级: [{value}]
Collections Unlocked: {value} => 已解锁收藏: {value}
Recipe Book Unlocked: {value} => 已解锁配方: {value}
Progress to level {value}: => 距离等级 {value}:
Progress to Level {roman}: {value} => 距离等级 {roman}: {value}
Determine how far you've progressed in SkyBlock and earn rewards from completing unique tasks. => 查看你的 SkyBlock 成长进度,完成各项任务获取奖励.
Each island has its own series of Chapters for you to complete! => 每座岛屿都有专属的章节等待你完成!
Complete tasks within a Chapter to earn small rewards, or complete entire Chapters to earn big ones! => 完成章节内的任务可获得奖励,完成整个章节可获得更丰厚的奖励!
Some islands also have Quests for you to complete! Some items can only be obtained through Quests! => 部分岛屿还提供任务!某些物品只能通过任务获得!
View the SkyBlock Calendar, upcoming events, and event rewards! => 查看 SkyBlock 日历、即将举行的活动及活动奖励!
Date: {text} => 日期: {text}
Active Event: {text} => 当前活动: {text}
Next Event: {text} => 下一场活动: {text}
Starting in: {time} => 距开始: {time}
Store global items that you want to access at any time from anywhere here. => 存放物品,随时随地取用.
Different bags allow you to store many different items inside! => 不同的随身袋可存放不同种类的物品!
View and manage all your Pets. => 查看并管理你的宠物.
Level up your pets faster by gaining xp in their favorite Skill! => 获取宠物对应技能的经验,使其更快升级!
Selected Pet: None => 已选宠物: 无
Selected Pet: {name} => 已选宠物: {name}
Opens the crafting grid. => 打开合成界面.
Store armor sets and quickly swap between them! => 存放成套盔甲,快速切换穿戴!
Contact your Banker from anywhere. => 随时随地联系银行职员.
Banker Status: Available => 银行状态: 可用
Cooldown: {time} => 冷却: {time}
Teleport to islands you've already visited. => 传送到已经到访的岛屿.
Right-click to warp home! => 右键传送回岛!
Click to pick location! => 点击选择目的地!
You can have multiple SkyBlock Profiles at the same time. => 你可以同时拥有多个 SkyBlock 档案.
Each profile has its own island, inventory, quest log... => 每个档案都有独立的岛屿、背包和任务日志等.
Profiles: {value}/{total} => 档案: {value}/{total}
Play with friends using /coopadd <name>! => 使用 /coopadd <name> 与好友合作游玩!
View and edit your SkyBlock settings. => 查看并修改你的 SkyBlock 设置.
Obtain the Cookie Buff from Booster Cookies in the hub's community shop. => 在主岛社区商店获取增益曲奇,享受曲奇增益.
Duration: {time} => 持续时间: {time}
Bits Available: {value} => 可获取比特: {value}
Click to get all the info! => 点击查看详细信息!
A sack which contains other sacks. Sackception! => 用来存放其他储物袋的总袋!
A useful bag which can hold all types of fish, bait, and fishing loot! => 存放各种鱼、鱼饵和钓鱼战利品的袋子!
A handy bag for holding your Potions in. => 方便存放药水的袋子.
A masterfully crafted Quiver which holds any kind of projectile you can think of! => 精制箭袋,可存放各种弹药!
A special bag which can hold Talismans, Rings, Artifacts and Orbs within it. All will still work while in this bag! => 可存放护符、戒指、神器和能量球的特殊袋子!放入后仍可生效!
Magical Power: {value} => 魔力: {value}
Selected Power: None => 已选魔力效果: 无
Selected Power: {name} => 已选魔力效果: {name}
A bag which holds items that evolve over time. Time flows twice as fast in here! => 存放随时间成长的物品,袋内时间流速为两倍!
Requires Timite Rift Collection {roman}. => 需要时晶裂隙收藏 {roman}.
Mithril Powder: {value} => 秘银粉末: {value}
Gemstone Powder: {value} => 宝石粉末: {value}
Forest Whispers: {value} => 森林低语: {value}
Queued visitors: {value} => 等候访客: {value}
Pests: {value} {icon0} => 害虫: {value} {icon0}
''')
AUCTION=pairs('''
Auctions Browser => 拍卖浏览器
Auction Browser => 拍卖浏览器
Manage Bids => 管理出价
Manage Auctions => 管理拍卖
Your Bids => 我的出价
Your Auctions => 我的拍卖
Create Auction => 创建拍卖
Create BIN Auction => 创建一口价拍卖
Auction Stats => 拍卖统计
Seller Stats => 卖家统计
Buyer Stats => 买家统计
Switch to BIN => 切换为一口价
Switch to Auction => 切换为竞拍
Custom Duration => 自定义时长
Weapons => 武器
Armor => 盔甲
Accessories => 饰品
Consumables => 消耗品
Cosmetics => 装饰品
Blocks => 方块
Tools & Misc => 工具与杂项
Tools & Misc. => 工具与杂项
Item Tier => 物品稀有度
BIN Filter => 拍卖类型
Submit Bid => 提交出价
Bid History => 出价记录
Buy Item Right Now => 立即购买
Claim All => 全部领取
Collect Auction => 领取拍卖所得
Cancel Auction => 取消拍卖
BIDDING ON ITEM: => 正在竞拍:
BUYING ITEM: => 正在购买:
Click an item in your inventory! => 点击背包中的物品!
Find items for sale by players across Hypixel SkyBlock! => 浏览 Hypixel SkyBlock 玩家出售的物品!
Items offered here are for auction, meaning you have to place the top bid to acquire them! => 这里的物品采用竞拍方式,最高出价者可获得物品!
View and manage your bids. => 查看并管理你的出价.
Set your own items on auction for other players to purchase. => 将你的物品上架拍卖,供其他玩家购买.
Click to become rich! => 点击开始拍卖!
View various statistics about you and the Auction House. => 查看你的拍卖行相关统计.
Increase your bid limit by achieving a higher SkyBlock Level! => 提升 SkyBlock 等级可提高出价上限!
Selects it for auction => 选择此物品进行拍卖
No item selected! => 尚未选择物品!
Click an item in your inventory to select it for this auction. => 点击背包中的物品,将其选为本次拍卖的物品.
The minimum price a player can offer to obtain your item. => 玩家竞拍此物品时的最低出价.
Once a player bids for your item, other players will have until the auction ends to make a higher bid. => 有人出价后,其他玩家可在拍卖结束前继续加价.
How long players will be able to place bids for. => 允许玩家参与竞拍的时长.
Note: Bids automatically increase the duration of auctions. => 注意:出价会自动延长拍卖时长.
BIN Auctions are simple. => 一口价拍卖操作简单.
Set a price, then one player may buy the item at that price. => 设定价格后,玩家即可按此价格直接购买物品.
(BIN means Buy It Now) => (BIN 即一口价购买)
The price at which you want to sell this item. => 你希望出售此物品的价格.
With traditional auctions, multiple buyers compete for the item by bidding turn by turn. => 传统拍卖中,多位买家通过轮流出价竞争物品.
Specify how long you want the auction to last. => 设置本次拍卖的持续时长.
Right-click for minutes! => 右键按分钟设置!
Click to set hours! => 点击按小时设置!
Find items by name, type, lore, or enchants. => 按名称、类型、描述或附魔搜索物品.
Filtered: => 当前筛选:
Filtered: {text} => 当前筛选: {text}
You need to bid at least {value} coins to hold the top bid on this auction. => 出价至少 {value} 硬币,才能成为当前最高出价者.
The top bid on auction end wins the item. => 拍卖结束时,最高出价者获得物品.
If you do not win, you can claim your bid coins back. => 未竞拍成功时,可领回出价的硬币.
Click to edit amount! => 点击修改金额!
No bids have been placed on this item yet. => 此物品尚无人出价.
Be the first to bid on it! => 成为第一个出价的人!
Click to bid! => 点击出价!
Seller: {name} => 卖家: {name}
Buyer: {name} => 买家: {name}
Bidder: {name} => 出价者: {name}
Top bidder: {name} => 最高出价者: {name}
Bidding on: {text} => 竞拍物品: {text}
Status: Expired! => 状态: 已到期!
Status: Sold! => 状态: 已售出!
Status: Ended! => 状态: 已结束!
''')
BAZAAR=pairs('''
Farming => 农业
Mining => 采矿
Combat => 战斗
Woods & Fishes => 木材与鱼类
Oddities => 杂项
Buy Instantly => 立即买入
Sell Instantly => 立即卖出
Create Buy Order => 创建买单
Create Sell Offer => 创建卖单
Manage Orders => 管理订单
Your Orders => 我的订单
Buy Orders => 买单
Sell Offers => 卖单
Sell Inventory Now => 立即出售背包物品
Sell Sacks Now => 立即出售储物袋物品
Sell Inventory => 出售背包物品
Sell Sacks => 出售储物袋物品
Buy Amount => 买入数量
Sell Amount => 卖出数量
Custom Amount => 自定义数量
Custom Price => 自定义价格
Fill Inventory => 装满背包
Top Order +0.1 => 最高买价 +0.1
Same as Top Order => 与最高买价相同
Best Offer -0.1 => 最低卖价 -0.1
Same as Best Offer => 与最低卖价相同
Spread => 价差
Cancel Order => 取消订单
Cancel Offer => 取消卖单
Cancel All Orders => 取消全部订单
Claim All => 全部领取
Collect All => 全部领取
Flip Order => 转为卖单
View Orders => 查看订单
Order Options => 订单选项
Confirm Buy Order => 确认买单
Confirm Sell Offer => 确认卖单
Confirm Instant Buy => 确认立即买入
Confirm Instant Sell => 确认立即卖出
Direct Mode => 简洁模式
Advanced Mode => 高级模式
Search Products => 搜索商品
Search Results => 搜索结果
Click to buy! => 点击买入!
Click to sell! => 点击卖出!
Click to create buy order! => 点击创建买单!
Click to create sell offer! => 点击创建卖单!
Click to view orders! => 点击查看订单!
Click to manage orders! => 点击管理订单!
Click to claim items! => 点击领取物品!
Click to claim coins! => 点击领取硬币!
Right-click to cancel! => 右键取消!
Right-click for options! => 右键查看选项!
Left-click to claim! => 左键领取!
Order filled! => 订单已完成!
Order expired! => 订单已过期!
Status: Filled! => 状态: 已完成!
Status: Expired! => 状态: 已过期!
Status: Active => 状态: 生效中
Status: Completed => 状态: 已完成
BUY: {text} => 买单: {text}
SELL: {text} => 卖单: {text}
Buy {value}x => 买入 {value}x
Sell {value}x => 卖出 {value}x
No orders found! => 没有订单!
No orders! => 没有订单!
Not enough coins! => 硬币不足!
Not enough space! => 空间不足!
No items to sell! => 没有可出售的物品!
Amount: {value}x => 数量: {value}x
Amount: {value} => 数量: {value}
Filled: {value}/{total} => 已成交: {value}/{total}
Filled: {value}/{total} ({percent}) => 已成交: {value}/{total} ({percent})
Status: {text} => 状态: {text}
''')
BAZAAR.update(pairs('''
All Ultimate Enchantment levels. => 所有等级的终极附魔.
Unique items that allow you to learn Powers at the Thaumaturgist. => 可在奇术师处学习能量的特殊物品.
Powerful stones which allow you to modify items with reforges not found at the Blacksmith. => 可为物品赋予铁匠铺无法提供的重铸效果的强大石头.
Shards can be syphoned to increase the levels of your Attributes. => 可汲取碎片来提升你的属性等级.
High-level Enchantments and Enchantments that cannot be found in the Enchantment Table. => 高等级附魔,以及无法在附魔台中获得的附魔.
[Bazaar] Executing instant buy... => [集市] 正在立即购买...
[Bazaar] Bought {value}x {name} for {market} coins! => [集市] 已购买 {value}x {name},花费 {market} 硬币!
Buy only one! => 只购买一个!
Buy a stack! => 购买一组!
Fill my inventory! => 装满我的背包!
Buy Order Quantity => 买单数量
Buy up to {value}x. => 最多购买 {value}x.
Click to specify! => 点击填写!
Click to buy now! => 点击立即购买!
Click to pick amount! => 点击选择数量!
Stack price: {market} coins => 整组价格: {market} 硬币
Inventory: None! => 背包中: 无!
Inventory: {value}x => 背包中: {value}x
None to sell in your inventory! => 背包中没有可出售的物品!
Instasell Ignore => 批量出售排除设置
Toggle whether this product is ignored when bulk instaselling from your inventory or sacks. => 设置批量出售背包或储物袋物品时,是否跳过此商品.
Bulk Instasell: Allowed! => 批量出售: 允许!
Bulk Instasell: Ignored! => 批量出售: 已排除!
Click to toggle! => 点击切换!
View Graphs => 查看图表
Are you that interested in this product, you want to see a bunch of graphs? => 想进一步了解此商品? 点击查看交易图表.
Instasell Ignore List => 批量出售排除列表
Manage products which are ignored from bulk instasells. => 管理批量出售时跳过的商品.
Ignored: None! => 已排除: 无!
Ignored: {value} products => 已排除: {value} 种商品
Find the instasell ignore toggle on each individual product menu. => 可在各商品页面设置是否排除批量出售.
Bazaar View => 集市显示模式
View prices, orders volume, recent transactions and graphs for each product. => 查看各商品的价格、订单量、近期成交和交易图表.
Front page data accounts for enchanted recipes. => 首页数据已考虑附魔物品的合成配方.
Click to toggle view! => 点击切换显示模式!
[Bazaar] Cancelling order... => [集市] 正在取消订单...
[Bazaar] Cancelled! Refunded {value}x {name} from cancelling Sell Offer! => [集市] 已取消卖单! 已退回 {value}x {name}!
[Bazaar] Putting goods in escrow... => [集市] 正在托管物品...
[Bazaar] Submitting sell offer... => [集市] 正在提交卖单...
[Bazaar] Submitting buy order... => [集市] 正在提交买单...
[Bazaar] Sell Offer Setup! {value}x {name} for {market} coins. => [集市] 卖单已创建! {value}x {name},总价 {market} 硬币.
[Bazaar] Buy Order Setup! {value}x {name} for {market} coins. => [集市] 买单已创建! {value}x {name},总价 {market} 硬币.
Sell Offer Setup => 卖单设置
Buy Order Setup => 买单设置
Use the same price as the lowest Sell Offer for this item. => 使用此物品当前最低卖单的价格.
Use the same price as the highest Buy Order for this item. => 使用此物品当前最高买单的价格.
Selling: {value}x => 出售数量: {value}x
Buying: {value}x => 买入数量: {value}x
Unit price: {market} coins => 单价: {market} 硬币
Click to proceed! => 点击继续!
Every {hits}th hit on a mob traps it for {seconds}s and deals {multiplier}x of that hit's damage over the trap duration. => 每第 {hits} 次命中生物将其困住 {seconds}s,并在此期间造成该次命中伤害的 {multiplier}x.
Bazaar Settings => 集市设置
View and edit your Bazaar settings. => 查看和修改你的集市设置.
{value} products => {value} 种商品
{value} product => {value} 种商品
Click to view products! => 点击查看商品!
Click to view details! => 点击查看详情!
Find products by name! => 按名称搜索商品!
Click to search! => 点击搜索!
Instantly sell any items in your inventory that can be sold on the Bazaar. => 立即出售背包中所有可在集市交易的物品.
You earn: {market} coins => 你将获得: {market} 硬币
Common commodity => 常见商品
Uncommon commodity => 罕见商品
Rare commodity => 稀有商品
{market} in {value} offers => {value} 个卖单,共 {market} 个
{market} in {value} offer => {value} 个卖单,共 {market} 个
{market} in {value} orders => {value} 个买单,共 {market} 个
{market} in {value} order => {value} 个买单,共 {market} 个
{market} insta-buys in {days}d => {days} 天内即时买入 {market} 个
{market} insta-sells in {days}d => {days} 天内即时卖出 {market} 个
{days}d Avg. price: {market} coins => {days} 天均价: {market} 硬币
You don't have any ongoing orders for these products. => 这些商品目前没有你的进行中订单.
Top Orders: => 最高买单:
Top Offers: => 最低卖单:
- {market} coins each | {marketCount}x in {value} order => - 单价 {market} 硬币 | {value} 个买单,共 {marketCount}x
- {market} coins each | {marketCount}x in {value} orders => - 单价 {market} 硬币 | {value} 个买单,共 {marketCount}x
- {market} coins each | {marketCount}x in {value} offer => - 单价 {market} 硬币 | {value} 个卖单,共 {marketCount}x
- {market} coins each | {marketCount}x in {value} offers => - 单价 {market} 硬币 | {value} 个卖单,共 {marketCount}x
Click to setup Buy Order! => 点击设置买单!
Click to setup Sell Offer! => 点击设置卖单!
{value}x {name} for {market} coins => {value}x {name} 售价 {market} 硬币
{icon0} {name} {marketBuy} | {marketSell} => {icon0} {name} {marketBuy} | {marketSell}
BUY: {name} => 买单: {name}
SELL: {name} => 卖单: {name}
'''))
# Group labels may differ from an item's canonical NEU name. Product labels
# themselves are read from the user's item dictionaries at runtime.
BAZAAR_LABELS=pairs('''
Power Stones => 能量石
Reforge Stones => 重铸石
Shards => 碎片
Minion Upgrades => 仆从升级
Minion Storage => 仆从储物箱
Compactors => 压缩器
Slayer => 杀手
Events => 活动
Bazaar => 集市
Farming => 农业
Mining => 采矿
Combat => 战斗
Woods & Fishes => 木材与鱼类
Oddities => 杂项
Enchantments => 附魔
Normal Enchantments => 普通附魔
Ultimate Enchantments => 终极附魔
Glacite Tunnels => 冰川矿道
Fossils => 化石
Slayers => 杀手
Tarantula Broodfather => 狼蛛育母
Revenant Horror => 亡魂恐魔
Sven Packmaster => 斯文狼王
Voidgloom Seraph => 虚空炽天使
Inferno Demonlord => 炼狱恶魔领主
Redstone => 红石
Lapis => 青金石
Lapis Lazuli => 青金石
End Stone => 末地石
Sand => 沙子
Emerald => 绿宝石
Wood => 木材
Oak => 橡木
Spruce => 云杉
Birch => 白桦
Dark Oak => 深色橡木
Acacia => 金合欢
Jungle => 丛林木
Fig => 无花果木
Mangrove => 红树林木
Helix => 螺旋木
Settings => 设置
Instant Buy => 立即购买
Instant Sell => 立即出售
Fish => 鱼类
Cod => 鳕鱼
Salmon => 鲑鱼
Flowers => 花卉
Baits => 鱼饵
Mob Drops => 生物掉落物
Lava Fishing => 岩浆钓鱼
Water Orbs => 水之宝珠
Chum Fishing => 集饵钓鱼
''')
STYLES=pairs('''
one => 一个
None! => 无!
Allowed! => 允许!
Ignored! => 已排除!
Cancelled! => 已取消卖单!
Refunded => 已退回
Bazaar => 集市
Sell Offer Setup! => 卖单已创建!
Buy Order Setup! => 买单已创建!
Skills => 技能
Skill => 技能
Skill Avg. => 技能平均等级
(non-cosmetic) => (不含装饰技能)
Collections => 收藏
Recipes => 配方
auction => 竞拍
top bid => 最高出价
SkyBlock Level => SkyBlock 等级
SkyBlock => SkyBlock
Coins => 硬币
coins => 硬币
Powder => 粉末
Commissions => 委托
Heart of the Mountain => 山峰之心
Heart of the Forest => 森林之心
Forest Whispers => 森林低语
Deep Caverns => 深层矿洞
Foraging Islands => 采伐群岛
Pets => 宠物
Quests => 任务
Chapters => 章节
Booster Cookies => 增益曲奇
Cookie Buff => 曲奇增益
Talismans => 护符
Rings => 戒指
Artifacts => 神器
Orbs => 能量球
Banker => 银行职员
Cooldown => 冷却
BIN => BIN
''')

def rules(mapping):
    rows=[]
    for en,zh in mapping.items():
        styles={a:b for a,b in STYLES.items() if a in en and b in zh}
        if en.startswith('[Bazaar] Claimed ') and '{market} coins' in en and '{market} 硬币' in zh:
            styles['{market} coins']='{market} 硬币'
        if en.startswith('[Bazaar] Cancelled! Refunded '):
            for status in ('已取消买单!','已取消卖单!'):
                if status in zh:styles['Cancelled!']=status
        rows.append(dict(source=[en],translation=zh,styleMappings=styles))
    return rows

def main():
    from bazaar_chat_data import MESSAGES, EXACT, STYLES as CHAT_STYLES
    BAZAAR.update(MESSAGES)
    STYLES.update(CHAT_STYLES)
    for command in ['stats','skills','collection','recipes','calendar','storage','bags','pets','craft','wardrobe','bank','warp','profiles','viewsettings','boostercookie']:
        for prep in ['via','with']:MAIN[f'Also accessible {prep} /{command}']=f'也可输入 /{command} 打开'
    for en,zh in {'Health':'生命值','Defense':'防御','Speed':'速度','Strength':'力量','Intelligence':'智力','Crit Chance':'暴击率','Crit Damage':'暴击伤害'}.items():
        MAIN['{icon0} '+en+' {value}']='{icon0} '+zh+' {value}'
    categories=pairs('''
Swords => 剑
Bows => 弓
Magic Weapons => 魔法武器
Hats => 头盔
Chestplates => 胸甲
Leggings => 护腿
Boots => 靴子
Talismans => 护符
Rings => 戒指
Orbs => 能量球
Artifacts => 神器
Potions => 药水
Food => 食物
Books => 书
Dyes => 染料
Skins => 皮肤
Runes => 符文
Furniture => 家具
Tools => 工具
Specials => 特殊物品
Magic => 魔法物品
Staff items => 法杖物品
Highest Bid => 最高出价
Lowest Bid => 最低出价
Ending soon => 即将结束
Ending Soon => 即将结束
Most Bids => 出价次数最多
Lowest Price => 价格最低
Highest Price => 价格最高
No filter => 不限
Show All => 全部显示
BIN Only => 仅一口价
Auctions Only => 仅竞拍
Common => 普通
Uncommon => 罕见
Rare => 稀有
Epic => 史诗
Legendary => 传说
Mythic => 神话
Divine => 神圣
Special => 特殊
Very Special => 非常特殊
Ultimate => 终极
''')
    for en,zh in categories.items():
        for prefix in ['', ' ', '▶ ', '◼ ']:AUCTION[prefix+en]=prefix+zh
    AUCTION['And {value} more options']='另有 {value} 个选项'
    AUCTION[' And {value} more options']=' 另有 {value} 个选项'
    for en,zh in {'Starting bid':'起拍价','Starting Bid':'起拍价','Item price':'物品价格','New bid':'新出价','Bid Amount':'出价金额','Price':'价格','Cost':'费用','Extra fee':'额外手续费','Per-Item Bid Limit':'单件出价上限','Top bid':'最高出价','Buy it now':'一口价'}.items():
        for currency in ['coins','Coins']:
            AUCTION[en+': {value} '+currency]=zh+': {value} 硬币'
            AUCTION[en+': {value} '+currency+' ({percent})']=zh+': {value} 硬币 ({percent})'
    for en,zh in {'Buy price':'买入价','Sell price':'卖出价','Price per unit':'单价','Per unit':'单价','Price':'价格','Total price':'总价','Total':'合计','To claim':'可领取','Order price':'订单价格'}.items():
        for currency in ['coins','Coins']:
            BAZAAR[en+': {value} '+currency]=zh+': {value} 硬币'
            BAZAAR[en+': {market} '+currency]=zh+': {market} 硬币'
    for en,zh in {'Auctions created':'创建拍卖','Auctions completed with bids':'有出价的已结束拍卖','Auctions completed without bids':'无人出价的已结束拍卖','Highest auction held':'最高拍卖金额','Total coins earned':'累计收入','Coins spent on fees':'累计手续费','Auctions won':'竞拍成功','Total bids':'累计出价','Highest bid':'最高出价','Coins spent':'累计花费'}.items():AUCTION[en+': {value}']=zh+': {value}'
    for en in ['Common','Uncommon','Rare','Epic','Legendary','Mythic','Special','Ultimate']:
        for verb,zh in [('Sold','已售出'),('Bought','已买入')]:AUCTION[f'{en} {verb}: {{value}}']=f'{categories[en]}{zh}: {{value}}'
    for mapping in [MAIN,AUCTION,BAZAAR]:mapping['Ends in: {time}']='剩余时间: {time}'
    for en,zh in [('Hour','小时'),('Hours','小时'),('Day','天'),('Days','天'),('Minutes','分钟')]:
        AUCTION['{value} '+en]='{value} '+zh
        AUCTION['Duration: {value} '+en]='持续时间: {value} '+zh
    main_titles={en:MAIN[en] for en in ['Your Skills','Recipe Book','Storage','Your Bags','Pets','Wardrobe','Personal Bank','Fast Travel','Profile Management','Settings','Your Equipment and Stats']}
    main_titles.update({'SkyBlock Menu':'SkyBlock 菜单','Skills':'技能','Collections':'收藏','Accessory Bag':'饰品袋','Bank':'银行','Craft Item':'合成物品','Wardrobe ({value}/{total})':'衣柜 ({value}/{total})','({value}/{total}) Pets':'宠物 ({value}/{total})'})
    auction_titles={en:AUCTION[en] for en in ['Auctions Browser','Auction Browser','Manage Auctions','Your Bids','Your Auctions','Create Auction','Create BIN Auction','Auction Stats']}
    auction_titles.update({'Auction House':'拍卖行','Auction View':'拍卖详情','BIN Auction View':'一口价详情','Confirm Bid':'确认出价','Confirm Purchase':'确认购买','Auction Duration':'拍卖时长','Auctions: "{text}"':'拍卖: "{text}"','Auctions: {text}':'拍卖: {text}','Auctions Browser ({value}/{total})':'拍卖浏览器 ({value}/{total})','BIN Auction View ({text})':'一口价详情 ({text})'})
    bazaar_titles={en:BAZAAR[en] for en in ['Manage Orders','Your Orders','Order Options','Confirm Buy Order','Confirm Sell Offer','Confirm Instant Buy','Confirm Instant Sell']}
    bazaar_titles.update({'Bazaar':'集市','Bazaar ➜ {text}':'集市 ➜ {text}','Bazaar → {text}':'集市 → {text}','Bazaar: {text}':'集市: {text}','How many do you want?':'需要多少个?','How much do you want to pay?':'你希望支付多少?','At what price are you selling?':'你希望以什么价格出售?'})
    for category in ['Farming','Mining','Combat','Woods & Fishes','Oddities']:
        for separator in [' ➜ ',' → ',': ']:bazaar_titles['Bazaar'+separator+category]='集市'+separator+BAZAAR[category]
    for mapping in [MAIN,AUCTION,BAZAAR]:
        for en,zh in {**main_titles,**auction_titles,**bazaar_titles}.items():
            if '{' not in en:mapping['To '+en]='返回'+zh
    data={'schemaVersion':1,'loreParagraphs':rules(COMMON),'menus':[
        {'id':'skyblock','titles':main_titles,'loreParagraphs':rules(MAIN)},
        {'id':'auction','titles':auction_titles,'loreParagraphs':rules(AUCTION)},
        {'id':'bazaar','titles':bazaar_titles,'labels':BAZAAR_LABELS,'loreParagraphs':rules(BAZAAR)}]}
    for root in ROOTS:
        chat_path=root/'chat.json';chat=read(chat_path)
        for source,target in EXACT.items():chat.setdefault(source,target)
        chat_path.write_text(json.dumps(chat,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        p=root/'gui-menus.json'
        if p.exists():
            old=read(p)
            def merge_rules(destination,incoming):
                seen={tuple(r['source']):r for r in destination}
                for row in incoming:
                    key=tuple(row['source'])
                    if key not in seen:destination.append(row)
                    elif seen[key]['translation']==row['translation']:
                        for a,b in row['styleMappings'].items():seen[key].setdefault('styleMappings',{}).setdefault(a,b)
            merge_rules(old['loreParagraphs'],data['loreParagraphs'])
            for menu in data['menus']:
                existing=next((m for m in old['menus'] if m['id']==menu['id']),None)
                if existing is None:old['menus'].append(menu)
                else:
                    for a,b in menu['titles'].items():existing['titles'].setdefault(a,b)
                    for a,b in menu.get('labels',{}).items():existing.setdefault('labels',{}).setdefault(a,b)
                    merge_rules(existing['loreParagraphs'],menu['loreParagraphs'])
            target=old
        else:target=data
        raw=json.dumps(target,ensure_ascii=False,indent=2)+'\n'
        if not p.exists() or p.read_text(encoding='utf-8')!=raw:
            backup=ROOT/'translation-state/gui/backup'/p.relative_to(ROOT)
            if p.exists() and not backup.exists():backup.parent.mkdir(parents=True,exist_ok=True);backup.write_bytes(p.read_bytes())
            p.write_text(raw,encoding='utf-8')
        p=root/'gui.json';d=read(p)
        # User's established Collection terminology also applies to menu titles.
        if d.get('Collections')=='收集':
            backup=ROOT/'translation-state/gui/backup'/p.relative_to(ROOT);backup.parent.mkdir(parents=True,exist_ok=True)
            if not backup.exists():backup.write_bytes(p.read_bytes())
            d['Collections']='收藏';p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print({m['id']:len(m['loreParagraphs']) for m in data['menus']},'common',len(data['loreParagraphs']))

if __name__=='__main__':main()
