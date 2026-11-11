"""Correct the reviewed Magic Find term without changing English match keys."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'src/main/resources/defaults'
OLD, NEW = '魔力寻获', '稀有物品概率加成'
ALIASES = (OLD, '魔法寻宝')

def fix_dictionary(key):
    path = BASE / key
    before = path.read_bytes()
    text = before.decode('utf-8')
    count = sum(text.count(term) for term in ALIASES)
    for term in ALIASES:
        text = text.replace(term, NEW)
    after = text.encode('utf-8')
    json.loads(after)
    if before != after:
        backup = ROOT / 'translation-state/magic-find-20261102' / path.name
        backup.parent.mkdir(parents=True, exist_ok=True)
        if not backup.exists():
            backup.write_bytes(before)
        hashes_path = BASE / 'upgrade-hashes.json'
        hashes = json.loads(hashes_path.read_text(encoding='utf-8'))
        hashes[key] = ','.join(sorted(set(filter(None, hashes.get(key, '').split(','))) | {hashlib.sha256(before).hexdigest()}))
        path.write_bytes(after)
        hashes_path.write_text(json.dumps(hashes, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        for root in (ROOT / 'dictionary-workspace', ROOT / 'run/config/sapphire'):
            mirror = root / key
            if mirror.exists() and mirror.read_bytes() == before:
                mirror.write_bytes(after)
    return count

def main():
    keys = ['translations/system-messages.json', 'translations/community-sapphire.json', 'translations/tablist.json']
    keys.extend(p.relative_to(BASE).as_posix() for p in (BASE / 'translations/community-wiki').glob('*.json'))
    count = sum(fix_dictionary(key) for key in keys)
    generator = ROOT / 'tools/apply_feedback_20261101.py'
    generator.write_text(generator.read_text(encoding='utf-8').replace(OLD, NEW), encoding='utf-8')
    for name in ('translation_policy.json', 'sapphire-translation-rules.json'):
        policy = ROOT / 'tools' / name
        data = json.loads(policy.read_text(encoding='utf-8'))
        for term in ALIASES:
            data['previous_translations'][term] = NEW
        policy.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Corrected {count} translation/style occurrences')

if __name__ == '__main__':
    main()
