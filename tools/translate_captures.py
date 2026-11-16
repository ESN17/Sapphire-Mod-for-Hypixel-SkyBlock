"""Translate captured chat lines that no dictionary rule covers yet.

Lines are deduplicated against the current dictionaries first; numbers become
{{N0}} placeholders so the new rules stay dynamic. Third-party mod prefixes are
skipped because they belong to their own translations.
"""
import argparse
import collections
import json
import re
import sys
import unicodedata
import tempfile
from functools import lru_cache
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import bulk_translate as bulk
from deepseek_client import DeepSeek, ApiError

ROOT = Path(__file__).resolve().parents[1]
TR = ROOT / 'src/main/resources/defaults/translations'
SYSTEM = ROOT / 'src/main/resources/defaults/translations/system-messages.json'
SKIP_PREFIXES = ('Aether >> ','[Skyblocker', '[CustomScoreboard', '[NoFrills', '[SBA', '[NEU')
SYSTEM_PROMPT = '''你在翻译 Hypixel SkyBlock 的公开游戏文本。scope 为 chat 时翻译聊天提示，gui 是菜单标题，gui-button 是按钮，gui-tooltip 是悬浮说明。输入是待翻译文本。
输出严格 JSON: {"translations":[{"id":"...","text":"简体中文"}]}，每个 id 恰好一次。
规则：
- {{N0}}、{{N1}} 占位符原样保留，各出现一次，不能改成具体数字。
- 保留图标/符号、玩家名、服务器代号、命令；ASCII 标点风格保持一致。
- 术语：SkyBlock=空岛；Skill=技能；Farming=农业；Foraging=采伐；Hunting=狩猎；Shard=碎片；
  Tree Gift=树木礼物；Whispers=低语；Motes=裂隙尘埃；Rift=裂隙；Attribute=属性；
  Fairy Soul=仙女之魂；Sea Creature=海怪；Museum=博物馆；RNG=随机掉落。
- 语气简洁自然，符合游戏提示。'''


def plain(text):
    return re.sub(r'§[0-9a-fk-or]', '', text, flags=re.I).strip()


SLOT = re.compile(r'(?<!\{)(?:\{\{([A-Za-z][A-Za-z0-9_]*)\}\}|\{([A-Za-z][A-Za-z0-9_]*)\})(?!\})')
NUMBER = r'[+-]?(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\.[0-9]+)?%?'


@lru_cache(maxsize=1)
def stat_symbols():
    # Mirror Java's \p{Co} and \p{S}, including supplementary-plane icons.
    ranges = []
    start = None
    for n in range(sys.maxunicode + 2):
        category = unicodedata.category(chr(n)) if n <= sys.maxunicode else ''
        if category == 'Co' or category.startswith('S'):
            if start is None:
                start = n
        elif start is not None:
            ranges.append(re.escape(chr(start)) + '-' + re.escape(chr(n - 1)))
            start = None
    return '[' + ''.join(ranges) + '☘⸕☯]'


def chat_pattern(source):
    parts = []
    end = 0
    for token in SLOT.finditer(source):
        name = token[1] or token[2]
        expr = NUMBER
        if name in ('name','text','prefix','suffix','player','location','event','mob','fireSaleItem','catalogItem'):
            expr = r'[^\r\n]{1,128}'
        elif name in ('market','marketBuy','marketSell','marketCount'):
            expr = r'[0-9]+(?:,[0-9]{3})*(?:\.[0-9]+)?[kKmMbBtT]?'
        elif name in ('roman','romanFrom','romanTo'):
            expr = r'(?:[IVXLCDM]{1,8}|[0-9]{1,3})'
        elif name in ('deathVictim','reviver','rankedPlayer'):
            expr = (r'(?:\[[A-Z][A-Z0-9_+]{0,24}\] )?' if name=='rankedPlayer' else '') + r'[A-Za-z0-9_]{1,16}'
        elif name == 'version':
            expr = r'[0-9][A-Za-z0-9._+-]{0,47}'
        elif name == 'time':
            expr = r'[0-9][0-9:., hmsd]{0,31}'
        elif name.startswith('icon'):
            expr = r'(?:[^\w\s]|_){1,8}'
        elif name.startswith('statIcon'):
            expr = NUMBER + r'[ \t]{0,2}' + stat_symbols() + '{1,4}'
        parts.extend((re.escape(source[end:token.start()]),expr))
        end = token.end()
    return re.compile(''.join(parts) + re.escape(source[end:]) + r'\Z')


def load_patterns(scope='chat'):
    patterns = []
    files = ['system-messages.json', 'death-messages.json', 'creature-messages.json']
    if scope in ('chat','gui'):
        files.append(scope+'.json')
    for name in files:
        path = TR / name
        if not path.exists():
            continue
        data = json.loads(path.read_text(encoding='utf8'))
        rows = data.get('rules') if isinstance(data, dict) and 'rules' in data else None
        if rows is None:
            for source in data:
                if isinstance(data[source], str):
                    patterns.append(re.compile('^' + re.escape(source) + '$'))
            continue
        for row in rows:
            if scope in row.get('scopes', ()):
                patterns.append(chat_pattern(row['source']))
    if scope == 'gui' and (TR/'gui-menus.json').is_file():
        menus = json.loads((TR/'gui-menus.json').read_text(encoding='utf8')).get('menus',[])
        for menu in menus:
            patterns.extend(chat_pattern(title) for title in menu.get('titles',{}))
    return patterns


def validate(source, text):
    if not isinstance(text, str) or not text.strip():
        return False
    if len(text) > 2048 or any(c in text for c in '\n\r\0§') or not re.search(r'[\u3400-\u9fff]', text):
        return False
    if collections.Counter(bulk.SLOT.findall(source)) != collections.Counter(bulk.SLOT.findall(text)):
        return False
    return True


def write_json(path, value):
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False, suffix='.tmp') as file:
        temp = Path(file.name)
    try:
        temp.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
        temp.replace(path)
    finally:
        temp.unlink(missing_ok=True)


SCOPES = ('chat', 'gui', 'gui-button', 'gui-tooltip', 'skyhanni')


def records(path):
    with path.open(encoding='utf8', errors='replace') as stream:
        for line in stream:
            raw = line.rstrip('\r\n')
            scope = 'chat'
            if path.suffix == '.jsonl':
                row = json.loads(raw) if raw else {}
                if not isinstance(row,dict):
                    continue
                scope, raw = row.get('scope'), row.get('source')
                if scope not in SCOPES or not isinstance(raw,str):
                    continue
            elif raw.startswith('[Sapphire:UI:'):
                header, sep, raw = raw.partition('] ')
                scope = header.removeprefix('[Sapphire:UI:')
                if not sep or scope not in ('gui','gui-button','gui-tooltip'):
                    continue
            text = plain(raw)
            if scope == 'chat' and text.startswith('[SkyHanni] '):
                scope = 'skyhanni'
            if text:
                yield scope,text


def item_name_index():
    names = collections.defaultdict(set)
    for path in (TR / 'items').glob('*.json'):
        item = json.loads(path.read_bytes())
        name = item.get('_original_name', '').strip()
        if name and item.get('name'):
            names[name.split()[0]].add(name)
    return {first: sorted(values, key=len, reverse=True) for first, values in names.items()}


def item_slots(text, index):
    # Inspect longest registered names first, retaining a separate candidate for
    # each occurrence. Distinct item slots cannot share one {name} binding.
    occupied = 0
    for token in re.finditer(r"(?<!\w)[\w'-]+", text):
        if token.start() < occupied:
            continue
        start = token.start()
        first = re.match(r"[\w'-]+", token.group())
        for name in index.get(first.group() if first else '', ()):
            end = start + len(name)
            if text.startswith(name, start) and (end == len(text) or not (text[end].isalnum() or text[end] == '_')):
                yield name, text[:start] + '{name}' + text[end:]
                occupied = end
                break


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture_dir')
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    patterns = {scope:load_patterns(scope) for scope in SCOPES}
    lines = {}
    for path in sorted(Path(args.capture_dir).iterdir()):
        if not path.is_file() or path.suffix not in ('.txt','.jsonl') or path.name.startswith('feedback-'):
            continue
        for key in records(path):
            lines[key] = lines.get(key,0) + 1
    samples = {}
    item_index = item_name_index()
    item_review = {}
    for scope,text in lines:
        stripped = re.sub(r'^\[\d{1,2}:\d{2}(?::\d{2})?\]\s*', '', text)
        if stripped.startswith(SKIP_PREFIXES) or re.fullmatch(r'✆ .+ ✆', text):
            continue
        # Player chat (optionally timestamped, ranked, icon-prefixed) is never translated.
        if re.match(r'^(?:\[\d{1,2}:\d{2}(?::\d{2})?\]\s*)?(?:(?:\[[^\]]{0,32}\]|[^A-Za-z0-9_\s]{1,3})\s*)*[A-Za-z0-9_]{1,16}\s*:\s', stripped):
            continue
        if 'joined the lobby' in text:
            continue
        source = bulk.normalize(text)[0]
        if not source.strip() or bulk.symbolic(source):
            continue
        if any(pattern.match(text) for pattern in patterns[scope]):
            continue
        matches = list(item_slots(stripped, item_index))
        if matches:
            item_review[(scope, source)] = [{'item': name, 'candidate': candidate} for name, candidate in matches]
        samples.setdefault((scope,source), text)
    if item_review and not args.dry_run:
        write_json(Path(args.capture_dir) / 'item-name-review.json',
                   [dict(scope=scope, source=source, matches=matches)
                    for (scope, source), matches in item_review.items()])
    pending = list(samples.items())
    print('unique', len(lines), 'pending', len(pending))
    if args.dry_run:
        for source, _ in pending[:80]:
            print(source)
        return
    report = Path(args.capture_dir) / 'translation-failures.json'
    if not pending:
        report.unlink(missing_ok=True)
        return
    api = DeepSeek(ROOT / 'deepseek.local.json')
    batches = [pending[i:i + 20] for i in range(0, len(pending), 20)]
    out = {}

    def run(batch):
        payload = {'items': [{'id': str(i), 'scope': key[0], 'source': key[1]} for i, (key, _) in enumerate(batch)]}
        try:
            result, _ = api.complete(SYSTEM_PROMPT, payload)
        except ApiError:
            return
        records = result.get('translations') if isinstance(result, dict) else None
        if not isinstance(records, list):
            return
        rows = {str(row.get('id')): row for row in records if isinstance(row, dict)}
        for i, (key, _) in enumerate(batch):
            row = rows.get(str(i))
            if row and validate(key[1], row.get('text')):
                out[key] = row['text']

    with ThreadPoolExecutor(max_workers=3) as pool:
        list(pool.map(run, batches))
    failed = sorted(samples.keys() - out.keys())
    if failed:
        write_json(report, {'failedTemplates': [s if scope=='chat' else f'[{scope}] {s}' for scope,s in failed]})
        print('untranslated templates:', failed, file=sys.stderr)
        raise SystemExit(1)
    data = json.loads(SYSTEM.read_text(encoding='utf8'))
    rules = data['rules']
    sources = {(scope,rule['source']) for rule in rules for scope in rule.get('scopes', ())}
    added = 0
    for (scope,source), text in out.items():
        if (scope,source) in sources:
            continue
        rules.append({'source': source, 'translation': text, 'scopes': [scope], 'styleMappings': {}})
        sources.add((scope,source))
        added += 1
    if added:
        write_json(SYSTEM, data)
    report.unlink(missing_ok=True)
    print('translated', len(out), 'rules added', added)


if __name__ == '__main__':
    main()
