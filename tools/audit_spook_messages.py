"""Audit unresolved Wiki placeholders and install narrowly scoped Great Spook fixes."""
import json,re,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'src/main/resources/defaults'
OUT=ROOT/'translation-review/spook-20261101'
def read(p):return json.loads(p.read_text(encoding='utf8'))
def encode(d):return (json.dumps(d,ensure_ascii=False,indent=2)+'\n').encode()
def main():
    OUT.mkdir(parents=True,exist_ok=True)
    candidates=[];fear=[]
    for p in sorted((BASE/'translations/community-wiki').glob('*.json')):
        for row in read(p)['entries']:
            if row['scope']=='system' and re.search(r'<[^>]+>|\[(?:name|player|username)\]',row['source'],re.I):
                candidates.append({'file':str(p.relative_to(ROOT)),'source':row['source'],'translation':row['translation'],'sourceFile':row.get('sourceFile'),'status':'candidate_not_runtime_confirmed'})
            if row['scope']=='system' and row['source'].startswith('[FEAR] '):fear.append(row)
    path=BASE/'translations/system-messages.json';old=path.read_bytes();data=read(path);rules=data['rules']
    added=[]
    def add(s,t,styles=None,evidence='compatibility variant'):
        if any(r['source']==s for r in rules):return
        rules.append({'source':s,'translation':t,'scopes':['chat'],'styleMappings':styles or {}})
        added.append({'source':s,'translation':t,'evidence':evidence})
    add('The Primal Fear has turned into a {mob}!','原初恐惧变成了{mob}!',{'Primal Fear':'原初恐惧'},'user reported live message; six manifestations confirmed by Wiki')
    add('The Primal Fear has turned into an {mob}!','原初恐惧变成了{mob}!',{'Primal Fear':'原初恐惧'})
    for prefix in ('','BOO! '):
        add(prefix+'A Primal Fear has been summoned!',('吓! ' if prefix else '')+'一个原初恐惧出现了!',{'Primal Fear':'原初恐惧'},'Hypixel forum spawn report; BOO prefix compatibility variant')
    names_path=BASE/'translations/entity-names.json';names_old=names_path.read_bytes();names=read(names_path)
    for en,zh in [('Math Teacher','数学老师'),('Vegan Crawler','素食爬行者')]:names.setdefault(en,zh)
    names_new=encode(names)
    if names_new!=names_old:
        backup=OUT/'entity-names.before.json'
        if not backup.exists():backup.write_bytes(names_old)
        hashes=read(BASE/'upgrade-hashes.json');key='translations/entity-names.json'
        hashes[key]=','.join(sorted(set(filter(None,hashes.get(key,'').split(',')))|{hashlib.sha256(names_old).hexdigest()}))
        (BASE/'upgrade-hashes.json').write_bytes(encode(hashes));names_path.write_bytes(names_new)
        for root in (ROOT/'dictionary-workspace',ROOT/'run/config/sapphire'):
            p=root/key
            if p.exists() and p.read_bytes()==names_old:p.write_bytes(names_new)
    demon=names['Public Speaking Demon']
    for s,t in [('Speak {player}!','{player},说话吧!'),('Say something interesting {player}!','{player},说点有趣的事吧!')]:
        add('[FEAR] Public Speaking Demon: '+s,'[恐惧] '+demon+': '+t,{'[FEAR]':'[恐惧]','Public Speaking Demon':demon},'existing Wiki corpus [name] converted to styled player capture')
    for color,zh in [('Green','绿色'),('Purple','紫色')]:
        add('You got {count} '+color+' Candy from the Great Spook Tree!','你从巨型幽灵树获得了 {count} 个'+zh+'糖果!',{color+' Candy':zh+'糖果','Great Spook Tree':'巨型幽灵树'},'existing Wiki corpus <amount> converted to numeric capture')
    new=encode(data)
    if old!=new:
        backup=OUT/'system-messages.before.json'
        if not backup.exists():backup.write_bytes(old)
        hashes=read(BASE/'upgrade-hashes.json');key='translations/system-messages.json'
        hashes[key]=','.join(sorted(set(filter(None,hashes.get(key,'').split(',')))|{hashlib.sha256(old).hexdigest()}))
        (BASE/'upgrade-hashes.json').write_bytes(encode(hashes));path.write_bytes(new)
        for root in (ROOT/'dictionary-workspace',ROOT/'run/config/sapphire'):
            p=root/key
            if p.exists() and p.read_bytes()==old:p.write_bytes(new)
    installed={row['source'] for row in rules}
    for candidate in candidates:
        normalized=candidate['source'].replace('[name]','{player}').replace('<amount>','{count}')
        if normalized in installed:candidate['status']='covered_by_specific_system_template'
    covered=sum(c['status']=='covered_by_specific_system_template' for c in candidates)
    (OUT/'placeholder-candidates.json').write_bytes(encode(candidates))
    (OUT/'existing-fear-dialogue.json').write_bytes(encode(fear))
    if added:(OUT/'added-rules.json').write_bytes(encode(added))
    report={'placeholder_candidates':len(candidates),'covered_by_specific_system_template':covered,'unresolved_placeholder_candidates':len(candidates)-covered,'note':'These are audit candidates, not confirmed missing translations: another runtime rule may cover them. No guessed catch-all replacement is installed.',
            'existing_fear_dialogue_entries':len(fear),'added_rules':len(read(OUT/'added-rules.json')) if (OUT/'added-rules.json').exists() else len(added),
            'references':['https://hypixelskyblock.minecraft.wiki/w/Primal_Fear','https://hypixelskyblock.minecraft.wiki/w/Public_Speaking_Demon','https://hypixel.net/threads/primal-fears-guide.5527557/page-2']}
    (OUT/'audit.json').write_bytes(encode(report));print(report)
if __name__=='__main__':main()
