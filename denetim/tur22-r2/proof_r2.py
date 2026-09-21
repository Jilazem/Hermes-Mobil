#!/usr/bin/env python3
# r2 denetçi KANIT seti: bağlan -> bounds -> 44-oncesi -> 44 burst -> 46-dolu -> 45 gonder burst
import os, re, sys, threading, time
sys.path.insert(0, '/Users/gokhanuzman/hermes-workspace/wt-t22/denetim/tur22')
import p5lib as P

OUT = '/Users/gokhanuzman/hermes-workspace/wt-t22/denetim/tur22-r2/kanit'
os.makedirs(OUT, exist_ok=True)


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


def nodes():
    return P.nodes()


# ---- 1. Bağlan: Sunucular listesi -> Düzenle -> 10.0.2.2:9171 ----
def has(want):
    return any((n['t'] or n['cd']) == want for n in nodes())

if not has('Düzenle'):
    P.tap('Sunucu ekle', 2.5)   # boş durum -> Sunucular listesi
if not has('Düzenle'):
    # Ayarlar > Profiller üzerinden dene
    P.tap('Ayarlar', 2); P.tap('Profiller', 2.5)
P.tap('Düzenle', 2.5)
eds = [n for n in nodes() if 'EditText' in n['cls']]
print('form EditText:', len(eds))
dump('form')
if len(eds) >= 3:
    set_et(1, 'http://10.0.2.2:9171', 1.0)
    set_et(2, 'mocktoken', 1.0)
    P.tap('Kaydet', 3)
dump('kayit-sonrasi')
P.tap('Sohbet', 2)
ok = False
for i in range(8):
    time.sleep(4)
    txt = ' '.join((n['t'] or '') for n in nodes())
    if 'Bağlantı hazır' in txt:
        print('BAĞLANDI (%ds)' % ((i + 1) * 4)); ok = True; break
if not ok:
    print('KRİTİK: Bağlantı kurulamadı — burst alınamaz')
    dump('son')
    sys.exit(2)

# ---- 2. bounds 48dp (4 üst bar + mikrofon + gönder) ----
lines = []
for want in ('Oturumlar', 'Menü', 'Sesli sohbet', 'Yeni sohbet', 'Basılı tut, konuş'):
    for n in nodes():
        if (n['t'] or n['cd']) == want:
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', n['b']))
            lines.append('%s %s gen=%dpx -> %.1fdp' % (want, n['b'], x2 - x1, (x2 - x1) / 2.625))
            break
# gönder/✓ düğmesi: content-desc 'Gönder' / 'Durdur' / Check
for n in nodes():
    cd = n['cd'] or ''
    if cd in ('Gönder', 'Durdur', 'Gönderimi geri al') or 'Check' in n['cls']:
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n['b']))
        lines.append('GONDER-DUGME[%s] %s gen=%dpx -> %.1fdp' % (cd or 'Check', n['b'], x2 - x1, (x2 - x1) / 2.625))
open(OUT + '/bounds-r2.txt', 'w').write('\n'.join(lines) + '\n')
print('\n'.join(lines))

# ---- 3. 44-oncesi (boş giriş) ----
P.sh(P.ADB + ['exec-out', 'screencap', '-p'], timeout=10)
import subprocess
subprocess.run(P.ADB + ['exec-out', 'screencap', '-p'], stdout=open(OUT + '/44-r2-oncesi.png', 'wb'), timeout=10)

# ---- 4. 44 burst (yazarken) ----
ed = [n for n in nodes() if 'EditText' in n['cls']]
ed.sort(key=lambda n: int(P.ctr(n['b'])[1]))
ex, ey = P.ctr(ed[-1]['b'])
P.sh(P.ADB + ['shell', 'input', 'tap', str(ex), str(ey)]); time.sleep(0.8)
stop = [False]
frames = []


def grab():
    i = 0
    while not stop[0] and i < 60:
        f = OUT + '/44-r2-burst-%02d.png' % i
        subprocess.run(P.ADB + ['exec-out', 'screencap', '-p'], stdout=open(f, 'wb'), timeout=8)
        frames.append(f); i += 1


th = threading.Thread(target=grab); th.start()
for ch in 'r2 denetim patates':
    subprocess.run(P.ADB + ['shell', 'input', 'text', ch.replace(' ', '%s')]); time.sleep(0.28)
stop[0] = True; th.join()
print('44 burst kare:', len(frames))
subprocess.run(P.ADB + ['exec-out', 'screencap', '-p'], stdout=open(OUT + '/46-r2-mesaj-dolu.png', 'wb'), timeout=10)

# ---- 5. 45 gonder-ani burst ----
# gönder düğmesini bul (content-desc 'Gönder' veya cd içinde)
def send_btn():
    for n in nodes():
        cd = n['cd'] or ''
        if cd in ('Gönder', 'Gönder (Enter)') or cd.lower() == 'send':
            return P.ctr(n['b'])
    return None

sb = send_btn()
print('send btn:', sb)
if not sb:
    # son EditText'in sağındaki düğme
    x, y = sb or (956, 2045)
frames2 = []
stop[0] = False


def grab2():
    i = 0
    while not stop[0] and i < 45:
        f = OUT + '/45-r2-burst-%02d.png' % i
        subprocess.run(P.ADB + ['exec-out', 'screencap', '-p'], stdout=open(f, 'wb'), timeout=8)
        frames2.append(f); i += 1


th2 = threading.Thread(target=grab2); th2.start()
# Enter ile gönder (Compose IME)
subprocess.run(P.ADB + ['shell', 'input', 'keyevent', 'KEYCODE_ENTER']); time.sleep(1.2)
stop[0] = True; th2.join()
print('45 burst kare:', len(frames2))
dump('final')
print('KANIT DIR:', OUT)
