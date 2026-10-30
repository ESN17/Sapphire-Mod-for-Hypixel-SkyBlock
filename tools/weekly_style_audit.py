"""Prepare all revised units with their public NEU colored source for the Java renderer audit."""
import collections,json,re
from pathlib import Path
from dictionary_tool import read,write,plain
from refine_weekly import STATE,BASE,TOKEN
from weekly_result_quality import restore,icons
def signature(s):return TOKEN.sub('¤',s)
def recolor(source,styled):
    original=plain(styled)
    if original==source:return styled
    if signature(original)!=signature(source):return None
    tokens=iter(TOKEN.findall(source));parts=[];start=0;positions=[];visible=''
    for m in re.finditer(r'§[0-9a-fk-or]|.',styled,re.I):
        if m.group().startswith('§'):continue
        positions.append(m.start());visible+=m.group()
    for m in TOKEN.finditer(visible):
        a=positions[m.start()];b=positions[m.end()-1]+1
        parts.extend([styled[start:a],next(tokens)]);start=b
    parts.append(styled[start:]);return ''.join(parts)
def prepare():
    plan=read(STATE/'refine-plan.json');old=read(BASE/'translation-state/plan.json')
    lookup={};allraw=[]
    def add(source,styled):lookup.setdefault(signature(source),[]).append(styled)
    for j in old['jobs'].values():add(j['source'],j['styled_source'])
    # Updated enchantment metadata and item names can differ from the earlier bulk plan.
    for path in (BASE/'build/neu-reference/items').glob('*.json'):
        data=json.loads(path.read_text(encoding='utf-8-sig'));group=[]
        add(plain(data.get('displayname','')),data.get('displayname',''))
        for line in data.get('lore',[])+['']:
            if plain(line).strip():
                add(plain(line),line);group.append(line)
            elif group:
                joined=' '.join(group);add(plain(joined),joined)
                if data.get('itemid')=='minecraft:enchanted_book':
                    for i in range(1,len(group)):
                        joined=' '.join(group[i:]);add(plain(joined),joined)
                group=[]
    rows=[];missing=[]
    for identity,j in plan['jobs'].items():
        result=STATE/'refined'/(identity+'.json')
        if not result.exists():continue
        styled=j.get('styled_source') if plain(j.get('styled_source',''))==j['source'] else None
        for raw in ([] if styled is not None else lookup.get(signature(j['source']),[])):
            styled=recolor(j['source'],raw)
            if styled is not None:break
        if styled is None:missing.append(identity);styled=j['source']
        previous=read(result);r=restore(j['source'],previous)
        if previous!=r:write(result,r)
        rows.append(dict(id=identity,kind=j['kind'],source=j['source'],raw=styled,text=r['text'],styleMappings=r['styleMappings']))
    out=STATE/'audit';out.mkdir(exist_ok=True)
    for i in range(0,len(rows),250):write(out/('part-'+str(i//250).zfill(3)+'.json'),{'units':rows[i:i+250]})
    write(STATE/'style-audit-input.json',{'units':len(rows),'without_colored_source':missing})
    print(json.dumps({'audit_units':len(rows),'without_colored_source':len(missing)}),flush=True)
if __name__=='__main__':prepare()
