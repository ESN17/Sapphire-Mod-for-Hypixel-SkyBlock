import json
import re
import unittest
from location_catalog import catalog, category_pages
from location_translation_data import LOCATIONS, MONTHS, NON_LOCATION_PAGES, ROOT, ROOTS

class LocationCatalogTest(unittest.TestCase):
    def test_every_catalogue_location_and_category_page_is_accounted_for(self):
        self.assertEqual(315, len(catalog()))
        self.assertEqual(339, len(category_pages()))
        self.assertFalse({row['name'] for row in catalog()} - LOCATIONS.keys())
        self.assertFalse(category_pages().keys() - LOCATIONS.keys() - NON_LOCATION_PAGES)
        self.assertNotIn('Rift Time', LOCATIONS)

    def test_all_place_names_are_fully_chinese(self):
        for name, translated in LOCATIONS.items():
            self.assertIsNone(re.search('[A-Za-z]', translated), name)
            self.assertRegex(translated, '[\u3400-\u9fff]')
        self.assertEqual(12,len(MONTHS))

    def test_runtime_dictionaries_are_synchronized(self):
        for filename in ('locations.json','skyblock-months.json','sidebar-formats.json','bazaar-events.json','scoreboard.json'):
            content=[(root/filename).read_bytes() for root in ROOTS]
            self.assertTrue(all(raw==content[0] for raw in content))
        events=json.loads((ROOTS[0]/'bazaar-events.json').read_text(encoding='utf-8'))
        self.assertEqual('丰收节',events['Feast'])
        self.assertEqual('圣诞节',events['Winter'])
