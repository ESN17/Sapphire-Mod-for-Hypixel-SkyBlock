import unittest
import apply_first_feedback as revision


class FirstFeedbackTests(unittest.TestCase):
    def test_named_and_numeric_slots_are_not_translated(self):
        self.assertEqual('残暴值:{ferocity} {{N0}}', revision.rewrite(
            'Ferocity: {ferocity} {{N0}}', '凶猛:{ferocity} {{N0}}'))

    def test_commands_keep_executable_arguments(self):
        self.assertEqual('杰瑞: /warp jerry', revision.rewrite('Jerry: /warp jerry', 'Jerry: /warp jerry'))

    def test_crop_and_rare_material_remain_distinct(self):
        self.assertEqual('种植南瓜掉落西葫芦', revision.rewrite('Pumpkins drop Squash', '种植南瓜掉落Squash'))
        self.assertEqual('向日葵掉落向阳花', revision.rewrite('Sunflower drops Helianthus', '向日葵掉落Helianthus'))
        self.assertEqual('地牢中的墓穴', revision.rewrite('Crypts in The Catacombs', '地下墓穴中的墓穴'))

    def test_names_and_terminology_are_idempotent(self):
        revision.ALIASES['Bluertooth Ring'] = {'蓝牙戒指'}
        first = revision.rewrite('Bluertooth Ring', '蓝牙戒指')
        self.assertEqual('更蓝的蓝牙戒指', first)
        self.assertEqual(first, revision.rewrite('Bluertooth Ring', first))
        self.assertEqual('地牢', revision.rewrite('Catacombs', '地牢墓穴'))


if __name__ == '__main__': unittest.main()
