"""User glossary: Slayer -> 杀手, only in translations with Slayer English sources."""
import json
import re
from attachment_tooltip_data import ROOTS, read

TERMS = {
    'Zombie Slayer':'僵尸杀手', 'Spider Slayer':'蜘蛛杀手', 'Wolf Slayer':'狼杀手',
    'Enderman Slayer':'末影人杀手', 'Vampire Slayer':'吸血鬼杀手', 'Blaze Slayer':'烈焰人杀手',
    'Ender Slayer':'末影杀手', 'End Slayer':'末地杀手', 'Undead Slayer':'亡灵杀手',
    'Giant Slayer':'巨人杀手', 'Arachnid Slayer':'蛛形杀手', 'Pack Slayer':'兽群杀手',
    'Slayers':'杀手', 'Slayer':'杀手',
}

def term(source, target):
    if not isinstance(target,str) or not re.search(r'\bSlayers?\b',source):return target
    for en,zh in TERMS.items():target=target.replace(en,zh)
    target=target.replace('猎手','杀手').replace('讨伐','杀手').replace('Blaze 杀手','烈焰人杀手')
    if source in TERMS:return TERMS[source]
    return target

def update(value):
    if isinstance(value,list):
        for child in value:update(child)
    elif isinstance(value,dict):
        if 'source' in value and 'translation' in value:
            source=value['source'];source=' '.join(source) if isinstance(source,list) else source
            value['translation']=term(source,value['translation'])
        if '_original_name' in value and 'name' in value:value['name']=term(value['_original_name'],value['name'])
        for key,child in value.items():
            # Never change server/NEU metadata, source keys, IDs or item IDs in the lookup scope.
            if key.startswith('_') or key in ('source','id'):continue
            if isinstance(child,(dict,list)):update(child)
            elif isinstance(child,str) and not re.fullmatch(r'[A-Z0-9_;]+',child):value[key]=term(key,child)

if __name__=='__main__':
    changed=0
    for root in ROOTS:
        for path in root.rglob('*.json'):
            if path.name=='enchantment-ids.json':continue
            value=read(path);before=json.dumps(value,ensure_ascii=False)
            update(value)
            if json.dumps(value,ensure_ascii=False)!=before:
                path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');changed+=1
    print('Updated dictionaries:',changed)
