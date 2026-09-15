#!/usr/bin/env python3
"""uiautomator dokumundeki TIKLANABILIR dugumleri listeler (bounds + metin).

Kullanim: python3 tiklanabilir.py <xml> [--tap <index>]
"""
import re
import subprocess
import sys

ADB = "/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb"
SERI = "emulator-5554"


def main() -> int:
    xml_path = sys.argv[1]
    with open(xml_path, encoding="utf-8", errors="replace") as fh:
        xml = fh.read()
    rows = []
    for node in re.finditer(r"<node[^>]*>", xml):
        tag = node.group(0)
        attrs = dict(re.findall(r'([a-zA-Z:_-]+)="([^"]*)"', tag))
        if attrs.get("clickable") != "true":
            continue
        m = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", tag)
        if not m:
            continue
        x1, y1, x2, y2 = (int(v) for v in m.groups())
        text = (attrs.get("text", "") + " | " + attrs.get("content-desc", "")).strip(" |")
        rows.append((y1, x1, y1, x2, y2, attrs.get("class", ""), text))
    for i, (_, x1, y1, x2, y2, cls, text) in enumerate(rows):
        print(f"[{i}] ({x1},{y1})-({x2},{y2}) {cls} :: {text[:70]}")
    if "--tap" in sys.argv:
        idx = int(sys.argv[sys.argv.index("--tap") + 1])
        _, x1, y1, x2, y2, _, text = rows[idx]
        cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
        subprocess.run([ADB, "-s", SERI, "shell", "input", "tap", str(cx), str(cy)], check=False)
        print(f"TIKLANDI [{idx}] ({cx},{cy}) :: {text[:50]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
