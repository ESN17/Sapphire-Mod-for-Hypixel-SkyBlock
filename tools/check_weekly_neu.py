"""Compare public English item metadata against current NEU; links alone do not alter translations."""
import io,json,urllib.request,zipfile
from pathlib import Path
from dictionary_tool import write
root=Path(__file__).resolve().parents[1];revision='20a302457375ddfd7005e192486ac86781729b0a'
with urllib.request.urlopen('https://codeload.github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/zip/'+revision,timeout=90) as response:data=response.read()
report={'revision':revision,'added':[],'changed':[],'removed':[]};seen=set();folder=root/'build/weekly-reference/neu/items'
with zipfile.ZipFile(io.BytesIO(data)) as z:
    for name in z.namelist():
        parts=name.split('/')
        if len(parts)!=3 or parts[1]!='items' or not name.endswith('.json'):continue
        filename=parts[2];seen.add(filename);item=json.loads(z.read(name));old=root/'build/neu-reference/items'/filename
        if not old.exists():report['added'].append(filename);write(folder/filename,item)
        else:
            previous=json.loads(old.read_text(encoding='utf-8-sig'))
            if any(previous.get(k)!=item.get(k) for k in ('displayname','lore')):report['changed'].append(filename);write(folder/filename,item)
report['removed']=sorted({p.name for p in (root/'build/neu-reference/items').glob('*.json')}-seen)
write(root/'translation-state/weekly-20261021/neu-comparison.json',report)
print(json.dumps({k:len(v) if isinstance(v,list) else v for k,v in report.items()}))
