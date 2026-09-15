#!/usr/bin/env python3
"""Tur-12 — emülatör UI sürücüsü: "Sesli mesaj" bölümünde CANLI durum + Isıt akışı.

Gerçek uygulama (com.hermes.mobile.v2) + Mac'teki sözleşme mock'u (10.0.2.2:8199).
Mock motorları tembel: /health hepsini "kapali" der, /synthesize o motoru "acik"
yapar — ısıtma akışının kanıtı budur.

Kullanım: python3 ui_warm.py <faz>
  faz1 : Ayarlar → Canlı ses → Sesli mesaj; voice_api alanına mock adresi yaz,
         canlı durum satırını 2 kez yokla (4,5 sn yenileme kanıtı) + ekran görüntüsü
  faz2 : "Isıt" düğmesi: çift tık koruması + "Isıtılıyor…" + "Hazır ✓" + durum dönüşü
  faz3 : motoru Kadın'a al, "Şimdi dene" → kapalı motor ipucu
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = "/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb"
PKG = "com.hermes.mobile.v2"
DEV = "emulator-5554"
OUT = "/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur12"
DUMP = "/sdcard/tur12-ui.xml"


def sh(*args, timeout=90):
    return subprocess.run([ADB, "-s", DEV, *args], capture_output=True, text=True, timeout=timeout)


def dump(tag: str):
    sh("shell", "uiautomator", "dump", DUMP)
    xml = sh("shell", "cat", DUMP).stdout
    with open(f"{OUT}/ui-{tag}.xml", "w", encoding="utf-8") as fh:
        fh.write(xml)
    return xml


def nodes(xml: str):
    out = []
    root = ET.fromstring(xml)
    for n in root.iter("node"):
        t = n.get("text") or ""
        d = n.get("content-desc") or ""
        b = n.get("bounds") or ""
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
        if (t or d) and m:
            x1, y1, x2, y2 = (int(v) for v in m.groups())
            out.append({
                "text": t,
                "desc": d,
                "cls": n.get("class") or "",
                "cx": (x1 + x2) // 2,
                "cy": (y1 + y2) // 2,
                "box": (x1, y1, x2, y2),
            })
    return out


def edit_text_nodes(xml: str):
    """Compose metin alanları uiautomator'da class=EditText görünür (text boş olabilir)."""
    out = []
    root = ET.fromstring(xml)
    for n in root.iter("node"):
        if (n.get("class") or "").endswith("EditText"):
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
            if m:
                x1, y1, x2, y2 = (int(v) for v in m.groups())
                out.append({
                    "text": n.get("text") or "",
                    "cls": "EditText",
                    "cx": (x1 + x2) // 2,
                    "cy": (y1 + y2) // 2,
                    "box": (x1, y1, x2, y2),
                })
    return out


def find(ns, needle, field="text"):
    return [n for n in ns if needle.lower() in (n[field] or "").lower()]


def tap(x, y):
    sh("shell", "input", "tap", str(x), str(y))
    time.sleep(1.2)


def tap_node(n):
    tap(n["cx"], n["cy"])


def shot(name: str):
    png = subprocess.run([ADB, "-s", DEV, "exec-out", "screencap", "-p"], capture_output=True).stdout
    with open(f"{OUT}/{name}.png", "wb") as fh:
        fh.write(png)
    print(f"[ekran] {name}.png ({len(png)} bayt)")


def scroll_up(times=1):
    for _ in range(times):
        sh("shell", "input", "swipe", "540", "1800", "540", "900", "300")
        time.sleep(1.0)


def visible_texts(xml):
    root = ET.fromstring(xml)
    return [n.get("text") for n in root.iter("node") if n.get("text")]


def report(tag):
    xml = dump(tag)
    ts = visible_texts(xml)
    print(f"--- görünen metinler ({tag}) ---")
    for t in ts:
        print("   ", t)
    return xml


def anahtar(xml):
    ts = visible_texts(xml)
    for key in ["Ses ucu durumu", "canlı ·", "Hazır", "Isıt", "Motor kapalı", "yoklama"]:
        hits = [t for t in ts if key in t]
        if hits:
            print(f"  ANAHTAR[{key}]: {hits}")


def faz1():
    print("== FAZ 1: canlı durum ==")
    sh("shell", "am", "force-stop", PKG)
    time.sleep(1)
    sh("shell", "am", "start", "-n", f"{PKG}/com.hermes.mobile.MainActivity")
    time.sleep(9)
    ns = nodes(dump("f1-ana"))
    ayar = find(ns, "Ayarlar")
    if ayar:
        tap_node(ayar[-1])
        time.sleep(2.5)
    ns = nodes(dump("f1-ayarlar"))
    cat = find(ns, "Canlı ses")
    if not cat:
        print("HATA: 'Canlı ses' kategorisi bulunamadı")
        return 1
    tap_node(cat[0])
    time.sleep(2.5)
    xml = report("f1-bolum-ust")
    ns = nodes(xml)
    alan = find(ns, "voice_api") or find(ns, "Seslendirme motoru")
    # Ses bölümünün dibine in (voice_api alanı + durum kartı).
    for _ in range(8):
        ns = nodes(dump("f1-scroll"))
        if find(ns, "voice_api"):
            break
        scroll_up(1)
    ns = nodes(dump("f1-bolum"))
    field = [n for n in ns if n["text"].strip() == "" or "10.0.2.2" in n["text"] or "http" in n["text"]]
    # Alanı metnine göre bul: voice_api başlığının ALTINDAKİ EditText.
    va = find(ns, "voice_api")
    hedef = None
    if va:
        y0 = va[0]["box"][3]
        for n in ns:
            if n["box"][1] >= y0 - 10 and n["box"][2] - n["box"][0] > 500:
                hedef = n
                break
    print("voice_api düğümü:", va[0]["box"] if va else None, "| metin alanı:", hedef["box"] if hedef else None)
    if hedef:
        tap_node(hedef)
        time.sleep(1.0)
        sh("shell", "input", "text", "http://10.0.2.2:8199")
        time.sleep(1.5)
        sh("shell", "input", "keyevent", "4")   # klavyeyi kapat
        time.sleep(1.5)
    xml = report("f1-durum1")
    anahtar(xml)
    shot("emulator-canli-durum-1")
    print("... 6 sn bekle: canlı yenileme sayacı artmalı ...")
    time.sleep(6)
    xml = report("f1-durum2")
    anahtar(xml)
    shot("emulator-canli-durum-2")
    return 0


def ike_gorunur():
    out = sh("shell", "dumpsys", "input_method").stdout
    m = re.search(r"mInputShown=(\w+)", out)
    return (m.group(1) == "true") if m else None


def faz1b():
    """Ayarlar → Canlı ses → Sesli mesaj: durum kartı + canlı yenileme sayacı."""
    print("== FAZ 1b: durum kartı (yerinde) ==")
    ns = nodes(dump("f1b-liste"))
    cat = find(ns, "Canlı ses")
    if cat:
        tap_node(cat[0])
        time.sleep(2.5)
    for i in range(8):
        ns = nodes(dump(f"f1b-scroll{i}"))
        if find(ns, "Ses ucu durumu"):
            print(f"durum kartı {i}. kaydırmada göründü")
            break
        scroll_up(1)
    xml = report("f1b-durum1")
    anahtar(xml)
    ns = nodes(xml)
    va = find(ns, "voice_api")
    for n in ns:
        if va and n["box"][1] >= va[0]["box"][3] - 10 and n["box"][2] - n["box"][0] > 500:
            print("voice_api alanındaki metin:", repr(n["text"]))
            break
    shot("emulator-canli-durum-1")
    print("... 6 sn bekle ...")
    time.sleep(6)
    xml = report("f1b-durum2")
    anahtar(xml)
    shot("emulator-canli-durum-2")
    return 0


def _alan_doldur(hedef):
    tap_node(hedef)
    time.sleep(1.2)
    sh("shell", "input", "text", "http://10.0.2.2:8199")
    time.sleep(1.5)
    if ike_gorunur():
        sh("shell", "input", "keyevent", "4")   # yalnız klavye açıkken
        print("klavye kapatıldı (keyevent 4)")
    else:
        print("klavye açılmamış — BACK gönderilmedi")
    time.sleep(1.5)


def _voice_alani(xml):
    """voice_api başlığının ALTINDAKİ EditText (Compose metin alanı)."""
    ets = edit_text_nodes(xml)
    ns = nodes(xml)
    va = find(ns, "voice_api")
    if not va or not ets:
        return None
    alt = va[0]["box"][3]
    aday = [e for e in ets if e["box"][1] >= alt - 20]
    return aday[0] if aday else (ets[-1] if ets else None)


def _bul(ns, anahtar, tag=""):
    hits = [n for n in ns if anahtar.lower() in (n["text"] or "").lower()]
    if tag:
        print(f"  BUL[{anahtar}]: " + (str(hits[0]["box"]) if hits else "YOK"))
    return hits


def _bul_exact(ns, text, tag=""):
    """Tam metin eşleşmesi: 'Isıt' ile 'Isıtılıyor…' / 'ısıtır' karışmasın."""
    hits = [n for n in ns if (n["text"] or "").strip() == text]
    if tag:
        print(f"  BUL_EXACT[{text}]: " + (str(hits[0]["box"]) if hits else "YOK"))
    return hits


def faz2():
    """Mock uca geç + Isıt akışı: kapalı → Isıtılıyor… → Hazır ✓ + durum dönüşü."""
    print("== FAZ 2: Isıt akışı (mock uç) ==")
    xml = dump("f2-once")
    alan = _voice_alani(xml)
    if alan is None:
        print("HATA: voice_api alanı görünmüyor")
        return 1
    _alan_doldur(alan)
    # Durum satırı mock'a dönsün (tüm motorlar kapalı) + Isıt görünsün.
    time.sleep(6)
    xml = report("f2-kapali")
    anahtar(xml)
    shot("emulator-isit-once")
    ns = nodes(xml)
    isit = _bul_exact(ns, "Isıt", "Isıt (tam)")
    if not isit:
        print("HATA: 'Isıt' düğmesi görünmüyor")
        return 1
    print(">>> Isıt'a iki kez hızlıca basılıyor (çift tık koruması ölçümü)")
    tap_node(isit[0])
    time.sleep(0.4)
    tap_node(isit[0])
    time.sleep(1.5)
    xml = report("f2-isitiliyor")
    anahtar(xml)
    shot("emulator-isitiliyor")
    print(">>> ısıtma sürüyor (mock 25 sn 'soğuk') — durum dönüşünü bekle")
    for i in range(12):
        time.sleep(5)
        ns = nodes(dump(f"f2-bekleme{i}"))
        if _bul(ns, "Hazır ✓"):
            print(f"Hazır ✓ {i + 1}. kontrolde görüldü ({(i + 1) * 5} sn)")
            break
    xml = report("f2-hazir")
    anahtar(xml)
    shot("emulator-hazir")
    print(">>> canlı satır açık motoru göstermeli (Kahya: açık)")
    time.sleep(6)
    xml = report("f2-son")
    anahtar(xml)
    shot("emulator-hazir-2")
    return 0


def faz2b():
    """Kartı tam görünür yap ve 'Hazır ✓' işaretini belgele (ısıtma sonrası)."""
    print("== FAZ 2b: Hazır ✓ görünürlüğü ==")
    xml = dump("f2b-once")
    for i in range(3):
        if _bul_exact(nodes(xml), "Hazır ✓"):
            break
        scroll_up(1)
        time.sleep(1.0)
        xml = dump(f"f2b-scroll{i}")
    xml = report("f2b-hazir")
    anahtar(xml)
    shot("emulator-hazir-3")
    return 0


def faz3():
    """Motoru Kadın'a al (kapalı kalır), 'Şimdi dene' → kapalı motor ipucu."""
    print("== FAZ 3: Şimdi dene + kapalı motor ipucu ==")
    xml = dump("f3-once")
    ns = nodes(xml)
    kadin = _bul_exact(ns, "Kadın", "Kadın")
    if not kadin:
        print("HATA: Kadın seçeneği görünmüyor")
        return 1
    tap_node(kadin[0])
    time.sleep(2.5)
    ns = nodes(dump("f3-kadin"))
    dene = _bul_exact(ns, "Şimdi dene", "Şimdi dene")
    if not dene:
        scroll_up(1)
        time.sleep(1.0)
        ns = nodes(dump("f3-kadin-scroll"))
        dene = _bul_exact(ns, "Şimdi dene", "Şimdi dene")
    if not dene:
        print("HATA: 'Şimdi dene' görünmüyor")
        return 1
    tap_node(dene[0])
    time.sleep(2.0)
    xml = report("f3-deniyor")
    anahtar(xml)
    shot("emulator-simdiden-dene")
    time.sleep(4)
    xml = report("f3-sonuc")
    anahtar(xml)
    shot("emulator-kapali-ipucu")
    ns = nodes(xml)
    for key in ["Motor kapalı —", "Isıt", "Kadın: kapalı", "Metinleştirme"]:
        print(f"  KONTROL[{key}]: {'VAR' if _bul(ns, key) else 'YOK'}")
    return 0


def faz4():
    """Bölümden çıkınca canlı döngü DURUYOR mu? (mock günlüğü zaman damgaları)."""
    print("== FAZ 4: bölümden çıkışta yenileme durur mu ==")
    sh("shell", "input", "keyevent", "4")   # kategori sayfasından listeye dön
    time.sleep(2.0)
    xml = report("f4-cikis")
    t0 = time.time()
    print(f"çıkış anı (host): {time.strftime('%H:%M:%S')}")
    time.sleep(13)
    log = open(f"{OUT}/mock.log", encoding="utf-8").read().strip().splitlines()
    son = [l for l in log if "/health" in l and l[11:19] > time.strftime("%H:%M:%S", time.localtime(t0 - 4))]
    print(f"çıkıştan sonraki 13 sn içinde /health isteği: {len(son)} (0 beklenir)")
    for l in log[-4:]:
        print("   ", l[:100])
    shot("emulator-bolumden-cikis")
    return 0


def faz5():
    """Bölümden çıkışta canlı döngü duruyor mu — kart açıkken → kategori listesine dön."""
    print("== FAZ 5: canlı döngü kapanışı ==")
    sh("shell", "am", "force-stop", PKG)
    time.sleep(1)
    sh("shell", "am", "start", "-n", f"{PKG}/com.hermes.mobile.MainActivity")
    time.sleep(9)
    ns = nodes(dump("f5-ana"))
    a = find(ns, "Ayarlar")
    if a:
        tap_node(a[-1])
        time.sleep(2.5)
    ns = nodes(dump("f5-liste"))
    c = find(ns, "Canlı ses")
    if not c:
        print("HATA: kategori bulunamadı")
        return 1
    tap_node(c[0])
    time.sleep(2.5)
    for i in range(8):
        ns = nodes(dump(f"f5-scroll{i}"))
        if find(ns, "Ses ucu durumu"):
            break
        scroll_up(1)
    print("kart açık — 11 sn canlı yoklama bekleniyor")
    t_aktif = time.time()
    time.sleep(11)
    log = open(f"{OUT}/mock.log", encoding="utf-8").read().splitlines()
    aktif = [l for l in log if "/health" in l and l[11:19] >= time.strftime("%H:%M:%S", time.localtime(t_aktif))]
    print(f"KART AÇIKKEN 11 sn'de /health: {len(aktif)}")
    for l in aktif[:4]:
        print("   ", l[:60])
    # Kartı kapat: BACK → SettingsScreen'in BackHandler'ı kategori listesine döner.
    sh("shell", "input", "keyevent", "4")
    time.sleep(2.0)
    xml = report("f5-listeye-donus")
    if not find(nodes(xml), "Görünüm"):
        print("UYARI: kategori listesine dönülmemiş olabilir")
    t_cikis = time.time()
    print(f"çıkış anı: {time.strftime('%H:%M:%S', time.localtime(t_cikis))}")
    time.sleep(14)
    log = open(f"{OUT}/mock.log", encoding="utf-8").read().splitlines()
    kapali = [l for l in log if "/health" in l and l[11:19] >= time.strftime("%H:%M:%S", time.localtime(t_cikis + 2))]
    print(f"KART KAPALIYKEN 12 sn'de /health: {len(kapali)} (0 beklenir)")
    for l in kapali[:4]:
        print("   ", l[:60])
    return 0


def faz6():
    """Teslim edilen APK (yeniden kurulum sonrası) için duman testi: kart görünüyor mu."""
    print("== FAZ 6: teslim APK'sı duman testi ==")
    sh("shell", "logcat", "-c", "-b", "crash")
    sh("shell", "am", "force-stop", PKG)
    time.sleep(1)
    sh("shell", "am", "start", "-n", f"{PKG}/com.hermes.mobile.MainActivity")
    time.sleep(9)
    ns = nodes(dump("f6-ana"))
    a = find(ns, "Ayarlar")
    if a:
        tap_node(a[-1])
        time.sleep(2.5)
    ns = nodes(dump("f6-liste"))
    c = find(ns, "Canlı ses")
    if c:
        tap_node(c[0])
        time.sleep(2.5)
    for i in range(8):
        ns = nodes(dump(f"f6-scroll{i}"))
        if find(ns, "Ses ucu durumu"):
            break
        scroll_up(1)
    xml = report("f6-kart")
    anahtar(xml)
    shot("emulator-tur12-final")
    print("=== crash tamponu ===")
    print(sh("shell", "logcat", "-d", "-b", "crash").stdout.strip()[:400] or "(bos)")
    return 0


if __name__ == "__main__":
    f = sys.argv[1] if len(sys.argv) > 1 else "faz1"
    sys.exit({
        "faz1": faz1,
        "faz1b": faz1b,
        "faz2": faz2,
        "faz2b": faz2b,
        "faz3": faz3,
        "faz4": faz4,
        "faz5": faz5,
        "faz6": faz6,
    }[f]())
