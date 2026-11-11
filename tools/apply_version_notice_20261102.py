"""Version notices keep the server-provided version token and link metadata."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'src/main/resources/defaults'
key='translations/system-messages.json';path=BASE/key;before=path.read_bytes();doc=json.loads(before)
sources={r['source'] for r in doc['rules']}
rows=[
('Support for your Minecraft version on latest content will be ending in the near future.','你当前使用的 Minecraft 版本即将停止支持最新游戏内容。'),
('We recommend updating to the latest version of Minecraft, {version}, as soon as possible.','建议尽快将 Minecraft 更新至最新版本 {version}。'),
('Click here to learn more!','点击此处了解详情！'),
]
for source,target in rows:
    if source not in sources:doc['rules'].append({'source':source,'translation':target,'scopes':['chat'],'styleMappings':{},'sourceAttribution':{'source':'User version warning screenshot, 2026-11-02'}})
after=(json.dumps(doc,ensure_ascii=False,indent=2)+'\n').encode()
if before!=after:
    backup=ROOT/'translation-state/version-notice-20261102/system-messages.json';backup.parent.mkdir(parents=True,exist_ok=True)
    if not backup.exists():backup.write_bytes(before)
    hashes_path=BASE/'upgrade-hashes.json';hashes=json.loads(hashes_path.read_text(encoding='utf-8'))
    hashes[key]=','.join(sorted(set(filter(None,hashes.get(key,'').split(',')))|{hashlib.sha256(before).hexdigest()}))
    path.write_bytes(after);hashes_path.write_text(json.dumps(hashes,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    for root in (ROOT/'dictionary-workspace',ROOT/'run/config/sapphire'):
        mirror=root/key
        if mirror.exists() and mirror.read_bytes()==before:mirror.write_bytes(after)
print('Version notices ready')
