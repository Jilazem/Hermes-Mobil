#!/usr/bin/env python3
# r2 denetçi tam kurulum (p5lib native): Ayarlar->Sunucular->Düzenle->10.0.2.2:9171->Kaydet->Sohbet
import sys, time
sys.path.insert(0, '/Users/gokhanuzman/hermes-workspace/wt-t22/denetim/tur22')
import p5lib as P


def set_et(idx, val, s=0.8):
    eds = [n for n in P.nodes() if 'EditText' in n['cls']]
    eds.sort(key=lambda n: int(P.ctr(n['b'])[1]))
    x, y = P.ctr(eds[idx]['b'])
    P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)]); time.sleep(0.6)
    P.sh(P.ADB + ['shell', 'input', 'keyevent', 'KEYCODE_MOVE_END'])
    for _ in range(25):
        P.sh(P.ADB + ['shell', 'input', 'keyevent', '67'])
    time.sleep(0.2)
    for ch in val:
        P.sh(P.ADB + ['shell', 'input', 'text', ch.replace(' ', '%s')]); time.sleep(0.06)
    time.sleep(s)
    P.sh(P.ADB + ['shell', 'input', 'keyevent', '4']); time.sleep(0.3)


def dump(tag):
    print('--%s--' % tag)
    for n in P.nodes():
        t = n['t'] or n['cd']
        if t:
            print(repr(t[:60]), n['b'], n['cls'].split('.')[-1])


P.tap('Ayarlar', 2)
P.tap('Sunucular', 2)
P.tap('Düzenle', 2.5)
dump('form')
set_et(1, 'http://10.0.2.2:9171', 1.0)
set_et(2, 'mocktoken', 1.0)
dump('token-sonrasi')
P.tap('Kaydet', 3)
dump('kayit-sonrasi')
P.tap('Sohbet', 3)
ok = False
for i in range(8):
    time.sleep(4)
    txt = ' '.join((n['t'] or '') for n in P.nodes())
    if 'Bağlantı hazır' in txt:
        print('BAĞLANDI (%ds)' % ((i + 1) * 4)); ok = True; break
if not ok:
    print('UYARI: Bağlantı hazır GÖRÜNMEDİ')
dump('sohbet-son')
