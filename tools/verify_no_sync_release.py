"""Verify and export the tested no-sync release without touching development state."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
stage = root / 'build/release-26.3-no-sync'
jar = stage / 'build/libs/sapphire-dev-0.0.5-mc26.3-no-sync.jar'
assert 'BUILD SUCCESSFUL' in (stage / 'build-release.log').read_text(encoding='utf-8'), 'Build did not pass'
assert 'SAPPHIRE_SMOKE_OK:' in (stage / 'smoke-release.log').read_text(encoding='utf-8'), 'Real client smoke did not pass'
tests = {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}
for file in (stage / 'build/test-results/test').glob('TEST-*.xml'):
    suite = ET.parse(file).getroot()
    for key in tests:
        tests[key] += int(suite.attrib.get(key, 0))
assert tests['tests'] >= 500 and tests['failures'] == tests['errors'] == 0, tests
original = json.loads((stage / 'original-source-hashes.json').read_text(encoding='utf-8'))
assert all(hashlib.sha256((root / name).read_bytes()).hexdigest() == digest for name, digest in original.items()), 'Development source changed'
with zipfile.ZipFile(jar) as z:
    assert z.testzip() is None
    names = z.namelist()
    assert not any('/sync/' in n or 'DictionarySyncSmokeTest' in n for n in names)
    metadata = json.loads(z.read('fabric.mod.json'))
    assert metadata['version'] == 'dev 0.0.5' and metadata['depends']['minecraft'] == '~26.3'
    assert 'THIRD_PARTY_NOTICES.md' in names
    for path in (stage / 'build/classes/java/main').rglob('*.class'):
        entry = path.relative_to(stage / 'build/classes/java/main').as_posix()
        assert z.read(entry) == path.read_bytes(), entry
    count = 0
    for path in (root / 'src/main/resources/defaults/translations').rglob('*.json'):
        entry = path.relative_to(root / 'src/main/resources').as_posix()
        assert z.read(entry) == path.read_bytes(), entry
        count += 1
    for name in names:
        if name.endswith('/'):
            continue
        data = z.read(name)
        assert b'sapphire.esn17cabin.club' not in data, name
        assert not re.search(rb'sk-[A-Za-z0-9]{20,}', data), 'Unexpected API key-like value'
        assert not any(token in name.lower() for token in ('deepseek.local', 'accounts.json', 'session.json', '.env')), name
destination = root / 'exports/dev-0.0.5-mc26.3'
destination.mkdir(parents=True, exist_ok=True)
target = destination / jar.name
assert not target.exists(), 'Refusing to overwrite an exported release'
shutil.copy2(jar, target)
digest = hashlib.sha256(target.read_bytes()).hexdigest()
(destination / (target.name + '.sha256')).write_text(f'{digest}  {target.name}\n', encoding='utf-8')
report = {'artifact': str(target), 'version': metadata['version'], 'minecraft': '26.3',
          'fabricLoader': '0.19.5', 'java': 25, 'dictionarySyncIncluded': False,
          'dictionaryJsonFiles': count, 'tests': tests, 'minecraftSmokePassed': True,
          'developmentSourcesUnchanged': True, 'bytes': target.stat().st_size, 'sha256': digest}
(destination / 'verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(report, ensure_ascii=False, indent=2))
