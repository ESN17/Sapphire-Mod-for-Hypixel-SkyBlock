"""Isolated three-target 1.0.0 candidates. Does not publish or modify the main 26.3 source."""
from pathlib import Path
import argparse, hashlib, json, re, shutil, xml.etree.ElementTree as ET, zipfile

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / 'build/release-1.0.0-candidates'
OUT = ROOT / 'exports/1.0.0-test'
EVIDENCE = ROOT / 'translation-state/release-1.0.0'
VERSIONS = {'26.1.2': '0.155.3+26.1.2', '26.2': '0.161.0+26.2', '26.3': '0.165.0+26.3'}
FILES = ('build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat', 'LICENSE', 'THIRD_PARTY_NOTICES.md')
OLD_API = [
    ('net.minecraft.world.entity.EntityTypes.ZOMBIE', 'net.minecraft.world.entity.EntityType.ZOMBIE'),
    ('.gui.hud', '.gui'), ('.gui.setScreen(', '.setScreen('), ('.gui.screen()', '.screen'), ('.gui.overlay()', '.getOverlay()'),
    ('.gameRenderer.mainRenderTarget()', '.getMainRenderTarget()'), ('net.minecraft.client.gui.Hud', 'net.minecraft.client.gui.Gui'),
    ('@Mixin(Hud.class)', '@Mixin(Gui.class)'), ('net/minecraft/client/gui/Hud;', 'net/minecraft/client/gui/Gui;'),
    ('team.getColor().map(net.minecraft.world.scores.TeamColor::displaySlot).orElse(null)', 'DisplaySlot.teamColorToSlot(team.getColor())'),
    ('net.minecraft.world.scores.TeamColor.valueOf(color.name()).rgb()', 'color.getColor()'),
]
KEY_API = [('InputConstants.isKeyDown(key)', 'InputConstants.isKeyDown(Minecraft.getInstance().getWindow(),key)')]

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def source_hashes():
    paths = [p for p in (ROOT / 'src').rglob('*') if p.is_file()] + [ROOT / f for f in FILES]
    return {p.relative_to(ROOT).as_posix(): digest(p) for p in paths}

def prepare():
    assert not WORK.exists(), 'Existing workspace: inspect it instead of replacing it'
    props = (ROOT / 'gradle.properties').read_text(encoding='utf8')
    assert 'minecraft_version=26.3' in props and 'version=1.0.0' in props
    WORK.mkdir(parents=True)
    (WORK / 'source-hashes.json').write_text(json.dumps(source_hashes(), indent=2), encoding='utf8')
    for mc, api in VERSIONS.items():
        stage = WORK / ('mc' + mc)
        stage.mkdir()
        for name in FILES:
            shutil.copy2(ROOT / name, stage / name)
        for name in ('src', 'gradle'):
            shutil.copytree(ROOT / name, stage / name)
        target_props = props
        for key, value in {'minecraft_version': mc, 'fabric_api_version': api}.items():
            target_props = re.sub(r'^' + key + r'=.*$', key + '=' + value, target_props, flags=re.M)
        (stage / 'gradle.properties').write_text(target_props, encoding='utf8')
        mod = stage / 'src/main/resources/fabric.mod.json'
        meta = json.loads(mod.read_text(encoding='utf8'))
        meta['depends']['minecraft'] = '=' + mc
        mod.write_text(json.dumps(meta, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
        build = (stage / 'build.gradle').read_text(encoding='utf8')
        build += '\ntest { workingDir = file("' + ROOT.as_posix() + '") }\n'
        build += 'tasks.withType(Jar).configureEach { archiveBaseName = "sapphire"; archiveVersion = "1.0.0-mc' + mc + '" }\n'
        if mc == '26.2':
            repo = ROOT / 'build/ports/dev004-mc26.2/official-maven'
            assert repo.is_dir()
            build = build.replace('repositories {', 'repositories {\n exclusiveContent {\n  forRepository { maven { url = uri("' + repo.as_posix() + '") } }\n  filter { includeGroupByRegex("net\\\\.fabricmc\\\\.fabric-api.*") }\n }', 1)
        (stage / 'build.gradle').write_text(build, encoding='utf8')
        replacements = OLD_API + KEY_API if mc == '26.1.2' else KEY_API if mc == '26.2' else []
        changed = []
        for path in (stage / 'src').rglob('*.java'):
            before = path.read_text(encoding='utf8')
            after = before
            for old, new in replacements:
                after = after.replace(old, new)
            if before != after:
                path.write_text(after, encoding='utf8')
                changed.append(path.relative_to(stage).as_posix())
        (stage / 'api-backport.json').write_text(json.dumps({'minecraft': mc, 'changed': changed}, indent=2), encoding='utf8')
        print(mc, 'prepared;', len(changed), 'API adaptations')

def verify(mc):
    stage = WORK / ('mc' + mc)
    for kind, markers in {
        'build': ('BUILD SUCCESSFUL',),
        'smoke': ('BUILD SUCCESSFUL', 'SAPPHIRE_SMOKE_OK', 'SAPPHIRE_CHAT_RESTORE_SMOKE_OK', 'SAPPHIRE_CHAT_CLICK_DISPATCH_SMOKE_OK', 'SAPPHIRE_HUD_OVERLAY_SMOKE_OK'),
        'ui': ('BUILD SUCCESSFUL', 'SAPPHIRE_API_UI_SMOKE_OK'),
    }.items():
        log = (WORK / f'mc{mc}-{kind}.log').read_text(encoding='utf8', errors='replace')
        for marker in markers:
            assert marker in log, (mc, kind, marker)
    suites = [ET.parse(p).getroot() for p in (stage / 'build/test-results/test').glob('TEST-*.xml')]
    totals = {key: sum(int(s.get(key, 0)) for s in suites) for key in ('tests', 'failures', 'errors', 'skipped')}
    assert totals['tests'] >= 708 and totals['failures'] == totals['errors'] == 0, (mc, totals)
    jar = stage / 'build/libs' / f'sapphire-1.0.0-mc{mc}.jar'
    with zipfile.ZipFile(jar) as z:
        assert z.testzip() is None
        meta = json.loads(z.read('fabric.mod.json'))
        assert meta['version'] == '1.0.0' and meta['depends']['minecraft'] == '=' + mc
        assert meta['authors'] == ['ESN17_OwO'] and meta['depends']['fabricloader'] == '>=0.19.5'
        for p in (stage / 'build/classes/java/main').rglob('*.class'):
            assert z.read(p.relative_to(stage / 'build/classes/java/main').as_posix()) == p.read_bytes(), str(p)
        for p in (ROOT / 'src/main/resources').rglob('*'):
            if p.is_file() and p.name != 'fabric.mod.json':
                assert z.read(p.relative_to(ROOT / 'src/main/resources').as_posix()) == p.read_bytes(), str(p)
        for entry in ('translation/DisplayTranslationSafety', 'client/SettingsTheme', 'client/SettingsCatalog', 'client/SapphireApiScreen'):
            assert 'com/sorakoi/sbtranslate/' + entry + '.class' in z.namelist()
        for name in z.namelist():
            assert name.lower().split('/')[0] not in ('run', 'config', 'backups', '.ssh', 'server')
            assert not any(s in name.lower() for s in ('deepseek.local.json', 'api-keys.local.json'))
            if not name.endswith('/'):
                assert not re.search(rb'sk-[A-Za-z0-9]{24,}', z.read(name)), 'Possible private key in artifact'
    return jar, {'minecraft': mc, 'version': '1.0.0', 'tests': totals, 'client_smoke': 'passed', 'settings_ui_smoke': 'passed', 'sha256': digest(jar), 'file': jar.name, 'size': jar.stat().st_size}

def export():
    assert source_hashes() == json.loads((WORK / 'source-hashes.json').read_text(encoding='utf8')), 'Main source changed during build'
    verified = [verify(mc) for mc in VERSIONS]
    OUT.mkdir(parents=True, exist_ok=True)
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    for jar, report in verified:
        dest = OUT / jar.name
        assert not dest.exists(), 'Refusing to replace an existing candidate'
        shutil.copy2(jar, dest)
        assert digest(dest) == report['sha256']
        mc = report['minecraft']
        for kind in ('build', 'smoke', 'ui'):
            shutil.copy2(WORK / f'mc{mc}-{kind}.log', EVIDENCE)
        shutil.copy2(WORK / ('mc' + mc) / 'api-backport.json', EVIDENCE / f'mc{mc}-api-backport.json')
        screenshot_dir = WORK / ('ui-mc' + mc) / 'api-ui-smoke'
        if screenshot_dir.exists():
            shutil.copytree(screenshot_dir, EVIDENCE / ('settings-mc' + mc), dirs_exist_ok=True)
    receipt = {'published': False, 'awaiting_user_acceptance': True, 'development_minecraft': '26.3', 'hypixel_online_test': False, 'releases': [r for _, r in verified]}
    (OUT / 'verification.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    (OUT / 'SHA256SUMS.txt').write_text(''.join(r['sha256'] + '  ' + r['file'] + '\n' for _, r in verified), encoding='utf8')
    for name in ('verification.json', 'SHA256SUMS.txt'):
        shutil.copy2(OUT / name, EVIDENCE / name)
    for name in ('安装与验收.txt', '问题分析与修复说明.txt'):
        shutil.copy2(EVIDENCE / name, OUT / name)
    shutil.copy2(EVIDENCE / 'settings-mc26.3/page--2.png', OUT / '设置页面预览.png')
    shutil.copy2(WORK / 'source-hashes.json', EVIDENCE)
    print(json.dumps(receipt, ensure_ascii=False, indent=2))

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['prepare', 'export'])
    args = parser.parse_args()
    prepare() if args.action == 'prepare' else export()
