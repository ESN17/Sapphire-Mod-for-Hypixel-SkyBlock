"""Dynamic skill-level announcements, from screenshot and existing wiki corpus."""
import json,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'src/main/resources/defaults'
STATE=ROOT/'translation-state/skill-levels-20261105';STATE.mkdir(parents=True,exist_ok=True)
key='translations/system-messages.json';p=BASE/key;before=p.read_bytes();data=json.loads(before);rules=data['rules'];added=[]
rules[:]=[r for r in rules if not r['source'].startswith('+{value}{icon} ')]
def add(source,target,styles=None):
    if any(r['source']==source and 'chat' in r['scopes'] for r in rules):return
    row=dict(source=source,translation=target,scopes=['chat'],styleMappings=styles or {})
    rules.append(row);added.append(row)
skills={'Farming':'农业','Mining':'采矿','Combat':'战斗','Foraging':'采伐','Fishing':'钓鱼','Enchanting':'附魔','Alchemy':'炼药','Carpentry':'木工','Taming':'驯养','Hunting':'狩猎','Runecrafting':'符文制作','Social':'社交','Dungeoneering':'地牢探索','Catacombs':'地下墓穴'}
perks={'Zoologist':'动物学家','Warrior':'战士','Farmhand':'农场帮手','Logger':'伐木工','Spelunker':'洞穴探险者','Treasure Hunter':'宝藏猎人','Conjurer':'咒术师','Brewer':'酿造师'}
arrows=['➜','→','➔','➡','->']
for en,zh in skills.items():
    add('SKILL LEVEL UP '+en+' {roman}','技能升级 '+zh+' {roman}',{'SKILL LEVEL UP':'技能升级',en:zh})
    for arrow in arrows:
        for sep in ['', ' ']:
            join=sep+arrow+sep
            add('SKILL LEVEL UP '+en+' {romanFrom}'+join+'{romanTo}','技能升级 '+zh+' {romanFrom}'+join+'{romanTo}',{'SKILL LEVEL UP':'技能升级',en:zh})
for en,zh in perks.items():
    add(en+' {roman}',zh+' {roman}',{en:zh})
add('SKILL LEVEL UP','技能升级',{'SKILL LEVEL UP':'技能升级'})
for en,zh in [('Rewards:','奖励：'),('Rewards','奖励')]:add(en,zh,{en:zh})
stats=json.loads((BASE/'translations/tooltip-labels.json').read_bytes())
for en in ['Health','Defense','Strength','Intelligence','Speed','Crit Chance','Crit Damage','Pet Luck','Ability Damage','Farming Fortune','Mining Fortune','Foraging Fortune','Hunting Fortune','Treasure Chance','Sea Creature Chance','Magic Find']:
    zh=stats[en]
    add('+{statIcon} '+en,'+{statIcon} '+zh,{en:zh})
    add('+{value} {icon} '+en,'+{value} {icon} '+zh,{en:zh})
    add('+{value} '+en,'+{value} '+zh,{en:zh})
    add('Grants +{value} {icon} '+en+'.','提供 +{value} {icon} '+zh+'。',{'Grants':'提供',en:zh})
for unit,zh in [('Coins','硬币'),('SkyBlock XP','空岛经验')]:add('+{amount} '+unit,'+{amount} '+zh,{unit:zh})
for en,noun,zhNoun in [('Farming Fortune','crops','作物'),('Mining Fortune','ore drops','矿石掉落'),('Foraging Fortune','logs','原木')]:
    zh=stats[en];tail='which increases your chance for multiple '+noun+'.';zhTail='提高获得多份'+zhNoun+'的概率。'
    add(tail,zhTail,{tail:zhTail})
    for value in ['{value}']+['{before}'+a+'{after}' for a in arrows]:
        add('Grants +'+value+' {icon} '+en+', '+tail,'提供 +'+value+' {icon} '+zh+'，'+zhTail,{'Grants':'提供',en:zh,tail:zhTail})
for arrow in arrows:
    for sep in ['', ' ']:
        join=sep+arrow+sep
        values='{before}'+join+'{after}'
        for v in [values,values+'%']:
            for prefix in ['', '+']:
                add(prefix+'Gain '+v+' extra pet exp.',prefix+'获得 '+v+' 额外宠物经验。',{'Gain':'获得','extra pet exp.':'额外宠物经验。'})
                add(prefix+'Gain '+v+' Exp Share rate.',prefix+'经验共享比例提高至 '+v+'。',{'Gain':'提高至','Exp Share rate.':'经验共享比例'})
            add('Deal '+v+' more damage to mobs.','对生物造成的伤害提高 '+v+'。',{'Deal':'造成的','more damage to mobs.':'对生物伤害提高'})
            add('Gain '+v+' more experience orbs from any source.','从所有来源获得的经验球增加 '+v+'。')
            add('Potions that you brew have a '+v+' longer duration.','你酿造的药水持续时间延长 '+v+'。')
        for en in ['Farming Fortune','Mining Fortune','Foraging Fortune','Treasure Chance']:
            zh=stats[en]
            add('Grants +'+values+' {icon} '+en+'.','提供 +'+values+' {icon} '+zh+'。',{'Grants':'提供',en:zh})
for source,target in [('Gain {value}% extra pet exp.','获得 {value}% 额外宠物经验。'),('Gain {value}% Exp Share rate.','经验共享比例提高至 {value}%。'),('Deal {value}% more damage to mobs.','对生物造成的伤害提高 {value}%。'),('Gain {value}% more experience orbs from any source.','从所有来源获得的经验球增加 {value}%。'),('Potions that you brew have a {value}% longer duration.','你酿造的药水持续时间延长 {value}%。')]:add(source,target)
if added:
    backup=STATE/'system-messages.before.json'
    if not backup.exists():backup.write_bytes(before)
    hp=BASE/'upgrade-hashes.json';h=json.loads(hp.read_bytes());h[key]=','.join(sorted(set(filter(None,h.get(key,'').split(',')))|{hashlib.sha256(before).hexdigest()}));hp.write_text(json.dumps(h,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    after=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode();p.write_bytes(after)
    for root in [ROOT/'dictionary-workspace',ROOT/'run/config/sapphire']:
        mirror=root/key
        if mirror.exists() and mirror.read_bytes()==before:mirror.write_bytes(after)
    baseline=json.loads(backup.read_bytes())['rules']
    (STATE/'added.json').write_text(json.dumps([r for r in rules if r not in baseline],ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print('Added',len(added),'dynamic rules')
