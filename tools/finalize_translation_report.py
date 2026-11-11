"""Record validation of the installed dictionaries and final local build."""
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

import dictionary_tool as dictionary


def main():
    root = Path(__file__).resolve().parent.parent
    index = dictionary.read(root / 'dictionary-workspace/neu-index.json')
    expected = set(index['items'])
    audit = dictionary.read(root / 'translation-state/audit-result.json')
    assert audit['passedItems'] == len(expected) and not audit['failedJobs']
    validation = {}
    for relative in ('src/main/resources/defaults', 'dictionary-workspace', 'run/config/sapphire'):
        folder = root / relative
        errors, warnings = dictionary.validate(folder)
        ids = {dictionary.read(p)['id'] for p in (folder / 'translations/items').glob('*.json')}
        assert not errors, errors[:10]
        assert ids == expected, relative
        validation[relative] = {'errors': len(errors), 'review_warnings': len(warnings), 'items': len(ids)}
    suites = [ET.parse(p).getroot() for p in (root / 'build/test-results/test').glob('TEST-*.xml')]
    tests = {key: sum(int(s.get(key, 0)) for s in suites) for key in ('tests', 'failures', 'errors', 'skipped')}
    assert tests['tests'] >= 58 and tests['failures'] == tests['errors'] == 0, tests
    smoke = (root / 'build/full-translation-smoke.log').read_text(encoding='utf-8-sig', errors='replace')
    assert 'SAPPHIRE_SMOKE_OK' in smoke and 'BUILD SUCCESSFUL' in smoke
    jar = root / 'build/libs/sapphire-1.0.0.jar'
    with zipfile.ZipFile(jar) as archive:
        names = archive.namelist()
        dictionaries = [n for n in names if n.startswith('defaults/translations/items/') and n.endswith('.json')]
        assert len(dictionaries) == len(expected)
        assert {json.loads(archive.read(n))['id'] for n in dictionaries} == expected
        manifest = archive.read('defaults/item-dictionaries.txt').decode('utf8').splitlines()
        assert len(manifest) == len(expected) and len(set(manifest)) == len(expected)
        assert not any('deepseek.local' in n or 'translation-state/' in n or 'microsoft-auth' in n for n in names)
        # Ensure this is the artifact for the current installed source dictionaries.
        for name in dictionaries:
            assert archive.read(name) == (root / 'src/main/resources' / name).read_bytes(), name
    installed = dictionary.read(root / 'translation-state/installed.json')
    assert installed['auditedSourceSha256'] == audit['dictionarySha256']
    report = {
        'completedAt': datetime.now(timezone.utc).isoformat(),
        'neuSource': index['source'], 'neuRevision': index['revision'],
        'policy': installed['policy'], 'itemIds': len(expected),
        'checkedLoreUnits': audit['checkedLoreUnits'],
        'runtimeAuditPassedItems': audit['passedItems'],
        'auditedStagingSha256': audit['dictionarySha256'],
        'installedValidation': validation, 'javaTests': tests,
        'offlineClientSmoke': 'passed',
        'jar': str(jar.relative_to(root)).replace('\\', '/'),
        'jarBytes': jar.stat().st_size,
        'jarSha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
        'notes': [
            'Item IDs include pet and enchantment variants; they are not distinct base items.',
            'Intentional NPC names, brands and symbol names produce manual-review warnings.',
            'The workspace also contains empty non-item UI translations outside this task.',
            'Compliant previously reviewed paragraphs take priority over generated wording.',
            'Automated and offline verification does not replace live Hypixel testing or human proofreading.'
        ]
    }
    dictionary.write(root / 'translation-state/final-validation.json', validation)
    dictionary.write(root / 'docs/full-translation-report.json', report)
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
