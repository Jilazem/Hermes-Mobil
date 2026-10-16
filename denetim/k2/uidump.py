import subprocess, xml.etree.ElementTree as ET, sys
out = subprocess.run(["adb","-s","emulator-5554","exec-out","uiautomator","dump","/dev/tty"],capture_output=True,text=True).stdout
start = out.find("<?xml")
root = ET.fromstring(out[start:out.rfind("</hierarchy>")+12])
kw = sys.argv[1].lower() if len(sys.argv)>1 else ""
for n in root.iter("node"):
    t=(n.get("text") or "")+" | "+(n.get("content-desc") or "")
    if kw and kw not in t.lower(): continue
    b=n.get("bounds")
    if n.get("clickable")=="true" or (kw and kw in t.lower()):
        print(f'{t.strip()[:60]:60} clickable={n.get("clickable")} {b}')
