"""Install only the completely audited revision, retaining all pre-install files."""
import datetime,hashlib,shutil,zipfile
from dictionary_tool import read,write,plain
from refine_weekly import BASE,STATE

quality=read(STATE/'quality-report.json');stage=read(STATE/'refine-stage-report.json')
assert not quality['missing'] and not quality['failed'] and not stage['missing_units']
assert not read(STATE/'style-failures.json')
roots=[BASE/'src/main/resources/defaults',BASE/'dictionary-workspace',BASE/'run/config/sapphire']
hashes=read(STATE/'before-hashes.json');sources=sorted((STATE/'staged/translations/items').glob('*.json'))
assert len(sources)==8937
changed=[]
backup=STATE/'pre-install-changes.zip'
with zipfile.ZipFile(backup,'a',zipfile.ZIP_DEFLATED) as z:
    saved=set(z.namelist())
    for n,root in enumerate(roots):
        for source in sources:
            relative='translations/items/'+source.name;target=root/relative
            if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest()!=hashes.get(relative):
                key=str(n)+'/'+relative
                if key not in saved:z.write(target,key);saved.add(key)
            target.parent.mkdir(parents=True,exist_ok=True)
            if not target.exists() or source.read_bytes()!=target.read_bytes():shutil.copyfile(source,target);changed.append(str(n)+'/'+source.name)
comparison=read(STATE/'neu-comparison.json')
for root in roots:
    path=root/'neu-index.json';index=read(path)
    for filename in comparison['changed']:
        raw=read(BASE/'build/weekly-reference/neu/items'/filename)
        index['items'][filename[:-5]]={'name':plain(raw['displayname']),'lore':[plain(s) for s in raw.get('lore',[])]}
    index['revision']=comparison['revision'];index['syncedAt']=datetime.datetime.now(datetime.timezone.utc).isoformat();write(path,index)
write(STATE/'installation-report.json',{'items':len(sources),'roots':list(map(str,roots)),'changed_files':len(changed),'revision':comparison['revision']})
print('Installed',len(sources),'items in',len(roots),'roots; changed files:',len(changed),flush=True)
