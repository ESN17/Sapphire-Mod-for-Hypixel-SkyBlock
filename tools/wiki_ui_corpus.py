"""Extract in-game menu text from Hypixel SkyBlock wiki UI pages.

The wiki stores menus as {{UI|Title|r, c=Item, category, &aTitle, &7lore/line//...}}
so the exact in-game strings (including § colours) can be rebuilt without
guessing. Output feeds the gui-menus / tooltip-paragraph dictionaries.
"""
from __future__ import annotations
import argparse
import json
import re
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'translation-review/wiki-ui'
API = 'https://hypixelskyblock.minecraft.wiki/api.php'
HEADERS = {'User-Agent': 'Sapphire-Translation/1.0 (maintainer tool)'}
COLOUR = re.compile(r'&([0-9a-fk-or])', re.I)
DEFAULT_PAGES = ('Skills/UI', 'Template:Heart of the Forest/UI', 'SkyBlock Menu/UI')
SKILL_PAGES = ('Farming/Skill UI', 'Mining/Skill UI', 'Combat/Skill UI', 'Foraging/Skill UI',
               'Fishing/Skill UI', 'Enchanting/Skill UI', 'Alchemy/Skill UI', 'Taming/Skill UI',
               'Hunting/Skill UI', 'Runecrafting/Skill UI', 'Social/Skill UI', 'Dungeoneering/Skill UI')


def wikitext(page: str) -> str:
    url = API + '?' + urllib.parse.urlencode({'action': 'parse', 'page': page, 'prop': 'wikitext',
                                              'format': 'json', 'formatversion': '2'})
    request = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(request, timeout=60) as response:
        data = json.load(response)
    if 'error' in data:
        raise SystemExit(f"{page}: {data['error'].get('info')}")
    return data['parse']['wikitext']


def cast(text: str) -> str:
    text = text.replace("\\'", "'").replace('\\,', ',').replace('\\/', '/').replace('\\"', '"')
    return COLOUR.sub(lambda m: '§' + m.group(1).lower(), text)


def slots(page: str):
    for line in wikitext(page).splitlines():
        line = line.strip()
        if not line.startswith('|') or '=' not in line:
            continue
        left, _, value = line[1:].partition('=')
        if not re.fullmatch(r'\d+,\s*\d+', left.strip()):
            continue
        parts = value.split(',', 2)
        if len(parts) < 3:
            continue
        item, category, rest = parts[0].strip(), parts[1].strip(), parts[2]
        title, _, lore = rest.partition(',')
        lines = [cast(part).strip() for part in lore.split('/')]
        yield {'slot': left.strip(), 'item': item, 'category': category,
               'title': cast(title).strip(), 'lines': lines}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--page', action='append', help='extra wiki UI page')
    parser.add_argument('--skills', action='store_true', help='include every skill UI page')
    args = parser.parse_args(argv)
    pages = list(DEFAULT_PAGES) + (list(SKILL_PAGES) if args.skills else []) + (args.page or [])
    OUT.mkdir(parents=True, exist_ok=True)
    corpus = {}
    for page in pages:
        try:
            entries = list(slots(page))
        except SystemExit as error:
            print('skip', error)
            continue
        corpus[page] = entries
        print(page, len(entries), 'slots')
    (OUT / 'corpus.json').write_text(json.dumps(corpus, ensure_ascii=False, indent=1), encoding='utf8')
    unique = sorted({line for entries in corpus.values() for entry in entries for line in entry['lines'] if line.strip()})
    (OUT / 'lines.json').write_text(json.dumps(unique, ensure_ascii=False, indent=1), encoding='utf8')
    print('unique lines:', len(unique))


if __name__ == '__main__':
    main()
