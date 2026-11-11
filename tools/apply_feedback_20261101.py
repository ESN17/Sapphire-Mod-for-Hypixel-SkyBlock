"""Add reviewed screenshot rules; preserve existing translations and upgrade hashes."""
import json,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'src/main/resources/defaults'
def read(p):return json.loads(p.read_text(encoding='utf8'))
def encoded(d):return (json.dumps(d,ensure_ascii=False,indent=2)+'\n').encode()
hashes=read(BASE/'upgrade-hashes.json')
counts={}
def save(name,data):
    p=BASE/'translations'/name;old=p.read_bytes();new=encoded(data)
    if old==new:return
    backup=ROOT/'translation-state/feedback-20261101'/name
    backup.parent.mkdir(parents=True,exist_ok=True)
    if not backup.exists():backup.write_bytes(old)
    key='translations/'+name
    hashes[key]=','.join(sorted(set(filter(None,hashes.get(key,'').split(',')))|{hashlib.sha256(old).hexdigest()}))
    p.write_bytes(new)
    # Only update workspace copies which still equal the reviewed previous file.
    for root in (ROOT/'dictionary-workspace',ROOT/'run/config/sapphire'):
        copy=root/key
        if copy.exists() and copy.read_bytes()==old:copy.write_bytes(new)
def add(rows,s,t,styles=None,scope=None):
    if any(r['source']==([s] if scope is None else s) for r in rows):return
    r={'source':[s] if scope is None else s,'translation':t,'styleMappings':styles or {}}
    if scope is not None:r['scopes']=scope
    rows.append(r)

system=read(BASE/'translations/system-messages.json');r=system['rules'];before=len(r)
external={row['source'] for name in ('death-messages.json','creature-messages.json') for row in read(BASE/('translations/'+name))['rules'] if 'chat' in row['scopes']}
# Remove only the duplicate introduced by this patch, leaving the existing death rule intact.
r[:]=[row for row in r if not (row['source']=='SLAYER QUEST FAILED!' and row['translation']=='杀手任务失败!')]
def chat(s,t,styles=None):
    if s not in external:add(r,s,t,styles,['chat'])
for label,zh in [('RARE DROP!','稀有掉落!'),('VERY RARE DROP!','非常稀有掉落!'),('CRAZY RARE DROP!','极其稀有掉落!'),('INSANE DROP!','超级稀有掉落!')]:
    for wrap in ('{name}','({name})'):
        for ending in (' ({statIcon0} Magic Find)',''):
            chat(label+' '+wrap+ending,zh+' '+wrap+ending.replace('Magic Find','稀有物品概率加成'),{label:zh,'Magic Find':'稀有物品概率加成'})
chat('You earned {value} Event EXP from playing SkyBlock!','你通过游玩空岛生存获得了 {value} 活动经验!',{'Event EXP':'活动经验'})
chat('You earned {value} GEXP from playing SkyBlock!','你通过游玩空岛生存获得了 {value} 公会经验!',{'GEXP':'公会经验'})
chat('Your Slayer kill gave you {value} HP healing for {count} seconds!','击杀杀手任务怪物后,你获得了持续 {count} 秒的 {value} 点生命恢复!',{'HP':'生命'})
chat('SLAYER MINIBOSS! The {mob} spawned!','杀手任务精英怪! {mob} 出现了!',{'SLAYER MINIBOSS!':'杀手任务精英怪!'})
chat('+{count} Kill Combo {statIcon0} Magic Find','+{count} 连杀 {statIcon0} 稀有物品概率加成',{'Kill Combo':'连杀','Magic Find':'稀有物品概率加成'})
chat('+{count} Kill Combo {value} coins per kill','+{count} 连杀 每次击杀获得 {value} 硬币',{'Kill Combo':'连杀','coins':'硬币'})
for word in ('items.','item.'):
    chat('[Sacks] {value} '+word+' (Last {time}.)','[收纳袋] 物品变动: {value} (最近 {time}.)',{'[Sacks]':'[收纳袋]'})
chat('You cast {count} votes for {player} in the Year {year} Elections!','你在第 {year} 年的选举中向 {player} 投了 {count} 票!',{'votes':'票'})
chat('{player} now has {value} of votes with {count} votes!','{player} 目前获得 {count} 票,占总票数的 {value}!',{'votes':'票'})
chat('Attaché Fame Rank +{count} votes','随员名望等级 +{count} 票',{'Attaché':'随员','Fame Rank':'名望等级','votes':'票'})
chat("Supreme Voter's Badge +{count} votes",'至尊选民徽章 +{count} 票',{"Supreme Voter's Badge":'至尊选民徽章','votes':'票'})
for s,t in {
 'DUNGEON STARTS IN {count} SECONDS!':'地牢将在 {count} 秒后开始!',
 'The dungeon starts in {count} seconds.':'地牢将在 {count} 秒后开始。',
 '{player} is now ready!':'{player} 已准备就绪!',
 '{player} opened a WITHER door!':'{player} 打开了一扇凋灵之门!',
 '{player} opened a BLOOD door!':'{player} 打开了血门!',
 '{player} obtained a Wither Key!':'{player} 获得了一把凋灵钥匙!',
 '{player} obtained a Blood Key!':'{player} 获得了一把血钥匙!',
 '{player} found a Wither Key!':'{player} 找到了一把凋灵钥匙!',
 '{player} found a Blood Key!':'{player} 找到了一把血钥匙!',
 'A Wither Key was picked up!':'已拾取凋灵钥匙!',
 'A Blood Key was picked up!':'已拾取血钥匙!',
 'The BLOOD DOOR has been opened!':'血门已开启!',
 'The Watcher has finished spawning mobs!':'守望者已召唤完所有怪物!',
 'You found a secret!':'你发现了一处秘密!',
 '{player} found a secret!':'{player} 发现了一处秘密!',
 '{player} found a Blessing of Power {roman}!':'{player} 找到了力量祝福 {roman}!',
 '{player} found a Blessing of Wisdom {roman}!':'{player} 找到了智慧祝福 {roman}!',
 '{player} found a Blessing of Life {roman}!':'{player} 找到了生命祝福 {roman}!',
 '{player} found a Blessing of Stone {roman}!':'{player} 找到了磐石祝福 {roman}!',
 '{player} found a Blessing of Time {roman}!':'{player} 找到了时间祝福 {roman}!',
 'PUZZLE SOLVED! {player} tied Tic Tac Toe! Good job!':'谜题已解开! {player} 在井字棋中打成平局! 干得好!',
 'PUZZLE FAIL! {player} lost Tic Tac Toe! Yikes!':'谜题失败! {player} 输掉了井字棋!',
 'EXTRA STATS':'额外统计', 'DUNGEON CLEARED!':'地牢已通关!',
 'Team Score: {value}':'队伍得分: {value}', 'Secrets Found: {value}':'已发现秘密: {value}',
 'Time Elapsed: {time}':'用时: {time}', 'You are not allowed to leave this area!':'你不能离开这片区域!'
}.items():chat(s,t)
chat('The Seraph recalled your {count} summoned allies!','炽天使收回了你的 {count} 个召唤盟友!')
chat('The Seraph recalled your {count} summoned ally!','炽天使收回了你的 {count} 个召唤盟友!')
for flare in ('SOS_FLARE','ALERT_FLARE','WARNING_FLARE'):
    item=read(BASE/('translations/items/'+flare+'.json'));en=item['_original_name'];zh=item['name']
    chat('Your previous '+en+' was removed!','你先前放置的'+zh+'已被移除!',{en:zh})
chat('Your flare disappeared because you were too far away!','你的信号弹因距离过远而消失了!')
chat('Your flare has expired!','你的信号弹已到期!')
chat('You have spawned your {mob} soul! ({value} Mana)','你召唤了{mob}之魂! (消耗 {value} 魔力)',{'Mana':'魔力'})
chat('You have despawned your {mob} soul!','你收回了{mob}之魂!')
for end in ('','!','.'):
    chat('LOOT SHARE You received loot for assisting {player}'+end,'战利品共享 你因协助 {player} 获得了战利品'+end,{'LOOT SHARE':'战利品共享'})
for s,t in {'SLAYER QUEST STARTED!':'杀手任务已开始!','SLAYER QUEST COMPLETE!':'杀手任务已完成!','SLAYER QUEST FAILED!':'杀手任务失败!','SLAYER QUEST CANCELLED!':'杀手任务已取消!','SLAYER BOSS SLAIN!':'杀手任务首领已被击杀!','NICE! SLAYER BOSS SLAIN!':'干得好! 杀手任务首领已被击杀!','Your Slayer Quest has been cancelled!':'你的杀手任务已取消!','You already have a Slayer Quest active!':'你已有进行中的杀手任务!','You do not have an active Slayer Quest!':'你没有进行中的杀手任务!','Talk to Maddox to claim your Slayer XP!':'与 Maddox 交谈以领取杀手经验!','Your Slayer boss has despawned!':'你的杀手任务首领已消失!','Your Slayer boss was slain by {player}!':'你的杀手任务首领被 {player} 击杀了!','You cannot start another Slayer Quest yet!':'你暂时无法开始另一个杀手任务!','You have reached the maximum level for this Slayer!':'你已达到此杀手任务的最高等级!'}.items():chat(s,t)
for mob,plural,zh in [('Zombie','Zombies','僵尸'),('Spider','Spiders','蜘蛛'),('Wolf','Wolves','狼'),('Enderman','Endermen','末影人'),('Blaze','Blazes','烈焰人'),('Vampire','Vampires','吸血鬼')]:
    for prefix in ('','» '):
        chat(prefix+'Slay {value} Combat XP worth of '+plural+'.',prefix+'击杀'+zh+'以获得 {value} 战斗经验。',{'Combat XP':'战斗经验',plural:zh})
    chat(mob+' Slayer LVL {level} - Next LVL in {value} XP!',zh+'杀手等级 {level} - 距离下一级还需 {value} 经验!',{mob+' Slayer':zh+'杀手','XP':'经验'})
    chat(mob+' Slayer LVL {level} - MAXED!',zh+'杀手等级 {level} - 已满级!',{mob+' Slayer':zh+'杀手','MAXED':'已满级'})
    chat('+{value} '+mob+' Slayer XP','+{value} '+zh+'杀手经验',{mob+' Slayer XP':zh+'杀手经验'})
counts['chat']=len(r)-before;save('system-messages.json',system)

gui=read(BASE/'translations/gui-menus.json')
def menu(id,titles):
    found=next((m for m in gui['menus'] if m['id']==id),None)
    if found is None:found={'id':id,'titles':titles,'loreParagraphs':[],'labels':{}};gui['menus'].append(found)
    return found['loreParagraphs']
tree=menu('heart-trees',{'Heart of the Mountain':'山峦之心','Heart of the Forest':'森林之心'})
labels={'Mining Master':'采矿大师','Mining Speed':'挖掘速度','Mining Fortune':'挖掘时运','Pristine':'提纯','Mining Speed Boost':'挖掘速度提升','Pickobulus':'镐爆术','Maniac Miner':'疯狂矿工','Gemstone Infusion':'宝石灌注','Powder Buff':'粉末增幅','Great Explorer':'大探险家','Efficient Miner':'高效矿工','Quick Forge':'快速锻造','Peak of the Mountain':'山巅之心','Core of the Mountain':'山峦核心','Center of the Forest':'森林核心','Forest Strength':'森林力量','Forest Speed':'森林速度','Foraging Fortune':'采伐时运','Foraging Wisdom':'采伐智慧','Hunting Fortune':'狩猎时运','Hunting Wisdom':'狩猎智慧','Sweep':'横扫','Foraging Speed':'采伐速度','Daily Powder':'每日粉末','Professional':'专业矿工','Mole':'鼹鼠','Seasoned Mineman':'经验丰富的矿工','Fortunate':'幸运矿工','Crystallized':'结晶化','Titanium Insanium':'疯狂秘钛','Sky Mall':'空岛商城','Mining Madness':'采矿狂热','Lonesome Miner':'孤独矿工','Vein Seeker':'矿脉搜寻者','Daily Grind':'每日苦练','Whispers':'低语','Cost':'花费','ENABLED':'已启用','DISABLED':'已禁用','LOCKED':'未解锁','MAXED':'已满级','=====[ UPGRADE ]=====':'=====[ 升级 ]====='}
labels.update({"Luck of the Forest":'森林之幸','Collector':'收藏家','Deep Waters':'深水领域',"Hunter’s Luck":'猎人之幸',"Hunter's Luck":'猎人之幸',"Galatea's Might":'加拉蒂亚之力','Iron Lungs':'铁肺','250 Gifts':'二百五十份礼物','Daily Wishes':'每日心愿','Forest Fisher':'森林渔夫','Strength Boost':'力量提升','Starlyn Supreme':'星辉至尊','Speed Boost':'速度提升','Efficient Forager':'高效伐木工','Half Empty':'半空','Half Full':'半满','Ricochet':'弹射','Essence Fortune':'精华时运','Timber':'木材','Two-for-one':'一举两得','Axe Toss':'投斧','Axe Throwing':'掷斧','Treecapitator':'树木终结者'})
for s,t in labels.items():add(tree,s,t,{s:t})
for s,t in {'Level {value}/{max}':'等级 {value}/{max}','Level {value}':'等级 {value}','Powder Spent: {value}/{max} ({percent})':'已消耗粉末: {value}/{max} ({percent})','Right-click to disable!':'右键点击禁用!','Right-click to enable!':'右键点击启用!','Click to upgrade!':'点击升级!','Click to unlock!':'点击解锁!','Click to select!':'点击选择!'}.items():add(tree,s,t)
for en,zh in [('Mithril Powder','秘银粉末'),('Gemstone Powder','宝石粉末'),('Glacite Powder','冰川粉末'),('Forest Whispers','森林低语'),('Desert Whispers','沙漠低语'),('Token of the Mountain','山峦之心代币'),('Token of the Forest','森林之心代币')]:
    add(tree,'{value} '+en,'{value} '+zh,{en:zh})
    add(tree,"You don't have enough "+en+'!', '你的'+zh+'不足!',{en:zh})
for en,zh in labels.items():
    for val in ('{value}','{statIcon0}'):
        add(tree,'Grants '+val+' '+en+'.','提供 '+val+' '+zh+'.',{en:zh})
widgets=menu('widgets',{'Widgets':'信息组件','Widget Settings':'信息组件设置','Widgets in {location}':'{location}的信息组件','Tablist Settings':'玩家列表设置'})
for s,t in {'Currently: ENABLED':'当前: 已启用','Currently: DISABLED':'当前: 已禁用','Left-click to disable this widget!':'左键点击禁用此组件!','Left-click to enable this widget!':'左键点击启用此组件!','Right-click to change settings!':'右键点击更改设置!','Shows your stats when in the Dwarven Mines.':'在矮人矿山显示你的属性。'}.items():add(widgets,s,t)
for en,zh in [('Stats','属性'),('Skills','技能'),('Profile','档案'),('Pet','宠物'),('Collection','收集'),('Collections','收集'),('Quests','任务'),('Commissions','委托'),('Events','活动'),('Election','选举'),('Essence','精华'),('Players','玩家'),('Server','服务器'),('Area','区域'),('Dungeon','地牢'),('Slayer','杀手任务'),('Fishing','钓鱼'),('Farming','农业')]:
    for prefix in ('','✔ ','✓ ','✖ ','✘ '):add(widgets,prefix+en+' Widget',prefix+zh+'组件',{en+' Widget':zh+'组件'})
    for area,place in [('Dwarven Mines','矮人矿山'),('Crystal Hollows','水晶洞窟'),('Hub','中心岛'),('The Park','公园'),('Galatea','加拉蒂亚')]:add(widgets,'Shows your '+en.lower()+' when in the '+area+'.','在'+place+'显示你的'+zh+'。')
slayer=menu('slayer-live',{'Slayer':'杀手任务','Slayer Quests':'杀手任务','Maddox the Slayer':'Maddox 杀手任务','Inferno Demonlord':'炼狱恶魔领主','Revenant Horror':'亡魂恐魔','Tarantula Broodfather':'狼蛛育母','Sven Packmaster':'斯文狼王','Voidgloom Seraph':'虚空炽天使'})
for s,t in {'Health: {statIcon0}':'生命值: {statIcon0}','Damage: {value} per second':'伤害: 每秒 {value}','DDR Apocalypse':'末日舞步','You already have a Slayer Quest active! Cancel it first!':'你已有进行中的杀手任务! 请先取消当前任务!','Match the attunement on your dagger to damage the boss.':'切换匕首的协调属性,使其与首领一致才能造成伤害。','Deals {a}/{b}/{c} True Damage at {d}/{e}/{f} Boss Health respectively every second.':'首领生命值分别为 {d}/{e}/{f} 时,每秒造成 {a}/{b}/{c} 点真实伤害。'}.items():add(slayer,s,t,{'True Damage':'真实伤害','Boss Health':'首领生命值'})
for en,zh in [('Blaze','烈焰人'),('Zombie','僵尸'),('Spider','蜘蛛'),('Wolf','狼'),('Enderman','末影人'),('Vampire','吸血鬼')]:add(slayer,'Reward: {value} '+en+' Slayer XP','奖励: {value} '+zh+'杀手经验',{en+' Slayer XP':zh+'杀手经验'})

slayer[:]=[r for r in slayer if r['source']!=['Health: {value}{icon}']]
gui['loreParagraphs']=[r for r in gui['loreParagraphs'] if r['source']!=['Health: {value}{icon}']]
for row in slayer:
    if not any(r['source']==row['source'] for r in gui['loreParagraphs']):gui['loreParagraphs'].append(row)
for m in gui['menus']:
    m['loreParagraphs']=[r for r in m['loreParagraphs'] if r['source']!=['Health: {value}{icon}']]
    if m['id']=='widgets':
        for en,zh in [('Dwarven Mines','矮人矿山'),('Crystal Hollows','水晶洞窟'),('Hub','中心岛'),('Galatea','加拉蒂亚')]:m['titles']['Widgets in '+en]=zh+'的信息组件'
save('gui-menus.json',gui)
tips=read(BASE/'translations/tooltip-paragraphs.json');tr=tips['loreParagraphs']
add(tr,'Ability: Mining Speed Boost RIGHT CLICK','技能: 挖掘速度提升 右键点击',{'Ability':'技能','Mining Speed Boost':'挖掘速度提升','RIGHT CLICK':'右键点击'})
add(tr,'Grants {statIcon0} Mining Speed for {value}s.','获得 {statIcon0} 挖掘速度,持续 {value}秒。',{'Mining Speed':'挖掘速度'})
save('tooltip-paragraphs.json',tips)
(BASE/'upgrade-hashes.json').write_bytes(encoded(hashes))
print(counts)
