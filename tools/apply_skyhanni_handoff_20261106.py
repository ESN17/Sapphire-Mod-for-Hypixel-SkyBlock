"""Add reviewed runtime labels from the supplied handoff, without replacing existing translations."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXACT = {
    "Attribute Shard Overlay": "属性碎片总览",
    "Active Attribute Levels": "当前属性等级",
    "Found Shards:": "已发现碎片：",
    "Maxed Shards:": "已满级碎片：",
    "Total Shard Levels:": "碎片总等级：",
    "Please open /am and turn on advanced mode.": "请打开 /am 并启用高级模式。",
    "No Shards Found": "未找到碎片",
    "Try changing your settings below.": "请尝试调整下方设置。",
    "Sorted By": "排序依据",
    "Price Source": "价格来源",
    "Price to Maxed": "升至满级所需费用",
    "Hide Maxed Shards": "隐藏已满级碎片",
    "Hide Maxed": "隐藏已满级",
    "Show Maxed": "显示已满级",
    "Only Not Unlocked": "仅显示未解锁",
    "Show All": "显示全部",
    "Include Hunting Box": "包括狩猎盒",
    "Exclude Hunting Box": "排除狩猎盒",
    "Include Bazaar Orders": "包括集市订单",
    "Exclude Bazaar Orders": "排除集市订单",
    "Only Current Inventory": "仅当前物品栏",
    "Show All Shards": "显示全部碎片",
    "Reset Hunting Box Shards": "重置狩猎盒碎片数据",
    "Reset hunting box shards data. Open the Hunting Box or click here to update the saved data.": "重置狩猎盒碎片数据。打开狩猎盒或点击此处更新已保存的数据。",
    "Shard Fusion": "碎片融合",
    "Confirm Fusion": "确认融合",
    "Attribute Shards": "属性碎片",
    "Attribute Shard Tracker": "属性碎片统计",
    "Overlay Price Source": "界面价格来源",
    "Change Price Source": "更改价格来源",
}
TEMPLATES = {
    "Found Shards: {sh0}/{sh1}": "已发现碎片：{sh0}/{sh1}",
    "Maxed Shards: {sh0}/{sh1}": "已满级碎片：{sh0}/{sh1}",
    "Total Shard Levels: {sh0}/{sh1}": "碎片总等级：{sh0}/{sh1}",
    "Missing shard data for {sh0} shard": "缺少 {sh0} 种碎片的数据",
    "Missing shard data for {sh0} shards": "缺少 {sh0} 种碎片的数据",
    "Total Price to Max All Shards: {sh0}": "所有碎片升至满级总价：{sh0}",
    "Total Price to Max Shown Shards: {sh0}": "当前显示碎片升至满级总价：{sh0}",
    "Current Tier: {sh0}": "当前等级：{sh0}",
    "Price per Shard: {sh0}": "每个碎片价格：{sh0}",
    "Amount in Hunting Box: {sh0}": "狩猎盒内数量：{sh0}",
    "Amount to Next Tier: {sh0}": "距下一级所需数量：{sh0}",
    "Price to Next Tier: {sh0}": "升至下一级费用：{sh0}",
    "Required to Fuse: {sh0}": "融合所需：{sh0}",
}

if __name__ == '__main__':
    for relative in ('compat/skyhanni-zh_cn.json', 'translations/compat-skyhanni-zh-cn.json'):
        path = ROOT/'src/main/resources/defaults'/relative
        if not path.exists():
            continue
        data = json.loads(path.read_text(encoding='utf8'))
        for en, zh in EXACT.items():
            data['translations'].setdefault(en, zh)
        rows = data.setdefault('templates', [])
        known = {row['source'] for row in rows}
        for en, zh in TEMPLATES.items():
            if en not in known:
                rows.append({'source': en, 'translation': zh})
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
        print(relative, len(data['translations']), len(rows))
