"""Repair Settings menu recognition and independently translate toggle rows; retain existing wording."""
import json, hashlib, re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'src/main/resources/defaults'
def read(p): return json.loads(p.read_text(encoding='utf-8'))
lookup={}; settings=[]
for p in [BASE/'translations/community-sapphire.json',*sorted((BASE/'translations/community-wiki').glob('*.json'))]:
    for row in read(p).get('entries',[]):
        if row['scope']=='tooltip': lookup.setdefault(row['source'],row)
        if 'Settings' in row.get('sourceFile',''): settings.append(row)
manual={
 'Sounds Settings':'音效设置','Sound Settings':'音效设置','Fishing Settings':'钓鱼设置',
 'Personal Settings':'个人设置','Comms Settings':'通讯设置','Chat Feedback Settings':'聊天反馈设置',
 'API Settings':'API 设置','User Interface Settings':'用户界面设置','Island Settings':'岛屿设置','Garden Settings':'花园设置',
 'Chat Feedback':'聊天反馈','Fishing Timer':'钓鱼计时','Fishing Overlay':'钓鱼状态悬浮字',
 'Skills API':'技能 API','Collections API':'收藏 API','Inventory API':'物品栏 API','Museum API':'博物馆 API',
 'Click to enable!':'点击启用！','Click to disable!':'点击禁用！','Click to toggle!':'点击切换！',
 'Click to change!':'点击更改！','Click for more settings!':'点击查看更多设置！','Includes:':'包括:',
 'Currently: ENABLED':'当前: 已启用','Currently: DISABLED':'当前: 已禁用',
}
labels=set(manual)
for row in settings:
    if row['source'].startswith('Includes:'):
        labels.update(x.strip() for x in re.split('[✔✓✖✘✗]',row['source'])[1:] if x.strip())
labels.update(['Sounds','Personal','Comms','User Interface','Ability Chat','Ability Cooldown Chat','Pristine Chat','Compact Chat',
 'Auto-Recombobulator Chat','Sack Notifications','Fire Sale Notifications','Sea Creature Chat','Fishing Status Holograms',
 'Fishing Sound','Banking API','Plot Holograms','Tablist Widgets','Confirm breaking ungrown mutations'])
new=[]
for source in sorted(labels):
    old=lookup.get(source)
    target=old['translation'] if old else manual.get(source)
    if target is None: raise ValueError('Missing reviewed wording: '+source)
    provenance={k:old[k] for k in ('sourceFile','sourceId','sourceRevision','revisionUrl','license') if k in old} if old else {'source':'Screenshot / locally reviewed UI label'}
    prefixes=[''] if source.endswith((':','!')) or source.startswith('Currently:') else ['','✔ ','✖ ','✓ ','✘ ','✗ ']
    for prefix in prefixes:
        new.append({'source':[prefix+source],'translation':prefix+target,'styleMappings':{source:target},'sourceAttribution':provenance})
# Exact old and current submenu titles; do not broadly accept arbitrary '* Settings' containers.
titles={s:manual[s] for s in ('Sounds Settings','Sound Settings','Fishing Settings','Personal Settings','Comms Settings','Chat Feedback Settings','API Settings','User Interface Settings','Island Settings','Garden Settings')}
for row in settings:
    if row['scope']=='gui' and row['source'].startswith('Settings - '): titles.setdefault(row['source'],row['translation'])
gui_path=BASE/'translations/gui-menus.json';before=gui_path.read_bytes();gui=read(gui_path)
menu=next((m for m in gui['menus'] if m['id']=='settings'),None)
if menu is None:
    menu={'id':'settings','titles':{},'loreParagraphs':[]};gui['menus'].append(menu)
existing_titles={s for m in gui['menus'] if m is not menu for s in m['titles']}
for s,t in titles.items():
    if s not in existing_titles:menu['titles'].setdefault(s,t)
added={}
for m in [menu,next(m for m in gui['menus'] if m['id']=='skyblock')]:
    sources={tuple(r['source']) for r in m['loreParagraphs']};n=0
    for r in new:
        if tuple(r['source']) not in sources:m['loreParagraphs'].append(r);sources.add(tuple(r['source']));n+=1
    assert len(m['loreParagraphs'])<=1024
    added[m['id']]=n
out=(json.dumps(gui,ensure_ascii=False,indent=2)+'\n').encode()
report_dir=ROOT/'translation-review/settings-20261102';report_dir.mkdir(parents=True,exist_ok=True)
if before!=out:
    backup=ROOT/'translation-state/settings-20261102/gui-menus.json';backup.parent.mkdir(parents=True,exist_ok=True)
    if not backup.exists():backup.write_bytes(before)
    hashes_path=BASE/'upgrade-hashes.json';hashes=read(hashes_path);key='translations/gui-menus.json'
    hashes[key]=','.join(sorted(set(filter(None,hashes.get(key,'').split(',')))|{hashlib.sha256(before).hexdigest()}))
    gui_path.write_bytes(out);hashes_path.write_text(json.dumps(hashes,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    mirrors={}
    for root in (ROOT/'dictionary-workspace',ROOT/'run/config/sapphire'):
        p=root/key
        if p.exists() and p.read_bytes()==before:p.write_bytes(out);mirrors[str(root)]='updated unchanged baseline'
        else:mirrors[str(root)]='retained existing copy'
    report={'addedRulesByMenu':added,'recognizedSettingsTitles':menu['titles'],'labels':len(labels),'sourceAttribution':'Existing SkyBlockZH / Wiki corpus attribution retained on derived rows. Screenshot labels translated locally.','mirrors':mirrors}
    (report_dir/'audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'added':added,'titles':len(menu['titles']),'labels':len(labels)},ensure_ascii=False))
