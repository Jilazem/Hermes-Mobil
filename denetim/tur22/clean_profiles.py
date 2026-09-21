#!/usr/bin/env python3
# Sunucular'daki eski profilleri siler: tur22b(9171) tek profil kalir -> otomatik aktif.
import time
import p5lib as P


def tapat(want, s=1.0):
    for n in P.nodes():
        if (n['t'] or n['cd']) == want:
            x, y = P.ctr(n['b'])
            P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)])
            time.sleep(s)
            return True
    return False


print("Sunucular'da mi:", [n['t'] for n in P.nodes() if n['t']][:4])
# once listeye in (form aciksa Vazgec)
for n in P.nodes():
    if n['t'] == 'Vazgeç':
        tapat('Vazgeç', 1.0)
        break

# 'the server' satirini sil
for i in range(2):
    for n in P.nodes():
        if n['t'] == 'the server' or n['t'] == 'mock-8198':
            # satira en yakin 'Sil' cd
            sy = int(P.ctr(n['b'])[1])
            best = min((m for m in P.nodes() if m['cd'] == 'Sil'),
                       key=lambda m: abs(P.ctr(m['b'])[1] - sy))
            x, y = P.ctr(best['b'])
            P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)])
            time.sleep(1.2)
            tapat('Sil', 1.5)  # onay dialogu
            print(f"silindi tur{i}")
            break
    time.sleep(0.5)

txt = [n['t'] for n in P.nodes() if n['t'] and 'http' in n['t']] + [n['t'] for n in P.nodes() if n['t'] in ('tur22b', 'aktif')]
print("kalan:", txt)
