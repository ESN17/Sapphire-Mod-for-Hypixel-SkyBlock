"""Human-reviewed semantic/style corrections to the completed DeepSeek revision."""
import re
from dictionary_tool import read,write
from refine_weekly import STATE

plan=read(STATE/'refine-plan.json')
for identity,prefix in [('fea198292c4b3edfbae4a52729a2','1'),('2ba339bc209cb938e335622747c7','2')]:
    job=plan['jobs'][identity]
    for field in ['source','previous','styled_source']:job[field]=job[field].replace(prefix+'{{N0}}','{{N0}}')
    for filename,refs in plan['refs'].items():
        for kind,pos,key in refs:
            if key==identity:plan['files'][filename]['loreParagraphs'][pos]['source']=[job['source']]
    path=STATE/'refined'/(identity+'.json');r=read(path);r['text']=r['text'].replace(prefix+'{{N0}}','{{N0}}');write(path,r)
write(STATE/'refine-plan.json',plan)

fixed={
'5382a70bfdaf2c3a564e11d21a81':(None,{'The Primordial':'原初之物'}),
'303ba56dd433637e213561d83dcf':(None,{'NONE':'无'}),
'da6cca3900f3a5d2d3c4851b70a7':('与大万圣节护甲和装备组合时,应用极度惊吓洗练.',{'Greater Spook':'极度惊吓','Great Spook':'大万圣节'}),
'60223f2d3a7a208f0e2e767d283d':(None,{'Enigma':'谜团'}),
'e7f956a5231cd520d0b97860057e':('盛大丰收节 Finnegan 将丰收节升级为盛大丰收节,使其持续整个空岛生存年.同时开放大烘焙坊,可花费谷粒兑换更多专属物品.',{'Grand Feast':'盛大丰收节','Harvest Feast':'丰收节','Grand Bakery':'大烘焙坊','Kernels':'谷粒','Finnegan upgrades the':'Finnegan 将'}),
'fb1f29629b8b23df3b221d58a223':(None,{'{{N0}}x{{N1}}':'{{N0}}乘{{N1}}'}),
'713b04c24afb9e1783aa1b8da58b':(None,{'whalker':'沃尔克'}),
'1fc8b0957f329750b0e6f5ae654f':(None,{'CalMWolfs':'卡尔姆沃尔夫斯'}),
'7957a058292c463baac8053e5142':(None,{'Alea{{N0}}':'阿莱娅{{N0}}'}),
'abda2fbb1c065a75e3751b2fbda5':(None,{'hannibal{{N0}}':'汉尼拔{{N0}}'}),
'e0483bcab5c77bc927a02fd842c5':(None,{'Alex{{N0}}':'亚历克斯{{N0}}'}),
'6cea8ac19a30746eff55a2f421aa':(None,{'lrg{{N0}}':'艾尔阿尔吉{{N0}}'}),
'5ca3f0deaa96eb1497759bf0c06c':(None,{'meow':'喵喵叫'}),
'187c6173ecd8785c32b160062623':(' - 裂隙行者血魔 (III)  - 集市 (III-V)',{}),
'fbb30b56e055f65a5cb1221ee093':(None,{'Sin':'罪','seeker':'寻','Scythe':'镰刀'}),
'4bc84c51387520e743d9d9d8b172':('可移除当前宠物装备的宠物道具.卸下的道具会退还给你.',{'Pet Items':'宠物道具','Pet Item':'道具'}),
'014b39b26d112d7864d8c1bf5a2a':(None,{'Can be applied once. Requires Bane of Arthropods VI!':'只能应用一次.需要节肢杀手 VI!'}),
'15b1c9e95baff03802b0f9b51635':(None,{'The Great Spook':'大万圣节','October':'十月'}),
}
reviewed=[]
for identity,job in plan['jobs'].items():
    path=STATE/'refined'/(identity+'.json');r=read(path);old=read(path);source=job['source']
    if identity in fixed:
        text,maps=fixed[identity]
        if text is not None:r['text']=text
        if identity=='e7f956a5231cd520d0b97860057e':r['styleMappings']={}
        r['styleMappings'].update(maps)
    if identity=='fbb30b56e055f65a5cb1221ee093':r['styleMappings']={'Sin':'罪','seeker':'寻','Scythe':'镰刀'}
    if source.startswith('Convert ') and 'Vitality used as' in source:
        stat=next(s for s in ['Defense','Strength','Attack Speed'] if s in source)
        zh,glyph={'Defense':('防御','\ue008'),'Strength':('力量','\ue00d'),'Attack Speed':('攻击速度','\ue001')}[stat]
        r['text']='将 {{N0}} 的  活力转化为 '+glyph+' '+zh+',持续 {{N1}} 秒.(上限 {{N2}}'+glyph+' '+zh+').'
        r['styleMappings']={' Vitality':' 活力',glyph+' '+stat:glyph+' '+zh,'{{N1}}s':'{{N1}} 秒','(Max {{N2}}'+glyph+' '+stat+').':'(上限 {{N2}}'+glyph+' '+zh+').'}
    if identity in ['7ccdbd7db4f4015b35e848c4a55a','1741f50e0371b72134a4fb6221df']:
        r['text']=r['text'].replace('非恐怖敌人','普通敌人');r['styleMappings'].update({'Spooky':'恐怖','non-scary':'普通'})
        r['styleMappings'].pop('scary',None)
    if identity=='f702ab2ef21b6d0cd552a0711fe9':
        r['text']='该物品的功能已并入铁砧.借助铁砧应用洗练石和其他物品!'
        r['styleMappings'].pop('Anvil',None)
        r['styleMappings'].update({'Anvils':'铁砧','with an Anvil':'借助铁砧'})
    if identity=='45ebc447b5b65e5e4c9020e5c4ec':r['text']='获得 {{N0}} 倍拾荒者硬币.'
    if 'Combine x{{N0}}' in source:r['styleMappings']['x{{N0}}']='{{N0}} 个'
    if '{{N0}}x' in source and '{{N0}} 个' in r['text']:r['styleMappings']['{{N0}}x']='{{N0}} 个'
    if identity=='45ebc447b5b65e5e4c9020e5c4ec':r['styleMappings']['{{N0}}x']='{{N0}} 倍'
    if identity=='df63739348d7c3a573bc3d54984d':r['styleMappings']['+{{N0}}x']='+{{N0}}倍'
    if identity=='abbfcb1293623bdcb82311ac9ef0':r['styleMappings']['x{{N0}} Pelts']='{{N0}} 张兽皮'
    if identity=='3880fd096f2ed0b0ca41c718c807':r['styleMappings']['x{{N0}}']='{{N0}} 倍'
    if identity=='05c724b1220aba65fba54e4e522e':r['text']=r['text'].replace('。','.')
    r['styleMappings']={a:b for a,b in r['styleMappings'].items() if a in source and b in r['text']}
    if r!=old:write(path,r);reviewed.append(identity)
write(STATE/'reviewed-fixes.json',{'reviewed_units':reviewed})
print('Reviewed units:',len(reviewed))
