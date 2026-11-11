"""Add reviewed installed-part/scroll text without replacing existing translations.
Generate regression fixtures from the local NEU reference (no credentials/network).
"""
from pathlib import Path
import collections
import json
import re

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'src/main/resources/defaults/translations'
ROOTS=[BASE,ROOT/'dictionary-workspace/translations',ROOT/'run/config/sapphire/translations']
BACKUP=ROOT/'translation-state/attachments/backup'
VARIABLE=re.compile(r'\{\{[A-Za-z][A-Za-z0-9_]*\}\}|(?<!\{)\{[A-Za-z][A-Za-z0-9_]*\}(?!\})')
FORMAT=re.compile(r'§[0-9a-fk-or]',re.I)

def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def write(p,data):
    encoded=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    if p.exists() and p.read_bytes()==encoded:return
    backup=BACKUP/p.relative_to(ROOT)
    if p.exists() and not backup.exists():
        backup.parent.mkdir(parents=True,exist_ok=True);backup.write_bytes(p.read_bytes())
    p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(encoded)

def main():
    rules=[]
    def add(source,target,styles=None):
        source=[source] if isinstance(source,str) else source
        assert collections.Counter(VARIABLE.findall(' '.join(source)))==collections.Counter(VARIABLE.findall(target))
        rules.append(dict(source=source,translation=target,styleMappings=styles or {}))

    part_ids=['MITHRIL_FUEL_TANK','TITANIUM_FUEL_TANK','GEMSTONE_FUEL_TANK','PERFECTLY_CUT_FUEL_TANK',
        'MITHRIL_DRILL_ENGINE','TITANIUM_DRILL_ENGINE','RUBY_POLISHED_DRILL_ENGINE',
        'SAPPHIRE_POLISHED_DRILL_ENGINE','AMBER_POLISHED_DRILL_ENGINE',
        'GOBLIN_OMELETTE','GOBLIN_OMELETTE_PESTO','GOBLIN_OMELETTE_SPICY',
        'GOBLIN_OMELETTE_SUNNY_SIDE','GOBLIN_OMELETTE_BLUE_CHEESE']
    for id in part_ids:
        item=read(BASE/'items'/f'{id}.json')
        add(item['_original_name'],item['name'])
        # Installed upgrade modules append "Part"; tanks and engines do not.
        if 'OMELETTE' in id:add(item['_original_name']+' Part',item['name']+' 配件')
    add('{capacity} Max Fuel Capacity','{capacity} 最大燃料容量')
    add('{cooldown} Pickaxe Ability Cooldown','{cooldown} 镐子技能冷却')
    for en,zh,icon in [('Mining Speed','挖掘速度',''),('Mining Fortune','挖掘时运','')]:
        # Keep the server's known stat icon literal; numbers remain live captures.
        add('+{amount}'+icon+' '+en,'+{amount}'+icon+' '+zh,{en:zh})
        add('+{amount} '+en,'+{amount} '+zh,{en:zh})
    add('Grants a {chance} chance to not consume Fuel when mining.',
        '挖矿时有 {chance} 概率不消耗燃料.',{'Fuel':'燃料'})
    add('Earn {amount} Powder from all sources.','所有来源的粉末收益提高 {amount}.',{'Powder':'粉末'})
    add('Grants +{amount} Mining Fortune, but this Drill consumes quadruple the Fuel.',
        '提供 +{amount} 挖掘时运,但此钻头消耗四倍燃料.',{'Mining Fortune':'挖掘时运','Drill':'钻头','Fuel':'燃料'})
    add('Adds {level} Level to all of your unlocked Heart of the Mountain perks.',
        '所有已解锁的山峰之心技能增加 {level} 等级.',{'Level':'等级','Heart of the Mountain':'山峰之心'})

    add('Scroll Abilities:','卷轴技能:')
    add('Ability: Wither Impact  RIGHT CLICK','技能: 凋零冲击  右键点击',
        {'Ability':'技能','Wither Impact':'凋零冲击','RIGHT CLICK':'右键点击'})
    add('Teleport {distance} blocks ahead of you dealing {damage} damage to nearby enemies. Also reduces your damage taken and grants an absorption shield for {duration} seconds.',
        '向前传送 {distance} 格,对附近敌人造成 {damage} 伤害.同时降低受到的伤害,并获得伤害吸收护盾,持续 {duration} 秒.',
        {'blocks':'格','seconds':'秒'})
    # Single-scroll installations use the same server text as the scroll item.
    for id in ['IMPLOSION_SCROLL','SHADOW_WARP_SCROLL','WITHER_SHIELD_SCROLL']:
        item=read(BASE/'items'/f'{id}.json');active=False
        for p in item['loreParagraphs']:
            s=' '.join(p['source'])
            if s.startswith('Ability:'):active=True
            if active and not s.startswith('EPIC '):add(p['source'],p['translation'],p.get('styleMappings'))

    added=0
    for root in ROOTS:
        p=root/'tooltip-paragraphs.json';data=read(p)
        seen={' '.join(x['source']):x for x in data['loreParagraphs']}
        for rule in rules:
            key=' '.join(rule['source'])
            if key in seen:
                assert seen[key]['translation']==rule['translation'],f'Existing translation conflict: {key}'
                continue
            data['loreParagraphs'].append(rule);seen[key]=rule
            if root==BASE:added+=1
        write(p,data)

    # Audit the damage abilities in the current wiki table, including upgraded
    # variants and additional current NEU weapons. No claim that every ability scales.
    weapons='ALCHEMISTS_STAFF ASPECT_OF_THE_DRAGON BAT_WAND STARRED_BAT_WAND BINGO_BLASTER BONZO_STAFF STARRED_BONZO_STAFF CELESTE_WAND CRYPT_DREADLORD_SWORD CRYPT_WITHERLORD_SWORD EMBER_ROD FIRE_FURY_STAFF FIRE_VEIL_WAND FROZEN_SCYTHE GIANTS_SWORD GIANTS_EYE_SWORD GLACIAL_SCYTHE STARRED_GLACIAL_SCYTHE GOLEM_SWORD ICE_SPRAY_WAND STARRED_ICE_SPRAY_WAND INK_WAND JERRY_STAFF LEAPING_SWORD MIDAS_STAFF STARRED_MIDAS_STAFF PIGMAN_SWORD RUNIC_STAFF SILK_EDGE_SWORD STAFF_OF_THE_VOLCANO STARLIGHT_WAND VOODOO_DOLL VOODOO_DOLL_WILTED YETI_SWORD STARRED_YETI_SWORD'.split()
    fixtures=[]
    for id in weapons:
        d=read(ROOT/'build/neu-reference/items'/f'{id}.json');lore=[];active=False
        for line in d['lore']:
            plain=FORMAT.sub('',line)
            if plain.startswith('Ability:'):active=True
            if active and plain.startswith('Mana Cost:'):break
            if active:lore.append(line)
        assert lore,id
        fixtures.append(dict(id=id,name=d['displayname'],lore=lore))
    write(ROOT/'src/test/resources/dynamic-ability-fixtures.json',fixtures)
    installed=[]
    for id in part_ids:
        d=read(ROOT/'build/neu-reference/items'/f'{id}.json');body=[]
        for line in d['lore'][2:]:
            if FORMAT.sub('',line).startswith('Put this item'):break
            body.append(line)
        name=FORMAT.sub('',d['displayname'])+(' Part' if 'OMELETTE' in id else '')
        installed.append(dict(id=id,lore=['§a'+name]+body))
    for id in ['IMPLOSION_SCROLL','SHADOW_WARP_SCROLL','WITHER_SHIELD_SCROLL']:
        d=read(ROOT/'build/neu-reference/items'/f'{id}.json');body=[];active=False
        for line in d['lore']:
            plain=FORMAT.sub('',line)
            if plain.startswith('Ability:'):active=True
            if plain.startswith('EPIC '):break
            if active:body.append(line)
        installed.append(dict(id=id,lore=body))
    write(ROOT/'src/test/resources/installed-attachment-fixtures.json',installed)
    runes=[]
    for p in sorted((ROOT/'build/neu-reference/items').glob('*_RUNE;*.json')):
        d=read(p);m=re.search(r'runes:\{([A-Za-z0-9_]+):([1-3])\}',d['nbttag'])
        assert m,p
        runes.append(dict(id=p.stem,kind=m[1],level=int(m[2]),name=d['displayname'],lore=d['lore']))
    write(ROOT/'src/test/resources/rune-fixtures.json',runes)
    report={'attachmentSharedRules':len({' '.join(r['source']) for r in rules}),'drillParts':part_ids,'damageWeaponAudit':weapons,
        'runeFamilies':len({r['kind'] for r in runes}),'runeVariants':len(runes),
        'sources':['https://hypixelskyblock.minecraft.wiki/w/Damage_Calculation',
        "https://hypixelskyblock.minecraft.wiki/w/Necron's_Blade_Scrolls",
        'https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO',
        'User screenshots: installed drill parts and Wither Impact'],
        'note':'Displayed numbers are copied from the server; no damage calculation is performed.'}
    write(ROOT/'translation-state/attachments/report.json',report)
    print(json.dumps({k:v for k,v in report.items() if isinstance(v,int)},ensure_ascii=False))

if __name__=='__main__':main()
