"""Download public source evidence only; no credentials or game data."""
import io,json,urllib.request,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]/'build/weekly-reference'
root.mkdir(parents=True,exist_ok=True)
url='https://codeload.github.com/hannibal002/SkyHanni/zip/refs/heads/beta'
with urllib.request.urlopen(url,timeout=60) as r: data=r.read(80*1024*1024)
count=0
with zipfile.ZipFile(io.BytesIO(data)) as z:
 for n in z.namelist():
  if '/src/' in n and n.endswith(('.kt','.json')):
   rel=Path(*Path(n).parts[1:]);dest=root/'skyhanni'/rel
   dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(z.read(n));count+=1
print(json.dumps({'source':url,'files':count}))
