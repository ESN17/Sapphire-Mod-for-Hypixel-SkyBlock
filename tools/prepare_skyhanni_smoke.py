"""Create isolated 26.2 test project; never edit the 26.3 development properties."""
from pathlib import Path
import shutil

ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT/'build/skyhanni-presence-26.2'
if DEST.exists():raise SystemExit('Existing test project: inspect rather than replace it')
DEST.mkdir(parents=True)
for name in ('build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','LICENSE','THIRD_PARTY_NOTICES.md'):
    shutil.copy2(ROOT/name,DEST/name)
shutil.copytree(ROOT/'gradle',DEST/'gradle')
shutil.copytree(ROOT/'src/main/java',DEST/'src/main/java')
# Only the key polling signature differs for 26.2 in current production code.
for path in (DEST/'src/main/java').rglob('*.java'):
    text=path.read_text(encoding='utf-8')
    text=text.replace('InputConstants.isKeyDown(key)','InputConstants.isKeyDown(Minecraft.getInstance().getWindow(),key)')
    path.write_text(text,encoding='utf-8')
props=(DEST/'gradle.properties').read_text().replace('minecraft_version=26.3','minecraft_version=26.2').replace('fabric_api_version=0.165.0+26.3','fabric_api_version=0.161.0+26.2')
(DEST/'gradle.properties').write_text(props,encoding='utf-8')
build=(DEST/'build.gradle').read_text(encoding='utf-8')
build=build.replace("dir('src/main/resources/defaults')",f"dir('{ROOT.as_posix()}/src/main/resources/defaults')")
repo=(ROOT/'build/ports/dev004-mc26.2/official-maven').as_posix()
build=build.replace('repositories {','repositories {\n exclusiveContent { forRepository { maven { url = uri("'+repo+'") } }; filter { includeGroupByRegex("net\\\\.fabricmc\\\\.fabric-api.*") } }',1)
build+='\nsourceSets.main.resources.srcDirs = ["'+(ROOT/'src/main/resources').as_posix()+'"]\n'
build+='processResources { filesMatching("fabric.mod.json") { filter { line -> line.replace(\'"minecraft": "~26.3"\', \'"minecraft": "~26.2"\') } } }\n'
(DEST/'build.gradle').write_text(build,encoding='utf-8')
mods=DEST/'build/smoke/mods';mods.mkdir(parents=True)
for pattern in ('SkyHanni-9.3.0-mc26.2.jar','fabric-language-kotlin-*.jar','HypixelModAPI-*.jar'):
    for jar in (ROOT/'build/skyhanni-reference').glob(pattern):shutil.copy2(jar,mods/jar.name)
print(DEST)
