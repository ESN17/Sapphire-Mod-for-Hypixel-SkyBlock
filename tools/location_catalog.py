"""Extract location names from the saved community Wiki catalogue (no network)."""
import html
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REFERENCE = ROOT / 'translation-state/locations/reference/Locations.html'
URL = 'https://hypixelskyblock.minecraft.wiki/w/Locations'

def catalog():
    source = REFERENCE.read_text(encoding='utf-8').split('<h2 id="Trivia"')[0]
    rows = {}
    section = ''
    for token in re.finditer(r'<h[23]\b[^>]*>.*?</h[23]>|<li><a\b[^>]*>.*?</a>|<a\b[^>]*>.*?</a>', source, re.S):
        raw = token.group()
        text = html.unescape(re.sub('<[^>]+>', '', raw)).strip()
        if raw.startswith('<h2'):
            section = text
        if not section or section == 'Contents':
            continue
        if raw.startswith('<h3'):
            name = text
            href = '/w/' + name.replace(' ', '_')
        elif (raw.startswith('<a') and text.startswith(('\ue067', '\ue020'))) or (raw.startswith('<li>') and section in ('Unreleased Locations', 'Removed Locations')):
            name = text[1:].strip() if text.startswith(('\ue067', '\ue020')) else text
            link = re.search(r'href="([^"]+)"', raw)
            if not link:
                continue
            href = html.unescape(link.group(1))
            if href == '/w/Rift_Time':
                continue  # Stat icon is shared with Rift locations.
        else:
            continue
        status = 'unreleased' if section == 'Unreleased Locations' else 'removed' if section == 'Removed Locations' else 'current'
        row = rows.setdefault(name, {'name': name, 'urls': [], 'statuses': []})
        full = 'https://hypixelskyblock.minecraft.wiki' + href
        if full not in row['urls']: row['urls'].append(full)
        if status not in row['statuses']: row['statuses'].append(status)
    return sorted(rows.values(), key=lambda r: r['name'].casefold())

def category_pages():
    rows = {}
    for path in REFERENCE.parent.glob('Category_Locations*.html'):
        section = path.read_text(encoding='utf-8').split('id="mw-pages"')[-1].split('printfooter')[0]
        for url, text in re.findall(r'<li[^>]*>\s*<a href="([^"]+)"[^>]*>(.*?)</a>', section, re.S):
            rows[html.unescape(re.sub('<[^>]+>', '', text))] = html.unescape(url)
    return rows

if __name__ == '__main__':
    rows = catalog()
    path = ROOT / 'translation-state/locations/catalog.json'
    path.write_text(json.dumps({'source': URL, 'accessed': '2026-10-17', 'names': rows}, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    path.with_name('category-pages.json').write_text(json.dumps(category_pages(),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Distinct display names:', len(rows))
    print('\n'.join(r['name'] for r in rows))
