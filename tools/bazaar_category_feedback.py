"""Explicit user item-name corrections from 2026-10-17; preserve English metadata."""
import json
from attachment_tooltip_data import ROOTS, read

NAMES = {'VITAMIN_DEATH':'亡灵维生素', 'ECCENTRIC_PAINTING':'古怪油画', 'ECCENTRIC_PAINTING_BUNDLE':'古怪油画礼包'}

def correct(data, item_id):
    data['name']=NAMES[item_id]
    for source, value in data.get('lore',{}).items():
        data['lore'][source]=value.replace('古怪画作','古怪油画').replace('维生素死亡','亡灵维生素')
    for paragraph in data.get('loreParagraphs',[]):
        paragraph['translation']=paragraph['translation'].replace('古怪画作','古怪油画').replace('维生素死亡','亡灵维生素')
        for source,value in paragraph.get('styleMappings',{}).items():
            paragraph['styleMappings'][source]=value.replace('古怪画作','古怪油画').replace('维生素死亡','亡灵维生素')
    for source,value in data.get('styleMappings',{}).items():
        data['styleMappings'][source]=value.replace('古怪画作','古怪油画').replace('维生素死亡','亡灵维生素')
    return data

if __name__=='__main__':
    for root in ROOTS:
        for item_id in NAMES:
            path=root/'items'/f'{item_id}.json'
            data=correct(read(path),item_id)
            path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Updated 3 item names in all 3 dictionary roots')
