"""Cache public SkyBlock Wiki sources for translation research, without importing them.

Python dependency for the separate extractor: mwparserfromhell==0.7.2.
Requests are serial, cached, robot-checked, throttled, and resumable.
"""
import argparse
import hashlib
import http.client
import json
import re
import time
import urllib.error
import urllib.parse
import urllib.request
import urllib.robotparser
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOST = 'https://hypixelskyblock.minecraft.wiki'
UA = 'SapphireTextResearch/0.1 (user-requested local translation research; no login)'
OUT = ROOT / 'translation-review/wiki-corpus-20261023'
CACHE = ROOT / 'build/wiki-corpus-20261023'
LICENSE = 'CC BY-NC-SA 3.0'
CATEGORIES = ['Non-player characters', 'Dialogue Subpages', 'Pages with UIs',
              'UI Subpages', 'Collection UI', 'Interactive UI', 'SkyBlock Menu',
              'Events', 'Quests', 'Locations']
EMBEDDED = ['Dialogue', 'ZoneText', 'UI', 'UIPage']


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


class Client:
    def __init__(self, offline=False):
        self.offline = offline
        self.last = 0
        self.robot = urllib.robotparser.RobotFileParser()
        self.robot.parse((ROOT / 'build/wiki-probe-20261023/robots.txt').read_text(encoding='utf-8').splitlines())

    def get(self, **params):
        params = dict(format='json', formatversion=2, maxlag=5, **params)
        query = urllib.parse.urlencode(sorted(params.items()))
        url = HOST + '/api.php?' + query
        if not self.robot.can_fetch(UA, url):
            raise ValueError('robots.txt disallows requested API URL')
        path = CACHE / 'requests' / (hashlib.sha256(query.encode()).hexdigest() + '.json')
        if path.exists():
            return json.loads(path.read_text(encoding='utf-8'))
        if self.offline:
            raise RuntimeError('Uncached request: ' + query)
        for attempt in range(4):
            time.sleep(max(0, 1.5 - (time.monotonic() - self.last)))
            try:
                req = urllib.request.Request(url, headers={'User-Agent': UA, 'Accept': 'application/json'})
                with urllib.request.urlopen(req, timeout=45) as response:
                    raw = response.read(32_000_001)
                if len(raw) > 32_000_000:
                    raise ValueError('Response exceeds 32 MB limit')
                data = json.loads(raw)
                if 'error' in data:
                    raise RuntimeError(str(data['error']))
                write(path, data)
                return data
            except urllib.error.HTTPError as exc:
                # Do not retry access denials or try alternate identities.
                if exc.code not in (429, 500, 502, 503, 504) or attempt == 3:
                    raise
                time.sleep(min(60, max(5 * 2**attempt, int(exc.headers.get('Retry-After', '0')))))
            except (OSError, RuntimeError, http.client.IncompleteRead) as exc:
                if attempt == 3:
                    raise
                print('Retry:', str(exc)[:160], flush=True)
                time.sleep(5 * 2**attempt)
            finally:
                self.last = time.monotonic()

    def listing(self, field, **params):
        results = []
        continuation = {}
        while True:
            data = self.get(action='query', list=field, **params, **continuation)
            results.extend(data.get('query', {}).get(field, []))
            continuation = data.get('continue')
            if not continuation:
                return results


def discover(client):
    pages, categories, errors = {}, {}, []

    def add(row, origin):
        if row['ns'] != 0:
            return
        entry = pages.setdefault(row['title'], dict(title=row['title'], pageid=row['pageid'], origins=[]))
        if origin not in entry['origins']:
            entry['origins'].append(origin)

    queue = ['Category:' + c for c in CATEGORIES]
    while queue:
        category = queue.pop(0)
        if category in categories:
            continue
        try:
            rows = client.listing('categorymembers', cmtitle=category, cmlimit=500)
            categories[category] = rows
            for row in rows:
                add(row, category)
                if row['ns'] == 14 and row['title'] not in categories:
                    queue.append(row['title'])
            print('Indexed', category, len(rows), 'total pages', len(pages), flush=True)
        except Exception as exc:
            errors.append(dict(stage='category', title=category, error=str(exc)))
            categories[category] = []
    for template in EMBEDDED:
        try:
            rows = client.listing('embeddedin', eititle='Template:' + template, einamespace=0, eilimit=500)
            for row in rows:
                add(row, 'Template:' + template)
            print('Indexed Template:' + template, len(rows), 'total pages', len(pages), flush=True)
        except Exception as exc:
            errors.append(dict(stage='embeddedin', title=template, error=str(exc)))
    result = dict(pages=sorted(pages.values(), key=lambda p: p['title']), categories=categories, errors=errors)
    write(OUT / 'index.json', result)
    return result


def references(source, title):
    """Discover UI/dialogue transclusions not already present in categories."""
    values = re.findall(r'\{\{\s*(?:UIPage\s*\|\s*)?([^{}|\n]+)', source, flags=re.I)
    found = set()
    for value in values:
        value = value.strip().removeprefix(':')
        if value.startswith('/'):
            value = title + value
        if ':' in value or len(value) > 200:
            continue
        if re.search(r'/(?:UI|Dialogue)(?:/|$)', value, re.I):
            found.add(value.replace('_', ' '))
    return found


def collect(client, index):
    sources = OUT / 'pages'
    sources.mkdir(parents=True, exist_ok=True)
    pages = {p['title']: p for p in index['pages']}
    pending = sorted(pages)
    requested, fetched, missing, errors = set(), {}, [], list(index['errors'])
    while pending:
        batch, pending = pending[:25], pending[25:]
        batch = [t for t in batch if t not in requested]
        if not batch:
            continue
        requested.update(batch)
        try:
            data = client.get(action='query', prop='revisions', titles='|'.join(batch),
                              rvprop='content|ids|timestamp', rvslots='main')
            for page in data['query']['pages']:
                title = page['title']
                if 'missing' in page or not page.get('revisions'):
                    missing.append(title)
                    continue
                rev = page['revisions'][0]
                source = rev['slots']['main'].get('content', '')
                record = dict(title=title, pageid=page['pageid'], revision=rev['revid'], timestamp=rev['timestamp'],
                              url=HOST + '/w/' + urllib.parse.quote(title.replace(' ', '_')),
                              revisionUrl=HOST + '/?oldid=' + str(rev['revid']), license=LICENSE,
                              origins=pages.get(title, {}).get('origins', []), wikitext=source)
                write(sources / (str(page['pageid']) + '.json'), record)
                fetched[title] = {k: v for k, v in record.items() if k != 'wikitext'}
                for target in references(source, title):
                    if target not in pages and target not in requested:
                        pages[target] = dict(title=target, origins=['transcluded by ' + title])
                        pending.append(target)
            print('Fetched', len(fetched), 'pages;', len(pending), 'pending;', len(missing), 'missing', flush=True)
        except Exception as exc:
            errors.append(dict(stage='fetch', titles=batch, error=str(exc)))
            print('Failed batch:', str(exc)[:180], flush=True)
        write(OUT / 'collection-report.json', dict(collectedAt=datetime.now(timezone.utc).isoformat(),
              discovered=len(index['pages']), requested=len(requested), fetched=len(fetched),
              pending=len(pending), missing=sorted(set(missing)), errors=errors,
              pages=sorted(fetched.values(), key=lambda p: p['title'])))
    return len(fetched)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--offline', action='store_true')
    parser.add_argument('--refresh-index', action='store_true')
    args = parser.parse_args()
    client = Client(args.offline)
    indexfile = OUT / 'index.json'
    index = json.loads(indexfile.read_text(encoding='utf-8')) if indexfile.exists() and not args.refresh_index else discover(client)
    print('Completed:', collect(client, index), 'pages', flush=True)


if __name__ == '__main__':
    main()
