#!/usr/bin/env python3
"""Bir dökümdeki tüm görünür metinleri basar (kaydırma teşhisi için)."""
import re
import sys

path = sys.argv[1]
with open(path, encoding="utf-8") as fh:
    xml = fh.read()
seen = []
for node in re.finditer(r"<node\b[^>]*>", xml):
    tag = node.group(0)
    for attr in ("text", "content-desc"):
        m = re.search(attr + r'="([^"]+)"', tag)
        if m and m.group(1).strip():
            seen.append(m.group(1).strip())
for line in dict.fromkeys(seen):
    print(line)
