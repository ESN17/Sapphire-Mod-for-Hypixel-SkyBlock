"""Read-only public search availability check; do not solve/bypass challenges."""
import concurrent.futures, urllib.request, urllib.parse, xml.etree.ElementTree as ET
from html.parser import HTMLParser

class Titles(HTMLParser):
    def __init__(self): super().__init__(); self.title=False; self.t=[]
    def handle_starttag(self,t,a):
        if t in ('title','h2','h3'): self.title=True
    def handle_endtag(self,t):
        if t in ('title','h2','h3'): self.title=False
    def handle_data(self,d):
        if self.title and d.strip(): self.t.append(d.strip())

def probe(pair):
    name,url=pair
    try:
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0','Accept-Language':'en-US,en;q=0.9'}),timeout=12) as r:
            data=r.read(1000000).decode('utf-8','replace'); status=r.status
        from pathlib import Path
        folder=Path('build/help-search-probe');folder.mkdir(parents=True,exist_ok=True)
        (folder/(name+'.html')).write_text(data,encoding='utf-8')
        if name.startswith('bingrss'):
            titles=[i.findtext('title') for i in ET.fromstring(data).findall('./channel/item')][:5]
        else:
            p=Titles(); p.feed(data); titles=p.t[:8]
        return name,status,titles
    except Exception as e: return name,type(e).__name__,str(e)[:150]

if __name__=='__main__':
    q=urllib.parse.quote('Hypixel SkyBlock Voidgloom guide')
    urls=[('sogou','https://www.sogou.com/web?query='+q),
          ('baidu','https://www.baidu.com/s?wd='+q),
          ('bing','https://www.bing.com/search?q='+q+'&ensearch=1'),
          ('bingrss','https://www.bing.com/search?format=rss&q='+q+'&ensearch=1')]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for result in pool.map(probe,urls): print(result)
