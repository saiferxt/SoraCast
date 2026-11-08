"""Generate release metadata after signing, without keys or GitHub credentials."""
from pathlib import Path
import argparse, hashlib, re
p=argparse.ArgumentParser()
p.add_argument("apk_directory",type=Path)
p.add_argument("--tag",required=True)
p.add_argument("--output",type=Path,default=Path("updates.properties"))
a=p.parse_args()
if not re.fullmatch(r"[A-Za-z0-9._-]+",a.tag):p.error("Invalid release tag")
root=Path(__file__).resolve().parents[1]
lines=["schema=1"]
for role,filename in (("phone","SoraCast_phone.apk"),("tv","SoraCast_TV.apk")):
    gradle=(root/"android"/role/"build.gradle").read_text(encoding="utf-8-sig")
    code=re.search(r"versionCode\s+(\d+)",gradle)[1]
    name=re.search(r"versionName\s+'([^']+)'",gradle)[1]
    sdk=re.search(r"minSdk\s+(\d+)",gradle)[1]
    apk=a.apk_directory/filename
    assert 0<apk.stat().st_size<=200*1024*1024
    fields={"packageName":"app.soracast."+role,"versionCode":code,"versionName":name,"minSdk":sdk,
      "bytes":str(apk.stat().st_size),"sha256":hashlib.file_digest(apk.open("rb"),"sha256").hexdigest(),
      "url":"https://github.com/saiferxt/SoraCast/releases/download/"+a.tag+"/"+filename,
      "notes":"Обновлятор телефона и TV. Проверка подписи и целостности APK. Установка с подтверждением Android."}
    lines.extend(role+"."+key+"="+value for key,value in fields.items())
a.output.write_text("\n".join(lines)+"\n",encoding="utf-8")
print(a.output)
