#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""TUR-14 denetmen UI probe — salt-okunur UI gezinmesi (kod degisikligi yok).

Adimlar: Oturumlar sekmesine gec -> kart dökümü (source chip arama) ->
ilk karta dokun -> sohbet ekranı dökümü (dokun->sohbet kanıtı) -> geri.
Cikti: denetim/tur14/denetmen-tab-oturumlar.xml, denetmen-sohbet-dokun.xml
"""
import re
import subprocess
import sys
import time

ADB = "/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb"
OUT_DIR = "/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur14"


def sh(*args):
    r = subprocess.run([ADB] + list(args), capture_output=True, text=True, timeout=60)
    return r.stdout + r.stderr


def dump_save(path):
    sh("shell", "uiautomator", "dump", "/data/local/tmp/denprobe.xml")
    xml = sh("shell", "cat", "/data/local/tmp/denprobe.xml")
    with open(path, "w", encoding="utf-8") as f:
        f.write(xml)
    return xml


def texts(xml):
    return [t for t in re.findall(r'text="([^"]+)"', xml) if t.strip()]


def find_bounds(xml, label):
    # label metnini tasiyan node'un bounds'unu dondur
    for m in re.finditer(r'<node[^>]*text="([^"]*)"[^>]*bounds="(\[[^"]+\])"', xml):
        if m.group(1) == label:
            return m.group(2)
    return None


def tap_center(bounds):
    x1, y1, x2, y2 = [int(v) for v in re.findall(r"-?\d+", bounds)]
    sh("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


xml0 = dump_save(f"{OUT_DIR}/denetmen-tab-canli.xml")
print("ADIM0 ekran metinleri:", texts(xml0)[:8])

b = find_bounds(xml0, "Oturumlar")
if not b:
    print("HATA: Oturumlar sekmesi bulunamadi")
    sys.exit(1)
tap_center(b)
time.sleep(2)

xml1 = dump_save(f"{OUT_DIR}/denetmen-tab-oturumlar.xml")
print("ADIM1 (Oturumlar) metinleri:")
for t in texts(xml1):
    print("  |", t)

# Ilk oturum kartina dokun (ilk ayrica zaman tasiyan kart satiri)
kart = None
for t in ("ICRA raporları V2 kuralları güncelleme", "Sexy snake oyunu yaz"):
    kart = find_bounds(xml1, t)
    if kart:
        print("Dokunulan kart:", t)
        break
if kart:
    tap_center(kart)
    time.sleep(2.5)
    xml2 = dump_save(f"{OUT_DIR}/denetmen-sohbet-dokun.xml")
    print("ADIM2 (sohbet) metinleri:")
    for t in texts(xml2)[:15]:
        print("  |", t)
    sh("shell", "input", "keyevent", "4")  # BACK
    time.sleep(1.5)
else:
    print("HATA: dokunulabilir kart bulunamadi")

print("PROBE TAMAM")
