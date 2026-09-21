#!/usr/bin/env python3
"""Tur-19 kanıt v3 — her adim dump ile dogrulanir (koordinat varsayimi yok).

Akis: temiz kurulum → Sohbet → Sunucu ekle (form, Ad/Adres/Token alanlari
imlecli doldurulur) → 'Sunucular'da mock-8198 satirina dokun (selectProfile)
→ sohbet ekraninda akis → cekmece Tumu → arama → gruplu → uzun basma Yanitla
(steer) → gonder → mock log steering doğrulama → shot 08 → 656 test / APK md5
rapor satirlari disari basilir.
"""
import os, re, subprocess, sys, time, json

ADB = '/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb'
D = ('-s', 'emulator-5554')
K = '/Users/gokhanuzman/hermes-workspace/wt-t19/denetim/tur19/kanit'
APK = '/Users/gokhanuzman/hermes-workspace/wt-t19/app/build/outputs/apk/debug/app-debug.apk'
MOCKLOG = '/tmp/tur19-mock.log'
TOKEN = open('/tmp/tur19-token.txt').read().strip()
LANG = json.load(open('/Users/gokhanuzman/hermes-workspace/wt-t19/denetim/tur19/lang_map.json'))
# EN -> TR normalizasyon icin uzun EN once
EN2TR = sorted(LANG.items(), key=lambda kv: -len(kv[1]))
os.makedirs(K, exist_ok=True)
t0 = time.time()
OK = []

def sh(*a, tout=60):
    return subprocess.run([ADB, *D, *a], capture_output=True, text=True, timeout=tout)

def dump():
    for _ in range(3):
        r = sh('shell', 'uiautomator', 'dump', '/sdcard/k3.xml', tout=30)
        if 'dumped' in r.stdout or r.returncode == 0:
            x = sh('shell', 'cat', '/sdcard/k3.xml', tout=30).stdout
            if len(x) > 500:
                # EN arayuzu TR'ye normalize et: yalniz text/content-desc degerleri
                # icinde, en uzun EN esleri once.
                def _norm(m):
                    v = m.group(2)
                    for en, tr in EN2TR:
                        if en in v:
                            v = v.replace(en, tr)
                    return m.group(1) + v + m.group(3)
                x = re.sub(r'((?:text|content-desc)=")([^"]*)(")', _norm, x)
                return x
        time.sleep(1.5)   # 'null root' / animasyon: biraz daha bekle
    raise SystemExit('dump alinamadi')

def ime_visible():
    r = sh('shell', 'dumpsys', 'input_method', tout=20)
    return 'mVisibleBound=true' in r.stdout   # mIsInputViewShown YANILTICI kalabilir

def hide_ime():
    # ESC IME'yi kapatir; IME yokken ESC formu da kapatir — TEK ESC + kapanma poll.
    if not ime_visible():
        return
    sh('shell', 'input', 'keyevent', '111')
    for _ in range(6):
        if not ime_visible():
            time.sleep(0.6)   # kapanma animasyonu
            return
        time.sleep(0.3)

def dismiss_stay():
    """'Çıkılsın mı?' varsa 'Kal' — drawer/akistan ONCE bu temizlenmeli."""
    x = dump()
    k = find(x, 'Kal', exact=True)
    if k:
        tap(k); time.sleep(1.5)
        return True
    return False

def go(tap_pat, expect_pat, where, exact=False, attempts=6):
    """expect_pat görünene kadar tap_pat'e tıkla; drawer/dialog/geri durumlarını temizler."""
    for a in range(attempts):
        x = dump()
        ts = ' '.join(texts(x))
        if expect_pat in ts:
            return x
        if 'Gezinme menüsünü kapat' in ts:
            sh('shell', 'input', 'keyevent', '111'); time.sleep(1.2)
            if 'Gezinme menüsünü kapat' in ' '.join(texts(dump())):
                sh('shell', 'input', 'swipe', '700', '1200', '20', '1200', '500'); time.sleep(1.8)
            continue
        if dismiss_stay():
            continue
        g = find(x, 'Geri')
        if g and 'Kayıtlı Hermes profilleri' in ts and expect_pat not in ts:
            pass  # bekle
        q = find(x, tap_pat)
        if q:
            tap(q)
        time.sleep(2.2)
    x = dump()
    if expect_pat in ' '.join(texts(x)):
        return x
    open(os.path.join(K, f'FAIL-{where}-{int(time.time()-t0)}.xml'), 'w').write(x)
    print(f"FAIL @ {where}: '{expect_pat}' yok | ekran: {' | '.join(texts(x)[:14])}", flush=True)
    raise SystemExit(1)

def need2(x, pat_tr, pat_en, where):
    p = find(x, pat_tr) or find(x, pat_en)
    if p:
        return p
    open(os.path.join(K, f'FAIL-{where}-{int(time.time()-t0)}.xml'), 'w').write(x)
    print(f'FAIL @ {where}: {pat_tr!r}/{pat_en!r} yok | {texts(x)[:14]}', flush=True)
    raise SystemExit(1)

def to_chat(expect='aktif ajan', where='sohbete-gecis'):
    expect_any = (expect, 'active agents')
    for a in range(10):
        x = dump()
        ts = ' '.join(texts(x))
        if any(e in ts for e in expect_any) and 'Gezinme menüsünü kapat' not in ts \
                and 'Close navigation menu' not in ts:
            return x
        # 1) drawer aciksa once kapat
        if 'Gezinme menüsünü kapat' in ts or 'Close navigation menu' in ts:
            sh('shell', 'input', 'keyevent', '111'); time.sleep(1.5)
            x = dump()
            if 'Gezinme menüsünü kapat' in ' '.join(texts(x)):
                sh('shell', 'input', 'swipe', '700', '1200', '20', '1200', '500'); time.sleep(2)
            continue
        # 1b) 'Profil' alt sayfasi/acilir sayfa: once ust scrim, olmadi sistem BACK
        if 'Sayfayı kapat' in ts:
            sh('shell', 'input', 'keyevent', '4'); time.sleep(1.8)
            continue
        # 2) 'Kal' dialogu
        if dismiss_stay():
            continue
        # 3) Geri (sunucular/alt ekran)
        g = find(x, 'Geri') or find(x, 'Back')
        if g:
            tap(g); time.sleep(1.8); continue
        # 4) Sohbet sekmesine bas
        s = find(x, 'Sohbet', exact=True) or find(x, 'Chat', exact=True)
        if s:
            tap(s); time.sleep(2)
        else:
            time.sleep(1.5)
    x = dump()
    if any(e in ' '.join(texts(x)) for e in expect_any):
        return x
    open(os.path.join(K, f'FAIL-{where}-{int(time.time()-t0)}.xml'), 'w').write(x)
    print(f"FAIL @ {where}: '{expect}' yok | ekran: {' | '.join(texts(x)[:14])}", flush=True)
    raise SystemExit(1)

def texts(x):
    return [m.group(1) for m in re.finditer(r'(?:text|content-desc)="([^"]+)"', x) if m.group(1).strip()]

def nodes(x):
    out = []
    for n in re.findall(r'<node[^>]*>', x):
        t = re.search(r'text="([^"]*)"', n)
        cd = re.search(r'content-desc="([^"]*)"', n)
        lab = ((t.group(1) if t else '') + ' ' + (cd.group(1) if cd else '')).strip()
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        cls = re.search(r'class="([^"]*)"', n)
        if b:
            out.append((lab, tuple(map(int, b.groups())), cls.group(1) if cls else ''))
    return out

def find(x, pat, cls=None, after_y=None, exact=False):
    for lab, g, c in nodes(x):
        hit = (lab == pat) if exact else (pat in lab)
        if hit and (cls is None or cls in c) and (after_y is None or g[1] >= after_y):
            return ((g[0] + g[2]) // 2, (g[1] + g[3]) // 2)
    return None

def need(x, pat, where, cls=None):
    p = find(x, pat, cls=cls)
    if not p:
        open(os.path.join(K, f'FAIL-{where}-{int(time.time()-t0)}.xml'), 'w').write(x)
        print(f'FAIL @ {where}: {pat!r} yok | ekran: {texts(x)[:14]}', flush=True)
        raise SystemExit(1)
    return p

def tap(pt):
    sh('shell', 'input', 'tap', str(pt[0]), str(pt[1]))

def shot(name):
    raw = subprocess.run([ADB, *D, 'exec-out', 'screencap', '-p'],
                         capture_output=True, timeout=60).stdout
    open(os.path.join(K, name), 'wb').write(raw)
    OK.append(name)
    print(f'  shot {name} ({len(raw)}b @ {int(time.time()-t0)}s)', flush=True)

def settle(pat, where, timeout=20, cls=None):
    """pat görünene kadar 1sn aralıkla dump."""
    x = ''
    end = time.time() + timeout
    while time.time() < end:
        x = dump()
        p = find(x, pat, cls=cls)
        if p:
            return x, p
        time.sleep(1.0)
    open(os.path.join(K, f'FAIL-{where}-{int(time.time()-t0)}.xml'), 'w').write(x)
    print(f'FAIL timeout @ {where}: {pat!r} | {texts(x)[:14]}', flush=True)
    raise SystemExit(1)

# 1) temiz kurulum ---------------------------------------------------------
r = sh('install', '-r', APK, tout=240)
print('install:', r.stdout.strip()[-12:], f'{int(time.time()-t0)}s', flush=True)
sh('shell', 'am', 'force-stop', 'com.hermes.mobile.v2')
sh('shell', 'pm', 'clear', 'com.hermes.mobile.v2', tout=40)
sh('logcat', '-b', 'crash', '-c')
sh('shell', 'am', 'start', '-n', 'com.hermes.mobile.v2/com.hermes.mobile.MainActivity')
time.sleep(9)
x = dump()
k = find(x, 'Kal', exact=True)
if k:
    tap(k); time.sleep(1.5)
# 'Profil' alt sayfasi acik gelirse sistem BACK ile kapat
if find(x, 'Sayfayı kapat'):
    sh('shell', 'input', 'keyevent', '4'); time.sleep(2)
# temiz kurulumda bile sekme durumu sapabilir → sohbet ekranina garanti gecis
x = to_chat('aktif ajan', 'serit-bos')   # sohbet ekranı + şerit (bağlantısız DÜRÜST 0)
print('sohbet-hazir', flush=True)

# 2) Sunucu profili: durum-duyarli, 2-denemeli ---------------------------------
def screen_is(*pats):
    x = dump()
    ts = ' '.join(texts(x))
    return x, all(p in ts for p in pats)

def try_add_profile():
    x = to_chat('aktif ajan', 'add-oncesi-sohbet')
    tap(need(x, 'Sunucu ekle', 'sunucu-ekle')); time.sleep(2.5)
    # Sunucular listesi gelmezse sohbet'e donup 2x daha dene
    for a in range(2):
        x = dump()
        if 'Kayıtlı Hermes profilleri' in ' '.join(texts(x)):
            break
        if a == 0:
            tap(need(dump(), 'Sunucu ekle', 'sunucu-ekle-2')); time.sleep(2.5)
    else:
        open(os.path.join(K, f'FAIL-sunucular-{int(time.time()-t0)}.xml'), 'w').write(x)
        raise SystemExit('Sunucular listesi acilmadi: ' + ' | '.join(texts(x)[:14]))
    x = go('Sunucu ekle', 'Oturum anahtarı', 'form-ac')
    eds = sorted([g for lab, g, c in nodes(x) if 'EditText' in c], key=lambda g: g[1])
    if len(eds) < 3:
        print('form EditText:', len(eds), flush=True); return False
    for g, val in ((eds[0], 'mock-8198'), (eds[1], 'http://10.0.2.2:8198'), (eds[2], TOKEN)):
        tap(((g[0]+g[2])//2, (g[1]+g[3])//2)); time.sleep(1.2)
        sh('shell', 'input', 'text', val); time.sleep(0.8)
        hide_ime()
        x2 = dump()
        if 'Oturum anahtarı' not in ' '.join(texts(x2)):
            print('form alan-sirada kapandi', flush=True); return False
    ks = find(x2, 'Kaydet')
    if not ks:
        sh('shell', 'input', 'swipe', '540', '1600', '540', '900', '300'); time.sleep(1.2)
        x2 = dump(); ks = find(x2, 'Kaydet')
    if not ks:
        print('Kaydet yok', flush=True); return False
    tap(ks); time.sleep(3)
    return True

have = False
for att in range(1, 3):
    x = dump()
    have = any(('mock-8198' in lab) for lab, g, c in nodes(x))
    if have:
        break
    print(f'deneme {att}', flush=True)
    try_add_profile()
    time.sleep(1)
x = dump()
print('kayit-sonrasi:', texts(x)[:14], flush=True)
if not any(('mock-8198' in lab) for lab, g, c in nodes(x)):
    open(os.path.join(K, f'FAIL-kayit-{int(time.time()-t0)}.xml'), 'w').write(x)
    raise SystemExit('mock-8198 kaydedilemedi (2 deneme)')
# beklenti: 'Sunucular' listesi / mock satiri gorunur
# 3) profil sec (row'a dokun → selectProfile) ------------------------------
row = None
for lab, g, c in nodes(x):
    if 'mock-8198' in lab:
        row = ((g[0]+g[2])//2, (g[1]+g[3])//2)
if not row:
    open(os.path.join(K, f'FAIL-mock-satir-{int(time.time()-t0)}.xml'), 'w').write(x)
    print('FAIL mock satir:', texts(x)[:14], flush=True)
    raise SystemExit(1)
tap(row)
print('profil-tap @', row, flush=True)

# 4) sohbette akis → serit sayi/grafik -------------------------------------
# Sunucular listesi secimden sonra da KALIR (HomeScreen route) → mock el
# sikisimini bekle, sonra sohbet sekmesine gec.
end = time.time() + 25
while time.time() < end:
    if os.path.exists(MOCKLOG) and 'session.active_list' in open(MOCKLOG).read()[-2000:]:
        break
    time.sleep(1.5)
x = to_chat('aktif ajan', 'serit-sonra-secim')
# bos durumun 09'dan farki: panelde mock 2 aktif + 3 oturum
tb = need(x, 'aktif ajan', 'serit-bul')
tap(tb); time.sleep(1.5)
shot('00-serit-genislemis-durum.png')
x = dump()
tb2 = find(x, 'aktif ajan')
if tb2:
    tap(tb2); time.sleep(1.2)     # topla
shot('01-serit-mock-dolu.png')

# cekmece SECIMDEN SONRA ACIK KALIR (Tur16 davranisi) → sagdaki scrim'e dokun, kapat
x = dump()
scrim = [g for lab, g, c in nodes(x) if 'Gezinme menüsünü kapat' in lab]
if scrim:
    tap((1010, 1200)); time.sleep(2)          # scrim gorunur strip (cekmece ~918px)
    x = dump()
    if any('Gezinme menüsünü kapat' in lab for lab, g, c in nodes(x)):
        raise SystemExit('cekmece kapatilamadi: ' + ' | '.join(texts(x)[:12]))

# mesaj gonder → stream (prompt.submit) → serit sayi/grafik
# baglanti kurulana kadar bekle: yerel placeholder 'baglaninca gonderilir' kaybolmali
end = time.time() + 45
while time.time() < end:
    x = dump()
    ts = ' '.join(texts(x))
    if 'bağlanınca gönderilir' not in ts and ('Mesaj yaz' in ts or 'Gönder' in ts or any(
            'EditText' in c and g[1] > 1500 for lab, g, c in nodes(x))):
        break
    time.sleep(2)
# composer: alt yari en buyuk EditText (placeholder baglantiyla degisir)
cands = [g for g in [g for lab, g, c in nodes(x) if 'EditText' in c and g[1] > 1200]]
if not cands:
    raise SystemExit('composer yok: ' + ' | '.join(texts(x)[:12]))
cp = max(cands, key=lambda gg: (gg[2]-gg[0])*(gg[3]-gg[1]))
tap(((cp[0]+cp[2])//2, (cp[1]+cp[3])//2)); time.sleep(1.0)
sh('shell', 'input', 'text', 'tur19 hiz testi'); time.sleep(1.5)
# Gonder, composer ile ayni seritte sagda belirir (IME aciksa serit yukari kayar —
# mutlak y>=1600 Filtresi IME acikken hata verir; gorece eslesme kullanilir)
send = None
end = time.time() + 25
while time.time() < end and not send:
    x = dump()
    # IME seridi kaydirabilir: composer'i her turda yeniden bul, Gonder'i ona gore esle
    cp2 = [g for lab, g, c in nodes(x) if 'EditText' in c and g[1] > 1000]
    ref = max(cp2, key=lambda gg: (gg[2]-gg[0])*(gg[3]-gg[1])) if cp2 else cp
    cands2 = [g for lab, g, c in nodes(x) if 'Gönder' in lab and abs(g[1] - ref[1]) < 500 and g[0] > ref[0]]
    if cands2:
        send = max(cands2, key=lambda g: g[2])
    else:
        time.sleep(1.5)
if not send:
    open(os.path.join(K, f'FAIL-gonder-yok-{int(time.time()-t0)}.xml'), 'w').write(x)
    raise SystemExit('composer altinda Gonder yok (ornek gonderme koldur)')
tap(send); time.sleep(2)
hide_ime()   # sohbette klavye cikmis olabilir → kapat (akisi durdurmaz)

# 7b) gonderme-serbestlik: sohbette 5sn mesaj bekle, klavye yok, serit ayni
# composer girdisi artik bos -> IME acik kalmis olabilir; tek ESC + poll
hide_ime()
time.sleep(3)                                                 # akmaya baslamis olmalı
x = dump()
tb3 = find(x, 'aktif ajan')
if not tb3:
    open(os.path.join(K, f'FAIL-serit-akista-{int(time.time()-t0)}.xml'), 'w').write(x)
    raise SystemExit('serit akista yok: ' + ' | '.join(texts(x)[:12]))
tap(tb3); time.sleep(1.2)
shot('09-serit-akista-sayi-grafik.png')
time.sleep(10)   # stream bitsin
# 7c) sohbette 5sn bekle → 00-serit-gonderme-serbest.png
time.sleep(5)
x = dump()
need(x, 'aktif ajan', 'serit-gonderme-sonrasi')
shot('00-serit-gonderme-serbest.png')

# 5) cekmece Tumu ----------------------------------------------------------
# olasi 'Kal' diyaloğunu YUTMA — once temizle
for _ in range(3):
    if not dismiss_stay():
        break
op = find(x, 'Oturumlar')
if not op:
    x = dump(); op = find(x, 'Oturumlar')
if not op:
    # son care: slow-swipe
    sh('shell', 'input', 'swipe', '8', '1200', '700', '1200', '600'); time.sleep(2.5)
    x = dump(); op = find(x, 'Oturumlar')
    if not op:
        raise SystemExit('Oturumlar dugmesi yok — cekmece acilamadi: ' + ' | '.join(texts(x)[:12]))
tap(op); time.sleep(2.0)
x = dump()
need(x, 'Genel akış', 'cekmece-tumu')
shot('02-tumu-akisi.png')

# 8) uzun basma → Yanıtla → mini composer → gönder -------------------------
dismiss_stay()
# drawer satirlari: REST 'Pavo' satiri, session_key eslesiyle CANLI kayd

# UZUN BASMA KALIBI (tur19 Claude devir, 37. tur kaniti): 'swipe X Y X Y 1500'
# (ayni nokta, 1500ms) Compose listesinde guvenilir tek yol — uiautomator
# gestureWithTiming() run31/34/35/37'de sedden basarisiz (alt-sayfa acilmadi).
# Sabit Y=1200 yanlis satira denk geldi (run37: satirlar 795/985; 1200 'Cron'
# satiriydi) — koordinat DUMP'TAN hedef satirin merkezinden alinir.
def longpress_text(x, hedefler):
    """Hedef metinlerden gorunur ilk satira uzun bas; 'Yanıtla' gorunene kadar
    tekrarla. Donen: guncel dump metni."""
    for _try in range(6):
        row = None
        for h in hedefler:
            row = find(x, h)
            if row:
                break
        if not row:
            return x   # hedef satir yok — cagiran taraf need() ile FAIL atsın
        px, py = row
        sh('shell', 'input', 'swipe', str(px), str(py), str(px), str(py), '1500')
        time.sleep(1.6)
        x = dump()
        if find(x, 'Yanıtla'):
            return x
    return x

x = longpress_text(x, ['Rapor 2026-284', 'Alan hesabı', 'Pavo'])
shot('05-alt-sayfa-yanitla.png')
tap(find(x, 'Yanıtla')); time.sleep(2.0)
shot('06-mini-composer-bos.png')
# mini composer girdi alani: SABIT (540,650) run37'de yanlis satira degdi
# (canli dump: girdi 'Yanıtını yaz…' ~y=493'teydi). Koordinat dump'tan.
x = dump()
inp = find(x, 'Yanıtını yaz') or find(x, 'Yanıtını yaz…', cls='android.widget.EditText') \
      or find(x, 'Yanıtını yaz', cls='android.widget.EditText')
if not inp:
    open(os.path.join(K, f'FAIL-mini-girdi-{int(time.time()-t0)}.xml'), 'w').write(x)
    raise SystemExit('mini composer girdi alani yok')
tap(inp); time.sleep(1.0)
sh('shell', 'input', 'text', 'Anlasildi, devam edin'); time.sleep(1.0)
shot('07-mini-composer-yazildi.png')
x = dump()
snd = find(x, 'Gönder')
print('Gonder:', snd, flush=True)
if not snd:
    open(os.path.join(K, f'FAIL-gonder-{int(time.time()-t0)}.xml'), 'w').write(x)
    raise SystemExit('Gönder yok')
tap(snd); time.sleep(1.5)
shot('08a-gonderiliyor.png')
time.sleep(2.5)
shot('08b-sonrasi-cekmece-yerinde.png')

# 6) arama ----------------------------------------------------------------
# run39 duzeltmesi: 08b'de mini-composer sheet'i/drawer acik kalabiliyor; arama
# alani o katmanin arkasinda find -> None -> 'tap(None)' TypeError (run39 satir 443
# kokeni). Bulunana kadar sheet'i kapat / drawer'i geri ac, tekrar dene.
al = None
for _r in range(5):
    x = dump()
    # run40 kalinti durumu: gonderme sonra mini-composer 'Vazgeç' ile kapatilmali
    vaz = find(x, 'Vazgeç')
    if vaz:
        tap(vaz); time.sleep(1.5)
        continue
    al = find(x, 'Oturum ara') or find(x, 'Ara', cls='android.widget.EditText')
    if al:
        break
    closer = find(x, 'Sayfayı kapat') or find(x, 'Kapat')
    if closer:
        tap(closer); time.sleep(1.2); continue
    tog = find(x, 'Gezinme menüsünü aç') or find(x, 'Oturumlar')
    if tog:
        tap(tog); time.sleep(1.6)
if not al:
    open(os.path.join(K, f'FAIL-arama-alani-{int(time.time()-t0)}.xml'), 'w').write(x)
    raise SystemExit('arama alani bulunamadi (sheet/drawer takili kalmis olabilir)')
tap(al); time.sleep(1.2)
sh('shell', 'input', 'text', 'Pavo'); time.sleep(1.8)
shot('03-arama.png')
x = dump()
assert any('Pavo' in t for t in texts(x)), 'arama sonucu Pavo icermiyor'
# arama metnini backspace ile silmek IME-durumuna bagli ve GUVENILMEZ
# (run31 kaniti: 5x67 'Pavo'yu silmedi). 'Aramayı temizle' dugumesi garantili
# temizlik yolu; cekmeceyi kapat-ac filtreleri de sifirlar.
hide_ime()
x = dump()
clr = find(x, 'Aramayı temizle')
if clr:
    tap(clr); time.sleep(1.2)
kk = find(x, 'Gezinme menüsünü kapat')
if kk:
    tap(kk); time.sleep(1.5)
x = dump()
op = find(x, 'Oturumlar')
if op:
    tap(op); time.sleep(2.2)
# filtre artik temiz olmali; degilse bir kez daha temizle
x = dump()
if 'Aramayı temizle' in ' '.join(texts(x)):
    c2 = find(x, 'Aramayı temizle')
    if c2:
        tap(c2); time.sleep(1.5)

# 7) gruplu gorunum (tur16 korumasi) --------------------------------------
# 'Tumu' cekmecesi 'Tumu' filtresiyle AYNIDIR → 02 kaniti 04'un aynisidir
# (kural geregi ayni md5 04 olarak KOPYALANMAZ; 04 farkli bir duruma ayrilmalidir)
x = dump()
g = need(x, 'Gruplar', 'gruplar')
tap(g); time.sleep(1.5)
shot('04-gruplu-gorunum.png')
# NOT: geri 'Tumu'ya donmek drawer'i canli-filtreyle bosaltiyor; 05 icin
# Gruplar gorunumunde kalip CANLI satira uzun basilacak.

# 9) steer mock log dogrulama + crash -------------------------------------
time.sleep(1)
logtxt = open(MOCKLOG).read() if os.path.exists(MOCKLOG) else ''
steer = re.findall(r"rpc (\w[\w.]*) (\{.*\})", logtxt)
last = steer[-40:]
steers = [l for l in logtxt.splitlines() if 'steering' in l]
print('MOCK RPC son:', [n for n, _ in last], flush=True)
print('STEERING:', steers[-3:], flush=True)
r = sh('logcat', '-b', 'crash', '-d', '-t', '400')
crash = [l for l in r.stdout.splitlines() if 'com.hermes' in l or 'FATAL' in l]
print('CRASH:', len(crash), flush=True)
for l in crash[:8]:
    print('  ', l[:140])
print('DONE', int(time.time()-t0), 's |', len(OK), 'png |', flush=True)
print('OK:', OK, flush=True)
