import concurrent.futures,json,re
import odin_dictionary as odin
from deepseek_client import DeepSeek
c=odin.common;c.STATE=odin.STATE;c.OUT=odin.OUT;c.COMMIT=odin.COMMIT
records=json.loads((c.STATE/'catalog.json').read_bytes());cache=json.loads((c.STATE/'translated.json').read_bytes())
pending=[r for r in records if r['id'] not in cache];failures=[]
def batch(rows):
    payload={};keep={}
    for r in rows:
        tokens=[]
        def mask(m):tokens.append(m.group());return '⟦P'+str(len(tokens)-1)+'⟧'
        payload[r['id']]=c.TOKENS.sub(mask,r['source']);keep[r['id']]=tokens
    try:
        values,usage=DeepSeek().complete(c.SYSTEM.replace('SkyHanni','Odin')+' Return a flat JSON object keyed by the exact ids provided. Preserve protected ⟦P0⟧ etc in order, without adding other numbers.',payload)
        if 'translations' in values and isinstance(values['translations'],dict):values=values['translations']
    except Exception as e:return {},[{'error':type(e).__name__}]
    good={};bad=[]
    for r in rows:
        v=values.get(r['id'])
        if isinstance(v,str):
            for i,t in enumerate(keep[r['id']]):v=v.replace('⟦P'+str(i)+'⟧',t)
        if c.valid(r['source'],v):good[r['id']]=v
        else:bad.append({'source':r['source'],'target':v})
    return good,bad
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    for good,bad in pool.map(batch,[pending[i:i+8] for i in range(0,len(pending),8)]):
        cache.update(good);failures.extend(bad);c.dump(c.STATE/'translated.json',cache);print('Accepted',len(cache),'/',len(records),flush=True)
c.dump(c.STATE/'repair-rejected.json',failures);c.export(records,cache)
data=json.loads(c.OUT.read_bytes());data['sourceVersion']='0.4.0 / main 2026-11-05';data['license']='BSD-3-Clause';c.dump(c.OUT,data)
