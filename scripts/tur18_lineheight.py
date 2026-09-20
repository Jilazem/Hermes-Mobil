#!/usr/bin/env python3
# Tur18 B3: ayni satirda role donusmus Text'den manuel `lineHeight = N.sp` override'unu
# kaldirir (fontScale sadece rol fontSize'ini carpar; sabit sp satir yuksekligi buyuk
# yazida kirpilma yapar). Cok satirli override'lar ayrica elle temizlendi.
import re, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/java/com/hermes/mobile"
total = 0
for p in sorted(ROOT.rglob("*.kt")):
    if "/theme/" in str(p):
        continue
    text = p.read_text()
    orig = text
    # yalniz tek satirlik, style= ile ayni satirdaki lineHeight = N.sp
    new = re.sub(r", lineHeight = \d+\.sp(?=\s*[,)])", "", text)
    if new != text:
        # yalniz ilgili satirlarda uygula: satir bazli
        lines = text.split("\n")
        out = []
        for l in lines:
            if "MaterialTheme.typography." in l:
                l2 = re.sub(r", lineHeight = \d+\.sp(?=\s*[,)])", "", l)
                if l2 != l:
                    total += 1
                l = l2
            out.append(l)
        p.write_text("\n".join(out))
print("kaldirilan satir-ici lineHeight:", total)
