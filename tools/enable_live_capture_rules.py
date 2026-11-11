"""Extend the existing collector allowlist without enabling it behind a live client."""
import json,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'src/main/resources/defaults'
def read(p):return json.loads(p.read_text(encoding='utf8'))
def encoded(v):return (json.dumps(v,ensure_ascii=False,indent=2)+'\n').encode()
additions={}
for name in ('system-messages.json','creature-messages.json','death-messages.json'):
    for row in read(BASE/('translations/'+name))['rules']:
        source=row['source']
        # Existing policy deliberately excludes arbitrary prose capture variables.
        if any('{'+v+'}' in source for v in ('name','text','prefix','suffix')):continue
        for scope in row['scopes']:additions.setdefault(scope,[]).append(source)
for menu in read(BASE/'translations/gui-menus.json')['menus']:
    for source in menu['titles']:
        if '{' not in source:additions.setdefault('gui',[]).append(source)
hashes=read(BASE/'upgrade-hashes.json')
for root in (BASE,ROOT/'dictionary-workspace',ROOT/'run/config/sapphire'):
    path=root/'collection-rules.json'
    if not path.exists():continue
    old=path.read_bytes();d=read(path)
    for scope,sources in additions.items():d[scope]=list(dict.fromkeys(d.get(scope,[])+sources))
    new=encoded(d)
    if new==old:continue
    backup=ROOT/'translation-state/live-capture-20261101'/path.relative_to(ROOT);backup.parent.mkdir(parents=True,exist_ok=True)
    if not backup.exists():backup.write_bytes(old)
    if root==BASE:hashes['collection-rules.json']=','.join(sorted(set(filter(None,hashes.get('collection-rules.json','').split(',')))|{hashlib.sha256(old).hexdigest()}))
    path.write_bytes(new)
(BASE/'upgrade-hashes.json').write_bytes(encoded(hashes))
print('Collector rules updated; in-game switch controls activation.')
