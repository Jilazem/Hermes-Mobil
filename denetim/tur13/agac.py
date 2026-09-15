#!/usr/bin/env python3
"""Dokumden bir metnin cevresindeki dugum agacini basar.

Kullanim: python3 agac.py <xml> <metin-parcasi>
"""
import re
import sys

xml_path, needle = sys.argv[1], sys.argv[2]
with open(xml_path, encoding="utf-8", errors="replace") as fh:
    xml = fh.read()

idx = xml.lower().find(needle.lower())
if idx < 0:
    print("BULUNAMADI")
    raise SystemExit(1)
start = max(0, idx - 1200)
end = min(len(xml), idx + 1200)
chunk = xml[start:end]
# Okunabilirlik: her <node ...> satirini kendi satirina al
chunk = chunk.replace("><", ">\n<")
print(chunk)
print("=== TOPLAM:", len(xml), "karakter")
