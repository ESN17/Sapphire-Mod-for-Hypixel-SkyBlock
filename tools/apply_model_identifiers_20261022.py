"""User corrections: Bits and literal equipment model identifiers; no API calls."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'src/main/resources/defaults'
ROOTS = [BASE, ROOT / 'dictionary-workspace', ROOT / 'run/config/sapphire']
MODELS = {
    'TITANIUM_DRILL_1': ('迪尔艾克斯355', 'DR-X355'),
    'TITANIUM_DRILL_2': ('迪尔艾克斯455', 'DR-X455'),
    'TITANIUM_DRILL_3': ('迪尔艾克斯555', 'DR-X555'),
    'TITANIUM_DRILL_4': ('迪尔艾克斯655', 'DR-X655'),
    'MITHRIL_DRILL_1': ('艾斯艾克斯阿尔226', 'SX-R226'),
    'MITHRIL_DRILL_2': ('艾斯艾克斯阿尔326', 'SX-R326'),
    'GEMSTONE_DRILL_1': ('提克斯-15', 'TX-15'),
    'GEMSTONE_DRILL_2': ('艾尔提-522', 'LT-522'),
    'GEMSTONE_DRILL_3': ('凯吉尔-12', 'KGR-12'),
    'ABICASE_SUMSUNG_1': ('吉3', 'G3'),
    'ABICASE_SUMSUNG_2': ('二代', 'GG'),
    'FTX_3070': ('弗提克斯 3070', 'FTX 3070'),
    'SYNTHESIZER_V1': ('1型', 'v1'),
    'SYNTHESIZER_V2': ('2型', 'v2'),
    'SYNTHESIZER_V3': ('3型', 'v3'),
}

def read(p):
    return json.loads(p.read_text(encoding='utf-8-sig'))

def encoded(data):
    return (json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode('utf-8')

def main():
    hashes = read(BASE / 'upgrade-hashes.json')
    changed = []
    def save(p, data):
        old = p.read_bytes()
        new = encoded(data)
        if old == new:
            return
        backup = ROOT / 'translation-state/feedback-20261022/before' / p.relative_to(ROOT)
        backup.parent.mkdir(parents=True, exist_ok=True)
        if not backup.exists():
            backup.write_bytes(old)
        if p.is_relative_to(BASE):
            key = p.relative_to(BASE).as_posix()
            prior = set(hashes.get(key, '').split(',')) - {''}
            prior.add(hashlib.sha256(old).hexdigest())
            hashes[key] = ','.join(sorted(prior))
        p.write_bytes(new)
        changed.append(str(p.relative_to(ROOT)))

    for root in ROOTS:
        for filename in ['tablist.json', 'scoreboard.json']:
            path = root / 'translations' / filename
            data = read(path)
            data['Bits:'] = '比特:'
            if filename == 'tablist.json':
                data['Bits'] = '比特'
            save(path, data)
        for item_id, (old, identifier) in MODELS.items():
            path = root / 'translations/items' / (item_id + '.json')
            data = read(path)
            data['name'] = data['name'].replace(old, identifier)
            data['styleMappings'] = {k: v.replace(old, identifier)
                                     for k, v in data.get('styleMappings', {}).items()}
            # Synthesizer maps only the number; use the full source token instead.
            if item_id == 'FTX_3070':
                data['styleMappings']['FTX'] = 'FTX'
            save(path, data)
    (BASE / 'upgrade-hashes.json').write_bytes(encoded(hashes))
    policy_path = ROOT / 'tools/translation_policy.json'
    policy = read(policy_path)
    policy['preserve_identifiers'] = sorted(set(policy.get('preserve_identifiers', [])) |
                                             {value[1] for value in MODELS.values()})
    for item_id in MODELS:
        data = read(BASE / 'translations/items' / (item_id + '.json'))
        policy['exact_names'][data['_original_name']] = data['name']
    rule = '物品型号和字母数字代号必须保留原样，例如 DR-X655、SX-R326、TX-15、FTX 3070、G3、GG、v1；不得音译字母，不得改写连字符或数字。只翻译型号周围的普通物品名称。'
    if rule not in policy['rules']:
        policy['rules'].append(rule)
    policy_path.write_bytes(encoded(policy))
    print(f'Updated {len(changed)} dictionary files across three roots; 15 model names reviewed.')

if __name__ == '__main__':
    main()
