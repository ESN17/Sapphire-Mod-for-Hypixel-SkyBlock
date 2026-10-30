"""Apply the user's first feedback, backing up only changed local dictionaries.

No API calls. English matching keys and numeric placeholders remain unchanged.
"""
import copy
import json
import re
import shutil
import zipfile
from pathlib import Path

import dictionary_tool as dt
from bulk_translate import normalize

BASE = Path(__file__).resolve().parent.parent
SPEC = dt.read(BASE / 'tools/translation_revision_v2.json')
ROOTS = ('src/main/resources/defaults', 'dictionary-workspace', 'run/config/sapphire')
STATE = BASE / 'translation-state/first-feedback'
ALIASES = {}


def contains(source, term):
    source = dt.VARIABLE.sub('', source)
    suffix = 's?' if term in ('Catacomb', 'Zealot', 'Hotspot', 'Armadillo') else ''
    return re.search(r'(?<![A-Za-z])' + re.escape(term) + suffix + r'(?![A-Za-z])', source, re.I)


def replace_literal(target, old, new):
    pattern = re.escape(old)
    if old[0].isascii() and old[0].isalpha():
        pattern = r'(?<![A-Za-z])' + pattern
    if old[-1].isascii() and old[-1].isalpha():
        pattern += r'(?![A-Za-z])'
    return re.sub(pattern, lambda _: new, target, flags=re.I)


def rewrite(source, target):
    commands = []
    def protect(match):
        commands.append(match.group())
        return '__SAPPHIRE_COMMAND_' + str(len(commands)-1) + '__'
    target = dt.VARIABLE.sub(protect, target)
    target = re.sub(r'/[A-Za-z][\w-]*(?: [A-Za-z0-9_-]+)*', protect, target)
    for name, translation in sorted(SPEC['names'].items(), key=lambda p: -len(p[0])):
        if contains(source, name):
            target = target.replace(translation, '__SAPPHIRE_DONE_NAME__')
            for old in sorted({name, *ALIASES.get(name, [])}, key=len, reverse=True):
                if old and old != translation:
                    target = replace_literal(target, old, translation)
            target = target.replace('__SAPPHIRE_DONE_NAME__', translation)
    for phrase, translation in SPEC['phrases'].items():
        if contains(source, phrase):
            target = replace_literal(target, phrase, translation)
    for term, rule in SPEC['terms'].items():
        if not contains(source, term):
            continue
        aliases = list(rule['old'])
        # Crop descriptions mention both the ordinary crop and the rare material.
        if term == 'Squash' and contains(source, 'Pumpkins'): aliases.remove('南瓜')
        if term == 'Helianthus' and contains(source, 'Sunflower'): aliases.remove('向日葵')
        if term == 'Catacomb' and contains(source, 'Crypts'): aliases.remove('墓穴')
        # A single substitution avoids rewriting substrings of a newly inserted term.
        pattern = '|'.join(('(?<![A-Za-z])' + re.escape(a) + '(?![A-Za-z])') if a.isascii() else re.escape(a)
                           for a in sorted(aliases, key=len, reverse=True))
        target = re.sub(pattern, lambda _: rule['translation'], target, flags=re.I)
    if contains(source, 'Pickonimbus'):
        target = target.replace('云霄镐', '光镐')
        target = re.sub(r'光镐\s+(?=\d|\{)', '光镐', target)
    if contains(source, 'Gummy Polar Bear'):
        target = target.replace('软糖北极熊', '北极熊软糖')
    for i, command in enumerate(commands):
        target = target.replace('__SAPPHIRE_COMMAND_' + str(i) + '__', command)
    return target


def paragraph(source, target, styles=None):
    return {'source': source, 'translation': target, 'styleMappings': styles or {}}


def magic_ball(data):
    old = data['loreParagraphs']
    # Keep attribute labels and final bag/rarity lines. Split every list entry.
    result = [p for p in old if len(p['source']) == 1 and p['source'][0].startswith(
        ('Farming Fortune:', 'Farming Wisdom:', 'Accessory Power:'))]
    result.append(paragraph(['Every SkyBlock Season, the Magic {{N0}}',
                             'Ball will grant one of the following', 'bonuses:'],
        '每个 SkyBlock 季节,魔法 {{N0}} 球会提供以下一种加成:',
        {'SkyBlock Season': 'SkyBlock 季节', 'Magic {{N0}} Ball': '魔法 {{N0}} 球'}))
    sayings = {'✦ It is certain.': '✦ 肯定如此.', '✧ Reply hazy, try again.': '✧ 答案模糊,再试一次.',
               "✧ Don't count on it.": '✧ 别指望了.', '✧ As I see it, yes.': '✧ 依我看,是的.',
               '✧ My sources say no.': '✧ 我的消息说不行.'}
    stats = {'Farming Fortune': '农业幸运', 'Farming Wisdom': '农业智慧',
             'Foraging Fortune': '采伐幸运', 'Foraging Wisdom': '采伐智慧',
             'Mining Fortune': '采矿幸运', 'Mining Wisdom': '采矿智慧',
             'Fishing Speed': '钓鱼速度', 'Fishing Wisdom': '钓鱼智慧',
             'Strength': '力量', 'Combat Wisdom': '战斗智慧'}
    for line in data['_original_lore']:
        if line in sayings:
            result.append(paragraph([line], sayings[line]))
        elif line.startswith('    '):
            source = normalize(line)[0]
            stat = next(s for s in stats if line.endswith(s))
            result.append(paragraph([source], source.replace(stat, stats[stat]), {stat: stats[stat]}))
    result += [p for p in old if p['source'] in [['Works while in Accessory Bag!'], ['EPIC ACCESSORY']]]
    data['loreParagraphs'] = result


def transform(original):
    d = copy.deepcopy(original)
    d['name'] = SPEC['names'].get(d['_original_name'], rewrite(d['_original_name'], d['name']))
    d['lore'] = {s: rewrite(s, t) for s, t in d.get('lore', {}).items()}
    d['styleMappings'] = {s: rewrite(s, t) for s, t in d.get('styleMappings', {}).items()}
    for p in d.get('loreParagraphs', []):
        p['translation'] = rewrite(' '.join(p['source']), p['translation'])
        if 'styleMappings' in p:
            p['styleMappings'] = {s: rewrite(s, t) for s, t in p['styleMappings'].items()}
    if d['id'] == 'MAGIC_8_BALL':
        magic_ball(d)
    if d['id'] == 'THE_ART_OF_PEACE':
        d['loreParagraphs'] = [p for p in d['loreParagraphs'] if not any(
            'Stop quoting' in s or 'Moon Tzu' in s for s in p['source'])]
        d['loreParagraphs'] += [paragraph(['Stop quoting my cousin.'], '别特么动不动引用我哥的话了。'),
                                 paragraph([' - Moon Tzu'], ' —— 孙子弟弟')]
    if d['id'] == 'GYROKINETIC_WAND':
        for p in d['loreParagraphs']:
            if any('Aligned' in s for s in p['source']):
                p['translation'] = p['translation'].replace('Aligned', '铁索连环')
                p['styleMappings'] = {s: t.replace('Aligned', '铁索连环') for s, t in p['styleMappings'].items()}
        combined = next(p for p in d['loreParagraphs'] if p['source'][0] == '||| Aligned')
        if len(combined['source']) > 1:
            index = d['loreParagraphs'].index(combined)
            d['loreParagraphs'][index:index+1] = [paragraph(['||| Aligned'], '||| 铁索连环', {'Aligned': '铁索连环'}),
                paragraph(combined['source'][1:], '将受到的伤害拆分,并在 {{N0}}s 内分摊.',
                          {'incoming damage': '受到的伤害'})]
    for p in d.get('loreParagraphs', []):
        override = SPEC.get('paragraph_overrides', {}).get(d['id'], {}).get(' '.join(p['source']))
        if override: p.update(copy.deepcopy(override))
    if d != original:
        d['_reviewed_revision'] = SPEC['revision']
    return d


def save_backed(path, value):
    if path.exists() and dt.read(path) == value: return
    backup = STATE / 'backup' / path.relative_to(BASE)
    if path.exists() and not backup.exists():
        backup.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, backup)
    dt.write(path, value)


def main():
    # The last built artifact offers a fast inventory; affected files are reread
    # from each root before edits so user changes are never replaced wholesale.
    with zipfile.ZipFile(BASE / 'build/libs/sapphire-1.0.0.jar') as z:
        values = {Path(n).name: json.loads(z.read(n)) for n in z.namelist()
                  if n.startswith('defaults/translations/items/') and n.endswith('.json')}
    for d in values.values():
        ALIASES.setdefault(d['_original_name'], set()).add(d['name'])
    changes = []
    for filename, baseline in values.items():
        if transform(baseline) == baseline: continue
        for relative in ROOTS:
            path = BASE / relative / 'translations/items' / filename
            old = dt.read(path)
            new = transform(old)
            if new == old: continue
            errors = dt.check_item_extensions(new, filename)
            for s, t in new.get('lore', {}).items(): errors += dt.check_text(s, t, filename, bool(dt.VARIABLE.search(s)))
            if errors: raise ValueError(errors)
            changes.append((path, new))
    # Validate all planned writes before applying any of them.
    for path, value in changes: save_backed(path, value)
    for relative in ROOTS:
        for path in (BASE / relative / 'translations').glob('*.json'):
            old = dt.read(path)
            if all(isinstance(t, str) for t in old.values()):
                new = {s: rewrite(s, t) for s, t in old.items()}
                if new != old: save_backed(path, new)
    policy_path = BASE / 'tools/translation_policy.json'
    policy = dt.read(policy_path)
    policy['version'] = SPEC['revision']
    policy['exact_names'].update(SPEC['names'])
    policy['terms'].update({s: r['translation'] for s, r in SPEC['terms'].items()})
    policy['terms'].update({'Catacombs': '地牢', 'Zealots': '末地战士'})
    save_backed(policy_path, policy)
    reviewed_path = BASE / 'tools/reviewed_item_names.json'
    reviewed = dt.read(reviewed_path)
    reviewed.update({'PICKONIMBUS': '光镐2000', 'CRUXMOTION': '核心精魄'})
    save_backed(reviewed_path, reviewed)
    previous = STATE / 'changed-items.json'
    previous_ids = dt.read(previous)['itemIds'] if previous.exists() else []
    changed_ids = sorted(set(previous_ids) | {d['id'] for _, d in changes})
    dt.write(STATE / 'changed-items.json', {'revision': SPEC['revision'], 'itemIds': changed_ids,
        'changedFiles': len(changes), 'roots': list(ROOTS)})
    print(json.dumps({'changedItems': len(changed_ids), 'changedFiles': len(changes)}, ensure_ascii=False), flush=True)


if __name__ == '__main__': main()
