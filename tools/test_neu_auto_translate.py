import copy
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import neu_auto_translate as n


class FakeClient:
    def __init__(self,bad=False):self.calls=0;self.bad=bad;self.inputs=[]
    def complete(self,system,data):
        self.calls+=1;self.inputs.append(copy.deepcopy(data))
        translations={'New Blade':'新刀刃','Old Blade':'旧刀刃','Renamed Blade':'更名刀刃',
                      'Deals {{N0}} damage.':'造成 {{N0}} 点伤害.','Deals {{N0}} fire damage.':'造成 {{N0}} 点火焰伤害.'}
        text=translations.get(data['source'],'伤害: {{N0}}')
        if self.bad:text=text.replace('{{N0}}','')
        return {'text':text,'styleMappings':{}},{'total_tokens':12}


class AutoTranslationTest(unittest.TestCase):
    def setUp(self):
        self.temp_base=n.ROOT/'build/neu-auto-tests';self.temp_base.mkdir(parents=True,exist_ok=True)
        self.temp=tempfile.TemporaryDirectory(dir=self.temp_base);self.root=Path(self.temp.name)
        self.rules=copy.deepcopy(n.read(n.ROOT/'tools/translation_policy.json'))
        self.rules['allowed_english']=['Maddox','Abiphone']
        self.baseline=self.root/'baseline'
        for name in n.REQUIRED:n.write(self.baseline/'translations'/(name+'.json'),{})
        for name in n.LICENSES:
            p=self.baseline/'licenses'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text('license fixture',encoding='utf8')
        self.old={'id':'OLD_BLADE','schemaVersion':1,'_original_name':'Old Blade',
                  '_original_lore':['Damage: +10'],'name':'人工旧刀刃','lore':{'Damage: {value}':'伤害:{value}'},'loreParagraphs':[]}
        n.write(self.baseline/'translations/items/OLD_BLADE.json',self.old)
        self.source=self.root/'neu/items';self.source.mkdir(parents=True)
        self.item={'internalname':'OLD_BLADE','displayname':'§fOld Blade','lore':['§7Damage: §c+10']}
        n.write(self.source/'OLD_BLADE.json',self.item)
        n.write(self.root/'rules.json',self.rules)
        self.config={'baseline':str(self.baseline),'rules':str(self.root/'rules.json'),
                     'state_directory':str(self.root/'state'),'output_directory':str(self.root/'pending'),
                     'deepseek_config':str(self.root/'not-needed.json'),'max_items_per_run':20,
                     'max_requests_per_run':100,'max_requests_per_day':300}
        self.sleep=patch.object(n.time,'sleep');self.sleep.start()
    def tearDown(self):
        self.sleep.stop()
        assert self.root.resolve().is_relative_to(self.temp_base.resolve())
        self.temp.cleanup()
    def run_worker(self,**kwargs):return n.run(self.config,source=self.source,**kwargs)
    def add_new(self):n.write(self.source/'NEW_BLADE.json',{'internalname':'NEW_BLADE','displayname':'New Blade','lore':['Deals 12 damage.']})
    def translator(self,client=None,limit=100,daily=300):return n.Translator(self.root/'state',self.rules,{},self.root/'none.json',limit,client,daily)

    def test_scan_uses_internal_id_and_ignores_recipe_nbt_filename(self):
        self.item.update(recipe={'A':'DIAMOND'},nbttag='private-looking data must never reach model')
        (self.source/'OLD_BLADE.json').unlink();n.write(self.source/'different-filename.json',self.item)
        report=self.run_worker(scan_only=True)
        self.assertEqual(0,report['change_count']);self.assertNotIn('nbttag',str(report))

    def test_detect_name_description_and_number_changes(self):
        _,items,_=n.load_payload(self.baseline)
        for name,lore,flag in [('Renamed Blade',['Damage: +10'],'name'),('Old Blade',['Deals 10 damage.'],'description'),('Old Blade',['Damage: +20'],'numbers')]:
            source=n.public_item({'internalname':'OLD_BLADE','displayname':name,'lore':lore})
            self.assertIn(flag,n.compare(items,{'OLD_BLADE':source})['items'][0]['fields'])

    def test_number_changes_reuse_curated_translation_without_api(self):
        before=(self.baseline/'translations/items/OLD_BLADE.json').read_bytes()
        self.item['lore']=['§7Damage: §c+75'];n.write(self.source/'OLD_BLADE.json',self.item)
        client=FakeClient();report=self.run_worker(client=client)
        self.assertEqual('pending_review',report['status']);self.assertEqual(0,client.calls)
        self.assertEqual(before,(self.baseline/'translations/items/OLD_BLADE.json').read_bytes())
        value=report['changes']['items'][0]['candidate_dictionary']
        self.assertEqual(self.old['lore'],value['lore']);self.assertEqual('人工旧刀刃',value['name'])
        self.assertEqual('伤害:{{N0}}',value['loreParagraphs'][0]['translation'])

    def test_new_items_make_full_hashed_zip_and_review_report(self):
        self.add_new();client=FakeClient();report=self.run_worker(client=client)
        self.assertEqual(2,client.calls);self.assertEqual('pending_review',report['status'])
        archive=self.root/'pending'/report['archive']
        payload,items,version=n.load_payload(archive)
        self.assertEqual({'OLD_BLADE','NEW_BLADE'},set(items))
        self.assertEqual(self.old,items['OLD_BLADE'][1]);self.assertEqual('新刀刃',items['NEW_BLADE'][1]['name'])
        self.assertEqual(report['sha256'],n.digest(archive.read_bytes()))
        self.assertTrue((self.root/'pending'/(version+'.review.json')).exists())
        self.assertTrue((self.root/'pending'/(version+'.review.txt')).exists())
        self.assertEqual('新刀刃',client.inputs[-1]['approved_name'])
        self.assertNotIn('nbttag',str(items['NEW_BLADE'][1]))

    def test_no_repeat_api_or_zip_for_same_source(self):
        self.add_new();client=FakeClient();first=self.run_worker(client=client);second=self.run_worker(client=client)
        self.assertEqual(2,client.calls);self.assertEqual('already_pending_review',second['status'])
        self.assertEqual(first['archive'],second['archive']);self.assertEqual(1,len(list((self.root/'pending').glob('*.zip'))))

    def test_resume_preserves_successful_units_when_budget_runs_out(self):
        self.add_new();client=FakeClient();self.config['max_requests_per_run']=1
        first=self.run_worker(client=client);self.assertEqual('incomplete',first['status'])
        self.assertFalse(list((self.root/'pending').glob('*.zip')))
        second=self.run_worker(client=client)
        self.assertEqual('pending_review',second['status']);self.assertEqual(2,client.calls)

    def test_item_limit_resumes_and_does_not_skip_failed_items(self):
        self.add_new();self.item['lore']=['Damage: +20'];n.write(self.source/'OLD_BLADE.json',self.item)
        self.config['max_items_per_run']=1;client=FakeClient()
        first=self.run_worker(client=client);self.assertEqual(1,len(first['remaining']))
        second=self.run_worker(client=client);self.assertEqual('pending_review',second['status']);self.assertEqual(2,client.calls)

    def test_description_change_keeps_old_rules_and_records_both_versions(self):
        self.item['lore']=['Deals 30 fire damage.'];n.write(self.source/'OLD_BLADE.json',self.item)
        report=self.run_worker(client=FakeClient());change=report['changes']['items'][0]
        self.assertEqual('pending_review',report['status']);self.assertEqual(self.old,change['previous_dictionary'])
        self.assertEqual(self.old['lore'],change['candidate_dictionary']['lore'])
        self.assertIn('火焰',change['candidate_dictionary']['loreParagraphs'][0]['translation'])

    def test_rename_only_changes_candidate(self):
        self.item['displayname']='Renamed Blade';n.write(self.source/'OLD_BLADE.json',self.item)
        report=self.run_worker(client=FakeClient());change=report['changes']['items'][0]
        self.assertEqual('人工旧刀刃',change['previous_chinese_name']);self.assertEqual('更名刀刃',change['candidate_chinese_name'])
        self.assertEqual('人工旧刀刃',n.read(self.baseline/'translations/items/OLD_BLADE.json')['name'])
        self.assertEqual('OLD_BLADE',report['compatibility_notes'][0]['id'])

    def test_bad_model_output_blocks_packaging_and_can_retry(self):
        self.add_new();bad=FakeClient(bad=True);report=self.run_worker(client=bad)
        self.assertEqual('incomplete',report['status']);self.assertTrue(report['failed'])
        self.assertFalse(list((self.root/'pending').glob('*.zip')))
        calls=bad.calls;self.assertEqual('incomplete',self.run_worker(client=bad)['status']);self.assertEqual(calls,bad.calls)
        self.config['retry_rejected']=True
        self.assertEqual('pending_review',self.run_worker(client=FakeClient())['status'])

    def test_identifiers_numbers_and_english_are_validated(self):
        n.bulk.POLICY=self.rules
        job=n.unit(['Titanium Drill DR-X655'],'name','Drill')
        self.assertTrue(n.validate_result(job,{'text':'钛钻头 迪尔艾克斯{{N0}}'},self.rules))
        self.assertFalse(n.validate_result(job,{'text':'钛钻头 DR-X{{N0}}'},self.rules))
        job=n.unit(['Deals 20 damage.'],'lore','Blade')
        for text in ['造成伤害.','Deals {{N0}} damage.','造成 {{N0}} {{N0}} 点伤害.','造成 {{Q0}} 点伤害.']:
            self.assertTrue(n.validate_result(job,{'text':text},self.rules),text)

    def test_quarantined_item_does_not_starve_later_changes(self):
        self.add_new();self.item['lore']=['Damage: +20'];n.write(self.source/'OLD_BLADE.json',self.item)
        self.config['max_items_per_run']=1
        self.assertEqual(1,len(self.run_worker(client=FakeClient(bad=True))['remaining']))
        report=self.run_worker(client=FakeClient())
        self.assertEqual('incomplete',report['status'])
        self.assertEqual([],report['remaining'])
        self.assertEqual('OLD_BLADE',report['changes']['items'][1]['candidate_dictionary']['id'])

    def test_daily_request_limit_persists_across_worker_instances(self):
        client=FakeClient();first=self.translator(client,daily=1)
        first.translate(n.unit(['New Blade'],'name','new'),{})
        second=self.translator(client,daily=1)
        with self.assertRaises(n.ApiError):second.translate(n.unit(['Deals 12 damage.'],'lore','new'),{})
        self.assertEqual(1,client.calls)

    def test_colored_words_require_style_anchors_but_numbers_do_not(self):
        n.bulk.POLICY=self.rules
        job=n.unit(['§7Grants §cStrength §7for §a10 §7seconds.'],'lore','Blade')
        result={'text':'获得力量,持续 {{N0}} 秒.','styleMappings':{}}
        self.assertTrue(any('style mapping' in e for e in n.validate_result(job,result,self.rules)))
        result['styleMappings']={'Strength':'力量'}
        self.assertFalse(n.validate_result(job,result,self.rules))

    def test_numeric_style_anchors_are_redundant_and_removed(self):
        job=n.unit(['§7Deals §c12 §7damage.'],'lore','Blade')
        result=n.clean_result(job,{'text':'造成 {{N0}} 点伤害.','styleMappings':{'12':'12','damage':'伤害'}},{})
        self.assertEqual({'damage':'伤害'},result['styleMappings'])

    def test_model_cache_is_revalidated_after_policy_change(self):
        client=FakeClient();first=self.translator(client);job=n.unit(['New Blade'],'name','new')
        first.translate(job,{});first.translate(job,{});self.assertEqual(1,client.calls)
        self.rules['version']='changed';self.translator(client).translate(job,{})
        self.assertEqual(2,client.calls)

    def test_wrapping_reuses_template_with_new_line_boundaries(self):
        old={'loreParagraphs':[{'source':['Deals {amount} damage.'],'translation':'造成 {amount} 点伤害.','styleMappings':{}}]}
        result=n.reusable(n.unit(['Deals 30','damage.'],'lore','Blade'),old)
        self.assertEqual('造成 {{N0}} 点伤害.',result['text'])

    def test_removed_items_are_not_deleted_from_candidate(self):
        (self.source/'OLD_BLADE.json').unlink();self.add_new()
        report=self.run_worker(client=FakeClient());self.assertEqual(['OLD_BLADE'],report['changes']['removed'])
        _,items,_=n.load_payload(self.root/'pending'/report['archive']);self.assertIn('OLD_BLADE',items)

    def test_baseline_hash_tampering_or_traversal_is_rejected(self):
        self.add_new();report=self.run_worker(client=FakeClient());original=self.root/'pending'/report['archive']
        for extra in ['../escape.json','translations/global.json']:
            target=self.root/('bad-'+str(len(extra))+'.zip')
            with zipfile.ZipFile(original) as source,zipfile.ZipFile(target,'w') as dest:
                for name in source.namelist():dest.writestr(name,source.read(name))
                dest.writestr(extra,b'{}')
            with self.assertRaises(ValueError):n.load_payload(target)

    def test_date_sequence_is_numeric_and_existing_zip_not_overwritten(self):
        out=self.root/'out';out.mkdir();day=n.datetime.now(n.timezone(n.timedelta(hours=8))).strftime('%Y%m%d')
        for seq in [2,9,10]:(out/(day+'_'+str(seq)+'.zip')).write_bytes(b'x')
        self.assertEqual(day+'_11',n.next_version(out))
        with self.assertRaises(ValueError):n.package({},out,day+'_10',{})
        with self.assertRaises(ValueError):n.version_key('20260230_1')

    def test_two_workers_cannot_share_a_state_lock(self):
        with n.locked(self.root/'lock'):
            with self.assertRaises(OSError):
                with n.locked(self.root/'lock'):pass

    def test_remote_snapshot_pins_commit_and_only_retains_display_fields(self):
        raw=io.BytesIO();sha='a'*40
        with zipfile.ZipFile(raw,'w') as z:z.writestr('repo/items/OLD.json',json.dumps(self.item|{'nbttag':'do not send','recipe':{'x':'y'}}))
        with patch.object(n,'fetch',side_effect=[json.dumps({'sha':sha}).encode(),raw.getvalue()]) as fetch:
            revision,rows=n.source_snapshot(self.root/'state')
            self.assertEqual(sha,revision);self.assertEqual({'id','displayname','lore'},set(rows['OLD_BLADE']))
            self.assertTrue(fetch.call_args_list[1].args[0].endswith(sha))


if __name__=='__main__':unittest.main()
