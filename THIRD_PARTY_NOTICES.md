# Third-party translation data

Sapphire includes adapted translation data from [BingKKni/SkyBlockZH](https://github.com/BingKKni/SkyBlockZH), commit `b8565af0d1eb9a7e946b7cf6f69867d214c1ea75`, retrieved on 2026-10-22.

The upstream project and the adapted corpus are licensed under **GPL-3.0-only**. The complete license is included in `src/main/resources/defaults/third-party/skyzh-LICENSE.txt` and installed beside the local dictionaries. This corpus is not covered by Sapphire's original CC0 dedication. The mod metadata identifies both licenses; redistributions containing this material must retain its notices and comply with GPL-3.0-only, including the applicable corresponding-source requirements.

The adapted data is stored separately in `src/main/resources/defaults/translations/community-skyzh.json`. Every record retains its upstream file and record ID. `tools/import_skyzh.py` documents the adaptation: only records marked verified by upstream are considered. Numeric placeholders are converted to typed templates; explicitly linked UI continuation lines are joined into complete paragraphs. Unsupported placeholder types, unreviewed terminology, conflicting translations, and incompatible captured styles are excluded. Local terminology is normalized, and Sapphire's existing dictionaries take priority. No SkyBlockZH Java code was copied.

“Verified by upstream” describes upstream metadata, not a claim that these lines were tested on Hypixel by Sapphire. Local regression tests validate text matching, captured styles, source immutability, and NPC/player-chat boundaries. Import counts and exclusions are recorded in `docs/skyzh-import-report.json`.

Existing NEU metadata continues to carry its own license in `src/main/resources/defaults/NEU-LICENSE.txt`.

## Supplemental Wiki corpus

The separately stored `community-wiki.json` manifest and `community-wiki/` parts contain adapted text from [Hypixel SkyBlock Wiki contributors](https://hypixelskyblock.minecraft.wiki/), collected on 2026-10-23. This source and the adaptations are licensed under [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/), separately from Sapphire code and the SkyBlockZH corpus. Retain attribution, indicate modifications, and comply with the noncommercial and share-alike terms when redistributing this data. The bundled `wiki-NOTICE.txt` provides attribution and license links; the article history and each entry's source/revision URLs identify the underlying contributions.

The collection contains 3,214 fetched pages and 282 rendered supplements. It extracts NPC dialogue, menu titles, tooltip paragraphs, and candidate system messages; Chinese translations use existing terminology and DeepSeek-assisted translation with display-engine regression checks. Existing dictionaries take priority, and previously imported SkyBlockZH NPC dialogue is excluded. Large collection snapshots and intermediate review reports are retained in the development archive and are not included in this source snapshot. The distributed translation entries retain source and revision attribution; the collection and adaptation tools are in `tools/`.

Wiki documentation is not a complete export of Hypixel's server text. Missing references and pages marked incomplete or outdated are recorded in the collection reports. `verifiedIngame: false` remains on imported entries: automated checks establish matching, variables, and display-copy behavior, not live-server completeness or a manual review of every translation. No endorsement by Hypixel or the Wiki is implied.

`source-review-exclusions.json` retains incomplete NPC examples and fixed-width chart examples (their prose captions are imported separately). `display-review-pending.json` retains translated samples that still fail the style-preserving renderer; these rules are not enabled. The status screen shows the pending count. Runtime installation requires a passing audit whose SHA-256 matches the exact staged files, and existing translations are never replaced by these supplemental entries.
