"""Reviewed public puzzle bank, Archer lore and split-hover Sacks fixes (additive)."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT/'src/main/resources/defaults'
STATE = ROOT/'translation-state/dungeon-supplement-20261103'
STATE.mkdir(parents=True, exist_ok=True)
def read(key): return json.loads((BASE/key).read_text(encoding='utf8'))
def write(key, data):
    path=BASE/key; before=path.read_bytes()
    if json.loads(before)==data: return
    backup=STATE/key; backup.parent.mkdir(parents=True, exist_ok=True)
    if not backup.exists(): backup.write_bytes(before)
    hashes=read('upgrade-hashes.json')
    hashes[key]=','.join(sorted(set(filter(None,hashes.get(key,'').split(',')))|{hashlib.sha256(before).hexdigest()}))
    (BASE/'upgrade-hashes.json').write_text(json.dumps(hashes,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    after=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode()
    path.write_bytes(after)
    for root in (ROOT/'dictionary-workspace',ROOT/'run/config/sapphire'):
        mirror=root/key
        if mirror.exists() and mirror.read_bytes()==before: mirror.write_bytes(after)

data=read('translations/system-messages.json'); rules=data['rules']; added=[]
def rule(source,target,scopes='chat',styles=None):
    missing=[s for s in scopes.split(',') if not any(r['source']==source and s in r['scopes'] for r in rules)]
    if not missing:return
    row=dict(source=source,translation=target,scopes=missing,styleMappings=styles or {})
    rules.append(row);added.append(row)

# Keep this run's fixture stable on a repeated invocation.
owned=STATE/'rules.json'
if owned.exists():
    prior=json.loads(owned.read_text(encoding='utf8'))
    rules[:]=[r for r in rules if r not in prior]

# Hover boundaries can separate the word from its comma/period. Retain the
# established translation, but map the word independently of punctuation.
for row in rules:
    if row['source'].startswith('[Sacks] '):
        for word in ('item','items'):
            if word+' ' in row['source'] or word+'.' in row['source'] or word+',' in row['source']:
                row.setdefault('styleMappings',{}).setdefault(word,'件物品')

weirdos='''The reward is not in my chest!|奖励不在我的箱子里!
One of us is telling the truth!|我们之中有一个人说的是真话!
They are both telling the truth. The reward isn't in {player}'s chest.|他们俩说的都是真话。奖励不在 {player} 的箱子里。
We are all telling the truth!|我们说的都是真话！
At least one of them is lying, and the reward is not in {player}'s chest.|他们俩至少有一个人在说谎，而且奖励不在 {player} 的箱子里。
{player} is telling the truth and the reward is in his chest.|{player} 说的是真话，奖励就在他的箱子里。
My chest doesn't have the reward. At least one of the others is telling the truth!|奖励不在我的箱子里。另外两人中，至少有一个说的是真话!
My chest doesn't have the reward we are all telling the truth.|奖励不在我的箱子里。我们说的都是真话。
My chest doesn't have the reward. We are all telling the truth.|奖励不在我的箱子里。我们说的都是真话。
One of the others is lying.|另外两人中有一个人在说谎。
My chest has the reward and I'm telling the truth!|奖励在我的箱子里,我说的可是真话!
They are both telling the truth, the reward is in {player}'s chest.|他们俩说的都是真话，奖励在 {player} 的箱子里。
They are both lying, the reward is in my chest!|他们俩都在说谎，奖励在我的箱子里！
The reward isn't in any of our chests.|奖励不在我们任何一个人的箱子里。
The reward is in my chest.|奖励在我的箱子里。
The reward is not in my chest. They are both lying.|奖励不在我的箱子里。他们俩都在说谎。
{player} is telling the truth.|{player} 说的是真话。
My chest has the reward.|奖励在我的箱子里。
Both of them are telling the truth. Also, {player} has the reward in their chest.|他们俩说的都是真话。另外，奖励在 {player} 的箱子里。'''
for line in weirdos.splitlines():
    source,target=line.split('|')
    rule('[NPC] {prefix}: '+source,'[NPC] {prefix}: '+target)
    rule(source,target,'entity-label')
    if source.startswith(('At least one','Both of them')):
        rule('[NPC] {prefix}: '+source[:-1]+'!','[NPC] {prefix}: '+target[:-1]+'！')

for npc in ('The Watcher','Bonzo','Scarf','The Professor','Thorn','Livid','Sadan','Maxor','Goldor','Storm','Necron','Maxor, Storm, Goldor, and Necron'):
    rule('What is the status of '+npc+'?',npc+' 的身份是什么？',styles={npc:npc})
questions='''Which brother is on the Spider's Den?|哪位兄弟在蜘蛛巢穴？
What is the name of Rick's brother?|Rick 的兄弟叫什么名字？
What is the name of the vendor in the Hub who sells stained glass?|中心岛出售染色玻璃的商人叫什么名字？
What is the name of the vendor in the Hub who sells stained|中心岛出售染色
glass?|玻璃的商人叫什么名字？
What is the name of the person that upgrades pets?|为宠物升级的人叫什么名字？
What is the name of the lady of the Nether?|下界女士叫什么名字？
Which villager in the Village gives you a Rogue Sword?|村庄里哪位村民会送你游侠剑？
How many unique minions are there?|共有多少种不同的仆从？
How many total Fairy Souls are there?|总共有多少个仙女之魂？
What SkyBlock year is it?|现在是 空岛 第几年?
Which of these enemies does not spawn in the Spider's Den?|以下哪种敌人不会在蜘蛛巢穴生成？
Which of these monsters only spawns at night?|以下哪种怪物只在夜晚生成？
Which of these is not a dragon in The End?|以下哪种龙不属于末地的龙？'''
for line in questions.splitlines(): rule(*line.split('|'))
locations=read('translations/locations.json')
for en,zh in [('Hub','中心岛'),('The Hub','中心岛'),("Spider's Den",'蜘蛛巢穴'),("the Spider's Den",'蜘蛛巢穴'),('The End','末地'),('The Farming Islands','农业群岛'),('Crimson Isle','绯红之岛'),('the Crimson Isle','绯红之岛'),('The Park','公园'),("Jerry's Workshop",'Jerry的工坊'),('Deep Caverns','深层矿洞'),('Gold Mine','金矿'),('Dungeon Hub','地牢大厅')]:
    rule('How many Fairy Souls are there in '+en+'?',locations.get(en,zh)+'有多少个仙女之魂？')
answers='''Stalker|窥视者
New Necromancer|新晋死灵法师
Apprentice Necromancer|见习死灵法师
Professor|教授
Shaman Necromancer|萨满死灵法师
Master Necromancer|死灵法师大师
Necromancer Lord|死灵领主
The Wither Lords|凋灵领主
Zombie Spider|僵尸蜘蛛
Cave Spider|洞穴蜘蛛
Wither Skeleton|凋灵骷髅
Dashing Spooder|疾行蜘蛛
Broodfather|育母蜘蛛
Night Spider|夜行蜘蛛
Zombie Villager|僵尸村民
Ghast|恶魂
Zombie Dragon|僵尸龙
Weak Dragon|虚弱龙
Stonk Dragon|股票龙
Holy Dragon|神圣龙
Boomer Dragon|老派龙
Booger Dragon|鼻涕龙
Older Dragon|年长龙
Elder Dragon|长老龙
Professor Dragon|教授龙
Zoomer Dragon|新潮龙
Stable Dragon|稳定龙'''
entities=read('translations/entity-names.json')
for line in answers.splitlines():
    en,zh=line.split('|');zh=entities.get(en,zh)
    if any(r['source']=='{icon1} '+en and 'chat' in r['scopes'] for r in rules):continue
    for symbol in 'ⓐⓑⓒ':rule(symbol+' '+en,symbol+' '+zh)
for symbol in 'ⓐⓑⓒ':
    for en,zh in [('Year {count}','第 {count} 年'),('{count} Minions','{count} 种仆从'),('{count} Fairy Souls','{count} 个仙女之魂')]:
        if not any(r['source']=='{icon1} '+en and 'chat' in r['scopes'] for r in rules):rule(symbol+' '+en,symbol+' '+zh)

# Confirmed server messages, with the player's complete original span copied.
rule('PUZZLE FAIL! {player} killed a Blaze in the wrong order! Yikes!',
     '谜题失败！{player} 击杀烈焰人的顺序错误！糟糕！',styles={'PUZZLE FAIL!':'谜题失败！','Blaze':'烈焰人','Yikes':'糟糕','!':'！'})
for ending in ('Yikes!','YIKES!'):
    rule('PUZZLE FAIL! {player} was fooled by {prefix}! '+ending,
         '谜题失败！{player} 被 {prefix} 骗了！糟糕！',styles={'PUZZLE FAIL!':'谜题失败！',ending[:-1]:'糟糕','!':'！'})
    if ending=='YIKES!':
        rule('PUZZLE FAIL! {player} killed a Blaze in the wrong order! '+ending,
             '谜题失败！{player} 击杀烈焰人的顺序错误！糟糕！',styles={'PUZZLE FAIL!':'谜题失败！','Blaze':'烈焰人',ending[:-1]:'糟糕','!':'！'})
for body,target in [
    ('Yikes!', '糟糕！'),
    ('{player} chose the wrong answer!', '{player} 选错了答案！'),
    ('Yikes! {player} chose the wrong answer!', '糟糕！{player} 选错了答案！'),
    ('I shall never forget this moment of misremembrance.', '我永远不会忘记这次记错答案的时刻。')]:
    rule('[STATUE] Oruo the Omniscient: '+body,'[雕像] Oruo the Omniscient: '+target,
         styles={'[STATUE]':'[雕像]','Oruo the Omniscient':'Oruo the Omniscient','chose the wrong answer':'选错了答案','Yikes':'糟糕','!':'！'})
for row in rules:
    if row['source']=='PUZZLE FAIL! {player} lost Tic Tac Toe! Yikes!':
        # Existing output dropped the colorful last word, making the entire
        # styled message fall back. Retain its sentence and translate the suffix.
        if row['translation']=='谜题失败! {player} 输掉了井字棋!':row['translation']+=' 糟糕!'
        row.setdefault('styleMappings',{}).update({'PUZZLE FAIL':'谜题失败','Tic Tac Toe':'井字棋','Yikes':'糟糕'})

paragraphs=read('translations/tooltip-paragraphs.json')
def paragraph(source,target,styles=None):
    if not any(' '.join(r['source'])==source for r in paragraphs['loreParagraphs']):
        paragraphs['loreParagraphs'].append(dict(source=[source],translation=target,styleMappings=styles or {}))
paragraph('Shoots a volley of {count} Arrows that explodes on impact, dealing the same damage as your highest bow hit in the last minute in a {radius} block radius.',
          '射出 {count} 支箭矢，命中时在 {radius} 格半径内引发爆炸，造成等同于你过去一分钟内最高单次弓箭命中的伤害。')
for bow in ('Bow','bow'):
    paragraph('Shoots {count} Arrows per second for {duration} seconds! Arrows deal {damage} of your highest '+bow+' hit in the last minute.',
              '每秒射出 {count} 支箭矢，持续 {duration} 秒！每支箭矢造成你过去一分钟内最高单次弓箭命中伤害的 {damage}。')
write('translations/system-messages.json',data)
write('translations/tooltip-paragraphs.json',paragraphs)
(STATE/'rules.json').write_text(json.dumps(added,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
(ROOT/'src/test/resources/fixtures/dungeon-supplement-20261103.json').write_text(json.dumps(added,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
(STATE/'sources.json').write_text(json.dumps({
    'puzzleBank':'https://hypixelskyblock.minecraft.wiki/w/Catacombs_Puzzle_Rooms?oldid=866660',
    'archer':'https://hypixelskyblock.minecraft.wiki/w/Archer?oldid=865954',
    'crossCheck':'https://github.com/SkyblockerMod/Skyblocker/tree/ca1163e2b7309113cc4dd3c084829d4417606235',
    'blazeFailure':'https://hypixel.net/threads/just-got-hecatomb-x.5495338/',
    'weirdosFailure':'https://www.reddit.com/r/HypixelSkyblock/comments/l5rg19/',
    'quizFailure':'https://hypixel.net/threads/greatest-party-finder-moment-of-them-all.4351724/page-2',
    'note':'Game text transcription and original Chinese translations; no solver implementation copied. Published question bank cannot establish undisclosed server messages.'
},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print('Added',len(added),'rules; Sacks style boundaries fixed.')
