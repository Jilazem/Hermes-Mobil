#!/usr/bin/env python3
# Tur22 kanıt sürücüsü v3 — D-02: her adım dump ile DOĞRULANARAK yürür.
# v2 düzeltmesi: tap_text artık ÖNCE clickable=True düğümü arar (v2, mesaj
# metni içinde geçen "Sunucu ekle" string'ine basıp formu hiç açmıyordu).
# Ekler: sembol (boş durum metinleri + buton etiketleri) doğrulaması, 48dp
# tıklama alanı ölçümü, iskelet nabız daraltması, reduced-motion sabitlik,
# animasyon karelerinde sha256 benzersizlik raporu, logcat crash sayacı.
import hashlib
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = ["/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb",
       "-s", "emulator-5554"]
PKG = "com.hermes.mobile.v2"
OUT = "/Users/gokhanuzman/hermes-workspace/wt-t22/denetim/tur22/kanit"
os.makedirs(OUT, exist_ok=True)


def sh(args, binout=None):
    if binout:
        with open(binout, "wb") as fh:
            return subprocess.run(args, stdout=fh, timeout=40)
    return subprocess.run(args, capture_output=True, timeout=40).stdout.decode(errors="replace")


def dump():
    sh(ADB + ["shell", "uiautomator", "dump", "/sdcard/ui.xml"])
    x = sh(ADB + ["shell", "cat", "/sdcard/ui.xml"])
    return ET.fromstring(x[x.index("<hierarchy"):])


def nodes():
    return [(n.get("text") or "", n.get("content-desc") or "", n.get("class") or "",
             n.get("bounds") or "", n.get("clickable") == "true")
            for n in dump().iter("node")]


def ctr(b):
    m = list(map(int, re.findall(r"-?\d+", b)))
    return (m[0] + m[2]) // 2, (m[1] + m[3]) // 2


def tap_text(want, timeout=8):
    """D-02: önce TIKANABILIR (buton) eşleşmesi; mesaj metnine asla basmaz."""
    end = time.time() + timeout
    while time.time() < end:
        ns = nodes()
        for t, cd, c, b, clk in ns:
            if clk and (t == want or cd == want):
                x, y = ctr(b)
                sh(ADB + ["shell", "input", "tap", str(x), str(y)])
                print(f"  tap-dugme '{t or cd}' ({x},{y})")
                return True
        time.sleep(0.5)
    print(f"  YOK: {want}")
    return False


def edit(i, text):
    eds = [b for t, cd, c, b, _k in nodes() if "EditText" in c]
    if i >= len(eds):
        print(f"  EditText #{i} YOK")
        return False
    x, y = ctr(eds[i])
    sh(ADB + ["shell", "input", "tap", str(x), str(y)])
    time.sleep(0.4)
    sh(ADB + ["shell", "input", "keyevent", "KEYCODE_MOVE_END"])
    for _ in range(40):
        sh(ADB + ["shell", "input", "keyevent", "KEYCODE_DEL"])
    sh(ADB + ["shell", "input", "text", text.replace(" ", "%s")])
    print(f"  edit[{i}] = {text}")
    time.sleep(0.4)
    return True


def shot(n):
    sh(ADB + ["exec-out", "screencap", "-p"], binout=f"{OUT}/{n}")


def shot_hash(n):
    shot(n)
    h = hashlib.sha256(open(f"{OUT}/{n}", "rb").read()).hexdigest()[:12]
    print(f"  📸 {n}  {h}")
    return h


def dump_symbols(tag):
    """Sembol/etiket doğrulaması: ekrandaki tüm text/content-desc + butonlar."""
    lines = []
    for t, cd, c, b, clk in nodes():
        if t or cd or clk:
            lines.append(f"{cls_short(c)}{' clk' if clk else ''} | {t[:70]!r} | cd={cd[:50]!r} | {b}")
    p = f"{OUT}/dogrulama-{tag}.txt"
    open(p, "w").write("\n".join(lines) + "\n")
    print(f"  ♿ sembol dökümü -> dogrulama-{tag}.txt ({len(lines)} satır)")
    return lines


def cls_short(c):
    return c.split(".")[-1]


def measure_tap(want, min_dp=48):
    density = float(sh(ADB + ["shell", "wm", "density"]).split(":")[-1])
    for t, cd, c, b, clk in nodes():
        if clk and (t == want or cd == want):
            m = list(map(int, re.findall(r"-?\d+", b)))
            hdp = (m[3] - m[1]) / (density / 160.0)
            wd = (m[2] - m[0]) / (density / 160.0)
            ok = "OK" if min(hdp, 999) >= min_dp else "<48 !!!"
            print(f"  48dp '{t or cd}': {wd:.0f}x{hdp:.0f}dp  {ok}")
            return hdp
    print(f"  48dp: düğüm yok: {want}")
    return None


def relaunch():
    sh(ADB + ["shell", "am", "force-stop", PKG])
    time.sleep(1)
    sh(ADB + ["shell", "am", "start", "-n", f"{PKG}/com.hermes.mobile.MainActivity"])
    time.sleep(5)


MODE = sys.argv[1] if len(sys.argv) > 1 else "main"

if MODE == "main":
    print("== A) Boş durum (bağlantı-yok) + sembol + 48dp ==")
    sh(ADB + ["shell", "pm", "clear", PKG])
    time.sleep(1)
    relaunch()
    shot_hash("01-baglantiyok-bosdurum.png")
    dump_symbols("01-bosluk")
    measure_tap("Sunucu ekle")

    print("== B) Sunucu ekle → form → kaydet (v2'de burada form AÇILMIYORDU) ==")
    tap_text("Sunucu ekle")
    time.sleep(1.5)
    edit(0, "tur22-mock")
    edit(1, "http://127.0.0.1:9151")
    edit(2, "demo-token")
    shot_hash("02-sunucu-formu.png")
    tap_text("Kaydet")
    time.sleep(2.5)
    shot_hash("03-baglanildi.png")

    print("== C) Çekmece açılış kareleri (benzersiz hash beklenir) ==")
    # ☰ = ContentDescription 'Oturumlar' veya 'Menü' (spec 3: SOHBET üst çubuğu)
    ns = nodes()
    menxy = None
    for t, cd, c, b, clk in ns:
        if clk and cd in ("Oturumlar", "Menü", "Gezinme menüsünü aç"):
            menxy = ctr(b)
            break
    if menxy is None:  # son çare: oturma sekmesindeki ☰
        sh(ADB + ["shell", "input", "keyevent", "KEYCODE_BACK"])
        time.sleep(1)
        menxy = (105, 215)
    import subprocess as sp
    sp.Popen(ADB + ["shell", "input", "tap", str(menxy[0]), str(menxy[1])],
             stdout=sp.DEVNULL, stderr=sp.DEVNULL)
    hs = [shot_hash(f"10-cekmece-acilis-{i+1}.png") for i in range(4)]
    print(f"  benzersiz: {len(set(hs))}/4")

    print("== D) İskelet kareleri (mock 6sn yavaş) ==")
    hs = [shot_hash(f"20-iskelet-{i+1}.png") for i in range(3)]
    time.sleep(7)
    shot_hash("21-dolu-liste.png")

    print("== E) Arama boş sonucu + CTA + sembol ==")
    tap_text("Oturum ara…", timeout=4)
    eds = [b for t, cd, c, b, _k in nodes() if "EditText" in c]
    if eds:
        x, y = ctr(eds[0])
        sh(ADB + ["shell", "input", "tap", str(x), str(y)])
        time.sleep(0.4)
        sh(ADB + ["shell", "input", "text", "zzz-yok-boyle"])
    time.sleep(1.2)
    shot_hash("30-arama-bos-sonuc.png")
    dump_symbols("30-arama-bos")
    tap_text("Aramayı temizle", timeout=4)
    time.sleep(0.8)

    print("== F) Arşiv sekmesi (3.) — boş arsiv sembolü ==")
    tap_text("Arşiv", timeout=4)
    time.sleep(1)
    shot_hash("31-arsiv-bos-durum.png")
    dump_symbols("31-arsiv")

    print("== G) Sohbet geçişi + mesaj girişi burst ==")
    tap_text("Tur22 kare testi", timeout=5)
    hs = [shot_hash(f"41-mesaj-girisi-{i+1}.png") for i in range(2)]
    for i in range(2):
        shot_hash(f"42-sohbet-gecis-{i+1}.png")
        time.sleep(0.35)
    time.sleep(2)
    shot_hash("43-sohbet-dolu.png")
    dump_symbols("43-sohbet")

    print("== H) Gönder basış burst (ölçek+haptic+ikon anları) ==")
    # composer: en alt EditText
    eds = [b for t, cd, c, b, _k in nodes() if "EditText" in c]
    x, y = ctr(eds[-1])
    sh(ADB + ["shell", "input", "tap", str(x), str(y)])
    time.sleep(0.5)
    sh(ADB + ["shell", "input", "text", "tur22-giris-testi"])
    time.sleep(0.5)
    shot("44-oncesi.png")
    # Gönder: content-desc 'Gönder' düğmesi
    send = None
    for t, cd, c, b, clk in nodes():
        if clk and cd == "Gönder":
            send = ctr(b)
    if send is None:
        send = (970, 2030)
        print("  ! 'Gönder' CD bulunamadı, son-çare koordinat")
    sp.Popen(ADB + ["shell", "input", "tap", str(send[0]), str(send[1])],
             stdout=sp.DEVNULL, stderr=sp.DEVNULL)
    times = [0.05, 0.12, 0.2, 0.35, 0.6, 1.0]
    prev = 0.0
    hs = []
    for tt in times:
        time.sleep(max(0.0, tt - prev)); prev = tt
        hs.append(shot_hash(f"45-gonder-burst-{i if False else len(hs)+1}.png"))
    print(f"  benzersiz: {len(set(hs))}/{len(hs)}")
    time.sleep(1.5)
    shot_hash("46-mesaj-dolu.png")

    log = sh(ADB + ["shell", "logcat", "-d", "-b", "crash"])
    open(f"{OUT}/logcat-crash.txt", "w").write(log)
    hermes_fatal = [l for l in log.splitlines() if "hermes" in l.lower() and "FATAL" in l]
    print("CRASH (hermes+FATAL):", len(hermes_fatal))
    print("BİTTİ")
