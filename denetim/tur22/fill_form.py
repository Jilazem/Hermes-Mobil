#!/usr/bin/env python3
# Formda alanlari ETİKET konumuna göre doldur (label'in hemen üstündeki EditText satırı).
import time
import p5lib as P

LABELS = [('Ad', 'tur22mock'), ('Adres', 'http://10.0.2.2:9171'), ('Oturum anahtarı', 'demo-token')]

for want, val in LABELS:
    lab = next((n for n in P.nodes() if n['t'] == want), None)
    if not lab:
        print("etiket yok:", want); continue
    ly = P.ctr(lab['b'])[1]
    # etiket, TextField'in alt-üst içindedir: merkez Y en yakın EditText
    eds = [n for n in P.nodes() if 'EditText' in n['cls']]
    tgt = min(eds, key=lambda n: abs(P.ctr(n['b'])[1] - ly))
    x, y = P.ctr(tgt['b'])
    P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)]); time.sleep(0.9)
    P.sh(P.ADB + ['shell', 'input', 'text', val]); time.sleep(0.6)
    P.sh(P.ADB + ['shell', 'input', 'keyevent', '4']); time.sleep(0.6)
    print(f"yazildi {want}: {val}")

vals = [n['t'] for n in P.nodes() if 'EditText' in n['cls'] and n['t']]
print("degerler:", vals)
for n in P.nodes():
    if (n['t'] or n['cd']) == 'Kaydet':
        x, y = P.ctr(n['b'])
        P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)])
        break
time.sleep(3)
rows = [n['t'] for n in P.nodes() if n['t'] in ('tur22mock', 'aktif') or '9171' in n['t']]
print("liste:", rows)
