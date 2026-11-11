"""Complete stat-category headings and numeric rows in recognized SkyBlock menus."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'src/main/resources/defaults'
def read(p):return json.loads(p.read_text(encoding='utf-8'))
def encoded(v):return (json.dumps(v,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
def main():
    labels=read(BASE/'translations/tooltip-labels.json')
    # Only actual attributes; rarity labels and item types are intentionally absent.
    names=['Health','Defense','True Defense','Intelligence','Speed','Strength','Damage','Crit Chance','Crit Damage',
           'Attack Speed','Bonus Attack Speed','Ability Damage','Ferocity','Health Regen','Vitality','Mending','Mana Regen',
           'Swing Range','Magic Find','Pet Luck','Breaking Power','Mining Speed','Mining Fortune','Mining Spread',
           'Gemstone Fortune','Block Fortune','Pristine','Farming Fortune','Bonus Pest Chance','Overbloom',
           'Foraging Fortune','Sweep','Timber','Fishing Speed','Sea Creature Chance','Treasure Chance','Double Hook Chance',
           'Heat Resistance','Cold Resistance','Pressure Resistance','Fear','Hunting Fortune',
           'Combat Wisdom','Farming Wisdom','Fishing Wisdom','Mining Wisdom','Foraging Wisdom','Enchanting Wisdom']
    stats={n:labels[n] for n in names}
    stats.update({'Sea Creature Chance':'海洋生物概率','Trophy Chance':'奖杯概率',
                  'Gemstone Spread':'宝石扩散','Dwarven Metal Fortune':'矮人金属时运','Ore Fortune':'矿石时运',
                  'Respiration':'水下呼吸','Tracking':'追踪','Pull':'拉力','Charm Chance':'魅惑概率','Rift Damage':'裂隙伤害',
                  'Alchemy Wisdom':'炼药智慧','Carpentry Wisdom':'木工智慧','Runecrafting Wisdom':'符文制作智慧',
                  'Taming Wisdom':'驯养智慧','Social Wisdom':'社交智慧','Hunting Wisdom':'狩猎智慧'})
    for en,zh in {'Wheat':'小麦','Carrot':'胡萝卜','Potato':'马铃薯','Pumpkin':'南瓜','Cactus':'仙人掌',
                  'Mushroom':'蘑菇','Sunflower':'向日葵','Moonflower':'月光花','Sugar Cane':'甘蔗',
                  'Melon Slice':'西瓜片','Cocoa Beans':'可可豆','Nether Wart':'下界疣','Wild Rose':'野玫瑰',
                  'Fig':'无花果','Mangrove':'红树','Helix':'螺旋'}.items():stats[en+' Fortune']=zh+'时运'
    categories={'Combat Stats':'战斗属性','Defensive Stats':'防御属性','Offensive Stats':'进攻属性',
                'Mining Stats':'采矿属性','Farming Stats':'农业属性','Foraging Stats':'采伐属性','Fishing Stats':'钓鱼属性',
                'Hunting Stats':'狩猎属性','Wisdom Stats':'智慧属性','Misc Stats':'其他属性',
                'Miscellaneous Stats':'其他属性','Rift Stats':'裂隙属性'}
    hashes=read(BASE/'upgrade-hashes.json')
    for root in [BASE,ROOT/'dictionary-workspace',ROOT/'run/config/sapphire']:
        p=root/'translations/gui-menus.json';old=p.read_bytes();d=read(p)
        menu=next((m for m in d['menus'] if m['id']=='stats'),None)
        if menu is None:
            menu=dict(id='stats',titles={'Stats & Equipment':'属性与装备','Your Stats Breakdown':'属性明细',**categories},loreParagraphs=[],labels={})
            d['menus'].append(menu)
        rules=menu['loreParagraphs']
        existing={' '.join(r['source']) for r in rules+d['loreParagraphs']}
        def add(source,target,styles):
            if source not in existing:
                rules.append(dict(source=[source],translation=target,styleMappings=styles));existing.add(source)
        for en,zh in categories.items():add(en,zh,{en:zh})
        for en,zh in stats.items():
            for prefix in ('','{icon} '):
                add(prefix+en,prefix+zh,{en:zh})
                for separator in (' ',': '):add(prefix+en+separator+'{value}',prefix+zh+separator+'{value}',{en:zh})
        # Alpha is a legacy stat icon, but is a letter rather than an icon token.
        for suffix in ('',' {value}',': {value}'):
            add('α Sea Creature Chance'+suffix,'α 海洋生物概率'+suffix,{'Sea Creature Chance':'海洋生物概率'})
        new=encoded(d)
        if new==old:continue
        backup=ROOT/'translation-state/stats-menu-20261023/before'/p.relative_to(ROOT)
        backup.parent.mkdir(parents=True,exist_ok=True)
        if not backup.exists():backup.write_bytes(old)
        if root==BASE:
            key='translations/gui-menus.json';prior=set(hashes.get(key,'').split(','))-{''}
            prior.add(hashlib.sha256(old).hexdigest());hashes[key]=','.join(sorted(prior))
        p.write_bytes(new)
    (BASE/'upgrade-hashes.json').write_bytes(encoded(hashes))
    print(f'Covered {len(categories)} stat headings and {len(stats)} attribute names; {len(rules)} rules scoped to stat menus.')
if __name__=='__main__':main()
