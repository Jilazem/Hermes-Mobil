#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""TUR-14 denetmen UI probe-2: gun ayraci + kaynak chip kaniti.

denetmen-sohbet-dokun.xml icinde gun ayraci etiketi (Bugun/Dun/gg.aa.yyyy)
ve denetmen-tab-oturumlar.xml icinde Tumu/Geçmis sekmeleri aranir.
Ayrica Tumu sekmesine dokunup kart dökümü alinir (source chip kaniti).
"""
import re
import subprocess
import time

ADB = "/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb"
OUT = "/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur14"


def sh(*args):
    r = subprocess.run([ADB] + list(args), capture_output=True, text=True, timeout=60)
    return r.stdout + r.stderr


def dump_save(path):
    sh("shell", "uiautomator", "dump", "/data/local/tmp/denprobe2.xml")
    xml = sh("shell", "cat", "/data/local/tmp/denprobe2.xml")
    with open(path, "w", encoding="utf-8") as f:
        f.write(xml)
    return xml


def texts(xml):
    return [t for t in re.findall(r'text="([^"]+)"', xml) if t.strip()]


def bounds_of(xml, label):
    for m in re.finditer(r'<node[^>]*text="([^"]*)"[^>]*?bounds="(\[[^"]+\])"', xml):
        if m.group(1) == label:
            return m.group(2)
    return None


def tap_center(b):
    x1, y1, x2, y2 = [int(v) for v in re.findall(r"-?\d+", b)]
    sh("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


# 1) Sohbet dökümünde gün ayracı ara
with open(f"{OUT}/denetmen-sohbet-dokun.xml", encoding="utf-8") as f:
    sohbet = f.read()
# Ayraç adayları: Bugün/Dün ya da gg.aa.yyyy deseni
aday = [t for t in texts(sohbet)
        if t in ("Bugün", "Dün", "Today", "Yesterday") or re.match(r"\d{2}\.\d{2}\.\d{4}", t)]
print("SOHBET gun ayraci adaylari:", aday)

# 2) Geri dön (probe-1 BACK atti; su an Oturumlar'da olmali), Tumu sekmesini dene
xml = dump_save(f"{OUT}/denetmen-tab-sonraki.xml")
print("Sekme adaylari:", [t for t in texts(xml) if t in ("Canlı", "Tümü", "Geçmiş")])

for hedef in ("Tümü", "Geçmiş"):
    b = bounds_of(xml, hedef)
    if b:
        print("Dokunuluyor:", hedef)
        tap_center(b)
        time.sleep(2)
        xml = dump_save(f"{OUT}/denetmen-tab-{hedef.lower()}.xml")
        ts = texts(xml)
        print(f"{hedef} ilk 14 metin:")
        for t in ts[:14]:
            print("  |", t)
        break

print("PROBE2 TAMAM")
