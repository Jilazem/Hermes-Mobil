#!/usr/bin/env python3
"""Tur-11 UI kanıtı: dökümde sesli mesaj öğelerini ara (Türkçe karakterli
sorgular terminal grep'inde Tirith'e takılabildiği için dosyadan okunur)."""
import re
import sys

KEYS = ["tut", "Sesli", "sesli", "Mikrofon", "Kayd", "motor", "kahya", "otomatik", "Dinliyorum"]

for path in sys.argv[1:]:
    with open(path, encoding="utf-8") as fh:
        xml = fh.read()
    found = []
    for m in re.finditer(r'content-desc="([^"]*)"', xml):
        if any(k.lower() in m.group(1).lower() for k in KEYS):
            found.append(("desc", m.group(1)))
    for m in re.finditer(r'text="([^"]*)"', xml):
        if any(k.lower() in m.group(1).lower() for k in KEYS):
            found.append(("text", m.group(1)))
    print(f"--- {path}: {len(found)} eşleşme")
    for kind, value in dict.fromkeys(found):
        print(f"    [{kind}] {value}")
