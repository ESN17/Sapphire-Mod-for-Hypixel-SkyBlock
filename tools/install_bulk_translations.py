"""Install this user's explicitly requested full standard revision after a complete audit.

The ordinary dictionary_tool merge remains non-overwriting. This migration backs up
every changed destination and keeps additional reviewed runtime rules.
"""
import hashlib
import json
import shutil
from pathlib import Path
import bulk_translate as bulk
import dictionary_tool as dictionary

ROOTS = ['src/main/resources/defaults', 'dictionary-workspace', 'run/config/sapphire']


def standardized(source, target):
    for old,new in bulk.POLICY['previous_translations'].items():
        if old == '海伯利安':
            import re
            target=re.sub(r'海伯利安(?!神剑)',new,target)
        else:
            target=target.replace(old,new)
    return bulk.punctuation(source,{'text':target})['text']


def compliant(source,target):
    return not dictionary.check_text(source,target,'reviewed',bool(dictionary.VARIABLE.search(source))) and all(
        not bulk.term_matches(source,term) or chinese in target for term,chinese in bulk.POLICY['terms'].items())


def merged(staged, old, reviewed=None):
    result=json.loads(json.dumps(staged))
    # Additional live variants (reforge bonuses, pet stats) were reviewed before
    # this full translation. Keep their rules, updating only the requested standard.
    result['lore']={s:standardized(s,t) for s,t in old.get('lore',{}).items() if t.strip() and compliant(s,standardized(s,t))}
    result['styleMappings']={**{s:standardized(s,t) for s,t in old.get('styleMappings',{}).items()},**result.get('styleMappings',{})}
    signatures={bulk.normalize(' '.join(p['source']))[0] for p in result['loreParagraphs']}
    for original in old.get('loreParagraphs',[]):
        if not original.get('translation'):continue
        signature=bulk.normalize(' '.join(original['source']))[0]
        if signature in signatures:continue
        paragraph=json.loads(json.dumps(original))
        paragraph['translation']=standardized(' '.join(paragraph['source']),paragraph['translation'])
        paragraph['styleMappings']={s:standardized(s,t) for s,t in paragraph.get('styleMappings',old.get('styleMappings',{})).items()}
        result['loreParagraphs'].append(paragraph);signatures.add(signature)
    # Prefer the user's previously accepted 100-item wording when it meets the new
    # policy. Older generic slots can capture a percentage included outside a new
    # generated slot, so string equality alone cannot recognize these equivalents.
    preferred=[]
    if reviewed and reviewed.get('name') and not reviewed.get('_translation_policy'):
        for original in reviewed.get('loreParagraphs',[]):
            source=' '.join(original['source']);target=standardized(source,original.get('translation',''))
            if not target.strip() or not compliant(source,target):continue
            paragraph=json.loads(json.dumps(original));paragraph['translation']=target
            paragraph['styleMappings']={s:standardized(s,t) for s,t in paragraph.get('styleMappings',reviewed.get('styleMappings',{})).items()}
            preferred.append(paragraph)
    preferred_keys={tuple(p['source']) for p in preferred}
    result['loreParagraphs']=preferred+[p for p in result['loreParagraphs'] if tuple(p['source']) not in preferred_keys]
    return result


def install():
    state=bulk.STATE; audit=dictionary.read(state/'audit-result.json');plan=dictionary.read(state/'plan.json')
    expected={i['id'] for i in plan['items']}
    if audit['failedJobs'] or set(audit['itemIds'])!=expected or audit['passedItems']!=len(expected):
        raise ValueError('Full runtime audit must pass for every planned item before installation')
    staged=state/'staged';files=sorted((staged/'translations/items').glob('*.json'),key=lambda p:p.name)
    digest=hashlib.sha256()
    for file in files:digest.update(file.name.encode());digest.update(file.read_bytes())
    if digest.hexdigest()!=audit.get('dictionarySha256'):raise ValueError('Staging changed after the audit')
    errors,warnings=dictionary.validate(staged)
    if errors:raise ValueError('\n'.join(errors))
    destinations=[];staged_values={source.name:dictionary.read(source) for source in files}
    for root_name in ROOTS:
        root=bulk.BASE/root_name
        for source in files:
            path=root/'translations/items'/source.name;value=staged_values[source.name]
            old=dictionary.read(path) if path.exists() else None
            review_path=state/'pre-bulk'/root_name/'translations/items'/source.name
            reviewed=dictionary.read(review_path) if review_path.exists() else None
            if old is not None:value=merged(value,old,reviewed)
            errors=dictionary.check_item_extensions(value,str(path))
            for s,t in value['lore'].items():errors+=dictionary.check_text(s,t,str(path),bool(dictionary.VARIABLE.search(s)))
            if errors:raise ValueError('\n'.join(errors))
            if value!=old:destinations.append((root_name,path,value))
        for filename in ['global.json','templates.json']:
            path=root/'translations'/filename
            if path.exists():
                old=dictionary.read(path);value={s:standardized(s,t) for s,t in old.items()}
                if value!=old:destinations.append((root_name,path,value))
    # Back up all existing files before replacing any destination.
    for root_name,path,_ in destinations:
        backup=state/'pre-bulk'/root_name/path.relative_to(bulk.BASE/root_name)
        if path.exists() and not backup.exists():backup.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,backup)
    for root_name,path,value in destinations:dictionary.write(path,value)
    manifest=bulk.BASE/'src/main/resources/defaults/item-dictionaries.txt'
    manifest.write_text(''.join(p.stem+'\n' for p in files),encoding='utf8')
    dictionary.write(state/'installed.json',{'itemCount':len(files),'roots':ROOTS,'policy':plan['policy'],
        'auditedSourceSha256':audit['dictionarySha256'],'backup':'translation-state/pre-bulk'})
    print(json.dumps({'installed_items_per_root':len(files),'changed_files':len(destinations),'roots':ROOTS,'review_warnings':len(warnings)}),flush=True)


if __name__=='__main__':install()
