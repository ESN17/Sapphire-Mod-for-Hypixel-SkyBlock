"""Normalize the user-requested Gabagool term in display translations only."""
import json
import re
from attachment_tooltip_data import ROOT, ROOTS, read

NAMES = {
    'Very Crude Gabagool': '极粗劣加巴古尔',
    'Crude Gabagool': '粗制加巴古尔',
    'Fuel Gabagool': '燃料加巴古尔',
    'Heavy Gabagool': '重型加巴古尔',
    'Hypergolic Gabagool': '超燃加巴古尔',
    'Gabagool Distillate': '加巴古尔蒸馏液',
    'Gabagool the Fish': '鱼加巴古尔',
}


def translate(text):
    for source, target in NAMES.items():
        text = re.sub(r'\b' + re.escape(source) + r'\b', lambda _: target, text, flags=re.I)
    text = re.sub(r'(?<![A-Za-z_])gabagool(?![A-Za-z_])', '加巴古尔', text, flags=re.I)
    # Remove leftover word spacing only where the new Chinese term meets Chinese.
    return re.sub(r'(?<=[\u3400-\u9fff]) +(?=加巴古尔)|(?<=加巴古尔) +(?=[\u3400-\u9fff])', '', text)


def update(value):
    if isinstance(value, list):
        for child in value:
            update(child)
    elif isinstance(value, dict):
        for key, child in value.items():
            if key.startswith('_') or key in ('source', 'id'):
                continue
            if isinstance(child, (dict, list)):
                update(child)
            elif isinstance(child, str):
                value[key] = translate(child)


def main():
    changed = 0
    for root in ROOTS:
        for path in root.rglob('*.json'):
            data = read(path)
            before = json.dumps(data, ensure_ascii=False)
            update(data)
            if before != json.dumps(data, ensure_ascii=False):
                path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
                changed += 1
    path = ROOT / 'tools/translation_policy.json'
    policy = read(path)
    policy['terms']['Gabagool'] = '加巴古尔'
    policy['terms']['gabagool'] = '加巴古尔'
    policy['exact_names'].update(NAMES)
    path.write_text(json.dumps(policy, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Updated {changed} dictionary files across three roots.')


if __name__ == '__main__':
    main()
