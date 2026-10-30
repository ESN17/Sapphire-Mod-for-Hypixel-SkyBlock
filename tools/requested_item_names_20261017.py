"""Apply the user's five explicit name corrections, with per-copy backups."""
from attachment_tooltip_data import BASE, ROOTS, read, write

NAMES={
    'SUPERBOOM_TNT':('超级爆炸TNT',None),
    'DWARVEN_OS_BLOCK_BRAN':('矮人的方块麦麸','矮人O的'),
    'DWARVEN_OS_GEMSTONE_GRAHAMS':('矮人的宝石全麦饼干','矮人O的'),
    'DWARVEN_OS_METALLIC_MINIS':('矮人的金属迷你酒',"Dwarven O's "),
    'DWARVEN_OS_ORE_OATS':('矮人的矿石燕麦',"Dwarven O's "),
}

def main():
    for root in ROOTS:
        for id,(target,prefix) in NAMES.items():
            p=root/'items'/f'{id}.json';d=read(p);old=d['name']
            # Do not touch the English reference or unrelated descriptions.
            d['name']=target
            for key,value in list(d.get('styleMappings',{}).items()):
                if value==old:d['styleMappings'][key]=target
                elif prefix and value==prefix.strip():d['styleMappings'][key]='矮人的'
            d['_reviewed_name_revision']='2026-10-17-user-request'
            write(p,d)
        p=root/'items/EXPLOSIVE_BOW.json';d=read(p)
        for paragraph in d['loreParagraphs']:
            if paragraph['source']==['Acts as Superboom TNT!']:
                paragraph['translation']='可当作超级爆炸TNT使用!'
                paragraph['styleMappings']={'Superboom TNT':'超级爆炸TNT'}
        write(p,d)
    print('Updated 5 requested names and the Explosive Bow reference in 3 dictionary copies.')

if __name__=='__main__':main()
