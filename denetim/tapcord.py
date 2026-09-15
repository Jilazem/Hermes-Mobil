#!/usr/bin/env python3
"""Denetim aracı: uiautomator dump içinde metin/desc ile düğüm bulup
dokunma koordinatını (merkez) yazdırır.

Kullanım: python3 denetim/tapcord.py <dump.xml> <metin-parçası> [--all]
Çıktı: her eşleşme için "cx cy <metin>" satırı.
"""
import sys
import xml.etree.ElementTree as ET


def center(bounds: str):
    m = __import__("re").match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds or "")
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def main() -> int:
    if len(sys.argv) < 3:
        print("kullanim: tapcord.py <dump.xml> <metin> [--all]")
        return 2
    path, needle = sys.argv[1], sys.argv[2].lower()
    show_all = "--all" in sys.argv
    root = ET.parse(path).getroot()
    found = 0
    for node in root.iter():
        for attr in ("text", "content-desc"):
            v = node.get(attr)
            if v and needle in v.lower():
                c = center(node.get("bounds", ""))
                print(f"{c[0]} {c[1]} {v!r}" if c else f"- - {v!r}")
                found += 1
                break
        else:
            continue
    if not found:
        print(f"BULUNAMADI: {needle!r}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
