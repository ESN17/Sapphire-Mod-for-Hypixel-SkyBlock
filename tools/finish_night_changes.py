"""Deterministic source adjustments for the display-boundary regression checks."""
from pathlib import Path
import json,re
ROOT=Path(__file__).resolve().parents[1]
p=ROOT/'src/main/java/com/sorakoi/sbtranslate/Sapphire.java'
s=p.read_text(encoding='utf8').replace('SapphireRanks.disconnect();','SapphireRanks.disconnect();com.sorakoi.sbtranslate.client.ChatDisplay.clear();')
p.write_text(s,encoding='utf8')
p=ROOT/'src/main/java/com/sorakoi/sbtranslate/client/ClientSmokeTest.java'
s=p.read_text(encoding='utf8')
s=re.sub(r'(\w+)\.getTooltipLines\(Item.TooltipContext.EMPTY,null,TooltipFlag.NORMAL\)',r'displayTooltip(\1)',s)
s=s.replace('var result=overlay.getNameForDisplay(info);','''var raw=overlay.getNameForDisplay(info);
            require(raw.getString().equals(original.getString()),"Tab parsing API remains English");
            Component result;
            try {
                var method=java.util.Arrays.stream(overlay.getClass().getDeclaredMethods()).filter(m->m.getName().endsWith("sapphire$row")).findFirst().orElseThrow();
                method.setAccessible(true);result=(Component)method.invoke(overlay,raw,info);
            }catch(Exception e){throw new IllegalStateException("Actual Tab render hook missing",e);}''')
if 'private static List<Component> displayTooltip' not in s:
 s=s.replace('    private static void require(','''    private static List<Component> displayTooltip(ItemStack stack){
        var raw=stack.getTooltipLines(Item.TooltipContext.EMPTY,null,TooltipFlag.NORMAL);
        var name=stack.getHoverName().getString();
        require(raw.isEmpty() || raw.getFirst().getString().contains(name),"raw tooltip remains parser-facing");
        try {Class.forName("net.minecraft.client.gui.screens.inventory.AbstractContainerScreen");}
        catch(ClassNotFoundException e){throw new IllegalStateException(e);}
        return com.sorakoi.sbtranslate.DisplayTranslationHooks.tooltip(stack,raw);
    }
    private static void require(''')
p.write_text(s,encoding='utf8')
p=ROOT/'src/main/resources/fabric.mod.json';d=json.loads(p.read_bytes())
if 'BSD-3-Clause' not in d['license']:d['license']+=' AND BSD-3-Clause'
if 'Odin' not in d['description']:d['description']+='\n可选兼容汉化：SkyHanni（LGPL-2.1-only）与 Odin（BSD-3-Clause），原项目版权归其作者；未安装时不启用对应兼容代码。'
p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
# Complete the final two reviewed strings while preserving numeric tokens.
import odin_dictionary as odin
c=odin.common;c.STATE=odin.STATE;c.OUT=odin.OUT;c.COMMIT=odin.COMMIT
records=json.loads((c.STATE/'catalog.json').read_bytes());cache=json.loads((c.STATE/'translated.json').read_bytes())
manual={'Highlights the most and 2nd most profitable chests.':'高亮收益最高和第 2 高的宝箱。','Text Scale':'文字缩放'}
for r in records:
 if r['source'] in manual:
  assert c.valid(r['source'],manual[r['source']]),r['source']
  cache[r['id']]=manual[r['source']]
c.dump(c.STATE/'translated.json',cache);c.export(records,cache)
d=json.loads(c.OUT.read_bytes());d['sourceVersion']='0.4.0 / main 2026-11-05';d['license']='BSD-3-Clause';c.dump(c.OUT,d)
