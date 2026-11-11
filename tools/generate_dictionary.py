#!/usr/bin/env python3
"""Generate editable translation stubs from a local NEU-Repo items directory."""
import json, pathlib, sys
if len(sys.argv) != 3:
    raise SystemExit('Usage: python tools/generate_dictionary.py PATH_TO_NEU_REPO/items PATH_TO_OUTPUT/items')
source, output = map(pathlib.Path, sys.argv[1:]); output.mkdir(parents=True, exist_ok=True)
count = 0
for path in sorted(source.glob('*.json')):
    try:
        data = json.loads(path.read_text(encoding='utf-8'))
        item_id = data.get('internalname') or path.stem
        if not isinstance(item_id,str) or '/' in item_id or '\\' in item_id: continue
        dest = output / (item_id + '.json')
        if dest.exists(): continue
        lore = data.get('lore', [])
        result = {'id':item_id, '_original_name':data.get('displayname',''), 'name':'', 'lore':{}, '_original_lore':lore}
        dest.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
        count += 1
    except (ValueError, OSError, TypeError) as exc: print('Skipped',path,exc)
print('Generated', count, 'new entries; existing translations preserved')
