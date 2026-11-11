"""Reviewed F7/M7 capture additions. Does not publish or package dictionaries."""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT/'src/main/resources/defaults'
STATE = ROOT/'translation-state/dungeon-f7-m7-20261104'
def read(key): return json.loads((BASE/key).read_text(encoding='utf8'))
def pairs(text): return dict(line.split('|', 1) for line in text.strip().splitlines())
def write(key, data):
    path=BASE/key; before=path.read_bytes()
    if json.loads(before)==data: return
    backup=STATE/key; backup.parent.mkdir(parents=True,exist_ok=True)
    if not backup.exists(): backup.write_bytes(before)
    hashes=read('upgrade-hashes.json')
    hashes[key]=','.join(sorted(set(filter(None,hashes.get(key,'').split(',')))|{hashlib.sha256(before).hexdigest()}))
    (BASE/'upgrade-hashes.json').write_text(json.dumps(hashes,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    after=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode()
    path.write_bytes(after)
    for root in [ROOT/'dictionary-workspace',ROOT/'run/config/sapphire']:
        mirror=root/key
        if mirror.exists() and mirror.read_bytes()==before: mirror.write_bytes(after)

data=read('translations/system-messages.json'); rules=data['rules']; added=[]
owned=STATE/'rules.json'
if owned.exists():
    old=json.loads(owned.read_text(encoding='utf8')); rules[:]=[r for r in rules if r not in old]
def rule(source,target,scopes='chat',styles=None):
    scopes=[s for s in scopes.split(',') if not any(r['source']==source and s in r['scopes'] for r in rules)]
    if not scopes:return
    row=dict(source=source,translation=target,scopes=scopes,styleMappings=dict(styles or {}))
    if source.count('!')==target.count('！')==1:row['styleMappings'].setdefault('!','！')
    rules.append(row);added.append(row)
labels=read('translations/entity-labels.json')
coverage=json.loads((STATE/'before.json').read_text(encoding='utf8'))
for row in coverage:
    if row['scope']!='chat' or not row['changed']:continue
    a=re.match(r'\[BOSS\] [^:]+: (.+)',re.sub('§.','',row['original']).strip())
    b=re.match(r'\[BOSS\] [^:：]+[:：]\s*(.+)',re.sub('§.','',row['current']).strip())
    if a and b:labels.setdefault(a[1],b[1])

dialogue=pairs('''WELL! WELL! WELL! LOOK WHO'S HERE!|哟！哟！哟！看看谁来了！
Don't boast about beating this simple-minded Wither.|打败那个头脑简单的凋灵，没什么好炫耀的。
BEGONE PILLAR!|柱子，给我消失！
Little ants, plotting and scheming, thinking they are invincible...|小小蝼蚁，机关算尽，还以为自己天下无敌……
I won't let you break the factory core, I gave my life to my Master.|我不会让你们摧毁工厂核心，我已将生命献给了主人。
The little ants have a brain it seems.|看来这些小蚂蚁还算有点脑子。
I will replace that gate with a stronger one!|我会换上一扇更坚固的大门！
YOUR END IS NEAR!!|你们的末日就要到了！！
Come closer!|再靠近点！
You have done it, you destroyed the factory...|你们做到了，你们摧毁了工厂……
Let's make some space!|来腾出点地方吧！
You... again?|又是……你们？
I no longer wish to fight, but I know that will not stop you.|我已无意战斗，但我知道这阻止不了你们。
We will decide it all, here, now.|就在此时，此地，了结一切吧。
You just made a terrible mistake!|你们犯下了一个可怕的错误！
Your skills have faded humans.|人类，你们的本领退步了。
My enemies are now my strongest allies!|昔日的敌人，如今成了我最强大的盟友！
My soul is disposable.|我的灵魂，舍弃也罢。
Storm was my favorite sparring partner, you must have enjoyed fighting him!|Storm 曾是我最喜欢的切磋对手，想必你们也很享受与他交手！
Oh, this one hurts!|啊，这下真疼！
This world isn't worth fighting for, how can you be so determined?|这个世界不值得为之而战，你们为何如此坚定？
Incredible. You did what I couldn't do myself.|不可思议。你们做到了连我自己都无法做到的事。
In a way I should thank you, I lost all hopes centuries ago that it would ever end.|某种意义上，我该感谢你们。几百年前，我就不再奢望这一切会结束。
I hope you'll become the Heroes I could never be.|希望你们能成为我未能成为的英雄。
So long champions of this mad world!|永别了，这个疯狂世界的勇士们！
My strengths are depleting, this... this is it...|我的力量正在枯竭，这……就是终点了……
If I had spent more time studying and less time watching anime, maybe mother would be here with me!|要是我当初多花点时间学习，少看点动画，也许母亲就能在这里陪着我了！
Do you really think we won't repair everything? Your impact will be minuscule!|你们真以为我们不会修复这一切？你们造成的影响微不足道！
I am not impressed.|这可打动不了我。
I have more of those.|这样的手下，我还有很多。
Futile.|徒劳无功。
Some of you do have some talent, I can feel it, the other ones not so much.|我能感觉到，你们之中有些人确有天赋，其他人就差远了。
I enjoy your passion, if the circumstances had been different, we could have been friends.|我欣赏你们的热忱。若非身处此境，我们本可以成为朋友。
A Wandering Soul? Poor guy.|一个游荡的灵魂？可怜的家伙。
Oof|唉。
Does that mean Necron is no more? I have been a mediocre master.|这是否意味着 Necron 已不复存在？我真是个平庸的师父。''')
for row in coverage:
    if row['scope']!='chat':continue
    source=re.sub('§.','',row['original']).strip()
    match=re.match(r'(\[BOSS\] [^:]+: )(.+)',source)
    if match and match[2] in dialogue:
        target=dialogue[match[2]]
        rule(source,match[1]+target,styles={match[1].strip():match[1].strip()})
        labels.setdefault(match[2],target)
rule('[BOSS] The Watcher: Welcome back to the realm of the living {player}.','[BOSS] The Watcher: 欢迎重返生者的世界，{player}。')

for source,target in pairs('''You became a ghost!|你变成了幽灵！
Hopefully your teammates will be able to revive you!|希望你的队友能够将你复活！
Someone else is currently reviving that player!|已有其他人正在复活这名玩家！
BEING REVIVED|正在复活
The Energy Laser is charging up!|能量激光正在充能！
The gate has been destroyed!|大门已被摧毁！
A mystical force in this room prevents you from using that ability!|房间内的神秘力量阻止了你使用该技能！
Someone has already activated this lever!|这个拉杆已被启动！
The Core entrance is opening!|核心入口正在开启！
This Terminal has already been completed!|这个终端已完成！
A mystical force prevents you from leaving the inner chamber!|神秘力量阻止了你离开内室！
Ragnarock was cancelled due to taking damage!|受到伤害，诸神黄昏已被打断！''').items():rule(source,target,'chat,title,subtitle')
for source,target in pairs('''You have teleported to {player}!|你已传送至 {player}！
❣ {player} is reviving {prefix}!|❣ {player} 正在复活 {prefix}！
❣ {player} was revived by The Watcher!|❣ {player} 被 The Watcher 复活了！
☠ {player} disconnected and became a ghost.|☠ {player} 断开了连接，变成了幽灵。
{player}'s Wish healed you for {health} health and granted you an absorption shield with {shield} health!|{player} 的祈愿为你恢复了 {health} 点生命，并提供了可吸收 {shield} 点伤害的护盾！
{player} formed a tether with you!|{player} 与你建立了生命链接！
{player} cast Cells Alignment on you!|{player} 对你施放了细胞对齐！
A Bat has been slain. +{count} Bonus Score|一只蝙蝠被击杀了。+{count} 额外评分
{player} picked up an Energy Crystal!|{player} 拾取了一颗能量水晶！
You are now Event Level {level}!|你的活动等级已达到 {level} 级！
You earned {count} Event Silver!|你获得了 {count} 活动银币！
You found a journal named {name}!|你找到了一本名为《{name}》的日记！''').items():rule(source,target,styles={'Wish':'祈愿','Energy Crystal':'能量水晶','Event Level':'活动等级','Event Silver':'活动银币','Cells Alignment':'细胞对齐'})
for source,target in pairs('''You will be revived in {time}|你将在 {time} 后复活
{count}/{total} Energy Crystals are now active!|已激活 {count}/{total} 颗能量水晶！
The gate will open in {seconds} seconds!|大门将在 {seconds} 秒后开启！
{player} activated a terminal! ({count}/{total})|{player} 激活了终端！({count}/{total})
{player} activated a lever! ({count}/{total})|{player} 启动了拉杆！({count}/{total})
{player} completed a device! ({count}/{total})|{player} 完成了装置！({count}/{total})
⚠ {prefix} is enraged! ⚠|⚠ {prefix} 被激怒了！⚠''').items():rule(source,target,'chat,subtitle',{'Energy Crystals':'能量水晶','terminal':'终端','lever':'拉杆','device':'装置'})
for en,zh in [('Implosion','聚爆'),('Spirit Pet','灵魂宠物')]:
    for noun in ['enemy','enemies']:
        rule('Your '+en+' hit {count} '+noun+' for {damage} damage.','你的'+zh+'命中了 {count} 个敌人，造成了 {damage} 点伤害。',styles={en:zh})
for en,zh in [('Damage','伤害'),('Defense','防御'),('Ability Damage','技能伤害')]:
    rule('◕ {player} picked up your '+en+' Orb!','◕ {player} 拾取了你的'+zh+'宝珠！',styles={en+' Orb':zh+'宝珠'})
for en,zh in [('Lightning Fireball','闪电火球'),('Static Field','静电场'),('Frenzy','狂暴'),('TNT Trap','炸药陷阱'),('Giga Lightning','巨型闪电'),('Damage Potion','伤害药水'),('Dimensional Slash','次元斩'),('Atomizing Ray','湮灭射线')]:
    for damage,cn in [('damage','伤害'),('true damage','真实伤害')]:
        rule("{prefix}'s "+en+' hit you for {damage} '+damage+'.','{prefix} 的'+zh+'对你造成了 {damage} 点'+cn+'。',styles={en:zh,damage:cn})
for en,zh in [('Power','力量'),('Stone','磐石'),('Life','生命'),('Wisdom','智慧'),('Time','时间')]:
    rule('DUNGEON BUFF! {player} found a Blessing of '+en+' {roman}! ({time})','地牢增益！{player} 找到了'+zh+'祝福 {roman}！({time})',styles={'DUNGEON BUFF!':'地牢增益！','Blessing of '+en:zh+'祝福'})
rule('ESSENCE! {player} found x{count} {name}!','精华！{player} 找到了 {count} 个 {name}！',styles={'ESSENCE!':'精华！'})
rule('{player} found a Wither Essence! Everyone gains an extra essence!','{player} 找到了凋灵精华！每个人额外获得一个精华！',styles={'Wither Essence':'凋灵精华'})
for en,zh in [('Archer','弓箭手'),('Mage','法师'),('Healer','治疗师'),('Berserk','狂战士'),('Tank','坦克')]:
    rule('+{xp} '+en+' Experience (Team Bonus)','+{xp} '+zh+'经验（团队加成）',styles={en+' Experience':zh+'经验','(Team Bonus)':'（团队加成）'})
for en,zh in [('Red','红色'),('Orange','橙色'),('Green','绿色'),('Blue','蓝色'),('Purple','紫色')]:
    rule('{player} picked the Corrupted '+en+' Relic!','{player} 拾取了腐化'+zh+'遗物！',styles={'Corrupted '+en+' Relic':'腐化'+zh+'遗物'})
    rule(en.upper()+' IS SPAWNING',zh+'巨龙即将出现','title')
rule('Master Mode The Catacombs - Floor {roman}','大师模式 地下墓穴 - 第 {roman} 层','chat,gui')
for letter in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ':rule("What starts with: '"+letter+"'?","选择英文名称以 '"+letter+"' 开头的物品",'gui')
for en,zh in [('WHITE','白色'),('ORANGE','橙色'),('MAGENTA','品红色'),('LIGHT BLUE','淡蓝色'),('YELLOW','黄色'),('LIME','黄绿色'),('PINK','粉色'),('GRAY','灰色'),('LIGHT GRAY','淡灰色'),('CYAN','青色'),('PURPLE','紫色'),('BLUE','蓝色'),('BROWN','棕色'),('GREEN','绿色'),('RED','红色'),('BLACK','黑色')]:
    rule('Select all the '+en+' items!','选择所有'+zh+'物品！','gui')
rule('{prefix} (DEAD)','{prefix}（死亡）','tablist',{'DEAD':'死亡'})
rule('Revive: {time}','复活：{time}','tablist')
for en,zh in [('Ice Fill','冰填谜题'),('Boulder','推石谜题')]:
    for icon in ['✦','✔','✖','✘','?']:
        rule(en+': ['+icon+']',zh+'：['+icon+']','tablist')
for en,zh in pairs('''Energy Crystal|能量水晶
Inactive Terminal|未激活的终端
Not Activated|尚未激活
Activated|已激活
Terminal Active|终端已激活
Inactive|未激活
Device|装置
Active|已激活
Energy Crystal Missing|缺少能量水晶
Crystal Active|水晶已激活
DEAD|死亡
CLICK HERE|点击这里''').items():labels.setdefault(en,zh)

# Action bar includes a variable defense counter between skill gains and secrets.
counter='{value}/{max}'
for prefix,translated,styles in [('', '', {}),('T{tier}     ','T{tier}     ',{}),('T{tier}!     ','T{tier}!     ',{})]+[
    ('+{xp} '+en+' Essence     ','+{xp} '+zh+'精华     ',{en+' Essence':zh+'精华'}) for en,zh in [('Diamond','钻石'),('Gold','金'),('Ice','冰'),('Dragon','巨龙'),('Undead','亡灵'),('Wither','凋灵'),('Spider','蜘蛛')]
]+[(f'+{{xp}} {en} ({{progress}}/{{cap}})     ',f'+{{xp}} {zh} ({{progress}}/{{cap}})     ',{en:zh}) for en,zh in [('Combat','战斗'),('Hunting','狩猎')]]+[(f'+{{xp}} {en} ({{progress}})     ',f'+{{xp}} {zh} ({{progress}})     ',{en:zh}) for en,zh in [('Combat','战斗'),('Hunting','狩猎')]]:
    rule(prefix+counter+'     {count}/{total} Secrets',translated+counter+'     {count}/{total} 秘密','actionbar',dict(styles,Secrets='秘密'))
    if prefix and not prefix.startswith('T'):rule(prefix+counter,translated+counter,'actionbar',styles)
for bang in ['','!']:
    rule('T{tier}'+bang+'     CASTING IN {time}','T{tier}'+bang+'     将在 {time} 后施放','actionbar')
    rule('T{tier}'+bang+'     CASTING','T{tier}'+bang+'     正在施放','actionbar')

rule('CASTING IN {time}','将在 {time} 后施放','actionbar')
rule('CASTING','正在施放','actionbar')
rule('Bedrock','基岩宝箱','gui')
rule("Don't move diagonally! Bad!",'不要斜着走！违规！')
rule('❣ {player} was revived by Genevieve the Fairy!','❣ {player} 被仙女 Genevieve 复活了！',styles={'Genevieve the Fairy':'仙女 Genevieve'})
rule('You found a journal named Grim Adversity!','你找到了一本名为《严峻逆境》的日记！',styles={'Grim Adversity':'严峻逆境'})
rule('{prefix} entered MM The Catacombs, Floor {roman}!','{prefix} 进入了大师模式地下墓穴第 {roman} 层！',styles={'MM The Catacombs':'大师模式地下墓穴'})
rule('The party was transferred to {prefix} because {player} left','由于 {player} 离开了队伍，队长已转交给 {prefix}')
rule('RUNE! You found a rune!','符文！你找到了一枚符文！',styles={'RUNE!':'符文！'})
rule('Talk to Dusk in the Mining District to learn about runes! [UNDERSTOOD]','与矿区的 Dusk 交谈以了解符文！[明白了]',styles={'Dusk':'Dusk','Mining District':'矿区','[UNDERSTOOD]':'[明白了]'})
labels.setdefault('JERRY! RUN!','Jerry！快跑！')
for tail in ['', '     {count}/{total} Secrets']:
    rule('+{xp} Runecrafting ({progress}/{cap})     '+counter+tail,
         '+{xp} 符文制作 ({progress}/{cap})     '+counter+tail.replace('Secrets','秘密'),'actionbar',{'Runecrafting':'符文制作','Secrets':'秘密'})

# The captured Weirdos color the possessive suffix together with the NPC name.
# Add style hints to existing reviewed wording, without replacing translations.
for row in rules:
    if "{player}'s chest" in row['source'] and "[NPC] {prefix}:" in row['source']:
        row.setdefault('styleMappings',{}).setdefault("'s",'的')

# Oruo displays the same questions and answers as overhead labels as well as chat.
for row in coverage:
    if row['scope']!='entity':continue
    source=re.sub('§.','',row['original']).strip()
    for candidate in rules[:]:
        if candidate['source']==source.rstrip('.') and 'chat' in candidate['scopes'] and ('?' in source or source.startswith(('ⓐ','ⓑ','ⓒ'))):
            target=candidate['translation']+('。' if source.endswith('.') else '')
            rule(source,target,'entity',candidate.get('styleMappings'))

paragraphs=read('translations/tooltip-paragraphs.json')
def paragraph(source,target,styles=None):
    if not any(' '.join(r['source'])==source for r in paragraphs['loreParagraphs']):
        paragraphs['loreParagraphs'].append(dict(source=[source],translation=target,styleMappings=styles or {}))
paragraph('Blows up cracked brick walls and crypts, which are typically found in Dungeons and the Crystal Hollows.',
          '炸开裂纹砖墙和墓穴；它们通常位于地牢和水晶洞窟。',{'Dungeons':'地牢','Crystal Hollows':'水晶洞窟'})
paragraph('This item can be reclaimed! Use /reclaim to take back what\'s rightfully yours!',
          '此物品可重新领取！使用 /reclaim 取回属于你的物品！',{'/reclaim':'/reclaim'})
paragraph('Consume a Kismet Feather to reroll the loot in this chest. You may only use a feather once per run/chest where applicable.',
          '消耗一根命运之羽，重新随机生成此宝箱中的战利品。每次地牢探索或每个适用宝箱只能使用一次。',{'Kismet Feather':'命运之羽'})
paragraph('This item is a preview of your currently selected arrow.','此物品用于预览你当前选择的箭矢。')
paragraph('Shows the layout of the Dungeon as it is explored and completed.','随着探索和房间完成情况，显示地牢的布局。')
for en,zh in [('Wither Impact','凋零冲击'),('Spirit Leap','灵魂跃迁'),('Big Heal','高级治疗'),('Howl','嚎叫'),('Pickobulus','镐爆术')]:
    for prefix in ['', '⦾ ']:
        rule(prefix+'Ability: '+en+'  RIGHT CLICK',prefix+'技能：'+zh+'  右键点击','item-tooltip,gui-tooltip',{'Ability':'技能',en:zh,'RIGHT CLICK':'右键点击'})
for source,target in pairs('''Passive: Bouncy!|被动：弹跳！
Stats added when shot!|属性在射出时生效！
RARE ARROW|稀有箭矢
Arrows Remaining: {count}|剩余箭矢：{count}
Selected: {catalogItem}|已选择：{catalogItem}''').items():rule(source,target,'item-tooltip,gui-tooltip',{'Bouncy':'弹跳','RARE':'稀有','ARROW':'箭矢'})
paragraph('Arrows have a {chance} chance to bounce to another target.','箭矢有 {chance} 的概率弹跳至另一目标。')
paragraph('Bounces up to {count} targets, each bounce dealing {reduction} less damage.','最多弹跳至 {count} 个目标，每次弹跳造成的伤害减少 {reduction}。')
paragraph('Throw your pickaxe to create an explosion mining all ores in a {radius} block radius.','投掷你的镐，产生爆炸并挖掘半径 {radius} 格内的所有矿石。')

write('translations/system-messages.json',data)
write('translations/entity-labels.json',labels)
write('translations/tooltip-paragraphs.json',paragraphs)
owned.write_text(json.dumps(added,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps({'rulesAdded':len(added)},ensure_ascii=False))
