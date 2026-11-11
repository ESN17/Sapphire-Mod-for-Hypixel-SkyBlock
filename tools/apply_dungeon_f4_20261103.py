"""Add reviewed F4 observations without changing existing translations or live capture."""
import hashlib
import json
import re
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'src/main/resources/defaults'
STATE=ROOT/'translation-state/dungeon-f4-20261103'
def read(key):return json.loads((BASE/key).read_text(encoding='utf8'))
changed=[]
def write(key,data):
    path=BASE/key;before=path.read_bytes()
    if json.loads(before)==data:return
    backup=STATE/key;backup.parent.mkdir(parents=True,exist_ok=True)
    if not backup.exists():backup.write_bytes(before)
    hashes=read('upgrade-hashes.json')
    hashes[key]=','.join(sorted(set(filter(None,hashes.get(key,'').split(',')))|{hashlib.sha256(before).hexdigest()}))
    (BASE/'upgrade-hashes.json').write_text(json.dumps(hashes,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    after=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode();path.write_bytes(after)
    for root in [ROOT/'dictionary-workspace',ROOT/'run/config/sapphire']:
        mirror=root/key
        if mirror.exists() and mirror.read_bytes()==before:mirror.write_bytes(after)
    changed.append(key)
def pairs(text):return dict(l.split('|',1) for l in text.strip().splitlines())

data=read('translations/system-messages.json');rules=data['rules'];added=[]
previous=STATE/'rules.json'
if previous.exists():
    owned=json.loads(previous.read_text(encoding='utf8'));rules[:]=[r for r in rules if r not in owned]
def rule(source,target,scopes='chat',styles=None):
    scopes=[s for s in scopes.split(',') if not any(r['source']==source and s in r['scopes'] for r in rules)]
    if not scopes:return
    variables=set(re.findall(r'\{[^{}]+\}',source))
    styles={k:v for k,v in (styles or {}).items() if set(re.findall(r'\{[^{}]+\}',k))<=variables}
    if source.count('!')==1 and target.count('！')==1:styles.setdefault('!','！')
    row=dict(source=source,translation=target,scopes=scopes,styleMappings=styles)
    rules.append(row);added.append(row)

labels=read('translations/entity-labels.json')
names=read('translations/entity-names.json')
terms=read('translations/tooltip-labels.json')
catalog={}
for path in (BASE/'translations/items').glob('*.json'):
    item=json.loads(path.read_text(encoding='utf8'))
    if item.get('_original_name') and item.get('name'):
        catalog.setdefault(re.sub('§.','',item['_original_name']),re.sub('§.','',item['name']))

# Existing reviewed/attributed boss wording is reused for the overhead dialogue.
coverage=json.loads((STATE/'before-coverage.json').read_text(encoding='utf8'))
for row in coverage:
    if row['scope']!='chat' or not row['changed']:continue
    original=re.sub('§.','',row['original']).strip();current=re.sub('§.','',row['current']).strip()
    prefix='[BOSS] Thorn: '
    translated_prefix=re.match(r'\[BOSS\] Thorn[:：]\s*',current)
    if original.startswith(prefix) and translated_prefix:
        labels.setdefault(original[len(prefix):],current[translated_prefix.end():])
for npc,source,target in [
    ('Thorn','This is it... where shall I go now?','到此为止了……如今我该去往何方？'),
    ('The Watcher','Aw, I liked that one.','唉，我还挺喜欢那家伙的。')]:
    rule('[BOSS] '+npc+': '+source,'[BOSS] '+npc+': '+target)
    labels.setdefault(source,target)

crowd=pairs('''Let's go Thorn! They don't stand a chance!|加油，Thorn！他们毫无胜算！
Vegans! Yeahhh!!! Now let's exploit some animals for personal gain! Woo woo!|素食主义万岁！耶！！！现在让我们利用动物来谋取私利吧！呜呼！
Here we go Thorn! Take it to 'em!|上吧，Thorn！给他们点颜色看看！
That's not a legal move!|这招犯规了！
I see they've figured out how to damage Thorn. Finally.|他们总算弄明白怎么伤到 Thorn 了。
Alright those humans are a joke, missing easy shots like that...|这些人类真是笑话，这么简单的一箭都能射偏……
Turn them into spirits!|把他们变成灵魂！
BOOOOO!!!|嘘——！！！
Go Thorn! Gooooo!!!|上啊，Thorn！加油！！！
{player} has no thumbs!|{player} 连拇指都没有吗！
{player} missed the shot! No way!! Hahaha|{player} 射偏了！不会吧！！哈哈哈！
My goodness, {player} really can't aim!!|我的天，{player} 真的不会瞄准！！
Keep fighting Thorn! Show them!!|继续战斗，Thorn！让他们见识你的厉害！！
Thorn! Thorn! Thorn!|Thorn！Thorn！Thorn！
Yeah!!! Keep dodging them Thorn!|好耶！！！继续躲开他们的攻击，Thorn！
That wasn't fair!! He can't fight back!!|这不公平！！他都没法还手！！
Cheaters!!! We need a replay!!|作弊！！！我们要求重赛！！
My disappointment is immeasurable and my day is ruined.|我失望透顶，今天的好心情全毁了。
Make it a best of {rounds}, best of {roundsAgain}!!!!|改为 {rounds} 局制，{roundsAgain} 局制！！！！
There was a bug...rematch!!!|刚才出漏洞了……重赛！！！''')
for source,target in crowd.items():
    rule('[CROWD] {mob}: '+source,'[观众] {mob}: '+target,styles={'[CROWD]':'[观众]'})

statue=pairs('''I am Oruo the Omniscient. I have lived many lives. I have learned all there is to know.|我是全知者 Oruo。我经历过许多世，已通晓世间万物。
Though I sit stationary in this prison that is The Catacombs, my knowledge knows no bounds.|虽然我被困在地下墓穴这座牢笼中，无法动弹，但我的学识无穷无尽。
Prove your knowledge by answering {count} questions and I shall reward you in ways that transcend time!|回答 {count} 个问题，证明你的学识，我将赐予你超越时间的奖赏！
Answer incorrectly, and your moment of ineptitude will live on for generations.|若是答错，你这一刻的无知将被后世铭记。
{player} answered Question #{number} correctly!|{player} 正确回答了第 {number} 题！
{count} questions left... Then you will have proven your worth to me!|还剩 {count} 个问题……答完便能向我证明你的价值！
One more question!|还剩最后一个问题！
{player} answered the final question correctly!|{player} 正确回答了最后一题！
I bestow upon you all the power of a hundred years!|我将百年之力赐予你们！''')
for source,target in statue.items():
    rule('[STATUE] Oruo the Omniscient: '+source,'[雕像] Oruo the Omniscient: '+target,styles={'[STATUE]':'[雕像]','The Catacombs':'地下墓穴'})
questions=pairs('''Which of these is not a dragon in The End?|以下哪一种不是末地的龙？
How many unique minions are there?|一共有多少种不同的仆从？
How many Fairy Souls are there in Hub?|中心岛有多少个仙女之魂？''')
for source,target in questions.items():
    rule(source,target,'chat,entity');labels.setdefault(source,target)
rule('Question #{number}','第 {number} 题','entity')
for en,zh in [('Minions','仆从'),('Fairy Souls','仙女之魂')]:
    for suffix in ['', '.']:
        rule('{icon1} {count} '+en+suffix,'{icon1} {count} '+zh+suffix,'chat,entity',{en:zh})
for en,zh in {'Superior Dragon':names.get('Superior Dragon','优越之龙'),'Young Dragon':names.get('Young Dragon','幼年之龙'),'Stonk Dragon':'暴富之龙'}.items():
    for suffix in ['', '.']:
        rule('{icon1} '+en+suffix,'{icon1} '+zh+suffix,'chat,entity',{en:zh})

spirit=catalog.get('Spirit Bow','灵魂之弓')
for source,target in [
    ('You picked up the Spirit Bow! Use it to attack Thorn!','你拾取了'+spirit+'！用它攻击 Thorn！'),
    ('The Spirit Bow has dropped!',spirit+'已掉落！'),
    ('{player} picked up the Spirit Bow!','{player} 拾取了'+spirit+'！')]:
    rule(source,target,'chat,subtitle',{'Spirit Bow':spirit})
labels.setdefault('Spirit Bow',spirit)
for en in ['Spirit Shortbow','Grand Machine Gun Shortbow','Superboom TNT']:
    if en not in catalog:continue
    rule(en,catalog[en],'chat,entity')
    rule(en+' x{count}',catalog[en]+' x{count}','entity', {en:catalog[en]})
    rule('a '+en+'  a','a '+catalog[en]+'  a','entity',{en:catalog[en]})
base=catalog.get('Machine Gun Shortbow');reforge=read('translations/reforges.json').get('Grand')
if base and reforge:
    rule('Grand Machine Gun Shortbow',reforge+base,'entity',{'Grand':reforge,'Machine Gun Shortbow':base})
for source,target in pairs('''The Arrow Trap hit you for {value} damage!|箭矢陷阱对你造成了 {value} 点伤害！
The Spirit Chicken's lightning struck you for {value} damage.|灵魂鸡的闪电对你造成了 {value} 点伤害。
A Spirit Sheep exploded, hitting you for {value} damage.|灵魂羊爆炸，对你造成了 {value} 点伤害。
A Chicken Mine exploded, hitting you for {value} damage.|鸡地雷爆炸，对你造成了 {value} 点伤害。
This menu is disabled here!|此处无法使用这个菜单！
Your {name} leveled up to level {level}!|你的 {name} 升到了 {level} 级！''').items():rule(source,target)
names.setdefault('Lonely Spider','孤独蜘蛛');names.setdefault('Cellar Spider','地窖蜘蛛')
for en,zh in [('Gold','黄金'),('Ice','冰')]:
    original=en+' Essence';name=catalog.get(original,zh+'精华');terms.setdefault(original,name)
    rule(original+' x{count}',name+' x{count}','entity,item-name,item-tooltip,gui-tooltip,chat',{original:name})
    rule('+{value} '+original,'+{value} '+name,'actionbar',{original:name})
    rule('+{value} '+original+'     {count}/{total} Secrets','+{value} '+name+'     {count}/{total} 秘密','actionbar',{original:name,'Secrets':'秘密'})
for en,zh in [('Ice Path','冰路谜题'),('Quiz','知识问答')]:rule(en+': [{icon1}]',zh+': [{icon1}]','tablist',{en:zh})
terms.setdefault('Blessing of Time','时间祝福');labels.setdefault('Blessing of Time','时间祝福')
rule('Blessing of Time {roman}','时间祝福 {roman}','tablist,entity',{'Blessing of Time':'时间祝福'})
for tail in ['', ' ({time})']:
    rule('DUNGEON BUFF! You found a Blessing of Time {roman}!'+tail,'地牢增益！你找到了时间祝福 {roman}！'+tail,styles={'DUNGEON BUFF!':'地牢增益！','Blessing of Time':'时间祝福'})
rule('Granted you +{hp} & +{hpMult}x HP, +{defense} & +{defMult}x {icon1} Defense, +{intelligence} & +{intMult}x {icon2} Intelligence, and +{strength} & +{strMult}x {icon3} Strength.',
     '获得 +{hp} 与 +{hpMult} 倍生命值、+{defense} 与 +{defMult} 倍 {icon1} 防御、+{intelligence} 与 +{intMult} 倍 {icon2} 智力，以及 +{strength} 与 +{strMult} 倍 {icon3} 力量。',
     styles={'HP':'生命值','Defense':'防御','Intelligence':'智力','Strength':'力量',**{'+{'+v+'}x':'+{'+v+'} 倍' for v in ['hpMult','defMult','intMult','strMult']}})

paragraphs=read('translations/tooltip-paragraphs.json')
def paragraph(source,target,styles=None):
    if not any(' '.join(r['source'])==source for r in paragraphs['loreParagraphs']):
        paragraphs['loreParagraphs'].append({'source':[source],'translation':target,'styleMappings':styles or {}})
paragraph('Grants {statIcon1} Attack Speed, {statIcon2} Speed and {multiplier}x melee damage increase for {seconds} seconds. Also summons {count} Zombie minions to aid you in battle!',
          '提供 {statIcon1} 攻击速度、{statIcon2} 速度和 {multiplier} 倍近战伤害加成，持续 {seconds} 秒，并召唤 {count} 只僵尸仆从助你战斗！',
          {'Attack Speed':'攻击速度','Speed':'速度','{multiplier}x':'{multiplier} 倍','melee damage':'近战伤害'})
feather=catalog.get('Kismet Feather','天运羽毛')
paragraph('Consume a Kismet Feather to reroll the loot in this chest.','消耗一根'+feather+'，重新随机生成此宝箱的战利品。',{'Kismet Feather':feather})
paragraph('You may only use a feather once per run/chest where applicable.','在允许使用的场景中，每次地牢探索或每个宝箱只能使用一根羽毛。')
rule('Bring a Kismet Feather','需要携带'+feather,'item-tooltip,gui-tooltip',{'Kismet Feather':feather})
rule('Cooldown: {minutes} minutes','冷却时间：{minutes} 分钟','item-tooltip',{'Cooldown':'冷却时间','minutes':'分钟'})
rule('Mana Cost: {statIcon1} ({value}ᛃ)','魔力消耗：{statIcon1} ({value}ᛃ)','item-tooltip',{'Mana Cost':'魔力消耗'})
rule('+{statIcon1} Intelligence','+{statIcon1} 智力','item-tooltip',{'Intelligence':'智力'})

write('translations/system-messages.json',data)
write('translations/tooltip-paragraphs.json',paragraphs)
write('translations/entity-labels.json',labels)
write('translations/entity-names.json',names)
write('translations/tooltip-labels.json',terms)
(STATE/'rules.json').write_text(json.dumps(added,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps({'rulesAdded':len(added),'changed':changed},ensure_ascii=False))
