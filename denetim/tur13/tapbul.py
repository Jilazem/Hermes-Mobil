#!/usr/bin/env python3
"""uiautomator dokumunde metne gore dugum bulup merkez koordinatini yazar.

Kullanim: python3 tapbul.py <xml> <metin-parcasi> [--tap]
Tahmini koordinat YOK: koordinat dokumden gelir (tur-12 dersi).
"""
import re
import subprocess
import sys

ADB = "/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb"
SERI = "emulator-5554"


def main() -> int:
    xml_path = sys.argv[1]
    needle = sys.argv[2]
    tap = "--tap" in sys.argv
    with open(xml_path, encoding="utf-8", errors="replace") as fh:
        xml = fh.read()
    hits = []
    for node in re.finditer(r"<node[^>]*>", xml):
        tag = node.group(0)
        attrs = dict(re.findall(r'([a-zA-Z:_-]+)="([^"]*)"', tag))
        text = (attrs.get("text", "") + " " + attrs.get("content-desc", "")).strip()
        if needle.lower() in text.lower():
            m = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", tag)
            if not m:
                continue
            x1, y1, x2, y2 = (int(v) for v in m.groups())
            hits.append((text, (x1 + x2) // 2, (y1 + y2) // 2, attrs.get("class", "")))
    if not hits:
        print("BULUNAMADI:", needle)
        return 1
    for text, cx, cy, cls in hits:
        print(f"DOKUM: '{text}' class={cls} merkez=({cx},{cy})")
    if tap:
        _, cx, cy, _ = hits[0]
        subprocess.run([ADB, "-s", SERI, "shell", "input", "tap", str(cx), str(cy)], check=False)
        print(f"TIKLANDI: ({cx},{cy})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
