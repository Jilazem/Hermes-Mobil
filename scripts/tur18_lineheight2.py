#!/usr/bin/env python3
# Tur18 B3 cok-satirli kalanlar: `lineHeight = N.sp` satirindan geriye en yakin
# 'Text(' veya 'BasicText(' acilisina kadar olan arguman blogunda
# MaterialTheme.typography rolu varsa override silinir (rol ezer, fontScale
# kirpilma riski kapanir). Markdown govde korumalari: bodyLine / fontSize param.
import re, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/java/com/hermes/mobile"
total = 0
for p in sorted(ROOT.rglob("*.kt")):
    if "/theme/" in str(p):
        continue
    lines = p.read_text().split("\n")
    keep = list(lines)
    for i, l in enumerate(lines):
        if re.match(r"^\s*lineHeight = \d+\.sp,\s*$", l):
            j = i - 1
            start = None
            while j >= 0 and i - j < 30:
                if re.search(r"\bText\(|\bBasicText\(", lines[j]):
                    start = j
                    break
                j -= 1
            if start is None:
                continue
            block = "\n".join(lines[start:i + 1])
            if "MaterialTheme.typography." in block and "bodyLine" not in block and "fontSize = fontSize" not in block:
                keep[i] = None
                total += 1
    if total and any(k is None for k in keep):
        out = [k for k in keep if k is not None]
        p.write_text("\n".join(out))
print("kaldirilan blok-ici lineHeight:", total)
