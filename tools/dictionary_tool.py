#!/usr/bin/env python3
"""Sapphire dictionary workflow; Python 3.10+, standard library only. Never overwrites nonempty translations."""
from __future__ import annotations
import argparse
import collections
import hashlib
import json
import pathlib
import re
import subprocess
import sys
import tempfile
from datetime import datetime, timezone

SCOPES = ('global', 'gui', 'chat', 'scoreboard', 'actionbar', 'title', 'subtitle', 'bossbar', 'templates', 'reforges', 'enchantments', 'enchantment-ids', 'name-modifiers', 'tooltip-labels', 'runes', 'dyes', 'locations', 'skyblock-months', 'sidebar-formats', 'bazaar-events')
VARIABLE = re.compile(r'(?<!\{)(?:\{\{[A-Za-z][A-Za-z0-9_]*\}\}|\{[A-Za-z][A-Za-z0-9_]*\})(?!\})')
FORMAT = re.compile(r'§[0-9a-fk-or]', re.I)
ID = re.compile(r'[A-Za-z0-9_;:.-]{1,160}\Z')

def unique_object(pairs):
    obj = {}
    for key, value in pairs:
        if key in obj:
            raise ValueError(f'duplicate key {key!r}')
        obj[key] = value
    return obj

def read(path):
    try:
        return json.loads(path.read_text(encoding='utf-8-sig'), object_pairs_hook=unique_object)
    except (ValueError, OSError) as exc:
        raise ValueError(f'{path}: {exc}') from exc

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + '.tmp')
    temp.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    temp.replace(path)

def plain(text):
    return FORMAT.sub('', text)

def read_neu(path, warnings):
    def pairs(values):
        result={}
        for key,value in values:
            if key in result:
                if key in {'internalname','displayname','lore','nbttag'}: raise ValueError(f'duplicate identity/text key {key!r}')
                warnings.append(f'{path}: duplicate unused metadata {key!r}; last value ignored')
                continue
            result[key]=value
        return result
    try:return json.loads(path.read_text(encoding='utf-8-sig'),object_pairs_hook=pairs)
    except (ValueError,OSError) as exc:raise ValueError(f'{path}: {exc}') from exc

def filename(item_id):
    # IDs remain exact inside JSON; Windows filenames cannot contain ':' or reserved device names.
    if not ID.fullmatch(item_id):
        raise ValueError(f'invalid ID {item_id!r}')
    if ':' in item_id or item_id.upper().split('.')[0] in {'CON','PRN','AUX','NUL', *('COM'+str(i) for i in range(1,10)), *('LPT'+str(i) for i in range(1,10))}:
        return 'id-' + hashlib.sha256(item_id.encode()).hexdigest()[:24] + '.json'
    return item_id + '.json'

def check_text(source, target, where, template=False):
    errors = []
    if not isinstance(source, str) or not isinstance(target, str):
        return [f'{where}: expected string key and value']
    if not source.strip(): errors.append(f'{where}: empty English key')
    for value in (source, target):
        if len(value) > 2048 or '\x00' in value or '\ufffd' in value or any(ord(c)<32 and c not in '\n\t' for c in value):
            errors.append(f'{where}: invalid character or overlong string')
        if '§' in plain(value): errors.append(f'{where}: invalid formatting code')
    a, b = VARIABLE.findall(source), VARIABLE.findall(target)
    if target.strip() and collections.Counter(a) != collections.Counter(b): errors.append(f'{where}: template variables differ: {a} / {b}')
    if template:
        if len(set(a)) != len(a): errors.append(f'{where}: repeated source variable')
        if re.search(r'\}\{', source): errors.append(f'{where}: adjacent variables are ambiguous')
        if a and len(VARIABLE.sub('', source)) < 2: errors.append(f'{where}: template needs literal context')
    return errors

def validate(root, strict=False):
    errors, warnings, ids = [], [], {}
    index_path = root / 'neu-index.json'
    neu = read(index_path).get('items', {}) if index_path.exists() else None
    for path in sorted((root / 'translations').rglob('*.json')):
        try:
            value = read(path)
            if not isinstance(value, dict): raise ValueError('expected JSON object')
            if path.parent.name == 'items':
                item_id = value.get('id', '')
                if not isinstance(item_id,str) or not ID.fullmatch(item_id): errors.append(f'{path}: $.id invalid')
                if item_id in ids: errors.append(f'{path}: $.id duplicates {ids[item_id]}')
                ids[item_id] = path
                if value.get('schemaVersion', 1) != 1: errors.append(f'{path}: $.schemaVersion unsupported')
                if not isinstance(value.get('name', ''),str): errors.append(f'{path}: $.name expected string')
                elif not value.get('name','').strip(): warnings.append(f'{path}: $.name is empty (pending)')
                elif not re.search(r'[\u3400-\u9fff]',value['name']): warnings.append(f'{path}: $.name has no Chinese characters')
                if neu is not None and item_id not in neu: warnings.append(f'{path}: $.id absent from NEU index')
                mapping = value.get('lore', {})
                errors.extend(check_item_extensions(value,str(path)))
                if not value.get('_original_name') and (neu is None or item_id not in neu): warnings.append(f'{path}: name translation needs _original_name or NEU metadata')
            elif path.stem == 'tooltip-paragraphs':
                errors.extend(check_item_extensions(value,str(path),1024));continue
            elif path.stem == 'gui-menus':
                errors.extend(check_gui_menus(value,str(path)));continue
            elif path.stem in SCOPES: mapping = value
            else: warnings.append(f'{path}: unrecognized dictionary filename'); continue
            if not isinstance(mapping,dict): raise ValueError('lore/text mapping must be an object')
            normalized = set()
            for source,target in mapping.items():
                errors.extend(check_text(source,target,f'{path}: $[{source!r}]',path.stem=='templates' or path.parent.name=='items' and bool(VARIABLE.search(source))))
                key=plain(source)
                if key in normalized: errors.append(f'{path}: normalized duplicate {key!r}')
                normalized.add(key)
                if isinstance(target,str) and not target.strip(): warnings.append(f'{path}: $[{source!r}] empty (pending)')
        except (ValueError, TypeError) as exc: errors.append(f'{path}: {exc}')
    return errors + (warnings if strict else []), warnings


def check_item_extensions(value, where, paragraph_limit=128):
    errors=[]
    styles=value.get('styleMappings',{})
    if not isinstance(styles,dict): errors.append(f'{where}: styleMappings must be an object')
    else:
        for source,target in styles.items():
            errors.extend(check_text(source,target,where+'.styleMappings'))
            if isinstance(target,str) and any(c in source+target for c in ('§','\n','\r')):
                errors.append(f'{where}: styleMappings must use plain single-line text')
    paragraphs=value.get('loreParagraphs',[])
    if not isinstance(paragraphs,list): return errors+[f'{where}: loreParagraphs must be an array']
    if len(paragraphs)>paragraph_limit: errors.append(f'{where}: too many lore paragraphs')
    seen=set()
    for paragraph in paragraphs:
        if not isinstance(paragraph,dict): errors.append(f'{where}: paragraph must be an object');continue
        source=paragraph.get('source');target=paragraph.get('translation')
        if not isinstance(source,list) or not 1<=len(source)<=16 or any(not isinstance(s,str) or not s.strip() or '\n' in s or '\r' in s for s in source):
            errors.append(f'{where}: invalid paragraph source lines');continue
        key=tuple(plain(s) for s in source)
        if key in seen:errors.append(f'{where}: duplicate paragraph source')
        seen.add(key)
        errors.extend(check_text(' '.join(source),target,where+'.loreParagraphs',True))
        paragraph_styles=paragraph.get('styleMappings',{})
        if not isinstance(paragraph_styles,dict):errors.append(f'{where}: paragraph styleMappings must be an object')
        else:
            for a,b in paragraph_styles.items():
                errors.extend(check_text(a,b,where+'.loreParagraphs.styleMappings'))
                if isinstance(a,str) and isinstance(b,str) and any(c in a+b for c in ('§','\n','\r')):
                    errors.append(f'{where}: paragraph styleMappings must use plain single-line text')
        if isinstance(target,str) and any(c in target for c in ('§','\n','\r')): errors.append(f'{where}: paragraph target must be one plain line')
    return errors


def check_gui_menus(value,where):
    errors=check_item_extensions(value,where,1024)
    if value.get('schemaVersion')!=1:errors.append(f'{where}: unsupported GUI schema')
    menus=value.get('menus')
    if not isinstance(menus,list):return errors+[f'{where}: menus must be an array']
    seen=set()
    for menu in menus:
        if not isinstance(menu,dict):errors.append(f'{where}: menu must be an object');continue
        id=menu.get('id')
        if not isinstance(id,str) or not ID.fullmatch(id):errors.append(f'{where}: invalid menu ID');continue
        if id in seen:errors.append(f'{where}: duplicate menu ID {id}')
        seen.add(id);titles=menu.get('titles')
        if not isinstance(titles,dict) or not titles:errors.append(f'{where}/{id}: titles must be a nonempty object')
        else:
            for en,zh in titles.items():errors.extend(check_text(en,zh,f'{where}/{id}.titles',True))
        labels=menu.get('labels',{})
        if not isinstance(labels,dict):errors.append(f'{where}/{id}: labels must be an object')
        else:
            for en,zh in labels.items():
                errors.extend(check_text(en,zh,f'{where}/{id}.labels'))
                if isinstance(en,str) and isinstance(zh,str) and (not en.strip() or not zh.strip() or len(en)>256 or len(zh)>256 or any(c in en+zh for c in ('§','\n','\r','\x00'))):errors.append(f'{where}/{id}: invalid label')
        errors.extend(check_item_extensions(menu,f'{where}/{id}',1024))
    return errors

def merge_gui_menus(old,new,conflicts,where):
    errors=check_gui_menus(new,where)
    if errors:raise ValueError('\n'.join(errors))
    merge_item_extensions(old,new,conflicts,where,1024)
    menus=old.setdefault('menus',[])
    for incoming in new['menus']:
        menu=next((m for m in menus if m['id']==incoming['id']),None)
        if menu is None:menu={'id':incoming['id'],'titles':{},'loreParagraphs':[]};menus.append(menu)
        merge_nonempty(menu['titles'],incoming['titles'],conflicts,where+'.titles',True)
        merge_nonempty(menu.setdefault('labels',{}),incoming.get('labels',{}),conflicts,where+'.labels')
        merge_item_extensions(menu,incoming,conflicts,where,1024)

def merge_item_extensions(old,new,conflicts,where,paragraph_limit=128):
    errors=check_item_extensions(new,where,paragraph_limit)
    if errors:raise ValueError('\n'.join(errors))
    if new.get('styleMappings'):merge_nonempty(old.setdefault('styleMappings',{}),new['styleMappings'],conflicts,where+'.styleMappings')
    for paragraph in new.get('loreParagraphs',[]):
        if not paragraph['translation'].strip():continue
        entries=old.setdefault('loreParagraphs',[])
        key=tuple(plain(s) for s in paragraph['source'])
        existing=next((p for p in entries if tuple(plain(s) for s in p['source'])==key),None)
        if existing is None:entries.append(paragraph)
        elif not existing.get('translation'):
            existing['translation']=paragraph['translation']
            if 'styleMappings' in paragraph:existing['styleMappings']=dict(paragraph['styleMappings'])
        elif existing['translation']!=paragraph['translation']:conflicts.append(f'{where}: paragraph kept existing translation')
        elif paragraph.get('styleMappings'):
            merge_nonempty(existing.setdefault('styleMappings',{}),paragraph['styleMappings'],conflicts,where+'.loreParagraphs.styleMappings')

def sync_neu(source, root):
    source = pathlib.Path(source)
    items_dir = source / 'items' if (source / 'items').is_dir() else source
    repo = items_dir.parent
    if not items_dir.is_dir(): raise ValueError(f'NEU items directory not found: {items_dir}')
    previous = read(root/'neu-index.json') if (root/'neu-index.json').exists() else {'items':{}}
    items, alias_candidates, report = {}, collections.defaultdict(set), {'new':[], 'changed':[], 'removed':[], 'errors':[], 'warnings':[]}
    existing = {}
    for path in (root/'translations/items').glob('*.json'):
        value = read(path)
        if value.get('id') in existing: raise ValueError(f'duplicate destination ID: {path}')
        existing[value.get('id')] = path
    for path in sorted(items_dir.glob('*.json')):
        try:
            data = read_neu(path,report['warnings'])
            item_id = data.get('internalname') or path.stem
            filename(item_id)
            if item_id in items: raise ValueError('duplicate internalname')
            name, lore = data.get('displayname', ''), data.get('lore', [])
            if not isinstance(name,str) or not isinstance(lore,list) or any(not isinstance(s,str) for s in lore):
                raise ValueError('invalid displayname/lore types')
            entry = {'name':plain(name), 'lore':[plain(s) for s in lore]}
            items[item_id] = entry
            nbt = data.get('nbttag','')
            # Only unambiguous ExtraAttributes.id aliases; PET/book IDs intentionally remain ambiguous.
            match = re.search(r'ExtraAttributes\s*:\s*\{[^{}]*?\bid\s*:\s*"([A-Z0-9_]+)"', nbt) if isinstance(nbt,str) else None
            if match: alias_candidates[match[1]].add(item_id)
            old = previous['items'].get(item_id)
            if old is None: report['new'].append(item_id)
            elif old != entry: report['changed'].append(item_id)
            if item_id not in existing:
                dest=root/'translations/items'/filename(item_id)
                if dest.exists(): raise ValueError('filename collision with existing dictionary')
                write(dest,{'schemaVersion':1,'id':item_id,'_original_name':entry['name'],'_original_lore':entry['lore'],'name':'','lore':{s:'' for s in entry['lore'] if s.strip()}})
        except (ValueError,TypeError,AttributeError) as exc: report['errors'].append(f'{path}: {exc}')
    if not items: raise ValueError('No valid NEU items; existing index retained')
    report['removed']=sorted(set(previous['items'])-set(items))
    try: revision=subprocess.check_output(['git','-C',str(repo),'rev-parse','HEAD'],stderr=subprocess.DEVNULL,text=True).strip()
    except (OSError,subprocess.CalledProcessError): revision='local-unversioned'
    aliases={key:next(iter(values)) for key,values in alias_candidates.items() if len(values)==1 and key not in ('PET','ENCHANTED_BOOK')}
    # Do not erase metadata for an invalid source file during a partially failed sync.
    if report['errors']:
        for item_id,entry in previous['items'].items(): items.setdefault(item_id,entry)
    write(root/'neu-index.json',{'schemaVersion':1,'source':'https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO','revision':revision,
        'syncedAt':datetime.now(timezone.utc).isoformat(),'items':items,'aliases':aliases})
    write(root/'neu-sync-report.json',report)
    license_path=repo/'LICENSE'
    if license_path.exists():
        target=root/'NEU-LICENSE.txt'; target.parent.mkdir(parents=True,exist_ok=True); target.write_text(license_path.read_text(encoding='utf8'),encoding='utf8')
    return report

def scan(root, category=None, item_id=None):
    rows=[]
    for path in sorted((root/'untranslated').glob('*.json')):
        value=read(path)
        if value.get('schemaVersion')!=1: raise ValueError(f'{path}: unsupported collection schema')
        if category and value.get('category')!=category: continue
        if item_id and value.get('id')!=item_id: continue
        rows.append((path,value))
    return rows

def export_records(root, output):
    if output.exists(): raise ValueError(f'Export already exists; refusing to overwrite your work: {output}')
    entries=[]
    for path,row in scan(root):
        original=row.get('original',{}); translation=row.get('translation',{})
        entries.append({'record':path.stem,'category':row['category'],'id':row.get('id'),'_original_name':original.get('name',''),
            'name':translation.get('name',''),'lore':{line:translation.get('lore',{}).get(line,'') for line in original.get('lore',[])},
            '_candidates':row.get('candidates',{}),'_status':row.get('status')})
    write(output,{'schemaVersion':1,'entries':entries}); return len(entries)

def merge_nonempty(old,new,conflicts,where,template=False):
    for key,value in new.items():
        if not isinstance(value,str): raise ValueError(f'{where}: {key!r} must be string')
        if not value.strip(): continue
        errors=check_text(key,value,where,template or bool(VARIABLE.search(key)))
        if errors: raise ValueError('\n'.join(errors))
        if old.get(key) and old[key]!=value: conflicts.append(f'{where}: {key!r} (kept existing translation)')
        elif not old.get(key): old[key]=value

def import_records(root, source):
    data=read(source)
    if data.get('schemaVersion')!=1: raise ValueError('unsupported export schema')
    staged, conflicts={},[]
    existing={}
    for path in (root/'translations/items').glob('*.json'):
        item=read(path)
        if item['id'] in existing: raise ValueError(f'duplicate ID: {item["id"]}')
        existing[item['id']]=path
    for row in data['entries']:
        category=row['category']; lore=row.get('lore',{})
        if not isinstance(lore,dict): raise ValueError('lore must be an object')
        if category=='gui-menus':
            path=root/'translations/gui-menus.json'
            value=staged.setdefault(path,read(path) if path.exists() else {'schemaVersion':1,'loreParagraphs':[],'menus':[]})
            merge_gui_menus(value,row,conflicts,str(path));continue
        if category in ('item','tooltip-paragraphs'):
            errors=check_item_extensions(row,'import',1024 if category=='tooltip-paragraphs' else 128)
            if errors:raise ValueError('\n'.join(errors))
        if not row.get('name') and not any(lore.values()) and not row.get('loreParagraphs') and not row.get('styleMappings'): continue
        if category=='item':
            item_id=row.get('id')
            if not item_id: conflicts.append('unknown-source item requires manual ID review; skipped'); continue
            path=existing.get(item_id,root/'translations/items'/filename(item_id))
            value=staged.setdefault(path,read(path) if path.exists() else {'schemaVersion':1,'id':item_id,'_original_name':row.get('_original_name',''),'name':'','lore':{}})
            if row.get('name'):
                if not isinstance(row['name'],str): raise ValueError('name must be a string')
                merge_nonempty(value,{'name':row['name']},conflicts,str(path))
            merge_nonempty(value.setdefault('lore',{}),lore,conflicts,str(path))
            merge_item_extensions(value,row,conflicts,str(path))
        elif category=='tooltip-paragraphs':
            path=root/'translations/tooltip-paragraphs.json'
            value=staged.setdefault(path,read(path) if path.exists() else {'schemaVersion':1,'loreParagraphs':[]})
            merge_item_extensions(value,row,conflicts,str(path),1024)
        elif category in SCOPES:
            path=root/'translations'/f'{category}.json'
            value=staged.setdefault(path,read(path) if path.exists() else {})
            merge_nonempty(value,lore,conflicts,str(path),category=='templates')
        else: raise ValueError(f'unsupported category {category!r}')
    # Validate all input before any writes. Each destination replacement is atomic.
    for path,value in staged.items(): write(path,value)
    return len(staged),conflicts

def merge_workspace(root, source):
    errors,_=validate(source)
    if errors: raise ValueError('\n'.join(errors))
    entries=[]
    for path in sorted((source/'translations').rglob('*.json')):
        value=read(path)
        if path.parent.name=='items': entries.append(dict(value,category='item'))
        elif path.stem=='tooltip-paragraphs': entries.append(dict(value,category='tooltip-paragraphs'))
        elif path.stem=='gui-menus': entries.append(dict(value,category='gui-menus'))
        elif path.stem in SCOPES: entries.append({'category':path.stem,'lore':value})
    # Reuse the same transactional validation/merge path without touching the source workspace.
    root.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='sapphire-merge-',dir=root) as temp:
        package=pathlib.Path(temp)/'package.json';write(package,{'schemaVersion':1,'entries':entries})
        return import_records(root,package)

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=pathlib.Path,default=pathlib.Path('run/config/sapphire'),help='Minecraft config/sapphire directory')
    sub=parser.add_subparsers(dest='command',required=True)
    for name in ('scan','stats'):
        p=sub.add_parser(name);p.add_argument('--category');p.add_argument('--id')
    p=sub.add_parser('validate');p.add_argument('--strict',action='store_true')
    p=sub.add_parser('export');p.add_argument('output',type=pathlib.Path)
    p=sub.add_parser('import');p.add_argument('input',type=pathlib.Path)
    p=sub.add_parser('merge');p.add_argument('source',type=pathlib.Path)
    p=sub.add_parser('sync');p.add_argument('source',type=pathlib.Path,nargs='?');p.add_argument('--github',action='store_true')
    args=parser.parse_args(argv)
    try:
        if args.command=='validate':
            errors,warnings=validate(args.root,args.strict)
            for error in errors: print('ERROR',error)
            for warning in warnings: print('WARN',warning)
            print(f'{len(errors)} errors; {len(warnings)} pending/review warnings');return bool(errors)
        if args.command in ('scan','stats'):
            rows=scan(args.root,args.category,args.id)
            if args.command=='scan':
                for path,row in rows: print(path.name,row['category'],row.get('id'),row.get('status'),row.get('occurrences'))
            keys=[(r['category'],r.get('context'),r.get('id'),json.dumps(r.get('original'),sort_keys=True)) for _,r in rows]
            print(json.dumps({'records':len(rows),'categories':dict(collections.Counter(r['category'] for _,r in rows)),
                'duplicates':len(keys)-len(set(keys))},ensure_ascii=False));return 0
        if args.command=='export': print('Exported',export_records(args.root,args.output),'records');return 0
        if args.command=='import':
            count,conflicts=import_records(args.root,args.input);print('Merged',count,'files')
            for conflict in conflicts: print('REVIEW',conflict)
            return 0
        if args.command=='merge':
            count,conflicts=merge_workspace(args.root,args.source);print('Merged',count,'filled dictionary files')
            for conflict in conflicts: print('REVIEW',conflict)
            return 0
        if args.command=='sync':
            if args.github:
                if args.source: raise ValueError('Choose local source OR --github')
                with tempfile.TemporaryDirectory(prefix='sapphire-neu-') as temp:
                    repo=pathlib.Path(temp)/'repo'
                    subprocess.run(['git','clone','--depth','1','https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO.git',str(repo)],check=True)
                    report=sync_neu(repo,args.root)
            elif args.source: report=sync_neu(args.source,args.root)
            else: raise ValueError('sync needs a local NEU repo path or --github')
            print(json.dumps({key:len(value) for key,value in report.items()}));return bool(report['errors'])
    except (ValueError,OSError,KeyError,TypeError,subprocess.CalledProcessError) as exc:
        print('ERROR',exc,file=sys.stderr);return 1
    return 0

if __name__=='__main__': sys.exit(main())
