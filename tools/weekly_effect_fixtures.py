"""Capture public NEU effect examples for complete enchantment and held-item regressions."""
from pathlib import Path
import re,json
from dictionary_tool import read,write,plain
root=Path(__file__).resolve().parents[1]
names=read(root/'src/main/resources/defaults/translations/enchantment-ids.json')
ids=set(names.values());enchants=[];pets=[]
for path in sorted((root/'build/neu-reference/items').glob('*.json')):
    identity=path.stem
    if not (identity.split(';')[0] in ids and ';' in identity or identity.startswith('PET_ITEM_')):continue
    raw=json.loads(path.read_text(encoding='utf-8-sig'));lore=raw.get('lore',[])
    if identity.split(';')[0] in ids and ';' in identity:
        for i,line in enumerate(lore):
            if re.fullmatch(r'.+ [IVXLCDM]+',plain(line)) and any(plain(line).startswith(n+' ') for n in names):
                effect=[]
                for value in lore[i+1:]:
                    if not plain(value).strip():break
                    effect.append(value)
                if effect:enchants.append(dict(id=identity,header=line,effect=effect))
                break
    if identity.startswith('PET_ITEM_'):
        groups=[];group=[]
        for line in lore+['']:
            if plain(line).strip():group.append(line)
            elif group:groups.append(group);group=[]
        excluded=('Consumed on use','Pet Items can boost pets','Right-click','This pet item can only','LEGENDARY','EPIC','RARE','UNCOMMON','COMMON','MYTHIC','SPECIAL','The pet must be visible')
        effects=[g for g in groups if not any(plain(g[0]).startswith(s) for s in excluded)]
        pets.append(dict(id=identity,name=plain(raw['displayname']),effects=effects))
write(root/'src/test/resources/weekly-effect-fixtures.json',dict(enchantments=enchants,petItems=pets))
print(json.dumps(dict(enchantments=len(enchants),petItems=len(pets))))
