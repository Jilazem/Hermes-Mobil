#!/usr/bin/env python3
# Ayarlar -> Sunucular -> 9171 profilini aktif et (yoksa olustur) -> don.
import time

import p5lib as P


def tapat(want, s=1.8, tries=2):
    for _ in range(tries):
        for n in P.nodes():
            if (n['t'] or n['cd']) == want:
                x, y = P.ctr(n['b'])
                P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)])
                time.sleep(s)
                return True
    return False


def dump(tag):
    print(f"--{tag}--")
    for n in P.nodes():
        if n['t'] or n['cd']:
            print(repr((n['t'] or n['cd'])[:60]), n['b'], 'clk' if n['clk'] else '')


P.sh(P.ADB + ['shell', 'input', 'keyevent', '111']); time.sleep(0.8)
print("ayarlar:", tapat('Ayarlar'))
dump('ayarlar-ekrani')
