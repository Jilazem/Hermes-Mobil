#!/usr/bin/env python3
# tur22-r1 kanıt akışı: temiz kurulum + 9171 mock profilli sohbet ekranı.
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


def editfields():
    return [n for n in P.nodes() if n['cls'].endswith('EditText')]


def type_into(idx, text):
    eds = editfields()
    if idx >= len(eds):
        print(f'EditText #{idx} yok'); return False
    x, y = P.ctr(eds[idx]['b'])
    P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)])
    time.sleep(0.6)
    P.sh(P.ADB + ['shell', 'input', 'text', text.replace(' ', '%s')])
    time.sleep(0.5)
    return True


P.sh(P.ADB + ['shell', 'pm', 'clear', P.PKG]); time.sleep(2.0)
P.sh(P.ADB + ['shell', 'am', 'start', '-n', P.PKG + '/com.hermes.mobile.MainActivity'])
time.sleep(4.0)
print('sunucu-ekle:', tapat('Sunucu ekle', s=2.0))
dump('form')
print('addr:', type_into(0, '10.0.2.2:9171'))
print('token:', type_into(1, 'mocktoken'))
dump('form-dolu')
print('kaydet:', tapat('Kaydet', s=3.0))
dump('sonrasi')
