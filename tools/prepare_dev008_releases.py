"""Freeze current sources into three isolated release projects (no credentials/run data)."""
from pathlib import Path
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[1]
VERSIONS = {'26.3': '0.165.0+26.3', '26.1.2': '0.155.3+26.1.2', '26.2': '0.161.0+26.2'}

def main():
    for mc, api in VERSIONS.items():
        dest = ROOT / 'build' / ('release-dev008-mc' + mc)
        if dest.exists():
            raise SystemExit('Refusing to replace existing staging directory: ' + str(dest))
        dest.mkdir(parents=True)
        for name in ('build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','LICENSE','THIRD_PARTY_NOTICES.md'):
            shutil.copy2(ROOT / name, dest / name)
        for name in ('src','gradle'):
            shutil.copytree(ROOT / name, dest / name)
        props = (dest / 'gradle.properties').read_text(encoding='utf8')
        props = props.replace('minecraft_version=26.3', 'minecraft_version=' + mc)
        props = props.replace('fabric_api_version=0.165.0+26.3', 'fabric_api_version=' + api)
        (dest / 'gradle.properties').write_text(props, encoding='utf8')
        mod = dest / 'src/main/resources/fabric.mod.json'
        metadata = json.loads(mod.read_text(encoding='utf8'))
        metadata['depends']['minecraft'] = '=' + mc
        mod.write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
        # Fixtures remain local to the development tree and never enter the JAR.
        with (dest / 'build.gradle').open('a', encoding='utf8') as out:
            out.write('\ntest { workingDir = file("../..") }\n')
            out.write('tasks.withType(Jar).configureEach { archiveBaseName = "sapphire"; archiveVersion = "dev-0.0.8-mc'+mc+'" }\n')
        if mc == '26.2':
            file = dest / 'build.gradle'
            text = file.read_text(encoding='utf8')
            repo = (ROOT / 'build/ports/dev004-mc26.2/official-maven').as_posix()
            text = text.replace('repositories {', 'repositories {\n exclusiveContent {\n  forRepository { maven { url = uri("'+repo+'") } }\n  filter { includeGroupByRegex("net\\\\.fabricmc\\\\.fabric-api.*") }\n }', 1)
            file.write_text(text, encoding='utf8')
        hashes = {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                  for p in (ROOT / 'src').rglob('*') if p.is_file()}
        (dest / 'development-source-hashes.json').write_text(json.dumps(hashes,indent=2),encoding='utf8')
        print(dest)

if __name__ == '__main__':
    main()
