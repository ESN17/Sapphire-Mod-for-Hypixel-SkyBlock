"""Write local evidence after the complete build; never alter capture or server."""
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

root=Path(__file__).resolve().parents[1]
state=root/'translation-state/dungeon-f5-f6-20261103'
reports=[ET.parse(p).getroot() for p in (root/'build/test-results/test').glob('TEST-*.xml')]
tests={k:sum(int(r.get(k,'0')) for r in reports) for k in ('tests','failures','errors','skipped')}
log=(root/'build/dungeon-final-build.log').read_text(encoding='utf8')
assert 'BUILD SUCCESSFUL' in log and tests['failures']==tests['errors']==0, 'Build or tests did not pass'
assert tests['tests']>600, 'Full regression suite is required'
published=state/'references/published-20261102_1.zip'
archive=zipfile.ZipFile(published)
old_terms={}
for name in archive.namelist():
    if not name.endswith('.json'):continue
    text=archive.read(name).decode('utf-8-sig')
    count=text.count('魔力寻获')+text.count('魔法寻宝')
    if count:old_terms[name]=count
local_old=[]
for path in (root/'src/main/resources/defaults/translations').rglob('*.json'):
    text=path.read_text(encoding='utf8')
    if '魔力寻获' in text or '魔法寻宝' in text:local_old.append(str(path.relative_to(root)))
assert not local_old,local_old
snapshot=(state/'capture.jsonl').read_bytes()
before=json.loads((state/'before-coverage.json').read_text(encoding='utf8'))
after=json.loads((root/'build/dungeon-coverage.json').read_text(encoding='utf8'))
(state/'after-coverage.json').write_text(json.dumps(after,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
properties=(root/'gradle.properties').read_text(encoding='utf8')
assert 'minecraft_version=26.3' in properties
result={
    'build':'successful (build -x jar -x sourcesJar --offline --no-daemon)',
    'tests':tests,'suites':len(reports),
    'captureSha256':hashlib.sha256(snapshot).hexdigest(),
    'liveCaptureMatchesSnapshot':(root/'dungeon.txt').read_bytes()==snapshot,
    'sampleGroups':len(after),'changedBefore':sum(r['changed'] for r in before),'changedAfter':sum(r['changed'] for r in after),
    'f5f6NewRules':len(json.loads((state/'rules.json').read_text(encoding='utf8'))),
    'supplementRules':len(json.loads((root/'src/test/resources/fixtures/dungeon-supplement-20261103.json').read_text(encoding='utf8'))),
    'publishedDictionary':'20261102_1.zip','publishedSha256':hashlib.sha256(published.read_bytes()).hexdigest(),
    'publishedOldMagicFindOccurrences':old_terms,'localOldMagicFindFiles':local_old,
    'developmentMinecraft':'26.3','jarExported':False,'serverModified':False,
    'limitation':'Public quiz bank and quoted failure variants verified; full consecutive Oruo failure dialogue still needs a live capture. Not tested in a live multiplayer session.'
}
(state/'validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps(result,ensure_ascii=False,indent=2))
