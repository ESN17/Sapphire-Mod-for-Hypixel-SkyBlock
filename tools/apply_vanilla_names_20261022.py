"""Apply reviewed Minecraft 26.1.2 name differences, preserving source text."""
import hashlib,json,re,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'src/main/resources/defaults'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def encoded(v):return (json.dumps(v,ensure_ascii=False,indent=2)+'\n').encode('utf8')
audit=read(ROOT/'build/vanilla-reference/audit.json')
rules={r['english']:(r['old'],r['new']) for r in audit['differences']}
rules.update({'Sea Lumies':('海光','海泡菜'),'Enchanted Sea Lumies':('附魔海光藻','附魔海泡菜'),
              'Mangrove Collection':('红树林收藏','红树收藏'),'Mangrove Fortune':('红树林幸运','红树幸运'),
              'Mangrove Trees':('红树林','红树')})
# Protect complete vanilla names before matching a shorter word such as Brick.
for english,term in read(ROOT/'build/vanilla-reference/terms.json')['terms'].items():
    rules.setdefault(english,(term['zh'],term['zh']))
pattern=re.compile(r'(?<![A-Za-z])(?:'+'|'.join(re.escape(s) for s in sorted(rules,key=len,reverse=True))+r')(?![A-Za-z])',re.I)
folded={k.lower():v for k,v in rules.items()}
def replacements(source):
    groups={}
    for m in pattern.finditer(source):
        old,new=folded[m.group().lower()];groups.setdefault(old,set()).add(new)
    return {old:next(iter(new)) for old,new in groups.items() if len(new)==1}
def text(value,pairs):
    if value in pairs:return pairs[value]
    pairs={k:v for k,v in pairs.items() if len(k)>1}
    if not pairs:return value
    correct=set(pairs.values())
    return re.sub('|'.join(re.escape(s) for s in sorted(set(pairs)|correct,key=len,reverse=True)),lambda m:m.group() if m.group() in correct else pairs[m.group()],value)
def adapt(value):
    if isinstance(value,list):return [adapt(v) for v in value]
    if not isinstance(value,dict):return value
    out={k:v if k.startswith('_') or k in ('source','sampleRaw','sourceFile','sourceId','id','npc') else adapt(v) for k,v in value.items()}
    source=value.get('source',value.get('_original_name',''))
    if isinstance(source,list):source=' '.join(source)
    pairs=replacements(source)
    for field in ('translation','name'):
        if isinstance(out.get(field),str):out[field]=text(out[field],pairs)
    if isinstance(out.get('styleMappings'),dict):
        out['styleMappings']={k:text(v,pairs) for k,v in out['styleMappings'].items()}
    for k,v in value.items():
        if isinstance(v,str) and k not in ('source','sampleRaw','sourceFile','sourceId','id','npc','name','translation') and not k.startswith('_'):
            out[k]=text(v,replacements(k))
    return out

def main():
    hashes=read(BASE/'upgrade-hashes.json');changes=[]
    candidates=subprocess.run(['rg','-l','-i','-P','-e',pattern.pattern,str(BASE/'translations')],capture_output=True,text=True,check=False)
    if candidates.returncode not in (0,1):raise RuntimeError(candidates.stderr)
    paths=[Path(p).relative_to(BASE) for p in candidates.stdout.splitlines()]
    explicit={r['id']:r for r in audit['differences']}
    for root in [BASE,ROOT/'dictionary-workspace',ROOT/'run/config/sapphire']:
        for relative in paths:
            p=root/relative;old=p.read_bytes();data=read(p);updated=adapt(data)
            if p.stem in explicit:updated['name']=explicit[p.stem]['new']
            if p.name=='gui-menus.json':
                for menu in updated['menus']:
                    if menu.get('labels',{}).get('Mangrove')=='红树林木':menu['labels']['Mangrove']='红树'
            if updated==data:continue
            backup=ROOT/'translation-state/vanilla-terms-20261022/before'/p.relative_to(ROOT)
            backup.parent.mkdir(parents=True,exist_ok=True)
            if not backup.exists():backup.write_bytes(old)
            if root==BASE:
                key=relative.as_posix();prior=set(hashes.get(key,'').split(','))-{''};prior.add(hashlib.sha256(old).hexdigest());hashes[key]=','.join(sorted(prior));changes.append(key)
            p.write_bytes(encoded(updated))
    (BASE/'upgrade-hashes.json').write_bytes(encoded(hashes))
    policy_path=ROOT/'tools/translation_policy.json';policy=read(policy_path)
    policy['exact_names'].update({k:v[1] for k,v in rules.items()})
    policy['terms'].update({'Sea Lumies':'海泡菜','Mangrove Log':'红树原木','TNT':'TNT'})
    policy['preserve_identifiers']=sorted(set(policy['preserve_identifiers'])|{'TNT'})
    policy_path.write_bytes(encoded(policy))
    reference=read(ROOT/'build/vanilla-reference/terms.json')
    audit.update(version=reference['version'],zhSha1=reference['zhSha1'],changedDictionaries=changes)
    (ROOT/'docs/vanilla-name-audit.json').write_bytes(encoded(audit))
    print('Reviewed',audit['checked'],'matches;',len(audit['differences']),'names corrected;',len(changes),'dictionaries changed per root.')
if __name__=='__main__':main()
