#!/usr/bin/env python3
# tur22-r1 KANIT: 48dp — 4 üst-bar düğmesi + composer düğmelerinin TIKANABİLİR
# kutu bounds'u, 2 ardışık çekimde sabit; 1080p-420dpi: 48dp = 126 px.
import hashlib
import re
import time

import p5lib as P

TARGETS = ['Oturumlar', 'Menü', 'Sesli sohbet', 'Yeni sohbet', 'Ek', 'Basılı tut, konuş']
PX48 = 126  # 48dp @ 420dpi (density 2.625)


def shot(name):
    b = P.sh(P.ADB + ['exec-out', 'screencap', '-p'], binary=False)
    return b


def grab():
    out = {}
    for t in TARGETS:
        # o anki ekran: 'Basılı tut, konuş' boş taslakta var; 'Gönder' dolu taslakta
        b = P.clickable_parent_of(lambda d, want=t: (d['t'] or d['cd']) == want)
        out[t] = b
    return out


lines = []
for rnd in (1, 2):
    g = grab()
    lines.append(f"--- çekim {rnd} ---")
    for k, v in g.items():
        if v:
            m = re.findall('[0-9]+', v); px = int(m[2]) - int(m[0])
            dp = px / 2.625
            lines.append(f"{k:20s} {v}  genişlik={px}px = {dp:.1f}dp {'OK' if px == PX48 else 'HATA'}")
        else:
            lines.append(f"{k:20s} YOK")
    time.sleep(0.5)

txt = "\n".join(lines) + "\n"
with open('kanit/bounds-48dp-r1.txt', 'w') as f:
    f.write("48dp KANIT (tur22-r1) — tıklanabilir kutu bounds, 1080x2400@420dpi, 48dp=126px\n")
    f.write("ölçüm komutu: uiautomator dump + clickable_parent_of (setup: mock 9171 bağlı sohbet ekranı)\n\n")
    f.write(txt)
    f.write("\nSHA256: " + hashlib.sha256(txt.encode()).hexdigest() + "\n")
print(txt)
