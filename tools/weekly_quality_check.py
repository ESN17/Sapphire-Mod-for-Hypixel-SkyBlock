"""Final content validation of every revised unit, with resumable quarantine for rejected output."""
import argparse,collections,json
from dictionary_tool import read,write
from refine_weekly import STATE,TOKEN,residual,configure_names
from weekly_result_quality import restore,icons
def check(quarantine=False):
    plan=read(STATE/'refine-plan.json');configure_names(plan);failed={};missing=[]
    for identity,j in plan['jobs'].items():
        path=STATE/'refined'/(identity+'.json')
        if not path.exists():missing.append(identity);continue
        old=read(path);r=restore(j['source'],old);errors=[]
        if collections.Counter(TOKEN.findall(j['source']))!=collections.Counter(TOKEN.findall(r['text'])):errors.append('numbers/variables changed')
        if residual(r['text']):errors.append('English remains: '+','.join(residual(r['text'])))
        if icons(j['source'])!=icons(r['text']):errors.append('icons changed')
        if errors:
            failed[identity]=errors
            if quarantine:
                write(STATE/'rejected'/(identity+'.json'),dict(source=j['source'],text=r['text'],styleMappings=r['styleMappings'],errors=errors))
                folder=STATE/'quality-quarantine';folder.mkdir(exist_ok=True);path.replace(folder/path.name)
        elif r!=old:write(path,r)
    write(STATE/'quality-report.json',dict(total_units=len(plan['jobs']),missing=missing,failed=failed))
    print(json.dumps(dict(total_units=len(plan['jobs']),missing=len(missing),failed=len(failed),reasons=collections.Counter(e.split(':')[0] for v in failed.values() for e in v))),flush=True)
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--quarantine',action='store_true');a=p.parse_args();check(a.quarantine)
