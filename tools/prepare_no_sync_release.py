"""Prepare an isolated 26.3 release without the unfinished dictionary updater.

Run from the repository root; existing development sources are never modified.
"""
from pathlib import Path
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'build/release-26.3-no-sync'
if DEST.exists():
    raise SystemExit(f'Refusing to overwrite existing staging project: {DEST}')
DEST.mkdir(parents=True)
for name in ('build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat', 'LICENSE', 'THIRD_PARTY_NOTICES.md'):
    shutil.copy2(ROOT / name, DEST / name)
shutil.copytree(ROOT / 'gradle', DEST / 'gradle')
shutil.copytree(ROOT / 'src', DEST / 'src', ignore=shutil.ignore_patterns('sync', 'DictionarySyncSmokeTest.java', 'DictionaryUpdateTest.java'))

def edit(relative, changes):
    file = DEST / relative
    content = file.read_text(encoding='utf-8')
    for before, after in changes:
        if content.count(before) != 1:
            raise RuntimeError(f'Expected exactly one match in {relative}: {before[:100]}')
        content = content.replace(before, after, 1)
    file.write_text(content, encoding='utf-8')

base = 'src/main/java/com/sorakoi/sbtranslate/'
edit('gradle.properties', [('version=dev 0.0.4', 'version=dev 0.0.5')])
source = (DEST / base / 'Sapphire.java').read_text(encoding='utf-8')
update_method = source[source.index('    public static synchronized java.util.concurrent.CompletableFuture<String> updateDictionaryAsync()'):source.index('    public static void reload(){')]
tick_notice = source[source.index('            var connection=client.getConnection();'):source.index('            if(++ticks%20==0')]
edit(base + 'Sapphire.java', [
    ('import com.sorakoi.sbtranslate.sync.*;\n', ''),
    ('    private static java.util.concurrent.CompletableFuture<String> updating;\n    private static final DictionaryUpdateNotice UPDATE_NOTICE=new DictionaryUpdateNotice();\n    private static String dictionaryStatus="尚未检查";\n    private boolean remoteDictionary;\n', ''),
    ('            new DictionaryUpdater(ROOT,new DictionaryTransport.Http()).recover();\n            remoteDictionary=DictionaryUpdater.installed(ROOT);\n            if(remoteDictionary)dictionaryStatus=DictionaryUpdater.installedVersion(ROOT);\n', ''),
    ('UPDATE_NOTICE.disconnect();', ''),
    (tick_notice, ''),
    ('            .then(literal("update").executes(c->{updateDictionaryAsync().thenAccept(s->c.getSource().sendFeedback(Component.literal(s)));c.getSource().sendFeedback(Component.literal("正在后台检查译名库更新..."));return 1;}))\n', ''),
    ('            if(Boolean.getBoolean("sapphire.dictionarySmoke")){com.sorakoi.sbtranslate.client.DictionarySyncSmokeTest.start(client);return;}\n', ''),
    ('            } else if(CONFIG.dictionaryAutoUpdate)updateDictionaryAsync();', '            }'),
    ('    public static String dictionaryStatus(){return dictionaryStatus;}\n', ''),
    ('+"，译名库="+dictionaryStatus', ''),
    (update_method, ''),
    ('        if(remoteDictionary && file.startsWith("translations/"))return;\n', ''),
])
edit(base + 'config/SapphireConfig.java', [
    ('    public boolean dictionaryAutoUpdate = true;\n    public String dictionaryUpdateUrl = com.sorakoi.sbtranslate.sync.DictionaryUpdater.DEFAULT_URL;\n', ''),
])
edit(base + 'client/SapphireScreen.java', [
    (',{"dictionaryAutoUpdate","译名库自动更新"}', ''),
])
edit(base + 'client/TranslationStatusScreen.java', [
    ('        lines.add("译名库更新: "+Sapphire.dictionaryStatus());\n', ''),
])

# Keep fixture paths rooted in the original workspace; they are not packaged.
with (DEST / 'build.gradle').open('a', encoding='utf-8') as out:
    out.write('\n// Isolated no-sync release; original project and sync work remain intact.\n')
    out.write('test { workingDir = file("../..") }\n')
    out.write('tasks.withType(Jar).configureEach { archiveBaseName = "sapphire"; archiveVersion = "dev-0.0.5-mc26.3-no-sync" }\n')

# Fail closed if any runtime update entry point or endpoint remains.
for file in (DEST / 'src/main/java').rglob('*.java'):
    text = file.read_text(encoding='utf-8')
    for forbidden in ('com.sorakoi.sbtranslate.sync', 'DictionaryUpdater', 'DictionaryUpdateNotice', 'dictionaryAutoUpdate', 'dictionaryUpdateUrl', 'updateDictionaryAsync', 'sapphire.esn17cabin.club'):
        if forbidden in text:
            raise RuntimeError(f'Unexpected sync dependency {forbidden} in {file}')

original_hashes = {str(p.relative_to(ROOT)).replace('\\', '/'): hashlib.sha256(p.read_bytes()).hexdigest()
                   for p in (ROOT / 'src').rglob('*') if p.is_file()}
(DEST / 'original-source-hashes.json').write_text(json.dumps(original_hashes, indent=2), encoding='utf-8')
print(DEST)
