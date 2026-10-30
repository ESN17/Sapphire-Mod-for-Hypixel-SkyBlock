import copy
import re
import unittest
from attachment_tooltip_data import ROOT, ROOTS, read
from gabagool_terminology import NAMES, update


class GabagoolTerminologyTest(unittest.TestCase):
    def test_preserves_matching_sources_ids_and_styles(self):
        data = {'id': 'CRUDE_GABAGOOL', '_original_name': 'Crude Gabagool',
                '_original_lore': ['Feed Gabagool.'], 'name': '粗制 Gabagool',
                'loreParagraphs': [{'source': ['Use {count} Fuel Gabagool.'],
                    'translation': '使用 {count} Fuel Gabagool.',
                    'styleMappings': {'Fuel Gabagool': 'Fuel Gabagool'}}],
                'lore': {'Reacts with gabagool.': '与 gabagool 反应.'}}
        before = copy.deepcopy(data)
        update(data)
        for key in ('id', '_original_name', '_original_lore'):
            self.assertEqual(before[key], data[key])
        paragraph = data['loreParagraphs'][0]
        self.assertEqual(before['loreParagraphs'][0]['source'], paragraph['source'])
        self.assertEqual('粗制加巴古尔', data['name'])
        self.assertEqual('使用 {count} 燃料加巴古尔.', paragraph['translation'])
        self.assertEqual({'Fuel Gabagool': '燃料加巴古尔'}, paragraph['styleMappings'])
        self.assertEqual('与加巴古尔反应.', data['lore']['Reacts with gabagool.'])
        once = copy.deepcopy(data)
        update(data)
        self.assertEqual(once, data)

    def test_all_roots_have_no_remaining_english_display_terms(self):
        for root in ROOTS:
            found = {}
            for path in root.rglob('*.json'):
                raw = path.read_text(encoding='utf-8-sig')
                if not re.search('gabagool', raw, re.I):
                    continue
                data = read(path)
                before = copy.deepcopy(data)
                update(data)
                self.assertEqual(before, data, str(path))
                if data.get('_original_name') in NAMES:
                    found[data['_original_name']] = data['name']
            self.assertEqual(NAMES, found)
        policy = read(ROOT / 'tools/translation_policy.json')
        self.assertEqual('加巴古尔', policy['terms']['Gabagool'])
        for name, translated in NAMES.items():
            self.assertEqual(translated, policy['exact_names'][name])
