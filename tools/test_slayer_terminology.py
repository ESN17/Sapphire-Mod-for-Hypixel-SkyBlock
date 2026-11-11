import copy
import unittest
from attachment_tooltip_data import ROOT, read
from slayer_terminology import update, term
from bazaar_category_feedback import correct

class SlayerTerminologyTest(unittest.TestCase):
    def test_future_generation_uses_user_glossary(self):
        policy=read(ROOT/'tools/translation_policy.json')
        self.assertEqual('杀手',policy['terms']['Slayer'])
        self.assertEqual('杀手',policy['terms']['Slayers'])
        self.assertEqual('亡灵维生素',policy['exact_names']['Vitamin Death'])
        self.assertEqual('古怪油画礼包',policy['exact_names']['Eccentric Painting Bundle'])
    def test_source_aware_glossary_preserves_neu_and_unrelated_hunters(self):
        data={'id':'WOLF_SLAYER', '_original_name':'Wolf Slayer', '_original_lore':['Requires Wolf Slayer 6.'],
              'name':'狼猎手','loreParagraphs':[{'source':['Requires Wolf Slayer {level}.'],'translation':'需要狼猎手 {level}.','styleMappings':{'Wolf Slayer':'狼猎手'}}],
              'lore':{'Hunter':'猎手','Blaze Slayer':'Blaze 猎手'}}
        original=copy.deepcopy(data);update(data)
        for key in ('id','_original_name','_original_lore'):self.assertEqual(original[key],data[key])
        self.assertEqual('狼杀手',data['name'])
        self.assertEqual('需要狼杀手 {level}.',data['loreParagraphs'][0]['translation'])
        self.assertEqual('狼杀手',data['loreParagraphs'][0]['styleMappings']['Wolf Slayer'])
        self.assertEqual('猎手',data['lore']['Hunter'])
        self.assertEqual('烈焰人杀手',data['lore']['Blaze Slayer'])

    def test_painting_bundle_keeps_reference_text_and_uses_new_name(self):
        data={'name':'古怪画作礼包','_original_name':'Eccentric Painting Bundle','loreParagraphs':[{'source':['9x Eccentric Painting'],'translation':'9x 古怪画作','styleMappings':{'Eccentric Painting':'古怪画作'}}]}
        correct(data,'ECCENTRIC_PAINTING_BUNDLE')
        self.assertEqual('古怪油画礼包',data['name'])
        self.assertEqual('9x 古怪油画',data['loreParagraphs'][0]['translation'])
        self.assertEqual(['9x Eccentric Painting'],data['loreParagraphs'][0]['source'])
