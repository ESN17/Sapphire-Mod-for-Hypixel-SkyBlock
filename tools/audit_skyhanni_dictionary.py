"""Offline structural audit; does not claim in-game linguistic completeness."""
import json,unicodedata
from pathlib import Path
from skyhanni_dictionary import STATE,OUT,dump,valid

data=json.loads(OUT.read_text(encoding='utf-8'))
rows=list(data['translations'].items())+[(r['source'],r['translation']) for r in data.get('templates',[])]
glyph=lambda s:[c for c in s if unicodedata.category(c) in ('Co','So')]
errors=[]
for source,target in rows:
    if not valid(source,target):errors.append({'source':source,'reason':'format/number/command/newline mismatch'})
    if glyph(source)!=glyph(target):errors.append({'source':source,'reason':'icon mismatch'})
report={'version':data['sourceVersion'],'sourceCommit':data['sourceCommit'],
        'staticStrings':len(data['translations']),'dynamicTemplates':len(data.get('templates',[])),
        'excludedDynamicCandidates':len(json.loads((STATE/'dynamic-excluded.json').read_text(encoding='utf-8'))),
        'errors':errors,'linguisticReview':'Machine-assisted; format audit plus selected manual corrections, not exhaustive human review',
        'runtimeVerification':'See build/skyhanni-presence-26.2/presence-verified.log and build/skyhanni-absent-26.3.log',
        'allSkyHanniTextCovered':False}
dump(STATE/'audit.json',report)
print(json.dumps({k:v for k,v in report.items() if k not in ('errors','linguisticReview','runtimeVerification')},ensure_ascii=False))
print('errors:',len(errors))
if errors:raise SystemExit(1)
