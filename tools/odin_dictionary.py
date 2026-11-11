"""Pinned official Odin display corpus; reuse validated, resumable translation tooling."""
import argparse,hashlib,json,re,shutil
from pathlib import Path
import skyhanni_dictionary as common
ROOT=Path(__file__).resolve().parents[1]
STATE=ROOT/'translation-state/odin-20261105'
COMMIT=json.loads((STATE/'commit.json').read_bytes())['sha']
SOURCE=STATE/'source'/('Odin-'+COMMIT)
OUT=ROOT/'src/main/resources/defaults/compat/odin-zh_cn.json'
def extract():
    rows={};dynamic=[]
    for path in (SOURCE/'src/main/kotlin').rglob('*.kt'):
        text=path.read_text(encoding='utf8');rel=path.relative_to(SOURCE).as_posix()
        for match in common.CHAIN.finditer(text):
            before=text[max(0,match.start()-180):match.start()]
            display=re.search(r'(?:name|description|desc|title|tooltip)\s*=\s*$|(?:[A-Za-z]*Setting|modMessage|devMessage|text|Text|textCentered|textDim|literal|tooltip|sendTitle)\s*\(\s*$',before)
            if not display:continue
            try:value=''.join(json.loads(x.replace('\\$','$')) for x in re.findall(common.LITERAL,match.group()))
            except ValueError:continue
            if not re.search('[A-Za-z]{2}',value) or len(value)>4000 or value.startswith(('http','/','com.')):continue
            ref={'file':rel,'line':text.count('\n',0,match.start())+1}
            if re.search(r'\$[{A-Za-z_]',value):dynamic.append({'source':value,**ref})
            else:rows.setdefault(value,[]).append(ref)
    records=[{'id':hashlib.sha256(s.encode()).hexdigest()[:20],'source':s,'references':refs} for s,refs in sorted(rows.items())]
    common.dump(STATE/'catalog.json',records);common.dump(STATE/'dynamic-review.json',dynamic)
    common.dump(STATE/'provenance.json',{'repository':'https://github.com/odtheking/Odin','commit':COMMIT,'license':'BSD-3-Clause','static':len(records),'dynamic':len(dynamic)})
    shutil.copyfile(SOURCE/'LICENSE',ROOT/'src/main/resources/defaults/third-party/odin-LICENSE.txt')
    print(json.dumps({'static':len(records),'dynamic':len(dynamic)}),flush=True)
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--translate',action='store_true');args=p.parse_args()
    common.STATE=STATE;common.OUT=OUT;common.COMMIT=COMMIT
    common.SYSTEM=common.SYSTEM.replace('SkyHanni','Odin')+' Use 猎杀 for Slayer. Keep Odin unchanged.'
    if not (STATE/'catalog.json').exists():extract();common.dynamic()
    if args.translate:
        common.translate(3)
        data=json.loads(OUT.read_bytes());data['sourceVersion']='official-main-20261105';data['license']='BSD-3-Clause';common.dump(OUT,data)
