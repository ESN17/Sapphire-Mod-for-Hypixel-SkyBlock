import unittest
import bulk_translate as bulk
import dictionary_tool as dictionary
import install_bulk_translations as installer
import enrich_style_mappings as enrichment

class BulkTranslationTest(unittest.TestCase):
    def test_masks_numbers_without_changing_symbols_or_formatting(self):
        source,styled,values=bulk.normalize('§7Gain §a+50% §b☘ Luck §7for §a3s.')
        self.assertEqual('Gain {{N0}} ☘ Luck for {{N1}}s.',source)
        self.assertEqual(['+50%','3'],values)
        self.assertIn('§a{{N0}}',styled)
        self.assertEqual('Gain +50% ☘ Luck for 3s.',bulk.restore(source,values))
    def test_color_inside_a_number_is_not_lost_for_following_words(self):
        source,styled,values=bulk.normalize('§7Gain +§c10 Strength')
        self.assertEqual('Gain {{N0}} Strength',source)
        self.assertEqual('§7Gain {{N0}}§c Strength',styled)
        self.assertEqual(['+10'],values)
    def test_placeholder_and_symbol_loss_is_rejected(self):
        job={'kind':'lore','source':'Gain {{N0}} ☘ for {{N1}}s.'}
        good={'text':'获得 {{N0}} ☘, 持续 {{N1}}秒.','styleMappings':{'s':'秒'}}
        self.assertEqual([],bulk.validate(job,good))
        for target in ['获得 {{N0}} ☘.', '获得 {{N0}} ☘ {{N0}} {{N1}}.', '获得 {{N0}}, 持续 {{N1}}秒.']:
            self.assertTrue(bulk.validate(job,dict(good,text=target)))
    def test_required_terms_and_aspect_names(self):
        self.assertTrue(bulk.validate({'kind':'name','source':'Uncommon Pet'},{'text':'优秀宠物'}))
        self.assertEqual([],bulk.validate({'kind':'name','source':'Uncommon Pet'},{'text':'罕见宠物'}))
        self.assertTrue(bulk.validate({'kind':'name','source':'Aspect of the End'},{'text':'末影之剑'}))
        self.assertEqual([],bulk.validate({'kind':'name','source':'Aspect of the End'},{'text':'末影之刃'}))
    def test_pure_symbols_and_numbers_are_not_sent_for_translation(self):
        self.assertEqual({'text':'{{N0}}% ☘','styleMappings':{}},bulk.known('{{N0}}% ☘','lore'))
    def test_double_brace_placeholder_form_and_count_are_preserved(self):
        self.assertEqual([],dictionary.check_text('Gain {{N0}}','获得 {{N0}}','test',True))
        self.assertTrue(dictionary.check_text('Gain {{N0}}','获得 {N0}','test',True))
        self.assertTrue(dictionary.check_text('Gain {{N0}}','获得 {{N0}} {{N0}}','test',True))
    def test_wrapped_prose_does_not_cross_blank_lines_or_consume_stats(self):
        raw=['§7Damage: §c+100','','§6Ability: Test  §eRIGHT CLICK','§7Teleport 8 blocks ahead of you and','§7gain speed.','','§9RARE SWORD']
        self.assertEqual([raw[:1],raw[2:3],raw[3:5],raw[6:]],list(bulk.groups(raw)))
    def test_full_install_keeps_live_bonus_rules_but_updates_requested_standard(self):
        staged={'id':'TEST','name':'末影之刃','lore':{},'loreParagraphs':[], 'styleMappings':{}}
        old={'name':'末影之剑','lore':{'Damage: {value} ({bonus})':'伤害：{value} ({bonus})','UNCOMMON SWORD':'优秀 剑'},
             'loreParagraphs':[{'source':['Can be reforged.'],'translation':'可以重铸。'}]}
        result=installer.merged(staged,old)
        self.assertEqual('末影之刃',result['name'])
        self.assertEqual('伤害:{value} ({bonus})',result['lore']['Damage: {value} ({bonus})'])
        self.assertEqual('罕见 剑',result['lore']['UNCOMMON SWORD'])
        self.assertEqual('可以洗练.',result['loreParagraphs'][0]['translation'])
    def test_enrichment_does_not_create_overlapping_anchor_ranges(self):
        self.assertEqual({'Attack Speed':'攻击速度','I':'I'},enrichment.prune_overlaps('Attack Speed I','攻击速度 I',
            {'Attack Speed':'攻击速度','Speed I':'速度 I','I':'I'}))
    def test_accepted_paragraph_is_preferred_unless_it_breaks_new_terminology(self):
        staged={'id':'TEST','name':'测试','lore':{},'loreParagraphs':[{'source':['Combo is {{N0}}% better.'],'translation':'Combo 提高 {{N0}}%.'}]}
        reviewed={'name':'测试','loreParagraphs':[{'source':['Combo is {bonus} better.'],'translation':'连击附魔提高 {bonus}。'},
                     {'source':['Mining Skill {level}'],'translation':'采矿等级 {level}'}]}
        result=installer.merged(staged,{},reviewed)
        self.assertEqual('连击附魔提高 {bonus}.',result['loreParagraphs'][0]['translation'])
        self.assertFalse(any('采矿等级' in p['translation'] for p in result['loreParagraphs']))

if __name__=='__main__':unittest.main()
