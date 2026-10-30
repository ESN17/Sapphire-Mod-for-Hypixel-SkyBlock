"""Repair only Wiki candidates rejected by the real Java rendering audit."""
import concurrent.futures
import collections
import json
import re
import time
import argparse
import translate_wiki_corpus as t
from repair_bulk_styles import chunks
from bulk_translate import punctuation


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--batch-size',type=int,default=6);args=parser.parse_args()
    plan=t.read(t.STATE/'plan.json');t.configure_npcs(plan)
    failures=t.read(t.ROOT/'build/reports/wiki-style-failures.json')
    by_source={r['id']:r for r in plan['rows']};jobs={}
    for failure in failures:
        row=by_source.get(failure['sourceId'])
        if not row:continue
        key=row['job'];job=jobs.setdefault(key,dict(plan['jobs'][key],style_variants=[],runtime_errors=[]))
        raw=' '.join(row.get('sampleRaw',row['source']).split())
        styled=t.normalize(raw)[1]
        if styled not in job['style_variants'] and len(job['style_variants'])<6:job['style_variants'].append(styled)
        reason=failure.get('reason','NPC prefix/base color or equivalent numeric rule failed')
        if reason not in job['runtime_errors']:job['runtime_errors'].append(reason)
    batch_size=max(1,min(6,args.batch_size))
    pending=list(jobs.values());batches=[pending[i:i+batch_size] for i in range(0,len(pending),batch_size)]
    system=t.SYSTEM+'''
这是显示引擎拒绝的译文修复。previous 是已有译文，请尽量只修复 styleMappings，确实无法区分彩色词时再改写译文。
style_runs 给出了全部单色片段。每个与底色不同的片段必须有中文颜色映射，尤其是整段说明、单位和深灰色括号。
允许完整单色短语的数字模板映射，例如 "{{N0}} Motes":"{{N0}}裂隙尘埃"、"(Max {{N1}} stacks)":"(上限 {{N1}} 层)"，必须保留同样占位符，不能混入不同颜色。
不能把不同颜色的整句话作为一个映射。重复数字只靠数字模板定位，不要把单个 {{N0}} 作为映射。
同词不同颜色请使用完整单色短语区分，并在译文中用可唯一定位的对应短语。保留原文半角标点风格，以免丢失标点的独立颜色。
如果 frozen_translation=true，text 必须与 previous.text 完全一致，只修复颜色映射。'''
    system+='''
previous 是已经在真实引擎中再次失败的结果，不能原样返回。runtime_errors 指出仍无法定位的颜色词。
如果英文原词仅一次，但中文译词出现两次，必须改写其中一处使彩色词唯一。例如彩色 love 和灰色 loves 不可都译成“爱”，可用“钟爱”和“喜欢”。
不要把另一个有色词的译文包含在普通灰色子句的映射值中！例如 The Catacombs→地牢 时，Increases ... 不得映射成含“在地牢中”的短语，颜色片段必须一一对应。
尽量不映射灰色/白色的普通底色句，只映射确实不同的颜色、粗体或斜体片段，避免长句吞掉短彩色词。'''
    system+='''
遇到重复的英文词而颜色不同，例如金色 fossil 和白色 If you find a fossil，可以映射整段单色白色子句 "If you find a fossil"→"如果找到化石"，再映射 "fossil"→"化石"；引擎先映射长短语后剩余短词即可唯一定位。这种情况应给白色/灰色片段也增加映射。
逐字彩虹色单词如 §cd§6y§ee§as 只需 dyes→染料，禁止把单个英文字母映射到汉字或标点。
若一个中文词被无关中文词包含，如 Ship→船 而出现船长，改写让对应中文可唯一定位，如 Ship→船只。
必须检查 previous 的每一个有色词，未解决 runtime_errors 时不允许原样照抄。'''
    client=t.DeepSeek();client.config.update(max_output_tokens=14000,timeout_seconds=150)
    errors=[];usage=collections.Counter()
    def work(batch):
        public=[];maps={}
        for job in batch:
            previous=t.read(t.STATE/'cache'/(job['id']+'.json'))
            p,m=t.protect_symbols(job)
            for value in t.WIKI_PLACEHOLDER.findall(job['source'])+t.npc_mentions(job['source']):
                if value not in m.values():m['{{P'+str(len(m))+'}}']=value
            def mask(value):
                if isinstance(value,str):
                    for token,literal in sorted(m.items(),key=lambda x:len(x[1]),reverse=True):value=value.replace(literal,token)
                    return value
                if isinstance(value,dict):return {mask(k):mask(v) for k,v in value.items()}
                if isinstance(value,list):return [mask(v) for v in value]
                return value
            p=mask(job);p.pop('style_variants',None)
            p['previous']=mask(previous);p['frozen_translation']=previous.get('origin','').startswith('existing-')
            p['style_runs']=[chunks(mask(s)) for s in job['style_variants']]
            public.append(p);maps[job['id']]=m
        text=' '.join(j['source'] for j in batch).casefold()
        glossary={s:v for s,v in plan['glossary'].items() if len(s)>2 and s.casefold() in text}
        for attempt in range(3):
            try:
                result,tokens=client.complete(system,dict(entries=public,glossary=glossary))
                lookup={r.get('id'):r for r in result.get('translations',[]) if isinstance(r,dict)}
                done=[];bad=[]
                for job in batch:
                    value=t.restore_symbols(lookup.get(job['id'],{}),maps[job['id']])
                    old=t.read(t.STATE/'cache'/(job['id']+'.json'))
                    if old.get('origin','').startswith('existing-'):value['text']=old['text'];value['origin']=old['origin']
                    elif isinstance(value.get('text'),str):value=punctuation(job['source'],value)
                    problems=t.validate(job,value)
                    if problems:
                        bad.append(dict(id=job['id'],errors=problems))
                        t.write(t.STATE/'style-rejected'/(job['id']+'.json'),dict(result=value,errors=problems))
                        continue
                    value=dict(text=value['text'],styleMappings=t.mappings(job['source'],value['text'],value.get('styleMappings',{})),
                               origin=old['origin'] if old.get('origin','').startswith('existing-') else 'deepseek-style-repair')
                    backup=t.STATE/'before-style-repair'/(job['id']+'.json')
                    if not backup.exists():t.write(backup,old)
                    t.write(t.STATE/'cache'/(job['id']+'.json'),value);done.append(job['id'])
                return done,bad,tokens
            except t.ApiError as exc:
                if not exc.retryable or attempt==2:return [],[dict(id=j['id'],errors=[str(exc)]) for j in batch],{}
                time.sleep(2)
    print('Repairing',len(pending),'unique jobs;',len(batches),'batches',flush=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
        futures=[pool.submit(work,b) for b in batches]
        count=0
        for future in concurrent.futures.as_completed(futures):
            done,bad,tokens=future.result();count+=len(done);errors.extend(bad);usage.update(tokens)
            t.write(t.STATE/'style-repair-progress.json',dict(repaired=count,total=len(pending),errors=errors,usage=dict(usage)))
            if count%30<6:print('Repaired',count,'/',len(pending),'validation errors',len(errors),flush=True)


if __name__=='__main__':main()
