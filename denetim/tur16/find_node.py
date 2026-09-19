#!/usr/bin/env python3
"""Tur16 kanıt: uiautomator dökümünden hedef düğümü bulur, merkez koordinatı basar."""
import subprocess, sys, re
import xml.etree.ElementTree as ET

ADB = "/Volumes/EX/007-HERMES-M4-LIVE/20-ARACLAR/android-sdk/platform-tools/adb"
needle = sys.argv[1]
subprocess.run([ADB, "-s", "emulator-5554", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
raw = subprocess.run([ADB, "-s", "emulator-5554", "shell", "cat", "/sdcard/ui.xml"], capture_output=True).stdout.decode("utf-8", "replace")
root = ET.fromstring(raw)
for n in root.iter():
    a = n.attrib
    hay = (a.get("text", "") + "|" + a.get("content-desc", "") + "|" + a.get("class", ""))
    if needle.lower() in hay.lower():
        b = re.findall(r"-?\d+", a.get("bounds", ""))
        if len(b) == 4:
            cx, cy = (int(b[0]) + int(b[2])) // 2, (int(b[1]) + int(b[3])) // 2
            print(f"{a.get('class')} | text={a.get('text','')[:40]!r} | desc={a.get('content-desc','')[:40]!r} | bounds={a.get('bounds')} | CENTER={cx},{cy}")
