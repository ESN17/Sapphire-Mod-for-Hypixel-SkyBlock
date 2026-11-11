"""Reviewed screenshot, Wiki sea-creature and SkyHanni display corrections."""
import hashlib,json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'src/main/resources/defaults'
STATE=ROOT/'translation-state/fishing-dungeon-feedback-20261104'
def read(key):return json.loads((BASE/key).read_text(encoding='utf8'))
def pairs(s):return dict(line.split('|',1) for line in s.strip().splitlines())
def write(key,data):
    path=BASE/key;before=path.read_bytes()
    if json.loads(before)==data:return
    backup=STATE/key;backup.parent.mkdir(parents=True,exist_ok=True)
    if not backup.exists():backup.write_bytes(before)
    hashes=read('upgrade-hashes.json');hashes[key]=','.join(sorted(set(filter(None,hashes.get(key,'').split(',')))|{hashlib.sha256(before).hexdigest()}))
    (BASE/'upgrade-hashes.json').write_text(json.dumps(hashes,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    after=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode();path.write_bytes(after)
    for root in [ROOT/'dictionary-workspace',ROOT/'run/config/sapphire']:
        mirror=root/key
        if mirror.exists() and mirror.read_bytes()==before:mirror.write_bytes(after)
data=read('translations/system-messages.json');rules=data['rules'];added=[]
owned=STATE/'rules.json'
if owned.exists():
    old=json.loads(owned.read_text(encoding='utf8'));rules[:]=[r for r in rules if r not in old]
def rule(source,target,scopes='chat',styles=None):
    scopes=[s for s in scopes.split(',') if not any(r['source']==source and s in r['scopes'] for r in rules)]
    if not scopes:return
    row=dict(source=source,translation=target,scopes=scopes,styleMappings=styles or {})
    rules.append(row);added.append(row)

# Existing wording is retained; the screenshot supplies independently colored spans.
for row in rules:
    if 'WITHER door' in row['source']:
        row.setdefault('styleMappings',{}).update({'RIGHT CLICK':'右键点击','WITHER':'凋灵之门'})
rule('You do not currently have an Energy Crystal! Find one and bring it here!',
     '你目前没有能量水晶！找到一颗并把它带到这里！',styles={'Energy Crystal':'能量水晶'})
for unit in ['second','seconds']:
    rule('Starting in {seconds} '+unit+'.','将在 {seconds} 秒后开始。','chat,title,subtitle,skyhanni')
    for s,t in [('Your Regular Ability is currently on cooldown for {seconds} more ','你的普通技能还需冷却 {seconds} 秒。'),
                ('Your Ultimate is currently on cooldown for {seconds} more ','你的终极技能还需冷却 {seconds} 秒。')]:rule(s+unit+'.',t)
    rule('The gate will open in {seconds} '+unit+'!','大门将在 {seconds} 秒后开启！','chat,subtitle')
for en,zh in [('Archer','弓箭手'),('Mage','法师'),('Healer','治疗师'),('Tank','坦克'),('Berserk','狂战士')]:
    rule('Your '+en+' stats are doubled because you are the only player using this class!',
         '由于队伍中只有你使用'+zh+'职业，你的职业属性翻倍！',styles={en:zh})
    for stat,cn in [('Extra Arrow Chance','额外箭矢概率'),('Arrow Damage','箭矢伤害'),('Melee Damage','近战伤害'),('Defense','防御'),('Health','生命值'),('Intelligence','智力'),('Speed','速度'),('Healing','治疗量'),('Cooldown Reduction','冷却缩减')]:
        rule('['+en+'] '+stat+' {before} -> {after}','['+zh+'] '+cn+' {before} -> {after}',styles={en:zh,stat:cn})
for en,zh in [('WOOD','木'),('GOLD','金'),('DIAMOND','钻石'),('EMERALD','绿宝石'),('OBSIDIAN','黑曜石'),('BEDROCK','基岩')]:
    rule(en+' CHEST REWARDS',zh+'宝箱奖励','chat,gui,skyhanni')
    for name in [en.title(),en.title()+' Chest',en+' CHEST']:
        rule(name,zh+'宝箱','gui')
    for name in [en.title()+' Chest',en+' CHEST']:
        rule(name,zh+'宝箱','item-name,gui-tooltip,skyhanni')
rule('{rankedPlayer} has obtained {catalogItem} x{count}!',
     '{rankedPlayer} 获得了 {catalogItem} x{count}！',styles={'!':'！'})

# Live tracker output differs from settings labels, and uses separate table cells.
for en,zh in pairs('''Sea Creature Tracker|海怪统计
Sea Creature Tracker:|海怪统计：
Fishing Profit Tracker|钓鱼收益统计
Slayer Profit Tracker|杀手任务收益统计
Total Sea Creatures|海怪总数
Total Uptime|总运行时间
Uptime|运行时间
Average Kill Time|平均击杀耗时
Kills per hour|每小时击杀数
Catches per hour|每小时钓获数
Items|物品
Amount|数量
Value|价值
Category|分类
All|全部
This Session|本次统计
Total|总计
Session|本次
Today|今天
This Day|当天
This Week|本周
This Month|本月
This Year|本年
This Mayor|本届市长
Click to hide!|点击隐藏！
Click to show!|点击显示！
Control + Click to remove this item!|按住 Ctrl 点击移除此物品！
You obtained this item recently.|你最近获得了此物品。
Click to copy line!|点击复制此行！
Shift Click to include stats in this tooltip!|按住 Shift 点击可包含此提示中的统计信息！
Use negative numbers to remove items.|使用负数减少物品数量。
to edit the number.|来修改数量。
Click to confirm.|点击确认。
This will reset your|这将重置你的
This will resume your|这将继续你的
This will pause your|这将暂停你的
current session of|本次统计：
You are not in SkyBlock!|你目前不在 SkyBlock 中！
You are not on SkyBlock!|你目前不在 SkyBlock 中！''').items():rule(en,zh,'skyhanni')
for en,zh in [('Total Uptime','总运行时间'),('Uptime','运行时间'),('Average Kill Time','平均击杀耗时')]:
    rule(en+': {time}',zh+'：{time}','skyhanni')
    rule(en+': {time} (Paused!)',zh+'：{time}（已暂停！）','skyhanni',{'(Paused!)':'（已暂停！）'})
for en,zh in [('Kills per hour','每小时击杀数'),('Catches per hour','每小时钓获数'),('Profit per hour','每小时收益'),('Profit per kill','每次击杀收益'),('Profit per catch','每次钓获收益')]:
    rule(en+': {value}',zh+'：{value}','skyhanni')
rule('{count} Total Sea Creatures','{count} 海怪总数','skyhanni')
rule('- {count} Total Sea Creatures','- {count} 海怪总数','skyhanni')
for name,zh in [('Session','本次'),('Total','总计'),('Today','今天'),('This Day','当天'),('This Week','本周'),('This Month','本月'),('This Year','本年'),('This Mayor','本届市长')]:
    rule('Display Mode ['+name+']','显示模式 ['+zh+']','skyhanni',{'Display Mode':'显示模式',name:zh})
    rule(name+' Profit: {value} coins',zh+'收益：{value} 硬币','skyhanni',{'coins':'硬币'})
for en,zh in [('BZ Instant Buy','集市即时买入'),('BZ Instant Sell','集市即时卖出'),('BZ Sell Offer','集市卖单'),('BZ Buy Order','集市买单'),('NPC Sell','NPC 收购'),('Lowest BIN','最低一口价')]:
    rule('Price Source ['+en+']','价格来源 ['+zh+']','skyhanni',{'Price Source':'价格来源',en:zh})
for en,zh in [('Sea Creature Tracker','海怪统计'),('Fishing Profit Tracker','钓鱼收益统计'),('Slayer Profit Tracker','杀手任务收益统计')]:
    for suffix,cn in [('to clipboard!','到剪贴板！'),('stats to clipboard!','统计数据到剪贴板！')]:
        rule('Copied '+en+' '+suffix,'已复制'+zh+cn,'skyhanni', {en:zh})
for row in list(rules):
    # Explicit SkyHanni prefix variants keep its prefix and interactive links intact.
    if 'skyhanni' in row['scopes'] and not row['source'].startswith(('[','{')):
        rule('[SkyHanni] '+row['source'],'[SkyHanni] '+row['translation'],'skyhanni,chat',row.get('styleMappings'))

creatures=read('translations/creature-messages.json')
# Do not recursively wrap previously generated compact messages on reruns.
creatures['rules'][:]=[r for r in creatures['rules'] if r['source'].count('DOUBLE HOOK!')<=1]
wiki=json.loads((STATE/'wiki-notices.json').read_text(encoding='utf8'))
missing=pairs('''A Fire Eel slithers out from the depths.|一条火鳗从深处蜿蜒游出。
A Dumpster Diver has emerged from the swamp!|一名垃圾潜水者从沼泽中现身了！
You get an inkling that you've caught... an Inkling!|你隐约感觉自己钓到了……一只墨灵！
WOAH! {player} summoned a Reindrake from the depths!|哇！{player} 从深处召唤出了一条驯鹿龙！
WOAH! {player} summoned TWO Reindrakes from the depths!|哇！{player} 从深处召唤出了两条驯鹿龙！
You caught a Stridersurfer.|你钓到了一只踏浪者。
A Water Worm surfaces!|一条水虫浮出了水面！''')
missing['[MOB] Puddle Jumper: Wow! You caught me!\n[MOB] Puddle Jumper: Great jumpin! Try to catch me every time!']='[生物] 水洼跳跃者：哇！你钩到我了！\n[生物] 水洼跳跃者：跳得真棒！试着每次都钩到我吧！'
missing["[MOB] Puddle Jumper: Wow! You're a master jumper! Here's a bonus; on me!\nThe Puddle Jumper awarded you with a Silver Lotus for making every jump!"]='[生物] 水洼跳跃者：哇！你真是跳跃大师！这份额外奖励，我请客！\n你成功跟上了每次跳跃，水洼跳跃者奖励了你一朵银莲花！'
entity_names=read('translations/entity-names.json')
# Reuse already reviewed creature names wherever these notices name one.
for en,zh in [('Fire Eel','火鳗'),('Dumpster Diver','垃圾潜水者'),('Inkling','墨灵'),('Reindrake','驯鹿龙'),('Stridersurfer','踏浪者'),('Water Worm','水虫')]:
    for source in missing:
        if en in source:missing[source]=missing[source].replace(zh,entity_names.get(en,zh))
for source,target in missing.items():
    if any(r['source']==source for r in creatures['rules']):continue
    reference=next((r for r in wiki['notices'] if r['source'].replace('[player]','{player}')==source),{})
    styles={en:entity_names[en] for en in entity_names if en in source and entity_names[en] in target}
    if source.startswith('WOAH!'):styles.update({'WOAH!':'哇！','TWO':'两条'})
    if '[MOB]' in source:styles.update({'[MOB]':'[生物]','Puddle Jumper':'水洼跳跃者','Silver Lotus':'银莲'})
    creatures['rules'].append(dict(source=source,translation=target,scopes=['chat','skyhanni'],styleMappings=styles,
        **{k:reference[k] for k in ['sourceUrl','revisionUrl','license','sampleRaw'] if k in reference}))
for row in creatures['rules']:
    if 'skyhanni' not in row['scopes']:row['scopes'].append('skyhanni')
    if row['source'].startswith("It's a Double Hook!"):
        row.setdefault('styleMappings',{}).update({'Double Hook':'一竿双钓','Woot woot!':'好耶！'})
    if row['source'].startswith("[MOB] Puddle Jumper: Wow! You're a master jumper!"):
        row['translation']=missing[row['source']]
        row['styleMappings']['The Puddle Jumper awarded you with a Silver Lotus for making every jump!']='你成功跟上了每次跳跃，水洼跳跃者奖励了你一朵银莲花！'
        row['styleMappings']['Silver Lotus']='银莲花'
# Generate only known whole catch messages, never arbitrary chat suffixes.
catch_sources={r['source'].replace('[player]','{player}') for r in wiki['notices'] if 'Fishing up' in r['context']}
for row in list(creatures['rules']):
    if 'DOUBLE HOOK!' in row['source']:continue
    if row['source'] not in catch_sources and not row['source'].startswith(('You caught a {mob}','You caught an {mob}')):continue
    for prefix,suffix in [('DOUBLE HOOK! ',''),('',' DOUBLE HOOK!')]:
        source=prefix+row['source']+suffix
        if any(r['source']==source for r in creatures['rules']):continue
        clone=dict(row);clone.update(source=source,translation=('一竿双钓！ ' if prefix else '')+row['translation']+(' 一竿双钓！' if suffix else ''),styleMappings=dict(row.get('styleMappings',{}),**{'DOUBLE HOOK!':'一竿双钓！'}))
        if 'sampleRaw' in clone:clone['sampleRaw']=('§e§lDOUBLE HOOK! §r' if prefix else '')+clone['sampleRaw']+(' §e§lDOUBLE HOOK!' if suffix else '')
        creatures['rules'].append(clone)
rule('DOUBLE HOOK!','一竿双钓！','chat,title,subtitle,skyhanni')
write('translations/system-messages.json',data)
write('translations/creature-messages.json',creatures)
owned.write_text(json.dumps(added,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps({'newSystemRules':len(added),'creatureRules':len(creatures['rules'])},ensure_ascii=False))
