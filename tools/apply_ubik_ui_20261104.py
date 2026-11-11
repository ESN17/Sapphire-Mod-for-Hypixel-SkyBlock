"""Add a scoped Split or Steal menu; preserve existing entries and local edits."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'src/main/resources/defaults'
STATE = ROOT / 'translation-state/ubik-ui-20261104'
key = 'translations/gui-menus.json'
path = BASE / key
before = path.read_bytes()
data = json.loads(before)
menu = next((m for m in data['menus'] if m['id'] == 'split-or-steal'), None)
if menu is None:
    menu = dict(id='split-or-steal', titles={'Split or Steal': '平分或独吞'}, labels={}, loreParagraphs=[])
    data['menus'].append(menu)

def add(en, zh, styles=None):
    if any(p['source'] == [en] for p in menu['loreParagraphs']):
        return
    menu['loreParagraphs'].append(dict(source=[en], translation=zh, styleMappings=styles or {}))

for en, zh in {
    'SPLIT': '平分', 'STEAL': '独吞', 'Split': '平分', 'Steal': '独吞',
    'Split or Steal': '平分或独吞', 'Your Choice': '你的选择',
    "Opponent's Choice": '对手的选择', 'Your Opponent': '你的对手',
    'Round Results': '回合结果', 'Game Over': '对局结束', 'Final Round': '最终回合',
    'Choose an option!': '请选择！', 'Make your choice!': '请作出选择！',
    'Click to split!': '点击选择平分！', 'Click to steal!': '点击选择独吞！',
    'Click to choose!': '点击选择！', 'Click to select!': '点击选择！',
    'Click to confirm!': '点击确认！', 'Waiting for your opponent...': '正在等待对手……',
    'Waiting for opponent...': '正在等待对手……', 'You': '你', 'Opponent': '对手',
    'Selected!': '已选择！', 'Not selected': '尚未选择', 'Confirmed!': '已确认！',
    'Click to play again!': '点击再玩一局！', 'Click to close!': '点击关闭！',
}.items():
    add(en, zh, {en: zh})

for en, zh in [('SPLIT', '平分'), ('STEAL', '独吞'), ('Split', '平分'), ('Steal', '独吞')]:
    for prefix, target in [('Your choice: ', '你的选择：'), ('Your Choice: ', '你的选择：'),
                           ("Opponent's choice: ", '对手的选择：'), ("Opponent's Choice: ", '对手的选择：')]:
        add(prefix + en, target + zh, {prefix.strip(): target, en: zh})

for en, zh in [
    ('Round {round}', '第 {round} 回合'), ('ROUND {round}', '第 {round} 回合'),
    ('Round {round} (FINAL)', '第 {round} 回合（最终回合）'),
    ('Round {round}/{total}', '第 {round}/{total} 回合'),
    ('Round: {round}', '回合：{round}'),
    ('Time Left: {time}', '剩余时间：{time}'), ('Time left: {time}', '剩余时间：{time}'),
    ('Time Left: {seconds}s', '剩余时间：{seconds}秒'),
    ('Opponent: {player}', '对手：{player}'),
    ('Your Total: {amount} Motes', '你的总收益：{amount} 裂隙尘埃'),
    ('Opponent Total: {amount} Motes', '对手总收益：{amount} 裂隙尘埃'),
    ('You earned {amount} Motes!', '你获得了 {amount} 裂隙尘埃！'),
    ('Your opponent earned {amount} Motes!', '你的对手获得了 {amount} 裂隙尘埃！'),
    ('{amount} Motes', '{amount} 裂隙尘埃'),
]:
    add(en, zh, {'Motes': '裂隙尘埃', '(FINAL)': '（最终回合）'})

# Existing reviewed chat rules also describe the round history shown in the GUI.
system = json.loads((BASE / 'translations/system-messages.json').read_bytes())
for row in system['rules']:
    if row['source'].startswith('ROUND {round}') or row['source'].startswith(('You earned {amount} ', 'Your opponent earned {amount} ')) and row['source'].endswith(' in this match!'):
        add(row['source'], row['translation'], row.get('styleMappings'))

after = (json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode()
if after != before:
    backup = STATE / key
    backup.parent.mkdir(parents=True, exist_ok=True)
    if not backup.exists():
        backup.write_bytes(before)
    hp = BASE / 'upgrade-hashes.json'
    hashes = json.loads(hp.read_bytes())
    hashes[key] = ','.join(sorted(set(filter(None, hashes.get(key, '').split(','))) | {hashlib.sha256(before).hexdigest()}))
    hp.write_text(json.dumps(hashes, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    path.write_bytes(after)
    for root in [ROOT / 'dictionary-workspace', ROOT / 'run/config/sapphire']:
        mirror = root / key
        if mirror.exists() and mirror.read_bytes() == before:
            mirror.write_bytes(after)
print(json.dumps({'menu': menu['id'], 'rules': len(menu['loreParagraphs'])}))
