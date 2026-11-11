"""Apply the user's Pristine terminology correction without changing English sources."""
import hashlib
import json
import re
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'src/main/resources/defaults'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def encoded(data):return (json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode('utf8')
def term(s):
    return s.replace('原粹触发!','提纯！').replace('原粹触发','提纯').replace('原粹','提纯').replace('纯净度','提纯').replace('棱彩','提纯').replace('纯净','提纯')
def related(s):return bool(re.search(r'\bPristine\b',s,re.I))
def adapt(value,context=False):
    if isinstance(value,str):return term(value) if context or '原粹' in value else value
    if isinstance(value,list):return [adapt(v,context) for v in value]
    if not isinstance(value,dict):return value
    source=value.get('source','')
    if isinstance(source,list):source=' '.join(source)
    context=context or related(source)
    result={}
    for key,child in value.items():
        if key.startswith('_') or key in ('source','sourceFile','sourceId','sampleRaw','id','npc','commit','upstream'):
            result[key]=child;continue
        active=context or related(key)
        if key=='name':active=active or related(value.get('_original_name',''))
        result[key]=adapt(child,active)
    return result

def main():
    hashes=read(BASE/'upgrade-hashes.json');changed=[]
    for root in [BASE,ROOT/'dictionary-workspace',ROOT/'run/config/sapphire']:
        for path in (root/'translations').rglob('*.json'):
            old=path.read_bytes()
            if not any(s in old for s in [b'Pristine',b'PRISTINE','原粹'.encode()]):continue
            data=read(path)
            is_book=path.stem.startswith('PRISTINE;')
            updated=adapt(data,is_book)
            if path.name=='enchantments.json':
                for key in ('Pristine','Prismatic'):
                    if key in updated:updated[key]='提纯'
            if updated==data:continue
            backup=ROOT/'translation-state/pristine-term-20261022/before'/path.relative_to(ROOT)
            backup.parent.mkdir(parents=True,exist_ok=True)
            if not backup.exists():backup.write_bytes(old)
            if root==BASE:
                key=path.relative_to(BASE).as_posix();prior=set(hashes.get(key,'').split(','))-{''}
                prior.add(hashlib.sha256(old).hexdigest());hashes[key]=','.join(sorted(prior))
            path.write_bytes(encoded(updated));changed.append(str(path.relative_to(ROOT)))
    (BASE/'upgrade-hashes.json').write_bytes(encoded(hashes))
    policy_path=ROOT/'tools/translation_policy.json';policy=read(policy_path)
    policy['terms']['Pristine']='提纯'
    policy_path.write_bytes(encoded(policy))
    print(f'Updated {len(changed)} dictionary files across three roots.')

if __name__=='__main__':main()
