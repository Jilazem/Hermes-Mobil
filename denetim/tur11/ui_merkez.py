#!/usr/bin/env python3
"""uiautomator dökümünden bir metnin merkez koordinatını çıkarır (tahmin yok).

Kullanım: python3 ui_merkez.py <dump.xml> "Ayarlar"
Çıktı:    "X Y" (yoksa boş)
"""
import re
import sys

path, needle = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as fh:
    xml = fh.read()

# <node ... text="Ayarlar" ... bounds="[x1,y1][x2,y2]"> — node'ları tek tek ayır.
for node in re.finditer(r"<node\b[^>]*>", xml):
    tag = node.group(0)
    text = re.search(r'(?:text|content-desc)="([^"]*)"', tag)
    if not text or needle.lower() not in text.group(1).lower():
        continue
    bounds = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
    if not bounds:
        continue
    x1, y1, x2, y2 = (int(v) for v in bounds.groups())
    # Alt sekme çubuğu jest bölgesinde olabilir: hedefi biraz yukarı al.
    cy = (y1 + y2) // 2
    if y2 > 2250:
        cy = 2250 - 20
    print(f"{(x1 + x2) // 2} {cy}  # ({text.group(1)} bounds=[{x1},{y1}][{x2},{y2}])")
    break
