#!/usr/bin/env python3
# tur22-r1 KANIT: 45-gonder-burst — mesaj yaz, GÖNDER'e bas, 1sn ✓ flash penceresinde
# 5 ardışık screencap + her anın bounds + dump. ÖNCEKİ/SONRAKİ hash'ler ayrı dosyada.
# NOT: screencap BINARY döner — p5lib.sh text=True kullanmaz, raw subprocess.
import hashlib
import json
import re
import subprocess
import time

import p5lib as P


def cap():
    return subprocess.run(P.ADB + ['exec-out', 'screencap', '-p'],
                          capture_output=True, timeout=20).stdout


def xy(b):
    return [int(v) for v in re.findall('[0-9]+', b)]


def tapx(x, y, s=1.0):
    P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)])
    time.sleep(s)


def dump_texts():
    return [n['t'] for n in P.nodes() if n['t']]


# 1) Mesaj alanına yaz
msgbox = None
for n in P.nodes():
    if 'Mesaj yaz' in (n['t'] or ''):
        msgbox = n['b']
if not msgbox:
    raise SystemExit('mesaj alanı yok')
x1, y1, x2, y2 = xy(msgbox)
tapx((x1+x2)//2, (y1+y2)//2, 1.2)
P.sh(P.ADB + ['shell', 'input', 'text', 'r1-gonder-flash-kaniti'])
time.sleep(0.8)

# 2) Gönder'i bul (İçerik var → ActionButton 'Gönder')
g = None
tries = 0
while g is None and tries < 5:
    for n in P.nodes():
        if (n['t'] or n['cd']) == 'Gönder':
            g = P.clickable_parent_of(lambda d: (d['t'] or d['cd']) == 'Gönder')
    tries += 1
    time.sleep(0.6)
print('Gönder kutusu:', g)
gx = P.ctr(g)

# 3) ÖNCEKİ durum hash (basış öncesi son görüntü)
pre = cap()
open('kanit/44-r1-oncesi.png','wb').write(pre)

# 4) bas + 5 ardışık kare (burst)
P.sh(P.ADB + ['shell', 'input', 'touchscreen', 'swipe', str(gx[0]), str(gx[1]),
              str(gx[0]), str(gx[1]), '80'])  # 80ms bas-bırak; BURST HEMEN (1sn flash penceresi)
burst = []
t0 = time.time()
for i in range(5):
    png = cap()
    name = f'kanit/45-r1-burst-{i}.png'
    open(name, 'wb').write(png)
    burst.append({'i': i, 't_ms': int((time.time()-t0)*1000),
                  'sha': hashlib.sha256(png).hexdigest()})

time.sleep(0.8)
# 5) SONRAKİ durum (flash bitti, mesaj balonu + mikrofon)
post = cap()
open('kanit/46-r1-mesaj-dolu.png', 'wb').write(post)

# 6) bounds + dump dökümü
lines = []
lines.append('gönder-kutu-oncesi: ' + str(g))
lines.append('burst zamanlama+sha256: ' + json.dumps(burst, ensure_ascii=False, indent=1))
lines.append('oncesi-sha: ' + hashlib.sha256(pre).hexdigest())
lines.append('sonrasi-sha: ' + hashlib.sha256(post).hexdigest())
lines.append('sonrasi-mesaj-balonu-var-mi: ' + str('r1-gonder-flas' in ' | '.join(dump_texts())))
open('kanit/45-r1-gonder-burst.txt', 'w').write('\n'.join(lines) + '\n')
print('\n'.join(lines[:1]))
print('burst:', json.dumps(burst))
print('farkli kare sha sayisi:', len({b["sha"] for b in burst}))
print('sonrasi balon kontrol:', 'r1-gonder-flas' in ' | '.join(dump_texts()))
