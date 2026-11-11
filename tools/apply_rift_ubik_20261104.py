"""Add screenshot-based Rift notices; retain existing wording and user edits."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'src/main/resources/defaults'
STATE=ROOT/'translation-state/rift-ubik-20261104'
STATE.mkdir(parents=True,exist_ok=True)
key='translations/system-messages.json'
path=BASE/key
before=path.read_bytes()
data=json.loads(before)
rules=data['rules'];added=[]
owned=STATE/'rules.json'
if owned.exists():
    previous=json.loads(owned.read_text(encoding='utf8'))
    rules[:]=[r for r in rules if r not in previous]
def add(source,target,styles=None,scopes=None):
    scopes=[s for s in (scopes or ['chat']) if not any(r['source']==source and s in r['scopes'] for r in rules)]
    if not scopes:return
    mappings=dict(styles or {})
    if '!' in source and '！' in target:mappings['!']='！'
    row=dict(source=source,translation=target,scopes=scopes,styleMappings=mappings)
    rules.append(row);added.append(row)
for currency in ['Motes','Mote','裂隙尘埃']:
    for en,zh in [('STEAL','独吞'),('SPLIT','平分')]:
        for suffix,final in [('', ''),(' (FINAL)','（最终回合）')]:
            add('ROUND {round}'+suffix+': You chose '+en+' and gained {amount} '+currency+'!',
                '第 {round} 回合'+final+'：你选择了'+zh+'，获得 {amount} 裂隙尘埃！',
                {'ROUND':'第','You chose':'你选择了','and gained':'获得',en:zh,'(FINAL)':'（最终回合）',currency:'裂隙尘埃'})
    add('You earned {amount} '+currency+' in this match!',
        '你在本场对局中获得了 {amount} 裂隙尘埃！',{currency:'裂隙尘埃'})
    add('Your opponent earned {amount} '+currency+' in this match!',
        '你的对手在本场对局中获得了 {amount} 裂隙尘埃！',{currency:'裂隙尘埃'})
    add('ORB! Picked up +{amount} '+currency+'!',
        '光球！拾取了 +{amount} 裂隙尘埃！',{'ORB!':'光球！',currency:'裂隙尘埃'})
    for gap in ['', ' ']:
        for icon in ['','ф','⏳','⌛']:
            add('ORB! Picked up +{amount} '+currency+', recovered +{seconds}'+gap+icon+' Rift Time!',
                '光球！拾取了 +{amount} 裂隙尘埃，恢复 +{seconds}'+gap+icon+' 裂隙时间！',
                {'ORB!':'光球！',currency:'裂隙尘埃','Rift Time':'裂隙时间'})
for gap in ['', ' ']:
    for icon in ['','ф','⏳','⌛']:
        add('You have {time}'+gap+icon+' left before the rift collapses!',
            '距离裂隙崩塌还剩 {time}'+gap+icon+'！')
        add('You used {time}'+gap+icon+' Rift Time to teleport to {location}!',
            '你消耗了 {time}'+gap+icon+' 裂隙时间，传送到了{location}！',{'Rift Time':'裂隙时间'})
# Source: https://hypixelskyblock.minecraft.wiki/w/Ubik%27s_Cube?oldid=825642
# The old imported entry treats [time] as a literal and cannot match live messages.
add('SPLIT! You need to wait {time} before you can play again.',
    '平分或独吞！你还需要等待 {time} 才能再次游玩。',{'SPLIT!':'平分或独吞！'})
# Deployables share one server sentence. Names must come from the item catalog.
add('Your previous {catalogItem} was removed!', '你先前放置的 {catalogItem} 已被移除！')
# Reuse reviewed pickaxe skill terminology, rather than guessing from item names.
for row in list(rules):
    source=row['source'];prefix='You used your ';suffix=' Pickaxe Ability!'
    if source.startswith(prefix) and source.endswith(suffix):
        ability=source[len(prefix):-len(suffix)]
        translated=row.get('styleMappings',{}).get(ability)
        if translated:add(ability+' is now available!',translated+'现已可以使用！',
                          {ability:translated,'is now available!':'现已可以使用！'})
# Glacite Mineshaft screenshot: existing headings need semantic color anchors.
for en,zh in [('LAPIS','青金石'),('TUNGSTEN','钨'),('UMBER','棕土'),('VANGUARD','先锋')]:
    for row in rules:
        if row['source']==en+' CORPSE LOOT!':
            row.setdefault('styleMappings',{}).update({en:zh,'CORPSE LOOT!':'尸体战利品!'})
    for prefix in ['', '[SkyHanni] ']:
        for sign in ['', '-']:
            add(prefix+'Profit for '+en.title()+' Corpse: '+sign+'{market}',prefix+zh+'尸体收益：'+sign+'{market}',
                {en.title()+' Corpse':zh+'尸体','Profit for':'收益',':':'：'},['chat','skyhanni'])
for prefix in ['[Skyblocker] ','[SkyBlocker] ']:
    for sign in ['', '-']:
        add(prefix+'Corpse Profit: '+sign+'{market}',prefix+'尸体收益：'+sign+'{market}',{'Corpse Profit:':'尸体收益：'})
add('You need a tool with a Breaking Power of {power} to mine {name}! Speak to Fragilis by the entrance to the Crystal Hollows to learn more!',
    '挖掘{name}需要破坏力达到 {power} 的工具！请与水晶洞窟入口旁的 Fragilis 交谈以了解详情。',
    {'Breaking Power':'破坏力','Fragilis':'Fragilis','Crystal Hollows':'水晶洞窟'})
add('You summoned your {catalogItem}!', '你召唤了{catalogItem}！')
add('{catalogItem} x{count}', '{catalogItem} x{count}')
# Bare gemstone reward rows can have a separately colored gemstone icon.
import re
for itemPath in (BASE/'translations/items').glob('*.json'):
    item=json.loads(itemPath.read_bytes());en=item.get('_original_name','');zh=item.get('name','')
    core=re.search(r'(?:Rough|Flawed|Fine|Flawless|Perfect) .+ Gemstone$',en)
    if zh and core:
        core=core.group();target=re.sub(r'^[^\w]+','',zh)
        add(en,zh,{en:zh,core:target})
        add(core,target,{core:target})
        add('{icon} '+core,'{icon} '+target,{core:target})
if json.loads(before)!=data:
    backup=STATE/key;backup.parent.mkdir(parents=True,exist_ok=True)
    if not backup.exists():backup.write_bytes(before)
    hashesPath=BASE/'upgrade-hashes.json';hashes=json.loads(hashesPath.read_bytes())
    hashes[key]=','.join(sorted(set(filter(None,hashes.get(key,'').split(',')))|{hashlib.sha256(before).hexdigest()}))
    hashesPath.write_text(json.dumps(hashes,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    after=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode();path.write_bytes(after)
    for root in [ROOT/'dictionary-workspace',ROOT/'run/config/sapphire']:
        mirror=root/key
        if mirror.exists() and mirror.read_bytes()==before:mirror.write_bytes(after)
    (STATE/'rules.json').write_text(json.dumps(added,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
key='translations/locations.json';path=BASE/key;before=path.read_bytes();locations=json.loads(before)
if 'Mountaintop' not in locations:
    locations['Mountaintop']=locations['The Mountaintop']
    backup=STATE/key;backup.parent.mkdir(parents=True,exist_ok=True)
    if not backup.exists():backup.write_bytes(before)
    hashesPath=BASE/'upgrade-hashes.json';hashes=json.loads(hashesPath.read_bytes())
    hashes[key]=','.join(sorted(set(filter(None,hashes.get(key,'').split(',')))|{hashlib.sha256(before).hexdigest()}))
    hashesPath.write_text(json.dumps(hashes,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    after=(json.dumps(locations,ensure_ascii=False,indent=2)+'\n').encode();path.write_bytes(after)
    for root in [ROOT/'dictionary-workspace',ROOT/'run/config/sapphire']:
        mirror=root/key
        if mirror.exists() and mirror.read_bytes()==before:mirror.write_bytes(after)
print(json.dumps({'added':len(added)},ensure_ascii=False))
