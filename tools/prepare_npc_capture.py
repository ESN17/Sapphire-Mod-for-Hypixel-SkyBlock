"""Exclude already imported NPC lines from later capture batches.

Default: refresh translation-review/npc-capture/exclusions.json.
Optional --input captures.json --output new-captures.json filters {"entries": [...]}.
Each capture needs npc + source, or source containing a full [NPC] Name: line.
Only color codes and whitespace are normalized. Numbers, punctuation, case and
NPC identity remain significant so different dialogue is never guessed equal.
"""
import argparse,json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
CORPUS=ROOT/'src/main/resources/defaults/translations/community-skyzh.json'
def plain(s):return re.sub(r'§[0-9a-fk-or]','',s,flags=re.I)
def key(row):
    source=plain(row.get('source',row.get('text','')));npc=plain(row.get('npc','')).strip()
    match=re.fullmatch(r'\s*\[NPC\] ([^:\r\n]+):\s*(.*)',source,re.S)
    if match:
        if npc and npc!=match[1].strip():return None
        npc=match[1].strip();source=match[2]
    if not npc or not source.strip():return None
    return npc,' '.join(source.split())
def filter_rows(rows,known):
    fresh=[];review=[];duplicates=0;seen=set(known)
    for row in rows:
        identity=key(row)
        if identity is None:review.append(row)
        elif identity in seen:duplicates+=1
        else:fresh.append(row);seen.add(identity)
    return dict(entries=fresh,needsReview=review,excludedDuplicates=duplicates)
def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input',type=Path);parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    if bool(args.input)!=bool(args.output):parser.error('--input and --output must be provided together')
    corpus=json.loads(CORPUS.read_text(encoding='utf-8'))
    rows=[r for r in corpus['entries'] if r['scope']=='npc'];known={key(r) for r in rows}
    manifest=dict(schemaVersion=1,upstream=corpus['upstream'],commit=corpus['commit'],license=corpus['license'],
        matching='NPC name + English source; formatting and whitespace ignored; numbers preserved',
        npcCount=len({n for n,s in known}),dialogueCount=len(known),
        entries=[dict(npc=n,source=s) for n,s in sorted(known)])
    target=ROOT/'translation-review/npc-capture/exclusions.json';write(target,manifest)
    print(f'Exclusion list: {len(known)} dialogue lines across {manifest["npcCount"]} NPCs.')
    if args.input:
        if args.input.resolve()==args.output.resolve():parser.error('Output must differ from input; original captures are retained')
        data=json.loads(args.input.read_text(encoding='utf-8'))
        result=filter_rows(data['entries'],known);write(args.output,result)
        print(f'New: {len(result["entries"])}; duplicates excluded: {result["excludedDuplicates"]}; unidentified: {len(result["needsReview"])}.')
if __name__=='__main__':main()
