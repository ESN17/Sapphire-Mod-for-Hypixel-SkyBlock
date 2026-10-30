"""Apply the user's approved enchantment review, preserving unrelated translations.

Run without --apply to stage and validate; --apply installs the validated changes.
Original files are retained under translation-state/enchantments/backup.
"""
import argparse
import collections
import copy
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / 'translation-state/enchantments'
REVIEW = ROOT / 'translation-review/enchantments'
CHANGES = {
    'ABSORB': '采伐专精', 'COMPACT': '采矿专精', 'CULTIVATING': '农业专精',
    'EXPERTISE': '钓鱼专精', 'HECATOMB': '地牢专精',
    'CHAMPION': '近战战斗专精', 'TOXOPHILITE': '远程战斗专精',
    'BIG_BRAIN': '超级大脑', 'BUG_BLENDER': '灭虫时运', 'CAYENNE': '辣椒素',
    'CLEAVE': '横扫之刃', 'ENDER_SLAYER': '末影杀手', 'FIRST_STRIKE': '一次打击',
    'LOOTING': '掠夺', 'PETALFALL': '斩断', 'MAGMARIZER': '史莱姆杀手',
    'SMALL_BRAIN': '超级小脑', 'REJUVENATE': '恢复', 'SMOLDERING': '烈焰人杀手',
    'TABASCO': '塔巴斯科', 'THUNDERBOLT': '雷电群击', 'ARCANE': '林地杀手',
    'ULTIMATE_REFRIGERATE': '冰盾',
}
ALIASES = {
    'Aiming': 'AIMING', 'Arcane': 'ARCANE', 'Dragon Hunter': 'DRAGON_HUNTER',
    'Ferocious Mana': 'FEROCIOUS_MANA', 'Hardened Mana': 'HARDENED_MANA',
    'Mana Vampire': 'MANA_VAMPIRE', 'Strong Mana': 'STRONG_MANA',
    'Magmarizer': 'MAGMARIZER', 'Pristine': 'PRISTINE', 'Syphon': 'SYPHON',
    'Reiterate': 'ULTIMATE_REITERATE',
}
SLOTS = re.compile(r'\{\{[A-Za-z][A-Za-z0-9_]*\}\}|(?<!\{)\{[A-Za-z][A-Za-z0-9_]*\}(?!\})')


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def mentions(source, name):
    return re.search(r'(?<![A-Za-z])' + re.escape(name) + r'(?![A-Za-z])', source) is not None


def revise(item, names, book_names):
    result = copy.deepcopy(item)
    family = item['id'].split(';')[0]
    is_book = item.get('_original_name') == 'Enchanted Book' and family in book_names
    paragraphs = []
    for p in result.get('loreParagraphs', []):
        source = ' '.join(p['source'])
        mappings = p.setdefault('styleMappings', {})
        # Only revise proven enchantment references, never generic stat words.
        if is_book or item['id'] == 'MAXED_ENCHANT_BOOK' or re.search(r'\benchant(?:ment|ments|s|ed)?\b', source, re.I):
            changes = {}
            for key, old in mappings.items():
                for english, chinese in names.items():
                    if key == english:
                        changes[old] = chinese
                    elif re.fullmatch(re.escape(english) + r' [IVXLCDM]+', key):
                        changes[old] = chinese + key[len(english):]
            # Unmapped literal English names and previously reviewed Chinese names
            # are safe only if the English source identifies that enchantment.
            for english, chinese in names.items():
                if mentions(source, english) and english in p['translation']:
                    changes[english] = chinese
            if changes:
                pattern = re.compile('|'.join(re.escape(k) for k in sorted(changes, key=len, reverse=True) if k))
                p['translation'] = pattern.sub(lambda m: changes[m.group()], p['translation'])
                for key, value in list(mappings.items()):
                    mappings[key] = pattern.sub(lambda m: changes[m.group()], value)
        if family == 'LION' and mentions(source, 'First Strike'):
            p['translation'] = p['translation'].replace('先攻', names['First Strike'])
            mappings['First Strike'] = names['First Strike']
            mappings['Triple-Strike'] = names['Triple-Strike']
        if family == 'PROSECUTE' and 'your target has.' in source:
            p['translation'] = p['translation'].replace('每损失百分之一', '每剩余百分之一')
        if is_book:
            english = book_names[family]
            first = p['source'][0]
            title = re.fullmatch(re.escape(english) + r' ([IVXLCDM]+|\{\{N\d+\}\})', first)
            if title:
                level = title.group(1)
                prefix = re.match(r'^.+? ' + re.escape(level) + r'(?:\s+|$)', p['translation'])
                if not prefix:
                    raise ValueError(f"Cannot split title: {item['id']} {p['translation']}")
                # The dedicated runtime dictionary renders the title independently.
                # Keep a standalone dictionary entry as a fallback for older loaders.
                paragraphs.append({'source': [first], 'translation': names[english] + ' ' + level,
                                   'styleMappings': {english: names[english]}})
                if len(p['source']) > 1:
                    p['source'] = p['source'][1:]
                    p['translation'] = p['translation'][prefix.end():]
                    p['styleMappings'] = {k: v for k, v in mappings.items() if k not in (first, english)}
                    paragraphs.append(p)
                continue
            # Conflict lists have no levels. Translate each name independently so
            # commas/line boundaries and different colors cannot collapse together.
            if all(line.strip().startswith('- ') and line.strip()[2:] in names for line in p['source']):
                for line in p['source']:
                    name = line.strip()[2:]
                    paragraphs.append({'source': [line], 'translation': line[:line.index(name)] + names[name],
                                       'styleMappings': {name: names[name]}})
                continue
            if len(p['source']) == 1 and p['source'][0] == f'▲ {english} cannot be combined!':
                p['translation'] = f'▲ {names[english]}无法合并!'
                p['styleMappings'] = {english: names[english]}
        paragraphs.append(p)
    result['loreParagraphs'] = paragraphs
    for p in paragraphs:
        assert collections.Counter(SLOTS.findall(' '.join(p['source']))) == collections.Counter(SLOTS.findall(p['translation'])), (item['id'], p)
        assert p['translation'].strip(), item['id']
    assert result.get('_original_lore') == item.get('_original_lore')
    assert result.get('_original_name') == item.get('_original_name')
    return result


def main():
    apply = argparse.ArgumentParser()
    apply.add_argument('--apply', action='store_true')
    args = apply.parse_args()
    catalog = read(REVIEW / 'review-catalog.json')
    proposals = read(REVIEW / 'name-proposals.json')
    for row in catalog['entries']:
        row['proposedChinese'] = CHANGES.get(row['id'], row['proposedChinese'])
        row['status'] = 'approved'
        proposals['proposals'][row['id']][0] = row['proposedChinese']
    names = {row['english']: row['proposedChinese'] for row in catalog['entries']}
    book_names = {row['id']: row['english'] for row in catalog['entries']}
    assert len(names) == 156
    ids = {row['english']: row['id'] for row in catalog['entries']}
    for alias, family in ALIASES.items():
        names[alias] = names[book_names[family]]
        ids[alias] = family
    catalog['status'] = proposals['status'] = 'approved-by-user'
    catalog['approvalNote'] = '2026-10-17：按用户逐项修订批准接入；Champion=近战战斗专精，Toxophilite=远程战斗专精。'
    proposals['notice'] = '用户已审核的名称；用途简述保留用于说明，正式效果仍以英文元数据为依据。'
    # Discover candidates from the existing archive without walking unrelated item files.
    candidates = []
    with zipfile.ZipFile(ROOT / 'build/libs/sapphire-1.0.0.jar') as jar:
        for path in jar.namelist():
            if not path.startswith('defaults/translations/items/') or not path.endswith('.json'):
                continue
            item = json.loads(jar.read(path))
            if item.get('_original_name') == 'Enchanted Book' or item['id'] == 'MAXED_ENCHANT_BOOK' or item['id'].startswith('LION;') or any(
                re.search(r'\benchant(?:ment|ments|s|ed)?\b', ' '.join(p['source']), re.I)
                and any(mentions(' '.join(p['source']), name) for name in names)
                for p in item.get('loreParagraphs', [])
            ):
                candidates.append(Path(path).name)
    roots = [ROOT / 'src/main/resources/defaults/translations', ROOT / 'dictionary-workspace/translations', ROOT / 'run/config/sapphire/translations']
    pending = {}
    counts = {}
    for directory in roots:
        changed = []
        for filename in candidates:
            file = directory / 'items' / filename
            if not file.exists():
                continue
            item = read(file)
            revised = revise(item, names, book_names)
            if revised != item:
                pending[file] = encoded(revised)
                changed.append(item['id'])
        pending[directory / 'enchantments.json'] = encoded(names)
        pending[directory / 'enchantment-ids.json'] = encoded(ids)
        global_path = directory / 'global.json'
        global_terms = read(global_path)
        global_terms['Enchanted Book'] = '附魔书'
        pending[global_path] = encoded(global_terms)
        counts[str(directory.relative_to(ROOT))] = changed
    pending[REVIEW / 'review-catalog.json'] = encoded(catalog)
    pending[REVIEW / 'name-proposals.json'] = encoded(proposals)
    approved = ['Sapphire 附魔译名已审核清单', catalog['approvalNote'], '', '共 156 项；累计附魔按经验类型命名。', '']
    for row in catalog['entries']:
        approved.append(f"{row['number']:03d}. {row['english']} → {row['proposedChinese']}")
    pending[REVIEW / '附魔译名已确认.txt'] = ('\n'.join(approved) + '\n').encode('utf-8-sig')
    for path, data in pending.items():
        relative = path.relative_to(ROOT)
        staged = STATE / 'staged' / relative
        staged.parent.mkdir(parents=True, exist_ok=True)
        staged.write_bytes(data)
        if args.apply:
            if path.exists():
                backup = STATE / 'backup' / relative
                if not backup.exists():
                    backup.parent.mkdir(parents=True, exist_ok=True)
                    backup.write_bytes(path.read_bytes())
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    report = {'applied': args.apply, 'families': 156, 'legacyAliases': len(ALIASES), 'changedItems': counts, 'files': len(pending)}
    STATE.mkdir(parents=True, exist_ok=True)
    (STATE / 'report.json').write_bytes(encoded(report))
    print(json.dumps({'applied': args.apply, 'families': 156, 'legacyAliases': len(ALIASES), 'changedItems': {k: len(v) for k, v in counts.items()}, 'files': len(pending)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
