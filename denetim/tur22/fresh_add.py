#!/usr/bin/env python3
# Vazgec -> profili sil -> Sunucu ekle -> EditText sinirlarina DOGRU tap -> kaydet.
import time

import p5lib as P


def dump(tag):
    print(f"--{tag}--")
    for n in P.nodes():
        t = n['t'] or n['cd']
        if t:
            print(repr(t[:60]), n['b'], n['cls'].split('.')[-1])


def nodes():
    return P.nodes()


def xy(n):
    return [int(v) for v in n['b'].strip('[]').replace('][', ',').split(',')]


def tapat(want, s=1.6, tries=2, contains=False):
    for _ in range(tries):
        for n in nodes():
            t = n['t'] or n['cd']
            hit = (want in t) if contains else (t == want)
            if hit:
                x1, y1, x2, y2 = xy(n)
                P.sh(P.ADB + ['shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2)])
                time.sleep(s)
                return True
    return False


def type_text(t):
    P.sh(P.ADB + ['shell', 'input', 'text', t.replace(' ', '%s')])
    time.sleep(0.4)


# 1) Vazgec
print('vazgec:', tapat('Vazgeç'))
time.sleep(1.0)
# 2) Sil + olasilik onay dialogu
print('sil:', tapat('Sil'))
dump('sil-onayi')
# 3) Sunucu ekle
print('sunucu-ekle:', tapat('Sunucu ekle', s=2.0))
# EditText'leri bos durumlarinda yakala (ipucu metinli, bos 't')
eds = [n for n in nodes() if n['cls'].endswith('EditText')]
print('form edit metinleri:', [(n['cd'] or n['t'], xy(n)[1]) for n in eds])
dump('yeni-form')
