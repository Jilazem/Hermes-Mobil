#!/usr/bin/env python3
# Duzenleme formunda: adres -> http://10.0.2.2:9171, token -> mocktoken, kaydet.
import time

import p5lib as P


def dump(tag):
    print(f"--{tag}--")
    for n in P.nodes():
        t = n['t'] or n['cd']
        if t:
            print(repr(t[:64]), n['b'], n['cls'].split('.')[-1])


def field_row(label):
    """Etiket konumundan satir merkezi; EditText satir genisligi icin form genisligi al."""
    for n in P.nodes():
        if (n['t'] or n['cd']) == label:
            x1, y1, x2, y2 = [int(v) for v in n['b'].strip('[]').replace('][', ',').split(',')]
            return (540, (y1 + y2) // 2 + 90)   # etiketin ~90px altindaki alan satiri
    return None


def clear_typed(s=0.6):
    """Mevcut icerigi sil: Ctrl+A yok; backspace ile uzun silme."""
    P.sh(P.ADB + ['shell', 'input', 'keyevent', 'KEYCODE_MOVE_END'])
    for _ in range(40):
        P.sh(P.ADB + ['shell', 'input', 'keyevent', '67'])
    time.sleep(s)


def type_text(t):
    t = t.replace(' ', '%s')
    P.sh(P.ADB + ['shell', 'input', 'text', t])


# 1) Adres alani
r = field_row('Adres')
print('adres row:', r)
P.sh(P.ADB + ['shell', 'input', 'tap', str(r[0]), str(r[1])]); time.sleep(1.2)
clear_typed()
type_text('http://10.0.2.2:9171'); time.sleep(0.6)
# 2) Token alani
r = field_row('Oturum anahtarı')
print('token row:', r)
P.sh(P.ADB + ['shell', 'input', 'tap', str(r[0]), str(r[1])]); time.sleep(1.2)
clear_typed(0.3)
type_text('mocktoken'); time.sleep(0.6)
# klavyeyi kapat
P.sh(P.ADB + ['shell', 'input', 'keyevent', '111']); time.sleep(0.8)
dump('kaydet-oncesi')
# 3) Kaydet
for n in P.nodes():
    if (n['t'] or n['cd']) == 'Kaydet':
        x, y = P.ctr(n['b'])
        P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)])
        break
time.sleep(2.5)
dump('kaydet-sonrasi')
