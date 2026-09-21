#!/usr/bin/env python3
# Sunucular ekranina gec, 9171 profilini BULUP aktif et; yoksa olustur.
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


print("sunucular:", tapat('Sunucular', s=2.0))
for n in P.nodes():
    if n['t'] or n['cd']:
        print(repr(n['t'][:60]), 'clk' if n['clk'] else '')
