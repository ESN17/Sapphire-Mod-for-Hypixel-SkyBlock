"""Add reviewed F5/F6 capture translations; preserve existing entries and live logs."""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'src/main/resources/defaults'
STATE = ROOT / 'translation-state/dungeon-f5-f6-20261103'
def read(key): return json.loads((BASE / key).read_text(encoding='utf8'))
def pairs(text): return dict(line.split('|', 1) for line in text.strip().splitlines())
changed = []
def write(key, data):
    path = BASE / key
    before = path.read_bytes()
    if json.loads(before) == data: return
    backup = STATE / key
    backup.parent.mkdir(parents=True, exist_ok=True)
    if not backup.exists(): backup.write_bytes(before)
    hashes = read('upgrade-hashes.json')
    hashes[key] = ','.join(sorted(set(filter(None, hashes.get(key, '').split(','))) | {hashlib.sha256(before).hexdigest()}))
    (BASE / 'upgrade-hashes.json').write_text(json.dumps(hashes, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    after = (json.dumps(data, ensure_ascii=False, indent=2)+'\n').encode()
    path.write_bytes(after)
    for root in [ROOT/'dictionary-workspace', ROOT/'run/config/sapphire']:
        mirror = root/key
        if mirror.exists() and mirror.read_bytes() == before: mirror.write_bytes(after)
    changed.append(key)

data = read('translations/system-messages.json')
rules = data['rules']
owned_path = STATE/'rules.json'
if owned_path.exists():
    owned_rules = json.loads(owned_path.read_text(encoding='utf8'))
    rules[:] = [r for r in rules if r not in owned_rules]
added = []
def rule(source, target, scopes='chat', styles=None):
    scopes = [s for s in scopes.split(',') if not any(r['source']==source and s in r['scopes'] for r in rules)]
    if not scopes: return
    styles = dict(styles or {})
    if source.count('!') == 1 and target.count('！') == 1: styles.setdefault('!', '！')
    row = dict(source=source, translation=target, scopes=scopes, styleMappings=styles)
    rules.append(row); added.append(row)

labels = read('translations/entity-labels.json')
terms = read('translations/tooltip-labels.json')
paragraphs = read('translations/tooltip-paragraphs.json')
catalog = {}
for p in (BASE/'translations/items').glob('*.json'):
    item = json.loads(p.read_text(encoding='utf8'))
    if item.get('_original_name') and item.get('name'):
        catalog.setdefault(re.sub('§.', '', item['_original_name']), re.sub('§.', '', item['name']))

# Retain reviewed community dialogue and reuse it for corresponding overhead lines.
coverage = json.loads((STATE/'before-coverage.json').read_text(encoding='utf8'))
for row in coverage:
    if row['scope'] != 'chat' or not row['changed']: continue
    source = re.sub('§.', '', row['original']).strip()
    target = re.sub('§.', '', row['current']).strip()
    a = re.match(r'\[BOSS\] (?:The Watcher|Scarf|Livid|Sadan): (.+)', source)
    b = re.match(r'\[BOSS\] (?:The Watcher|Scarf|Livid|Sadan)[:：]\s*(.+)', target)
    if a and b: labels.setdefault(a[1], b[1])

livid = pairs('''Welcome, you've arrived right on time. I am Livid, the Master of Shadows.|欢迎，你们来得正是时候。我是 Livid，暗影之主。
This Orb you see, is Thorn, or what is left of him.|你们眼前这颗宝珠就是 Thorn，或者说，是他剩下的残骸。
In a way, I have to thank you for finally getting rid of him.|某种意义上，我还得感谢你们终于除掉了他。
I can now turn those Spirits into shadows of myself, identical to their creator.|如今我能将那些灵魂化为自己的暗影，与创造它们的我一模一样。
I respect you for making it to here, but I'll be your undoing.|你们能走到这里值得敬佩，但我会让你们止步于此。
Keep looking!|继续找吧！
I am Livid, but I'm not Livid.|我是 Livid，却又不是 Livid。
Wrong one!|找错了！
So foolish!|真愚蠢！
I am but a shadow.|我不过是一道暗影。
They couldn't even find the real Livid, psht!|他们连真正的 Livid 都找不到，啧！
You look like a real fool right now.|你现在看起来真像个傻瓜。
Yikes! Imagine getting that wrong.|哎呀！居然连这都能弄错。
Impossible! How did you figure out which one I was?!|不可能！你们怎么认出哪个才是我？！
If you think this is the end, you are so wrong!|如果你们以为这就结束了，那就大错特错了！
My shadows are everywhere, THEY WILL FIND YOU!!|我的暗影无处不在，它们一定会找到你们！！''')
for source, target in livid.items():
    # The same dialogue is emitted by Livid, named Livid clones and Enderman disguises.
    # Keep the entire NPC name span, rather than translating clone/pet skin names.
    rule('[BOSS] {prefix}: '+source, '[BOSS] {prefix}: '+target)
    labels.setdefault(source, target)

for npc, source, target in [
    ('Scarf', 'How can you move forward when you keep regretting the past?', '一直为过去悔恨，又怎能继续前进？'),
    ('Scarf', "I couldn't bring mother back, so I found the next best thing! I died!", '我没能让母亲复活，于是选了另一个办法！我也死了！'),
    ('Sadan', "I'm sorry, but I need to concentrate. I wish it didn't have to come to this.", '抱歉，我必须集中精神。我也不愿事情走到这一步。'),
]:
    rule('[BOSS] '+npc+': '+source, '[BOSS] '+npc+': '+target)
    labels.setdefault(source, target)
labels.setdefault("Dude.. we're undead.", '老兄……我们本来就是亡灵。')

for source, target in pairs('''Those Golems will be your undoing, {player}!|那些傀儡会要了你的命，{player}！
Reckless humans! You should know by now my Golems do not want to be awoken, {player}!|鲁莽的人类！你早该知道我的傀儡不愿被唤醒，{player}！
All my Golems want is some peace and quiet! Can you not, {player}?!|我的傀儡只想安静休息！别再打扰它们了，{player}？！
How many more of my Golems will you disturb, {player}?|你还要惊扰多少我的傀儡，{player}？
Wonderous! Not one sleeping Golem was left unperturbed by you reckless humans! Thanks, {player}!|真了不起！沉睡的傀儡全被你们这群莽撞的人类惊醒了！可真谢谢你，{player}！''').items():
    rule('[BOSS] Sadan: '+source, '[BOSS] Sadan: '+target)
    rule(source, target, 'entity')

rule("Sadan's Interest Level", 'Sadan 的兴致', 'bossbar')
rule("Sadan's Giants", 'Sadan 的巨人', 'bossbar')
for source, target in {'GRAY':'灰色','RED':'红色','GREEN':'绿色','BLUE':'蓝色','WHITE':'白色','YELLOW':'黄色','PURPLE':'紫色','PINK':'粉色','LIME':'黄绿色','AQUA':'青色','DARK_GREEN':'深绿色'}.items():
    rule('The livid color is '+source, 'Livid 的颜色是'+target, 'title,subtitle', {source:target})
rule('The Stormy {mob} struck you for {damage} damage!', '风暴的{mob}对你造成了 {damage} 点伤害！', styles={'Stormy':'风暴的'})
rule('☠ Defeated {name} in {time} (NEW RECORD!)', '☠ 击败 {name}，用时 {time}（新纪录！）', styles={'(NEW RECORD!)':'（新纪录！）'})

rule('You bought {catalogItem}!', '你购买了{catalogItem}！')
rule('Buying: {catalogItem}', '购买：{catalogItem}', 'item-tooltip,gui-tooltip', {'Buying':'购买'})
for en in ['Beating Heart', 'InfiniBoom™ TNT']:
    if en in catalog: rule(en, catalog[en], 'entity,item-name,item-tooltip,gui-tooltip')
rule('InfiniBoom™ TNT', read('translations/items/INFINITE_SUPERBOOM_TNT.json')['name'], 'item-name,entity')
# Potion rank is a live value, not tied to the tested floor or potion tier.
rule('Dungeon {roman} Potion', '地牢 {roman} 药水', 'item-name')
for en, zh in [('Regeneration','生命恢复'),('Strength','力量'),('Critical','暴击'),('Speed','速度'),('Resistance','抗性'),('Archery','箭术')]:
    rule(en+' {roman} ({time})', zh+' {roman} ({time})', 'item-tooltip,gui-tooltip', {en:zh})
for source, target, styles in [
    ('Grants {statIcon1} Health Regen.', '提供 {statIcon1} 生命恢复。', {'Health Regen':'生命恢复'}),
    ('Increases {icon1} Strength by {value}.', '增加 {value} {icon1} 力量。', {'Strength':'力量'}),
    ('Grants {statIcon1} Speed.', '提供 {statIcon1} 速度。', {'Speed':'速度'}),
    ('Increases {icon1} Defense by {value}.', '增加 {value} {icon1} 防御。', {'Defense':'防御'}),
    ('Increases bow damage by {value}.', '弓箭伤害提高 {value}。', {'bow damage':'弓箭伤害'}),
    ('Runecrafting Wisdom: {value}', '符文智慧：{value}', {'Runecrafting Wisdom':'符文智慧'}),
    ('Bonus: {statIcon1} Runecrafting Wisdom', '加成：{statIcon1} 符文智慧', {'Bonus':'加成','Runecrafting Wisdom':'符文智慧'}),
    ('Counter: {count}', '计数：{count}', {'Counter':'计数'}),
    ('Acts as Superboom TNT!', '可发挥'+catalog['Superboom TNT']+'的效果！', {'Superboom TNT':catalog['Superboom TNT']}),
]: rule(source, target, 'item-tooltip,gui-tooltip', styles)
for en, zh in [('Missing Enchantments:', '缺少的附魔：'),('Combinable in Anvil', '可在铁砧中合并'),('Combinable in Anvil, Combinable in Anvil','可在铁砧中合并，可在铁砧中合并')]:
    rule(en, zh, 'item-tooltip,gui-tooltip')

def paragraph(source, target, styles=None):
    baseline = STATE/'translations/tooltip-paragraphs.json'
    if baseline.exists() and not any(' '.join(r['source'])==source for r in json.loads(baseline.read_text(encoding='utf8'))['loreParagraphs']):
        paragraphs['loreParagraphs'][:] = [r for r in paragraphs['loreParagraphs'] if ' '.join(r['source'])!=source]
    if not any(' '.join(r['source']) == source for r in paragraphs['loreParagraphs']):
        paragraphs['loreParagraphs'].append(dict(source=[source], translation=target, styleMappings=styles or {}))
paragraph('A Seismic Wave emerges from underneath you and travels in a straight line. Deals {damage} +{increase} every {statIcon1} Defense in damage to any enemy in its path.',
          '一道地震波从你脚下涌出，沿直线前进，对路径上的敌人造成 {damage} 点伤害；每 {statIcon1} 防御使伤害提高 +{increase}。', {'Defense':'防御'})
paragraph('Increases {icon1} Crit Chance by {chance} and {icon2} Crit Damage by {damage}.',
          '增加 {chance} {icon1} 暴击几率和 {damage} {icon2} 暴击伤害。', {'Crit Chance':'暴击几率','Crit Damage':'暴击伤害','.':'。'})

write('translations/system-messages.json', data)
write('translations/entity-labels.json', labels)
write('translations/tooltip-paragraphs.json', paragraphs)
previous = STATE/'rules.json'
previous.write_text(json.dumps(added, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
print(json.dumps({'rulesAdded':len(added), 'changed':changed}, ensure_ascii=False))
