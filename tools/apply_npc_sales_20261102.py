"""Add NPC sales using exact dictionary item names, and pin Fire Sale time styles."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'src/main/resources/defaults'
key='translations/system-messages.json';path=BASE/key;before=path.read_bytes();doc=json.loads(before)
for row in doc['rules']:
    if row['source'] in ('You sold {name} x{count} for {coins} Coins!','You sold {catalogItem} x{count} for {coins} Coins!'):
        row['source']=row['source'].replace('{name}','{catalogItem}')
        row['translation']=row['translation'].replace('{name}','{catalogItem}')
        row['styleMappings'].update({'You sold':'你卖出了','x{count}':'{count} 个','for':',获得','Coins':'硬币'})
    if '[WARP] To {player} in the next ' in row['source']:
        for var,unit,target in [('days','d','天'),('hours','h','小时'),('minutes','m','分'),('seconds','s','秒')]:
            token='{'+var+'}'+unit
            if token in row['source']:row['styleMappings'][token]='{'+var+'}'+target
doc['rules']=[r for r in doc['rules'] if r['source']!='You sold {catalogItem} x{count} for {amount} Coins!']
sources={r['source'] for r in doc['rules']}
for currency in ('Coins','coins'):
    for tail,target in [(' x{count}',' ×{count}'),('','')]:
        if currency=='Coins' and tail:continue # Repair the existing wording above; do not shadow it.
        source='You sold {catalogItem}'+tail+' for {amount} '+currency+'!'
        if source in sources:continue
        doc['rules'].append({'source':source,'translation':'你售出了 {catalogItem}'+target+'，获得 {amount} 硬币！','scopes':['chat'],
            'styleMappings':{'You sold':'你售出了','for':'，获得',currency:'硬币','x{count}':'×{count}'} if tail else {'You sold':'你售出了','for':'，获得',currency:'硬币'},
            'sourceAttribution':{'source':'User NPC sale screenshot, 2026-11-02; item names from existing item catalog only'}})
after=(json.dumps(doc,ensure_ascii=False,indent=2)+'\n').encode()
if before!=after:
    backup=ROOT/'translation-state/npc-sales-20261102/system-messages.json';backup.parent.mkdir(parents=True,exist_ok=True)
    if not backup.exists():backup.write_bytes(before)
    hashes_path=BASE/'upgrade-hashes.json';hashes=json.loads(hashes_path.read_text(encoding='utf-8'))
    hashes[key]=','.join(sorted(set(filter(None,hashes.get(key,'').split(',')))|{hashlib.sha256(before).hexdigest()}))
    path.write_bytes(after);hashes_path.write_text(json.dumps(hashes,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    for root in (ROOT/'dictionary-workspace',ROOT/'run/config/sapphire'):
        mirror=root/key
        if mirror.exists() and mirror.read_bytes()==before:mirror.write_bytes(after)
print('NPC sale rules ready; exact item catalog names only')
