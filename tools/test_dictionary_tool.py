import json
import pathlib
import tempfile
import unittest
import dictionary_tool as tool

class DictionaryToolTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=pathlib.Path(self.temp.name)/'config';self.repo=pathlib.Path(self.temp.name)/'neu';(self.repo/'items').mkdir(parents=True)
    def tearDown(self): self.temp.cleanup()
    def neu(self,name='English',lore=None):
        tool.write(self.repo/'items/TEST.json',{'internalname':'TEST','displayname':name,'lore':lore or ['Damage: +100'],'nbttag':'{ExtraAttributes:{id:"TEST"}}'})
    def test_sync_is_incremental_and_preserves_chinese(self):
        self.neu();report=tool.sync_neu(self.repo,self.root);self.assertEqual(['TEST'],report['new'])
        path=self.root/'translations/items/TEST.json';item=tool.read(path);item['name']='已有译文';tool.write(path,item)
        self.neu('Changed');report=tool.sync_neu(self.repo,self.root)
        self.assertEqual(['TEST'],report['changed']);self.assertEqual('已有译文',tool.read(path)['name'])
        self.assertEqual('Changed',tool.read(self.root/'neu-index.json')['items']['TEST']['name'])
    def test_malformed_neu_and_missing_fields(self):
        self.neu();tool.write(self.repo/'items/BARE.json',{})
        (self.repo/'items/BROKEN.json').write_text('{bad',encoding='utf8')
        report=tool.sync_neu(self.repo,self.root);self.assertEqual(1,len(report['errors']));self.assertIn('BARE',report['new'])
    def test_blank_lore_is_not_exported_as_translation_key(self):
        self.neu(lore=[' ','','Damage: +100']);tool.sync_neu(self.repo,self.root)
        self.assertEqual({'Damage: +100':''},tool.read(self.root/'translations/items/TEST.json')['lore'])
    def test_duplicate_keys_and_template_variables(self):
        path=self.root/'translations/global.json';path.parent.mkdir(parents=True);path.write_text('{"A":"甲","A":"乙"}',encoding='utf8')
        errors,_=tool.validate(self.root);self.assertTrue(errors)
        path.unlink();tool.write(self.root/'translations/templates.json',{'Damage: {value}':'伤害：{wrong}'})
        errors,_=tool.validate(self.root);self.assertTrue(errors)
    def test_export_import_preserves_existing_translation(self):
        self.neu();tool.sync_neu(self.repo,self.root)
        record={'schemaVersion':1,'category':'item','id':'TEST','original':{'name':'English','lore':['Damage: +100']},'translation':{'name':'','lore':{}}}
        tool.write(self.root/'untranslated/test.json',record);output=self.root/'export.json';self.assertEqual(1,tool.export_records(self.root,output))
        data=tool.read(output);data['entries'][0]['name']='测试';data['entries'][0]['lore']['Damage: +100']='伤害：+100';tool.write(output,data)
        tool.import_records(self.root,output);item=tool.read(self.root/'translations/items/TEST.json');self.assertEqual('测试',item['name'])
        data['entries'][0]['name']='覆盖';tool.write(output,data);_,conflicts=tool.import_records(self.root,output)
        self.assertTrue(conflicts);self.assertEqual('测试',tool.read(self.root/'translations/items/TEST.json')['name'])
        with self.assertRaises(ValueError):tool.export_records(self.root,output)
    def test_invalid_import_writes_nothing(self):
        data={'schemaVersion':1,'entries':[{'category':'gui','lore':{'Menu':'菜单'}},{'category':'templates','lore':{'Damage: {value}':'伤害：{x}'}}]}
        path=self.root/'import.json';tool.write(path,data)
        with self.assertRaises(ValueError):tool.import_records(self.root,path)
        self.assertFalse((self.root/'translations/gui.json').exists())
    def test_unknown_id_is_not_guessed(self):
        path=self.root/'import.json';tool.write(path,{'schemaVersion':1,'entries':[{'category':'item','id':None,'name':'未知','lore':{}}]})
        count,conflicts=tool.import_records(self.root,path);self.assertEqual(0,count);self.assertTrue(conflicts)
    def test_windows_ids_are_safe(self):
        for value in ('../escape','a/b','a\\b'):self.assertRaises(ValueError,tool.filename,value)
        self.assertNotIn(':',tool.filename('RUNE:1'));self.assertNotEqual('CON.json',tool.filename('CON'))
    def test_paragraphs_and_styles_merge_without_overwriting_user_text(self):
        source=self.root.parent/'workspace'
        item={'schemaVersion':1,'id':'TEST','name':'测试','lore':{'Damage: {value}':'伤害：{value}'},
              'styleMappings':{'blocks':'格'},'loreParagraphs':[{'source':['Teleport {distance} blocks','ahead.'],'translation':'向前传送 {distance} 格。'}]}
        tool.write(source/'translations/items/TEST.json',item)
        count,conflicts=tool.merge_workspace(self.root,source);self.assertEqual(1,count);self.assertFalse(conflicts)
        saved=tool.read(self.root/'translations/items/TEST.json');self.assertEqual(item['loreParagraphs'],saved['loreParagraphs']);self.assertEqual(item['styleMappings'],saved['styleMappings'])
        item['loreParagraphs'][0]['translation']='传送 {distance} 格。';item['styleMappings']['blocks']='方块'
        tool.write(source/'translations/items/TEST.json',item)
        _,conflicts=tool.merge_workspace(self.root,source);self.assertEqual(2,len(conflicts))
        self.assertEqual(saved,tool.read(self.root/'translations/items/TEST.json'))
    def test_invalid_paragraph_import_writes_nothing(self):
        row={'category':'item','id':'TEST','loreParagraphs':[{'source':['Teleport {distance} blocks'],'translation':'传送 {wrong} 格'}]}
        path=self.root/'import.json';tool.write(path,{'schemaVersion':1,'entries':[{'category':'gui','lore':{'Menu':'菜单'}},row]})
        with self.assertRaises(ValueError):tool.import_records(self.root,path)
        self.assertFalse((self.root/'translations/gui.json').exists())
    def test_paragraph_maps_validate_and_merge_without_replacing_existing_anchor(self):
        bad={'loreParagraphs':[{'source':['Red'], 'translation':'红', 'styleMappings':[]}]}
        self.assertTrue(tool.check_item_extensions(bad,'test'))
        old={'loreParagraphs':[{'source':['Red Blue'], 'translation':'红 蓝', 'styleMappings':{'Red':'红'}}]}
        new={'loreParagraphs':[{'source':['Red Blue'], 'translation':'红 蓝', 'styleMappings':{'Red':'赤','Blue':'蓝'}}]}
        conflicts=[];tool.merge_item_extensions(old,new,conflicts,'test')
        self.assertEqual({'Red':'红','Blue':'蓝'},old['loreParagraphs'][0]['styleMappings'])
        self.assertEqual(1,len(conflicts))
    def test_bundled_reforges_cover_research_snapshot(self):
        project=pathlib.Path(__file__).resolve().parent.parent
        metadata=tool.read(project/'docs/reforge-sources.json')
        data=tool.read(project/'src/main/resources/defaults/translations/reforges.json')
        expected=set(metadata['basic']+metadata['stone']+metadata['legacy']+metadata['aliases'])
        self.assertEqual(expected,set(data));self.assertEqual(136,len(set(metadata['basic']+metadata['stone'])))
        self.assertEqual(165,len(data));self.assertEqual('英雄的',data['Heroic']);self.assertEqual(len(data),len(set(data.values())))
    def test_enchantment_dictionaries_merge_and_preserve_user_names(self):
        source=self.root.parent/'workspace'
        tool.write(source/'translations/enchantments.json',{'Champion':'近战战斗专精','Toxophilite':'远程战斗专精'})
        tool.write(source/'translations/enchantment-ids.json',{'Champion':'CHAMPION','Toxophilite':'TOXOPHILITE'})
        errors,warnings=tool.validate(source);self.assertFalse(errors);self.assertFalse(warnings)
        count,conflicts=tool.merge_workspace(self.root,source);self.assertEqual(2,count);self.assertFalse(conflicts)
        tool.write(source/'translations/enchantments.json',{'Champion':'修改后的名称'})
        _,conflicts=tool.merge_workspace(self.root,source);self.assertTrue(conflicts)
        self.assertEqual('近战战斗专精',tool.read(self.root/'translations/enchantments.json')['Champion'])
    def test_shared_tooltip_paragraphs_validate_merge_and_keep_edits(self):
        source=self.root.parent/'workspace'
        original={'schemaVersion':1,'loreParagraphs':[{'source':['Requires level {level}'],'translation':'需要等级 {level}'}]}
        tool.write(source/'translations/tooltip-paragraphs.json',original)
        errors,warnings=tool.validate(source);self.assertFalse(errors);self.assertFalse(warnings)
        _,conflicts=tool.merge_workspace(self.root,source);self.assertFalse(conflicts)
        original['loreParagraphs'][0]['translation']='所需等级 {level}'
        tool.write(source/'translations/tooltip-paragraphs.json',original)
        _,conflicts=tool.merge_workspace(self.root,source);self.assertTrue(conflicts)
        saved=tool.read(self.root/'translations/tooltip-paragraphs.json')
        self.assertEqual('需要等级 {level}',saved['loreParagraphs'][0]['translation'])
    def test_unused_neu_duplicates_warn_but_identity_duplicates_fail(self):
        p=self.repo/'items/TEST.json';p.write_text('{"internalname":"TEST","displayname":"Test","modver":"1","modver":"2"}',encoding='utf8')
        report=tool.sync_neu(self.repo,self.root);self.assertEqual(1,len(report['warnings']));self.assertFalse(report['errors'])
        p.write_text('{"internalname":"TEST","internalname":"WRONG"}',encoding='utf8')
        with self.assertRaises(ValueError):tool.sync_neu(self.repo,self.root)
    def test_merge_workspace_only_imports_completed_values(self):
        self.neu();tool.sync_neu(self.repo,self.root)
        dest=pathlib.Path(self.temp.name)/'game';count,_=tool.merge_workspace(dest,self.root);self.assertEqual(0,count)
        p=self.root/'translations/items/TEST.json';value=tool.read(p);value['name']='测试';tool.write(p,value)
        count,_=tool.merge_workspace(dest,self.root);self.assertEqual(1,count);self.assertEqual('测试',tool.read(dest/'translations/items/TEST.json')['name'])

if __name__=='__main__':unittest.main()
