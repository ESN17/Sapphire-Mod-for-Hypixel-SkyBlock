import hashlib,json
from pathlib import Path
root=Path(__file__).resolve().parents[1];base=root/'src/main/resources/defaults';state=root/'translation-state/help-20261105/name-backups'
hp=base/'upgrade-hashes.json';hashes=json.loads(hp.read_bytes());changed=[]
for p in list((base/'translations').rglob('*.json'))+list((base/'compat').glob('*.json'))+[root/'tools/sapphire-translation-rules.json']:
    before=p.read_bytes();text=before.decode('utf8')
    if '狂欢药瓶' not in text:continue
    after=text.replace('狂欢药瓶','狂欢之瓶').encode('utf8');json.loads(after)
    backup=state/p.relative_to(root);backup.parent.mkdir(parents=True,exist_ok=True)
    if not backup.exists():backup.write_bytes(before)
    p.write_bytes(after);changed.append(str(p.relative_to(root)))
    if p.is_relative_to(base):
        key=p.relative_to(base).as_posix();hashes[key]=','.join(sorted(set(filter(None,hashes.get(key,'').split(',')))|{hashlib.sha256(before).hexdigest()}))
        for folder in ['dictionary-workspace','run/config/sapphire']:
            local=root/folder/key
            if local.exists() and local.read_bytes()==before:local.write_bytes(after)
hp.write_text(json.dumps(hashes,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps({'changed':len(changed)},ensure_ascii=False))
