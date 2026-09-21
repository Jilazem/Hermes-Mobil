#!/usr/bin/env python3
# tur22-r1: düzenleme sayfasında Alan/Token alanlarını ETİKET y-koordinatına göre
# doğru hedefle -> 9171 + mocktoken -> Kaydet -> sohbet ekranına dön -> bağlantı bekle.
import time

import p5lib as P


def nodes():
    return P.nodes()


def xy(b):
    return [int(v) for v in b.strip('[]').replace('][', ',').split(',')]


def dump(tag):
    print(f"--{tag}--")
    for n in nodes():
        t = n['t'] or n['cd']
        if t:
            print(repr(t[:60]), n['b'], n['cls'].split('.')[-1])


def tapx(x, y, s=1.2):
    P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)])
    time.sleep(s)


def clear_field():
    P.sh(P.ADB + ['shell', 'input', 'keyevent', 'KEYCODE_MOVE_END'])
    time.sleep(0.2)
    for _ in range(60):
        P.sh(P.ADB + ['shell', 'input', 'keyevent', '67'])
    time.sleep(0.4)


def type_text(t):
    P.sh(P.ADB + ['shell', 'input', 'text', t.replace(' ', '%s')])
    time.sleep(0.4)


def field_below(label):
    """Etiket metninin hemen altındaki EditText'i bul."""
    ns = nodes()
    ly = None
    for n in ns:
        if (n['t'] or n['cd']) == label:
            ly = xy(n['b'])[1]
    if ly is None:
        return None
    cands = [n for n in ns if n['cls'].endswith('EditText') and xy(n['b'])[1] >= ly - 5]
    cands.sort(key=lambda n: xy(n['b'])[1])
    return cands[0] if cands else None


# 1) Alan(Ad) alanını temizle (yanlış yazılmış 'mocktoken' gidecek)
f = field_below('Ad')
print('Ad alan:', f['b'] if f else None)
if f:
    x1, y1, x2, y2 = xy(f['b']); tapx((x1+x2)//2, (y1+y2)//2)
    clear_field()
    type_text('mock9171')

# 2) Adres
f = field_below('Adres')
print('Adres alan:', f['b'] if f else None)
if f:
    x1, y1, x2, y2 = xy(f['b']); tapx((x1+x2)//2, (y1+y2)//2)
    clear_field()
    type_text('http://10.0.2.2:9171')

# 3) Token
f = field_below('Oturum anahtarı')
print('Token alan:', f['b'] if f else None)
if f:
    x1, y1, x2, y2 = xy(f['b']); tapx((x1+x2)//2, (y1+y2)//2)
    clear_field()
    type_text('mocktoken')

dump('doldu')
# Kaydet
for n in nodes():
    if (n['t'] or n['cd']) == 'Kaydet':
        x1, y1, x2, y2 = xy(n['b']); tapx((x1+x2)//2, (y1+y2)//2, 3.0)
        break
# Geri (sunucular -> sohbet)
for n in nodes():
    if (n['t'] or n['cd']) == 'Geri':
        x1, y1, x2, y2 = xy(n['b']); tapx((x1+x2)//2, (y1+y2)//2, 2.0)
        break
# WS bağlantısı için bekle
for i in range(20):
    time.sleep(1)
    blob = ' | '.join((n['t'] or n['cd']) for n in nodes() if (n['t'] or n['cd']))
    if 'çevrimiçi' in blob.lower() or 'Bağlandı' in blob or 'bağlı' in blob.lower():
        break
dump('sohbet')
