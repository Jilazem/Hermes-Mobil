#!/usr/bin/env python3
# Kapanmis cekmece + Ayarlar sekmesine gecis + Sunucular'i bulup listele.
import time

import p5lib as P


def dump(tag):
    print(f"--{tag}--")
    for n in P.nodes():
        if n['t'] or n['cd']:
            print(repr((n['t'] or n['cd'])[:70]), n['b'], 'clk' if n['clk'] else '')


# 1) cekmeceyi kapat: sol bosluga tikla (cekmece %85 genislik; sag 15% bosluk)
P.sh(P.ADB + ['shell', 'input', 'tap', '1040', '1200']); time.sleep(1.0)
P.sh(P.ADB + ['shell', 'input', 'tap', '1040', '1200']); time.sleep(1.0)
dump('kapatma-sonrasi')
# 2) Ayarlar alt sekmesi
P.sh(P.ADB + ['shell', 'input', 'tap', '950', '2270']); time.sleep(2.5)
dump('ayarlar-sekme')
