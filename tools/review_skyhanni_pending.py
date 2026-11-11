"""Reviewed translations for the strict formatting validation queue (9.3.0)."""
import json
from skyhanni_dictionary import STATE, export, dump, valid

TRANSLATIONS = [
    '禁止执行 /cf，除非 §6§l增益饼干 §7处于生效状态。',
    '禁止执行 /cf，除非 §d§l神话兔子宠物 §7已装备。',
    '禁止执行 /cf，除非 §9热巧克力混合剂 §7处于生效状态。',
    '购买 §2水晶洞窟通行证 §e，出售者为 §5Gwendolyn',
    '购买 §2水晶洞窟通行证 §e，出售者为 §5Gwendolyn§e！',
    '积攒超过 3m 储存的随机掉落经验',
    '说明此处存在的问题，例如：寻路穿墙、忽略明显的捷径，或 /shnavigate 中缺少 NPC、钓鱼热点或空岛区域名称。',
    '在巧克力工厂中存有 60 Billion 巧克力',
    '高亮树木周围飞舞的 §c蜻蜓 §7，需要手持 §e空蜻蜓瓶 §7。',
    '高亮 §c树上的幼虫 §7，需要手持 §e幼虫钩 §7。',
    '手持物品，或执行 /shdebugprice <物品名称或ID>',
    '挖掘 1 million 矿石',
    '显示你在集市中距离每日 15 billion 硬币交易上限的进度。\n单笔订单超出整数上限的硬币不计入该限额。',
    '当弹簧靴的蓄力足以到达阶段 1 的水晶位置时，播放声音并显示标题（地牢第 7 层首领战）。',
    '在一天内从 NPC 处偷取 500mil 硬币',
    '根据聊天记录统计向 NPC 售出物品所得的硬币，对照每日 500M 上限，并显示可移动的信息栏（格林尼治标准时间午夜重置）。',
    '§615.3M 硬币收入',
    '§647.2m 硬币支出',
    '§6Hoppity §7赠送了 §b3 §d只兔子 §7，地点为你的 §a花园§7。',
    '§7你购买了 §b7 §f只兔子 §7，出售者为 §aHoppity§7。',
    '§7你找到了 §b4 §6§l颗配菜蛋 §r§7，地点为 §6巧克力工厂§7。',
    '§7你找回了 §b20§7/§a40 §7颗错过的 §6餐食蛋 §7，提供者为 §cRabbit Hitman§7。',
    '§7你花费了 §b4h 36m §7，地点为 §6巧克力工厂§7。',
    '§c距离上次 §5极其稀有掉落的未出货次数',
    '已插入路径点 {sh0}，位置为 {sh1}。',
]

if __name__=='__main__':
    rows=json.loads((STATE/'pending.json').read_text(encoding='utf-8'))
    assert len(rows)==len(TRANSLATIONS), 'Review queue changed; inspect before applying'
    cache=json.loads((STATE/'translated.json').read_text(encoding='utf-8'))
    for row,target in zip(rows,TRANSLATIONS):
        assert valid(row['source'],target),row['source']
        cache[row['id']]=target
    dump(STATE/'manual-review.json',[{'source':r['source'],'translation':t} for r,t in zip(rows,TRANSLATIONS)])
    dump(STATE/'translated.json',cache)
    export(json.loads((STATE/'catalog.json').read_text(encoding='utf-8')),cache)
