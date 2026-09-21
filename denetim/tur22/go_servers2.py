#!/usr/bin/env python3
# Cekmeceyi kapat, alt sekme Ayarlar'a gec, Sunucular bolumunu bul.
import time

import p5lib as P


def dump(tag):
    print(f"--{tag}--")
    for n in P.nodes():
        if n['t'] or n['cd']:
            print(repr((n['t'] or n['cd'])[:60]), n['b'], 'clk' if n['clk'] else '')


P.sh(P.ADB + ['shell', 'input', 'keyevent', '111']); time.sleep(1.0)
P.sh(P.ADB + ['shell', 'input', 'tap', '950', '2270']); time.sleep(2.2)
dump('ayarlar-sekme')
