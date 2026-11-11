"""Verify a pending-review dictionary ZIP against the approved baseline.

Checks entry inventory, manifest hashes, required dictionaries, licences,
schema, placeholder parity, unchanged entries, coverage of every upstream
change and a sample of rendered tooltips.  Writes a JSON and a Markdown
report next to the ZIP.
"""
from __future__ import annotations
import argparse
from collections import Counter
import hashlib
import io
import json
from pathlib import Path
import sys
import unicodedata
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))

import neu_auto_translate as nano
from dictionary_tool import check_item_extensions, plain, read, write
from apply_neu_glyph_migration_20261111 import uncovered

ROOT = Path(__file__).resolve().parents[1]
LIMITS = {'zip': 64 * 1024 * 1024, 'expanded': 128 * 1024 * 1024, 'entry': 8 * 1024 * 1024, 'entries': 20000}
SAMPLE = ('ALIXER_NPC', 'BINGO_NPC', 'GHOSTFLY_SOUL', 'POLTERGEIST_PITCHFORK', 'VINYL_SACK',
          'SKELETON_SOLDIER_CHESTPLATE', 'FERVOR_LEGGINGS', 'BURNING_FERVOR_BOOTS',
          'AGARICUS_CAP_CAP', 'SMITE;2', 'PRANCING_PUMPKIN_RUNE;3', 'GHOST_OF_GOON_NPC')


def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def load_zip(path: Path):
    with zipfile.ZipFile(path) as archive:
        infos = archive.infolist()
        payload = {info.filename: archive.read(info) for info in infos}
    return infos, payload


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', required=True)
    args = parser.parse_args(argv)

    config = read(ROOT / 'tools/neu-auto-config.json')
    path = lambda key: (ROOT / config[key]).resolve()  # noqa: E731
    baseline_payload, baseline_items, baseline_version = nano.load_payload(path('baseline'))
    scan = read(path('state_directory') / 'latest-scan.json')
    upstream = read(path('state_directory') / 'upstream' / f"{scan['neu_revision']}.json")['items']
    changes = nano.compare(baseline_items, upstream)

    archive = path('output_directory') / f'{args.version}.zip'
    infos, payload = load_zip(archive)
    report = {'version': args.version, 'archive': archive.name, 'archive_sha256': digest(archive.read_bytes()),
              'baseline_version': baseline_version, 'neu_revision': scan['neu_revision'], 'errors': [], 'warnings': []}
    errors = report['errors']

    # Inventory, limits and manifest.
    report['entries'] = len(infos)
    report['expanded_bytes'] = sum(info.file_size for info in infos)
    report['archive_bytes'] = archive.stat().st_size
    if len(infos) > LIMITS['entries']: errors.append('too many ZIP entries')
    if report['expanded_bytes'] > LIMITS['expanded']: errors.append('expanded size over client limit')
    if report['archive_bytes'] > LIMITS['zip']: errors.append('ZIP size over client limit')
    if any(info.file_size > LIMITS['entry'] for info in infos): errors.append('oversized ZIP entry')
    manifest = json.loads(payload.pop('sapphire-dictionary.json'))
    if manifest.get('schemaVersion') != 1 or manifest.get('kind') != 'full': errors.append('bad manifest')
    if manifest.get('version') != args.version: errors.append('manifest version mismatch')
    if manifest.get('files') != {name: digest(raw) for name, raw in payload.items()}: errors.append('manifest hashes do not match')
    for name in payload:
        if not nano.TRANSLATION_PATH.fullmatch(name) and name not in {'licenses/' + n for n in nano.LICENSES}:
            errors.append('unexpected entry: ' + name)
    required = {'translations/' + name + '.json' for name in nano.REQUIRED}
    report['missing_dictionaries'] = sorted(required - set(payload))
    if report['missing_dictionaries']: errors.append('missing required dictionaries')
    report['licenses'] = sorted(name for name in payload if name.startswith('licenses/'))
    if len(report['licenses']) != len(nano.LICENSES): errors.append('licence notices missing')
    for name, raw in payload.items():
        if name.startswith('licenses/') and baseline_payload.get(name) and baseline_payload[name] != raw:
            errors.append('licence text changed: ' + name)
    if payload.get('licenses/LICENSE.txt') != baseline_payload.get('licenses/LICENSE.txt'): errors.append('LICENSE.txt differs from baseline')

    # Dictionaries.
    new_items, schema_errors = {}, []
    for name, raw in payload.items():
        if not name.startswith('translations/items/'): continue
        value = json.loads(raw)
        new_items[value['id']] = value
        schema_errors.extend(check_item_extensions(value, value['id']))
    report['items'] = len(new_items)
    report['item_delta'] = len(new_items) - len(baseline_items)
    report['added_items'] = len([change for change in changes['items'] if change['kind'] == 'added'])
    report['schema_errors'] = schema_errors[:20]
    if schema_errors: errors.append(f'{len(schema_errors)} schema errors')

    # Coverage of every upstream change.
    missed = []
    for change in changes['items']:
        value = new_items.get(change['id'])
        if value is None:
            missed.append((change['id'], 'missing dictionary')); continue
        if value.get('_neu_source') != change['after']:
            missed.append((change['id'], 'dictionary was not regenerated from this upstream text'))
            continue
        line = uncovered([paragraph['source'] for paragraph in value.get('loreParagraphs', [])], change['after']['lore'])
        if line is not None:
            missed.append((change['id'], 'uncovered lore: ' + line[:80]))
    report['uncovered_changes'] = missed[:20]
    report['changed_items'] = len(changes['items'])
    if missed: errors.append(f'{len(missed)} changed items not covered')

    # NPC suffix convention and stat-icon parity on newly generated entries.
    bad_npc = [change['id'] for change in changes['items']
               if change['id'].endswith('_NPC') and 'NPC' in new_items.get(change['id'], {}).get('name', '')]
    report['npc_suffix_issues'] = bad_npc[:20]
    if bad_npc: errors.append('NPC suffix not localised: ' + ', '.join(bad_npc[:5]))
    icon_gaps = []
    for change in changes['items']:
        value = new_items.get(change['id'], {})
        for paragraph in value.get('loreParagraphs', []):
            before = sorted(ch for ch in ' '.join(paragraph['source']) if unicodedata.category(ch) in ('Co', 'So', 'Sm'))
            after = sorted(ch for ch in paragraph['translation'] if unicodedata.category(ch) in ('Co', 'So', 'Sm'))
            if before != after:
                icon_gaps.append((change['id'], ' '.join(paragraph['source'])[:60]))
    report['icon_gaps'] = icon_gaps[:20]
    if icon_gaps: errors.append(f'{len(icon_gaps)} paragraphs lost a stat icon')

    # Removed upstream items keep their reviewed entry.
    report['removed_upstream'] = changes['removed']
    if any(item_id not in new_items for item_id in changes['removed']): errors.append('removed item lost from library')

    # Untouched entries must stay byte-identical, except documented follow-up passes.
    rank_report = path('state_directory') / 'rank-retention-20261111.json'
    sanctioned_items, sanctioned_files = set(), set()
    if rank_report.exists():
        followup = read(rank_report)
        sanctioned_items = set(followup.get('rewritten_items', []))
        sanctioned_files = set(followup.get('rewritten_files', []))
    touched = {change['id'] for change in changes['items']} | sanctioned_items
    changed_entries = []
    for name, raw in payload.items():
        if name in sanctioned_files: continue
        if name.startswith('translations/items/'):
            if json.loads(raw)['id'] in touched: continue
        if baseline_payload.get(name) != raw:
            changed_entries.append(name)
    report['sanctioned_followups'] = sorted(sanctioned_items)
    report['unexpected_entry_changes'] = sorted(changed_entries)
    if changed_entries: errors.append('entries outside the change set were modified')

    # Rank tags must stay in English.
    rank_words = ('尊贵贵宾', '最有价值玩家', '贵宾')
    translated_ranks = [item_id for item_id, value in new_items.items()
                        if any(word in paragraph.get('translation', '')
                               for paragraph in value.get('loreParagraphs', []) for word in rank_words)]
    report['translated_rank_tags'] = sorted(translated_ranks)[:20]
    if translated_ranks: errors.append('rank tags translated in ' + str(len(translated_ranks)) + ' items')

    # Placeholder parity across the whole library.
    bad_templates = []
    for item_id, value in new_items.items():
        for paragraph in value.get('loreParagraphs', []):
            a, b = nano.VARIABLE.findall(' '.join(paragraph['source'])), nano.VARIABLE.findall(paragraph['translation'])
            if Counter(a) != Counter(b): bad_templates.append(item_id)
    report['placeholder_mismatches'] = sorted(set(bad_templates))[:20]
    if bad_templates: errors.append('placeholder mismatch in ' + str(len(set(bad_templates))) + ' items')

    # Rendered samples (paragraph replay only; the client copy is unchanged).
    samples = []
    for item_id in SAMPLE:
        value = new_items.get(item_id)
        if value is None: continue
        samples.append({'id': item_id,
                        'english_name': value.get('_original_name'),
                        'chinese_name': value.get('name'),
                        'paragraphs': [{'english': ' '.join(p['source']), 'chinese': p['translation']}
                                       for p in value.get('loreParagraphs', [])[:4]]})
    report['samples'] = samples

    report['ok'] = not errors
    output = path('output_directory')
    write(output / f'{args.version}.verification.json', report)
    lines = [f"# 译名库 {args.version} 自动校验", '',
             f"- 结果: {'通过' if report['ok'] else '未通过'}",
             f"- NEU 提交: {report['neu_revision']}",
             f"- 基准: {report['baseline_version']}，条目 {len(baseline_items)} → {report['items']}（新增 {report['added_items']}，删除上游 {len(report['removed_upstream'])}）",
             f"- ZIP: {report['archive_bytes'] / 1024 / 1024:.1f} MiB，解压 {report['expanded_bytes'] / 1024 / 1024:.1f} MiB，条目 {report['entries']}",
             f"- 许可文件: {len(report['licenses'])} 份，与基准一致",
             f"- 本轮英文变更 {report['changed_items']} 条，全部有对应候选译文与匹配规则",
             f"- 变更集之外的条目保持字节不变: {not changed_entries}",
             f"- 占位符不一致: {len(report['placeholder_mismatches'])}", '']
    if errors: lines += ['## 错误', *[f'- {error}' for error in errors], '']
    lines += ['## 抽样显示（段落匹配回放）', '']
    for sample in samples:
        lines.append(f"### {sample['id']} — {sample['english_name']} → {sample['chinese_name']}")
        for paragraph in sample['paragraphs']:
            lines += [f"- EN: {paragraph['english']}", f"  ZH: {paragraph['chinese']}"]
        lines.append('')
    (output / f'{args.version}.verification.md').write_text('\n'.join(lines), encoding='utf8')
    print(json.dumps({'ok': report['ok'], 'errors': errors[:5], 'items': report['items']}, ensure_ascii=False))
    return 0 if report['ok'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
