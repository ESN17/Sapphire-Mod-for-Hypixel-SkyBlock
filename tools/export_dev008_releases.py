"""Verify tested, version-specific dev008 JARs before exporting the three installable files."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[1]
LOGS={'26.3':ROOT/'build/dev008-mc26.3-final.log',
      '26.1.2':ROOT/'build/dev008-mc26.1.2-final.log',
      '26.2':ROOT/'build/dev008-mc26.2-final.log'}

def verify(mc,log):
    stage=ROOT/'build'/('release-dev008-mc'+mc)
    output=log.read_text(encoding='utf8',errors='replace')
    assert 'BUILD SUCCESSFUL' in output and 'SAPPHIRE_SMOKE_OK' in output, mc+' build/smoke incomplete'
    if mc=='26.1.2':
        ui=(ROOT/'build/dev008-mc26.1.2-ui.log').read_text(encoding='utf8',errors='replace')
        assert 'BUILD SUCCESSFUL' in ui and 'SAPPHIRE_API_UI_SMOKE_OK' in ui
    suites=[ET.parse(p).getroot() for p in (stage/'build/test-results/test').glob('TEST-*.xml')]
    totals={k:sum(int(s.get(k,0)) for s in suites) for k in ('tests','failures','errors','skipped')}
    assert totals['tests']>=640 and totals['failures']==totals['errors']==0, (mc,totals)
    hashes=json.loads((stage/'development-source-hashes.json').read_text(encoding='utf8'))
    assert all(hashlib.sha256((ROOT/p).read_bytes()).hexdigest()==h for p,h in hashes.items()), 'Development sources changed after freezing'
    jar=stage/'build/libs'/('sapphire-dev-0.0.8-mc'+mc+'.jar')
    with zipfile.ZipFile(jar) as z:
        assert z.testzip() is None
        metadata=json.loads(z.read('fabric.mod.json'))
        assert metadata['version']=='dev 0.0.8'
        assert metadata['depends']['minecraft']=='='+mc
        assert metadata['depends']['fabricloader']=='>=0.19.5'
        assert metadata['authors']==['ESN17_OwO']
        for name in ('rank/RankDisplay','rank/RankClient','client/SapphireRanks','client/OutgoingChatTranslation',
                     'client/TranslationEditorScreen','item/TooltipDisplayCache','sync/DictionaryUpdater','sync/DictionaryUpdateNotice','item/ItemIdResolver',
                     'mixin/ChatComponentMixin','mixin/PlayerTabOverlayMixin','client/ChatOriginalHover',
                     'compat/SkyHanniCompat','compat/SkyHanniDictionary','compat/SkyHanniMixinPlugin'):
            entry='com/sorakoi/sbtranslate/'+name+'.class'
            assert z.read(entry)==(stage/'build/classes/java/main'/entry).read_bytes(), entry
        resources=ROOT/'src/main/resources'
        count=0
        for p in resources.rglob('*'):
            if not p.is_file() or p.name=='fabric.mod.json':continue
            rel=p.relative_to(resources).as_posix()
            assert z.read(rel)==p.read_bytes(), mc+' mismatched resource: '+rel
            count+=rel.startswith('defaults/translations/') and rel.endswith('.json')
        with zipfile.ZipFile(ROOT/'exports/dictionaries/20261104_2.zip') as published:
            for name in published.namelist():
                if name.startswith('translations/') and name.endswith('.json'):
                    assert z.read('defaults/'+name)==published.read(name), mc+' differs from published dictionary: '+name
        for name in z.namelist():
            assert name.lower().split('/')[0] not in ('run','config','backups','.ssh','server')
            assert 'deepseek.local.json' not in name
            if not name.endswith('/'):
                assert not re.search(rb'sk-[A-Za-z0-9]{24,}',z.read(name)), 'Potential API key in artifact: '+name
    return jar,{'minecraft':mc,'version':'dev 0.0.8','tests':totals,'smoke':'passed',
                'translation_json_files':count,'dictionary_version':'20261104_2','size':jar.stat().st_size,
                'sha256':hashlib.sha256(jar.read_bytes()).hexdigest()}

def main():
    assert 'minecraft_version=26.3' in (ROOT/'gradle.properties').read_text()
    assert 'version=dev 0.0.8' in (ROOT/'gradle.properties').read_text()
    validated=[verify(mc,log) for mc,log in LOGS.items()]
    dest=ROOT/'exports/dev-0.0.8'
    dest.mkdir(exist_ok=True)
    for jar,report in validated:
        target=dest/jar.name
        if target.exists():assert target.read_bytes()==jar.read_bytes(), 'Refusing to replace a different exported build'
        else:shutil.copy2(jar,target)
        report['file']=target.name
    (dest/'verification.json').write_text(json.dumps({'development_minecraft':'26.3','releases':[r for _,r in validated]},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    (dest/'SHA256SUMS.txt').write_text(''.join(r['sha256']+'  '+jar.name+'\n' for jar,r in validated),encoding='utf8')
    (dest/'安装说明.txt').write_text(
        'Sapphire dev 0.0.8\n\n按 Minecraft 版本选择一个 JAR 放入 mods 文件夹，移除旧版 Sapphire，勿同时安装三份。\n'
        '运行要求：Java 25、Fabric Loader 0.19.5 或更新兼容版本，以及对应 Minecraft 版本的 Fabric API。\n'
        '本次保留当前全部功能，包括词典同步、Sapphire 用户称号、Tab 中文翻译、按住 Ctrl 显示中文、API 聊天翻译和词典编辑。\n'
        '26.3 / Fabric API 0.165.0+26.3\n26.1.2 / Fabric API 0.155.3+26.1.2\n26.2 / Fabric API 0.161.0+26.2\n\n'
        '三个版本均经过完整构建和独立客户端启动/Mixin/显示测试，未使用玩家账号登录 Hypixel 人工验收。\n'
        '包含聊天悬停原文、最新翻译修订和可选 SkyHanni 汉化；不安装 SkyHanni 也可正常使用 Sapphire。\n'
        'dev 0.0.8 包含属性碎片 ID 识别修复、发现新版译名库时的下载通知，以及 SkyHanni 表格物品名匹配修复。\n'
        '内置全部最新译文，与已发布的 20261104_2 译名库一致，包含地牢、海怪、裂隙、部署物和四种矿井尸体提示。\n'
        'SkyHanni 需另装与 Minecraft 匹配的官方版本；本次未捆绑 SkyHanni 或其他依赖。\n'
        '后续开发工程保持 Minecraft 26.3；26.1.2 和 26.2 仅导出 JAR，不保留本次临时移植工程。\n',encoding='utf8')
    print(json.dumps([r for _,r in validated],ensure_ascii=False,indent=2))

if __name__=='__main__':main()
