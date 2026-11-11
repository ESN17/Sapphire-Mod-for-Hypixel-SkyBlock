"""Reviewed November live feedback. English metadata and custom overrides are preserved."""
import hashlib
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'src/main/resources/defaults'
CHANGES = []
def read(key):
    return json.loads((BASE / key).read_text(encoding='utf-8'))
def write(key, data):
    path = BASE / key
    before = path.read_bytes()
    if json.loads(before) == data:
        return
    after = (json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode()
    backup = ROOT / 'translation-state/live-feedback-20261102' / key
    backup.parent.mkdir(parents=True, exist_ok=True)
    if not backup.exists():
        backup.write_bytes(before)
    hashes = read('upgrade-hashes.json')
    hashes[key] = ','.join(sorted(set(filter(None, hashes.get(key, '').split(','))) | {hashlib.sha256(before).hexdigest()}))
    (BASE / 'upgrade-hashes.json').write_text(json.dumps(hashes, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    path.write_bytes(after)
    for root in (ROOT / 'dictionary-workspace', ROOT / 'run/config/sapphire'):
        mirror = root / key
        if mirror.exists() and mirror.read_bytes() == before:
            mirror.write_bytes(after)
    CHANGES.append(key)

def matching(pattern, directory='translations'):
    result = subprocess.run(['rg', '-l', pattern, str(BASE / directory)], capture_output=True, encoding='utf-8')
    return [Path(p).relative_to(BASE).as_posix() for p in result.stdout.splitlines() if p.endswith('.json')]

def translated_values(value, replacements, protected=False):
    if isinstance(value, dict):
        return {k: translated_values(v, replacements, protected or k in ('source', '_original_name', '_original_lore')) for k, v in value.items()}
    if isinstance(value, list):
        return [translated_values(v, replacements, protected) for v in value]
    if isinstance(value, str) and not protected:
        for old, new in sorted(replacements.items(), key=lambda p: -len(p[0])):
            value = value.replace(old, new)
    return value

FISH = dict(zip('BAT BERRY CANDY CENTURY CHILL CLUCK DIAMOND DUST EGG EON EXPERIMENT FISH FOSSIL GABAGOOL GIFT GOLDOR HERRING MAXOR MOB MYTH NOPE OOPS PARTY PRICELESS RABBIT RIBBIT ROCK SHRIMP SKELETON SNOWFLAKE SPOOK STEW STORM SWAMP TREE WORM ZOOP'.split(),
    '蝙蝠 浆果 糖果 世纪 寒冰 咯咯 钻石 尘埃 鸡蛋 万古 实验 游鱼 化石 加巴古尔 礼物 戈尔多 鲱鱼 麦克瑟 怪物 神话 拒绝 哎呀 派对 无价 兔子 呱呱 岩石 鲜虾 骷髅 雪花 幽灵 炖汤 风暴 沼泽 树木 蠕虫 祖普'.split()))
FISH['GORF']='反蛙' # Deliberately reversed English name: hsiF ehT gorF.

def names():
    aliases = {'朱朱短弓':'唤魔短弓','烧焦的文本':'烧书','金火龙':'金龙','黄金龙':'金龙','收割者宝珠':'死神宝珠','典狱长头盔':'监守者头盔'}
    reviewed = {'Flash':'闪钓','Juju Shortbow':'唤魔短弓','Burnt Texts':'烧书','Golden Dragon':'金龙','Reaper Orb':'死神宝珠','Warden Helmet':'监守者头盔'}
    for key in matching('|'.join(aliases)):
        write(key, translated_values(read(key), aliases))
    fish_names = {};fish_old={}
    for path in sorted((BASE/'translations/items').glob('*_THE_FISH*.json')):
        key=path.relative_to(BASE).as_posix()
        data = read(key)
        new = FISH[data['id'].split('_THE_FISH')[0]] + '之鱼'
        old = data['name']; en = data['_original_name']
        backup=ROOT/'translation-state/live-feedback-20261102'/key
        fish_old[en]=json.loads(backup.read_text(encoding='utf-8'))['name'] if backup.exists() else old
        data['name'] = new
        # Only references in this item are rewritten; e.g. plain Herring remains 鲱鱼.
        data = translated_values(data, {old:new}) if old != new else data
        data['name'] = new
        write(key, data)
        fish_names[en] = new
    # Exact English-keyed references elsewhere, including style mappings and labels.
    def fix_refs(value):
        if isinstance(value, list):
            return [fix_refs(x) for x in value]
        if not isinstance(value, dict):
            return value
        out = {k: (fish_names[k] if k in fish_names and isinstance(v,str) else fix_refs(v)) for k,v in value.items()}
        source = out.get('source')
        if isinstance(source,list): source=' '.join(source)
        if isinstance(source,str):
            for en,new in fish_names.items():
                if en not in source:continue
                old=fish_old[en]
                if old==new:continue
                for field in ('translation','numericTranslation'):
                    if field in out and new not in out[field]:out[field]=out[field].replace(old,new)
        if source in fish_names and 'translation' in out: out['translation']=fish_names[source]
        return out
    for key in matching('the Fish|hsiF ehT gorF'):
        write(key, fix_refs(read(key)))
    for filename in ('translation_policy.json','sapphire-translation-rules.json'):
        p=ROOT/'tools'/filename;d=json.loads(p.read_text(encoding='utf-8'))
        d.setdefault('exact_names',{}).update(reviewed | fish_names)
        d.setdefault('previous_translations',{}).update(aliases)
        for field in ('terms','exact_names'):
            if field in d:d[field]=translated_values(d[field],aliases)
        p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    p=ROOT/'tools/reviewed_item_names.json';d=json.loads(p.read_text(encoding='utf-8'))
    for key in matching(r'"_original_name".*(?:the Fish|hsiF ehT gorF|Juju Shortbow|Burnt Texts|Golden Dragon|Reaper Orb|Warden Helmet)', 'translations/items'):
        row=read(key);d[row['id']]=row['name']
    p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return fish_names

def reforges():
    canonical=read('translations/reforges.json');audit=[]
    for key in matching('Applies the .*reforge', 'translations/items'):
        d=read(key)
        match=re.search(r'Applies the (.+?) reforge', ' '.join(d.get('_original_lore',[])))
        if not match:continue
        en=match[1];zh=canonical.get(en)
        if not zh:
            audit.append({'id':d['id'],'reforge':en,'missing':True});continue
        for p in d.get('loreParagraphs',[]):
            if en not in ' '.join(p['source']):continue
            styles=p.setdefault('styleMappings',{});old=styles.get(en)
            if old and old!=zh:
                p['translation']=p['translation'].replace(old,zh)
                for k,v in list(styles.items()):styles[k]=v.replace(old,zh)
                audit.append({'id':d['id'],'reforge':en,'before':old,'after':zh})
            if 'Applies the '+en+' reforge' in ' '.join(p['source']):styles[en]=zh
        write(key,d)
    # Shared bonus headings must use the same modifier shown on equipment.
    key='translations/tooltip-paragraphs.json';d=read(key)
    for p in d['loreParagraphs']:
        en=' '.join(p['source']).removesuffix(' Bonus')
        if p['source']==[en+' Bonus'] and en in canonical:
            p['translation']=canonical[en]+' 加成';p['styleMappings']={en:canonical[en],'Bonus':'加成'}
    write(key,d)
    return audit

def rule(rows, source, translation, mappings=None, scope='chat'):
    entry={'source':source,'translation':translation,'scopes':[scope],'styleMappings':mappings or {}}
    rows[:]=[r for r in rows if not (r['source']==source and scope in r['scopes'])]
    rows.append(entry)

def messages():
    key='translations/system-messages.json';d=read(key);rows=d['rules']
    rows[:]=[r for r in rows if not (r['source'].startswith('Autopet equipped your ') and '[{count}{icon}]' in r['source'])]
    def add(s,t,m=None,scope='chat'):rule(rows,s,t,m,scope)
    for noun in ('item','items'):
        add('[Sacks] {value} '+noun+'. (Last {time}.)','[储物袋] {value} 件物品. (最近 {time}.)',{'[Sacks]':'[储物袋]',noun:'件物品','Last':'最近'})
        for other in ('item','items'):
            add('[Sacks] {value} '+noun+', {count} '+other+'. (Last {time}.)','[储物袋] {value} 件物品, {count} 件物品. (最近 {time}.)',{'[Sacks]':'[储物袋]',noun+',':'件物品,',other+'.':'件物品.','Last':'最近'})
    for kill in ('Kill','kill'):
        add('Your Slayer '+kill+' gave you {value} HP healing for {count} seconds!','击杀杀手首领后，你每秒恢复 {value} 点生命，持续 {count} 秒!',{'Your Slayer '+kill+' gave you':'击杀杀手首领后，你每秒恢复','HP':'点生命','healing for':'，持续','seconds':'秒'})
    add('SLAYER BOSS! The {mob} spawned!','杀手首领! {mob} 出现了!',{'SLAYER BOSS!':'杀手首领!','The':'','spawned!':'出现了!'})
    types={'Zombie':'僵尸','Spider':'蜘蛛','Wolf':'狼','Enderman':'末影人','Blaze':'烈焰人','Vampire':'吸血鬼'}
    for en,zh in types.items():
        add(en+' Slayer LVL {level} - Next LVL in {value} XP!',zh+'杀手 等级 {level} - 距下一级还需 {value} 经验!',{en+' Slayer LVL':zh+'杀手 等级','- Next LVL in':'- 距下一级还需','XP!':'经验!'})
    for pet in ('{catalogItem}','{catalogItem} {icon}','[{prefix}] {catalogItem}','[{prefix}] {catalogItem} {icon}'):
        for tail in ('',' VIEW RULE',' VIEW RULE ({value})'):
            add('Autopet equipped your [Lvl {level}] '+pet+'!'+tail,'自动切宠物 已装备 [等级 {level}] '+pet+'!'+tail.replace('VIEW RULE','查看规则'),{'Autopet':'自动切宠物','equipped your':'已装备','Lvl':'等级','VIEW RULE':'查看规则'})
    for icon in ('','{icon} '):
        for ending in (' ({count}/{total})',' Brace yourselves! ({count}/{total})'):
            add(icon+'{rankedPlayer} placed a Summoning Eye!'+ending,icon+'{rankedPlayer} 放置了召唤之眼!'+ending.replace('Brace yourselves!','做好准备!'),{'placed a Summoning Eye!':'放置了召唤之眼!','Brace yourselves!':'做好准备!'})
        add(icon+'The {mob} has spawned!',icon+'{mob} 出现了!',{'The':'','has spawned!':'出现了!'})
        add(icon+'{rankedPlayer} destroyed an Ender Crystal!',icon+'{rankedPlayer} 摧毁了末影水晶!',{'destroyed an Ender Crystal!':'摧毁了末影水晶!'})
    for en,zh in read('translations/entity-names.json').items():
        if en.endswith(' Dragon'):add(en.upper()+' DOWN!',zh+'已被击败!',{en.upper():zh,'DOWN!':'已被击败!'})
    add('{rankedPlayer} dealt the final blow.','{rankedPlayer} 完成了最后一击.',{'dealt the final blow.':'完成了最后一击.'})
    for ordinal in ('st','nd','rd','th'):
        add('{rank}'+ordinal+' Damager - {rankedPlayer} - {value}','伤害第 {rank} 名 - {rankedPlayer} - {value}',{ordinal+' Damager':'名'})
    add('Your Damage: {value} (Position #{rank})','你的伤害: {value} (排名 #{rank})',{'Your Damage:':'你的伤害:','Position':'排名'})
    add('FEAR. A Primal Fear has been summoned!','恐惧. 一只原始恐惧已被召唤!',{'FEAR.':'恐惧.','A Primal Fear':'一只原始恐惧','has been summoned!':'已被召唤!'})
    # Login/status lines: account identifiers are captures, never looked up as item names.
    add('You are playing on profile: {text} ({suffix})','你正在游玩的存档: {text} ({suffix})',{'You are playing on profile:':'你正在游玩的存档:'})
    add('You are playing on profile: {text} (Co-op)','你正在游玩的存档: {text} (合作)',{'You are playing on profile:':'你正在游玩的存档:','Co-op':'合作'})
    add('You are playing on profile: {text}','你正在游玩的存档: {text}',{'You are playing on profile:':'你正在游玩的存档:'})
    add('Profile ID: {text}','存档 ID: {text}',{'Profile ID:':'存档 ID:'})
    add('You have {count} unclaimed deliveries!','你有 {count} 份未领取的物品!',{'You have':'你有','unclaimed deliveries!':'份未领取的物品!'})
    add('Click here or talk to Jerry to claim them!','点击这里或与 Jerry 对话领取!',{'Click here':'点击这里','or talk to':'或与','Jerry':'Jerry','to claim them!':'对话领取!'})
    add('You equipped Loadout {count}!','已切换至预设装备 {count}!',{'You equipped Loadout':'已切换至预设装备'})
    add('Your Tuning Points were auto-assigned as convenience!','你的调谐点已自动分配!',{'Your':'你的','Tuning Points':'调谐点','were auto-assigned as convenience!':'已自动分配!'})
    for arrow in ('➜','→'):
        add('BESTIARY {mob} {roman} '+arrow+' {text}','生物图鉴 {mob} {roman} '+arrow+' {text}',{'BESTIARY':'生物图鉴'})
    for prefix in ('','({page}/{total}) '):
        add(prefix+'Bestiary ➜ {mob}',prefix+'生物图鉴 ➜ {mob}',{'Bestiary':'生物图鉴'},'bestiary-title')
    add('[SkyHanni] Copied message to clipboard!','[SkyHanni] 已将消息复制到剪贴板!',{'Copied message to clipboard!':'已将消息复制到剪贴板!'},'skyhanni')
    add('Copied message to clipboard!','已将消息复制到剪贴板!',scope='skyhanni')
    for prefix in ('','[SkyHanni] '):
        add(prefix+'You have gained {value} Bits.',prefix+'你获得了 {value} 比特.',{'You have gained':'你获得了','Bits.':'比特.'},'skyhanni')
    add('{mob} Profit Tracker','{mob} 收益统计',{'Profit Tracker':'收益统计'},'skyhanni')
    for en,zh in {'Mob Kill Coins':'击杀怪物所得硬币','Slayer Spawn Costs':'杀手首领召唤费用','Bosses killed':'已击杀首领','Total Profit':'总收益','Session Profit':'本次收益','Profit Per Hour':'每小时收益','Total Time':'总运行时间','Price Source':'价格来源','Display Mode':'显示模式'}.items():
        add(en,zh,scope='skyhanni')
        add(en+': {text}',zh+': {text}',{en:zh},'skyhanni')
        add(en+': {value} coins',zh+': {value} 硬币',{en:zh,'coins':'硬币'},'skyhanni')
    for en,zh in {'Price Source [BZ Instant Buy]':'价格来源 [集市即时买入]','Price Source [BZ Instant Sell]':'价格来源 [集市即时卖出]','Display Mode [Session]':'显示模式 [本次]','Display Mode [Total]':'显示模式 [总计]','Reset session!':'重置本次统计!','Resume session!':'继续本次统计!','Pause session!':'暂停本次统计!','(Paused!)':'(已暂停!)'}.items():
        mappings=dict(zip(en.split(' ['),zh.split(' ['))) if ' [' in en else {en:zh}
        add(en,zh,mappings,'skyhanni')
    add('Total Time: {time} (Paused!)','总运行时间: {time} (已暂停!)',{'Total Time':'总运行时间','Paused!':'已暂停!'},'skyhanni')
    write(key,d)

def bestiary():
    key='translations/entity-menus.json';d=read(key);menu=d['menus'][0]
    locations=read('translations/locations.json')
    category_names=['Hub','The Farming Islands',"Spider's Den",'The End','Crimson Isle','Deep Caverns','Dwarven Mines','Crystal Hollows','The Park','Moonglade Marsh','Torrhus Canyon','Lotus Atoll']
    categories={en:locations[en] for en in category_names if en in locations}
    categories.update({'Your Island':'你的岛屿','Fishing':'钓鱼','Spooky':'万圣节','Spooky Festival':'万圣节庆典','Mythological Creatures':'神话生物','Jerry':'Jerry','Kuudra':'库德拉','Critter Safari':'小动物观赏区','The Catacombs':'地下墓穴','The Garden':'花园'})
    titles=menu['titles']
    # Avoid turning every named sub-location into a menu candidate on each tooltip.
    for title in list(titles):
        if any(s in title for s in ('Bestiary ➜ ','Fishing ➜ ','Critter Safari ➜ ')):del titles[title]
    for en,zh in categories.items():
        for head in ('Bestiary ➜ ','({page}/{total}) Bestiary ➜ '):titles[head+en]=head.replace('Bestiary','生物图鉴')+zh
        for parent in ('Fishing','Critter Safari'):
            for head in (parent+' ➜ ','({page}/{total}) '+parent+' ➜ '):titles[head+en]=head.replace(parent,categories[parent])+zh
    for en,zh in {'Bestiary Milestones':'图鉴里程碑','Bestiary Milestone':'图鉴里程碑','Bestiary Rewards':'图鉴奖励','Bestiary ➜ {mob}':'生物图鉴 ➜ {mob}','({page}/{total}) Bestiary ➜ {mob}':'({page}/{total}) 生物图鉴 ➜ {mob}'}.items():titles[en]=zh
    labels=menu.setdefault('labels',{});labels.update(categories)
    phrases={'Bestiary':'生物图鉴','Bestiary Milestones':'图鉴里程碑','Bestiary Milestone {roman}':'图鉴里程碑 {roman}','Milestone {roman}':'里程碑 {roman}','Your Bestiary':'你的生物图鉴','Bestiary Completion':'图鉴完成度','Families Found':'已发现种类','Families Completed':'已完成种类','Families Unlocked':'已解锁种类','Family Tier':'种类等级','Family Tiers':'种类等级','Rewards':'奖励','Drops':'掉落物','Rare Drops':'稀有掉落物','Loot':'战利品','Stats':'属性','Variants':'变种','Overall Progress':'总进度','Overall Progress: SHOWN':'总进度: 已显示','Overall Progress: HIDDEN':'总进度: 已隐藏',"You haven't unlocked this Family yet!":'你尚未解锁这个种类!','Click to view rewards!':'点击查看奖励!','Click to claim rewards!':'点击领取奖励!','Click to claim!':'点击领取!','Click to view family!':'点击查看种类!','Click to view variants!':'点击查看变种!','Click to toggle!':'点击切换!','MAXED!':'已满级!','MAX!':'已满级!','NOT DISCOVERED':'尚未发现','UNLOCKED':'已解锁','LOCKED':'未解锁','Progress to Tier {roman}: {value}':'升至 {roman} 级的进度: {value}','Overall Progress: {value}':'总进度: {value}','Overall Progress: {value} (MAX!)':'总进度: {value} (已满级!)','Families Found {count}/{total}':'已发现种类 {count}/{total}','Families Completed {count}/{total}':'已完成种类 {count}/{total}','Families Unlocked: {count}/{total}':'已解锁种类: {count}/{total}','Kills: {value}':'击杀数: {value}','Deaths: {value}':'死亡次数: {value}','Kills to Next Tier: {value}':'升至下一级还需击杀: {value}','Total Kills: {value}':'总击杀数: {value}','Next Tier: {roman}':'下一级: {roman}','Tier {roman}':'等级 {roman}','Bestiary Level: {level}':'图鉴等级: {level}','Total Families: {count}':'种类总数: {count}','SkyBlock XP: {value}':'空岛经验: {value}','{value} Combat XP':'{value} 战斗经验','{value} SkyBlock XP':'{value} 空岛经验','{value} coins':'{value} 硬币','Strength':'力量','Magic Find':'稀有物品概率加成','Coins':'硬币','Experience Orbs':'经验球','Health':'生命值','Combat XP':'战斗经验','SkyBlock XP':'空岛经验'}
    paragraphs=menu['loreParagraphs']
    for en,zh in phrases.items():
        paragraphs[:]=[p for p in paragraphs if p['source']!=[en]]
        styles={k:v for k,v in phrases.items() if '{' not in k and k in en and v in zh}
        paragraphs.append({'source':[en],'translation':zh,'styleMappings':styles})
    # Reuse already reviewed corpus translations in the newly recognized menu contexts.
    paragraphs[:]=[p for p in paragraphs if 'bestiary' not in p.get('sourceAttribution',{}).get('sourceFile','').lower()]
    seen={' '.join(p['source']) for p in paragraphs}
    for p in [BASE/'translations/community-sapphire.json',*sorted((BASE/'translations/community-wiki').glob('*.json'))]:
        for row in json.loads(p.read_text(encoding='utf-8')).get('entries',[]):
            if 'bestiary' not in row.get('sourceFile','').lower():continue
            if row['scope']=='tooltip' and row['source'] not in seen:
                source=row.get('numericSource',row['source']);translation=row.get('numericTranslation',row['translation'])
                if source in seen:continue
                seen.add(source)
                paragraphs.append({'source':source.split('\n'),'translation':translation,'styleMappings':row.get('styleMappings',{}),
                    'sourceAttribution':{k:row[k] for k in ('sourceFile','sourceId','sourceRevision','revisionUrl','license') if k in row}})
            if row['scope']=='gui':titles.setdefault(row['source'],row['translation'])
    write(key,d)

if __name__=='__main__':
    fish=names();audit=reforges();messages();bestiary()
    report={'changedFiles':CHANGES,'fishNames':fish,'reforges':audit,'published':False}
    p=ROOT/'build/live-feedback-20261102-audit.json';p.parent.mkdir(exist_ok=True)
    p.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(f'Updated {len(CHANGES)} files; {len(fish)} fish names; {len(audit)} reforge findings',flush=True)
