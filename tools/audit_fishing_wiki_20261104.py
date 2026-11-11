"""Cache and enumerate current public Wiki sea-creature dialogue for review."""
import html,json,re,time,urllib.request,urllib.parse
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'translation-state/fishing-dungeon-feedback-20261104'
OUT.mkdir(parents=True,exist_ok=True)
HOST='https://hypixelskyblock.minecraft.wiki'
opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
def get(filename,**params):
    path=OUT/filename
    if path.exists():return json.loads(path.read_text(encoding='utf8'))
    url=HOST+'/api.php?'+urllib.parse.urlencode(dict(format='json',formatversion=2,**params))
    with opener.open(urllib.request.Request(url,headers={'User-Agent':'SapphireTextResearch/0.1 (local translation audit)'}),timeout=45) as response:result=json.load(response)
    if 'error' in result:raise RuntimeError(result['error'])
    path.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    time.sleep(1.5)
    return result
category=get('sea-category.json',action='query',list='categorymembers',cmtitle='Category:Sea Creatures',cmlimit=500)
assert 'continue' not in category
titles=[p['title'] for p in category['query']['categorymembers'] if p['ns']==0]
pages=[]
for start in range(0,len(titles),20):
    result=get(f'sea-pages-{start}.json',action='query',prop='revisions',titles='|'.join(titles[start:start+20]),rvprop='content|ids',rvslots='main',redirects=1)
    pages.extend(result['query']['pages'])
    print(f'Cached {len(pages)}/{len(titles)} pages',flush=True)
notices=[];no_notices=[]
for page in pages:
    raw=page['revisions'][0]['slots']['main']['content'];found=[]
    for m in re.finditer(r'\{\{Dialogue\s*\|([^{}]+)\}\}',raw,re.I):
        source=html.unescape(m[1]).strip()
        if '|' in source:continue
        sample=re.sub(r'&([0-9a-fk-or])',r'§\1',source,flags=re.I)
        plain=re.sub(r'§.','',sample)
        # Preserve context to distinguish catches from later combat messages.
        found.append(dict(page=page['title'],source=plain,sampleRaw=sample,
            context=raw[max(0,m.start()-140):m.start()],sourceUrl=HOST+'/w/'+urllib.parse.quote(page['title'].replace(' ','_')),
            revisionUrl=HOST+'/?oldid='+str(page['revisions'][0]['revid']),license='CC BY-NC-SA 3.0'))
    notices.extend(found)
    if not found:no_notices.append(page['title'])
result=dict(pages=len(pages),titles=titles,notices=notices,pagesWithoutDialogue=no_notices)
(OUT/'wiki-notices.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps({'pages':len(pages),'notices':len(notices),'pagesWithoutDialogue':no_notices},ensure_ascii=False))
