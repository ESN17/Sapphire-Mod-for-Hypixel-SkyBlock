import unittest
from skyhanni_dictionary import valid

class SkyHanniFormatTest(unittest.TestCase):
    def test_chinese_adjacent_command(self):
        self.assertTrue(valid('Run /sh now','现在执行/sh'))
        self.assertFalse(valid('Run /sh now','现在执行/shreload'))
        self.assertFalse(valid('Run /sh now','现在执行'))
    def test_slash_between_translated_nouns_is_not_a_command(self):
        self.assertTrue(valid('Dungeons/Kuudra','地牢/Kuudra'))
        self.assertTrue(valid('Use /gfs <name/id>','使用 /gfs <名称/ID>'))
    def test_repeated_commands_are_counted(self):
        self.assertTrue(valid('/sh then /sh','/sh 然后 /sh'))
        self.assertFalse(valid('/sh then /sh','/sh'))
    def test_numbers_colors_and_lines_are_preserved(self):
        self.assertTrue(valid('§aGain 12\n§cLose 3','§a获得 12\n§c失去 3'))
        self.assertFalse(valid('§aGain 12','§c获得 12'))
        self.assertFalse(valid('Gain 12','获得 13'))
        self.assertFalse(valid('Line 1\nLine 2','行 1 行 2'))
    def test_dynamic_variables_are_protected(self):
        self.assertTrue(valid('Selected player: {sh0}','已选择玩家：{sh0}'))
        self.assertFalse(valid('Selected player: {sh0}','已选择玩家：{sh1}'))

if __name__=='__main__':unittest.main()
