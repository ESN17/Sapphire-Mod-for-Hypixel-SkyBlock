"""Fill unambiguous bilingual anchors using translations already present in this batch.

Never changes text, numbers, or source data. Runtime audit still decides whether
the resulting full Style alignment is valid.
"""
import collections
import copy
import re
import bulk_translate as bulk


def uniform_phrase(job, phrase):
    text = ''; styles = []; color = None; flags = set()
    for part in re.split(r'(§[0-9a-fk-or])', job['styled_source']):
        if re.fullmatch(r'§[0-9a-fk-or]', part):
            code = part[1]
            if code in '0123456789abcdefr':
                color = code; flags = set()
            else:
                flags.add(code)
        else:
            text += part; styles.extend([(color, tuple(sorted(flags)))] * len(part))
    found = list(re.finditer(r'(?<![A-Za-z])' + re.escape(phrase) + r'(?![A-Za-z])', text))
    for match in found:
        span_styles={styles[i] for i in range(match.start(),match.end()) if not text[i].isspace()}
        if len(span_styles)==1:return True
        if re.fullmatch('[A-Za-z]{3,}',phrase) and len({s[0] for s in span_styles})>=3 and len({s[1] for s in span_styles})==1:return True
    return False


def prune_overlaps(source, target, mappings):
    selected = {}; source_ranges = []; target_ranges = []
    for a,b in sorted(mappings.items(), key=lambda pair:(-len(pair[0]),pair[0])):
        ss=[(m.start(),m.end()) for m in re.finditer(r'(?<![A-Za-z])'+re.escape(a)+r'(?![A-Za-z])',source)]
        ts=[(m.start(),m.end()) for m in re.finditer(re.escape(b),target)]
        ss=[p for p in ss if not any(p[0]<q[1] and q[0]<p[1] for q in source_ranges)]
        ts=[p for p in ts if not any(p[0]<q[1] and q[0]<p[1] for q in target_ranges)]
        if ss and len(ss)==len(ts):
            selected[a]=b; source_ranges.extend(ss); target_ranges.extend(ts)
    return selected


def enrich():
    plan = bulk.read(bulk.STATE / 'plan.json')
    lexicon = collections.defaultdict(set)
    cached = {key: bulk.cached(key) for key in plan['jobs']}
    for result in cached.values():
        if result:
            for source, target in result.get('styleMappings', {}).items():
                if bulk.SLOT.search(source+target):continue # Slot numbers are local to one job, never a reusable glossary.
                lexicon[source].add(target)
    for source, target in bulk.POLICY['terms'].items():
        lexicon[source].add(target)
    for source, targets in {
        'Level': ['等级'], 'Enchanting Level': ['附魔等级'], 'Axe': ['斧'],
        'Bow': ['弓'], 'Vitality': ['生命回复'], 'True Defense': ['真实防御'],
        'Ferocity': ['凶残'], 'Max': ['上限'], 's': ['秒'],
        'Rainbow':['彩虹'], 'Dyes':['染料'], 'Prismatic':['提纯','棱彩'],
        'blocks':['格'], 'block':['格'], 'seconds':['秒'], 'second':['秒'],
        'Tree':['树'], 'Trees':['树'], 'Copper':['铜'], 'Sheep':['羊'], 'Ship':['船'],
        'Dragons':['龙'], 'Dragon':['龙'], 'Egg':['蛋'], 'Fish':['鱼'],
        'Top':['前'], 'GOLD':['金'], 'SILVER':['银'], 'Year':['年'], 'years':['年'],
        'Lvl':['等级'], 'Level':['等级','级'], 'Gold':['金'], 'pieces':['件'],
        'Ding':['叮'], 'Rat':['鼠'], 'fire':['火'], 'sparkle':['闪光'], 'meow':['喵喵叫'],
    }.items():
        lexicon[source].update(targets)
    changed = 0
    for key, job in plan['jobs'].items():
        result = cached[key]
        if not result:
            continue
        result=bulk.punctuation(job['source'],copy.deepcopy(result))
        anchors = {a:b for a,b in result.get('styleMappings', {}).items() if (a==b or uniform_phrase(job,a))
                   and (not bulk.SLOT.search(a) or len(bulk.SLOT.sub('',a).strip())>=2)
                   and not bulk.SLOT.match(a) # Numeric-led ranges can hide a style transition inside the source number.
                   and not (len(b)==1 and a.lower() in ('grants','gain','every','for','and','the','your','in','at','by','of','to','a','an','is','with','on','per','from','when','this','that','it'))}
        for source, targets in lexicon.items():
            if source in anchors or source not in job['source']:
                continue
            if source.lower() in ('grants','gain','every','for','and','the','your','in','at','by','of','to','a','an','is','with','on','per','from','when','this','that','it'):
                continue
            matches = [target for target in targets if target in result['text']]
            if len(matches) == 1 and uniform_phrase(job,source):
                anchors[source] = matches[0]
        anchors=prune_overlaps(job['source'],result['text'],anchors)
        candidate = dict(result, styleMappings=anchors)
        if candidate != cached[key] and not bulk.validate(job, candidate):
            bulk.cache_result(key, candidate, result.get('origin', 'deepseek'))
            changed += 1
    print('Enriched unambiguous style mappings:', changed, flush=True)


if __name__ == '__main__':
    enrich()
