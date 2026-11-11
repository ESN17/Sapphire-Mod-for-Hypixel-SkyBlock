"""Apply API-only backports to frozen dev006 projects; development stays on 26.3."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]

def adapt(mc, replacements):
    stage = ROOT / 'build' / ('release-dev006-mc' + mc)
    assert ('minecraft_version='+mc) in (stage/'gradle.properties').read_text()
    changed = []
    for path in (stage/'src').rglob('*.java'):
        before = path.read_text(encoding='utf8')
        after = before
        for a,b in replacements:
            after = after.replace(a,b)
        if before != after:
            path.write_text(after,encoding='utf8')
            changed.append(path.relative_to(stage).as_posix())
    if changed:
        report=stage/'api-backport.json'
        if report.exists():
            changed=sorted(set(changed+json.loads(report.read_text(encoding='utf8'))['changed']))
        report.write_text(json.dumps({'minecraft':mc,'changed':changed},indent=2),encoding='utf8')
    print(mc, 'adapted files:', len(changed))

if __name__ == '__main__':
    adapt('26.1.2', [
        ('.gui.hud', '.gui'),
        ('.gui.setScreen(', '.setScreen('),
        ('.gui.screen()', '.screen'),
        ('.gui.overlay()', '.getOverlay()'),
        ('.gameRenderer.mainRenderTarget()', '.getMainRenderTarget()'),
        ('net.minecraft.client.gui.Hud', 'net.minecraft.client.gui.Gui'),
        ('@Mixin(Hud.class)', '@Mixin(Gui.class)'),
        ('net/minecraft/client/gui/Hud;', 'net/minecraft/client/gui/Gui;'),
        ('team.getColor().map(net.minecraft.world.scores.TeamColor::displaySlot).orElse(null)', 'DisplaySlot.teamColorToSlot(team.getColor())'),
        ('net.minecraft.world.scores.TeamColor.valueOf(color.name()).rgb()', 'color.getColor()'),
        ('InputConstants.isKeyDown(key)', 'InputConstants.isKeyDown(Minecraft.getInstance().getWindow(),key)'),
    ])
    adapt('26.2', [
        ('InputConstants.isKeyDown(key)', 'InputConstants.isKeyDown(Minecraft.getInstance().getWindow(),key)'),
    ])
