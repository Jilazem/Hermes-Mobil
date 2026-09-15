#!/usr/bin/env python3
"""Denetim aracı: dump içindeki metin düğümlerini sınırlarıyla listeler.

Kullanım: python3 denetim/nodes_bounds.py <dump.xml> [alt-dizi]
"""
import sys
import xml.etree.ElementTree as ET

path = sys.argv[1]
needle = sys.argv[2].lower() if len(sys.argv) > 2 else ""

root = ET.parse(path).getroot()
for node in root.iter("node"):
    text = node.get("text") or node.get("content-desc") or ""
    if needle and needle not in text.lower():
        continue
    if text.strip():
        print(f"{node.get('bounds')} {node.get('class','').split('.')[-1]}: {text[:80]!r}")
