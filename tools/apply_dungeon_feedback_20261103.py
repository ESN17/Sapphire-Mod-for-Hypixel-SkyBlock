"""Reviewed F1-F3 capture. Additive dictionaries; every measured value is a variable."""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'src/main/resources/defaults'
STATE = ROOT / 'translation-state/dungeon-feedback-20261103'
changes = []

def read(key):
    return json.loads((BASE / key).read_text(encoding='utf8'))

def write(key, data):
    path = BASE / key
    before = path.read_bytes()
    if json.loads(before) == data:
        return
    backup = STATE / key
    backup.parent.mkdir(parents=True, exist_ok=True)
    if not backup.exists():
        backup.write_bytes(before)
    hashes = read('upgrade-hashes.json')
    hashes[key] = ','.join(sorted(set(filter(None, hashes.get(key, '').split(','))) | {hashlib.sha256(before).hexdigest()}))
    (BASE / 'upgrade-hashes.json').write_text(json.dumps(hashes, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    after = (json.dumps(data, ensure_ascii=False, indent=2)+'\n').encode()
    path.write_bytes(after)
    for root in (ROOT/'dictionary-workspace', ROOT/'run/config/sapphire'):
        mirror = root/key
        if mirror.exists() and mirror.read_bytes() == before:
            mirror.write_bytes(after)
    changes.append(key)

data = read('translations/system-messages.json')
rules = data['rules']
previous = STATE/'rules.json'
if previous.exists():
    owned=json.loads(previous.read_text(encoding='utf8'))
    rules[:]=[r for r in rules if r not in owned]
added = []

def rule(source, target, scopes='chat', styles=None):
    scopes = scopes.split(',')
    # Existing reviewed rules remain authoritative.
    missing = [s for s in scopes if not any(r['source'] == source and s in r['scopes'] for r in rules)]
    if not missing:
        return
    variables=set(re.findall(r'\{[^{}]+\}',source))
    styles={k:v for k,v in (styles or {}).items() if set(re.findall(r'\{[^{}]+\}',k)) <= variables}
    if source.count('!')==1 and target.count('！')==1:
        styles.setdefault('!','！')
    row = dict(source=source, translation=target, scopes=missing, styleMappings=styles)
    rules.append(row)
    added.append(row)

def pairs(text):
    return dict(line.split('|', 1) for line in text.strip().splitlines())

classes = {'Archer':'弓箭手','Healer':'治疗师','Mage':'法师','Tank':'坦克','Berserk':'狂战士'}
skills = {'Rapid Fire':'连射','Explosive Shot':'爆炸射击','Wish':'祈愿','Healing Circle':'治疗法阵','Thunderstorm':'雷暴','Guided Sheep':'制导羊','Seismic Wave':'地震波','Castle of Stone':'磐石城堡','Throwing Axe':'投掷斧','Ragnarok':'诸神黄昏'}
ultimates={'Archer':'Rapid Fire','Healer':'Wish','Mage':'Thunderstorm','Tank':'Castle of Stone','Berserk':'Ragnarok'}
for en, zh in skills.items():
    rule(en+' is ready to use! Press DROP to activate it!', zh+'已就绪！按丢弃键释放！', styles={en:zh,'DROP':'丢弃键'})
    rule(en+' is now available!', zh+'已就绪！', styles={en:zh})
    rule('Used '+en+'!', '已使用'+zh+'！', styles={en:zh})
for en, zh in classes.items():
    skill=ultimates[en];skillzh=skills[skill]
    rule('Your '+en+' ULTIMATE '+skill+' is now available!', '你的'+zh+'终极技能'+skillzh+'已就绪！', styles={en:zh,'ULTIMATE':'终极技能',skill:skillzh})
    tail, zhtail = ('You have done a total of {value} healing and damage so far!', '累计治疗与伤害：{value}！') if en=='Healer' else ('You have dealt {value} '+('Ranged' if en=='Archer' else 'Total')+' Damage so far!', '累计'+('远程' if en=='Archer' else '总')+'伤害：{value}！')
    if en=='Tank':
        tail,zhtail='You have tanked and dealt {value} Total Damage so far!','累计承受与造成伤害：{value}！'
    for circle in '❶❷❸❹❺❻❼❽❾':
        for timed in [False, True]:
            rule(en+' Milestone '+circle+': '+tail+(' {time}' if timed else ''), zh+'里程碑 '+circle+'：'+zhtail+(' {time}' if timed else ''), styles={en:zh,'Milestone':'里程碑'})
    rule('+{value} '+en+' Experience', '+{value} '+zh+'经验', styles={en:zh,'Experience':'经验'})
    rule('Selected class: '+en, '所选职业：'+zh, 'gui-tooltip', {'Selected class':'所选职业',en:zh})
    rule('[{level}] {player} ('+en+' {roman})', '[{level}] {player} ('+zh+' {roman})', 'tablist', {en:zh})

for source, target in pairs('''Your bone plating reduced the damage you took by {value}!|你的骨甲减少了 {value} 点所受伤害！
Your Wish healed you for {value} health and granted you an absorption shield with {shield} health!|你的祈愿为你恢复了 {value} 点生命，并提供了 {shield} 点伤害吸收护盾！
Your Wish healed your entire team for {value} health and shielded them for {shield}!|你的祈愿为全队恢复了 {value} 点生命，并提供了 {shield} 点护盾！
Your Thunderstorm hit {count} enemies for {value} damage.|你的雷暴命中了 {count} 个敌人，造成 {value} 点伤害。
The Tripwire Trap hit you for {value} damage!|绊线陷阱对你造成了 {value} 点伤害！
The Stormy Tank Zombie struck you for {value} damage!|风暴坦克僵尸对你造成了 {value} 点伤害！
{rankedPlayer} has obtained {name}!|{rankedPlayer} 获得了 {name}！
RIGHT CLICK on a WITHER door to open it. This key can only be used to open {count} door!|右键点击凋灵之门即可打开。这把钥匙只能打开 {count} 扇门！
PUZZLE SOLVED! {player} wasn't fooled by {name}! Good job!|解谜成功！{player} 没有被 {name} 骗到！干得好！
PUZZLE SOLVED! {player} tied Tic Tac Toe! Good job!|解谜成功！{player} 在井字棋中取得平局！干得好！
Your Auto Recombobulator recombobulated {name}!|你的自动重组器重组了 {name}！
ESSENCE! You found x{count} {name}!|精华！你找到了 {count} 个 {name}！
{rankedPlayer} just found a {name}!|{rankedPlayer} 刚刚找到了 {name}！
RARE REWARD! {name}|稀有奖励！{name}
Warning! The instance will close in {time}.|注意！此副本将在 {time} 后关闭。
The Catacombs - Floor {roman}|地下墓穴 - 第 {roman} 层
Team Score: {value} ({text})|队伍评分：{value} ({text})
Team Score: {value} ({text}) (NEW RECORD!)|队伍评分：{value} ({text})（新纪录！）
☠ Defeated {name} in {time}|☠ 击败 {name}，用时 {time}
OBSIDIAN CHEST REWARDS|黑曜石宝箱奖励
RNG METER! Reselected the {catalogItem} for Catacombs - Floor {floor}! CLICK HERE to select a new drop!|祈运条！已重新选定 {catalogItem} 为地下墓穴第 {floor} 层的目标掉落！点击这里选择新的掉落！''').items():
    rule(source,target,styles={'Wish':'祈愿','Thunderstorm':'雷暴','Auto Recombobulator':'自动重组器','ESSENCE!':'精华！','RARE REWARD!':'稀有奖励！','RNG METER!':'祈运条！','CLICK HERE':'点击这里','NEW RECORD!':'新纪录！','Catacombs - Floor {floor}':'地下墓穴第 {floor} 层'})

for en,zh in {'Ability Damage':'技能伤害','Damage':'伤害','Defense':'防御'}.items():
    rule('◕ You picked up a '+en+' Orb from {player} healing you for {value}❤ and granting you +{bonus} '+en+' for {seconds} seconds.', '◕ 你拾取了 {player} 的'+zh+'光球，恢复 {value}❤，并获得 +{bonus} '+zh+'，持续 {seconds} 秒。', styles={en:zh,en+' Orb':zh+'光球'})

blessings={'Power':'力量','Life':'生命','Stone':'磐石','Wisdom':'智慧'}
for en,zh in blessings.items():
    phrase='Blessing of '+en
    name=zh+'祝福'
    for time in ['', ' ({time})']:
        rule('DUNGEON BUFF! You found a '+phrase+' {roman}!'+time, '地牢增益！你找到了'+name+' {roman}！'+time, styles={'DUNGEON BUFF!':'地牢增益！',phrase:name})
        rule('DUNGEON BUFF! A '+phrase+' {roman} was found!'+time, '地牢增益！发现了'+name+' {roman}！'+time, styles={'DUNGEON BUFF!':'地牢增益！',phrase:name})
    rule('A '+phrase+' was picked up!', name+'已被拾取！', styles={phrase:name})
    rule(phrase+' {roman}',name+' {roman}','tablist,gui-tooltip,entity', {phrase:name})
    rule(phrase,name,'gui-tooltip,entity')
for source,target in pairs('''Granted you +{value} & +{multiplier}x {icon1} Strength.|获得 +{value} 与 +{multiplier} 倍 {icon1} 力量。
Also granted you +{value} & +{multiplier}x {icon1} Crit Damage.|同时获得 +{value} 与 +{multiplier} 倍 {icon1} 暴击伤害。
Granted you +{value}x HP and +{regen}x {icon1} Health Regen.|获得 +{value} 倍生命值与 +{regen} 倍 {icon1} 生命恢复。
Granted you +{value} & +{multiplier}x {icon1} Defense and +{damage} {icon2} Damage.|获得 +{value} 与 +{multiplier} 倍 {icon1} 防御，以及 +{damage} {icon2} 伤害。
Granted you +{value} & +{multiplier}x {icon1} Intelligence and +{speed} {icon2} Speed.|获得 +{value} 与 +{multiplier} 倍 {icon1} 智力，以及 +{speed} {icon2} 速度。''').items():
    rule(source,target,styles={'Strength':'力量','Crit Damage':'暴击伤害','HP':'生命值','Health Regen':'生命恢复','Defense':'防御','Damage':'伤害','Intelligence':'智力','Speed':'速度','+{multiplier}x':'+{multiplier} 倍','+{value}x':'+{value} 倍','+{regen}x':'+{regen} 倍'})

# Dialogue is restricted to the captured speaker in chat, and exact hologram lines.
dialogue = {
'Mort': pairs('''You should find it useful if you get lost.|迷路时它应该能帮上忙。
Good luck.|祝你好运。'''),
'The Watcher': pairs('''Ah, we meet again. As I foresaw...|啊，我们又见面了。正如我所预见的……
Hmmm... this one!|嗯……就这个！
This guy looks like a fighter.|这家伙看起来很能打。
Let's see how you can handle this.|让我看看你如何应付这个。
You'll do.|就你了。
That will be enough for now.|暂时就这些吧。
You have proven yourself. You may pass.|你们已证明了自己的实力。可以通过了。
Go, fight!|去吧，战斗！
Go and live again!|去吧，重获新生！
I'm impressed.|真令我刮目相看。'''),
'Bonzo': pairs('''Gratz for making it this far, but I'm basically unbeatable.|恭喜你们走到这里，不过我可是近乎无敌的。
I don't even need to fight, this is the life!|都不用我亲自动手，这才叫享受！
I can summon lots of undead! Check this out.|我能召唤大批亡灵！看好了。
Oh noes, you got me.. what ever will I do?!|哎呀，你们打败我了……这可怎么办啊？！
Oh I'm dead!|啊，我死了！
Sike|骗你们的！
Alright, maybe I'm just weak after all..|好吧，也许我确实太弱了……
But my masters are a lot stronger..|但我的主人们可强得多……
Just you wait...|你们等着吧……'''),
'Scarf': pairs('''This is where the journey ends for you, Adventurers.|冒险家们，你们的旅途到此为止了。
The last few who tried fighting me are now in those Crypts.|上几个挑战我的人，如今都躺在那些墓穴里。
If you can beat my Undeads, I'll personally grant you the privilege to replace them.|要是你们能打败我的亡灵，我会亲自赐予你们取代它们的殊荣。
ARISE, MY CREATIONS!|苏醒吧，我的造物！
This should be interesting.|这应该会很有趣。
I'll make you take the Mage's place!|我要让你取代我的法师！
I'll make you take the Archer's place!|我要让你取代我的弓箭手！
Darn! My Warrior!|可恶！我的战士！
Those toys are not strong enough I see.|看来那些玩具还不够强。
Don't get too excited though.|不过别高兴得太早。
Did you forget? I was taught by the best! Let's dance.|忘了吗？我的老师可是最强的！来较量一番吧。
Whatever...|随便吧……
You'll never beat my teacher..|你们绝不可能打败我的老师……
His technique.. is too advanced..|他的技艺……太高深了……'''),
'The Professor': pairs('''I was burdened with terrible news recently...|最近，我听到了一个令人痛心的消息……
My most talented student...|我最有天赋的学生……
I'll show you real power!|让你们见识真正的力量！
You took one of them down, but that won't be enough!|你们打倒了其中一个，但这还远远不够！
Oh? You found my Guardians' one weakness?|哦？你们找到了我的守卫者唯一的弱点？
Even if you took my barrier down, I can still fight.|即使你们击破了我的屏障，我依然能战斗。
This time I'll be your opponent!|这次，由我来做你们的对手！
I see. You have forced me to use my ultimate technique.|我明白了。是你们逼我使出终极秘技的。
These Guardians aren't just for show I'm afraid!|这些守卫者可不只是摆设！
The process is irreversible, but I'll be stronger than a Wither now!|这个过程不可逆转，但我将比凋灵更强大！
What?! My Guardian power is unbeatable!|什么？！我的守卫者之力是无敌的！
How could you... I needed more power...|你们怎么可能……我需要更强的力量……
I can't let my Master hear about this.. he'll kill m-|不能让我的主人知道这件事……他会杀了我——'''),
'Necron': pairs('''That is enough, fool!|够了，蠢货！
Adventurers! Be careful of who you are messing with..|冒险家们！看清楚你们招惹的是谁……
Before I have to deal with you myself.|别逼我亲自出手收拾你们。''')}
for npc in ['Hugo','Luverne']:
    dialogue[npc]={"The reward isn't in any of our chests.":'奖励不在我们任何一个人的箱子里。'}
dialogue['Hope']={"The reward is in my chest!":'奖励就在我的箱子里！'}
for npc in ['Baxter','Rose']:
    dialogue[npc]={"The reward is not in my chest. They are both lying.":'奖励不在我的箱子里。他们两个都在说谎。'}
labels=read('translations/entity-labels.json')
for npc, entries in dialogue.items():
    prefix='[NPC]' if npc in ['Mort','Hugo','Luverne','Hope','Baxter','Rose'] else '[BOSS]'
    for en,zh in entries.items():
        rule(prefix+' '+npc+': '+en,prefix+' '+npc+': '+zh)
        labels.setdefault(en,zh)
for en,zh in pairs('''MINIBOSS|小首领
BOSS|首领
ABILITY DAMAGE|技能伤害
DEFENSE|防御
DAMAGE|伤害
HELP!|救命！
What was that?|那是什么声音？
Thought I heard something...|好像听到了什么声音……
What?|什么？
I'm back, baby!|我又回来了！
I almost died last week!|我上周差点死了！
⚠ ANGER LEVEL ⚡ ⚠|⚠ 愤怒等级 ⚡ ⚠
Healing |治疗的 ''').items():
    labels.setdefault(en,zh)
for en,zh in [('ABILITY DAMAGE','技能伤害'),('DAMAGE','伤害'),('DEFENSE','防御')]:
    rule(en+' {value}',zh+' {value}','entity')
rule('⚠ CHARGING BEAM {value} ⚠','⚠ 光束充能 {value} ⚠','entity')
rule('Watch your back {player}!','{player}，小心身后！','entity')

# Keep item and enchantment names tied to the existing dictionaries.
catalog={}
for path in (BASE/'translations/items').glob('*.json'):
    row=json.loads(path.read_text(encoding='utf8'))
    if row.get('_original_name') and row.get('name'):
        catalog.setdefault(re.sub('§.','',row['_original_name']),re.sub('§.','',row['name']))
for en in ['Wither Key','Blood Key','Revive Stone','Superboom TNT','Premium Flesh','Adaptive Belt','Recombobulator 3000']:
    if en in catalog:
        labels.setdefault(en,catalog[en])
        rule(en,catalog[en],'chat,gui-tooltip,entity')
for en,zh in blessings.items():
    labels.setdefault('Blessing of '+en,zh+'祝福')
for essence,zh in {'Undead':'亡灵','Wither':'凋灵','Dragon':'巨龙','Spider':'蜘蛛','Diamond':'钻石'}.items():
    en=essence+' Essence';name=catalog.get(en,zh+'精华')
    rule(en+' x{count}', name+' x{count}','chat,gui-tooltip,entity')
    rule('+{value} '+en,'+{value} '+name,'actionbar', {en:name})
    rule('+{value} '+en+'     {count}/{total} Secrets','+{value} '+name+'     {count}/{total} 秘密','actionbar', {en:name,'Secrets':'秘密'})
rule('Enchanted Book ({name} {roman})','附魔书（{name} {roman}）','chat,gui-tooltip,entity', {'Enchanted Book':'附魔书'})
for en in ['Adaptive Belt','Recombobulator 3000']:
    if en in catalog:
        rule('a '+en+'  a','a '+catalog[en]+'  a','entity',{en:catalog[en]})

for source,target in pairs('''{count}/{total} Secrets|{count}/{total} 秘密
This ability is on cooldown for {time}.|此技能还需冷却 {time}。
+{value} Bits from Cookie Buff!|饼干增益：+{value} 比特！''').items():
    rule(source,target,'actionbar', {'Secrets':'秘密','Bits':'比特','Cookie Buff':'饼干增益'})
for en,zh in {'Combat':'战斗','Runecrafting':'符文','Hunting':'狩猎'}.items():
    for progress in ['({value}/{max})','({value})']:
        for secrets in ['', '     {count}/{total} Secrets']:
            rule('+{xp} '+en+' '+progress+secrets,'+{xp} '+zh+' '+progress+secrets.replace('Secrets','秘密'),'actionbar',{en:zh,'Secrets':'秘密'})
rule('T{tier}     {count}/{total} Secrets','T{tier}     {count}/{total} 秘密','actionbar', {'Secrets':'秘密'})
rule('T{tier}!     {count}/{total} Secrets','T{tier}!     {count}/{total} 秘密','actionbar', {'Secrets':'秘密'})
rule('{value} Score Reached!','评分达到 {value}！','title')
rule('Inventory full!','背包已满！','subtitle')

tab=read('translations/tablist.json')
for en,zh in pairs('''Ultimate:|终极技能:
Revive Stones:|复活石:
Player Stats|玩家统计
Downed: NONE|倒地玩家: 无
Downed:|倒地玩家:
Time: N/A|时间: 不适用
Revive: N/A|复活: 不适用
Team Deaths:|队伍死亡次数:
Team Damage Dealt:|队伍造成伤害:
Team Healing Done:|队伍治疗量:
Your Milestone:|你的里程碑:
Discoveries:|探索发现:
Secrets Found:|已找到的秘密:
Crypts:|墓穴:
Dungeon Stats|地牢统计
Dungeon: Catacombs|地牢: 地下墓穴
Opened Rooms:|已开启房间:
Completed Rooms:|已完成房间:
Time: Soon!|时间: 即将开始！
Time:|时间:
Account Info|账号信息
Pet Sitter: N/A|宠物保姆: 不适用
Dungeon Buffs|地牢增益''').items():
    tab.setdefault(en,zh)
rule('Party ({count})','队伍 ({count})','tablist')
rule('Puzzles: ({count})','谜题: ({count})','tablist')
rule('Ultimate: Ready','终极技能: 已就绪','tablist',{'Ultimate':'终极技能','Ready':'已就绪'})
for en,zh in {'Three Weirdos':'三个怪人','Teleport Maze':'传送迷宫','Creeper Beams':'苦力怕光束','Tic Tac Toe':'井字棋','Water Board':'水流解谜','Higher Or Lower':'高低判断'}.items():
    rule(en+': [{icon1}]',zh+': [{icon1}]','tablist',{en:zh})
for en,zh in {'Healthy':'健康守卫者','Reinforced':'强化守卫者','Laser':'激光守卫者','Chaos':'混沌守卫者'}.items():
    rule('- '+en+' {market}❤','- '+zh+' {market}❤','scoreboard',{en:zh})
rule('{icon1} The Catacombs (F{floor})','{icon1} 地下墓穴 (F{floor})','scoreboard',{'The Catacombs':'地下墓穴'})
rule('Cleared: {value} ({score})','已探索: {value} ({score})','scoreboard',{'Cleared':'已探索'})
sidebar=read('translations/scoreboard.json')
for en,zh in {'Keys:':'钥匙:','Time Elapsed:':'已用时间:','Solo':'单人'}.items():
    sidebar.setdefault(en,zh)

terms=read('translations/tooltip-labels.json')
for en,zh in {'Wither Key':'凋灵钥匙','Blood Key':'鲜血钥匙',**{'Blessing of k'.replace('k',k):v+'祝福' for k,v in blessings.items()}}.items():
    terms.setdefault(en,zh)
    labels.setdefault(en,zh)
for en,zh in read('translations/enchantments.json').items():
    # A plain enchantment name in a reward book has its level in a separate span.
    terms.setdefault(en,zh)
for en,zh in skills.items():
    terms.setdefault(en,zh)
    for button,cn in [('LEFT CLICK','左键点击'),('RIGHT CLICK','右键点击')]:
        for gap in [' ', '  ']:
            rule('Ability: '+en+gap+button,'技能：'+zh+gap+cn,'gui-tooltip',{'Ability':'技能',en:zh,button:cn})
    for plural in ['enemy','enemies']:
        rule('Your '+en+' hit {count} '+plural+' for {value} damage.','你的'+zh+'命中了 {count} 个敌人，造成 {value} 点伤害。',styles={en:zh})

for en,zh in [('ABILITY DAMAGE','技能伤害'),('DAMAGE','伤害'),('DEFENSE','防御')]:
    rule(en+' {time}',zh+' {time}','entity')
rule('⚠ CHARGING BEAM {time} ⚠','⚠ 光束充能 {time} ⚠','entity')
entitynames=read('translations/entity-names.json')
entitynames.setdefault('Crypt Souleater','墓穴噬魂者')
entitynames.setdefault('Undead Skeleton','亡灵骷髅')
for singular in ['Shard','Shards']:
    # Mob and shard are different names (e.g. Zombie Knight drops Zombie Soldier Shards).
    rule('CHARM! You charmed the {mob} and received {count} {name} '+singular+'!', '魅惑成功！你魅惑了 {mob}，获得 {count} 个 {name} 碎片！',styles={'CHARM!':'魅惑成功！',singular:'碎片'})
for item in ['Tank Zombie Shard','Zombie Soldier Shard','Skeletor Shard']:
    if item in catalog:
        for suffix in ['', 's']:
            rule('CHARM! You charmed the {mob} and received {count} '+item+suffix+'!', '魅惑成功！你魅惑了 {mob}，获得 {count} 个'+catalog[item]+'！',styles={'CHARM!':'魅惑成功！',item+suffix:catalog[item]})
rule('You sold {catalogItem} x{count} for {coins} Coin!','你卖出了 {count} 个 {catalogItem}，获得 {coins} 硬币！',styles={'Coin':'硬币'})
for currency in ['Coin','Coins']:
    rule('You sold Healing {roman} Splash Potion x{count} for {coins} '+currency+'!','你卖出了 {count} 瓶喷溅型治疗药水 {roman}，获得 {coins} 硬币！',styles={'Healing':'治疗','Splash Potion':'喷溅型药水',currency:'硬币'})
for suffix in ['', ' x{count}']:
    rule('Healing {roman} Splash Potion'+suffix,'喷溅型治疗药水 {roman}'+suffix,'gui-tooltip',{'Healing':'治疗','Splash Potion':'喷溅型药水'})
rule('Healing {roman} ({time})','治疗 {roman} ({time})','gui-tooltip',{'Healing':'治疗'})
rule('Heals for +{value} every second.','每秒恢复 +{value} 点生命。','gui-tooltip')
for en,zh in {'Sell Price':'出售价格','Click to sell!':'点击出售！'}.items():
    rule(en,zh,'gui-tooltip')
for unit in ['Coin','Coins']:
    rule('{value} '+unit,'{value} 硬币','gui-tooltip',{unit:'硬币'})
paragraph_hint=('Applicable on: Held Item, Precursor Eye','适用于：手持物品、先驱之眼')
# Reforge is a separately identifiable prefix; do not fabricate unknown item names.
base=catalog.get('Zombie Soldier Leggings')
reforge=read('translations/reforges.json').get('Heavy')
if base and reforge:
    name=reforge+base
    rule('Your Auto Recombobulator recombobulated Heavy Zombie Soldier Leggings!','你的自动重组器重组了'+name+'！',styles={'Auto Recombobulator':'自动重组器','Heavy':reforge,'Zombie Soldier Leggings':base})
for en,zh in {'Foraging':'采集','Combat':'战斗','Hunting':'狩猎'}.items():
    rule('Skills: '+en+' {level}: {value}','技能: '+zh+' {level}: {value}','tablist',{'Skills':'技能',en:zh})
    rule('Skills: '+en+' {level}: MAX','技能: '+zh+' {level}: 满级','tablist',{'Skills':'技能',en:zh,'MAX':'满级'})
rule('Interest: {hours} Hours','利息: {hours} 小时','tablist',{'Interest':'利息','Hours':'小时'})
rule('You have {count} active effects. Use "/effects" to see them!','你有 {count} 个生效中的效果。使用 "/effects" 查看！','tablist')
rule('Haste {roman}','急迫 {roman}','tablist',{'Haste':'急迫'})
rule('Event: Starlyn Contests','活动: Starlyn 竞赛','tablist',{'Event':'活动','Contests':'竞赛'})

# Shared paragraphs accept any wrapping and preserve numeric/color spans.
paragraphs=read('translations/tooltip-paragraphs.json')
def paragraph(source,target,styles=None):
    if not any(' '.join(r['source'])==source for r in paragraphs['loreParagraphs']):
        paragraphs['loreParagraphs'].append({'source':[source],'translation':target,'styleMappings':styles or {}})
paragraph(*paragraph_hint,{'Applicable on':'适用于','Held Item':'手持物品','Precursor Eye':'先驱之眼'})
for source,target in pairs('''Blows up cracked brick walls and crypts, which are typically found in Dungeons and the Crystal Hollows.|炸开地牢与水晶洞窟中常见的裂纹砖墙和墓穴。
Shows the layout of the Dungeon as it is explored and completed.|随探索进度显示地牢布局。
{value} of their maximum health. The cooldown is reduced by {seconds} seconds for every player below {threshold} health.|相当于最大生命值的 {value}。每有一名玩家生命值低于 {threshold}，冷却时间便减少 {seconds} 秒。
inside for {value} of their health every second.|每秒为范围内的玩家恢复 {value} 生命值。
Blows up cracked brick walls and crypts.|炸开裂纹砖墙与墓穴。''').items():
    paragraph(source,target,{'Dungeons':'地牢','Crystal Hollows':'水晶洞窟'})
for en,zh in [('Undead','亡灵'),('Wither','凋灵')]:
    paragraph(en+' Essence can be used to convert some items into Dungeon items and upgrade them!',zh+'精华可将部分物品转为地牢物品，并用于升级！',{en+' Essence':zh+'精华','Dungeon':'地牢'})

# Reuse existing, attributed Wiki tooltip prose after removing UI headers;
# keep only numeric templates when a paragraph contains measured values.
for file in (BASE/'translations/community-wiki').glob('*.json'):
    for row in json.loads(file.read_text(encoding='utf8')).get('entries',[]):
        source=row['source']; target=row['translation']
        if not any('Ability: '+s+' ' in source for s in skills):
            continue
        if 'While on Foraging Islands' in source:
            continue
        if re.search(r'(?<![A-Za-z])\d',re.sub(r'\{\{[^}]+\}\}', '', source)):
            continue
        # Existing corpus supplies exact game strings and attribution; add no guessed descriptions.
        paragraph(source,target,row.get('styleMappings'))
        # The orb uses LEFT CLICK for its ultimate, while class-menu descriptions
        # include a bullet and RIGHT CLICK. Match the description independently.
        match=re.search(r'CLICK (.+) Cooldown: (.+)$',source)
        translated=re.search(r'右键 (.+)冷却[:：]',target)
        if match and translated:
            paragraph(match[1],translated[1].strip(),row.get('styleMappings'))

for source,target in pairs('''Throw an Axe, doing the damage of your highest damage hit in the last minute.|投掷一把斧头，造成与你过去一分钟内最高单次命中相同的伤害。
Fortifies your defence, reducing the damage you take by {reduction} for {seconds} seconds. Also aggros all enemies in a {radius} block radius.|强化你的防御，使你在 {seconds} 秒内受到的伤害降低 {reduction}，并吸引半径 {radius} 格内的所有敌人。
Summon {count} Zombie minions to aid you in battle!|召唤 {count} 只僵尸仆从助你战斗！
A Seismic Wave emerges from underneath you and travels in a straight line.|一道地震波从你脚下涌出，沿直线前进。''').items():
    paragraph(source,target)
for increase in ['+{increase}', '+ {increase}']:
    for defense in ['{defense}', '+{defense}']:
        source='Deals {damage} '+increase+' every '+defense+' Defense in damage to any enemy in its path.'
        paragraph(source,'对路径上的敌人造成 {damage} 点伤害；每 '+defense+' 点防御使伤害提高 '+increase+'。',{'Defense':'防御'})

for row in list(added):
    if 'gui-tooltip' in row['scopes']:
        rule(row['source'],row['translation'],'item-tooltip',row['styleMappings'])
        if row['source'].startswith(('Enchanted Book (','Healing {roman} Splash Potion')) or any(row['source'].startswith(e+' Essence x') for e in ['Undead','Wither','Dragon','Spider','Diamond']):
            rule(row['source'],row['translation'],'item-name',row['styleMappings'])

write('translations/tooltip-paragraphs.json',paragraphs)
write('translations/tooltip-labels.json',terms)
write('translations/entity-names.json',entitynames)
write('translations/system-messages.json',data)
write('translations/entity-labels.json',labels)
write('translations/tablist.json',tab)
write('translations/scoreboard.json',sidebar)
STATE.mkdir(parents=True,exist_ok=True)
(STATE/'rules.json').write_text(json.dumps(added,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps({'newRules':len(added),'changedFiles':changes},ensure_ascii=False))

if __name__=='__main__':
    pass
