# SkyHanni optional Chinese compatibility

Reference checked on 2026-11-02: official SkyHanni **9.3.0 Beta**, released
2026-10-25. The Modrinth project's source link resolves to
https://github.com/SkyHanniStudios/SkyHanni. Release notes document the move
to this organization. Do not substitute similarly named repositories.

- Release: https://github.com/SkyHanniStudios/SkyHanni/releases/tag/9.3.0
- Source commit: `301459c3f7445af8fe05cb01bfbd679cc7ba2c76`
- Official 26.2 JAR SHA-256: `eaa54e58958f211464a204f31cc2754da378e7b2ce88753ecc41b97dfdad9467`
- Latest beta branch observed: `07716a4366d94b20eb9e1de9f7b853fd9384f220`.
  Translation baseline is the latest published release, not unpublished changes.
- Published Minecraft targets: 26.1/26.1.1/26.1.2 and 26.2. There was **no
  official 26.3 build** at verification time. This bridge does not port SkyHanni
  itself or override its Minecraft dependency. Sapphire development stays 26.3.

## Isolation and operation

Sapphire does not depend on, download, or install SkyHanni. Mixins use optional
string targets, and an early plugin checks the mod ID and method bytecode without
loading optional classes. It requires the measuring, wrapping and drawing APIs
together, so an incompatible update leaves original text. Only SkyHanni's shaded
MoulConfig and its own renderables/chat entry points are affected. Other mods'
configuration libraries, server packets, regex detection, saved settings and item
data are not rewritten.

The independent JSON dictionary is loaded on Sapphire's existing background
worker **only when SkyHanni is installed**. Lookups use exact maps and dynamic
templates indexed by fixed prefix; component results have a bounded cache.
Width calculation and display receive the same translation. The `/sapphire`
settings contain a **SkyHanni 汉化** toggle; turning it off restores original text.

Local edits can be placed in `config/sapphire/compat/skyhanni-zh_cn.json`:

```json
{"translations":{"Visitor Timer":"访客计时器"}}
```

Use `/sapphire reload` after editing. User entries override the bundled map;
server dictionary updates do not overwrite this optional compatibility file.

## Coverage and maintenance

The extraction script reads Kotlin constant concatenations in config classes and
explicit display call sites, preserving file/line provenance. It never executes
the source. The catalog contains 7,122 static strings and 663 constrained dynamic
templates. Translation retains formatting codes, commands, placeholders and
numeric values. These are **candidate coverage counts**, not proof that every
screen or runtime sentence is translated. Some source strings are examples,
developer messages, or labels; generated text and other rendering paths may still
need dedicated adapters. 487 source occurrences were deliberately excluded for
ambiguous/short prefixes, nested expressions or unsupported interpolation.

Sources, translations, pending exclusions and manual review are recorded under
`translation-review/skyhanni-9.3.0/`. The catalog retains source locations and
`verifiedIngame: false`. Machine-assisted wording has format checks; it is not a
claim of comprehensive human linguistic review or Hypixel testing.

To update, verify a new official release and supported Minecraft version, pin its
commit, extract with `tools/skyhanni_dictionary.py --source <official-source>`,
then `--dynamic`. Inspect the diff and exclusions before `--translate`. The
translation cache reuses unchanged source hashes and never replaces a cached
translation automatically. Version/commit constants and provenance must be
updated deliberately. Recheck the optional Mixin API using the official JAR;
run both absence and presence tests before announcing support.

Translations adapted from SkyHanni are LGPL-2.1-only, separate from Sapphire's
own code and other dictionaries. See `THIRD_PARTY_NOTICES.md` and the bundled
`defaults/third-party/skyhanni-LICENSE.txt`. No endorsement by SkyHanni is implied.
