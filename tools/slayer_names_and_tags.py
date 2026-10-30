"""User-requested RPG weapon names and verified item classification markers."""
import json
from attachment_tooltip_data import ROOT, ROOTS, read
from live_mining_tooltip_data import write

GROUPS = {
    '僵尸': {'REVENANT_SWORD':'镇魂弯刀','REAPER_SWORD':'斩魂弯刀',
             'AXE_OF_THE_SHREDDED':'碎魂长戟','REAPER_SCYTHE':'死神之镰'},
    '蜘蛛': {'RECLUSE_FANG':'隐毒之牙','TARANTULA_FANG':'蛛魔之牙',
             'SCORPION_FOIL':'蝎尾刺剑','STING':'弑蛛之锋',
             'SCORPION_BOW':'猎蛛短弓','MOSQUITO_BOW':'饮血短弓'},
    '狼': {'SHAMAN_SWORD':'祭灵之剑','POOCH_SWORD':'啸月之剑'},
    '末影人': {'VOIDWALKER_KATANA':'踏虚太刀','VOIDEDGE_KATANA':'断空太刀',
               'VORPAL_KATANA':'斩魄太刀','ATOMSPLIT_KATANA':'裂界太刀'},
    '烈焰人': {'FIREDUST_DAGGER':'余烬之剑','BURSTFIRE_DAGGER':'焚魔之剑','HEARTFIRE_DAGGER':'炎狱之剑',
               'MAWDUST_DAGGER':'暮影之剑','BURSTMAW_DAGGER':'噬渊之剑','HEARTMAW_DAGGER':'裂魂之剑'},
    '吸血鬼': {'SILVER_LACED_KARAMBIT':'银纹猎血刃','SILVERTWIST_KARAMBIT':'银旋诛血刃',
               'STEAK_STAKE':'猎血木桩','SHARP_STEAK_STAKE':'嗜血穿心桩'},
}
NAMES = {key:value for group in GROUPS.values() for key,value in group.items()}
NAMES.update({'NECRON_BLADE':'未完型的Necron之刃','MIDAS_STAFF':'米达斯法杖',
              'MIDAS_JEWEL':'米达斯宝石','MIDAS_SWORD':'米达斯之剑',
              'STARRED_MIDAS_STAFF':'\ue068 米达斯法杖','STARRED_MIDAS_SWORD':'\ue068 米达斯之剑',
              'KING_MIDAS_MINIBOSS':'米达斯国王 (小头目)'})

def replace_zh(value, replacements):
    for old,new in replacements:
        value=value.replace(old,new)
    return value.replace('迈达斯','米达斯')

def main():
    base=ROOTS[0]
    records=[]
    for group, names in GROUPS.items():
        for id, target in names.items():
            d=read(base/'items'/f'{id}.json')
            records.append(dict(group=group,id=id,english=d['_original_name'],chinese=target))
    changed=[]
    for root in ROOTS:
        replacements=sorted([(read(root/'items'/f'{id}.json')['name'],target)
                             for id,target in NAMES.items()
                             if read(root/'items'/f'{id}.json')['name']!=target
                             # Short common nouns (e.g. Sting = 毒刺) are not
                             # necessarily references to this named weapon.
                             and len(read(root/'items'/f'{id}.json')['name'])>2],key=lambda x:-len(x[0]))
        for p in (root/'items').glob('*.json'):
            d=read(p);before=json.dumps(d,ensure_ascii=False)
            if p.stem in NAMES:
                old=d['name'];d['name']=NAMES[p.stem]
                # Remove stale name fragments; map the full English name so
                # a new literary name inherits the original name's style.
                d['styleMappings']={k:v for k,v in d.get('styleMappings',{}).items()
                                    if v not in old or v in d['name']}
                d['styleMappings'][d['_original_name']]=d['name']
                d['_reviewed_name_revision']='2026-10-17-slayer-rpg'
            if 'name' in d:d['name']=replace_zh(d['name'],replacements)
            d['lore']={k:replace_zh(v,replacements) for k,v in d.get('lore',{}).items()}
            for row in [d]+d.get('loreParagraphs',[]):
                if 'translation' in row:row['translation']=replace_zh(row['translation'],replacements)
                if 'styleMappings' in row:row['styleMappings']={k:replace_zh(v,replacements) for k,v in row['styleMappings'].items()}
            if json.dumps(d,ensure_ascii=False)!=before:
                write(p,d);changed.append(str(p.relative_to(ROOT)))
        p=root/'tooltip-paragraphs.json';d=read(p)
        seen={' '.join(x['source']) for x in d['loreParagraphs']}
        def add(source,target,styles=None):
            if source not in seen:
                d['loreParagraphs'].append(dict(source=[source],translation=target,styleMappings=styles or {}));seen.add(source)
        for en,zh in {'Undead':'亡灵','Arachnal':'蛛类'}.items():
            label='亡灵类' if en=='Undead' else zh
            add(f'This item is {en} {{icon0}}!',f'此物品属于{label} {{icon0}}!',{en:label,'This item is':'此物品属于'})
            add(f'This item is {en}!',f'此物品属于{label}!',{en:label,'This item is':'此物品属于'})
        # The historical Arachnal icon is a Cyrillic letter, not a symbol.
        add('This item is Arachnal Ж!','此物品属于蛛类 Ж!',{'Arachnal':'蛛类','This item is':'此物品属于','Ж':'Ж'})
        add('This item is consumed on use.','此物品使用后消耗.')
        add('This item is in its final form!','此物品已达最终形态!')
        for source in ['This item is no longer used in the game. It can be exchanged in your Legacy Trades menu - accessed with /legacytrades.',
                       'This item is no longer used throughout the game, and can be exchanged in your Legacy Trades menu, accessed through /legacytrades.']:
            add(source,'此物品已停用,可输入 /legacytrades 打开旧物兑换菜单进行兑换.',{'Legacy Trades':'旧物兑换','/legacytrades':'/legacytrades'})
        write(p,d)
    out=ROOT/'translation-state/live-mining/slayer-names.json'
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (ROOT/'src/test/resources/slayer-name-fixtures.json').write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(f'Updated {len(records)} Slayer weapon names and {len(changed)} item dictionary files across 3 copies; item tags synchronized.')

if __name__=='__main__':main()
