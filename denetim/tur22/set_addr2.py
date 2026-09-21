#!/usr/bin/env python3
# Duzenle: adres -> 10.0.2.2:9171, token -> mocktoken. EditText sinirlari DOGRUDAN kullanilir.
import time

import p5lib as P


def dump(tag):
    print(f"--{tag}--")
    for n in P.nodes():
        t = n['t'] or n['cd']
        if t:
            print(repr(t[:64]), n['b'], n['cls'].split('.')[-1])


def nodes():
    return P.nodes()


def et(field_label=None, contains=None):
    """EditText node'lari: etiketi (ust komshu) veya iceren metne gore."""
    eds = [n for n in nodes() if n['cls'].endswith('EditText')]
    if contains is not None:
        return [n for n in eds if contains in (n['t'] or n['cd'])]
    if field_label:
        out = []
        for n in nodes():
            if (n['t'] or n['cd']) == field_label:
                lx1, ly1, lx2, ly2 = [int(v) for v in n['b'].strip('[]').replace('][', ',').split(',')]
                for e in eds:
                    x1, y1, x2, y2 = [int(v) for v in e['b'].strip('[]').replace('][', ',').split(',')]
                    # EditText etiketle ayni/baslangic satirda VEYA hemen altta
                    if y1 >= ly1 - 5 and y1 <= ly2 + 260:
                        out.append(e)
        return out
    return eds


def ctr_of(n):
    x1, y1, x2, y2 = [int(v) for v in n['b'].strip('[]').replace('][', ',').split(',')]
    return (x1 + x2) // 2, (y1 + y2) // 2


def tapn(n, s=1.2):
    x, y = ctr_of(n)
    P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)])
    time.sleep(s)


def tapat(want, s=1.6, tries=2):
    for _ in range(tries):
        for n in nodes():
            if (n['t'] or n['cd']) == want:
                tapn(n, s); return True
    return False


def clear_field():
    P.sh(P.ADB + ['shell', 'input', 'keyevent', 'KEYCODE_MOVE_END'])
    time.sleep(0.2)
    for _ in range(50):
        P.sh(P.ADB + ['shell', 'input', 'keyevent', '67'])
    time.sleep(0.4)


def type_text(t):
    P.sh(P.ADB + ['shell', 'input', 'text', t.replace(' ', '%s')])
    time.sleep(0.4)


print('duzenle:', tapat('Düzenle'))
# Adres EditText: '://' icerir
a = et(contains='://')
print('adres alanlari:', [(ctr_of(n), n['t'][:30]) for n in a])
if a:
    tapn(a[0]); clear_field()
    type_text('http://10.0.2.2:9171')
# Etiket kontrol — guncel dump
nd = nodes()
addr_ok = any('10.0.2.2:9171' in (n['t'] or n['cd']) for n in nd)
print('adres yazildi mi:', addr_ok)
# Token EditText: 'Oturum anahtarı' etiketinin altindaki EditText
tok = et(field_label='Oturum anahtarı')
print('token alanlari:', [(ctr_of(n), n['cls'].split('.')[-1]) for n in tok])
# en alttaki (en buyuk y1) token olmali; filtre: y1 > 1050
tok = [t for t in tok if ctr_of(t)[1] > 1050]
if tok:
    tapn(sorted(tok, key=lambda e: ctr_of(e)[1])[-1])
    clear_field()
    type_text('mocktoken')
# dogrulama oncesi dump
dump('kaydet-oncesi')
if tapat('Kaydet', s=2.5):
    dump('kaydet-sonrasi')
