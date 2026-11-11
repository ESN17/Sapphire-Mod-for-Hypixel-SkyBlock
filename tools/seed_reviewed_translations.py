"""Reuse already reviewed translations that pass the new user standard, without discarding them."""
import collections
from pathlib import Path
import bulk_translate as bulk
from dictionary_tool import read

def seed():
    plan=read(bulk.STATE/'plan.json');count=0
    for item in plan['items']:
        path=bulk.BASE/'dictionary-workspace/translations/items'/bulk.filename(item['id'])
        if not path.exists():continue
        old=read(path)
        if not old.get('name'):continue
        candidates=[]
        source,_,values=bulk.normalize(old.get('_original_name',item['original_name']))
        candidates.append(('name',source,old['name'],values,old.get('styleMappings',{})))
        for source,target in old.get('lore',{}).items():
            if target:
                normalized,_,values=bulk.normalize(source)
                candidates.append(('lore',normalized,target,values,old.get('styleMappings',{})))
        for paragraph in old.get('loreParagraphs',[]):
            if paragraph.get('translation'):
                normalized,_,values=bulk.normalize(' '.join(paragraph['source']))
                candidates.append(('lore',normalized,paragraph['translation'],values,paragraph.get('styleMappings',old.get('styleMappings',{}))))
        for kind,source,target,values,styles in candidates:
            if len(values)!=len(set(values)):continue # Ambiguous repeated constants need a reviewed positional mapping.
            for n,value in enumerate(values):target=target.replace(value,'{{N'+str(n)+'}}')
            for unit in ([item['name']] if kind=='name' else item['groups']):
                key=unit['job'];job=plan['jobs'][key]
                if source!=job['source']:continue
                relevant={a:b for a,b in styles.items() if a in source and b in target and not bulk.NUMBER.search(a+b)}
                # Numeric-looking English units (e.g. s) are still regular anchors, not model slots.
                result=bulk.punctuation(source,{'text':target,'styleMappings':relevant})
                if not bulk.validate(job,result):
                    bulk.cache_result(key,result,'existing-reviewed-standardized');count+=1
    print('Reused reviewed translation units meeting the new standard:',count,flush=True)

if __name__=='__main__':seed()
