"""Preserve the colored units/statuses in live Bazaar claim/refund messages."""
import json
import hashlib
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'src/main/resources/defaults'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def write(p,value):p.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

hashes=read(BASE/'upgrade-hashes.json')
for file in ['translations/gui-menus.json','translations/system-messages.json']:
    prior=set(hashes.get(file,'').split(','))-{''}
    prior.add(hashlib.sha256((BASE/file).read_bytes()).hexdigest())
    hashes[file]=','.join(sorted(prior))
write(BASE/'upgrade-hashes.json',hashes)

def fix(row,source):
    if source.startswith('[Bazaar] Claimed '):
        # There are two target currency units but only one in the English source.
        # Bind the original unit to its amount rather than an ambiguous word.
        row.setdefault('styleMappings',{})['{market} coins']='{market} 硬币'
    if source.startswith('[Bazaar] Cancelled! Refunded '):
        if source.lower().endswith('buy order!'):
            row.setdefault('styleMappings',{})['Cancelled!']='已取消买单!'
        elif source.lower().endswith('sell offer!'):
            row.setdefault('styleMappings',{})['Cancelled!']='已取消卖单!'

for root in [BASE/'translations',ROOT/'dictionary-workspace/translations',ROOT/'run/config/sapphire/translations']:
    menus=read(root/'gui-menus.json')
    for menu in menus['menus']:
        for row in menu.get('loreParagraphs',[]):fix(row,' '.join(row['source']))
    write(root/'gui-menus.json',menus)
    system=read(root/'system-messages.json')
    for row in system['rules']:fix(row,row['source'])
    write(root/'system-messages.json',system)
print('Updated scoped claim/refund style mappings in all three dictionaries.')
