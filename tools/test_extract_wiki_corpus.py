import unittest
from extract_wiki_corpus import extract, fields, readable


def page(text):
    return dict(title='Example', pageid=1, revision=2, url='https://example.test', revisionUrl='https://example.test/?oldid=2',
                license='CC BY-NC-SA 3.0', origins=[], wikitext=text)


class WikiExtractionTest(unittest.TestCase):
    def test_npc_and_formatting(self):
        rows, _, pending, _ = extract(page('{{Dialogue|&e[NPC] &5Enigma&f: Find [[Enigma Soul|my souls]]!}}'))
        self.assertEqual(rows[0]['npc'], 'Enigma')
        self.assertEqual(rows[0]['source'], 'Find my souls!')
        self.assertFalse(pending)

    def test_nested_and_unknown_template(self):
        rows, _, pending, _ = extract(page('{{Dialogue|{{Rmt|&eHello {{Unknown|x}}}}}}'))
        self.assertEqual(len(pending), 1)
        self.assertIn('{{Unknown|x}}', pending[0]['rawWiki'])
        self.assertFalse(rows)

    def test_ui_escaped_comma_command_and_paragraph(self):
        rows, menus, _, _ = extract(page(r'{{UI|Test|2, 3=Bone, none, &aPets, &7View pets\, now./&7Use \/pets.//&eClick!|id=test}}'))
        self.assertEqual(len(menus), 1)
        self.assertEqual([r['source'] for r in rows], ['Test', 'Pets', 'View pets, now. Use /pets.', 'Click!'])

    def test_commented_old_ui_is_excluded(self):
        rows, menus, _, _ = extract(page('<!--{{UI|Old}}-->{{UI|New}}'))
        self.assertEqual([r['source'] for r in rows], ['New'])

    def test_separators_nested(self):
        self.assertEqual(fields(r'X,{{A|a,b}},x\, y,z', ',', 3), ['X', '{{A|a,b}}', r'x\, y', 'z'])

    def test_prose_not_extracted_and_missing_marker_retained(self):
        rows, _, _, missing = extract(page('The wiki says hello. {{More Info Needed}}'))
        self.assertFalse(rows)
        self.assertEqual(len(missing), 1)


if __name__ == '__main__':
    unittest.main()
