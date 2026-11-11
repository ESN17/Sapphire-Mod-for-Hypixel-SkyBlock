"""Build a provenance-preserving, read-only Wiki retrieval index from our cached sources."""
import argparse,html,json,re,sqlite3
from html.parser import HTMLParser
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class Text(HTMLParser):
    def __init__(self):super().__init__();self.parts=[];self.skip=0
    def handle_starttag(self,tag,attrs):
        if tag in ('style','script'):self.skip+=1
        if not self.skip:
            for key,value in attrs:
                if key in ('data-minetip-title','data-minetip-text') and value:self.parts.append(value.replace('/','\n'))
    def handle_endtag(self,tag):
        if tag in ('style','script'):self.skip=max(0,self.skip-1)
    def handle_data(self,data):
        if not self.skip:self.parts.append(data)
def clean(value):
    value=html.unescape(value);value=re.sub(r'[§&][0-9a-fk-or]','',value,flags=re.I)
    return re.sub(r'\s+',' ',value).strip()
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--refresh-example',action='store_true');args=parser.parse_args()
    state=ROOT/'translation-state/help-20261105';state.mkdir(parents=True,exist_ok=True)
    if args.refresh_example:
        from collect_wiki_corpus import Client,write,HOST,LICENSE
        client=Client()
        for title in ['Fiesta Flask','Glossy Gemstone','Scatha Shard','Mining Fiesta','Carnival','Brynmor','Doug','Shop Perk']:
            result=client.get(action='parse',page=title,prop='text|revid');data=result['parse']
            write(state/(title.replace(' ','_')+'.json'),{'title':title,'revision':data['revid'],'url':HOST+'/w/'+title.replace(' ','_'),
                'license':LICENSE,'html':data['text']})
    target=state/'wiki.sqlite';temporary=target.with_suffix('.sqlite.tmp')
    if temporary.exists():temporary.unlink()
    db=sqlite3.connect(temporary)
    db.executescript('CREATE TABLE docs(id INTEGER PRIMARY KEY,title TEXT,url TEXT,revision TEXT,license TEXT,body TEXT);'
                     "CREATE VIRTUAL TABLE search USING fts4(title,aliases,body);")
    aliases={}
    for p in (ROOT/'src/main/resources/defaults/translations/items').glob('*.json'):
        d=json.loads(p.read_bytes());aliases[d['_original_name']]=d['name']
    sources={}
    cache=ROOT/'translation-review/wiki-corpus-20261023'
    for p in (cache/'pages').glob('*.json'):
        d=json.loads(p.read_bytes());render=cache/'rendered'/p.name
        if render.exists():d['html']=json.loads(render.read_bytes()).get('parse',{}).get('text','')
        sources[d['title']]=d
    for p in state.glob('*.json'):
        d=json.loads(p.read_bytes())
        if 'title' in d:sources[d['title']]=d
    for d in sources.values():
        if d.get('html'):
            t=Text();t.feed(d['html']);body=clean(' '.join(t.parts))
        else:body=clean(d.get('wikitext',''))
        if len(body)<30:continue
        title=d['title'];zh=aliases.get(title,'');alias=zh+' '+' '.join(zh[i:i+2] for i in range(max(0,len(zh)-1)))
        db.execute('INSERT INTO docs(title,url,revision,license,body) VALUES(?,?,?,?,?)',(title,d['url'],str(d.get('revision','')),d.get('license','CC BY-NC-SA 3.0'),body[:80000]))
        db.execute('INSERT INTO search(rowid,title,aliases,body) VALUES(last_insert_rowid(),?,?,?)',(title,alias,body[:80000]))
    db.commit();count=db.execute('SELECT count(*) FROM docs').fetchone()[0];db.close();temporary.replace(target)
    (state/'manifest.json').write_text(json.dumps({'schemaVersion':1,'documents':count,'snapshot':'2026-11-05',
        'source':'https://hypixelskyblock.minecraft.wiki/','license':'CC BY-NC-SA 3.0'},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'documents':count,'bytes':target.stat().st_size}))
if __name__=='__main__':main()
