#!/usr/bin/env python3
# tur22-r1: Uzak-adres alanını temizle (mocktoken yanlış yazılmıştı) -> Vazgeç yerine
# boş kal -> Kaydet -> Sohbet sekmesine geç -> bağlantı durumu bekle.
import time

import p5lib as P


def nodes():
    return P.nodes()


def xy(b):
    return [int(v) for v in b.strip('[]').replace('][', ',').split(',')]


def tapx(x, y, s=1.2):
    P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)])
    time.sleep(s)


def dump(tag):
    print(f"--{tag}--")
    for n in nodes():
        t = n['t'] or n['cd']
        if t:
            print(repr(t[:60]), n['b'])


# 1) 4. EditText (uzak adres, y~870) temizle
eds = [n for n in nodes() if n['cls'].endswith('EditText')]
print('EditTextler:', [(n['b'], repr(n['t'][:20])) for n in eds])
for n in eds:
    if 'mocktoken' in (n['t'] or '') and xy(n['b'])[1] > 800:
        x1, y1, x2, y2 = xy(n['b']); tapx((x1+x2)//2, (y1+y2)//2)
        P.sh(P.ADB + ['shell', 'input', 'keyevent', 'KEYCODE_MOVE_END']); time.sleep(0.2)
        for _ in range(40):
            P.sh(P.ADB + ['shell', 'input', 'keyevent', '67'])
        time.sleep(0.3)
        print('uzak-adres temizlendi')

# 2) Kaydet
for n in nodes():
    if (n['t'] or n['cd']) == 'Kaydet':
        x1, y1, x2, y2 = xy(n['b']); tapx((x1+x2)//2, (y1+y2)//2, 2.5); break
# 3) Geri
for n in nodes():
    if (n['t'] or n['cd']) == 'Geri':
        x1, y1, x2, y2 = xy(n['b']); tapx((x1+x2)//2, (y1+y2)//2, 2.0); break
# 4) Sohbet sekmesi
for n in nodes():
    if (n['t'] or n['cd']) == 'Sohbet':
        x1, y1, x2, y2 = xy(n['b']); tapx((x1+x2)//2, (y1+y2)//2, 2.0); break
# 5) Bağlantı bekle
ok = False
for i in range(25):
    time.sleep(1)
    blob = ' | '.join((n['t'] or n['cd']) for n in nodes() if (n['t'] or n['cd']))
    if 'Tur22 kare testi' in blob or 'İkinci oturum' in blob:
        ok = True; break
print('oturum-liste gorundu mu:', ok)
dump('durum')
