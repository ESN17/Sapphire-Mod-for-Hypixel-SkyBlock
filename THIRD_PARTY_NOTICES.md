# Third-party translation data

## SkyHanni optional compatibility dictionary

`src/main/resources/defaults/compat/skyhanni-zh_cn.json` contains Chinese adaptations
of display text from [SkyHanniStudios/SkyHanni](https://github.com/SkyHanniStudios/SkyHanni),
release 9.3.0, commit `301459c3f7445af8fe05cb01bfbd679cc7ba2c76`, retrieved 2026-11-02.
Copyright remains with the SkyHanni contributors; this adapted dictionary is
**LGPL-2.1-only**, not CC0. The complete upstream license is bundled as
`defaults/third-party/skyhanni-LICENSE.txt`. Keep this notice and license when
redistributing the data. The editable JSON and `tools/skyhanni_dictionary.py`
are the preferred form for modifying this adaptation; provenance and source
locations are retained in `translation-review/skyhanni-9.3.0/`.

Changes consist of extracting constant display strings, Chinese translation
(DeepSeek-assisted with local review and formatting validation), and constrained
interpolation templates. No SkyHanni executable code is included. Sapphire's
independently written optional adapter does not install SkyHanni or change its
license or dependencies. This is not an official SkyHanni translation project.
See `docs/skyhanni-compatibility.md` for the verified versions and coverage limits.

## Odin display corpus (2026-11-05)

The optional `defaults/compat/odin-zh_cn.json` adapts display text from
[odtheking/Odin](https://github.com/odtheking/Odin), official Fabric release 0.4.0
and source commit `fba58c19f77bebbabd43369d1da913d1d28ae64b` (0.4.1 development).
Copyright (c) 2025 odtheking; BSD-3-Clause. The full license is bundled in
`defaults/third-party/odin-LICENSE.txt` and embedded in distributed dictionary
JSON. Changes: Chinese translation, formatting validation and dynamic templates.
Source locations and exclusions are recorded in `translation-state/odin-20261105/`.
No Odin executable code is included in Sapphire. No endorsement is implied.

The tutorial retrieval corpus additionally retains Wiki article URLs, revisions,
and CC BY-NC-SA 3.0 attribution. It is an extracted, searchable snapshot, not a
live server dataset. Tutorial responses identify their Wiki sources.

## English capture provenance and Sapphire translations

Sapphire used the English game captures from the [SkyBlockZH snapshot of 2026-10-22](https://github.com/BingKKni/SkyBlockZH/tree/b8565af0d1eb9a7e946b7cf6f69867d214c1ea75). The older Sapphire corpus also adapted Chinese translations from that snapshot. The original import counts are recorded in `docs/skyzh-import-report.json`.

In the current development tree, Sapphire independently translated the 8,806 English records with DeepSeek assistance and rebuilt their bilingual style mappings. The new corpus is `defaults/translations/community-sapphire.json`. Source file and record identifiers, English formatting samples and numeric examples are retained for provenance and testing. The historical snapshot supplied the capture collection under GPL-3.0-only. Its license and attribution remain with the data. This notice describes the pinned snapshot; later upstream revisions have their own terms. Older released Sapphire artifacts retain their original notices.

Sapphire also retranslated overlapping entries in the supplementary Wiki corpus from its English sources. Those entries retain their Wiki revisions and license. The legacy archive filename required by older clients is generated from the rewritten Sapphire file at packaging time.

Existing NEU metadata continues to carry its own license in `src/main/resources/defaults/NEU-LICENSE.txt`.

## Supplemental Wiki corpus

The separately stored `community-wiki.json` manifest and `community-wiki/` parts contain adapted text from [Hypixel SkyBlock Wiki contributors](https://hypixelskyblock.minecraft.wiki/), collected on 2026-10-23. This source and the adaptations are licensed under [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/), separately from Sapphire code and the SkyBlockZH corpus. Retain attribution, indicate modifications, and comply with the noncommercial and share-alike terms when redistributing this data. The bundled `wiki-NOTICE.txt` provides attribution and license links; the article history and each entry's source/revision URLs identify the underlying contributions.

The collection contains 3,214 fetched pages and 282 rendered supplements. It extracts NPC dialogue, menu titles, tooltip paragraphs, and candidate system messages; Chinese translations use existing terminology and DeepSeek-assisted translation with display-engine regression checks. Existing dictionaries take priority, and previously imported SkyBlockZH NPC dialogue is excluded. Source snapshots, exclusions, revisions, extraction reports, and translation-installation reports remain in `translation-review/wiki-corpus-20261023/`; the collection and adaptation tools are in `tools/`.

Wiki documentation is not a complete export of Hypixel's server text. Missing references and pages marked incomplete or outdated are recorded in the collection reports. `verifiedIngame: false` remains on imported entries: automated checks establish matching, variables, and display-copy behavior, not live-server completeness or a manual review of every translation. No endorsement by Hypixel or the Wiki is implied.

`source-review-exclusions.json` retains incomplete NPC examples and fixed-width chart examples (their prose captions are imported separately). `display-review-pending.json` retains translated samples that still fail the style-preserving renderer; these rules are not enabled. The status screen shows the pending count. Runtime installation requires a passing audit whose SHA-256 matches the exact staged files, and existing translations are never replaced by these supplemental entries.

The 2026-10-30 creature supplement (`translations/creature-messages.json`) adapts fishing and combat notices from these cached Wiki pages under the same CC BY-NC-SA 3.0 terms. Each extracted notice retains its source and revision URLs. The entity-name reference catalog, including page attribution, is in `translation-review/entities-20261030/catalog.json`; `tools/extract_entity_catalog.py` describes its extraction. Chinese wording was revised and names normalized for display. Existing name translations reused from Sapphire's dictionaries retain their original data notices. The catalog and the known death-message patterns are not a complete server-side export.
