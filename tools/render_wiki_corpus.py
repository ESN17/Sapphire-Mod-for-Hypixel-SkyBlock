"""Expand cached pages whose UI/dialogue depends on template inheritance or Lua."""
import json
from collect_wiki_corpus import Client, OUT, write


def main():
    entries = json.loads((OUT / 'needs-render.json').read_text(encoding='utf-8'))['entries']
    titles = {r['title'] for r in entries}
    titles.update(['SkyBlock Menu/UI', 'Pets/UI', 'Auction House/UI', 'Bazaar/UI'])
    pages = {}
    for path in (OUT / 'pages').glob('*.json'):
        page = json.loads(path.read_text(encoding='utf-8'))
        if page['title'] in titles:
            pages[page['title']] = page
    client = Client()
    errors, done = [], []
    for title, page in sorted(pages.items()):
        try:
            data = client.get(action='parse', oldid=page['revision'], prop='text|revid|categories')
            write(OUT / 'rendered' / (str(page['pageid']) + '.json'), data)
            done.append(title)
        except Exception as exc:
            errors.append(dict(title=title, error=str(exc)))
        print('Expanded', len(done), '/', len(pages), title, flush=True)
        write(OUT / 'render-report.json', dict(requested=len(pages), completed=len(done), pages=done, errors=errors))


if __name__ == '__main__':
    main()
