"""Reviewed server-message templates from SkyHanni beta and official Hypixel wiki.
Reference checkout: build/weekly-reference/skyhanni (2026-10-21).
Exact examples: ChatFilter, ShortenCoins, BazaarApi, BazaarOrderApi,
AuctionOutbidWarning, MiningEventTracker, MiningAPI, TabWidget and mining/*.
Compatibility variants are deliberately scoped to server display channels.
"""
import json
from pathlib import Path
from dictionary_tool import read,write
ROOT=Path(__file__).resolve().parents[1]
DEST=[ROOT/'src/main/resources/defaults/translations',ROOT/'dictionary-workspace/translations',ROOT/'run/config/sapphire/translations']
rules=[]
def rule(source,target,scopes=('chat',),**maps):
    anchors={'[Auction]':'[拍卖]','[Bazaar]':'[集市]','CLICK':'点击查看','coins':'硬币','Coins':'硬币','PASSIVE EVENT':'被动活动','EVENT':'活动','RUNNING FOR':'剩余','ACTIVE IN':'正在','STARTED':'开始了','ENDED':'结束了','Cold':'寒冷','campfire':'营火','Pickobulus':'镐爆','Mithril Powder':'秘银粉末','Gemstone Powder':'宝石粉末','Glacite Powder':'冰川粉末','MONOLITH':'巨石','Dark Monolith':'黑暗巨石','PRISTINE':'提纯','Metal Detector':'金属探测器'}
    for s,t in anchors.items():
        if s in source and t in target:maps.setdefault(s,t)
    if source.startswith('[Bazaar] Claimed ') and '{market} coins' in source and '{market} 硬币' in target:
        maps.setdefault('{market} coins','{market} 硬币')
    if source.startswith('PRISTINE! You found '):
        maps.update({'PRISTINE':'提纯','You found':'你获得了'})
    rules.append(dict(source=source,translation=target,scopes=list(scopes),styleMappings=maps))
def lines(text):
    return dict(line.split('|',1) for line in text.strip().splitlines())
chat=lines('''Putting item in escrow...|正在托管物品...
Putting coins in escrow...|正在托管硬币...
Setting up the auction...|正在创建拍卖...
Processing purchase...|正在处理购买...
Processing bid...|正在处理出价...
Claiming BIN auction...|正在领取一口价拍卖...
Visit the Auction House to collect your item!|前往拍卖行领取物品!
You don't have enough coins!|你的硬币不足!
You don't have enough coins to do that!|你的硬币不足,无法执行此操作!
You cannot afford this auction!|你的硬币不足以购买此拍卖物品!
This auction has ended!|此拍卖已结束!
This auction has been claimed!|此拍卖物品已被领取!
This auction wasn't found!|未找到此拍卖!
You cannot bid on your own auction!|你不能对自己的拍卖出价!
You cannot buy your own auction!|你不能购买自己的拍卖物品!
You don't have enough space in your inventory!|你的物品栏空间不足!
You cancelled the auction!|你取消了拍卖!
Auction cancelled!|拍卖已取消!
You have no auctions to claim!|你没有可领取的拍卖!
Your auction has expired!|你的拍卖已到期!
You cannot use the Auction House right now!|你现在无法使用拍卖行!
The Auction House is currently disabled!|拍卖行目前已停用!
The Bazaar is currently disabled!|集市目前已停用!''')
for s,t in chat.items():rule(s,t)
for s,t in [
('[Auction] {player} bought {name} for {coins} coins CLICK','[拍卖] {player} 花费 {coins} 硬币购买了 {name} 点击查看'),
('[Auction] {player} outbid you by {coins} coins for {name} CLICK','[拍卖] {player} 对 {name} 的出价比你高 {coins} 硬币 点击查看'),
('[Auction] {player} bid {coins} coins on your {name}!','[拍卖] {player} 为你的 {name} 出价 {coins} 硬币!'),
('[Auction] Your {name} sold for {coins} coins!','[拍卖] 你的 {name} 以 {coins} 硬币售出!'),
('You purchased {name} for {coins} coins!','你花费 {coins} 硬币购买了 {name}!'),
('You sold {name} x{count} for {coins} Coins!','你卖出了 {count} 个 {name},获得 {coins} 硬币!'),
('You collected {coins} coins from selling {name}!','你领取了出售 {name} 所得的 {coins} 硬币!'),
('You collected {name} from the auction house!','你从拍卖行领取了 {name}!'),
('You claimed {name} from the auction house!','你从拍卖行领取了 {name}!'),
('You claimed {coins} coins from your auctions!','你领取了拍卖所得的 {coins} 硬币!'),
('You bid {coins} coins on {name}!','你为 {name} 出价 {coins} 硬币!'),
('You created an auction for {name}!','你为 {name} 创建了拍卖!'),
('You created a BIN auction for {name}!','你为 {name} 创建了一口价拍卖!'),
('You claimed {coins} coins back from an auction!','你从拍卖中取回了 {coins} 硬币!'),
('You won an auction for {name} with a bid of {coins} coins!','你以 {coins} 硬币的出价赢得了 {name} 的拍卖!'),
('[Bazaar] Claimed {count}x {name} worth {market} coins bought for {marketBuy} each!','[集市] 领取了 {count} 个 {name},总价值 {market} 硬币,购买单价为 {marketBuy}!'),
('[Bazaar] Claimed {market} coins from selling {count}x {name} at {marketSell} each!','[集市] 领取了以单价 {marketSell} 出售 {count} 个 {name} 所得的 {market} 硬币!'),
]:rule(s,t)
events=lines('''GONE WITH THE WIND|随风而行
2X POWDER|双倍粉末
2x Powder|双倍粉末
DOUBLE POWDER|双倍粉末
GOBLIN RAID|哥布林突袭
BETTER TOGETHER|齐心协力
RAFFLE|抽奖
MITHRIL GOURMAND|秘银美食家
FORTUNATE FREEZING|幸运冰冻
MINING FIESTA|挖矿节''')
for s,t in list(events.items()):events.setdefault(s.title(),t)
for s,t in events.items():
    for ending,zh in [(' STARTED!','开始了!'),(' ENDED!','结束了!'),(' starts in {time}!','将在 {time} 后开始!')]:rule(s+ending,t+zh,('chat','title','subtitle','bossbar'))
    rule(s,t,('bossbar','title','subtitle','tablist'))
for s,t in [
('PASSIVE EVENT {event} RUNNING FOR {time}','被动活动 {event} 剩余 {time}'),
('EVENT {event} ACTIVE IN {location} for {time}','活动 {event} 正在 {location} 进行,剩余 {time}'),
('EVENT {event} ACTIVE IN {location} FOR {time}','活动 {event} 正在 {location} 进行,剩余 {time}'),
]:rule(s,t,('bossbar','chat','scoreboard','tablist'))
mining=lines('''NEW AREA DISCOVERED!|发现新区域!
■ Mine new gemstones and ores!|■ 开采新的宝石和矿石!
■ Look for Mineshafts!|■ 寻找矿井!
■ Find rare fossils!|■ 寻找稀有化石!
■ Find Dulin...?|■ 找到 Dulin...?
The mineshaft entrance has caved in... it doesn't look like anyone else will be able to get in here.|矿井入口坍塌了...看起来其他人已经无法进入这里.
MINESHAFT!|矿井!
A Mineshaft portal spawned nearby!|附近出现了矿井传送门!
WOW! You found a Glacite Mineshaft portal!|哇! 你发现了冰川矿井传送门!
MINESHAFT! A Mineshaft portal spawned nearby!|矿井! 附近出现了矿井传送门!
CRYSTAL NUCLEUS LOOT BUNDLE|水晶核心战利品礼包
You've earned a Crystal Loot Bundle!|你获得了水晶战利品礼包!
You have already placed this Crystal!|你已经放置了这颗水晶!
You haven't found this Crystal yet!|你还没有找到这颗水晶!
EXCAVATION COMPLETE|挖掘完成
You didn't find anything. Maybe next time!|你什么也没找到.下次再试试吧!
LUCKY! Your Resourceful perk saved your key from being consumed!|幸运! 你的物尽其用天赋避免了钥匙被消耗!
CHEST LOCKPICKED|宝箱已解锁
COMPACT! You found an Enchanted Hard Stone!|压缩! 你发现了一块附魔硬石!
You uncovered a treasure chest!|你发现了一个宝箱!
You have successfully picked the lock on this chest!|你成功打开了这个宝箱的锁!
You froze to death.|你冻死了.
☠ You froze to death.|☠ 你冻死了.
Heat: IMMUNE|热量: 免疫
Cold: IMMUNE|寒冷: 免疫
Your Pickobulus didn't destroy any blocks!|你的镐爆没有破坏任何方块!''')
for s,t in mining.items():rule(s,t,('chat','bossbar','title','subtitle','tablist','scoreboard'))
for s,t in [('Mining Event: Not announced','采矿活动: 尚未公布'),('PICK IT UP!','捡起来!'),("You've brought me all of the components!",'你已经带来了所有零件!'),("That's not one of the components I need! Bring me one of the missing components:",'这不是我需要的零件! 请带来下列缺失的零件之一:'),('MAYHEM! You received a Mining Fortune buff from your Mineshaft Mayhem perk!','混乱! 你的矿井混乱天赋提供了挖掘时运加成!')]:rule(s,t,('chat','tablist','title','subtitle'))
rule('Mining Event: {event}','采矿活动: {event}',('tablist',))
rule('Ends in: {time}','剩余时间: {time}',('tablist',))
rule('✦ CRYSTAL FOUND ({count}/{total})','✦ 发现水晶 ({count}/{total})',('chat','title','subtitle'))
for s,t in [('Mithril','秘银'),('Gemstone','宝石'),('Glacite','冰川')]:rule(s+' Powder',t+'粉末',('chat','tablist'))
for s,t in [('Mining Speed Boost','挖掘速度提升'),('Pickobulus','镐爆'),('Maniac Miner','疯狂矿工'),('Gemstone Infusion','宝石灌注'),('Sheer Force','蛮力')]:
    rule('You used your '+s+' Pickaxe Ability!','你使用了'+t+'镐子技能!',('chat',),**{s:t,'Pickaxe Ability':'镐子技能'})
for s,t in [
('The warmth of the campfire reduced your {icon} Cold to {value}!','营火的温暖使你的 {icon} 寒冷降至 {value}!'),
('You used your {name} Pickaxe Ability!','你使用了 {name} 镐子技能!'),
('Your Pickobulus destroyed {count} blocks!','你的镐爆破坏了 {count} 个方块!'),
('PRISTINE! You found {icon} {name} x{count}!','提纯！ 你获得了 {icon} {name} x{count}!'),
('You found {name} x{count} with your Metal Detector!','你用金属探测器找到了 {count} 个 {name}!'),
('You found {name} with your Metal Detector!','你用金属探测器找到了 {name}!'),
('You have received a {name}!','你获得了 {name}!'),
('MONOLITH! You found a mysterious Dark Monolith and were rewarded {coins} Coins!','巨石! 你发现了神秘的黑暗巨石,获得了 {coins} 硬币!'),
('MONOLITH! You found a mysterious Dark Monolith and were rewarded {count} ᠅ Mithril Powder!','巨石! 你发现了神秘的黑暗巨石,获得了 {count} ᠅ 秘银粉末!'),
('MONOLITH! You found a mysterious Dark Monolith and were rewarded {coins} Coins and {count} ᠅ Mithril Powder!','巨石! 你发现了神秘的黑暗巨石,获得了 {coins} 硬币和 {count} ᠅ 秘银粉末!'),
('You found {count} Mithril Powder!','你找到了 {count} 秘银粉末!'),
('You found {count} Gemstone Powder!','你找到了 {count} 宝石粉末!'),
('You found {count} Glacite Powder!','你找到了 {count} 冰川粉末!'),
]:rule(s,t)
tab=lines('''Info|信息
Stats:|属性:
Pet:|宠物:
Pet Training:|宠物训练:
Island|岛屿
Collection:|收集:
Slayer:|杀手:
Daily Quests:|每日任务:
Bestiary:|怪物图鉴:
Forges:|锻造炉:
Timers:|计时:
Trapper:|捕兽人:
Commissions:|委托:
Powders:|粉末:
Crystals:|水晶:
Faction Quests:|派系任务:
Barbarian Reputation:|野蛮人声望:
Mage Reputation:|法师声望:
Trophy Fish:|战利品鱼:
Good to know:|实用信息:
Advertisement:|公告:
Composter:|堆肥机:
Pests:|害虫:
Visitors:|访客:
Dragon spawned!|巨龙已生成!
Egg respawning!|龙蛋正在重生!
Full Traps: None|已满陷阱: 无
None|无
Not active|未激活
Not Active|未激活
Inactive|未激活
Ready|已完成
Ready!|已完成!
LOCKED|未解锁
EMPTY|空闲
DONE|完成
DONE!|完成!
NOT FOUND|未找到
FOUND|已找到
PLACED|已放置
NOT LOOTED|未搜刮
LOOTED|已搜刮
No effects active|没有生效中的效果
No active pet|未召唤宠物
No Active Pet|未召唤宠物
No commissions available|没有可用委托''')
labels=lines('''Players|玩家
Guests|来客
Active Effects|生效中的效果
Server|服务器
Profile|档案
SB Level|空岛等级
Bank|银行
Interest|利息
Soulflow|灵魂流
Fire Sales|限时特卖
Election|选举
Skills|技能
Minions|仆从
North Stars|北极星
Gems|宝石点数
Opened Rooms|已开启房间
Unclaimed chests|未领取宝箱
Copper|铜币
Sowdust|播种尘
Garden Level|花园等级
Pest Traps|害虫陷阱
Full Traps|已满陷阱
Mithril Powder|秘银粉末
Gemstone Powder|宝石粉末
Glacite Powder|冰川粉末
Health|生命值
Defense|防御
Strength|力量
Speed|速度
Intelligence|智力
Crit Chance|暴击率
Crit Damage|暴击伤害
Magic Find|魔法寻宝
Pet Luck|宠物幸运
Mining Speed|挖掘速度
Mining Fortune|挖掘时运
Farming Fortune|耕作时运
Foraging Fortune|伐木时运
Fishing Speed|钓鱼速度
Sea Creature Chance|海洋生物几率
Farming|耕作
Mining|采矿
Combat|战斗
Foraging|伐木
Fishing|钓鱼
Enchanting|附魔
Alchemy|炼药
Carpentry|木工
Runecrafting|符文制作
Taming|驯养
Hunting|狩猎
Fairy Souls|仙女之魂
Commission Milestone|委托里程碑
Heart of the Mountain|山之心''')
for s,t in labels.items():
    tab.setdefault(s+':',t+':')
    for suffix in ['{market}','{value}/{max}','({count})','[{level}] {value}','{time}']:
        rule(s+': '+suffix,t+': '+suffix,('tablist',))
    rule(s+' ({count})',t+' ({count})',('tablist',))
# Free text only where it is an identifier to preserve, never an arbitrary player row.
for s,t in [('Server','服务器'),('Profile','档案'),('Party','队伍'),('Coop','合作岛')]:rule(s+': {text}',t+': {text}',('tablist',))
for s,t in [('Area','区域'),('Dungeon','地牢'),('Location','地点')]:rule(s+': {location}',t+': {location}',('tablist',))
rule('Event: {event}','活动: {event}',('tablist',))
for s,t in [('Rain','降雨'),('Thunder','雷暴'),('Island closes in','岛屿关闭倒计时')]:rule(s+': {time}',t+': {time}',('tablist',))
for s,t in [('Lapis','青金石'),('Tungsten','钨'),('Umber','棕土'),('Vanguard','先锋')]:
    rule(s.upper()+' CORPSE LOOT!',t+'尸体战利品!',('chat','title','subtitle'))
    for status,zh in [('NOT LOOTED','未搜刮'),('LOOTED','已搜刮')]:rule(s+': '+status,t+': '+zh,('tablist',))
for s,t in [('Jade','翡翠'),('Amber','琥珀'),('Amethyst','紫水晶'),('Sapphire','蓝宝石'),('Topaz','黄玉')]:
    rule(s+' Crystal',t+'水晶',('chat','tablist','title','subtitle'))
    rule('✦ You placed the '+s+' Crystal!','✦ 你放置了'+t+'水晶!',('chat',),**{s+' Crystal':t+'水晶'})
    for status,zh in [('NOT FOUND','未找到'),('FOUND','已找到'),('PLACED','已放置')]:rule(s+': '+status,t+': '+zh,('tablist',))
    for source,target in [('You placed the '+s+' Crystal!','你放置了'+t+'水晶!'),('You found the '+s+' Crystal!','你找到了'+t+'水晶!')]:rule(source,target)
commissions=lines('''Mithril Miner|秘银矿工
Titanium Miner|钛矿矿工
Goblin Slayer|哥布林杀手
Ice Walker Slayer|冰行者杀手
Goblin Raid Slayer|哥布林突袭杀手
Lucky Raffle|幸运抽奖
Raffle|抽奖
Mithril Gourmand|秘银美食家
Hard Stone Miner|硬石矿工
Automaton Slayer|自动机杀手
Sludge Slayer|史莱姆杀手
Thyst Slayer|紫晶寄生虫杀手
Corleone Slayer|科里昂杀手
Yog Slayer|瑜伽兽杀手
Glacite Collector|冰川石收集者
Tungsten Collector|钨矿收集者
Umber Collector|棕土收集者
Glacite Mineshafts|冰川矿井''')
for gem,zh in [('Jade','翡翠'),('Amber','琥珀'),('Amethyst','紫水晶'),('Sapphire','蓝宝石'),('Topaz','黄玉'),('Ruby','红宝石'),('Jasper','碧玉'),('Onyx','缟玛瑙'),('Peridot','橄榄石'),('Citrine','黄水晶'),('Aquamarine','海蓝宝石')]:commissions[gem+' Gemstone Collector']=zh+('' if zh.endswith('宝石') else '宝石')+'收集者'
locations=read(DEST[0]/'locations.json')
for location in ['Royal Mines','Cliffside Veins','Lava Springs','Rampart\'s Quarry','Upper Mines']:
    for material,zh in [('Mithril','秘银'),('Titanium','钛矿')]:commissions[location+' '+material]=locations.get(location,location)+zh
for s,t in commissions.items():
    for suffix in ['{value}','{value}/{max}','DONE','DONE!']:
        zsuffix={'DONE':'完成','DONE!':'完成!'}.get(suffix,suffix);rule(s+': '+suffix,t+': '+zsuffix,('tablist','scoreboard'))
    rule(s.upper()+' Commission Complete! Visit the King to claim your rewards!',t+'委托完成! 拜访国王领取奖励!')
    rule(s.upper()+' Commission Complete! Visit an Emissary to claim your rewards!',t+'委托完成! 拜访使者领取奖励!')
for s,t in tab.items():rule(s,t,('tablist',))
for row in rules:
    row['translation']=row['translation'].replace('冰川矿井',locations.get('Glacite Mineshafts','冰晶矿井'))
for dest in DEST:
    labelsPath=dest/'tooltip-labels.json';tooltipLabels=read(labelsPath);tooltipLabels.setdefault('Held Item','携带物品');write(labelsPath,tooltipLabels)
    for tier in range(1,5):
        path=dest/'items'/('RESPIRATION;'+str(tier)+'.json');data=read(path)
        for p in data.get('loreParagraphs',[]):
            if 'Respiration' in ' '.join(p['source']) and '水下呼吸' in p['translation']:p.setdefault('styleMappings',{}).setdefault('Respiration','水下呼吸')
        write(path,data)
    for filename,values in [('chat.json',chat),('tablist.json',tab),('mining-events.json',events)]:
        path=dest/filename;data=read(path) if path.exists() else {}
        for s,t in values.items():data.setdefault(s,t)
        write(path,data)
    path=dest/'system-messages.json';data=read(path) if path.exists() else {'rules':[]};merged={(r['source'],tuple(r['scopes'])):r for r in data['rules']}
    for r in rules:merged[(r['source'],tuple(r['scopes']))]=r
    data['rules']=list(merged.values());write(path,data)
hashes=read(ROOT/'translation-state/weekly-20261021/before-hashes.json')
manifest=ROOT/'src/main/resources/defaults/upgrade-hashes.json'
existing=read(manifest) if manifest.exists() else {}
for file,value in hashes.items():
    existing[file]=','.join(sorted((set(existing.get(file,'').split(','))|{value})-{''}))
write(manifest,existing)
print(json.dumps({'system_rules':len(rules),'tab_labels':len(tab),'mining_events':len(events)}))
