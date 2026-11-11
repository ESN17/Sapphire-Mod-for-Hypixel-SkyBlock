"""Rebuild the current source as dev 0.0.8 for three MC targets, preserving development."""
from pathlib import Path
import argparse, datetime, hashlib, json, re, shutil, xml.etree.ElementTree as ET, zipfile

ROOT=Path(__file__).resolve().parents[1]
WORK=ROOT/'build/dev008-refresh-20261106'
VERSIONS={'26.1.2':'0.155.3+26.1.2','26.2':'0.161.0+26.2','26.3':'0.165.0+26.3'}
FILES=('build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','LICENSE','THIRD_PARTY_NOTICES.md')
OLD_API=[
    ('net.minecraft.world.entity.EntityTypes.ZOMBIE','net.minecraft.world.entity.EntityType.ZOMBIE'),
    ('.gui.hud','.gui'),('.gui.setScreen(','.setScreen('),('.gui.screen()','.screen'),('.gui.overlay()','.getOverlay()'),
    ('.gameRenderer.mainRenderTarget()','.getMainRenderTarget()'),('net.minecraft.client.gui.Hud','net.minecraft.client.gui.Gui'),
    ('@Mixin(Hud.class)','@Mixin(Gui.class)'),('net/minecraft/client/gui/Hud;','net/minecraft/client/gui/Gui;'),
    ('team.getColor().map(net.minecraft.world.scores.TeamColor::displaySlot).orElse(null)','DisplaySlot.teamColorToSlot(team.getColor())'),
    ('net.minecraft.world.scores.TeamColor.valueOf(color.name()).rgb()','color.getColor()'),
]
KEY_API=[('InputConstants.isKeyDown(key)','InputConstants.isKeyDown(Minecraft.getInstance().getWindow(),key)')]

def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def source_hashes():
    paths=[p for p in (ROOT/'src').rglob('*') if p.is_file()]+[ROOT/f for f in FILES]
    return {p.relative_to(ROOT).as_posix():digest(p) for p in paths}

def prepare():
    assert not WORK.exists(),'Refusing to overwrite an existing build workspace'
    assert 'minecraft_version=26.3' in (ROOT/'gradle.properties').read_text()
    WORK.mkdir(parents=True)
    snapshot=source_hashes()
    (WORK/'source-hashes.json').write_text(json.dumps(snapshot,indent=2),encoding='utf8')
    destination=ROOT/'exports/dev-0.0.8'
    backup=ROOT/'backups/releases'/('dev-0.0.8-before-refresh-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
    shutil.copytree(destination,backup)
    (WORK/'backup.json').write_text(json.dumps({'path':str(backup),'files':{p.name:digest(p) for p in backup.iterdir() if p.is_file()}},indent=2),encoding='utf8')
    for mc,api in VERSIONS.items():
        stage=WORK/('mc'+mc);stage.mkdir()
        for name in FILES:shutil.copy2(ROOT/name,stage/name)
        for name in ('src','gradle'):shutil.copytree(ROOT/name,stage/name)
        props=(stage/'gradle.properties').read_text(encoding='utf8')
        for key,value in {'minecraft_version':mc,'fabric_api_version':api,'version':'dev 0.0.8'}.items():
            props=re.sub(r'^'+key+r'=.*$',key+'='+value,props,flags=re.M)
        (stage/'gradle.properties').write_text(props,encoding='utf8')
        mod=stage/'src/main/resources/fabric.mod.json';data=json.loads(mod.read_text(encoding='utf8'));data['depends']['minecraft']='='+mc
        mod.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
        build=(stage/'build.gradle').read_text(encoding='utf8')
        build+='\ntest { workingDir = file("'+ROOT.as_posix()+'") }\n'
        build+='tasks.withType(Jar).configureEach { archiveBaseName = "sapphire"; archiveVersion = "dev-0.0.8-mc'+mc+'" }\n'
        if mc=='26.2':
            repo=(ROOT/'build/ports/dev004-mc26.2/official-maven').as_posix()
            assert Path(repo).is_dir()
            build=build.replace('repositories {','repositories {\n exclusiveContent {\n  forRepository { maven { url = uri("'+repo+'") } }\n  filter { includeGroupByRegex("net\\\\.fabricmc\\\\.fabric-api.*") }\n }',1)
        (stage/'build.gradle').write_text(build,encoding='utf8')
        replacements=(OLD_API+KEY_API if mc=='26.1.2' else KEY_API if mc=='26.2' else [])
        changed=[]
        for path in (stage/'src').rglob('*.java'):
            before=path.read_text(encoding='utf8');after=before
            for a,b in replacements:after=after.replace(a,b)
            if before!=after:path.write_text(after,encoding='utf8');changed.append(path.relative_to(stage).as_posix())
        (stage/'api-backport.json').write_text(json.dumps({'minecraft':mc,'changed':changed},indent=2),encoding='utf8')
        print(mc,'prepared;',len(changed),'API-only adaptations')
    print('Backup:',backup)

def verify(mc):
    stage=WORK/('mc'+mc)
    log=(WORK/('mc'+mc+'-build.log')).read_text(encoding='utf8',errors='replace')
    for marker in ('BUILD SUCCESSFUL','SAPPHIRE_SMOKE_OK','SAPPHIRE_CHAT_CLICK_DISPATCH_SMOKE_OK','SAPPHIRE_HUD_OVERLAY_SMOKE_OK'):
        assert marker in log,(mc,marker)
    suites=[ET.parse(p).getroot() for p in (stage/'build/test-results/test').glob('TEST-*.xml')]
    totals={key:sum(int(s.get(key,0)) for s in suites) for key in ('tests','failures','errors','skipped')}
    assert totals['tests']>=703 and totals['failures']==totals['errors']==0,(mc,totals)
    jar=stage/'build/libs'/('sapphire-dev-0.0.8-mc'+mc+'.jar')
    with zipfile.ZipFile(jar) as z:
        assert z.testzip() is None
        meta=json.loads(z.read('fabric.mod.json'))
        assert meta['version']=='dev 0.0.8' and meta['depends']['minecraft']=='='+mc
        assert meta['depends']['fabricloader']=='>=0.19.5' and meta['authors']==['ESN17_OwO']
        for path in (stage/'build/classes/java/main').rglob('*.class'):
            entry=path.relative_to(stage/'build/classes/java/main').as_posix()
            assert z.read(entry)==path.read_bytes(),entry
        resources=ROOT/'src/main/resources'
        for path in resources.rglob('*'):
            if path.is_file() and path.name!='fabric.mod.json':
                assert z.read(path.relative_to(resources).as_posix())==path.read_bytes(),str(path)
        for entry in ('llm/HelpEvidence','client/SapphireHelp','mixin/compat/SkyHanniDrawStringMixin','mixin/compat/SkyHanniTabMeasureMixin'):
            assert 'com/sorakoi/sbtranslate/'+entry+'.class' in z.namelist(),entry
        for name in z.namelist():
            assert name.lower().split('/')[0] not in ('run','config','backups','.ssh','server')
            assert not any(s in name.lower() for s in ('deepseek.local.json','api-keys.local.json'))
            if not name.endswith('/'):assert not re.search(rb'sk-[A-Za-z0-9]{24,}',z.read(name)),'Potential private key in artifact'
    return jar,{'minecraft':mc,'version':'dev 0.0.8','tests':totals,'smoke':'passed','size':jar.stat().st_size,'sha256':digest(jar),'file':jar.name}

def export():
    assert source_hashes()==json.loads((WORK/'source-hashes.json').read_text(encoding='utf8')),'Development files changed during build'
    verified=[verify(mc) for mc in VERSIONS]
    destination=ROOT/'exports/dev-0.0.8'
    receipt={'development_minecraft':'26.3','rebuilt_from_current_source':True,'hypixel_online_test':False,'releases':[report for _,report in verified]}
    evidence=ROOT/'translation-state/dev008-refresh-20261106';evidence.mkdir(parents=True,exist_ok=True)
    for jar,report in verified:
        target=destination/jar.name;temporary=target.with_suffix('.jar.new');shutil.copy2(jar,temporary);temporary.replace(target)
        assert digest(target)==report['sha256']
        mc=report['minecraft'];shutil.copy2(WORK/('mc'+mc+'-build.log'),evidence/('mc'+mc+'-build.log'))
        shutil.copy2(WORK/('mc'+mc)/'api-backport.json',evidence/('mc'+mc+'-api-backport.json'))
    (destination/'verification.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    (destination/'SHA256SUMS.txt').write_text(''.join(r['sha256']+'  '+j.name+'\n' for j,r in verified),encoding='utf8')
    (destination/'安装说明.txt').write_text(
        'Sapphire dev 0.0.8（2026-11-06 当前工程重新构建覆盖版）\n\n'
        '按 Minecraft 版本选择一个 JAR，替换 mods 文件夹里的旧 Sapphire，勿同时安装多份。\n'
        '要求 Java 25、Fabric Loader 0.19.5 或更新兼容版本，以及对应版本的 Fabric API。\n'
        '26.1.2：Fabric API 0.155.3+26.1.2\n26.2：Fabric API 0.161.0+26.2\n26.3：Fabric API 0.165.0+26.3\n\n'
        '包含当前全部源码修复及内置译文：聊天点击动作、Tab/Bossbar 显示翻译、SkyHanni 显示兼容改进等。\n'
        '/sahelp 支持 Wiki、论坛及公开网页检索；默认短回答，来源单独通过 /sahelp sources 查看。公开搜索受限时使用已取得的资料。\n'
        '保留词典同步、翻译编辑、API 与其他现有功能；不捆绑个人密钥、账号数据或其他模组。\n'
        '三个版本已分别构建、回归测试并运行离线客户端检查；未登录 Hypixel 实战验收收益统计。\n'
        '主工程保持 Minecraft 26.3；仅为本次导出临时适配 26.1.2 和 26.2。\n',encoding='utf8')
    for name in ('verification.json','SHA256SUMS.txt'):shutil.copy2(destination/name,evidence/name)
    shutil.copy2(WORK/'backup.json',evidence/'backup.json')
    print(json.dumps(receipt,ensure_ascii=False,indent=2))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('action',choices=['prepare','export']);args=p.parse_args()
    prepare() if args.action=='prepare' else export()
