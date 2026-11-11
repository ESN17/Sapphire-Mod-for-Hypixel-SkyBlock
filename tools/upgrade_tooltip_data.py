"""Build shared display dictionaries from local, reviewed NEU translations.
Backs up changed files before updating the three project dictionary copies.
"""
from pathlib import Path
import collections
import copy
import itertools
import json
import re
import zipfile

ROOT=Path(__file__).resolve().parents[1]
BACKUP=ROOT/'translation-state/upgrade-tooltips/backup'
BASE=ROOT/'src/main/resources/defaults/translations'
ROOTS=[BASE,ROOT/'dictionary-workspace/translations',ROOT/'run/config/sapphire/translations']
LABELS={
 'Gear Score':'装备评分','Damage':'伤害','Strength':'力量','Health':'生命值','Defense':'防御','Defence':'防御',
 'True Defense':'真实防御','True Defence':'真实防御','Intelligence':'智力','Speed':'速度','Swing Range':'挥击范围',
 'Crit Chance':'暴击率','Crit Damage':'暴击伤害','Attack Speed':'攻击速度','Bonus Attack Speed':'额外攻击速度',
 'Ability Damage':'技能伤害','Magic Find':'稀有物品概率加成','Pet Luck':'宠物幸运','Ferocity':'残暴值',
 'Mining Speed':'挖掘速度','Mining Fortune':'挖掘时运','Mining Spread':'采矿扩散','Gemstone Fortune':'宝石时运',
 'Farming Fortune':'农业时运','Foraging Fortune':'采伐时运','Foraging Wisdom':'采伐智慧','Mining Wisdom':'采矿智慧',
 'Combat Wisdom':'战斗智慧','Fishing Wisdom':'钓鱼智慧','Farming Wisdom':'农业智慧','Enchanting Wisdom':'附魔智慧',
 'Sea Creature Chance':'海怪概率','Fishing Speed':'钓鱼速度','Treasure Chance':'宝藏概率','Double Hook Chance':'双钩概率',
 'Vitality':'活力','Mending':'治疗效果','Health Regen':'生命回复','Mana Regen':'魔力回复','Mending':'治疗效果',
 'Hunting Fortune':'狩猎时运','Bonus Pest Chance':'额外害虫概率','Overbloom':'稀有作物概率加成',
 'Cold Resistance':'寒冷抗性','Heat Resistance':'热量抗性','Pressure Resistance':'水压抗性','Breaking Power':'破坏力',
 'Sweep':'横扫','Timber':'伐木','Fear':'恐惧','Kills':'击杀数','Price paid':'支付价格','Gifted coins':'赠送硬币',
 'Strength Bonus':'力量加成','Damage Bonus':'伤害加成','Mana Cost':'魔力消耗','Soulflow Cost':'灵魂流消耗',
 'COMMON':'普通','UNCOMMON':'罕见','RARE':'稀有','EPIC':'史诗','LEGENDARY':'传说','MYTHIC':'神话',
 'DIVINE':'神圣','SPECIAL':'特殊','VERY SPECIAL':'非常特殊','ULTIMATE':'终极','DUNGEON':'地牢','SHINY':'闪耀',
 'SWORD':'剑','LONGSWORD':'长剑','BOW':'弓','SHORTBOW':'短弓','HELMET':'头盔','CHESTPLATE':'胸甲','LEGGINGS':'护腿',
 'BOOTS':'靴子','BELT':'腰带','CLOAK':'披风','NECKLACE':'项链','BRACELET':'手镯','GLOVES':'手套','GAUNTLET':'护手',
 'PICKAXE':'镐','DRILL':'钻头','AXE':'斧','HOE':'锄','SHOVEL':'锹','SHEARS':'剪刀','FISHING':'钓鱼','ROD':'竿',
 'WEAPON':'武器','ARMOR':'盔甲','EQUIPMENT':'装备','TOOL':'工具','ACCESSORY':'饰品','WAND':'法杖','DEPLOYABLE':'部署物',
 'REFORGE':'洗练','STONE':'石','RUNE':'符文','DYE':'染料','COSMETIC':'装饰品','PET':'宠物','ITEM':'物品',
 'Co-op Soulbound':'合作档灵魂绑定','Soulbound':'灵魂绑定','Dyed':'染色','Bonus':'加成','Coins':'硬币',
}
BONUSES={
 'Ambered':'采矿时发现哥布林的概率提高 {{N0}}.',
 'Beady':'对  害虫提供 {{N0}} 农业时运.',
 'Blazing':'每拥有 {{N0}}♨ 热量,发现蠕虫的概率提高 {{N1}}.',
 'Blessed':'收获作物时,有 {{N0}} 概率获得附魔形态的产物.',
 'Thorny':'所有盔甲上的荆棘附魔每提高一级,额外提供 {{N0}} 稀有作物概率加成.',
 'Calcified':'面对海怪时提供 {{N0}} 防御.',
 'Candied':'万圣节活动期间,生物掉落糖果的概率提高 {{N0}}.',
 'Buzzing':'此物品对  害虫造成 x{{N0}} 伤害.',
 'Erudite':'狮鹫地穴链始终额外包含 {{N0}} 个地穴.',
 'Perfect':'提供 {{N0}}  防御.',
 'Fleet':'挖掘方块时提供 {{N0}}  采矿扩散.',
 'Fabled':' 暴击有概率造成至多 {{N0}} 额外  伤害.',
 'Renowned':'提供 {{N0}}  稀有物品概率加成,并使所有战斗属性提高 {{N1}}.',
 'Royal':'矮人矿洞采矿事件中的 ᠅ 秘银粉末收益提高 {{N0}}.',
 'Hyper':'使用传送技能后获得 {{N0}} 速度,持续 {{N1}}s.',
 'Sticky':'捕获水属性碎片时提供 {{N0}} 狩猎时运.',
 'Glacial':'每拥有 {{N1}}❄ 寒冷,提供 {{N0}} 挖掘时运.',
 'Fanged':'每第 {{N0}} 次近战命中生物时,造成 {{N1}}  伤害.',
 'Geometric':'在虫洞中钓鱼时,有 {{N0}} 概率额外钓到奖杯鱼.',
 'Lustrous':'Cole 市长的采矿狂欢生效期间,提供 {{N0}} 采矿扩散.',
 'Bountiful':'每次收获作物时获得 {{N0}} 硬币.',
 'Stiff':'使用此钓鱼竿击杀的海怪掉落 {{N0}} 鱼浆.',
 'Deep Fried':'盛宴期间发现调味料的概率提高 {{N0}}.',
 'Heated':'每拥有 {{N2}}♨ 热量,提供 {{N0}} 挖掘时运和 {{N1}} 挖掘速度.',
 'Chomp':'将此竿放在物品栏中时,每击杀一种不同的熔岩海怪,以后钓出该海怪时其  生命值降低 {{N0}}.',
 'Magnetic':'采矿时的经验收益提高 {{N0}}.',
 'Earthy':'耕作时获得 {{N0}} 播种粉尘.',
 'Mantid':'每击杀一只  害虫,获得 {{N0}} 额外害虫概率,持续 {{N1}} 分钟.',
 'Marshy':'采伐时获得 {{N0}} 森林低语.',
 'Gilded':'击杀生物时,有小概率赠送硬币给附近的一名玩家.',
 'Cubic':'受到来自  炼狱生物的 {{N0}}  伤害.',
 'Majestic':'在  小动物狩猎区内提供 {{N0}} 速度.',
 'Fruitful':'在采矿岛屿上提供 {{N0}} 防御.',
 'Precise':'命中生物头部时造成 {{N0}}  伤害.',
 'Trashy':'在  死水沼泽内提供 {{N0}} 宝藏概率.',
 'Stellar':'在  坠落之星附近采矿时,获得 {{N0}} ᠅ 秘银粉末和 {{N1}} 挖掘时运.',
 'Scraped':'若正在挖掘的方块与此物品的  破坏力相同,获得 {{N0}} 挖掘时运.',
 'Ancient':'每级地牢技能等级提供 {{N0}} 暴击伤害.',
 'Undead':'受到来自  亡灵生物的 {{N0}}  伤害.',
 'Blood-Soaked':'生命窃取、汲取和吸血附魔的等级提高 {{N0}} 级.',
 'Mithraic':'挖掘秘银和钛时提供 {{N0}} 挖掘时运.',
 'Loving':'提供 {{N0}}  技能伤害.',
 'Refined':'挖掘矿石时,有 {{N0}} 概率获得附魔形态的产物.',
 'Auspicious':'提供 {{N0}}  挖掘时运.',
 'Treacherous':'从宝藏钓获中获得 {{N0}} 冰之精华.',
 'Empowered':'在  地牢中提供 {{N0}} 治疗效果.',
 'Headstrong':'命中生物头部时造成 {{N0}}  伤害.',
 'Salty':'在  杰瑞工坊内提供 x{{N0}} 属性.',
 'Bloodshot':'击杀敌人时,有 {{N0}} 概率使其结茧!',
 'Spiritual':'在  地牢内击杀敌人时,有 {{N0}} 概率生成灵魂诱饵.',
 'Squeaky':' 害虫的生成速度提高 {{N0}}.',
 'Suspicious':'为物品的基础属性增加 {{N0}} 伤害.',
 'Dimensional':'挖掘钛时掉落 {{N0}} ᠅ 秘银粉末.',
 'Withered':'每级地牢技能等级提供 {{N0}} 力量.',
}
ANCHORS={**LABELS,'Catacombs':'地牢','The Catacombs':'地牢','Strength':'力量','Crit Damage':'暴击伤害',
 'Undead':'亡灵','Skeletal':'骷髅','Wither':'凋灵','mobs':'生物','damage':'伤害','Grants':'提供',
 'Pests':'害虫','Blocks':'方块','Worms':'蠕虫','Mithril Powder':'秘银粉末','Coins':'硬币','Combat Stats':'战斗属性',
 'Cold':'寒冷','Heat':'热量','Sea Creatures':'海怪','Chum':'鱼浆','Wormhole':'虫洞','Spirit Decoy':'灵魂诱饵',
 'Mining Islands':'采矿岛屿','Infernal':'炼狱','Critter Safari':'小动物狩猎区','Backwater Bayou':'死水沼泽',
 'Fallen Stars':'坠落之星','Dungeons':'地牢','Life Steal':'生命窃取','Drain':'汲取','Vampirism':'吸血',
 'Mana':'魔力','Forest Whispers':'森林低语','Sowdust':'播种粉尘',"Jerry's Workshop":'杰瑞工坊'}
VARIABLE=re.compile(r'\{\{[A-Za-z][A-Za-z0-9_]*\}\}|(?<!\{)\{[A-Za-z][A-Za-z0-9_]*\}(?!\})')

def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def write(p,obj):
 data=(json.dumps(obj,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
 if p.exists() and p.read_bytes()==data:return
 backup=BACKUP/p.relative_to(ROOT)
 if p.exists() and not backup.exists():backup.parent.mkdir(parents=True,exist_ok=True);backup.write_bytes(p.read_bytes())
 p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)

def main():
 reforges=read(BASE/'reforges.json');rules=[];seen={}
 def add(source,target,styles=None,icons=False):
  if isinstance(source,str):source=[source]
  source=list(source);styles=dict(styles or {})
  if icons:
   # Glyph positions are captured from the displayed text. Font/version changes
   # must not replace an icon or attach a new icon to the translated tooltip.
   glyphs=list(dict.fromkeys(re.findall('[\ue000-\uf8ff]', ' '.join(source))))
   for i,glyph in enumerate(glyphs):
    if ' '.join(source).count(glyph)!=1 or target.count(glyph)!=1:continue
    if re.search(r'\}'+re.escape(glyph), ' '.join(source)):continue
    token='{icon'+str(i)+'}'
    source=[s.replace(glyph,token) for s in source];target=target.replace(glyph,token)
    styles={k.replace(glyph,token):v.replace(glyph,token) for k,v in styles.items()}
  assert collections.Counter(VARIABLE.findall(' '.join(source)))==collections.Counter(VARIABLE.findall(target)),(source,target)
  for english,chinese in ANCHORS.items():
   if english in ' '.join(source) and chinese in target:styles[english]=chinese
  # Retain only anchors actually present on both sides of this revised paragraph.
  styles={k:v for k,v in styles.items() if k in ' '.join(source) and v in target}
  for long,short in itertools.permutations(list(styles),2):
   if long in styles and short in styles and long!=short and short in long and styles[long]==styles[short]:
    if ' '.join(source).count(short)>' '.join(source).count(long):styles.pop(long,None)
    else:styles.pop(short,None)
  key=tuple(source)
  if key in seen:
   assert seen[key]==target,(key,seen[key],target)
   return
  seen[key]=target;rules.append({'source':source,'translation':target,'styleMappings':styles})
 dyes={};runes={};bonus_count=0
 with zipfile.ZipFile(ROOT/'build/libs/sapphire-1.0.0.jar') as jar:
  for file in jar.namelist():
   if not file.startswith('defaults/translations/items/') or not file.endswith('.json'):continue
   item=json.loads(jar.read(file));english=item.get('_original_name','');chinese=item.get('name','')
   if item['id'].startswith('DYE_') and english.endswith(' Dye') and chinese.endswith('染料'):
    dyes[english[:-4]]=chinese[:-2].strip()
   if '_RUNE;' in item['id']:
    en=re.sub(r'^[^A-Za-z]+| (?:[IVXLCDM]+|\d+)$','',english)
    cn=re.sub(r'^[^\w\u3400-\u9fff]+| (?:[IVXLCDM]+|\d+)$','',chinese).strip()
    if en.endswith(' Rune') and cn:runes[en]=cn
   if 'Reforge Stone' not in item.get('_original_lore',[]):continue
   active=False
   for p in item.get('loreParagraphs',[]):
    first=p['source'][0]
    heading=re.fullmatch(r'(.+) Bonus',first)
    gilded=first=="Byron's Compassion (Gilded)"
    if heading and heading.group(1) in reforges or gilded:
     active=True;bonus_count+=1;name='Gilded' if gilded else heading.group(1)
     title=reforges[name].removesuffix('的')
     if gilded:add(first,"Byron 的怜悯 (鎏金)",{"Byron's Compassion":"Byron 的怜悯",'Gilded':'鎏金'})
     else:add(first,title+' 加成',{name:title,'Bonus':'加成'})
     if len(p['source'])>1:add(p['source'][1:],BONUSES[name],p.get('styleMappings'),True)
    elif active:
     if first.startswith(('Right-click','❣')) or re.fullmatch('[A-Z ]+',first):active=False;continue
     add(p['source'],p['translation'],p.get('styleMappings'),True)
 # Generic live requirements and warnings are deliberately scoped to item tooltips.
 add(['Some of your enchantments require','a higher Enchanting level!'],'你的部分附魔需要更高的附魔等级!')
 add('Requires level {level}','需要等级 {level}')
 skills={'Combat':'战斗','Mining':'采矿','Fishing':'钓鱼','Farming':'农业','Foraging':'采伐','Enchanting':'附魔','Alchemy':'炼金','Carpentry':'木工','Runecrafting':'符文','Taming':'驯兽','Hunting':'狩猎','Social':'社交','Catacombs':'地牢'}
 for en,cn in skills.items():
  for prefix in ['', '{icon0} ']:
   for ending in ['', '.']:
    add(prefix+'Requires '+en+' Skill {level}'+ending,prefix+'需要'+cn+'技能 {level}'+ending,{en:cn,'Requires':'需要'})
 for prefix in ['', '{icon0} ']:
  for name in ['Catacombs','The Catacombs']:
   add(prefix+'Requires '+name+' Skill {level} while in The Catacombs.',prefix+'在地牢中需要地牢技能 {level}.',{'Catacombs':'地牢','The Catacombs':'地牢'})
   for master in ['', 'Master Mode ']:
    add(prefix+'Requires '+master+name+' Floor {roman} Completion.',prefix+'需要完成'+('大师模式' if master else '')+'地牢第 {roman} 层.',{'Master Mode':'大师模式',name:'地牢','Floor':'层','Requires':'需要'})
 # Live Smite descriptions reorder the three mob types and use newer icon fonts.
 for order in itertools.permutations(['Undead','Skeletal','Wither']):
  a,b,c=order
  source=f'Increases damage dealt to {{icon0}} {a}, {{icon1}} {b} and {{icon2}} {c} mobs by {{amount}}.'
  target=f'对 {{icon0}} {ANCHORS[a]}, {{icon1}} {ANCHORS[b]}和 {{icon2}} {ANCHORS[c]}生物的伤害提高 {{amount}}.'
  add(source,target)
 for root in ROOTS:
  for name,data in [('tooltip-labels',LABELS),('tooltip-paragraphs',{'schemaVersion':1,'loreParagraphs':rules}),('runes',runes),('dyes',dyes)]:write(root/(name+'.json'),data)
  names=read(root/'enchantments.json');names['Gravity']=names['Dragon Hunter']='飞行生物杀手';write(root/'enchantments.json',names)
  for p in root.joinpath('items').glob('DRAGON_HUNTER;*.json'):
   data=read(p)
   for para in data.get('loreParagraphs',[]):
    if para['source'][0].startswith('Gravity '):
     para['translation']=para['translation'].replace('重力','飞行生物杀手')
     para['styleMappings']['Gravity']='飞行生物杀手'
   write(p,data)
 review=ROOT/'translation-review/enchantments/review-catalog.json';catalog=read(review)
 for row in catalog['entries']:
  if row['id']=='DRAGON_HUNTER':row['proposedChinese']='飞行生物杀手'
 write(review,catalog)
 proposal=ROOT/'translation-review/enchantments/name-proposals.json';data=read(proposal);data['proposals']['DRAGON_HUNTER'][0]='飞行生物杀手';write(proposal,data)
 report={'reforgeBonuses':bonus_count,'paragraphRules':len(rules),'runeNames':len(runes),'dyeNames':len(dyes),'note':'Current English reference remains unchanged; live icons and numbers are captured, not replaced.'}
 write(ROOT/'translation-state/upgrade-tooltips/report.json',report);print(json.dumps(report,ensure_ascii=False))

if __name__=='__main__':main()
