#!/usr/bin/env python3
# pm clear -> tek profil 9171 ekle -> kontrol. Her adim dump ile dogrulanir.
import time

import p5lib as P


def dump(tag, only_text=True):
    print(f"--{tag}--")
    for n in P.nodes():
        t = n['t'] or n['cd']
        if t:
            print(repr(t[:64]), n['b'], 'clk' if n['clk'] else '')


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


P.sh(P.ADB + ['shell', 'pm', 'clear', P.PKG]); time.sleep(2.0)
P.sh(P.ADB + ['shell', 'am', 'start', '-n', P.PKG + '/com.hermes.mobile.MainActivity'])
time.sleep(4.0)
dump('onboarding')
print('sunucu-ekle:', tapat('Sunucu ekle', s=2.0))
dump('form')
