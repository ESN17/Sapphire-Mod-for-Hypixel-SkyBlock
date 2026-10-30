import unittest
from translate_wiki_corpus import validate, mappings


class WikiTranslationValidationTest(unittest.TestCase):
    def test_number_and_command_are_protected(self):
        job={'source':'Use /pets to buy {{N0}} pets.'}
        self.assertEqual(validate(job,{'text':'使用 /pets 购买 {{N0}} 只宠物。'}),[])
        self.assertIn('numeric placeholder mismatch',validate(job,{'text':'使用 /pets 购买宠物。'}))
        self.assertIn('command changed',validate(job,{'text':'使用 /pet 购买 {{N0}} 只宠物。'}))

    def test_symbol_and_control_failures(self):
        job={'source':'Gain ✦ {{N0}} Speed.'}
        self.assertIn('icon/symbol mismatch',validate(job,{'text':'获得 {{N0}} 速度。'}))
        self.assertIn('invalid length/control characters',validate(job,{'text':'获得 ✦ {{N0}}\n速度。'}))

    def test_styles_must_exist_in_both_languages(self):
        self.assertEqual(mappings('Magic Find','稀有物品概率加成',{'Magic Find':'稀有物品概率加成','Strength':'力量'}),
                         {'Magic Find':'稀有物品概率加成'})

    def test_chinese_adjacent_command_and_numeric_style_templates(self):
        self.assertEqual(validate({'source':'Use /pets now.'},{'text':'现在使用/pets。'}),[])
        self.assertEqual(mappings('Gain {{N0}} Motes','获得 {{N0}} 裂隙尘埃',
            {'{{N0}} Motes':'{{N0}} 裂隙尘埃','{{N0}}':'{{N0}}','Gain {{N0}}':'获得 {{N1}}'}),
            {'{{N0}} Motes':'{{N0}} 裂隙尘埃'})

    def test_wiki_example_names_are_never_filled_in(self):
        self.assertIn('Wiki example placeholder changed',validate({'source':'Welcome, [player]!'},
            {'text':'欢迎, Steve!'}))
        self.assertEqual(validate({'source':'Welcome, [player]!'}, {'text':'欢迎, [player]!'}),[])


if __name__=='__main__':unittest.main()
