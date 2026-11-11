"""Verify the three dev009 builds, original-data smoke checks, resources and private-data exclusion."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSIONS = ('26.1.2', '26.2', '26.3')


def verify(mc):
    stage = ROOT / 'build' / ('release-dev009-mc' + mc)
    log = (ROOT / 'build' / ('dev009-mc' + mc + '-final.log')).read_text(encoding='utf8', errors='replace')
    for marker in ('BUILD SUCCESSFUL', 'SAPPHIRE_SMOKE_OK', 'SAPPHIRE_CHAT_CLICK_DISPATCH_SMOKE_OK', 'SAPPHIRE_HUD_OVERLAY_SMOKE_OK'):
        assert marker in log, (mc, marker)
    suites = [ET.parse(p).getroot() for p in (stage / 'build/test-results/test').glob('TEST-*.xml')]
    totals = {key: sum(int(s.get(key, 0)) for s in suites) for key in ('tests', 'failures', 'errors', 'skipped')}
    assert totals['tests'] >= 680 and totals['failures'] == totals['errors'] == 0, (mc, totals)
    if mc == '26.1.2':
        ui = (ROOT / 'build/dev009-mc26.1.2-ui.log').read_text(encoding='utf8', errors='replace')
        assert 'BUILD SUCCESSFUL' in ui and 'SAPPHIRE_API_UI_SMOKE_OK' in ui
    hashes = json.loads((stage / 'development-source-hashes.json').read_text(encoding='utf8'))
    assert all(hashlib.sha256((ROOT / path).read_bytes()).hexdigest() == digest for path, digest in hashes.items()), 'Development sources changed after snapshot'
    jar = stage / 'build/libs' / ('sapphire-dev-0.0.9-mc' + mc + '.jar')
    with zipfile.ZipFile(jar) as archive:
        assert archive.testzip() is None
        metadata = json.loads(archive.read('fabric.mod.json'))
        assert metadata['version'] == 'dev 0.0.9'
        assert metadata['depends']['minecraft'] == '=' + mc
        assert metadata['depends']['fabricloader'] == '>=0.19.5'
        assert metadata['authors'] == ['ESN17_OwO']
        for name in ('mixin/ChatScreenMixin', 'mixin/ChatComponentMixin', 'mixin/PlayerTabOverlayMixin',
                     'mixin/BossHealthOverlayMixin', 'client/OnlineHudTranslation', 'llm/HudTranslationQueue',
                     'llm/LlmStyledText', 'client/ChatOriginalHover', 'sync/DictionaryUpdater',
                     'client/TranslationEditorScreen', 'compat/SkyHanniCompat', 'compat/OdinCompat'):
            entry = 'com/sorakoi/sbtranslate/' + name + '.class'
            assert archive.read(entry) == (stage / 'build/classes/java/main' / entry).read_bytes(), entry
        resources = ROOT / 'src/main/resources'
        dictionary_hashes = {}
        for path in resources.rglob('*'):
            if not path.is_file() or path.name == 'fabric.mod.json':
                continue
            relative = path.relative_to(resources).as_posix()
            data = path.read_bytes()
            assert archive.read(relative) == data, (mc, relative)
            if relative.startswith('defaults/translations/') and relative.endswith('.json'):
                dictionary_hashes[relative] = hashlib.sha256(data).hexdigest()
        for name in archive.namelist():
            assert name.lower().split('/')[0] not in ('run', 'config', 'backups', '.ssh', 'server')
            assert not any(secret in name.lower() for secret in ('deepseek.local.json', 'api-keys.local.json'))
            if not name.endswith('/'):
                assert not re.search(rb'sk-[A-Za-z0-9]{24,}', archive.read(name)), 'Potential API key in artifact'
    return jar, {'minecraft': mc, 'version': 'dev 0.0.9', 'tests': totals, 'smoke': 'passed',
                 'translation_json_files': len(dictionary_hashes),
                 'dictionary_snapshot_sha256': hashlib.sha256(json.dumps(dictionary_hashes, sort_keys=True).encode()).hexdigest(),
                 'size': jar.stat().st_size, 'sha256': hashlib.sha256(jar.read_bytes()).hexdigest()}


def main():
    props = (ROOT / 'gradle.properties').read_text()
    assert 'minecraft_version=26.3' in props and 'version=dev 0.0.9' in props
    validated = [verify(mc) for mc in VERSIONS]
    destination = ROOT / 'exports/dev-0.0.9'
    destination.mkdir(exist_ok=True)
    for jar, report in validated:
        target = destination / jar.name
        if target.exists():
            assert target.read_bytes() == jar.read_bytes(), 'Refusing to overwrite a different exported build'
        else:
            shutil.copy2(jar, target)
        report['file'] = target.name
    (destination / 'verification.json').write_text(json.dumps({'development_minecraft': '26.3',
        'hypixel_online_test': False, 'releases': [report for _, report in validated]}, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    (destination / 'SHA256SUMS.txt').write_text(''.join(report['sha256'] + '  ' + jar.name + '\n' for jar, report in validated), encoding='utf8')
    (destination / '安装说明.txt').write_text(
        'Sapphire dev 0.0.9\n\n按 Minecraft 版本选择一个 JAR 放入 mods 文件夹，移除旧版 Sapphire，勿同时安装三份。\n'
        '要求：Java 25、Fabric Loader 0.19.5 或更新兼容版本，以及对应 Minecraft 版本的 Fabric API。\n'
        '26.1.2：Fabric API 0.155.3+26.1.2\n26.2：Fabric API 0.161.0+26.2\n26.3：Fabric API 0.165.0+26.3\n\n'
        '保留现有功能，包括词典同步、用户称号、翻译编辑、/sahelp、可选 SkyHanni/Odin 汉化等。\n'
        '本版修复 26.3 鼠标编号变化造成的聊天左键失效：左键保留原命令，右键复制英文原文。\n'
        'Tab 各列、顶部/底部和 Boss 血条在显示时覆盖译文，供其他模组读取的原始文本不修改。\n'
        '本地词典优先；API 总开关启用且配置有效时，可翻译剩余英文，保留动态数值与样式。\n'
        '开关：/sapphire → API 与提示词 → 翻译范围 → Tab 列表漏译 / Boss 血条漏译。\n'
        '首次请求等待或 API 失败时保留原文；相同模板复用译文并绑定当前数值，避免每帧请求。\n'
        '包含当前工程全部内置译文；未捆绑个人 API Key、账号信息、运行配置或其他模组。\n'
        '三个版本均通过构建、回归与独立离线客户端测试；尚未登录 Hypixel 实战验收。\n'
        '开发主工程继续使用 26.3；26.1.2 和 26.2 仅导出 JAR，不保留本次临时移植工程。\n', encoding='utf8')
    print(json.dumps([report for _, report in validated], ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
