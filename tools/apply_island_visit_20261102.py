"""Add the reported island visitor notification; retain customized dictionaries."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'src/main/resources/defaults'
key='translations/system-messages.json'
path=BASE/key;before=path.read_bytes();doc=json.loads(before)
sources={r['source'] for r in doc['rules']}
for suffix in ('Your Island!','your island!'):
    source='[SkyBlock] {player} is visiting '+suffix
    if source in sources:continue
    doc['rules'].append({'source':source,'translation':'[空岛] {player} 正在访问你的岛屿！','scopes':['chat'],
        'styleMappings':{'[SkyBlock]':'[空岛]','is visiting':'正在访问',suffix[:-1]:'你的岛屿'},
        'sourceAttribution':{'source':'User screenshot, 2026-11-02'}})
after=(json.dumps(doc,ensure_ascii=False,indent=2)+'\n').encode()
if before!=after:
    backup=ROOT/'translation-state/island-visit-20261102/system-messages.json'
    backup.parent.mkdir(parents=True,exist_ok=True)
    if not backup.exists():backup.write_bytes(before)
    hashes_path=BASE/'upgrade-hashes.json';hashes=json.loads(hashes_path.read_text(encoding='utf-8'))
    hashes[key]=','.join(sorted(set(filter(None,hashes.get(key,'').split(',')))|{hashlib.sha256(before).hexdigest()}))
    path.write_bytes(after);hashes_path.write_text(json.dumps(hashes,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    for root in (ROOT/'dictionary-workspace',ROOT/'run/config/sapphire'):
        mirror=root/key
        if mirror.exists() and mirror.read_bytes()==before:mirror.write_bytes(after)
print('Island visitor templates ready')
