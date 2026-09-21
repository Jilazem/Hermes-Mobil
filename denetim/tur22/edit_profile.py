#!/usr/bin/env python3
# 'the server' profilini duzenle -> adres 10.0.2.2:9171 + token -> kaydet -> ana ekran.
import time

import p5lib as P


def dump(tag):
    print(f"--{tag}--")
    for n in P.nodes():
        t = n['t'] or n['cd']
        if t:
            print(repr(t[:64]), n['b'], n['cls'].split('.')[-1], 'clk' if n['clk'] else '')


def tapat(want, s=1.6, tries=2, contains=False):
    for _ in range(tries):
        for n in P.nodes():
            t = n['t'] or n['cd']
            hit = (want in t) if contains else (t == want)
            if hit:
                x, y = P.ctr(n['b'])
                P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)])
                time.sleep(s)
                return True
    return False


print('duzenle:', tapat('Düzenle'))
dump('duzenleme-formu')
