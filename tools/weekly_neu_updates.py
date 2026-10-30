"""Add current public NEU source variants without deleting existing compatible rules."""
import collections,json,re
from dictionary_tool import read,write,plain
from refine_weekly import STATE,BASE,key
plan=read(STATE/'refine-plan.json');report=read(STATE/'neu-comparison.json');added=0
for filename in report['changed']:
    data=plan['files'][filename];raw=read(BASE/'build/weekly-reference/neu/items'/filename)
    data['_original_lore']=[plain(s) for s in raw.get('lore',[])]
    existing={' '.join(p['source']) for p in data.get('loreParagraphs',[])};groups=[];group=[]
    for line in raw.get('lore',[])+['']:
        if plain(line).strip():group.append(line)
        elif group:groups.append(group);group=[]
    for group in groups:
        index=0
        def sub(m):
            global index
            if m.group().startswith('§'):return m.group()
            value='{{N'+str(index)+'}}';index+=1;return value
        colored=re.sub(r'§[0-9a-fk-or]|(?<![A-Za-z])[-+]?\d+(?:[,.]\d+)*%?',sub,' '.join(group))
        source=plain(colored)
        if source in existing:continue
        # These nine upstream changes are line wraps, corrected wording, labels and binding status.
        previous=next((p['translation'] for p in data.get('loreParagraphs',[]) if ' '.join(p['source']).replace('to to','to').replace('The Great Spook','Great Spook')==source.replace('The Great Spook','Great Spook')),source)
        paragraph=dict(source=[source],translation=previous,styleMappings={});pos=len(data['loreParagraphs']);data['loreParagraphs'].append(paragraph);existing.add(source)
        identity=key(['paragraph',source,previous,{}]);plan['refs'][filename].append(['paragraph',pos,identity])
        plan['jobs'].setdefault(identity,dict(id=identity,kind='paragraph',source=source,previous=previous,styleMappings={},styled_source=colored));added+=1
write(STATE/'refine-plan.json',plan)
print(json.dumps({'new_source_variants':added,'units':len(plan['jobs'])}))
