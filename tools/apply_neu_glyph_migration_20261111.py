"""Prepare deterministic NEU icon-glyph migrations for the 2026-11-11 review run.

NEU replaced several Unicode symbol glyphs with Minecraft private-use glyph
characters (and, for a few items, the other way round).  The wording of those
items is unchanged, so the already reviewed Chinese text stays valid; only the
icon code points inside `source` and the translated lines have to follow the
new upstream spelling.

The script reads the approved baseline ZIP, the cached upstream snapshot from
the latest scan and writes ready-made item dictionaries into the incremental
tool's candidate cache.  `neu_auto_translate.py run` then reuses them without
calling the API.  Genuine wording changes are left untouched for the normal
translation path.
"""
from __future__ import annotations
import argparse
from copy import deepcopy
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))

import neu_auto_translate as nano
from dictionary_tool import check_item_extensions, plain, read, write

ROOT = Path(__file__).resolve().parents[1]

# Old symbol / new private-use glyph equivalents observed in NEU master.
GLYPH_CLASSES = (
    ('\ue070', '\u2708'), ('\ue071', '\u262e'), ('\ue072', '\u2693'), ('\ue073', '\u2643'),
    ('\ue075', '\u2699'), ('\ue076', '\u2682'), ('\ue077', '\u2743'), ('\ue078', '\u2299'),
    ('\ue079', '\u2603'), ('\ue07a', '\u2744'), ('\ue07b', '\u2730'), ('\ue07c', '\u2668'),
    ('\ue07d', '\u2646'), ('\ue07f', '\ue018'), ('\ue080', '\u26e8'), ('\ue081', '\U0001f9b4'),
    ('\ue083', '\u26cf'), ('\ue084', '\u0f15'), ('\ue085', '\u2620'), ('\ue086', '\u2e19'),
    ('\ue01f', '\u2764'), ('\ue020', '\u0444'),
)
CANON = {member: family[0] for family in GLYPH_CLASSES for member in family}


def canonical(text: str) -> str:
    return ''.join(CANON.get(char, char) for char in text)


def item_replacement(upstream: dict, item_id: str) -> dict:
    """Pick the glyph spelling this item now uses; refuse ambiguous items."""
    lore = [plain(line) for line in upstream['lore']]
    joined = '\n'.join(lore)
    replacement = {}
    for family in GLYPH_CLASSES:
        present = [member for member in family if member in joined]
        if len(present) > 1:
            raise ValueError(f'{item_id}: mixed glyph spellings in upstream lore: {present!r}')
        if present:
            for member in family:
                replacement[member] = present[0]
    return replacement


def migrate(old: dict, upstream: dict) -> tuple[dict, int]:
    item_id = upstream['id']
    replacement = item_replacement(upstream, item_id)

    def fix(value: str) -> str:
        return ''.join(replacement.get(char, char) for char in value)

    value = deepcopy(old)
    paragraphs = []
    seen = set()
    dropped = 0
    for paragraph in value.get('loreParagraphs', []):
        paragraph['source'] = [fix(line) for line in paragraph['source']]
        paragraph['translation'] = fix(paragraph['translation'])
        paragraph['styleMappings'] = {fix(key): fix(target) for key, target in (paragraph.get('styleMappings') or {}).items()}
        key = tuple(plain(line) for line in paragraph['source'])
        if key in seen:
            # Two rules that only differed by the old icon spelling collapse into one.
            dropped += 1
            continue
        seen.add(key)
        paragraphs.append(paragraph)
    value['loreParagraphs'] = paragraphs
    value['styleMappings'] = {fix(key): fix(target) for key, target in (value.get('styleMappings') or {}).items()}
    value['_original_name'] = plain(upstream['displayname'])
    value['_original_lore'] = [plain(line) for line in upstream['lore']]
    value['_neu_source'] = upstream
    return value, dropped


def uncovered(paragraphs: list[list[str]], lines: list[str]) -> str | None:
    """Replay the client matcher: paragraphs consume consecutive non-blank lines."""
    index = 0
    while index < len(lines):
        if not plain(lines[index]).strip():
            index += 1
            continue
        matched = False
        for source in paragraphs:
            target = nano.bulk.normalize(' '.join(source))[0]
            for count in range(1, min(16, len(lines) - index) + 1):
                if count > 1 and not plain(lines[index + count - 1]).strip():
                    break
                combined = ' '.join(plain(line) for line in lines[index:index + count])
                if nano.bulk.normalize(combined)[0] == target:
                    index += count
                    matched = True
                    break
            if matched:
                break
        if not matched:
            return plain(lines[index])
    return None


def verify(value: dict, upstream: dict) -> list[str]:
    """Check the schema and that every upstream line is still translated."""
    errors = check_item_extensions(value, upstream['id'])
    paragraphs = [paragraph['source'] for paragraph in value.get('loreParagraphs', [])]
    missed = uncovered(paragraphs, upstream['lore'])
    if missed is not None:
        errors.append(f"{upstream['id']}: lore line no longer covered: {missed[:120]!r}")
    return errors


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dry-run', action='store_true', help='report without writing candidates')
    args = parser.parse_args(argv)

    config = read(ROOT / 'tools/neu-auto-config.json')
    path = lambda key: (ROOT / config[key]).resolve()  # noqa: E731
    payload, items, _ = nano.load_payload(path('baseline'))
    state = path('state_directory')
    scan = read(state / 'latest-scan.json')
    upstream = read(state / 'upstream' / f"{scan['neu_revision']}.json")['items']
    rules = read(path('rules'))
    policy_hash = nano.digest(rules)
    base_hash = nano.digest({name: nano.digest(raw) for name, raw in payload.items()})
    changes = nano.compare(items, upstream)['items']

    migrated, skipped, failed, collapsed = [], [], [], {}
    for change in changes:
        if change['kind'] != 'changed' or 'name' in change.get('fields', []):
            continue
        old = items[change['id']][1]
        before = [canonical(plain(line)) for line in nano.previous_source(old)['lore']]
        after = [canonical(plain(line)) for line in change['after']['lore']]
        if before != after:
            skipped.append(change['id'])
            continue
        try:
            value, dropped = migrate(old, change['after'])
            errors = verify(value, change['after'])
            if errors:
                failed.append({'id': change['id'], 'errors': errors[:5]})
                continue
        except ValueError as error:
            failed.append({'id': change['id'], 'errors': [str(error)]})
            continue
        migrated.append(change['id'])
        if dropped:
            collapsed[change['id']] = dropped
        if not args.dry_run:
            key = nano.digest([old, change['after'], policy_hash, base_hash])
            write(state / 'candidates' / f'{key}.json', value)

    report = {'baseline': config['baseline'], 'neu_revision': scan['neu_revision'],
              'glyph_classes': [list(family) for family in GLYPH_CLASSES],
              'migrated': sorted(migrated), 'collapsed_duplicate_paragraphs': collapsed,
              'wording_changes_left_for_model': sorted(skipped),
              'failed': failed, 'dry_run': args.dry_run}
    write(state / 'glyph-migration-20261111.json', report)
    print(json.dumps({'migrated': len(migrated), 'wording_changes': len(skipped),
                      'failed': len(failed), 'dry_run': args.dry_run}, ensure_ascii=False))
    return 1 if failed else 0


if __name__ == '__main__':
    raise SystemExit(main())
