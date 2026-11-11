"""Local live capture summary. Reads only collector records; never uploads text."""
import argparse,json,time,re,os
from pathlib import Path
from datetime import datetime,timezone
ROOT=Path(__file__).resolve().parents[1]
def summarize(source,output):
    records=[];errors=0
    for p in sorted(source.glob('*.json')):
        if not re.fullmatch('[a-f0-9]{64}\.json',p.name):continue
        try:
            r=json.loads(p.read_text(encoding='utf8'))
            records.append({'key':p.stem,'category':r['category'],'context':r.get('context',''),'id':r.get('id'),'name':r.get('original',{}).get('name',''),
                            'firstSeen':r.get('firstSeen'),'lastSeen':r.get('lastSeen'),'occurrences':r.get('occurrences',0),'status':r.get('status'),
                            'candidates':r.get('candidates',{}),'translation':r.get('translation',{})})
        except (OSError,ValueError,KeyError):errors+=1
    records.sort(key=lambda r:r['occurrences'],reverse=True)
    result={'schemaVersion':1,'updatedAt':datetime.now(timezone.utc).isoformat(),'sourceDirectory':str(source.resolve()),'recordCount':len(records),'readErrors':errors,'records':records}
    output.mkdir(parents=True,exist_ok=True)
    path=output/'pending-translations.json';tmp=path.with_suffix('.tmp');tmp.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8');os.replace(tmp,path)
    return result
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path,default=ROOT/'run/config/sapphire/untranslated');p.add_argument('--output',type=Path,default=ROOT/'translation-review/live-capture');p.add_argument('--watch',action='store_true');p.add_argument('--interval',type=int,default=10);a=p.parse_args()
    # Cooperative stop file: no process killing needed.
    a.output.mkdir(parents=True,exist_ok=True)
    while True:
        if (a.output/'STOP').exists():return
        r=summarize(a.source,a.output)
        print(json.dumps({k:r[k] for k in ('updatedAt','recordCount','readErrors')},ensure_ascii=False),flush=True)
        if not a.watch:return
        time.sleep(max(5,a.interval))
if __name__=='__main__':main()
