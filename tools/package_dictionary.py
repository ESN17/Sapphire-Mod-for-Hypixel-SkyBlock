"""Build a public, data-only Sapphire dictionary release. Never reads run/config."""
import argparse
import hashlib
import json
import re
import zipfile
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("version", help="YYYYMMDD_number, for example 20261030_1")
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9]{8}_[1-9][0-9]{0,8}", args.version):
        parser.error("Invalid release version")
    datetime.strptime(args.version[:8], "%Y%m%d")
    target = ROOT / "exports/dictionaries"
    target.mkdir(parents=True, exist_ok=True)
    archive = target / f"{args.version}.zip"
    if archive.exists():
        parser.error("Release already exists; use a new version rather than changing a published ZIP")
    defaults = ROOT / "src/main/resources/defaults"
    payload = {}
    for source in sorted((defaults / "translations").rglob("*.json")):
        raw = source.read_bytes()
        json.loads(raw)
        payload[source.relative_to(defaults).as_posix()] = raw
    # Older clients require this filename. Give them exactly the rewritten corpus.
    payload["translations/community-skyzh.json"] = payload["translations/community-sapphire.json"]
    for name, source in {
        "LICENSE.txt": ROOT / "LICENSE",
        "THIRD_PARTY_NOTICES.md": ROOT / "THIRD_PARTY_NOTICES.md",
        "NEU-LICENSE.txt": defaults / "NEU-LICENSE.txt",
        "skyzh-LICENSE.txt": defaults / "third-party/skyzh-LICENSE.txt",
        "wiki-NOTICE.txt": defaults / "third-party/wiki-NOTICE.txt",
    }.items():
        payload[f"licenses/{name}"] = source.read_bytes()
    # Existing clients allow these JSON names and ignore unknown dictionaries.
    # New clients load them separately from parser-facing game translations.
    for source in sorted((defaults / "compat").glob("*.json")):
        data = json.loads(source.read_bytes())
        license_name = "odin-LICENSE.txt" if source.name.startswith("odin-") else "skyhanni-LICENSE.txt"
        license_path = defaults / "third-party" / license_name
        if license_path.exists():
            data["licenseText"] = license_path.read_text(encoding="utf-8")
        payload["translations/compat-" + source.name.replace("_", "-")] = (json.dumps(data, ensure_ascii=False, indent=2)+"\n").encode("utf-8")
    manifest = {"schemaVersion": 1, "kind": "full", "version": args.version,
                "files": {name: hashlib.sha256(raw).hexdigest() for name, raw in payload.items()}}
    temporary = archive.with_suffix(".zip.tmp")
    with zipfile.ZipFile(temporary, "x", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as output:
        output.writestr("sapphire-dictionary.json", json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
        for name, raw in payload.items():
            output.writestr(name, raw)
    temporary.replace(archive)
    releases = []
    for path in sorted(target.glob("*.zip")):
        if re.fullmatch(r"[0-9]{8}_[1-9][0-9]{0,8}\.zip", path.name):
            releases.append({"name": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "size": path.stat().st_size})
    def order(record):
        day, sequence = record["name"][:-4].split("_")
        return (day, int(sequence))
    releases.sort(key=order)
    active = [r for r in releases if order(r) >= ("20261109", 1)]
    historical = [r for r in releases if order(r) < ("20261109", 1)]
    notice = "此前的译名包使用过 SkyBlockZH 的中文译文。鉴于上游许可证已变更，旧包停止用于客户端更新，仅作历史文件留存。"
    index = {"schemaVersion": 1, "files": active, "archivedFiles": historical, "historyNotice": notice}
    index_temp = target / "index.json.tmp"
    index_temp.write_text(json.dumps(index, indent=2) + "\n", encoding="utf-8")
    index_temp.replace(target / "index.json")
    def links(records):
        return "\n".join(f'<li><a href="{r["name"]}">{r["name"]}</a> · {r["size"]:,} bytes</li>' for r in reversed(records))
    (target / "index.html").write_text('<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>Sapphire 译名库</title>'
        '<h1>Sapphire 官方译名库</h1><p>客户端下载完整译名库后自动校验和更新。</p><ul>' + links(active) +
        '</ul><h2>历史译名包</h2><p>' + notice + '</p><details><summary>查看历史文件</summary><ul>' + links(historical) +
        '</ul></details><p><a href="index.json">机器索引及 SHA-256</a></p></html>\n', encoding="utf-8")
    (target / f"{args.version}.zip.sha256").write_text(f'{hashlib.sha256(archive.read_bytes()).hexdigest()}  {archive.name}\n', encoding="utf-8")
    print(json.dumps({"archive": str(archive), "translation_files": len(payload) - 5,
                      "zip_bytes": archive.stat().st_size, "sha256": hashlib.sha256(archive.read_bytes()).hexdigest()}, ensure_ascii=False))


if __name__ == "__main__":
    main()
