#!/usr/bin/env python3
# tur22-r1: mevcut 'the server' profilini düzenle -> 9171 mock + mocktoken -> kaydet -> sohbet.
import time

import p5lib as P


def dump(tag):
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


def clear_field():
    P.sh(P.ADB + ['shell', 'input', 'keyevent', 'KEYCODE_MOVE_END'])
    time.sleep(0.2)
    for _ in range(60):
        P.sh(P.ADB + ['shell', 'input', 'keyevent', '67'])
    time.sleep(0.4)


def type_text(t):
    P.sh(P.ADB + ['shell', 'input', 'text', t.replace(' ', '%s')])
    time.sleep(0.4)


print('duzenle:', tapat('Düzenle'))
eds = [n for n in P.nodes() if n['cls'].endswith('EditText')]
print('EditText sayisi:', len(eds), [n['b'] for n in eds])
if eds:
    x, y = P.ctr(eds[0]['b'])
    P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)]); time.sleep(0.6)
    clear_field()
    type_text('http://10.0.2.2:9171')
dump('adres-sonrasi')
eds = [n for n in P.nodes() if n['cls'].endswith('EditText')]
tok = sorted(eds, key=lambda n: P.ctr(n['b'] if isinstance(n['b'],str) else n['b'])[1])[-1]
x, y = P.ctr(tok['b'])
P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)]); time.sleep(0.6)
clear_field()
type_text('mocktoken')
print('kaydet:', tapat('Kaydet', s=3.5))
dump('sonrasi')
