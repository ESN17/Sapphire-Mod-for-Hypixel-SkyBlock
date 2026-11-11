"""Reviewed name disambiguation, excluding rabbits, with source-aware references."""
import collections,hashlib,json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'src/main/resources/defaults'
STATE=ROOT/'translation-state/name-dedup-20261105';STATE.mkdir(parents=True,exist_ok=True)
choices={
 'Bonzo Shard':'邦佐属性碎片','Newt Shard':'水螈碎片','Thorn Shard':'荆棘属性碎片','Bramble Shard':'刺丛碎片',
 'Power Dragon Shard':'威能龙碎片','Ember Shard':'余烬属性碎片','Thunder Shard':'雷霆属性碎片',
 'Flaming Chestplate':'燃焰胸甲','The Cake':'那块蛋糕','Carrots':'胡萝卜作物','Potatoes':'马铃薯作物',
 'Chum':'诱鱼料','Colossal Experience Bottle':'超巨型经验瓶','Marine Dye':'海蓝染料',
 'Silex':'燧石强化剂','Foul Flesh':'污秽腐肉','Garland':'花彩','Gem':'空岛宝石',
 'Ghostly Boots':'幽魂靴子','Wraith (Monster)':'怨灵 (怪物)','Glowy Chum Bait':'荧光诱鱼饵',
 'Honed Shark Tooth Necklace':'精磨鲨鱼牙项链','Hunk of Ice':'大块冰','Lump of Magma':'岩浆团',
 'Power Crystal':'动力水晶','Vermin Trap':'虫害陷阱',
 'Nearly Coherent doR gnihsiF':'近乎连贯的竿鱼钓','gorF (Sea Creature)':'蛙青 (海怪)','gorF':'蛙青',
}
for part,zh in [('Helmet','头盔'),('Chestplate','胸甲'),('Leggings','护腿'),('Boots','靴子')]:
    choices['Ember Ash '+part]='烬灰'+zh
    choices['Farmhand '+part]='农工'+zh
mirror_bases={'MIRRORED_BOW':'弓','MIRRORED_STRING':'线','MIRRORED_WOOD':'橡木木板',
 'MIRRORED_FISHING_ROD':'钓鱼竿','MIRRORED_LEASH':'拴绳','MIRRORED_SLIME_BALL':'黏液球','MIRRORED_STICK':'木棍',
 'RIFT_TROPHY_MIRRORED':'镜像世界时间护符','ETALPTSEHC_NORI':'铁胸甲',
 'EPOCH_CAKE_MAGENTA':'裂隙世纪蛋糕','GORF_THE_FISH':'青蛙之鱼',
 'REVERSE_REFORGE_STONE_CORE':'重铸石核心','TINY_HAMMER':'微型锤'}
def original_bytes(p):
    backup=STATE/'backups'/p.relative_to(ROOT)
    return backup.read_bytes() if backup.exists() else p.read_bytes()
items={p:json.loads(original_bytes(p)) for p in (BASE/'translations/items').glob('*.json')}
for d in items.values():
    if d['id'] in mirror_bases:
        base=mirror_bases[d['id']];choices[d['_original_name']]='“'+base+'”' if len(base)==1 else base[::-1]
mapping_path=STATE/'renames.json'
renames=json.loads(mapping_path.read_bytes()) if mapping_path.exists() else []
for p,d in items.items():
    en=d.get('_original_name','')
    if d['id'].startswith('FACTION_RABBIT_'):continue
    if en in choices and d['name']!=choices[en] and not any(r['id']==d['id'] for r in renames):
        renames.append(dict(id=d['id'],english=en,old=d['name'],new=choices[en]))
mapping_path.write_text(json.dumps(renames,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
terms={r['english']:(r['old'],r['new']) for r in renames}
# Match the mob's base name in nameplates and messages as well as its NEU entry.
terms['Wraith']=('幽灵','怨灵')
terms['gorF']=('蛙倒影','蛙青')
patterns={en:re.compile(r'(?<![A-Za-z])'+re.escape(en)+r'(?![A-Za-z])',re.I) for en in terms}
old_names=collections.defaultdict(set)
for d in items.values():old_names[d['name']].add(d.get('_original_name',''))
known_english={d.get('_original_name','') for d in items.values()}
protected_names={en:[(zh,other) for zh,names in old_names.items() if old in zh and zh!=old
                     for other in names if other and other not in terms]
                 for en,(old,new) in terms.items()}
ambiguous=[]
def text_source(source):return ' '.join(source) if isinstance(source,list) else source if isinstance(source,str) else ''
def translated(text,source,path):
    if not isinstance(text,str):return text
    source=text_source(source)
    if source in choices:return choices[source]
    if source in known_english:return text
    if source.startswith('▶ '):
        english=source.split('▶');chinese=text.split('▶')
        if len(english)==len(chinese):
            for i in range(1,len(english)):
                en=english[i].strip()
                if en in choices:
                    part=chinese[i];chinese[i]=part[:len(part)-len(part.lstrip())]+choices[en]+part[len(part.rstrip()):]
            return '▶'.join(chinese)
    hits=[en for en in terms if patterns[en].search(source)]
    # These ordinary words also describe crops, ingredients and gemstones in
    # general prose. Their new names apply only to the exact item label.
    hits=[en for en in hits if en not in {'Carrots','Potatoes','Gem','The Cake'} or source==en]
    # Longer item phrases own their contained terms (e.g. Glowy Chum Bait vs Chum).
    hits=[en for en in hits if not any(en!=other and en.lower() in other.lower() for other in hits)]
    result=text
    for en in hits:
        old,new=terms[en]
        if en=='gorF':result=re.sub(r'(?<![A-Za-z])gorF(?![A-Za-z])',new,result)
        if old not in result:continue
        competing=[other for other in old_names[old] if other and other!=en and other.lower() not in en.lower() and re.search(r'(?<![A-Za-z])'+re.escape(other)+r'(?![A-Za-z])',source,re.I)]
        if competing:
            ambiguous.append(dict(file=path,source=source,translation=text,term=en,other=competing));continue
        protected={}
        for zh,other in sorted(protected_names[en],key=lambda pair:len(pair[0]),reverse=True):
            if zh in result and re.search(r'(?<![A-Za-z])'+re.escape(other)+r'(?![A-Za-z])',source,re.I):
                token='\x00'+str(len(protected))+'\x00';protected[token]=zh;result=result.replace(zh,token)
        result=result.replace(old,new)
        for token,zh in protected.items():result=result.replace(token,zh)
    return result
def walk(value,path):
    if isinstance(value,list):return [walk(v,path) for v in value]
    if not isinstance(value,dict):return value
    out={k:walk(v,path) for k,v in value.items()}
    if 'source' in value and 'translation' in value:out['translation']=translated(value['translation'],value['source'],path)
    if 'numericSource' in value and 'numericTranslation' in value:
        out['numericTranslation']=translated(value['numericTranslation'],value['numericSource'],path)
    if '_original_name' in value and value['_original_name'] in choices and 'name' in value:
        out['name']=choices[value['_original_name']]
    for k,v in list(out.items()):
        if isinstance(v,str):out[k]=translated(v,k,path)
    if 'styleMappings' in value and 'source' in value and 'translation' in value:
        target=out.get('numericTranslation',out['translation'])
        for en,old in value['styleMappings'].items():
            candidate=out['styleMappings'][en]
            if candidate not in target and old in target:out['styleMappings'][en]=old
    return out
hp=BASE/'upgrade-hashes.json';hashes=json.loads(hp.read_bytes());changed=[]
paths=list((BASE/'translations').rglob('*.json'))+[BASE/'compat/skyhanni-zh_cn.json',ROOT/'tools/sapphire-translation-rules.json']
for p in paths:
    before=p.read_bytes();data=json.loads(original_bytes(p));rel=p.relative_to(ROOT).as_posix()
    if isinstance(data,dict) and data.get('id','').startswith('FACTION_RABBIT_'):continue
    updated=walk(data,rel)
    if json.loads(before)==updated:continue
    backup=STATE/'backups'/rel;backup.parent.mkdir(parents=True,exist_ok=True)
    if not backup.exists():backup.write_bytes(before)
    after=(json.dumps(updated,ensure_ascii=False,indent=2)+'\n').encode();p.write_bytes(after);changed.append(rel)
    if p.is_relative_to(BASE):
        key=p.relative_to(BASE).as_posix();hashes[key]=','.join(sorted(set(filter(None,hashes.get(key,'').split(',')))|{hashlib.sha256(before).hexdigest()}))
        for root in [ROOT/'dictionary-workspace',ROOT/'run/config/sapphire']:
            mirror=root/key
            if mirror.exists() and mirror.read_bytes()==before:mirror.write_bytes(after)
hp.write_text(json.dumps(hashes,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
changed_path=STATE/'changed-files.json'
old_changed=json.loads(changed_path.read_bytes()) if changed_path.exists() else []
changed_path.write_text(json.dumps(sorted(set(changed+old_changed)),ensure_ascii=False,indent=2)+'\n',encoding='utf8')
(STATE/'ambiguous-references.json').write_text(json.dumps(ambiguous,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
groups=collections.defaultdict(list)
for p in items:
    d=json.loads(p.read_bytes())
    if d['id'].startswith('FACTION_RABBIT_'):continue
    groups[d['name']].append((d['id'],d.get('_original_name','')))
remaining={n:rows for n,rows in groups.items() if len({e for _,e in rows})>1}
(STATE/'remaining.json').write_text(json.dumps(remaining,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
lines=['物品译名去重结果（2026-11-05）','镜像名称倒序；单字加中文双引号。兔子保留；同英文名的品质/等级/皮肤变体保留同名。','']
lines += [r['english']+' ['+r['id']+']：'+r['old']+' → '+r['new'] for r in renames]
lines += ['','非兔子、不同英文名称的剩余冲突组：'+str(len(remaining))]
(STATE/'译名修改清单.txt').write_text('\n'.join(lines)+'\n',encoding='utf8')
print(json.dumps(dict(renamed=len(renames),files=len(changed),ambiguous=len(ambiguous),remaining=remaining),ensure_ascii=False))
