"""Fill the new "max 25M" cap into the Fervor tooltips.

NEU appended ", max 25M" to the Ground Pound paragraph of every Fervor armour
tier.  The already reviewed Fervor wording carries the approved cap clause, so
this step extends each paragraph with the same phrasing instead of asking the
model for a second wording.

Run after the rules hash changed; results go to the incremental tool's
candidate cache.
"""
from __future__ import annotations
import json
from copy import deepcopy
from pathlib import Path
import re
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))

import neu_auto_translate as nano
from dictionary_tool import check_item_extensions, plain, read, write
from apply_neu_glyph_migration_20261111 import uncovered

ROOT = Path(__file__).resolve().parents[1]
OLD_TAIL = 'EHP).'


def signature(text: str) -> str:
    return re.sub(r'\{\{N[0-9]+\}\}', '', text)[:40]


def patch(old: dict, upstream: dict) -> dict | None:
    value = deepcopy(old)
    context = plain(upstream['displayname'])
    groups = [nano.unit(lines, 'lore', context) for lines in nano.bulk.groups(upstream['lore'])]
    patched = 0
    for paragraph in value['loreParagraphs']:
        if not paragraph['source'] or not paragraph['source'][-1].endswith(OLD_TAIL):
            continue
        joined = ' '.join(paragraph['source'])
        for job in groups:
            if 'EHP, max ' not in job['source']:
                continue
            if not signature(joined) == signature(job['source']):
                continue
            cap = '{{N' + str(len(job['values']) - 1) + '}}'
            paragraph['source'] = job['lines']
            assert paragraph['translation'].endswith(').'), paragraph['translation']
            paragraph['translation'] = paragraph['translation'][:-2] + ',上限 ' + cap + 'M).'
            mappings = dict(paragraph.get('styleMappings') or {})
            mappings.pop('(Damage scales with your EHP).', None)
            mappings.update({'Damage scales with your': '伤害随你的', 'EHP': '有效生命值', 'max': '上限'})
            paragraph['styleMappings'] = mappings
            patched += 1
            break
    if not patched:
        return None
    value['_original_name'] = plain(upstream['displayname'])
    value['_original_lore'] = [plain(line) for line in upstream['lore']]
    value['_neu_source'] = upstream
    return value


def main() -> int:
    config = read(ROOT / 'tools/neu-auto-config.json')
    path = lambda key: (ROOT / config[key]).resolve()  # noqa: E731
    payload, items, _ = nano.load_payload(path('baseline'))
    scan = read(path('state_directory') / 'latest-scan.json')
    upstream = read(path('state_directory') / 'upstream' / f"{scan['neu_revision']}.json")['items']
    rules = read(path('rules'))
    policy_hash = nano.digest(rules)
    base_hash = nano.digest({name: nano.digest(raw) for name, raw in payload.items()})
    changes = {change['id']: change for change in nano.compare(items, upstream)['items']}

    written, skipped, failed = [], [], []
    for item_id in sorted(changes):
        if 'FERVOR' not in item_id or 'AURORA' in item_id:
            continue
        change = changes[item_id]
        if change['kind'] != 'changed' or 'name' in change.get('fields', []):
            continue
        old = items.get(item_id, (None, {}))[1]
        value = patch(old, change['after'])
        if value is None:
            skipped.append(item_id)
            continue
        errors = check_item_extensions(value, item_id)
        missed = uncovered([p['source'] for p in value['loreParagraphs']], change['after']['lore'])
        if missed is not None:
            errors.append(f'{item_id}: lore line no longer covered: {missed[:120]!r}')
        if errors:
            failed.append({'id': item_id, 'errors': errors[:5]})
            continue
        key = nano.digest([old, change['after'], policy_hash, base_hash])
        write(path('state_directory') / 'candidates' / f'{key}.json', value)
        written.append(item_id)

    write(path('state_directory') / 'fervor-ehp-20261111.json', {'written': written, 'skipped': skipped, 'failed': failed})
    print(json.dumps({'written': len(written), 'skipped': len(skipped), 'failed': len(failed)}, ensure_ascii=False))
    return 1 if failed else 0


if __name__ == '__main__':
    raise SystemExit(main())
