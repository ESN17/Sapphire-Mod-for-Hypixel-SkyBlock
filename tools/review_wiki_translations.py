"""Apply reviewed wording/anchors only to the new Wiki translation cache."""
import translate_wiki_corpus as t

PATCHES = {
 'fb3b8bea13f7b79d6034456d1d0d': ('Barry 是个反跳跃者、偏执狂,违背我所有的价值观.', {'anti-':'反'}),
 '89ff8a38d64746ff2efde75b98b1': ('我的巧克力是全世界最上等的.', {'Chocolate':'巧克力','world':'世界'}),
 '1f0b5319d08b1fb6d4c3bda4252d': ('不管它属于哪个哥布林,那家伙的长相即便以同族的标准来看也相当丑陋.', {'Goblin':'哥布林','even for a Goblin.':'即便以同族的标准来看也相当丑陋.'}),
 '50ccfd5883738e05689f8b38d58c': ('这些我可帮不了你,但我能提高迈达斯武器上支付的硬币数量,让你发挥出它们最大的 ❁ 伤害加成.',
    {"Well, I can't help you do any of that, but I":'这些我可帮不了你,但我','can':'能','Coins':'硬币','Midas':'迈达斯','❁ Damage':'❁ 伤害',
     'so you can harness their maximum':'让你发挥出它们最大的'}),
 '74fd3ddb70b83d621abf7a6ba182': ('裂隙时间剩余不足 {{N0}}ф 时,你的风险会超过收益.', {'Rift Time':'裂隙时间'}),
 '43df7572284a12645e3412e86895': ('太好了!我担任这艘船只的船长,从维修到航行的一切事务都由我负责.', {'Ship':'船只'}),
 'b5d4853b440a8132d8fcbe116fb0': ('你可以把鱼饵存放在物品栏或钓鱼袋中,后者在生鳕鱼收藏 III 解锁.',
    {'Bait':'鱼饵','Fishing Bag':'钓鱼袋','Raw Cod Collection III':'生鳕鱼收藏 III'}),
 '9632031c219368bf1fe0e88e39fd': ('灵魂营火没有普通营火那么猛烈.你的 ❈ 防御对它有效!',
    {"The soul campfire isn't quite as strong as a regular":'灵魂营火没有普通','campfire':'营火','❈ Defense':'❈ 防御'})
}

def main():
    plan=t.read(t.STATE/'plan.json');t.configure_npcs(plan)
    for key,(text,styles) in PATCHES.items():
        value=dict(text=text,styleMappings=styles,origin='reviewed-wiki-phrasing')
        problems=t.validate(plan['jobs'][key],value)
        if problems:raise ValueError((key,problems))
        value['styleMappings']=t.mappings(plan['jobs'][key]['source'],text,styles)
        path=t.STATE/'cache'/(key+'.json');backup=t.STATE/'before-manual-review'/(key+'.json')
        if not backup.exists():t.write(backup,t.read(path))
        t.write(path,value)
    print('Reviewed',len(PATCHES),'Wiki translations')

if __name__=='__main__':main()
