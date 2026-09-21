#!/usr/bin/env python3
import time
import p5lib as P


def tapat(want, s=2.0):
    for n in P.nodes():
        if (n['t'] or n['cd']) == want:
            x, y = P.ctr(n['b'])
            P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)])
            time.sleep(s)
            return True
    return False


if not any((n['t'] or n['cd']) == 'Sunucu ekle' for n in P.nodes()):
    raise SystemExit("Sunucu ekle yok")
print("1 tap:", tapat('Sunucu ekle', 2.2))
nd = [n['cls'] for n in P.nodes() if 'EditText' in n['cls']]
print("form sonra EditText:", len(nd))
if len(nd) >= 3:
    eds = [n for n in P.nodes() if 'EditText' in n['cls']]
    def setedit(idx, val):
        eds = [n for n in P.nodes() if 'EditText' in n['cls']]
        x, y = P.ctr(eds[idx]['b'])
        P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)]); time.sleep(0.8)
        P.sh(P.ADB + ['shell', 'input', 'text', val]); time.sleep(0.6)
        P.sh(P.ADB + ['shell', 'input', 'keyevent', '4']); time.sleep(0.6)
    setedit(0, 'tur22mock')
    setedit(1, 'http://10.0.2.2:9171')
    setedit(2, 'demo-token')
    vals = [n['t'] for n in P.nodes() if 'EditText' in n['cls'] and n['t']]
    print("degerler:", vals)
    print("Kaydet:", tapat('Kaydet', 3.0))
    rows = [n['t'] for n in P.nodes() if n['t'] in ('tur22mock', 'aktif') or 'http' in n['t']]
    print("liste:", rows)
