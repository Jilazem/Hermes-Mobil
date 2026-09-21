#!/usr/bin/env python3
# Temiz acilis + 15sn pencerede cekmece iskelet ornekleme (D maddesi hata ayiklama).
import time
import p5lib as P

P.sh(P.ADB + ['shell', 'am', 'force-stop', P.PKG])
time.sleep(1.2)
P.sh(P.ADB + ['shell', 'am', 'start', '-n', P.PKG + '/com.hermes.mobile.MainActivity'])
time.sleep(2.5)   # uygulama + Compose acilis; refreshAll henuz /api/sessions'da (mock 15sn)

b = P.clickable_parent_of(lambda n: (n['t'] or n['cd']) == 'Oturumlar')
if not b:
    print("!! Oturumlar dugumu yok"); raise SystemExit(1)
x, y = P.ctr(b)
P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)])
for i in range(12):
    texts = [f"{n['t'][:44]!r}" if n['t'] else f"cd={n['cd'][:26]!r}" for n in P.nodes() if n['t'] or n['cd']]
    print(f"== t~{2.5 + 1.5*(i+1):.1f}s ==", texts)
    time.sleep(1.5)
