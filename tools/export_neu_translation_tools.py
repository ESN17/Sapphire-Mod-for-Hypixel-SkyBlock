"""Export reviewed terminology and a self-contained, credential-free NEU worker bundle."""
from pathlib import Path
import json
import zipfile
from neu_auto_translate import ROOT,load_payload,encoded

def main():
    payload,items,_=load_payload(ROOT/'src/main/resources/defaults')
    policy=json.loads((ROOT/'tools/translation_policy.json').read_text(encoding='utf8'))
    policy['version']='2026-10-31-neu-incremental-review'
    policy['terms'].update({'Bits':'比特','Pristine':'提纯','Gabagool':'加巴古尔','Sea Lumies':'海泡菜',
                           'Mangrove Log':'红树原木','Motes':'裂隙尘埃','Mote':'裂隙尘埃',
                           'Enigma Souls':'恩尼格玛之魂',"Enigma's Cloak":'恩尼格玛的斗篷'})
    policy['locations']=json.loads(payload['translations/locations.json'])
    policy['reference_item_names']={v['_original_name']:v['name'] for _,v in items.values() if v.get('_original_name') and v.get('name')}
    policy['allowed_english']=['Abiphone','AbingoPhone','Hypixel','SkyBlock','NPC','Kat','Goldor','Storm','Maxor',
        'Maddox','Aatrox','Barry','Diana','Paul','Cole','Marina','Foxy','Diaz','Finnegan','Scorpius','Derpy',
        'Jerry','Shen','Elizabeth','Dante','Duncan','Ophelia','Mort','Zog','George','Rosetta','Sirius',
        'Dusk','Oringo','Jyrre','Sam','Rhys','Tia','Melody','Romero','Juliette','Rick','Elle',
        'Dean','Enigma','Montezuma','Taylor','Tom','Alice','John','Jake','Dahlia','Anita','Jacob']
    policy['rules'] += [
        '只根据 NEU 物品内部 ID 比较 displayname 和 lore；配方、NBT、排序等变化不触发翻译。',
        '已有人工译文优先；不在源工程或基准库内覆盖。新描述以新增匹配规则补充，改名仅写入待审核候选库。',
        '普通英文必须翻译；未登记的 NPC 名字进入人工处理，不自动把所有英文当成专名。',
        'reference_item_names 为导出时参考；运行时以当前基准库的已审物品名为准。',
        '缺失或未通过校验的条目阻止整库打包；缓存合格译文，重试不重复付费。',
        'NEU 删除条目只出报告，不自动删除旧译文；生成的 ZIP 必须由人工审核后再上传。'
    ]
    policy['workflow']={'publish':False,'output':'full_review_zip','comparison':['internalname','displayname','lore'],
                        'review_fields':['English before/after','Chinese before/after','rules','API usage'],
                        'preserve_existing':True,'new_npc_requires_exception':True}
    (ROOT/'tools/sapphire-translation-rules.json').write_bytes(encoded(policy))
    dest=ROOT/'exports/neu-auto-translation';dest.mkdir(parents=True,exist_ok=True)
    (dest/'sapphire-translation-rules.json').write_bytes(encoded(policy))
    config=json.loads((ROOT/'tools/neu-auto-config.json').read_text(encoding='utf8'))
    config.update(baseline='baseline',state_directory='state',output_directory='pending-review')
    script_names=['neu_auto_translate.py','bulk_translate.py','dictionary_tool.py','deepseek_client.py','translation_policy.json','test_neu_auto_translate.py']
    instructions=(ROOT/'tools/neu-auto-README.txt').read_bytes()
    (dest/'使用说明.txt').write_bytes(instructions)
    with zipfile.ZipFile(dest/'Sapphire-NEU-自动翻译工具.zip','w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for name in script_names:z.writestr('tools/'+name,(ROOT/'tools'/name).read_bytes())
        z.writestr('tools/sapphire-translation-rules.json',encoded(policy))
        z.writestr('tools/neu-auto-config.json',encoded(config))
        z.writestr('deepseek.example.json',(ROOT/'deepseek.example.json').read_bytes())
        z.writestr('使用说明.txt',instructions)
        for name,raw in payload.items():z.writestr('baseline/'+name,raw)
    print(json.dumps({'directory':str(dest),'canonical_item_names':len(policy['reference_item_names']),
                      'locations':len(policy['locations']),'translation_files':len(payload)-5},ensure_ascii=False))

if __name__=='__main__':main()
