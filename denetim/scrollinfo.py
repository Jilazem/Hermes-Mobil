#!/usr/bin/env python3
"""Denetim aracı: dump içindeki kaydırılabilir düğümleri listeler.

Kullanım: python3 denetim/scrollinfo.py <dump.xml>
"""
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
found = False
for node in root.iter("node"):
    if node.get("scrollable") == "true":
        found = True
        print(
            "SCROLLABLE:",
            node.get("class"),
            "pkg=" + str(node.get("package")),
            node.get("bounds"),
        )
if not found:
    print("kaydırılabilir düğüm YOK")
