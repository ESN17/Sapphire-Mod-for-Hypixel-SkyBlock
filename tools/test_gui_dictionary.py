import copy
import pathlib
import tempfile
import unittest
import dictionary_tool as tool

class GuiDictionaryTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=pathlib.Path(self.temp.name)/'target';self.source=pathlib.Path(self.temp.name)/'source'
        self.data={'schemaVersion':1,'loreParagraphs':[],'menus':[{'id':'bazaar','titles':{'Bazaar ➜ {text}':'集市 ➜ {text}'},'loreParagraphs':[{'source':['Price: {value} coins'],'translation':'价格: {value} 硬币'}]}]}
    def test_merge_supports_contexts_and_keeps_human_edits(self):
        p=self.source/'translations/gui-menus.json';tool.write(p,self.data)
        tool.write(self.source/'translations/name-modifiers.json',{'Shiny':'闪耀'})
        self.assertEqual([],tool.validate(self.source)[0]);tool.merge_workspace(self.root,self.source)
        installed=self.root/'translations/gui-menus.json';old=tool.read(installed)
        old['menus'][0]['loreParagraphs'][0]['translation']='自定义价格: {value} 硬币';tool.write(installed,old)
        _,conflicts=tool.merge_workspace(self.root,self.source)
        self.assertTrue(conflicts);self.assertEqual('自定义价格: {value} 硬币',tool.read(installed)['menus'][0]['loreParagraphs'][0]['translation'])
        self.assertEqual({'Shiny':'闪耀'},tool.read(self.root/'translations/name-modifiers.json'))
    def test_bad_title_variables_and_duplicate_contexts_are_rejected(self):
        bad=copy.deepcopy(self.data);bad['menus'][0]['titles']['Bazaar ➜ {text}']='集市'
        self.assertTrue(tool.check_gui_menus(bad,'test'))
        bad=copy.deepcopy(self.data);bad['menus'].append(copy.deepcopy(bad['menus'][0]))
        self.assertTrue(tool.check_gui_menus(bad,'test'))
    def test_invalid_gui_import_writes_no_other_dictionary(self):
        bad=copy.deepcopy(self.data);bad['menus'][0]['loreParagraphs'][0]['translation']='价格: {other}'
        data={'schemaVersion':1,'entries':[{'category':'gui','lore':{'Menu':'菜单'}},dict(bad,category='gui-menus')]}
        p=self.source/'import.json';tool.write(p,data)
        with self.assertRaises(ValueError):tool.import_records(self.root,p)
        self.assertFalse((self.root/'translations/gui.json').exists())
    def test_shared_paragraph_merge_accepts_more_than_item_limit(self):
        rows=[{'source':[f'Label {i}: {{value}}'],'translation':f'标签 {i}: {{value}}'} for i in range(140)]
        tool.write(self.source/'translations/tooltip-paragraphs.json',{'schemaVersion':1,'loreParagraphs':rows})
        tool.merge_workspace(self.root,self.source)
        self.assertEqual(140,len(tool.read(self.root/'translations/tooltip-paragraphs.json')['loreParagraphs']))
    def test_label_merge_preserves_user_translation(self):
        self.data['menus'][0]['labels']={'Redstone':'红石'}
        tool.write(self.source/'translations/gui-menus.json',self.data)
        tool.merge_workspace(self.root,self.source)
        p=self.root/'translations/gui-menus.json';old=tool.read(p)
        old['menus'][0]['labels']['Redstone']='我的红石';tool.write(p,old)
        _,conflicts=tool.merge_workspace(self.root,self.source)
        self.assertTrue(conflicts);self.assertEqual('我的红石',tool.read(p)['menus'][0]['labels']['Redstone'])
    def test_invalid_labels_rejected(self):
        for labels in [[],{'Bad':'line\nbreak'},{'Bad':''},{'Bad':'§a颜色'}]:
            bad=copy.deepcopy(self.data);bad['menus'][0]['labels']=labels
            self.assertTrue(tool.check_gui_menus(bad,'test'))

if __name__=='__main__':unittest.main()
