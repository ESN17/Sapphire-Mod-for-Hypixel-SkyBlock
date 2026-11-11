"""Reviewed Rift HUD and currency wording; preserve English source and NPC names."""
import hashlib,json,re,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'src/main/resources/defaults'
def read(p):return json.loads(p.read_text(encoding='utf-8'))
def encoded(v):return (json.dumps(v,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
def currency(s):
    s=re.sub(r'\bMotes Grubber\b','\ue000',s)
    s=re.sub(r'\bmotes?\b|微粒|微尘','裂隙尘埃',s,flags=re.I)
    return s.replace('\ue000','Motes Grubber')
def enigma(s):
    s=s.replace('恩尼格码','恩尼格玛')
    phrases={'Enigma Cloak':'恩尼格玛的斗篷',"Enigma's Cloak":'恩尼格玛的斗篷','Enigma’s Cloak':'恩尼格玛的斗篷',
             'Enigma Souls':'恩尼格玛之魂','Enigma Soul':'恩尼格玛之魂','Enigma':'恩尼格玛',
             '谜魂披风':'恩尼格玛的斗篷','谜团斗篷':'恩尼格玛的斗篷','谜团之魂':'恩尼格玛之魂',
             '谜之魂':'恩尼格玛之魂','迷之魂':'恩尼格玛之魂','谜魂':'恩尼格玛之魂',
             '谜者居所':'恩尼格玛居所','谜团':'恩尼格玛'}
    return re.sub('|'.join(re.escape(k) for k in sorted(phrases,key=len,reverse=True)),lambda m:phrases[m.group()],s)
def adapt(v):
    if isinstance(v,list):return [adapt(x) for x in v]
    if not isinstance(v,dict):return v
    out={k:adapt(x) for k,x in v.items()}
    source=v.get('source',v.get('_original_name',''))
    if isinstance(source,list):source=' '.join(source)
    if re.search(r'\bmotes?\b',source,re.I):
        for field in ('translation','name'):
            if field in v:out[field]=currency(v[field])
        if 'styleMappings' in v:out['styleMappings']={k:currency(x) for k,x in v['styleMappings'].items()}
    if re.search(r'\bEnigma\b',source,re.I):
        for field in ('translation','name'):
            if field in v:out[field]=enigma(v[field])
        if 'styleMappings' in v:out['styleMappings']={k:enigma(x) for k,x in v['styleMappings'].items()}
    # Flat dictionaries map English keys to translated values.
    for k,x in v.items():
        if isinstance(x,str) and re.search(r'\bmotes?\b',k,re.I):out[k]=currency(x)
        if isinstance(x,str) and re.search(r'\bEnigma\b',k,re.I):out[k]=enigma(x)
    return out
def main():
    hashes=read(BASE/'upgrade-hashes.json');changed=[]
    def save(p,d):
        old=p.read_bytes();new=encoded(d)
        if old==new:return
        backup=ROOT/'translation-state/rift-20261023/before'/p.relative_to(ROOT)
        backup.parent.mkdir(parents=True,exist_ok=True)
        if not backup.exists():backup.write_bytes(old)
        p.write_bytes(new)
        if p.is_relative_to(BASE):
            key=p.relative_to(BASE).as_posix();prior=set(hashes.get(key,'').split(','))-{''}
            prior.add(hashlib.sha256(old).hexdigest());hashes[key]=','.join(sorted(prior));changed.append(key)
    paths=subprocess.check_output(['rg','-l','-i',r'\bmotes?\b|\bEnigma\b',str(BASE/'translations')],text=True).splitlines()
    for root in [BASE,ROOT/'dictionary-workspace',ROOT/'run/config/sapphire']:
        for original in paths:
            p=root/Path(original).relative_to(BASE);save(p,adapt(read(p)))
        p=root/'translations/locations.json';d=read(p);d.setdefault('The Rift','裂隙维度');save(p,d)
        labels={'Mote':'裂隙尘埃','Motes':'裂隙尘埃','Mote:':'裂隙尘埃:','Motes:':'裂隙尘埃:',
                'Lifetime Motes:':'累计裂隙尘埃:','Rift Time Left:':'裂隙剩余时间:',
                'Timecharms:':'时光护符:','Enigma Souls:':'恩尼格玛之魂:',
                'Rift Damage:':'裂隙伤害:','Mana Regen:':'魔力回复:',
                'Essence:':'精华:','Sun Gecko:':'太阳壁虎:',
                'Barry is the best':'Barry 是空岛历来',
                'mayor that SkyBlock':'最出色的',
                'has ever had.':'市长.'}
        for scope in ('tablist','scoreboard','tooltip-labels'):
            p=root/f'translations/{scope}.json';d=read(p)
            d.update(labels if scope=='tablist' else {k:v for k,v in labels.items() if k.startswith('Mote')})
            save(p,d)
        aliases={'Enigma Soul':'恩尼格玛之魂','Enigma Souls':'恩尼格玛之魂','Enigma Cloak':'恩尼格玛的斗篷',"Enigma's Cloak":'恩尼格玛的斗篷','Enigma’s Cloak':'恩尼格玛的斗篷','Mote':'裂隙尘埃','Motes':'裂隙尘埃'}
        for scope in ('global','tooltip-labels'):
            p=root/f'translations/{scope}.json';d=read(p);d.update(aliases);save(p,d)
        p=root/'translations/system-messages.json';d=read(p)
        def rule(s,t,scopes,styles=None):
            old=next((r for r in d['rules'] if r['source']==s and r['scopes']==scopes),None)
            row=dict(source=s,translation=t,scopes=scopes,styleMappings=styles or {})
            if old is None:d['rules'].append(row)
            else:old.update(row)
        tab=['tablist']
        rule('Visited Rift: {count} times','到访裂隙: {count} 次',tab,{'Visited Rift':'到访裂隙','times':'次'})
        rule('Visited Rift: {count} time','到访裂隙: {count} 次',tab,{'Visited Rift':'到访裂隙','time':'次'})
        rule('Barry is the best mayor that SkyBlock has ever had.','Barry 是空岛历来最出色的市长.',tab,{'Barry':'Barry'})
        for en,zh in [('day','天'),('days','天'),('Day','天'),('Days','天'),('hour','小时'),('hours','小时'),('Hour','小时'),('Hours','小时')]:
            rule('{value} '+en,'{value} '+zh,tab,{en:zh})
        for day in ('day','days'):
            for hour in ('hour','hours'):
                rule('{count} '+day+', {value} '+hour,'{count} 天 {value} 小时',tab,{day:'天',hour:'小时'})
        rule('Garden Farming Fortune {roman} {value} Days','花园耕作时运 {roman} {value} 天',tab,{'Garden Farming Fortune':'花园耕作时运','Days':'天'})
        # Short Rift timers are values, not the in-game clock (which stays unchanged).
        for label,zh in [('Rift Time Left:','裂隙剩余时间:'),('Shen:','Shen:')]:
            rule(label+' {count}m',zh+' {count}分钟',tab,{label:zh,'m':'分钟'})
            rule(label+' ({count}h)',zh+' ({count}小时)',tab,{label:zh,'h':'小时'})
        scopes=['chat','actionbar','title','subtitle','bossbar','gui-tooltip','scoreboard','tablist']
        for word in ('Mote','Motes','mote','motes'):
            rule(word+': {value}','裂隙尘埃: {value}',scopes,{word:'裂隙尘埃'})
            rule('{value} '+word,'{value} 裂隙尘埃',scopes,{word:'裂隙尘埃'})
            for en,zh in [('Cost:','花费:'),('Price:','价格:'),('Sell Price:','卖出价格:'),('Buy Price:','买入价格:'),('Worth:','价值:')]:
                rule(en+' {value} '+word,zh+' {value} 裂隙尘埃',scopes,{en:zh,word:'裂隙尘埃'})
            for en,zh in [('You gained','你获得了'),('You earned','你获得了'),('You received','你收到了'),('You spent','你花费了'),('You lost','你失去了')]:
                for end in ('!','.'):
                    rule(en+' {value} '+word+end,zh+' {value} 裂隙尘埃'+end,['chat'],{en:zh,word:'裂隙尘埃'})
        save(p,d)
        p=root/'translations/tooltip-paragraphs.json';d=read(p)
        for word in ('Mote','Motes','mote','motes'):
            for prefix,zh in [('', ''),('Cost: ','花费: '),('Worth: ','价值: '),('Sell Price: ','卖出价格: '),('Buy Price: ','买入价格: ')]:
                s=prefix+'{value} '+word
                if not any(r['source']==[s] for r in d['loreParagraphs']):
                    d['loreParagraphs'].append(dict(source=[s],translation=zh+'{value} 裂隙尘埃',styleMappings={word:'裂隙尘埃',**({prefix.strip():zh.strip()} if prefix else {})}))
        save(p,d)
    policy=read(ROOT/'tools/translation_policy.json')
    for key in ('terms','exact_names'):policy[key].update({'Mote':'裂隙尘埃','Motes':'裂隙尘埃','mote':'裂隙尘埃','motes':'裂隙尘埃'})
    for key in ('terms','exact_names'):policy[key].update({'Enigma Soul':'恩尼格玛之魂','Enigma Souls':'恩尼格玛之魂','Enigma Cloak':'恩尼格玛的斗篷',"Enigma's Cloak":'恩尼格玛的斗篷','Enigma’s Cloak':'恩尼格玛的斗篷'})
    (ROOT/'tools/translation_policy.json').write_bytes(encoded(policy))
    (BASE/'upgrade-hashes.json').write_bytes(encoded(hashes))
    print('Updated',len(set(changed)),'dictionaries per root.')
if __name__=='__main__':main()
