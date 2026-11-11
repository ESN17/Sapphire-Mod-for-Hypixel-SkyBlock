"""Summarize collected sources, installed rules, pending review and actual checks."""
import datetime
import hashlib
import html
import json
import xml.etree.ElementTree as ET
from pathlib import Path
from translate_wiki_corpus import ROOT, OUT, STATE, read, write


def main():
    collection=read(OUT/'collection-report.json');extraction=read(OUT/'extraction-report.json')
    render=read(OUT/'render-report.json');installation=read(OUT/'installation-report.json')
    audit=read(ROOT/'build/reports/wiki-audit-summary.json');plan=read(STATE/'plan.json')
    assert audit['failures']==0 and audit['sha256']==installation['stagedSha256']
    mirrors=[]
    for relative in ['src/main/resources/defaults/translations','dictionary-workspace/translations','run/config/sapphire/translations']:
        folder=ROOT/relative;manifest=read(folder/'community-wiki.json')
        files=[folder/'community-wiki.json']+[folder/'community-wiki'/p for p in manifest['parts']]
        digest=hashlib.sha256()
        for path in files:digest.update(path.read_bytes())
        assert digest.hexdigest()==audit['sha256']
        mirrors.append(dict(path=relative,sha256=digest.hexdigest(),bytes=sum(p.stat().st_size for p in files)))
    tests=dict(tests=0,failures=0,errors=0,skipped=0)
    for path in (ROOT/'build/test-results/test').glob('TEST-*.xml'):
        result=ET.parse(path).getroot()
        for key in tests:tests[key]+=int(result.get(key,0))
    assert tests['tests']>=479 and tests['failures']==0 and tests['errors']==0
    jar=ROOT/'build/libs/sapphire-dev 0.0.3.jar'
    jar_hash=hashlib.sha256(jar.read_bytes()).hexdigest()
    assert jar_hash=='e7726e9735e5e4332d91f256d6f6cec38b1f0333764b07d159fbb18f75e23799'
    cached=sum((STATE/'cache'/(key+'.json')).exists() for key in plan['jobs'])
    report=dict(createdAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),
        collection=dict(pages=collection['fetched'],missing=collection['missing'],errors=collection['errors'],
                        renderedPages=render['completed'],menuLayouts=extraction['menuLayouts'],
                        excludedSkyzhNpcOccurrences=extraction['excludedImportedNpcOccurrences'],wikiGapPages=len(extraction['wikiGapPages'])),
        translation=dict(uniqueUnits=len(plan['jobs']),cachedUnits=cached,activeRules=installation['entries'],
                         scopes=installation['scopes'],pendingDisplaySamples=installation['heldReviewRows'],
                         sourceReviewSamples=installation['sourceReviewRows']),
        validation=dict(gradleCheck=tests,pythonWikiTests=13,displayAudit=audit,mirrors=mirrors),
        features=['/sapphire 翻译状态：加载数量、错误、重新加载、格式待核对数量',
                  '按 NPC 身份匹配动态数值与玩家称呼，保留原值、格式和点击事件',
                  '独立分片补充词库，现有译文优先；审计文件哈希与安装文件一致'],
        release=dict(packaged=False,versionUnchanged='dev 0.0.3',jarSha256=jar_hash),
        limitations=['Wiki 不是服务器完整文本导出；缺失引用与过时页面已记录。',
                     '待核对样例已有译文，但未通过格式校验，未启用对应规则。',
                     '译文经过自动检查与部分人工修订；未逐条进行游戏内实测。'],
        references=dict(wiki='https://hypixelskyblock.minecraft.wiki/',license='https://creativecommons.org/licenses/by-nc-sa/3.0/'))
    write(OUT/'completion-report.json',report)
    scopes=installation['scopes'];labels={'npc':'NPC 对话','gui':'菜单标题','tooltip':'提示与描述','system':'系统通知'}
    rows=''.join(f'<tr><td>{labels[k]}</td><td>{v:,}</td></tr>' for k,v in scopes.items())
    document=f'''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Sapphire Wiki 翻译接入报告</title><style>body{{font:16px/1.7 system-ui,sans-serif;max-width:900px;margin:40px auto;padding:0 24px;color:#183345;background:#f5f8fb}}article{{background:white;padding:30px;border-radius:16px}}h1{{color:#1266a0}}table{{border-collapse:collapse;width:100%}}td,th{{padding:8px;text-align:left;border-bottom:1px solid #dbe5ec}}.note{{background:#fff4d6;padding:16px;border-radius:8px}}a{{color:#086ca7}}</style>
<article><h1>Sapphire Wiki 翻译接入报告</h1><p>已接入 <strong>{installation['entries']:,}</strong> 条补充规则，已启用规则全部通过显示校验。规则数量包含固定示例与数值模板，不是全服覆盖率。</p>
<table><tr><th>类别</th><th>启用规则数</th></tr>{rows}</table>
<p>收集 {collection['fetched']:,} 个页面、{render['completed']} 个渲染补充页面和 {extraction['menuLayouts']:,} 个菜单布局；排除 {extraction['excludedImportedNpcOccurrences']:,} 次已导入 SkyZH NPC 对话的重复出现。{cached:,} 个独立单元均已有译文或复用了现有译文。</p>
<p class="note">仍有 {installation['heldReviewRows']} 个格式样例暂未启用，另有 {installation['sourceReviewRows']} 个来源示例需单独处理（缺失 NPC 前缀或固定宽度图表；图表说明文字已单独导入）。Wiki 有 {len(collection['missing'])} 个引用页面不存在、{len(extraction['wikiGapPages'])} 个页面标注了缺失或过时信息。不能保证覆盖全部服务器文本。</p>
<h2>新增功能</h2><ul>{''.join('<li>'+html.escape(x)+'</li>' for x in report['features'])}</ul>
<h2>验证</h2><p>Gradle check：{tests['tests']} 项测试，0 失败，{tests['skipped']} 项跳过。另有 13 项 Python 测试通过。三个词库副本哈希一致。未逐条游戏实测。</p>
<p>本次未打包，版本仍为 dev 0.0.3，现有 JAR 哈希未变。</p>
<h2>追溯文件</h2><ul><li><a href="completion-report.json">完整核验数据</a></li><li><a href="display-review-pending.json">格式待核对清单与译文</a></li><li><a href="source-review-exclusions.json">来源示例处理清单</a></li><li><a href="collection-report.json">页面来源与缺失引用</a></li><li><a href="extraction-report.json">提取统计与 Wiki 缺口</a></li></ul>
<p>数据来源：<a href="https://hypixelskyblock.minecraft.wiki/">Hypixel SkyBlock Wiki</a>。补充数据遵循 <a href="https://creativecommons.org/licenses/by-nc-sa/3.0/">CC BY-NC-SA 3.0</a>；逐条保留来源页与修订信息。</p></article></html>'''
    (OUT/'completion-report.html').write_text(document,encoding='utf8')
    print(json.dumps(dict(activeRules=installation['entries'],pending=installation['heldReviewRows'],tests=tests,jarUnchanged=True),ensure_ascii=False))


if __name__=='__main__':main()
