#!/usr/bin/env python3
# Tur22 kanıt sürücüsü — burst screencap ile animasyon anlarını ve boş durumları
# toplar. Yalnız debug kanıtıdır; ürün koduna dokunmaz.
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = ["adb", "-s", "emulator-5554"]
PKG = "com.hermes.mobile.v2"
OUT = "/Users/gokhanuzman/hermes-workspace/wt-t22/denetim/tur22/kanit"


def sh(*args, t=30):
    return subprocess.run(args, capture_output=True, text=True, timeout=t).stdout


def ui_dump():
    sh(*ADB, "shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = sh(*ADB, "shell", "cat", "/sdcard/ui.xml")
    return ET.fromstring(xml[xml.index("<hierarchy"):])


def find(pred):
    try:
        root = ui_dump()
    except Exception as e:
        print("  dump hatası:", e)
        return None
    for n in root.iter("node"):
        if pred(n):
            m = list(map(int, re.findall(r"-?\d+", n.get("bounds"))))
            return ((m[0] + m[2]) // 2, (m[1] + m[3]) // 2), (n.get("text") or n.get("content-desc"))
    return None


def tap_node(pred, label):
    hit = find(pred)
    if not hit:
        print(f"  BULUNAMADI: {label}")
        return False
    (x, y), txt = hit
    print(f"  tap {label} -> ({x},{y}) '{txt}'")
    sh(*ADB, "shell", "input", "tap", str(x), str(y))
    return True


def tap(x, y):
    sh(*ADB, "shell", "input", "tap", str(x), str(y))


def text(s):
    s = s.replace(" ", "%s").replace("'", "'\\''")
    sh(*ADB, "shell", "input", "text", s)


def shot(name):
    sh(*ADB, "exec-out", "screencap", "-p", stdout_path := f"{OUT}/{name}") if False else \
        subprocess.run(ADB + ["exec-out", "screencap", "-p"], stdout=open(f"{OUT}/{name}", "wb"), timeout=30)
    print(f"  📸 {name}")


def burst(prefix, n, gap):
    for i in range(n):
        shot(f"{prefix}-{i+1:02d}.png")
        time.sleep(gap)


def relaunch():
    sh(*ADB, "shell", "am", "force-stop", PKG)
    time.sleep(1)
    sh(*ADB, "shell", "am", "start", "-n", f"{PKG}/com.hermes.mobile.MainActivity")
    time.sleep(6)


print("== 1) Uygulamayı sıfırdan aç, Sunucu ekle CTA ==")
relaunch()
shot("01-baglantiyok-bosdurum.png")

# CTA: EmptyState 'Sunucu ekle' TextButton
if not tap_node(lambda n: (n.get("text") or "") == "Sunucu ekle", "CTA Sunucu ekle"):
    # Yedek: header'daki ilk tıklanabilir (sunucu ekranı)
    tap(105, 215)
time.sleep(2)
shot("02-sunucu-ekleme.png")

# Sunucu formunu doldur
print("== 2) Sunucu formu ==")
# URL alanı
hit = find(lambda n: "http" in (n.get("text") or "") or n.get("class") == "android.widget.EditText")
root_url = None
root = None
try:
    root = ET.fromstring((sh(*ADB, "shell", "uiautomator", "dump", "/sdcard/ui.xml"),
                          sh(*ADB, "shell", "cat", "/sdcard/ui.xml"))[1][60:])
except Exception:
    pass
# Basit yol: ekrandaki EditText'leri sırayla doldur (URL, token)
eds = []
try:
    r2 = ui_dump()
    for n in r2.iter("node"):
        if "EditText" in (n.get("class") or ""):
            m = list(map(int, re.findall(r"-?\d+", n.get("bounds"))))
            eds.append(((m[0]+m[2])//2, (m[1]+m[3])//2))
except Exception as e:
    print("edit dump err", e)
print("  EditText konumları:", eds)
if len(eds) >= 1:
    tap(*eds[0]); time.sleep(0.5); text("http://127.0.0.1:9151"); time.sleep(0.5)
if len(eds) >= 2:
    tap(*eds[1]); time.sleep(0.5); text("demo-token"); time.sleep(0.5)
shot("03-sunucu-formu-dolu.png")

# Kaydet/Kes düğmesi
saved = tap_node(lambda n: (n.get("text") or "") in ("Kaydet", "Ekle", "Bağlan", "Tamam"), "Kaydet")
if not saved:
    # actionbar son tıklanabilir
    pass
time.sleep(3)
shot("04-baglanti-sonrasi.png")

print("== 3) Çekmece: iskelet burst + 3 açılış karesi ==")
# 3 ardışık aç/kapa — burst 150ms
tap(105, 215)
burst("10-cekmece-acilis", 3, 0.15)
time.sleep(0.7)
shot("11-cekmece-dolu.png")
# İskelet → dolu: force-stop + yeniden aç (ilk yükleme 2.5s yavaş)
sh(*ADB, "shell", "am", "force-stop", PKG)
sh(*ADB, "shell", "am", "start", "-n", f"{PKG}/com.hermes.mobile.MainActivity")
time.sleep(6)
tap(105, 215)
burst("20-iskelet", 3, 0.2)   # iskelet anı (2.5s penceresi)
time.sleep(2.6)
shot("21-dolu-liste.png")

# Arama: boş arama sonucu
print("== 4) Çekmece arama boş sonucu ==")
hit = find(lambda n: "Oturum ara" in (n.get("text") or "") or
            (n.get("class") or "").endswith("EditText"))
# arama alanına tıkla (çekmece açık)
a = find(lambda n: n.get("class") == "android.widget.EditText")
if a:
    (x, y), _ = a
    tap(x, y); time.sleep(0.5); text("zzz-yok-boyle-bir-sey"); time.sleep(1)
shot("30-arama-bos-sonuc.png")
text("")  # temizle yok; manuel
# temizle düğmesi
tap_node(lambda n: (n.get("content-desc") or "") == "Temizle", "Aramayı temizle")
time.sleep(0.6)

# Arşiv sekmesi
print("== 5) Arşiv sekmesi boş durumu ==")
tap_node(lambda n: (n.get("text") or "") == "Arşiv", "Arşiv sekmesi")
time.sleep(0.8)
shot("31-arsiv-bos-durum.png")
# 4. durum — sohbet boş-sohbet zaten 01'de (bağlantı-yok).

# Mesaj girişi
print("== 6) Mesaj girişi ==")
# çekmece kapat
tap(700, 700); time.sleep(0.8)
composer = find(lambda n: "Mesaj yaz" in (n.get("text") or "") or
                (n.get("class") or "").endswith("EditText"))
# composer'ı bul: ekranın alt kısmındaki EditText
r3 = ui_dump()
best = None
for n in r3.iter("node"):
    if "EditText" in (n.get("class") or ""):
        m = list(map(int, re.findall(r"-?\d+", n.get("bounds"))))
        if m[1] > 1800:
            best = ((m[0]+m[2])//2, (m[1]+m[3])//2)
if best:
    tap(*best); time.sleep(0.6); text("tur22-mesaj-girisi"); time.sleep(0.4)
shot("40-oncesi.png")
# gönder düğmesi — EditText sağı, sağ alt
tap(970, best[1] if best else 2030)
burst("41-mesaj-girisi", 2, 0.12)
time.sleep(0.6)
shot("42-mesaj-dolu.png")

# Crash buffer
log = sh(*ADB, "shell", "logcat", "-d", "-b", "crash")
open(f"{OUT}/logcat-crash.txt", "w").write(log)
hermes = [l for l in log.splitlines() if "hermes" in l.lower() and "FATAL" in l]
print("== CRASH satırı (hermes+FATAL):", len(hermes))
print("BİTTİ")
