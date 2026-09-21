#!/usr/bin/env python3
# TUR22 FINAL tek-akış: profil temizliği + tek mock profil + iskelet kanıtı.
# Adımlar dump ile doğrulanır; BACK yerine ESC (111) — 'Çıkılsın mı?' tuzak değil.
import subprocess
import time

import p5lib as P

MOCK = "http://10.0.2.2:9171"


def tapat(want, s=1.8, tries=2, cd=False):
    for _ in range(tries):
        for n in P.nodes():
            if (n['cd'] if cd else n['t']) == want:
                x, y = P.ctr(n['b'])
                P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)])
                time.sleep(s)
                return True
        time.sleep(1.0)
    return False


def type_field(label, val):
    """label etiketinin gömülü olduğu EditText'e yaz; ESC ile klavyeyi gizle."""
    lab = next((n for n in P.nodes() if n['t'] == label), None)
    if not lab:
        print("  !etiket yok:", label)
        return False
    ly = P.ctr(lab['b'])[1]
    eds = [n for n in P.nodes() if 'EditText' in n['cls']]
    if not eds:
        print("  !EditText yok")
        return False
    tgt = min(eds, key=lambda n: abs(P.ctr(n['b'])[1] - ly))
    x, y = P.ctr(tgt['b'])
    P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)]); time.sleep(0.9)
    P.sh(P.ADB + ['shell', 'input', 'text', val]); time.sleep(0.6)
    P.sh(P.ADB + ['shell', 'input', 'keyevent', '111']); time.sleep(0.6)  # ESC
    # doğrula
    n2 = min([n for n in P.nodes() if 'EditText' in n['cls']],
             key=lambda n: abs(P.ctr(n['b'])[1] - ly), default=None)
    return True


def state():
    return [(n['t'][:40] if n['t'] else "cd:" + n['cd'][:22]) for n in P.nodes() if n['t'] or n['cd']]


# 1) Uygulamayı temiz aç
P.sh(P.ADB + ['shell', 'am', 'force-stop', P.PKG]); time.sleep(1.2)
P.sh(P.ADB + ['shell', 'am', 'start', '-n', P.PKG + '/com.hermes.mobile.MainActivity']); time.sleep(4)
print("1) açılış:", state()[:6])

# 2) Sunucular ekranına geç (üst CTA ya da Ayarlar)
if not tapat('Sunucu ekle', 2.0):
    print("  CTA bulunamadı, tekrar state:", state()[:8])
print("2) sunucular:", any(n['t'] == 'Sunucular' for n in P.nodes()))

# 3) Yeni profil ekle
if not tapat('Sunucu ekle', 2.2):
    print("  form CTA yok"); raise SystemExit(1)
ok_ad = type_field('Ad', 'tur22mock')
print("3) form:", len([n for n in P.nodes() if 'EditText' in n['cls']]), "Ad yazıldı:", ok_ad)
print("   Adres:", type_field('Adres', MOCK))
print("   Token:", type_field('Oturum anahtarı', 'demo-token'))
vals = [n['t'] for n in P.nodes() if 'EditText' in n['cls'] and n['t']]
print("   değerler:", vals)
if not tapat('Kaydet', 2.5):
    print("   Kaydet YOK"); raise SystemExit(2)
rows = [n['t'] for n in P.nodes() if 'tur22mock' in n['t'] or MOCK.split('://')[1] in n['t']]
print("4) kayıt satırları:", rows)

# 4) Eski profilleri sil (the server, mock-8198, tur22b) — tur22mock kalsın
for name in ('the server', 'mock-8198', 'tur22b', 'tur22-mock'):
    row = next((n for n in P.nodes() if n['t'] == name), None)
    if not row:
        continue
    sy = P.ctr(row['b'])[1]
    sil = min((m for m in P.nodes() if m['cd'] == 'Sil'),
              key=lambda m: abs(P.ctr(m['b'])[1] - sy))
    x, y = P.ctr(sil['b'])
    P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)]); time.sleep(1.2)
    tapat('Sil', 1.5)
    print("5) silindi:", name)
time.sleep(1)

# 5) Profil tek ise seçili olur; değilse satırına bas
srows = [n for n in P.nodes() if n['t'] == 'tur22mock']
if srows:
    x, y = P.ctr(srows[0]['b'])
    P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)]); time.sleep(2.5)
    akt = [n for n in P.nodes() if n['t'] == 'aktif'
           and abs(P.ctr(n['b'])[1] - P.ctr(srows[0]['b'])[1]) < 80]
    print("6) aktif:", bool(akt))

# 6) Sohbet ekranına dön; çekmece + 15sn iskelet penceresi videosu
P.sh(P.ADB + ['shell', 'input', 'keyevent', '4']); time.sleep(1.2)
# çıkış dialogu geldi ise Kal
if any(n['t'] == 'Çıkılsın mı?' for n in P.nodes()):
    tapat('Kal', 1.0)
print("7) ekran:", state()[:8])

# 7) force-stop -> start: refreshAll /api/sessions 15sn'de; 2.5sn sonra video+çekmece
P.sh(P.ADB + ['shell', 'rm', '/sdcard/v03.mp4'])
P.sh(P.ADB + ['shell', 'am', 'force-stop', P.PKG]); time.sleep(1.2)
P.sh(P.ADB + ['shell', 'am', 'start', '-n', P.PKG + '/com.hermes.mobile.MainActivity'])
rec = subprocess.Popen(P.ADB + ['shell', 'screenrecord', '--time-limit', '20',
                                 '/sdcard/v03.mp4'],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
time.sleep(2.5)
d = P.clickable_parent_of(lambda n: (n['t'] or n['cd']) == 'Oturumlar')
if d:
    x, y = P.ctr(d)
    P.sh(P.ADB + ['shell', 'input', 'tap', str(x), str(y)])
    print("8) çekmece ateş:", x, y)
# durum örnekleri (iskelet satırları var mı)
for i in range(8):
    time.sleep(1.5)
    b = [n for n in P.nodes() if 'Gezinme' in n['cd']]
    print(f"   t~{2.5+1.5*(i+1):.1f}sn cekmece-acik={bool(b)} metin={[n['t'][:30] for n in P.nodes() if n['t']][:6]}")
rec.wait(timeout=30)
subprocess.run(P.ADB + ['pull', '/sdcard/v03.mp4', f'{P.OUT}/v03-iskelet-final.mp4'],
               capture_output=True, timeout=90)
print("9) video indirildi")
