"""Disambiguate Avarice by English source, never blanket-replace Greed's Chinese name."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'src/main/resources/defaults'
STATE=ROOT/'translation-state/name-collisions-20261105'
EN='crown of avarice';OLD='贪婪之冠';NEW='贪欲之冠'
def has(value):
    if isinstance(value,list):value=' '.join(str(v) for v in value)
    return EN in (value if isinstance(value,str) else json.dumps(value,ensure_ascii=False)).lower()
def replace(value):
    if isinstance(value,str):return value.replace(OLD,NEW)
    if isinstance(value,list):return [replace(v) for v in value]
    if isinstance(value,dict):return {k:replace(v) for k,v in value.items()}
    return value
def walk(value):
    if isinstance(value,list):return [walk(v) for v in value]
    if not isinstance(value,dict):return value
    result={k:walk(v) for k,v in value.items()}
    if has(value.get('_original_name','')) and 'name' in result:result['name']=replace(result['name'])
    if has(value.get('source','')) and 'translation' in result:result['translation']=replace(result['translation'])
    for k,v in list(result.items()):
        # English-keyed terminology/style maps, lore lines and SkyHanni strings.
        if has(k):result[k]=replace(v)
    return result
changed=[];hashpath=BASE/'upgrade-hashes.json';hashes=json.loads(hashpath.read_bytes())
paths=list((BASE/'translations').rglob('*.json'))+[BASE/'compat/skyhanni-zh_cn.json',ROOT/'tools/sapphire-translation-rules.json']
for p in paths:
    before=p.read_bytes()
    if EN.encode() not in before.lower():continue
    original=json.loads(before);updated=walk(original)
    if updated==original:continue
    rel=p.relative_to(ROOT).as_posix();backup=STATE/'backups'/rel;backup.parent.mkdir(parents=True,exist_ok=True)
    if not backup.exists():backup.write_bytes(before)
    after=(json.dumps(updated,ensure_ascii=False,indent=2)+'\n').encode();p.write_bytes(after);changed.append(rel)
    if p.is_relative_to(BASE):
        key=p.relative_to(BASE).as_posix()
        hashes[key]=','.join(sorted(set(filter(None,hashes.get(key,'').split(',')))|{hashlib.sha256(before).hexdigest()}))
        for root in [ROOT/'dictionary-workspace',ROOT/'run/config/sapphire']:
            mirror=root/key
            if mirror.exists() and mirror.read_bytes()==before:mirror.write_bytes(after)
hashpath.write_text(json.dumps(hashes,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
report=STATE/'changed-files.json'
previous=json.loads(report.read_bytes()) if report.exists() else []
report.write_text(json.dumps(sorted(set(previous+changed)),ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps(changed,ensure_ascii=False,indent=2))
