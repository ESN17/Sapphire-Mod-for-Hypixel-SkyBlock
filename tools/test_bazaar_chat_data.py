import collections
import re
import unittest
from attachment_tooltip_data import ROOTS, read
from bazaar_chat_data import MESSAGES, EXACT


class BazaarChatDataTest(unittest.TestCase):
    def test_reviewed_catalog_is_synced_and_keeps_placeholders(self):
        for root in ROOTS:
            menu=next(m for m in read(root/'gui-menus.json')['menus'] if m['id']=='bazaar')
            rules={p['source'][0]:p['translation'] for p in menu['loreParagraphs'] if len(p['source'])==1}
            for source,target in MESSAGES.items():
                self.assertEqual(target,rules[source])
                self.assertEqual(collections.Counter(re.findall(r'\{[^}]+\}',source)),collections.Counter(re.findall(r'\{[^}]+\}',target)))
            chat=read(root/'chat.json')
            for source,target in EXACT.items():
                self.assertEqual(target,chat[source])
