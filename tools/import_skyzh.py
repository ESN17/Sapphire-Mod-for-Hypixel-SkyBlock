"""Adapt captured SkyBlockZH dialogue, UI and system records as a separate GPL corpus.

Never execute upstream code. Existing Sapphire translations take priority at runtime.
Numeric placeholders and explicitly linked UI continuation lines are supported.
Unsupported placeholder types and conflicting entries are left for review.
"""
import collections
import hashlib
import json
import re
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'src/main/resources/defaults'
REF=ROOT/'build/skyzh-reference'

def read(p):return json.loads(p.read_text(encoding='utf8'))
def encoded(d):return (json.dumps(d,ensure_ascii=False,indent=2)+'\n').encode('utf8')
def plain(s):return re.sub('§[0-9a-fk-or]','',s,flags=re.I)

def records(value):
    if isinstance(value,dict):
        if isinstance(value.get('text'),str):yield value
        else:
            for v in value.values():yield from records(v)
    elif isinstance(value,list):
        i=0
        while i<len(value):
            v=value[i];i+=1
            if not isinstance(v,dict) or 'text' not in v:
                yield from records(v);continue
            if v.get('continuation'):continue
            group=[v]
            while i<len(value) and isinstance(value[i],dict) and value[i].get('continuation'):
                group.append(value[i]);i+=1
            row=dict(v)
            if len(group)>1:
                if any(r.get('zh') for r in group[1:]):continue
                row['text']=' '.join(r['text'].strip() for r in group)
                row['raw']=' '.join(r.get('raw',r['text']).strip() for r in group)
                row['rawLines']=[r.get('raw',r['text']) for r in group]
                row['segments']=[s for r in group for s in r.get('segments',[])]
                row['placeholders']=[s for r in group for s in r.get('placeholders',[])]
            if not row.get('zh') and row.get('segments'):
                if all('zh' in s for s in row['segments']):row['zh']=''.join(s['zh'] for s in row['segments'])
            yield row

def main():
    z=zipfile.ZipFile(REF/'source.zip');prefix=z.namelist()[0]
    documents={n[len(prefix):]:json.loads(z.read(n)) for n in z.namelist()
               if '/original_text/' in n and n.endswith('.json')}
    npcs={d['npc'] for d in documents.values() if isinstance(d.get('npc'),str)}
    policy=read(ROOT/'tools/translation_policy.json')
    vanilla=read(ROOT/'docs/vanilla-name-audit.json')['differences']
    vanilla_terms={r['english'].lower():(r['old'],r['new']) for r in vanilla}
    # Nether Bricks / Stone Brick Slab must not match the shorter Brick rule.
    for english,term in read(ROOT/'build/vanilla-reference/terms.json')['terms'].items():
        vanilla_terms.setdefault(english.lower(),(term['zh'],term['zh']))
    vanilla_pattern=re.compile(r'(?<![A-Za-z])(?:'+'|'.join(re.escape(s) for s in sorted(vanilla_terms,key=len,reverse=True))+r')(?![A-Za-z])',re.I)
    excluded={(r['scope'],r['npc'],r['source']) for r in read(ROOT/'tools/skyzh-style-exclusions.json')}
    allowed=sorted(npcs|set(policy.get('preserve_identifiers',[])),key=len,reverse=True)
    allowed_re=re.compile(r'(?<![A-Za-z])(?:'+'|'.join(re.escape(s) for s in allowed)+r')(?![A-Za-z])')
    terms={**read(BASE/'translations/locations.json'),**policy['exact_names']}
    terms={k:v for k,v in terms.items() if re.search('[A-Za-z]',k) and k not in npcs}
    terms.update({'SkyBlock':'空岛','Bits':'比特','Slayer':'杀手','Minion':'仆从'})
    term_re=re.compile(r'(?<![A-Za-z])(?:'+'|'.join(re.escape(s) for s in sorted(terms,key=len,reverse=True))+r')(?![A-Za-z])')
    def normalize(s,source=''):
        s=term_re.sub(lambda m:terms[m.group()],s)
        if re.search(r'\bSlayers?\b',source):
            for old in ('猎杀任务','猎手','讨伐','屠魔'):s=s.replace(old,'杀手')
        if re.search(r'\bBits\b',source):s=s.replace('点券','比特')
        if re.search(r'\bPristine\b',source):s=s.replace('纯净度','提纯').replace('纯净','提纯').replace('原粹','提纯')
        if 'Harvest Feast' in source:s=s.replace('丰收盛宴','丰收节')
        if re.search(r'\bmotes?\b',source,re.I):
            from apply_rift_feedback_20261023 import currency
            s=currency(s)
        if re.search(r'\bEnigma\b',source,re.I):
            from apply_rift_feedback_20261023 import enigma
            s=enigma(s)
        pairs=collections.defaultdict(set)
        for hit in vanilla_pattern.finditer(source):
            old,new=vanilla_terms[hit.group().lower()];pairs[old].add(new)
        replacements={old:next(iter(new)) for old,new in pairs.items() if len(new)==1 and (len(old)>1 or s==old)}
        if replacements:
            correct=set(replacements.values())
            s=re.sub('|'.join(re.escape(t) for t in sorted(set(replacements)|correct,key=len,reverse=True)),lambda m:m.group() if m.group() in correct else replacements[m.group()],s)
        if 'Sea Lumies' in source:s=s.replace('海光藻','海泡菜').replace('海光','海泡菜')
        if 'Mangrove Log' in source:s=s.replace('红树林原木','红树原木')
        return s.replace('重铸','洗练').replace('小人','仆从')
    def residual(s):
        s=allowed_re.sub('',s);s=re.sub(r'https?://\S+|/[A-Za-z][\w-]*','',s)
        return [w for w in re.findall('[A-Za-z]+',s) if not re.fullmatch('[IVXLCDM]+',w) and w not in ('x','s','m','k','M','B')]
    groups=collections.defaultdict(list);skipped=collections.Counter();examined=collections.Counter()
    for path,doc in documents.items():
        parts=path.split('/');kind=parts[2]
        scope={'NPC_Message':'npc','GUI_Title':'gui','GUI_Item':'tooltip','GUI_Lore':'tooltip','ChatMessage':'system'}.get(kind)
        if not scope:continue
        npc=doc.get('npc','') if scope=='npc' else ''
        if scope=='npc' and (not npc or ':' in npc):continue
        for row in records(doc):
            examined[scope]+=1
            if row.get('rawLines') and scope in ('npc','system'):continue
            examples={};tokens={};unsupported=False
            for i,p in enumerate(row.get('placeholders',[])):
                if scope=='npc' or p.get('type') not in ('number','coins','percentage','year','level') or p['token'] in tokens:
                    unsupported=True;break
                if row['text'].count(p['token'])!=1:unsupported=True;break
                token='{{N'+str(i)+'}}';tokens[p['token']]=token
                sample=str(p.get('example','12'))
                if not re.fullmatch(r'[+-]?(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?%?',sample):sample='12'
                examples[token]=sample
            if unsupported:skipped['unsupported_dynamic_type']+=1;continue
            def convert(s):
                for token,target_token in tokens.items():s=s.replace(token,target_token)
                return s
            row=dict(row)
            for key in ('text','zh','raw'):
                if key in row:row[key]=convert(row[key])
            if row.get('rawLines'):row['rawLines']=[convert(s) for s in row['rawLines']]
            source=plain(row['text']).strip();target=normalize(row.get('zh',''),source).strip()
            if (scope,npc,source) in excluded:
                skipped['captured_style_incompatible']+=1;continue
            if isinstance(row.get('raw'),str) and plain(row['raw']).strip()!=source:
                skipped['captured_text_differs']+=1;continue
            if row.get('translate') is False or row.get('continuation') or not target or source==target:
                skipped['empty_preserved_or_continuation']+=1;continue
            if not row.get('verified_ingame',doc.get('verified_ingame',False)):
                skipped['upstream_not_verified_ingame']+=1;continue
            if re.search(r'%\d*\$?[sdf]|[{}\r\n]',re.sub(r'\{\{N\d+\}\}','',source+target)) or source.startswith('{{'):
                skipped['dynamic_or_multiline']+=1;continue
            if collections.Counter(re.findall(r'\{\{N\d+\}\}',source))!=collections.Counter(re.findall(r'\{\{N\d+\}\}',target)):
                skipped['variable_mismatch']+=1;continue
            if not source or max(len(source),len(target))>2048 or '§' in target or residual(re.sub(r'\{\{N\d+\}\}','',target)):
                skipped['unreviewed_terms_or_invalid']+=1;continue
            if collections.Counter(re.findall(r'\d+(?:[,.]\d+)*',source))!=collections.Counter(re.findall(r'\d+(?:[,.]\d+)*',target)):
                skipped['numbers_differ']+=1;continue
            if collections.Counter(c for c in source if '\ue000'<=c<='\uf8ff')!=collections.Counter(c for c in target if '\ue000'<=c<='\uf8ff'):
                skipped['icons_differ']+=1;continue
            styles={}
            for segment in row.get('segments',[]):
                en=plain(convert(segment.get('text',''))).strip();zh=normalize(convert(segment.get('zh','')),source).strip()
                if en and zh and en in source and zh in target:
                    if en in styles and styles[en]!=zh:styles={};break
                    styles[en]=zh
            entry=dict(scope=scope,npc=npc,source=source,translation=target,styleMappings=styles,
                       sourceFile=path,sourceId=row.get('id',''),upstreamVerifiedIngame=True)
            if isinstance(row.get('raw'),str):entry['sampleRaw']=row['raw']
            if row.get('rawLines'):entry['rawLines']=row['rawLines']
            if examples:entry['templateExamples']=examples
            groups[(scope,npc,source)].append(entry)
    rows=[]
    for key,candidates in groups.items():
        if len({r['translation'] for r in candidates})!=1:
            skipped['conflicting_translations']+=1;continue
        rows.append(max(candidates,key=lambda r:len(r['styleMappings'])))
    rows.sort(key=lambda r:(r['scope'],r['npc'],r['source']))
    data=dict(schemaVersion=1,upstream='https://github.com/BingKKni/SkyBlockZH',
              commit=(REF/'commit.txt').read_text().strip(),license='GPL-3.0-only',
              changes='Upstream verified records; numeric templates and joined UI continuations supported; Sapphire terminology adapted; unsupported/conflicting/style-incompatible records excluded.',entries=rows)
    hashes=read(BASE/'upgrade-hashes.json')
    key='translations/community-skyzh.json'
    for root in [BASE,ROOT/'dictionary-workspace',ROOT/'run/config/sapphire']:
        p=root/key
        if p.exists() and p.read_bytes()!=encoded(data):
            old=p.read_bytes();backup=ROOT/'translation-state/feedback-20261022/before'/p.relative_to(ROOT)
            backup.parent.mkdir(parents=True,exist_ok=True)
            if not backup.exists():backup.write_bytes(old)
            if root==BASE:
                prior=set(hashes.get(key,'').split(','))-{''};prior.add(hashlib.sha256(old).hexdigest());hashes[key]=','.join(sorted(prior))
        p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(encoded(data))
        license_path=root/'third-party/skyzh-LICENSE.txt';license_path.parent.mkdir(parents=True,exist_ok=True)
        license_path.write_bytes(z.read(prefix+'LICENSE'))
    (BASE/'upgrade-hashes.json').write_bytes(encoded(hashes))
    report=dict(upstream=data['upstream'],commit=data['commit'],license=data['license'],examined=dict(examined),
                imported=dict(collections.Counter(r['scope'] for r in rows)),npcNames=len({r['npc'] for r in rows if r['scope']=='npc'}),skipped=dict(skipped))
    (ROOT/'docs/skyzh-import-report.json').write_bytes(encoded(report))
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
