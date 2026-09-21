#!/usr/bin/env python3
# Tek akis: Sunucular'a gec -> Sunucu ekle -> formu doldur -> Kaydet -> kontrol.
import time
import p5lib as P


def tapat(want, s=2.0, tries=2):
    for _ in range(tries):
        for n in P.nodes():
            if (n['t'] or n['cd']) == want:
                x, y = P.ctr(n['b'])
                P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)])
                time.sleep(s)
                return True
        time.sleep(1.0)
    return False


if not any(n['t'] == 'Sunucular' for n in P.nodes()):
    # sohbet ekranindaysak: ust bar 'Sunucu ekle' CTA ya da Ayarlar yolu
    tapat('Sunucu ekle', 2.0)
print("Sunucular:", any(n['t'] == 'Sunucular' for n in P.nodes()))

print("ekle tap:", tapat('Sunucu ekle', 2.2))
nd = [n for n in P.nodes() if 'EditText' in n['cls']]
print("form EditText:", len(nd))

LABELS = [('Ad', 'tur22mock'), ('Adres', 'http://10.0.2.2:9171'), ('Oturum anahtarı', 'demo-token')]
for want, val in LABELS:
    for attempt in range(3):
        lab = next((n for n in P.nodes() if n['t'] == want), None)
        if not lab:
            time.sleep(1.0); continue
        ly = P.ctr(lab['b'])[1]
        eds = [n for n in P.nodes() if 'EditText' in n['cls']]
        tgt = min(eds, key=lambda n: abs(P.ctr(n['b'])[1] - ly))
        x, y = P.ctr(tgt['b'])
        P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)]); time.sleep(0.9)
        P.sh(P.ADB + ['shell', 'input', 'text', val]); time.sleep(0.6)
        P.sh(P.ADB + ['shell', 'input', 'keyevent', '4']); time.sleep(0.6)
        print("yazildi:", want)
        break

vals = [n['t'] for n in P.nodes() if 'EditText' in n['cls'] and n['t']]
print("degerler:", vals)
print("Kaydet:", tapat('Kaydet', 3.0))
rows = [n['t'] for n in P.nodes() if n['t'] in ('tur22mock', 'aktif') or '9171' in n['t']]
print("liste:", rows)
