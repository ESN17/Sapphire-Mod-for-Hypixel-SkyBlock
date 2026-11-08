"""Create an isolated build tree for one of the three Sapphire 1.0.1 binaries. Python 3.8+."""
import argparse
import json
from pathlib import Path
import re
import shutil


def main():
    root = Path(__file__).resolve().parent
    rules = json.loads((root / "release/targets.json").read_text(encoding="utf8"))
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("minecraft", choices=rules)
    parser.add_argument("destination", type=Path, nargs="?")
    args = parser.parse_args()
    mc = args.minecraft
    dest = (args.destination or root / "build" / ("mc" + mc)).resolve()
    if dest.exists():
        parser.error("Please select a new destination directory")
    # Do not recursively copy a project into one of its own source directories.
    if dest != root and root in dest.parents and dest.relative_to(root).parts[0] != "build":
        parser.error("Use build/ for destinations inside the source tree")
    dest.mkdir(parents=True)
    for name in ("build.gradle", "settings.gradle", "gradle.properties", "gradlew", "gradlew.bat", "LICENSE", "THIRD_PARTY_NOTICES.md"):
        shutil.copy2(root / name, dest / name)
    for name in ("src", "gradle", "docs", "translation-review", "tools"):
        shutil.copytree(root / name, dest / name)
    props = dest / "gradle.properties"
    text = props.read_text(encoding="utf8")
    for key, value in (("minecraft_version", mc), ("fabric_api_version", rules[mc]["fabric_api"])):
        text = re.sub(r"^" + key + r"=.*$", key + "=" + value, text, flags=re.M)
    props.write_text(text, encoding="utf8")
    meta = dest / "src/main/resources/fabric.mod.json"
    data = json.loads(meta.read_text(encoding="utf8"))
    data["depends"]["minecraft"] = "=" + mc
    meta.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    actual = []
    for path in (dest / "src").rglob("*.java"):
        before = path.read_text(encoding="utf8")
        after = before
        for old, new in rules[mc]["replacements"]:
            after = after.replace(old, new)
        if before != after:
            path.write_text(after, encoding="utf8")
            actual.append(path.relative_to(dest).as_posix())
    if sorted(actual) != sorted(rules[mc]["adapted_files"]):
        raise RuntimeError("Adapted files differ from the recorded release")
    with (dest / "build.gradle").open("a", encoding="utf8") as file:
        file.write('\ntasks.withType(Jar).configureEach { archiveBaseName = "sapphire"; archiveVersion = "1.0.1-mc' + mc + '" }\n')
    print(dest)


if __name__ == "__main__":
    main()
