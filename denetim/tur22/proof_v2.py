#!/usr/bin/env python3
# Tur22 — temiz kanıt sürücüsü v2 (D-02: her adım dump ile DOĞRULANARAK yürür;
# 6s'lik mock yavaşlatması iskelet penceresini genişletir).
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = ["/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb",
       "-s", "emulator-5554"]
PKG = "com.hermes.mobile.v2"
OUT = "/Users/gokhanuzman/hermes-workspace/wt-t22/denetim/tur22/kanit"


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


def tap_text(want, cls="TextView", timeout=8):
    end = time.time() + timeout
    while time.time() < end:
        for t, cd, c, b, _clk in nodes():
            if want in t or want in cd:
                x, y = ctr(b)
                sh(ADB + ["shell", "input", "tap", str(x), str(y)])
                print(f"  tap '{t or cd}' ({x},{y})")
                return True
        time.sleep(0.6)
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
    # mevcut içeriği temizle (Select-all + sil)
    sh(ADB + ["shell", "input", "keyevent", "KEYCODE_MOVE_END"])
    for _ in range(40):
        sh(ADB + ["shell", "input", "keyevent", "KEYCODE_DEL"])
    sh(ADB + ["shell", "input", "text", text.replace(" ", "%s")])
    print(f"  edit[{i}] = {text}")
    time.sleep(0.4)
    return True


def shot(n):
    sh(ADB + ["exec-out", "screencap", "-p"], binout=f"{OUT}/{n}")
    print(f"  📸 {n}")


# 01 bağlantı-yok boş durumu (temiz açılış — CTA 48dp altı kontrolü, spec 6)
shot("01-baglantiyok-bosdurum.png")
density = float(sh(ADB + ["shell", "wm", "density"]).split(":")[-1])
for t, cd, c, b, clk in nodes():
    if clk and (t in ("Sunucu ekle", "Aramayı temizle", "Yeni sohbet") or
                cd in ("Sunucu ekle",)):
        m = list(map(int, re.findall(r"-?\d+", b)))
        hdp = (m[3] - m[1]) / (density / 160.0)
        print(f"  a11y 48dp kontrolü: '{t or cd}' = {hdp:.0f}dp{'  <48 !!!' if hdp < 48 else '  OK'}")


# 02 Sunucu ekle → formu doldur → kaydet
tap_text("Sunucu ekle")
time.sleep(1.5)
edit(0, "tur22-mock")
edit(1, "http://127.0.0.1:9151")
edit(2, "demo-token")
shot("02-sunucu-formu.png")
tap_text("Kaydet", timeout=5)
time.sleep(2)
shot("03-baglanildi.png")

# 10 Çekmece açılış — 6 sn yükleme penceresi, 3 kare
sh(ADB + ["shell", "input", "keyevent", "KEYCODE_BACK"])
time.sleep(1)
# oturum sekmesindeki ☰ / Oturumlar — sol üst
start = time.time()
sh(ADB + ["shell", "input", "tap", "105", "215"])
print(f"  tap at {time.time()-start:.2f}")
for i in range(3):
    shot(f"10-cekmece-acilis-{i+1}.png")
    time.sleep(0.25)

# 20 iskelet — polling 6s yavaş; çekmece açıkken 3 kare
for i in range(3):
    shot(f"20-iskelet-{i+1}.png")
    time.sleep(0.5)
# bekle: liste dolmalı (6sn penceresi — ilk tap'tan ~8sn sonra)
while time.time() - start < 10:
    time.sleep(1)
shot("21-dolu-liste.png")

# 30 arama boş sonucu
tap_text("Oturum ara", timeout=4)
# placeholder metin EditText'te olur — ilk EditText'e yaz
eds = [b for t, cd, c, b, _k in nodes() if "EditText" in c]
if eds:
    x, y = ctr(eds[0])
    sh(ADB + ["shell", "input", "tap", str(x), str(y)])
    time.sleep(0.5)
    sh(ADB + ["shell", "input", "text", "zzz-yok-boyle"])
time.sleep(1.2)
shot("30-arama-bos-sonuc.png")
# temizle
tap_text("Aramayı temizle", timeout=4)
time.sleep(0.8)

# 31 arşiv sekmesi boş
tap_text("Arşiv", timeout=4)
time.sleep(1)
shot("31-arsiv-bos-durum.png")

# 11/40 çekmece kapat — 1. satıra bas (sohbet değişimi + mesaj girişi burst)
tap_text("Tur22 kare testi", timeout=5)
for i in range(2):
    shot(f"41-mesaj-girisi-{i+1}.png")
    time.sleep(0.2)
# 1.5s mesaj mock gecikmesi → iskelet → 2 kare
for i in range(2):
    shot(f"42-sohbet-gecis-{i+1}.png")
    time.sleep(0.35)
time.sleep(2)
shot("43-sohbet-dolu.png")

# haptic/ölçek anı: composer'a yaz, gönder butonuna bas
for t, cd, c, b, _k in nodes():
    if "Mesaj yaz" in t or ("EditText" in c and int(re.findall(r"\d+", b)[1]) > 1800):
        x, y = ctr(b)
        sh(ADB + ["shell", "input", "tap", str(x), str(y)])
        break
time.sleep(0.6)
sh(ADB + ["shell", "input", "text", "tur22-giris-testi"])
time.sleep(0.6)
shot("44-oncesi.png")
# gönder düğmesi: en sağ alt kare (970, ~2030)
sh(ADB + ["shell", "input", "tap", "970", "2030"])
for i in range(3):
    shot(f"45-mesaj-giris-burst-{i+1}.png")
    time.sleep(0.2)
time.sleep(1.5)
shot("46-mesaj-dolu.png")

log = sh(ADB + ["shell", "logcat", "-d", "-b", "crash"])
open(f"{OUT}/logcat-crash.txt", "w").write(log)
hermes_fatal = [l for l in log.splitlines() if "hermes" in l.lower() and "FATAL" in l]
print("CRASH (hermes+FATAL):", len(hermes_fatal))
print("BİTTİ")
