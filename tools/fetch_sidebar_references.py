"""Download only public Wiki references for the sidebar translation audit."""
import urllib.request
import urllib.error
import urllib.parse
import html
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'translation-state/locations/reference'
ROOT.mkdir(parents=True, exist_ok=True)
pages = ['Category:Locations', 'SkyBlock_Time', 'Slayer', 'Bits', 'Copper', 'Dungeons', "Jacob's_Farming_Contest", 'Events']
for page in pages:
    url = 'https://hypixelskyblock.minecraft.wiki/w/' + urllib.parse.quote(page, safe=':_')
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'SapphireTranslationResearch/1.0'}), timeout=30) as response:
            data = response.read()
        path = ROOT / (page.replace(':', '_') + '.html')
        path.write_bytes(data)
        print(page, len(data))
    except urllib.error.HTTPError as error:
        print(page, 'HTTP', error.code)

category = (ROOT / 'Category_Locations.html').read_text(encoding='utf-8')
next_page = re.search(r'<a href="([^"]+)"[^>]*>next page</a>', category)
if next_page:
    url = urllib.parse.urljoin('https://hypixelskyblock.minecraft.wiki', html.unescape(next_page.group(1)))
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'SapphireTranslationResearch/1.0'}), timeout=30) as response:
        data = response.read()
    (ROOT / 'Category_Locations_2.html').write_bytes(data)
    print('Category page 2', len(data))
