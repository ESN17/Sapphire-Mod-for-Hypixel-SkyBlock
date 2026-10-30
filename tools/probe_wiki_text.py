"""Small, cached MediaWiki extraction experiment. Never modifies mod dictionaries.

Uses public page HTML from the parse API, retaining revision IDs and attribution.
Rendered game dialogue is distinct from prose and UI tooltip attributes. Captures
are candidates for review, not verified live server messages.
"""
import argparse,collections,hashlib,json,re,time,urllib.request,urllib.parse,urllib.robotparser
from pathlib import Path
from datetime import datetime,timezone
from lxml import html
from prepare_npc_capture import key as npc_key

ROOT=Path(__file__).resolve().parents[1]
HOST='https://hypixelskyblock.minecraft.wiki'
UA='SapphireTextResearch/0.1 (user-requested local translation research; no login)'
PAGES=['Enigma','Bartender','Wizard','Motes Grubber','Maxwell','Odger','Rhys','Fetchur','Auction Master','Bazaar (NPC)',
       'Bazaar','Auction House','Pristine','Trophy Fish','Trophy Frogs','Crystal Nucleus','Goblin Raid','Raffle']
def write(p,data):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def classes(node):return set((node.get('class') or '').split())
def plain(node):return re.sub(r'\s+',' ',node.text_content()).strip()
def rendered_spans(node):
    spans=[]
    def append(text,style):
        if not text:return
        if spans and spans[-1]['format']==style:spans[-1]['text']+=text
        else:spans.append(dict(text=text,format=style))
    def walk(n,style):
        if not isinstance(n.tag,str):return
        current=list(style)
        for c in (n.get('class') or '').split():
            if re.fullmatch(r'format-[0-9a-fk-or]',c):
                code=c[-1]
                if code=='r':current=[]
                elif code in '0123456789abcdef':current=[code]+[x for x in current if x in 'klmno']
                elif code not in current:current.append(code)
        append(n.text,current)
        for child in n:
            if child.tag=='br':append('\n',current)
            else:walk(child,current)
            append(child.tail,current)
    walk(node,[])
    return spans
def record(node,title,revid,kind):
    spans=rendered_spans(node);source=''.join(x['text'] for x in spans).strip()
    row=dict(kind=kind,source=source,page=title,revision=revid,url=HOST+'/w/'+urllib.parse.quote(title.replace(' ','_')),
             revisionUrl=HOST+'/?oldid='+str(revid),license='CC BY-NC-SA 3.0',wikiFormatting=spans,
             sampleRaw=''.join('§r'+''.join('§'+c for c in s['format'])+s['text'] for s in spans),verifiedIngame=False)
    tr=next(iter(node.iterancestors('tr')),None)
    if tr is not None:
        cells=tr.findall('td')
        if len(cells)>1 and node not in cells[0].iter():row['condition']=plain(cells[0])
    match=re.match(r'^\[NPC\] ([^:\n]{1,90}):\s*(.*)$',source,re.S)
    if match:row.update(kind='npc',npc=match[1].strip(),source=match[2].strip(),fullSource=source)
    return row
def extract(data):
    page=data['parse'];root=html.fromstring(page['text']);title=page['title'];revid=page['revid']
    rows=[];tooltips=[];class_counts=collections.Counter();section=''
    npc_page=any(c['category']=='Non-player_characters' for c in page.get('categories',[]))
    for n in root.iter():
        if n.tag in ('h2','h3','h4'):section=plain(n)
        cs=classes(n)
        for c in cs:
            if any(t in c for t in ('chat','dialogue','message')):class_counts[c]+=1
        if 'mcdialogue-line' in cs:
            text=plain(n)
            if not text or re.fullmatch(r'[-─━=\s]+',text):continue
            kind='dialogue-option' if text.startswith('Select an option:') else 'dialogue-other'
            if not npc_page:kind='system-notice-candidate'
            row=record(n,title,revid,kind);row['section']=section;rows.append(row)
        # Tooltips are separate UI data, never counted as system notices.
        for attribute in ('data-minetip-text','data-mine-tip'):
            if n.get(attribute):tooltips.append(dict(page=title,revision=revid,attribute=attribute,value=n.get(attribute)))
    return rows,tooltips,dict(class_counts)
def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--offline',action='store_true');args=ap.parse_args()
    cache=ROOT/'build/wiki-probe-20261023/raw';out=ROOT/'translation-review/wiki-probe-20261023'
    cache.mkdir(parents=True,exist_ok=True)
    robots=ROOT/'build/wiki-probe-20261023/robots.txt'
    robot=urllib.robotparser.RobotFileParser();robot.parse(robots.read_text(encoding='utf-8').splitlines())
    last=0
    def get(params,path):
        nonlocal last
        if path.exists():return json.loads(path.read_text(encoding='utf-8'))
        if args.offline:raise ValueError('Uncached request in offline mode')
        url=HOST+'/api.php?'+urllib.parse.urlencode(dict(format='json',formatversion='2',maxlag='5',**params))
        if not robot.can_fetch(UA,url):raise ValueError('robots.txt disallows this URL')
        time.sleep(max(0,1.5-(time.monotonic()-last)))
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':UA,'Accept':'application/json'}),timeout=25) as r:
            raw=r.read(6000001)
            if len(raw)>6000000:raise ValueError('Page exceeds probe size limit')
            data=json.loads(raw)
        last=time.monotonic()
        if 'error' in data:raise ValueError(str(data['error']))
        write(path,data);return data
    all_rows=[];all_tips=[];pages=[];errors=[]
    for title in PAGES:
        slug=re.sub('[^a-z0-9]+','-',title.lower()).strip('-')
        try:
            data=get(dict(action='parse',page=title,prop='text|revid|categories',redirects='1'),cache/(slug+'.json'))
            rows,tips,types=extract(data);all_rows+=rows;all_tips+=tips
            pages.append(dict(title=data['parse']['title'],revision=data['parse']['revid'],dialogueLines=len(rows),tooltipAttributes=len(tips),messageClasses=types))
            print(title+': '+str(len(rows))+' dialogue rows, '+str(len(tips))+' UI attributes',flush=True)
        except Exception as e:errors.append(dict(page=title,error=str(e)));print(title+': '+str(e),flush=True)
    try:
        category=get(dict(action='query',prop='categoryinfo',titles='Category:Non-player characters',list='categorymembers',
                          cmtitle='Category:Non-player characters',cmlimit='20'),cache/'npc-category.json')
        write(out/'npc-category-sample.json',category)
    except Exception as e:category={};errors.append(dict(page='NPC category',error=str(e)))
    corpus=json.loads((ROOT/'src/main/resources/defaults/translations/community-skyzh.json').read_text(encoding='utf-8'))
    known={npc_key(r) for r in corpus['entries'] if r['scope']=='npc'}
    fresh=[];duplicates=[];other=[];seen={}
    for row in all_rows:
        if row['kind']!='npc':other.append(row);continue
        k=npc_key(row)
        if k in known:row['exclusionReason']='already-imported';duplicates.append(row)
        elif k in seen:
            seen[k].setdefault('otherOccurrences',[]).append({s:row[s] for s in ('page','revision','url','condition') if s in row})
            row['exclusionReason']='duplicate-in-probe';duplicates.append(row)
        else:seen[k]=row;fresh.append(row)
    write(out/'new-npc-dialogue.json',dict(schemaVersion=1,entries=fresh))
    write(out/'excluded-npc-dialogue.json',dict(schemaVersion=1,entries=duplicates))
    write(out/'other-dialogue-candidates.json',dict(schemaVersion=1,entries=other))
    notices=[r for r in other if r['kind']=='system-notice-candidate']
    write(out/'system-notice-candidates.json',dict(schemaVersion=1,entries=notices))
    write(out/'ui-tooltip-attributes.json',dict(schemaVersion=1,entries=all_tips))
    report=dict(checkedAt=datetime.now(timezone.utc).isoformat(),wiki=HOST,license='CC BY-NC-SA 3.0',
                copyrightUrl='https://meta.weirdgloop.org/w/Licensing',requestedPages=len(PAGES),successfulPages=len(pages),
                importedNpcBaseline=len(known),newNpcLines=len(fresh),alreadyImported=sum(r['exclusionReason']=='already-imported' for r in duplicates),
                duplicateInProbe=sum(r['exclusionReason']=='duplicate-in-probe' for r in duplicates),
                otherDialogueLines=len(other),uiAttributes=len(all_tips),pages=pages,errors=errors,
                systemNoticeCandidates=len(notices),
                npcCategory=category.get('query',{}).get('pages',[]),
                limitations=['Small sample only; wiki text is not a full server text export.',
                             'NPC conditions, placeholders, formatting and exact punctuation require game verification.',
                             'UI attributes and dialogue options are not counted as system notifications.',
                             'No mod dictionary changes or automatic translation imports.'])
    write(out/'report.json',report)
    print(json.dumps({k:v for k,v in report.items() if k not in ('pages','limitations')},ensure_ascii=False),flush=True)
if __name__=='__main__':main()
