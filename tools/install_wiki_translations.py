"""Stage validated Wiki translations as a separate, attributed fallback dictionary."""
import collections
import argparse
import hashlib
import json
import re
import shutil
from translate_wiki_corpus import STATE, OUT, ROOT, read, write, restore, mappings, validate, configure_npcs
from dictionary_tool import plain


def numeric_rule(source, values):
    return '{{N' in source and len(re.sub(r'\{\{N\d+\}\}', '', source).strip()) >= 2 and not re.search(r'\}\}\{\{', source) and all(
        re.fullmatch(r'[+-]?(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\.[0-9]+)?%?', v) for v in values)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--install',action='store_true')
    parser.add_argument('--hold-failures',action='store_true',help='Retain failed formatted samples for review, without activating their rules')
    args=parser.parse_args()
    hold_path=STATE/'display-review.json';held=read(hold_path) if hold_path.exists() else {}
    if args.hold_failures:
        audit=read(ROOT/'build/reports/wiki-audit-summary.json');previous=read(OUT/'installation-report.json')
        if audit.get('sha256')!=previous.get('stagedSha256'):raise ValueError('Audit does not match current staging report')
        for failure in read(ROOT/'build/reports/wiki-style-failures.json'):
            held.setdefault(failure['sourceId'],failure)
        write(hold_path,held)
    plan=read(STATE/'plan.json'); entries={}; missing=[]; invalid=[]; review=[]; held_rows=[]
    configure_npcs(plan)
    for row in plan['rows']:
        path=STATE/'cache'/(row['job']+'.json')
        if not path.exists(): missing.append(row['id']);continue
        result=read(path); job=plan['jobs'][row['job']]
        errors=validate(job,result)
        if errors:
            invalid.append(dict(id=row['id'],job=row['job'],errors=errors));continue
        target=restore(result['text'],row['values'])
        source=row['source']; styles=mappings(source,target,result.get('styleMappings',{}))
        if row['id'] in held:
            held_rows.append(dict(id=row['id'],job=row['job'],source=source,translation=target,url=row['url'],
                revisionUrl=row['revisionUrl'],sampleRaw=row.get('sampleRaw',source),
                styleMappings=result.get('styleMappings',{}),reason=held[row['id']].get('reason','Display audit mismatch')))
            continue
        if row['scope']!='npc' and source.startswith('[NPC] '):
            review.append(dict(id=row['id'],source=source,translation=target,url=row['url'],
                reason='Wiki NPC example has no complete named dialogue prefix; keep for source review, not as a system-message rule.'))
            continue
        if '┌---' in source and '┘' in source and '┌---' in target and '┘' in target:
            # Charts are fixed-width layouts, not reflowable prose. Retain the
            # diagram in the archive; install only its explanatory captions.
            review.append(dict(id=row['id'],source=source,translation=target,url=row['url'],
                reason='Fixed-width Wiki chart example; captions imported separately, diagram kept unchanged.'))
            raw=' '.join(row.get('sampleRaw',source).split())
            for index,(a,b,sample) in enumerate([
                (source.split('┌',1)[0].strip(),target.split('┌',1)[0].strip(),raw.split('┌',1)[0].strip()),
                (source.rsplit('┘',1)[1].strip(),target.rsplit('┘',1)[1].strip(),raw.rsplit('┘',1)[1].strip())]):
                if not a or not b:continue
                caption=dict(scope=row['scope'],npc=row['npc'],source=a,translation=b,
                    styleMappings=mappings(a,b,result.get('styleMappings',{})),sampleRaw=sample,
                    sourceId=row['id']+'-caption-'+str(index),sourceFile=row['url'],sourceRevision=row['revision'],
                    revisionUrl=row['revisionUrl'],license='CC BY-NC-SA 3.0',verifiedIngame=False)
                if caption['sourceId'] in held:
                    held_rows.append({**caption,'reason':held[caption['sourceId']].get('reason','Display audit mismatch')});continue
                entries.setdefault((row['scope'],row['npc'],a),caption)
            continue
        numeric=numeric_rule(job['source'],row['values'])
        if numeric:styles=mappings(job['source'],result['text'],result.get('styleMappings',{}))
        raw=' '.join(row.get('sampleRaw',source).split())
        if plain(raw).strip()!=source:raw=source
        entry=dict(scope=row['scope'],npc=row['npc'],source=source,translation=target,styleMappings=styles,
                   sampleRaw=raw,sourceId=row['id'],sourceFile=row['url'],sourceRevision=row['revision'],
                   revisionUrl=row['revisionUrl'],license='CC BY-NC-SA 3.0',verifiedIngame=False,
                   occurrences=row.get('occurrences',[]))
        if numeric:entry.update(numericSource=job['source'],numericTranslation=result['text'])
        entries.setdefault((entry['scope'],entry['npc'],source),entry)
        # Numeric UI/server examples are also reusable rules. NPC identity and text remain exact.
        if row['scope']!='npc' and source!=job['source'] and numeric:
            generic=dict(entry,source=job['source'],translation=result['text'],
                         styleMappings=mappings(job['source'],result['text'],result.get('styleMappings',{})),
                         sampleRaw=job['source'],templateExamples={'{{N'+str(i)+'}}':v for i,v in enumerate(row['values'])})
            entries.setdefault((entry['scope'],entry['npc'],generic['source']),generic)
    data=dict(schemaVersion=1,upstream='https://hypixelskyblock.minecraft.wiki',license='CC BY-NC-SA 3.0',
              attribution='Hypixel SkyBlock Wiki contributors; source and revision retained per entry.',
              entries=list(entries.values()))
    staged=STATE/'staged'; parts=[]; group=[]; size=0
    def flush():
        nonlocal group,size
        if not group:return
        name='wiki-'+str(len(parts)+1).zfill(3)+'.json'
        write(staged/'community-wiki'/name,{**data,'entries':group})
        parts.append(name);group=[];size=0
    for entry in data['entries']:
        # Detailed occurrence lists stay in the source corpus, outside the runtime data.
        entry={k:v for k,v in entry.items() if k!='occurrences'}
        entry_size=len(json.dumps(entry,ensure_ascii=False,indent=2).encode('utf8'))+150
        if size+entry_size>4*1024*1024:flush()
        group.append(entry);size+=entry_size
    flush()
    manifest={**data,'entries':[],'parts':parts,'heldReviewRows':len(held_rows),'sourceReviewRows':len(review)}
    write(staged/'community-wiki.json',manifest)
    digest=hashlib.sha256()
    for path in [staged/'community-wiki.json']+[staged/'community-wiki'/name for name in parts]:digest.update(path.read_bytes())
    report=dict(entries=len(entries),scopes=dict(collections.Counter(e['scope'] for e in entries.values())),
                missingRows=len(missing),invalidRows=len(invalid),invalid=invalid,missing=missing,
                sourceReviewRows=len(review),heldReviewRows=len(held_rows),stagedSha256=digest.hexdigest(),parts=len(parts))
    write(OUT/'installation-report.json',report)
    write(OUT/'source-review-exclusions.json',review)
    write(OUT/'display-review-pending.json',held_rows)
    if missing or invalid:
        print(json.dumps({k:v for k,v in report.items() if k not in ('invalid','missing')},ensure_ascii=False))
        print('Staged only: incomplete or invalid translations need repair before installation.')
        return
    if not args.install:
        print(json.dumps({k:v for k,v in report.items() if k not in ('invalid','missing')},ensure_ascii=False))
        print('Staged only. Run the Java rendering audit before --install.');return
    audit=read(ROOT/'build/reports/wiki-audit-summary.json')
    if audit.get('failures')!=0 or audit.get('sha256')!=digest.hexdigest():raise ValueError('Current staged files must pass the Java rendering audit before installation')
    for folder in [ROOT/'src/main/resources/defaults/translations', ROOT/'dictionary-workspace/translations', ROOT/'run/config/sapphire/translations']:
        (folder/'community-wiki').mkdir(parents=True,exist_ok=True)
        for name in parts:shutil.copyfile(staged/'community-wiki'/name,folder/'community-wiki'/name)
        write(folder/'community-wiki.json',manifest)
    notice=ROOT/'src/main/resources/defaults/third-party/wiki-NOTICE.txt'
    for base in [ROOT/'dictionary-workspace',ROOT/'run/config/sapphire']:
        (base/'third-party').mkdir(exist_ok=True)
        shutil.copyfile(notice,base/'third-party/wiki-NOTICE.txt')
    print(json.dumps({k:v for k,v in report.items() if k not in ('invalid','missing')},ensure_ascii=False))


if __name__=='__main__':main()
