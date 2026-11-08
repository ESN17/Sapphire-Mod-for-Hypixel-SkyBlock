"""Extract named mobs and fishing notices from the already cached public Wiki.

No network at runtime. Keeps page/revision attribution, excludes NPC templates,
and writes a review catalog rather than modifying existing translations.
"""
import json
import html
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'translation-review/wiki-corpus-20261023/pages'
OUT = ROOT / 'translation-review/entities-20261030'


def main():
    mobs, sea, notices = {}, {}, {}
    for path in SOURCE.glob('*.json'):
        page = json.loads(path.read_text(encoding='utf-8'))
        raw = page['wikitext']
        attribution = {k: page[k] for k in ('title', 'url', 'revision', 'revisionUrl', 'license')}
        for match in re.finditer(r'\{\{(?:Mob|MobSprite)\s*\|([^{}|\n]+)', raw, re.I):
            name = match[1].split(';')[0].strip()
            if name and len(name) < 90 and not any(c in name for c in '<>[]'):
                mobs.setdefault(name, attribution)
        if re.search(r'\{\{Infobox/(?:Mob|Sea creature|Boss)\s*[|\n]', raw, re.I):
            mobs.setdefault(page['title'], attribution)
        if re.search(r'\{\{Infobox/Sea creature\s*[|\n]', raw, re.I):
            sea[page['title']] = attribution
            for match in re.finditer(r'\{\{Dialogue\s*\|([^{}]+)\}\}', raw, re.I):
                original = match[1].strip()
                if '\n' in original or '|' in original or len(original) > 500:
                    continue
                plain = re.sub(r'[&§][0-9a-fk-or]', '', original, flags=re.I)
                if plain.startswith('[NPC]'):
                    continue
                sample=re.sub(r'&([0-9a-fk-or])',r'§\1',html.unescape(original),flags=re.I)
                notices.setdefault(plain, dict(sampleRaw=sample, **attribution))
    OUT.mkdir(parents=True, exist_ok=True)
    result = dict(source='cached Wiki pages collected 2026-10-23; public lists checked 2026-10-30',
                  mobs=dict(sorted(mobs.items())), seaCreatures=dict(sorted(sea.items())), notices=notices)
    (OUT / 'catalog.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'{len(mobs)} mob references; {len(sea)} sea creature pages; {len(notices)} fishing notices')


if __name__ == '__main__':
    main()
