"""Keep server rank names in English in the review ZIP.

User rule 2026-11-11: [VIP], [VIP+], [MVP], [MVP+] and [MVP++] (including the
coloured spellings) stay English; the same goes for bare "MVP+" in requirement
lines.  Item lore, legacy lore maps, wiki entries and the one item name that
used a translated rank are all covered.  Only the rank token is rewritten; the
rest of the line and player names stay untouched.  The ZIP manifest is
regenerated so the client updater still accepts the file.
"""
from __future__ import annotations
import json
from pathlib import Path
import re
import sys
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))

import neu_auto_translate as nano
from dictionary_tool import read, write

ROOT = Path(__file__).resolve().parents[1]
RANK_SOURCE = re.compile(r'\[[^\[\]]{0,16}?(VIP|MVP)(\+{0,2})[^\[\]]{0,8}\]')
BARE_RANK = re.compile(r'(?<![A-Za-z])(VIP|MVP)(\+{0,2})(?![A-Za-z+])')
RANK_CHINESE = re.compile(r'(尊贵贵宾|最有价值玩家|贵宾|最尊贵会员|尊贵会员|会员)(\+{0,2})')
BRACKET_CHINESE = re.compile(r'\[[^\[\]]{0,16}[\u3400-\u9fff][^\[\]]{0,6}\]')
# The only reviewed item name that rendered a rank in Chinese.
RANK_NAMES = {'VOTER_BADGE_VIP': 'VIP 选民徽章'}
# Its item-level style mapping needs the same spelling as the name.
RANK_MAPS = {('VOTER_BADGE_VIP', "VIP Voter's Badge"): 'VIP 选民徽章'}


def english_tag(text: str) -> str | None:
    match = RANK_SOURCE.search(text)
    if match:
        return f'[{match.group(1)}{match.group(2)}]'
    match = BARE_RANK.search(text)
    if match:
        return f'{match.group(1)}{match.group(2)}'
    return None


def apply_tag(text: str, tag: str) -> str:
    """Replace the Chinese rank, keeping exactly one pair of brackets."""
    if tag.startswith('[') and BRACKET_CHINESE.search(text):
        return BRACKET_CHINESE.sub(tag, text, count=1)
    return RANK_CHINESE.sub(tag, text)


def fix_item_maps(value: dict) -> bool:
    """Item-level styleMappings can hold a translated rank too."""
    changed = False
    maps = value.get('styleMappings')
    if isinstance(maps, dict):
        for key, target in list(maps.items()):
            if not isinstance(key, str) or not isinstance(target, str):
                continue
            tag = english_tag(key)
            if tag and RANK_CHINESE.search(target):
                maps[key] = apply_tag(target, tag)
                changed = True
    return changed


def rewrite_node(node: dict, tags: list[str]) -> bool:
    """Handle dictionaries shaped as {source, translation, styleMappings}."""
    source = node.get('source')
    translation = node.get('translation')
    if isinstance(source, list):
        source = ' '.join(str(part) for part in source)
    if not isinstance(source, str) or not isinstance(translation, str):
        return False
    tag = english_tag(source)
    if tag is None or not RANK_CHINESE.search(translation):
        return False
    node['translation'] = apply_tag(translation, tag)
    maps = node.get('styleMappings')
    if isinstance(maps, dict):
        for key, value in list(maps.items()):
            if isinstance(value, str) and RANK_CHINESE.search(value):
                maps[key] = apply_tag(value, tag)
    tags.append(tag)
    return True


def walk(node, tags: list[str]) -> bool:
    changed = False
    if isinstance(node, dict):
        if rewrite_node(node, tags):
            changed = True
        for value in node.values():
            changed = walk(value, tags) or changed
    elif isinstance(node, list):
        for value in node:
            changed = walk(value, tags) or changed
    return changed


def main() -> int:
    version = sys.argv[1] if len(sys.argv) > 1 else '20261111_1'
    archive = ROOT / 'exports/dictionaries/pending-review' / f'{version}.zip'
    with zipfile.ZipFile(archive) as z:
        manifest = json.loads(z.read('sapphire-dictionary.json'))
        payload = {info.filename: z.read(info) for info in z.infolist()
                   if info.filename != 'sapphire-dictionary.json'}

    rewritten_files, replacements = [], []
    for name, raw in sorted(payload.items()):
        if not name.endswith('.json'):
            continue
        value = json.loads(raw)
        before = json.dumps(value, ensure_ascii=False, sort_keys=True)
        tags: list[str] = []
        if name.startswith('translations/items/'):
            for paragraph in value.get('loreParagraphs', []):
                if rewrite_node(paragraph, tags):
                    continue
            for source, target in list(value.get('lore', {}).items()):
                tag = english_tag(source)
                if tag and RANK_CHINESE.search(target):
                    value['lore'][source] = apply_tag(target, tag)
                    tags.append(tag)
        else:
            walk(value, tags)
        if value.get('id') in RANK_NAMES and value.get('name') != RANK_NAMES[value['id']]:
            value['name'] = RANK_NAMES[value['id']]
            tags.append('name')
        if fix_item_maps(value):
            tags.append('item-styleMappings')
        for (item_id, key), target in RANK_MAPS.items():
            if value.get('id') == item_id and isinstance(value.get('styleMappings'), dict) \
                    and value['styleMappings'].get(key) != target:
                value['styleMappings'][key] = target
                tags.append('item-styleMappings')
        if json.dumps(value, ensure_ascii=False, sort_keys=True) != before:
            payload[name] = nano.encoded(value)
            rewritten_files.append(name)
            replacements += [{'file': name, 'tag': tag} for tag in tags]

    manifest['files'] = {name: nano.digest(raw) for name, raw in payload.items()}
    temporary = archive.with_suffix('.zip.tmp')
    with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        z.writestr('sapphire-dictionary.json', nano.encoded(manifest))
        for name in sorted(payload):
            z.writestr(name, payload[name])
    temporary.replace(archive)

    # Keep the SHA-256 sidecar and the review JSON in step with the rewritten ZIP.
    sha = nano.digest(archive.read_bytes())
    (archive.parent / f'{version}.zip.sha256').write_text(f'{sha}  {archive.name}\n', encoding='utf8')
    review = archive.parent / f'{version}.review.json'
    if review.exists():
        data = read(review)
        data['sha256'] = sha
        write(review, data)

    report_path = ROOT / 'translation-state/neu-auto/rank-retention-20261111.json'
    previous = read(report_path) if report_path.exists() else {}
    write(report_path, {'version': version,
                        'rewritten_files': sorted(set(previous.get('rewritten_files', [])) | set(rewritten_files)),
                        'rewritten_items': sorted(set(previous.get('rewritten_items', [])) |
                                                  {Path(item).stem for item in rewritten_files if item.startswith('translations/items/')}),
                        'rank_replacements': previous.get('rank_replacements', []) + replacements})
    print(json.dumps({'files': len(rewritten_files), 'replacements': len(replacements), 'sha256': sha}, ensure_ascii=False))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
