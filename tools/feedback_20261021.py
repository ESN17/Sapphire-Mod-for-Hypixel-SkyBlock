"""Reviewed screenshot fixes; preserve existing item dictionaries and prior upgrade hashes."""
import json, hashlib, zipfile, shutil
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'src/main/resources/defaults'
STATE=ROOT/'translation-state/feedback-20261021'
STATE.mkdir(parents=True,exist_ok=True)
def read(p): return json.loads(p.read_text(encoding='utf-8-sig'))
def write(p,data):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

# Keep all known shipped hashes so an untouched installation upgrades across
# multiple releases. Never identify a user-edited file by its filename alone.
hashes=read(BASE/'upgrade-hashes.json')
jar=STATE/'sapphire-1.0.0.jar'
if not jar.exists(): shutil.copy2(ROOT/'build/libs/sapphire-1.0.0.jar',jar)
with zipfile.ZipFile(jar) as z:
    for name in z.namelist():
        if name.startswith('defaults/') and not name.endswith('/'):
            file=name.removeprefix('defaults/')
            prior=set(hashes.get(file,'').split(','))-{''}
            prior.add(hashlib.sha256(z.read(name)).hexdigest())
            hashes[file]=','.join(sorted(prior))
write(BASE/'upgrade-hashes.json',hashes)

changes=[]
def save(name,data):
    for root in [BASE,ROOT/'dictionary-workspace',ROOT/'run/config/sapphire']:
        write(root/'translations'/name,data)
tab=read(BASE/'translations/tablist.json')
tab.update({'Bits:':'比特:','Bits':'比特','Mayor:':'市长:','Year:':'年份:','Ends In:':'距结束:','Attack Speed:':'攻击速度:',
    'Active Effects':'生效中的效果','Cookie Buff':'饼干增益','Upgrades':'升级项目',
    'The Great Spook':'惊魂万圣节','Enderman':'末影人'})
# Reuse reviewed pet names, without changing profile names or NPC names.
import re
for p in (BASE/'translations/items').glob('*.json'):
    item=read(p)
    m=re.fullmatch(r'\[Lvl \{LVL\}\] (.+)',item.get('_original_name',''))
    if m:
        zh=re.sub(r'^\[[^]]+\]\s*','',item.get('name',''))
        if zh and not re.search(r'[A-Za-z{}]',zh):tab.setdefault(m[1],zh)
save('tablist.json',tab)
score=read(BASE/'translations/scoreboard.json')
score.update({'Purse:':'钱袋:','Piggy:':'存钱罐:','Bits:':'比特:','Copper:':'铜币:'})
save('scoreboard.json',score)
events=read(BASE/'translations/mining-events.json');events['The Great Spook']='惊魂万圣节';save('mining-events.json',events)
sys=read(BASE/'translations/system-messages.json')
def rule(source,target,scope='tablist',styles=None):
    row={'source':source,'translation':target,'scopes':[scope],'styleMappings':styles or {}}
    old=next((r for r in sys['rules'] if r['source']==source and scope in r['scopes']),None)
    if old:
        if old['translation']!=target:changes.append({'source':source,'before':old['translation'],'after':target})
        old.update(row)
    else:sys['rules'].append(row)

rule('You are playing on MC.HYPIXEL.NET','你正在 MC.HYPIXEL.NET 游玩')
rule('SB Level: [{level}] {value}/{max} XP','空岛等级: [{level}] {value}/{max} 经验',styles={'SB Level':'空岛等级','XP':'经验'})
rule('Interest: {value} Hours ({market})','利息: {value} 小时后发放 ({market})',styles={'Interest':'利息','Hours':'小时后发放'})
rule('Election: Over!','选举: 已结束!',styles={'Election':'选举','Over!':'已结束!'})
rule('Mayor: {player}','市长: {player}')
rule('Year: {value}','年份: {value}')
rule('Ends In: {value}d','距结束: {value}天',styles={'Ends In':'距结束','d':'天'})
rule('[Lvl {level}] {name}','[{level}级] {name}',styles={'Lvl':'级'})
rule('{value}/{max} XP ({percent})','{value}/{max} 经验 ({percent})',styles={'XP':'经验'})
for skill in ['Farming','Mining','Combat','Foraging','Fishing','Enchanting','Alchemy','Carpentry','Runecrafting','Taming','Hunting']:
    rule(skill+' {level}: {percent}',tab[skill+':'][:-1]+' {level}: {percent}')
for en,zh in {
    'No effects active. Drink Potions or splash them on the ground to buff yourself!':'当前没有生效中的效果。饮用药水或将喷溅药水扔到脚下，即可获得增益！',
    'No effects active. Drink Potions or splash them on the':'当前没有生效中的效果。饮用药水或将喷溅药水',
    'ground to buff yourself!':'扔到脚下，即可获得增益！',
    'Not active! Obtain booster cookies from the community shop in the hub.':'尚未激活！可在中心岛的社区商店购买增益饼干。',
    'Not active! Obtain booster cookies from the community':'尚未激活！可在中心岛的社区商店',
    'shop in the hub.':'购买增益饼干。',
    'Ranks, Boosters & MORE! STORE.HYPIXEL.NET':'会员、增益道具等尽在 STORE.HYPIXEL.NET',
}.items():rule(en,zh)
for en,zh in [('Coins Allowance','硬币津贴'),('Accessory Bag','饰品袋')]:
    rule(en+' {roman} {value} Days',zh+' {roman} 剩余 {value} 天',styles={en:zh,'Days':'天'})

rule('BIN Auction started for {name}!','已为 {name} 创建一口价拍卖！','chat',{'BIN Auction started for':'已为'})
rule('Auction started for {name}!','已为 {name} 创建拍卖！','chat')
rule('You collected {coins} coins from selling {name} to {player} in an auction!',
     '你领取了拍卖 {name} 所得的 {coins} 硬币（买家：{player}）！','chat',{'coins':'硬币'})
for r in sys['rules']:
    if 'chat' not in r['scopes']:continue
    s=r['source'];old=r['translation'];new=old
    if s.startswith('[Bazaar] Claimed {count}x'):
        new='[集市] 已领取 {count} 个 {name}，总价 {market} 硬币（买入单价 {marketBuy} 硬币）！'
    elif s.startswith('[Bazaar] Claimed {market} coins'):
        new='[集市] 已领取出售 {count} 个 {name} 所得的 {market} 硬币（卖出单价 {marketSell} 硬币）！'
    if new!=old:r['translation']=new;changes.append({'source':s,'before':old,'after':new})
    if s.startswith('[Bazaar] Claimed'):r.setdefault('styleMappings',{})['x']='个'
save('system-messages.json',sys)

menus=read(BASE/'translations/gui-menus.json')
reword={
 'You don\'t have any ongoing orders for these products.':'你目前没有这些商品的未完成订单。',
 'To At what price are you selling?':'返回出售价格设置',
 'To Auctions Browser':'返回拍卖列表','To Auction Browser':'返回拍卖列表',
 'Auctions Browser':'拍卖列表','Auction Browser':'拍卖列表',
 'BIN Auctions are simple.':'一口价拍卖无需竞价。',
 '(BIN means Buy It Now)':'（即按标价立即购买）',
 'If you do not win, you can claim your bid coins back.':'如果未能拍得物品，可以领回出价时支付的硬币。',
 'Order filled!':'订单已全部成交！',
 '[Bazaar] Executing instant buy...':'[集市] 正在买入物品...',
 '[Bazaar] Executing instant sell...':'[集市] 正在卖出物品...',
 '[Bazaar] Claiming order...':'[集市] 正在领取订单所得...',
 'Auctions created: {value}':'已创建拍卖: {value}',
 'Auctions completed with bids: {value}':'成功成交的拍卖: {value}',
 'Auctions completed without bids: {value}':'无人出价的拍卖: {value}',
 'Auctions won: {value}':'竞拍成功次数: {value}',
 'Use the same price as the lowest Sell Offer for this item.':'按此物品当前最低卖价挂单。',
 'Use the same price as the highest Buy Order for this item.':'按此物品当前最高买价挂单。',
}
for menu in menus['menus']:
    for p in menu.get('loreParagraphs',[]):
        s=' '.join(p['source']);old=p['translation'];new=reword.get(s,old)
        if s.startswith('[Bazaar] Claimed {value}x'):
            new='[集市] 已领取 {value} 个 {name}，总价 {market} 硬币（买入单价 {marketBuy} 硬币）！'
        elif s.startswith('[Bazaar] Claimed {market} coins'):
            new='[集市] 已领取出售 {value} 个 {name} 所得的 {market} 硬币（卖出单价 {marketSell} 硬币）！'
        if new!=old:
            changes.append({'source':s,'before':old,'after':new});p['translation']=new
            # Remove only aliases no longer present after the reviewed rewrite.
            p['styleMappings']={a:b for a,b in p.get('styleMappings',{}).items() if b in new}
        if s.startswith('[Bazaar] Claimed'):p.setdefault('styleMappings',{})['x']='个'
    # Titles should agree with button labels.
    for source in menu.get('titles',{}):
        if source in reword:menu['titles'][source]=reword[source]
        menu['titles'][source]=menu['titles'][source].replace('拍卖浏览器','拍卖列表')
save('gui-menus.json',menus)
# Exact chat entries and system templates can precede the menu fallback.
chat=read(BASE/'translations/chat.json')
for source,target in list(chat.items()):
    replacement=reword.get(source,reword.get('[Bazaar] '+source))
    if replacement:
        if not source.startswith('[Bazaar] '):replacement=replacement.removeprefix('[集市] ')
        if replacement!=target:changes.append({'source':source,'before':target,'after':replacement});chat[source]=replacement
save('chat.json',chat)
for row in sys['rules']:
    source=row['source'];replacement=reword.get(source,reword.get('[Bazaar] '+source))
    if replacement and 'chat' in row['scopes']:
        if not source.startswith('[Bazaar] '):replacement=replacement.removeprefix('[集市] ')
        row['translation']=replacement
save('system-messages.json',sys)
review=STATE/'wording-review.json'
previous=read(review) if review.exists() else []
for change in changes:
    if change not in previous:previous.append(change)
write(review,previous)
print(f'Reviewed {len(changes)} wording changes; {len(sys["rules"])} system rules.')
