"""Verify the deployed read-only help endpoint without sending player data."""
import concurrent.futures, json, urllib.parse, urllib.request
from pathlib import Path

def fetch(source,query):
    url='https://sapphire.esn17cabin.club/wiki/?'+urllib.parse.urlencode({'source':source,'q':query})
    with urllib.request.urlopen(url,timeout=25) as r:
        assert r.status==200
        data=json.load(r)
    docs=data.get('documents',[])
    result={'source':source,'status':data.get('status','wiki'),'documents':[{'title':d['title'],'url':d['url'],'coverage':d.get('coverage','page-excerpt')} for d in docs]}
    # Public engines can legitimately require verification; report this without bypassing it.
    if source!='web': assert docs, result
    else: assert docs or data.get('status') in ('empty','unavailable','busy','stale'), result
    assert all(d.get('text') and d['url'].startswith('https://') for d in docs)
    return result

if __name__=='__main__':
    queries=[('wiki','Fiesta Flask'),('forum','Voidgloom guide'),('web','Hypixel SkyBlock SkyHanni profit tracker')]
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        results=list(pool.map(lambda args:fetch(*args),queries))
    folder=Path('translation-state/help-search-20261106');folder.mkdir(parents=True,exist_ok=True)
    (folder/'endpoint-verification.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
    for r in results: print(r['source'],r['status'],len(r['documents']),r['documents'][0]['title'] if r['documents'] else '(degraded; no invented evidence)')
    print('SAPPHIRE_HELP_ENDPOINTS_OK')
