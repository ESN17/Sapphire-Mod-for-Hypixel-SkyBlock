"""Add live screenshot aliases and stat-icon formats, preserving other entries."""
import json
from pathlib import Path
from attachment_tooltip_data import ROOT, ROOTS, read

BACKUP=ROOT/'translation-state/live-mining/backup'
def write(path,data):
    raw=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    if path.exists() and path.read_bytes()==raw:return
    backup=BACKUP/path.relative_to(ROOT)
    if path.exists() and not backup.exists():
        backup.parent.mkdir(parents=True,exist_ok=True);backup.write_bytes(path.read_bytes())
    path.write_bytes(raw)

def main():
    for root in ROOTS:
        p=root/'name-modifiers.json';data=read(p) if p.exists() else {}
        data['Shiny']='闪耀';write(p,data)
        p=root/'tooltip-labels.json';data=read(p)
        for en,zh in {'Pristine':'提纯','Block Fortune':'方块时运'}.items():
            assert en not in data or data[en]==zh
            data[en]=zh
        write(p,data)
        p=root/'tooltip-paragraphs.json';data=read(p)
        seen={' '.join(x['source']):x for x in data['loreParagraphs']}
        def add(source,target,styles=None):
            if source in seen:
                assert seen[source]['translation']==target,source
                return
            row=dict(source=[source],translation=target,styleMappings=styles or {})
            data['loreParagraphs'].append(row);seen[source]=row
        aliases={'Titanium Drill Engine':'TITANIUM_DRILL_ENGINE','Mithril Drill Engine':'MITHRIL_DRILL_ENGINE',
            'Sunny Side Goblin Egg':'GOBLIN_OMELETTE_SUNNY_SIDE','Blue Cheese Goblin Egg':'GOBLIN_OMELETTE_BLUE_CHEESE',
            'Spicy Goblin Egg':'GOBLIN_OMELETTE_SPICY','Pesto Goblin Egg':'GOBLIN_OMELETTE_PESTO','Goblin Egg':'GOBLIN_OMELETTE'}
        for en,id in aliases.items():
            name=read(root/'items'/f'{id}.json')['name']
            add(en+' Part',name+' 配件')
            # Bare Goblin Eggs are different items; only translate their installed
            # "Part" heading. Bare drill-engine aliases are unambiguous.
            if en.endswith('Engine'):add(en,name)
        add('Grants {statIcon0} Mining Spread towards Blocks.',
            '挖掘方块时提供 {statIcon0} 采矿扩散.',{'Mining Spread':'采矿扩散','Blocks':'方块','Grants':'提供'})
        add('Grants {statIcon0} Mining Fortune, which increases your chance for multiple drops.',
            '提供 {statIcon0} 挖掘时运,提高获得多份掉落物的概率.',{'Mining Fortune':'挖掘时运','Grants':'提供'})
        add('Gain {statIcon0} Mining Wisdom and a {chance} chance to drop an enchanted item.',
            '获得 {statIcon0} 采矿智慧,并有 {chance} 概率掉落附魔物品.',{'Mining Wisdom':'采矿智慧','Gain':'获得'})
        write(p,data)
    print('Live part aliases, Pristine/Block Fortune, and stat-icon effects synchronized.')

if __name__=='__main__':main()
