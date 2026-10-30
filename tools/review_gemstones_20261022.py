"""Reviewed gemstone names, lore and Pristine notifications; no remote translation."""
import hashlib
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'src/main/resources/defaults'
ROOTS=[BASE,ROOT/'dictionary-workspace',ROOT/'run/config/sapphire']
GEMS={
 'AMBER':('Amber','琥珀','Mining Speed','采矿速度',None,None),
 'AMETHYST':('Amethyst','紫水晶','Defense','防御',None,None),
 'AQUAMARINE':('Aquamarine','海蓝宝石','Fishing Speed','钓鱼速度','additional charge','额外充能'),
 'CITRINE':('Citrine','黄水晶','Foraging Fortune','伐木时运','highlight treasures','标出宝藏'),
 'JADE':('Jade','翡翠','Mining Fortune','采矿时运',None,None),
 'JASPER':('Jasper','碧玉','Strength','力量',None,None),
 'ONYX':('Onyx','缟玛瑙','Crit Damage','暴击伤害','bonus treasures','额外宝藏'),
 'OPAL':('Opal','蛋白石','True Defense','真实防御',None,None),
 'PERIDOT':('Peridot','橄榄石','Farming Fortune','耕作时运','bonus Fossil Essence','额外化石精华'),
 'RUBY':('Ruby','红宝石','Health','生命值',None,None),
 'SAPPHIRE':('Sapphire','蓝宝石','Intelligence','智力',None,None),
 'TOPAZ':('Topaz','黄玉','Pristine','提纯',None,None),
}
TIERS={'ROUGH':'粗糙','FLAWED':'瑕疵','FINE':'精致','FLAWLESS':'无瑕','PERFECT':'完美'}

def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def encoded(d):return (json.dumps(d,ensure_ascii=False,indent=2)+'\n').encode('utf8')

def main():
 hashes=read(BASE/'upgrade-hashes.json');changes=[]
 def save(p,d):
  old=p.read_bytes();new=encoded(d)
  if old==new:return
  backup=ROOT/'translation-state/feedback-20261022/before'/p.relative_to(ROOT)
  backup.parent.mkdir(parents=True,exist_ok=True)
  if not backup.exists():backup.write_bytes(old)
  if p.is_relative_to(BASE):
   key=p.relative_to(BASE).as_posix();prior=set(hashes.get(key,'').split(','))-{''}
   prior.add(hashlib.sha256(old).hexdigest());hashes[key]=','.join(sorted(prior))
  p.write_bytes(new);changes.append(str(p.relative_to(ROOT)))
 for root in ROOTS:
  locations=read(root/'translations/locations.json')
  for gem,(en,zh,stat,stat_zh,bonus,bonus_zh) in GEMS.items():
   for tier,tier_zh in TIERS.items():
    p=root/'translations/items'/f'{tier}_{gem}_GEM.json';d=read(p)
    glyph=d['_original_name'][0]
    d['name']=f'{glyph} {tier_zh}{zh}'
    # The complete material phrase avoids mapping the final "Gemstone" to
    # a second 宝石 inside names such as Ruby/Sapphire/Aquamarine.
    d['styleMappings']={tier.title():tier_zh,en+' Gemstone':zh}
    for row in d['loreParagraphs']:
     source=' '.join(row['source']);maps={}
     if source.startswith('A slightly better version of '):
      target=f'品质稍有提升的{zh},但仍需进一步打磨.';maps[en]=zh
     elif source.startswith('A type of '):
      target=f'经过精心加工的{zh}.';maps[en]=zh
     elif source.startswith('A near perfect version of '):
      target=f'品质近乎完美的{zh}.';maps[en]=zh
     elif source.startswith('A perfectly refined '):
      target=f'完美精炼的{zh}.';maps['refined '+en]='精炼的'+zh
     elif source.startswith('Some say that when harnessed '):
      target=f'据说,运用得当时,它能为持有者提供额外的 {glyph} {stat_zh}'
      maps={'harnessed properly':'运用得当',glyph+' '+stat:glyph+' '+stat_zh}
      if bonus:
       target+=';也有人说它能提供'+bonus_zh+'.' if gem=='AQUAMARINE' else ';也有人说它能'+bonus_zh+'.' if gem=='CITRINE' else ';也有人说它能带来'+bonus_zh+'.'
       if gem=='PERIDOT':maps['Fossil Essence']='化石精华'
       else:maps[bonus]=bonus_zh
      else:target+='.'
     elif source.startswith('Mined from the cold depths'):
      place=locations['Glacite Tunnels'];target=f'开采自{place}寒冷的深处.';maps={'Glacite Tunnels':place}
     elif source.startswith('Harvested from the plentiful'):
      place=locations['Crystal Hollows'];target=f'采自{place}中储量丰富的宝石矿脉.';maps={'Crystal Hollows':place}
     elif source.startswith('Found sprinkled around'):
      place=locations['Crimson Isle'];target=f'散布于{place}各处.';maps={'Crimson Isle':place}
     elif source=='Collection Item':target='收藏品';maps={'Collection Item':'收藏品'}
     elif source.endswith(' GEMSTONE'):
      rarity={'COMMON':'普通','UNCOMMON':'罕见','RARE':'稀有','EPIC':'史诗','LEGENDARY':'传说'}[source.split()[0]]
      target=rarity+'宝石';maps={source.split()[0]:rarity,'GEMSTONE':'宝石'}
     else:raise ValueError('Unreviewed gemstone paragraph: '+source)
     row['translation']=target;row['styleMappings']=maps
    save(p,d)
  p=root/'translations/system-messages.json';data=read(p)
  for row in data['rules']:
   if row['source']=='PRISTINE! You found {icon} {name} x{count}!':
    row['translation']='提纯！ 你获得了 {icon} {name} x{count}!'
    row['styleMappings']={'PRISTINE':'提纯','You found':'你获得了'}
  save(p,data)
  # Keep gem-related equipment and commission text consistent with the review.
  def normalize_refs(value):
   if isinstance(value,str):
    return value.replace('红宝石宝石','红宝石').replace('蓝宝石宝石','蓝宝石').replace('纯净度','提纯')
   if isinstance(value,list):return [normalize_refs(v) for v in value]
   if isinstance(value,dict):return {k:normalize_refs(v) for k,v in value.items()}
   return value
  for filename in ['system-messages.json']+['items/'+item+'.json' for item in
    ['BAL;3','BAL;4','CHILLED_PRISTINE_POTATO','GEMSTONE_DRILL_1','GEMSTONE_DRILL_2','GEMSTONE_DRILL_3','GEMSTONE_DRILL_4','GLACITE_GOLEM;4']]:
   p=root/'translations'/filename;save(p,normalize_refs(read(p)))
 (BASE/'upgrade-hashes.json').write_bytes(encoded(hashes))
 policy_path=ROOT/'tools/translation_policy.json';policy=read(policy_path)
 for gem,(en,zh,*_) in GEMS.items():
  for tier,tier_zh in TIERS.items():
   d=read(BASE/'translations/items'/f'{tier}_{gem}_GEM.json')
   policy['exact_names'][d['_original_name']]=d['name']
   policy['exact_names'][f'{tier.title()} {en} Gemstone']=tier_zh+zh
 policy_path.write_bytes(encoded(policy))
 print(f'Reviewed 60 gemstones and Pristine notification; {len(changes)} files updated.')

if __name__=='__main__':main()
