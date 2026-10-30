"""Reviewed screenshot UI paragraphs and scoped fishing notification variants."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'src/main/resources/defaults'
def read(p):return json.loads(p.read_text(encoding='utf8'))
def encoded(d):return (json.dumps(d,ensure_ascii=False,indent=2)+'\n').encode('utf8')
def main():
    hashes=read(BASE/'upgrade-hashes.json')
    def save(p,data):
        old=p.read_bytes();new=encoded(data)
        if old==new:return
        backup=ROOT/'translation-state/ui-feedback-20261022/before'/p.relative_to(ROOT)
        backup.parent.mkdir(parents=True,exist_ok=True)
        if not backup.exists():backup.write_bytes(old)
        if p.is_relative_to(BASE):
            key=p.relative_to(BASE).as_posix();prior=set(hashes.get(key,'').split(','))-{''};prior.add(hashlib.sha256(old).hexdigest());hashes[key]=','.join(sorted(prior))
        p.write_bytes(new)
    for root in [BASE,ROOT/'dictionary-workspace',ROOT/'run/config/sapphire']:
        p=root/'translations/gui-menus.json';data=read(p)
        auction=next(m for m in data['menus'] if m['id']=='auction')
        auction['titles'].setdefault('Co-op Auction House','合作拍卖行')
        common=data['loreParagraphs']
        def paragraph(source,target,styles=None):
            if not any(' '.join(r['source'])==source for r in common):
                common.append(dict(source=[source],translation=target,styleMappings=styles or {}))
        paragraph('Level up your pets faster by gaining XP in their favorite skill!','获取宠物擅长技能的经验,让它更快升级!',{'XP':'经验','favorite skill':'擅长技能'})
        paragraph('View and edit preset armor and equipment sets with other settings to make switching activities easy.','查看并编辑护甲、装备和其他设置的预设,方便切换不同活动.')
        paragraph('Stats that influence what you catch and how quickly you catch it while fishing.','这些属性会影响钓获的种类和钓鱼速度.')
        paragraph('Also accessible via /{text}.','也可使用 /{text} 打开.')
        paragraph('Also accessible via /{text}','也可使用 /{text} 打开')
        paragraph('You own {count} auctions in progress or which recently ended.','你有 {count} 场正在进行或刚结束的拍卖.',{'{count} auctions':'{count} 场'})
        paragraph('With {count} auctions from co-op!','另有合作成员的 {count} 场拍卖!')
        for verb in ('auction has','auctions have'):
            paragraph('Your '+verb+' {count} bids totaling {value} coins.','你的拍卖共收到 {count} 次出价,总计 {value} 硬币.',{'{count} bids':'{count} 次出价','{value} coins':'{value} 硬币'})
        labels={'Fishing Stats':'钓鱼属性','Fishing Speed':'钓鱼速度','Sea Creature Chance':'海洋生物概率','Trophy Chance':'奖杯概率','Treasure Chance':'宝藏概率','Double Hook Chance':'双钩概率'}
        for en,zh in labels.items():
            paragraph(en,zh)
            if en!='Fishing Stats':paragraph('{icon} '+en+' {value}','{icon} '+zh+' {value}',{en:zh})
        save(p,data)
        p=root/'translations/system-messages.json';data=read(p)
        def rule(source,target,styles,scope='chat'):
            if not any(r['source']==source and scope in r['scopes'] for r in data['rules']):
                data['rules'].append(dict(source=source,translation=target,scopes=[scope],styleMappings=styles))
        rule('Selected pet: {name}','当前宠物: {name}',{'Selected pet':'当前宠物'},'gui-tooltip')
        # Fixed qualities preserve their own colors; the name capture resolves
        # through known NEU trophy names, never through a player-name dictionary.
        for quality,zh in {'BRONZE':'青铜','SILVER':'白银','GOLD':'黄金','DIAMOND':'钻石'}.items():
            for heading,label in {'TROPHY FISH':'奖杯鱼','TROPHY FROG':'奖杯青蛙','TROPHY':'奖杯渔获'}.items():
                for icon in ('','{icon} '):
                    for article in ('','a ','an '):
                        rule(icon+heading+'! You caught '+article+'{name} '+quality+'!',icon+label+'! 你钓到了 {name} '+zh+'!',{heading:label,'You caught '+article.rstrip():'你钓到了',quality:zh})
            rule('NEW DISCOVERY: {name} '+quality,'新发现: {name} '+zh,{'NEW DISCOVERY':'新发现',quality:zh})
        for heading,label in {'GOOD CATCH':'不错的收获','GREAT CATCH':'丰厚的收获','OUTSTANDING CATCH':'绝佳的收获'}.items():
            for icon in ('','{icon} '):
                for article in ('','a ','an '):
                    rule(icon+heading+'! You caught '+article+'{name}!',icon+label+'! 你钓到了 {name}!',{heading:label,'You caught '+article.rstrip():'你钓到了'})
                rule(icon+heading+'! You caught {count} Coins!',icon+label+'! 你钓到了 {count} 硬币!',{heading:label,'You caught':'你钓到了','Coins':'硬币'})
        save(p,data)
    (BASE/'upgrade-hashes.json').write_bytes(encoded(hashes))
    print('Added reviewed auction, SkyBlock menu, fishing stats and trophy catch rules to all roots.')
if __name__=='__main__':main()
