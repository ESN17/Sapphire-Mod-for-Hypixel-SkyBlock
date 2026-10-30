"""Small human-reviewed corrections and whitespace-only API anchor repairs."""
import bulk_translate as bulk


def main():
    plan=bulk.read(bulk.STATE/'plan.json');count=0
    rejected=bulk.read(bulk.STATE/'style-repair-errors.json')
    for key in rejected:
        path=bulk.STATE/'style-rejected'/(key+'.json')
        if not path.exists():continue
        row=bulk.read(path);result=bulk.punctuation(plan['jobs'][key]['source'],row['result'])
        if not bulk.validate(plan['jobs'][key],result):
            bulk.cache_result(key,result,'reviewed-anchor-whitespace');count+=1
    fixes={
        '9bb08d93129a05d4ffc88873':('可在 Maddox 不忙时呼叫他.',{'Maddox':'Maddox'}),
        '4caaca0c80ff7ba376b925e5':('虽已失去,却从未被遗忘.这是 Einary 最喜欢的连帽衫.穿上它,或许能感受到他留下的爱与勇气.别告诉他你穿过它!',{"Einary's":'Einary'}),
        '58fd41e668178cc4b4795f73':('秩序 建立有序的世界.',{'Order':'秩序'}),
        'ec0a0c5682d0f4df6062b119':('自由 摆脱恐惧.',{'Freedom':'自由'}),
    }
    for key,(text,maps) in fixes.items():
        result={'text':text,'styleMappings':maps};errors=bulk.validate(plan['jobs'][key],result)
        if errors:raise ValueError((key,errors))
        bulk.cache_result(key,result,'manual-review');count+=1
    def patch(key, text=None, maps=None):
        nonlocal count
        value=bulk.cached(key)
        if text is not None:value['text']=text
        if maps is not None:value['styleMappings']=maps
        errors=bulk.validate(plan['jobs'][key],value)
        if errors:raise ValueError((key,errors))
        bulk.cache_result(key,value,'manual-review');count+=1
    patch('c48239f9012d7dd8eb89285c','濒死时免于死亡,获得伤害免疫和 {{N0}}  力量,持续 {{N1}}s,并恢复全部生命值.冷却时间和力量加成随你的地牢技能等级提升.此技能仅在地下墓穴内生效!',
          {' Strength':' 力量','Strength':'力量','Dungeoneering Skill':'地牢技能','The Catacombs':'地下墓穴'})
    patch('3997d0d257c62330403ad3d9',maps={'Mage Reputation':'法师声望','Chickzilla':'Chickzilla','chickens':'只鸡'})
    patch('bd8e61974ac53f0db044512a',maps={'Farming Minions':'农业仆从','Farming Minion':'农业仆从'})
    patch('ec0a0c5682d0f4df6062b119',maps={'Freedom from fear.':'摆脱恐惧.','Freedom':'自由'})
    patch('47f69e727f84950d3ca4237e','感谢这精彩的 {{N0}} 年,以及更长远的未来!',{'years':'年'})
    patch('513f765d5b78ee00f17a1f00','消耗以打开内含 {{N0}}x 冰寒 V 附魔书的礼包.',{'Ice Cold V Book':'冰寒 V 附魔书'})
    patch('d394896bc3fc8d545b92ef44','消耗以打开内含 {{N0}}x 力量 VII 附魔书的礼包.',{'Power VII Book':'力量 VII 附魔书'})
    patch('563102d3e7843e4216ef3241',maps={'blocks':'格'})
    patch('5526bca5c2f8e1169d28e9d9',maps={'Enemies':'敌人','Enemy':'敌人','Magic Find':'稀有物品概率加成'})
    patch('fbdf2217973a50de36f0a3ef','盛宴 Finnegan 将丰收宴升级为盛大宴会,使其持续整个 SkyBlock 年.同时开放大烘焙坊,可花费谷粒兑换更多专属物品.',
          {'Grand Feast ':'盛宴 ','Grand Feast':'盛大宴会','Harvest Feast':'丰收宴','Grand Bakery':'大烘焙坊','Kernels':'谷粒'})
    patch('68e13ecc182bbbb7e39f843c','盖亚构装体是一种神话生物,使用稀有或更高品质的狮鹫宠物即可遇到.它会造成真实击退,无视矮龟等抗击退效果',{'rare':'稀有','True Knockback':'真实击退'})
    patch('f3d4a795e269ceb088c166fb','在地下墓穴内,你的秘密发现数量每增加一位数,地牢物品基础属性提高 {{N0}}. (上限 {{N1}})',{'The Catacombs':'地下墓穴','(Max {{N1}})':'(上限 {{N1}})'})
    patch('0358405a89b29757cfb63788',maps={' Intelligence':' 智力',' Mining Speed':' 采矿速度','Intelligence':'智力','Mining Speed':'采矿速度'})
    patch('410f098df049fb9d53cff004','DDR 天启 生命值降至 {{N0}} 时 生成危险的火坑,你需要躲避.',{'DDR Apocalypse':'DDR 天启','Starting at {{N0}} HP':'生命值降至 {{N0}} 时'})
    for key in ['8c56830fc80249c9c5099c6e','9a161ffb5ec08884f8e30eec']:
        patch(key,maps={'Kat':'Kat','Pet Sitter':'宠物保姆','days' if key.startswith('8c') else 'day':'天'})
    patch('c4a30d1e200715c5300f0db7',maps={'Fishing Festival':'钓鱼节','first':'前'})
    patch('2ade45c23d063141eae5e31e','钓起并迎战危险的鲨鱼,赢取独特的海鲨战利品.',{'Sharks':'鲨鱼','Shark':'海鲨'})
    key='f37bfc3ebd5a974f737fc95b';tail=plan['jobs'][key]['source'].split('No Pet/',1)[1]
    patch(key,maps={'No Pet':'无宠物',tail:tail})
    patch('53078c29a41db488fb78139c',maps={'New Year Cakes':'新年蛋糕','New Year Cake':'新年蛋糕','Health':'生命值'})
    patch('87a566e6de9bb08cf531df61',maps={'pelt':'张兽皮'})
    patch('60a0e25e28e4a470f2d80862',maps={'meow':'喵喵叫'})
    patch('7695f223dd768c8798605fc0',maps={'original':'原','anvil':'铁砧'})
    patch('4f5721f1f44d4c6f93524504','穿戴时,你的攻击有概率对附近敌人造成 {{N0}} 伤害加上 {{N1}} 的力量伤害!',{'{{N0}} damage plus {{N1}}':'{{N0}} 伤害加上 {{N1}}'})
    patch('bbf8779df06406053a77bed8',maps={'Thunder':'雷'})
    patch('0b82a9624da7d77b5133d6f4','该物品的功能已并入铁砧系统.使用铁砧应用洗练石和其他物品!',{'Anvils':'铁砧系统','Anvil':'铁砧','Reforge Stones':'洗练石'})
    patch('0b9a010c83e8a2a47b7f7822',maps={'Sin':'罪'})
    patch('40ff0f8209b1cc311d1b7d36',maps={'Healer':'治疗者','Mending':'治疗'})
    patch('16650d4a6d39ae3c7a45c5bc','可移除当前宠物装备的宠物道具.卸下的宠物物品会退还给你.',{'Pet Items':'宠物道具','Pet Item':'宠物物品','Pet':'宠物'})
    patch('54fa9d036f28baa2b38041b8',maps={'Vitality':'生命恢复','Speed':'速度'})
    patch('4acf9140552537e9d1ff411c',maps={'Abiphone':'Abiphone','Abiphones':'Abiphone'})
    patch('457391c632ee803fbe7870d6','被至少一支箭命中的怪物会被减速,并在 {{N1}}s 内受到 {{N0}} 伤害/s.',{'damage':'伤害'})
    patch('4f16b4821a72b3e35cbbc73a',maps={'votes':'票','Mayor Elections':'市长选举'})
    patch('01abfd371ae5aed925b58974',maps={'Zombies killed':'只僵尸','Health':'生命','Max':'上限'})
    patch('296c3ca1348a7505cd13e8dc',maps={'Zombies killed':'只僵尸','Strength':'力量'})
    for key in ['f24a37353efd029b417a95c9','ac646b4284f60e7abef286b3','6fd0155362b8435d90806e36']:
        value=bulk.cached(key)
        patch(key,maps={a:b for a,b in value['styleMappings'].items() if not bulk.SLOT.search(a) or len(bulk.SLOT.sub('',a))>=2})
    patch('40ff0f8209b1cc311d1b7d36','治疗者: {{N0}} 治疗量与链接玩家和',{'Healer':'治疗者','Mending':'治疗量'})
    names=bulk.read(bulk.BASE/'tools/reviewed_item_names.json')
    for item in plan['items']:
        if item['id'] not in names:continue
        text=names[item['id']]
        for index,number in enumerate(item['name']['values']):text=text.replace(number,'{{N'+str(index)+'}}')
        patch(item['name']['job'],text,{})
    for key,job in plan['jobs'].items():
        if job['source']=='First Strike, Triple-Strike, and Combo are {{N0}}% more effective.':
            patch(key,'先攻,三重打击和连击附魔的效果提高 {{N0}}%.',{'Combo':'连击'})
    print('Reviewed exceptional units:',count,flush=True)


if __name__=='__main__':main()
