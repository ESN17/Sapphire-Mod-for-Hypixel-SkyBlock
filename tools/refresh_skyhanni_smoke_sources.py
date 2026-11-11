"""Refresh disposable compatibility test sources, keeping main development on 26.3."""
from pathlib import Path
import shutil
root=Path(__file__).resolve().parents[1];dest=root/'build/skyhanni-presence-26.2'
assert (dest/'build.gradle').is_file()
for path in (root/'src/main/java').rglob('*.java'):
    target=dest/path.relative_to(root);target.parent.mkdir(parents=True,exist_ok=True)
    text=path.read_text(encoding='utf8').replace('InputConstants.isKeyDown(key)','InputConstants.isKeyDown(Minecraft.getInstance().getWindow(),key)')
    target.write_text(text,encoding='utf8')
print('Refreshed isolated 26.2 compatibility fixture; main properties unchanged.')
