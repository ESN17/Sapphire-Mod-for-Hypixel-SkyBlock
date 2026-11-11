"""Add reviewed live lore variants without replacing existing translations."""
import json
from attachment_tooltip_data import ROOT,ROOTS,read

def main():
    row={
        'source':['Drops rarely from Gemstones while',"Mayor Cole's Mining Fiesta is active."],
        'translation':'在 Cole 市长的采矿狂欢期间,挖掘宝石有较低概率掉落.',
        'styleMappings':{'Gemstones':'宝石',"Mayor Cole's": 'Cole 市长的','Mining Fiesta':'采矿狂欢'}
    }
    for root in ROOTS:
        p=root/'items/GLOSSY_GEMSTONE.json';data=read(p)
        if any(r['source']==row['source'] for r in data.get('loreParagraphs',[])):continue
        backup=ROOT/'translation-state/bazaar-live/backup'/p.relative_to(ROOT)
        if not backup.exists():backup.parent.mkdir(parents=True,exist_ok=True);backup.write_bytes(p.read_bytes())
        data.setdefault('loreParagraphs',[]).append(row)
        p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Added current Glossy Gemstone lore variant in all 3 dictionaries; existing wording retained.')

if __name__=='__main__':main()
