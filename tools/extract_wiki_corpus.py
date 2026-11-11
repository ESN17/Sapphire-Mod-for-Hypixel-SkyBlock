"""Extract game text from cached Wiki sources; retain unresolved templates for review."""
import collections
import hashlib
import html
import json
import re
import sys
from pathlib import Path

from collect_wiki_corpus import ROOT, OUT, LICENSE, write
from prepare_npc_capture import key as npc_key
from probe_wiki_text import extract as extract_rendered_dialogue
from lxml import html as lhtml
sys.path.insert(0, str(ROOT / 'build/wiki-tools-deps'))
import mwparserfromhell as mw

COLORS = re.compile(r'[§&]([0-9a-fk-or])', re.I)


def plain(value):
    return COLORS.sub('', value)


def readable(value):
    """Resolve only transparent markup. Unknown templates remain verbatim."""
    parts = []
    for node in mw.parse(str(value)).nodes:
        kind = type(node).__name__
        if kind == 'Comment':
            continue
        if kind == 'Wikilink':
            parts.append(readable(node.text if node.text is not None else node.title))
        elif kind == 'Template':
            name = str(node.name).strip().lower()
            if name in ('rmt', 'rm', 'nowrap') and node.has(1):
                parts.append(readable(node.get(1).value))
            elif name in ('color', 'colour') and node.has(2):
                parts.append(readable(node.get(2).value))
            elif name in ('!', '='):
                parts.append(name)
            elif name == 'dashline':
                parts.append('--------------------------------')
            else:
                parts.append(str(node))
        elif kind == 'Tag':
            tag = str(node.tag).lower()
            if tag == 'br':
                parts.append('\n')
            elif tag not in ('ref', 'references', 'noinclude'):
                parts.append(readable(node.contents))
        elif kind == 'ExternalLink':
            parts.append(readable(node.title) if node.title else str(node.url))
        else:
            parts.append(str(node))
    return html.unescape(''.join(parts)).replace("'''", '').replace("''", '')


def normalized(value):
    return ' '.join(plain(value).split())


def template_name(template):
    return str(template.name).strip().lower().removeprefix('template:')


def fields(value, delimiter, maxsplit=0):
    # Protect separators inside nested templates and wikilinks as well as \, and \/.
    parts, current, braces, links, i = [], [], 0, 0, 0
    while i < len(value):
        pair = value[i:i+2]
        if value[i] == '\\' and i + 1 < len(value):
            current.append(pair); i += 2; continue
        if pair in ('{{', '}}', '[[', ']]'):
            if pair == '{{': braces += 1
            elif pair == '}}': braces = max(0, braces - 1)
            elif pair == '[[': links += 1
            else: links = max(0, links - 1)
            current.append(pair); i += 2; continue
        if value[i] == delimiter and not braces and not links and (not maxsplit or len(parts) < maxsplit):
            parts.append(''.join(current).strip()); current = []
        else:
            current.append(value[i])
        i += 1
    parts.append(''.join(current).strip())
    return parts


def unescape(value):
    return re.sub(r'\\([,/&])', r'\1', value)


def extract(page):
    text = page['wikitext']
    code = mw.parse(text)
    # HTML comments sometimes contain obsolete UI layouts. Remove them before walking templates.
    for comment in code.filter_comments(recursive=True):
        code.remove(comment)
    rows, menus, unresolved = [], [], []
    meta = {k: page[k] for k in ('title', 'pageid', 'revision', 'url', 'revisionUrl', 'license')}
    npc_page = 'Category:Non-player characters' in page['origins'] or bool(re.search(r'\{\{Infobox/NPC', text, re.I))

    def add(raw, kind, **context):
        rendered = readable(raw).strip()
        centered = rendered.startswith('$[c]')
        rendered = re.sub(r'^\$\[c\]', '', rendered)
        source = normalized(rendered)
        if not source or re.fullmatch(r'[\s─━\-=]+', source):
            return
        row = dict(kind=kind, source=source, sampleRaw=COLORS.sub(r'§\1', rendered), rawWiki=str(raw),
                   verifiedIngame=False, centered=centered, **meta, **context)
        match = re.match(r'^\[NPC\]\s+([^:]+):\s*(.*)$', source)
        if match:
            row.update(kind='npc', npc=match[1].strip(), source=match[2].strip(), fullSource=source)
            row['sampleRaw'] = re.sub(r'^.*?:\s*', '', row['sampleRaw'], count=1)
        if source.startswith('Select an option:'):
            row['kind'] = 'dialogue-option'
        if '{{' in rendered or '}}' in rendered or '%inherit%' in rendered:
            row['needsRender'] = True
            unresolved.append(row)
        else:
            rows.append(row)

    for template in code.filter_templates(recursive=True):
        name = template_name(template)
        raw = str(template)
        pos = text.find(raw)
        headings = re.findall(r'^={2,4}\s*(.*?)\s*={2,4}\s*$', text[:max(pos, 0)], re.M)
        section = headings[-1] if headings else ''
        if name == 'dialogue':
            for param in template.params:
                if str(param.name).strip().isdigit():
                    for line in readable(param.value).splitlines():
                        add(line, 'dialogue-other' if npc_page else 'system-notice-candidate', section=section)
        elif name == 'zonetext':
            for param in template.params:
                if str(param.name).strip().isdigit():
                    add(param.value, 'system-notice-candidate', section=section, template='ZoneText')
        elif name in ('rmt', 'rm') and re.search(r'chat|message|notification', section, re.I):
            if template.has(1):
                add(template.get(1).value, 'system-notice-candidate', section=section, template=name)
        elif name == 'ui':
            title = str(template.get(1).value).strip() if template.has(1) else ''
            menu_id = str(template.get('id').value).strip() if template.has('id') else ''
            menu = dict(**meta, menuTitle=normalized(readable(title)), menuId=menu_id, slots=[])
            add(title, 'gui-title', menuId=menu_id)
            for param in template.params:
                slot = str(param.name).strip()
                value = str(param.value).strip()
                if re.fullmatch(r'\d+\s*,\s*\d+|row\d+|col\d+', slot):
                    chunks = fields(value, ',')
                    menu['slots'].append(dict(slot=slot, rawWiki=value))
                    if len(chunks) >= 3 and not re.match(r'^none(?:;|$)', chunks[2], re.I):
                        add(unescape(chunks[2]), 'tooltip-name', menu=menu['menuTitle'], menuId=menu_id, slot=slot)
                    if len(chunks) >= 4:
                        # Keep paragraphs together, not arbitrary wiki line wrapping.
                        paragraphs, group = [], []
                        for line in fields(chunks[3], '/'):
                            if normalized(readable(line)):
                                group.append(unescape(line))
                            elif group:
                                paragraphs.append(' '.join(group)); group = []
                        if group: paragraphs.append(' '.join(group))
                        for paragraph in paragraphs:
                            add(paragraph, 'tooltip-paragraph', menu=menu['menuTitle'], menuId=menu_id, slot=slot)
                elif slot in ('goback', 'title'):
                    add(unescape(value), 'tooltip-name', menu=menu['menuTitle'], menuId=menu_id, slot=slot)
            menus.append(menu)
    missing = [str(t) for t in code.filter_templates() if template_name(t) in ('more info needed', 'stub', 'outdated')]
    return rows, menus, unresolved, missing


def main():
    corpus = json.loads((ROOT / 'src/main/resources/defaults/translations/community-sapphire.json').read_text(encoding='utf-8'))
    known = {npc_key(r) for r in corpus['entries'] if r['scope'] == 'npc'}
    entries, excluded, unresolved, menus, gaps, errors = {}, [], [], [], [], []
    raw_template_references=[]
    occurrences = collections.Counter()
    rendered_pages = OUT / 'rendered'
    for path in sorted((OUT / 'pages').glob('*.json')):
        page = json.loads(path.read_text(encoding='utf-8'))
        try:
            rows, ui, pending, missing = extract(page)
            raw_template_references.extend(pending)
            rendered_path = rendered_pages / path.name
            if rendered_path.exists():
                pending=[]
                rendered = json.loads(rendered_path.read_text(encoding='utf-8'))
                rendered_rows, _, _ = extract_rendered_dialogue(rendered)
                for row in rendered_rows:
                    row['title'] = page['title']; row['pageid'] = page['pageid']
                    row['source']=' '.join(row['source'].split())
                    if row['kind'] == 'npc':
                        row['sampleRaw'] = re.sub(r'^.*?:\s*', '', row['sampleRaw'], count=1)
                rows.extend(rendered_rows)
                root = lhtml.fromstring(rendered['parse']['text'])
                for node in root.iter():
                    # Reuse the tested UI parser on fully expanded display values.
                    title = node.get('data-minetip-title') or ''
                    lore = node.get('data-minetip-text') or ''
                    if title or lore:
                        # Escape separators already resolved by the Wiki. Slash escapes are retained.
                        synthetic = '{{UI|Rendered UI|1, 1=Icon, none, ' + title.replace(',', r'\,') + ', ' + lore.replace(',', r'\,') + '}}'
                        display_page = dict(page, wikitext=synthetic)
                        display_rows, _, display_pending, _ = extract(display_page)
                        rows.extend(r for r in display_rows if r['kind'] != 'gui-title')
                        pending.extend(display_pending)
                    if 'mcui-header' in (node.get('class') or '').split():
                        header = normalized(node.text_content())
                        if header:
                            rows.append(dict(kind='gui-title', source=header, sampleRaw=header, verifiedIngame=False,
                                             **{k: page[k] for k in ('title','pageid','revision','url','revisionUrl','license')}))
            menus.extend(ui); unresolved.extend(pending)
            if missing: gaps.append(dict(page=page['title'], markers=missing))
            for row in rows:
                occurrences[row['kind']] += 1
                key = (row['kind'], row.get('npc', ''), row['source'])
                if row['kind'] == 'npc' and npc_key(row) in known:
                    excluded.append(row)
                    continue
                origin = {k: row[k] for k in ('title', 'revision', 'url', 'section', 'menu', 'slot') if k in row}
                if key in entries:
                    if origin not in entries[key]['occurrences']:
                        entries[key]['occurrences'].append(origin)
                else:
                    row['id'] = hashlib.sha256(json.dumps(key, ensure_ascii=False).encode()).hexdigest()[:24]
                    row['occurrences'] = [origin]
                    entries[key] = row
        except Exception as exc:
            errors.append(dict(page=page['title'], error=str(exc)))
    grouped = collections.defaultdict(list)
    for row in entries.values(): grouped[row['kind']].append(row)
    for kind, rows in grouped.items(): write(OUT / (kind + '.json'), dict(entries=rows))
    write(OUT / 'menu-layouts.json', dict(menus=menus))
    write(OUT / 'needs-render.json', dict(entries=unresolved))
    write(OUT / 'raw-template-references.json', dict(entries=raw_template_references))
    write(OUT / 'excluded-npc-dialogue.json', dict(entries=excluded))
    report = dict(uniqueCandidates={k: len(v) for k,v in grouped.items()}, occurrences=dict(occurrences),
                  menuLayouts=len(menus), excludedImportedNpcOccurrences=len(excluded), knownNpcBaseline=len(known),
                  needsRender=len(unresolved), wikiGapPages=gaps, errors=errors, license=LICENSE)
    write(OUT / 'extraction-report.json', report)
    print(json.dumps({k:v for k,v in report.items() if k != 'wikiGapPages'}, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    main()
