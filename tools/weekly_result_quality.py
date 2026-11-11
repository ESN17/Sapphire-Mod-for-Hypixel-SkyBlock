"""Restore only unambiguous formatting anchors; never guess a missing stat or translated word."""
import collections,re,unicodedata
TOKEN=re.compile(r'\{\{[^{}]+\}\}|\{[^{}]+\}|[+-]?\d+(?:[,.]\d+)*%?')
def icon(c):return ord(c)>127 and (unicodedata.category(c).startswith('S') or unicodedata.category(c) in ('Co','No','Nl'))
def icons(text):return collections.Counter(c for c in text if icon(c))
def restore(source,result):
    text=result['text'];maps={a.strip():b.strip() for a,b in result.get('styleMappings',{}).items() if a.strip() and b.strip() and not re.fullmatch(r'\{\{[^{}]+\}\}|\{[^{}]+\}',a.strip())}
    for english,before,after in [('Glacite Mineshaft','冰川矿井','冰晶矿井'),('Carnival','狂欢节','嘉年华')]:
        if english in source:
            text=text.replace(before,after);maps={a:b.replace(before,after) for a,b in maps.items()}
    for m in TOKEN.finditer(source):
        if source.count(m.group())!=1 or text.count(m.group())!=1:continue
        end=m.end()
        while end<len(source) and icon(source[end]):end+=1
        fragment=source[m.end():end]
        if fragment and all(text.count(c)<source.count(c) for c in set(fragment)):
            text=text.replace(m.group(),m.group()+fragment,1)
    # Decorative prefixes/suffixes occupy the same position in item names.
    prefix='';suffix=''
    for c in source:
        if not (icon(c) or c.isspace()):break
        prefix+=c
    for c in source[::-1]:
        if not (icon(c) or c.isspace()):break
        suffix=c+suffix
    for fragment,before in [(prefix,True),(suffix,False)]:
        glyphs=[c for c in fragment if icon(c)]
        if glyphs and all(text.count(c)<source.count(c) for c in set(glyphs)):
            text=fragment+text if before else text+fragment
    for a,b in sorted(maps.items(),key=lambda e:-len(e[0])):
        if not a.strip() or source.count(a)!=1 or text.count(b)!=1:continue
        at=source.index(a);start=at
        while start>0 and (icon(source[start-1]) or source[start-1].isspace()):start-=1
        fragment=source[start:at].lstrip()
        glyphs=[c for c in fragment if icon(c)]
        if glyphs and all(text.count(c)<source.count(c) for c in set(glyphs)):text=text.replace(b,fragment+b,1)
    # Map translated time suffixes as a whole numeric span, preserving live value colors.
    units={'s':['秒'],'m':['分钟','分'],'h':['小时'],'d':['天'],'k':['千'],'x':['倍']}
    for m in re.finditer(r'(\{\{[^{}]+\}\}|\{[^{}]+\})([smhdkx])(?!\w)',source):
        token,unit=m.groups()
        if unit=='s':text=text.replace(m.group(),token+'秒')
        for translation in units[unit]:
            found=re.search(re.escape(token)+r'\s*'+translation,text)
            if found:maps.setdefault(m.group(),found.group());break
    return dict(result,text=text,styleMappings=maps)
