import unittest
import io
import json
import tempfile
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest.mock import patch
import translate_captures as capture
from translate_captures import chat_pattern


class ChatCapturePatternTest(unittest.TestCase):
    def test_ui_does_not_become_chat_or_skip_matching_chat_text(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            dictionary = root/'system-messages.json'
            dictionary.write_text(json.dumps({'rules':[{'source':'Open menu','translation':'打开','scopes':['chat']}]}),encoding='utf8')
            (root/'batch.txt').write_text('Open menu\n[Sapphire:UI:gui-button] §aOpen menu\n[Sapphire:UI:invalid] Ignore this',encoding='utf8')
            (root/'batch.jsonl').write_text(json.dumps({'scope':'gui-button','source':'§aOpen menu'}),encoding='utf8')
            with patch.object(capture,'TR',root), patch.object(capture,'SYSTEM',dictionary), patch.object(capture,'DeepSeek') as api, patch('sys.argv',['capture',directory]):
                api.return_value.complete.return_value=({'translations':[{'id':'0','text':'打开菜单'}]},None)
                capture.main()
                self.assertEqual(api.return_value.complete.call_args.args[1]['items'],[{'id':'0','scope':'gui-button','source':'Open menu'}])
                self.assertEqual(json.loads(dictionary.read_text(encoding='utf8'))['rules'][-1]['scopes'],['gui-button'])
    def test_other_surface_does_not_cover_chat_and_missing_api_row_blocks_write(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            dictionary = root / 'system-messages.json'
            baseline = json.dumps({'rules':[{'source':'Unseen notification!', 'translation':'测试', 'scopes':['tooltip']}]})
            dictionary.write_text(baseline,encoding='utf8')
            (root/'session.txt').write_text('Unseen notification!',encoding='utf8')
            with patch.object(capture,'TR',root), patch.object(capture,'SYSTEM',dictionary), patch.object(capture,'DeepSeek') as api, patch('sys.argv',['capture',directory]), redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()) as errors:
                api.return_value.complete.return_value=({'translations':[]},None)
                with self.assertRaises(SystemExit):
                    capture.main()
                self.assertIn('Unseen notification!',errors.getvalue())
                report=json.loads((root/'translation-failures.json').read_text(encoding='utf8'))
                self.assertEqual(report['failedTemplates'],['Unseen notification!'])
                self.assertEqual(dictionary.read_text(encoding='utf8'),baseline)
                api.return_value.complete.return_value=({'translations':[{'id':'0','text':'新的系统通知！'}]},None)
                capture.main()
                self.assertFalse((root/'translation-failures.json').exists())
                self.assertEqual(json.loads(dictionary.read_text(encoding='utf8'))['rules'][-1]['scopes'],['chat'])
    def test_review_all_items_with_longest_name_first(self):
        idx={'Enchanted':['Enchanted Hard Stone'], 'Stone':['Stone']}
        self.assertEqual(list(capture.item_slots("Trade Enchanted Hard Stone for Stone!",idx)),
                         [('Enchanted Hard Stone','Trade {name} for Stone!'),('Stone','Trade Enchanted Hard Stone for {name}!')])
    def test_numeric_slots_in_both_spellings(self):
        for token in ('{{N0}}', '{N0}'):
            pattern = chat_pattern('You gained ' + token + ' coins.')
            self.assertTrue(pattern.fullmatch('You gained 1,250 coins.'))
            self.assertFalse(pattern.fullmatch('You gained several coins.'))

    def test_reordered_icon_and_player_are_not_the_same_message(self):
        pattern = chat_pattern('Drop {rankedPlayer} {statIcon1}')
        self.assertTrue(pattern.fullmatch('Drop [MVP+] Alex +12% ✯'))
        self.assertFalse(pattern.fullmatch('Drop +12% ✯ Alex'))
