import unittest
from install_wiki_translations import numeric_rule


class WikiNumericRuleTest(unittest.TestCase):
    def test_separated_live_numbers_keep_individual_captures(self):
        self.assertTrue(numeric_rule('Progress: {{N0}} {{N1}} / {{N2}} Tickets', ['0%', '0', '150']))
        self.assertTrue(numeric_rule('Earn {{N0}} Coins', ['+10,000']))

    def test_names_and_ambiguous_numbers_cannot_be_numeric_templates(self):
        self.assertFalse(numeric_rule('Hello {{N0}}!', ['{player}']))
        self.assertFalse(numeric_rule('Value {{N0}}{{N1}}', ['1', '2']))
        self.assertFalse(numeric_rule('{{N0}} {{N1}} {{N2}}', ['1', '2', '3']))


if __name__ == '__main__': unittest.main()
