"""Screenshot-driven visitor, Fire Sale and Garden notification additions."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'src/main/resources/defaults'
key='translations/system-messages.json';path=BASE/key;before=path.read_bytes();doc=json.loads(before)
for row in doc['rules']:
    if row['source'].startswith('[SkyBlock] {player} is visiting '):
        row['source']=row['source'].replace('{player}','{rankedPlayer}')
        row['translation']=row['translation'].replace('{player}','{rankedPlayer}')
sources={r['source'] for r in doc['rules']}
def add(source,target,styles=None):
    if source in sources:return
    sources.add(source);doc['rules'].append({'source':source,'translation':target,'scopes':['chat'],'styleMappings':styles or {},'sourceAttribution':{'source':'User screenshots, 2026-11-02; numbers are variable captures'}})
for prefix,suffix in [('', ''),('{icon} ',''),('{icon} ',' {iconEnd}')]:
    add(prefix+'FIRE SALE'+suffix,prefix+'限时特卖'+suffix,{'FIRE SALE':'限时特卖'})
for prefix in ('','{icon} '):
    add(prefix+'Selling an item for a limited time!',prefix+'限时出售商品！',{'Selling an item for a limited time':'限时出售商品'})
    for en,zh in [('{days}d {hours}h {minutes}m','{days}天 {hours}小时 {minutes}分'),('{hours}h {minutes}m','{hours}小时 {minutes}分'),('{minutes}m {seconds}s','{minutes}分 {seconds}秒'),('{hours}h','{hours}小时'),('{minutes}m','{minutes}分'),('{seconds}s','{seconds}秒'),('{time}','{time}')]:
        add(prefix+'[WARP] To {player} in the next '+en+' to grab yours!',prefix+'[传送] 前往 {player} 购买，剩余时间：'+zh+'！',{'[WARP]':'[传送]','To':'前往','in the next':'购买，剩余时间：','to grab yours':'！'})
add('{icon} {fireSaleItem} ({count} left)','{icon} {fireSaleItem}（剩余 {count} 件）',{'left':'件'})
for head,zh in [('RARE DROP!','稀有掉落！'),('VERY RARE DROP!','非常稀有掉落！')]:
    add(head+' {name} ({statIcon})',zh+' {name}（{statIcon}）',{head:zh})
after=(json.dumps(doc,ensure_ascii=False,indent=2)+'\n').encode()
if before!=after:
    backup=ROOT/'translation-state/sale-garden-20261102/system-messages.json';backup.parent.mkdir(parents=True,exist_ok=True)
    if not backup.exists():backup.write_bytes(before)
    hashes_path=BASE/'upgrade-hashes.json';hashes=json.loads(hashes_path.read_text(encoding='utf-8'))
    hashes[key]=','.join(sorted(set(filter(None,hashes.get(key,'').split(',')))|{hashlib.sha256(before).hexdigest()}))
    path.write_bytes(after);hashes_path.write_text(json.dumps(hashes,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    for root in (ROOT/'dictionary-workspace',ROOT/'run/config/sapphire'):
        mirror=root/key
        if mirror.exists() and mirror.read_bytes()==before:mirror.write_bytes(after)
print('Visitor, Fire Sale and variable Garden drop rules ready')
