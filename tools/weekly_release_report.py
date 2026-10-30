"""Record reproducible validation and artifact facts; never read API credentials."""
import collections,hashlib,json,re,xml.etree.ElementTree as ET,zipfile
from dictionary_tool import read,write
from refine_weekly import STATE,BASE

tests=collections.Counter()
for path in (BASE/'build/test-results/test').glob('TEST-*.xml'):
    root=ET.parse(path).getroot()
    for field in ['tests','failures','errors','skipped']:tests[field]+=int(root.get(field,'0'))
assert tests['tests'] and tests['failures']==tests['errors']==0,tests
quality=read(STATE/'quality-report.json');assert not quality['missing'] and not quality['failed']
assert not read(STATE/'style-failures.json')
assert not read(BASE/'build/weekly-audit/effect-gaps.json')
smokes={
    'client':('build/sapphire-release-smoke-20261021/logs/latest.log','SAPPHIRE_SMOKE_OK'),
    'editor_ui':('build/sapphire-release-ui-20261021/logs/latest.log','SAPPHIRE_API_UI_SMOKE_OK')
}
for path,marker in smokes.values():assert marker in (BASE/path).read_text(encoding='utf-8'),path
jar=BASE/'build/libs/sapphire-1.0.0.jar';secrets=[]
with zipfile.ZipFile(jar) as z:
    item_count=len([n for n in z.namelist() if n.startswith('defaults/translations/items/') and n.endswith('.json')])
    assert item_count==8937
    for name in z.namelist():
        if name.endswith('/') :continue
        if re.search(rb'sk-[a-f0-9]{32,}',z.read(name)):secrets.append(name)
    assert not secrets,'Credential-like data found in jar'
usage=collections.Counter()
for line in (STATE/'refine-usage.jsonl').read_text().splitlines():
    for k,v in json.loads(line).items():
        if isinstance(v,int):usage[k]+=v
report={
    'date':'2026-10-21',
    'features':{
        'scoreboard':'复用 344 条地名词典，处理额外图标、不可见字符和大小写；保留时间原样',
        'auction_and_bazaar':'补充成交、出价、领取、托管、取消、限制与失败提示；保留玩家名、金额和交互样式',
        'in_game_editor':'/sapphire → 修改翻译，或 /sapphire edit；搜索、修改、新建、保存重载与自动备份',
        'mining_events':'矮人矿洞、水晶洞窟/核心、冰晶隧道的活动、委托、寒冷、尸体和奖励提示',
        'tablist':'渲染时翻译行、页眉和页脚；跳过真实玩家名',
        'pet_items':'携带物品标题、名称与效果，复用物品 ID 对应词典',
        'enchantments':'修复效果行被误识别为附魔标题的问题，覆盖 763 级附魔效果样例',
        'deepseek_revision':'以既有中文为基础校对全部 8937 项物品，16851 个去重名称/说明单元；保留 NPC 名、命令、网址及数值记号'
    },
    'coverage':{'items':item_count,'revised_units':quality['total_units'],'system_rules':890,'tab_labels':100,'locations':344,'enchantment_level_fixtures':763,'pet_item_fixtures':42},
    'neu':read(STATE/'neu-comparison.json'),
    'validation':{'tests':dict(tests),'style_audit_failures':0,'content_audit_failures':0,'effect_fixture_gaps':0,'credential_scan_matches':0,'offline_smokes':{key:path for key,(path,marker) in smokes.items()}},
    'api_usage_tokens':dict(usage),
    'artifact':{'path':str(jar),'bytes':jar.stat().st_size,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest()},
    'sources':[
        'https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/tree/20a302457375ddfd7005e192486ac86781729b0a',
        'https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/features/chat/ChatFilter.kt',
        'https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/features/chat/ShortenCoins.kt',
        'https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/features/inventory/bazaar/BazaarOrderApi.kt',
        'https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/features/mining/eventtracker/MiningEventTracker.kt',
        'https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/data/model/TabWidget.kt'
    ],
    'limits':['系统提示覆盖已查到的公开句式及兼容变体，不等于已进入服务器逐条触发验证。','审计中 50 条自定义旧模板缺少对应的公开彩色原文，使用纯文本校验；其余按现有 NEU 彩色样例验证。','玩家自己修改过的旧词典不会被自动升级覆盖。']
}
write(BASE/'docs/weekly-translation-report.json',report)
print(json.dumps({'tests':dict(tests),'items':item_count,'jar_bytes':jar.stat().st_size,'credential_matches':0}))
