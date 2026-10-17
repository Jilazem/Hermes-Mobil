#!/usr/bin/env python3
"""K18B-G1 emülatör akış kanıtı — bot oluştur / çoğalt / sabitle / devam et.

NEDEN betik: klavye her açıldığında dialog düzeni kayıyor (D-02 sınıfı);
sabit koordinat bayatlıyor. Bu betik her dokunuştan ÖNCE taze uiautomator
dump alır, hedefi metinden/sınırdan bulur, merkezine dokunur. Her adım
ekran görüntüsü + dump bırakır; stdout akışın kanıtıdır.

Kullanım: python3 roster_flow.py <adim>   (adimlar: form, kaydet-sonrasi,
devam, kalici)  — ya da 'hepsi' tek koşuda.
"""
import re
import subprocess
import sys
import time
from pathlib import Path

ADB = ["adb", "-s", "emulator-5554"]
OUT = Path("/Volumes/EX/80-YEREL/hermes-workspace/wt-k18b-g1/denetim/k18b-g1")
OUT.mkdir(parents=True, exist_ok=True)

BOT_AD = "Arastirmaci"
BOT_KISILIK = "Merakli%sve%sdetayci%sbir%sarastirmaci"  # adb %s = boşluk
BOT_KISILIK_OK = "Merakli ve detayci bir arastirmaci"
PROFIL_ETIKET = "Arastirma Ekibi"
MODEL_ETIKET = "DeepSeek-V4.1"


def sh(*args, timeout=90):
    r = subprocess.run(ADB + list(args), capture_output=True, text=True, timeout=timeout)
    return r.stdout


def dump(tag):
    raw = sh("exec-out", "uiautomator", "dump", "/dev/tty")
    m = re.search(r"<\?xml.*", raw, re.S)
    xml = m.group(0) if m else ""
    (OUT / f"ui-{tag}.xml").write_text(xml, encoding="utf-8")
    nodes = []
    for mm in re.finditer(r"<node[^>]*>", xml):
        n = mm.group(0)
        g = lambda p: (re.search(p, n).group(1) if re.search(p, n) else "")
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if not b:
            continue
        x1, y1, x2, y2 = (int(b.group(i)) for i in (1, 2, 3, 4))
        nodes.append({
            "cls": g(r'class="([^"]*)"'), "text": g(r'text="([^"]*)"'),
            "desc": g(r'content-desc="([^"]*)"'),
            "cx": (x1 + x2) // 2, "cy": (y1 + y2) // 2,
            "bounds": (x1, y1, x2, y2),
        })
    return nodes


def shot(tag):
    p = OUT / f"em-{tag}.png"
    p.write_bytes(subprocess.run(ADB + ["exec-out", "screencap", "-p"],
                                 capture_output=True, timeout=90).stdout)
    print(f"  shot: {p}")


def tap(nd, tag):
    print(f"  tap [{nd['cx']},{nd['cy']}] {nd['text'][:40]!r} {nd['desc'][:30]!r}")
    sh("shell", "input", "tap", str(nd["cx"]), str(nd["cy"]))
    time.sleep(1.5)


def tap_text(nodes, needle, tag, cls=None, exact=False):
    for nd in nodes:
        hay = nd["text"] + " " + nd["desc"]
        hit = (nd["text"] == needle or nd["desc"] == needle) if exact else (needle in hay)
        if hit and (cls is None or cls in nd["cls"]):
            tap(nd, tag)
            return nd
    raise SystemExit(f"HEDEF YOK: {needle!r} ({tag})")


def clear_field(nd, tag):
    """Alanı seç-kur: tıkla (imleç orta), sona git, sil."""
    tap(nd, tag)
    sh("shell", "input", "keyevent", "123")  # MOVE_END
    sh("shell", "input", "keyevent", *(["67"] * 60))  # DEL x60
    time.sleep(0.5)


def set_field(nd, text, tag):
    clear_field(nd, tag)
    sh("shell", "input", "text", text)
    time.sleep(1)


def nav_roster():
    """Ana ekran → Bildirimler → Ayarlar → Botlarım (her adımda taze dump)."""
    n = dump("n0")
    if not any("Botlarım" in x["text"] for x in n):
        if any(x["text"] == "Ayarlar" or x["desc"] == "Ayarlar" for x in n):
            tap_text(n, "Ayarlar", "n0-ayarlar")
            time.sleep(1.5)
            n = dump("n1")
        if not any("Botlarım" in x["text"] for x in n):
            # Ana sekme çubuğundan Bildirimler > Ayarlar.
            if any(x["text"] == "Bildirimler" or x["desc"] == "Bildirimler" for x in n):
                tap_text(n, "Bildirimler", "n1-bildirim")
                time.sleep(1.5)
                n = dump("n2")
            tap_text(n, "Ayarlar", "n2-ayarlar")
            time.sleep(1.5)
            n = dump("n3")
        tap_text(n, "Botlarım", "n3-botlarim")
        time.sleep(2)
    return dump("n4")


def step_form():
    """Botlarım > Yeni bot > formu doldur (her adımda taze dump)."""
    n = nav_roster()
    tap_text(n, "Yeni bot", "f1-yeni")  # sağ üst '+ Yeni bot'
    n = dump("f2")
    eds = [x for x in n if "EditText" in x["cls"]]
    print(f"  EditText sayısı: {len(eds)}")
    set_field(eds[0], BOT_AD, "f2-ad")
    n = dump("f3")
    eds = [x for x in n if "EditText" in x["cls"]]
    set_field(eds[2], BOT_KISILIK, "f3-kisilik")
    n = dump("f4")
    shot("03-form-dolu")
    # Profil açılır menüsü — 'Hermes profili' alanının EditText/Spinner satırı.
    prof = [x for x in n if "Spinner" in x["cls"]]
    tap(prof[0] if prof else tap_text(n, "Yönlendirici", "f4-profil"), "f4-profil")
    n = dump("f5")
    tap_text(n, PROFIL_ETIKET, "f5-profil-sec")
    n = dump("f6")
    shot("04-profil-secildi")
    step_model(n)
    n = dump("f8")
    shot("06-kayit-oncesi")
    return n


def step_model(n=None):
    """Model seç → ModelPickerSheet → DeepSeek satırı. 'Seç' TAM EŞLEŞME
    ister — 'Seçilmedi' metni de alt-dizgi olarak yakalar (D-02 dersi)."""
    if n is None:
        n = dump("m1")
    tap_text(n, "Seç", "f6-model-ac", exact=True)
    time.sleep(3)
    n = dump("f7")
    shot("05-model-secici")
    try:
        tap_text(n, MODEL_ETIKET, "f7-model-sec")
    except SystemExit:
        # Liste görünmüyorsa biraz kaydırıp tekrar dene.
        sh("shell", "input", "swipe", "540", "2100", "540", "1000", "500")
        time.sleep(1.5)
        n = dump("f7b")
        tap_text(n, MODEL_ETIKET, "f7b-model-sec")
    time.sleep(1)
    n = dump("m2")
    secili = any("DeepSeek" in x["text"] for x in n)
    print(f"  MODEL SEÇİLDİ AFİŞİ: {secili}")
    return n


def step_save():
    n = dump("s1")
    tap_text(n, "Kaydet", "s1-kaydet")
    time.sleep(2)
    n = dump("s2")
    shot("07-kart-listesi")
    ok = any(BOT_AD in x["text"] for x in n)
    print(f"  KART LİSTEDE: {ok}")
    return n


def step_pin_dup(n=None):
    # Çoğalt → ikinci kart; sonra sabitle.
    if n is None:
        n = dump("p0")
    tap_text(n, "Çoğalt", "p1-cogalt")
    time.sleep(1.5)
    n = dump("p2")
    shot("08-cogaltildi")
    kopya = any("(kopya)" in x["text"] for x in n)
    print(f"  KOPYA VAR: {kopya}")
    tap_text(n, "Sabitle", "p3-sabitle")
    time.sleep(1.5)
    n = dump("p4")
    shot("09-sabitlendi")
    return n


def step_continue(n=None):
    if n is None:
        n = dump("c0")
    tap_text(n, "Bu botla devam et", "c1-devam")
    time.sleep(3)
    n = dump("c2")
    shot("10-devam-sohbet")
    prof = any(PROFIL_ETIKET in x["text"] for x in n)
    model = any("DeepSeek" in x["text"] for x in n)
    print(f"  SOHBETTE PROFİL ÇİPİ: {prof}  MODEL AFİŞİ: {model}")
    for x in n:
        if x["text"]:
            print(f"    [{x['cx']},{x['cy']}] {x['text'][:50]!r}")
    return n


def step_persist():
    sh("shell", "am", "force-stop", "com.hermes.mobile.v3")
    time.sleep(2)
    sh("shell", "am", "start", "-n", "com.hermes.mobile.v3/com.hermes.mobile.MainActivity")
    time.sleep(5)
    n = dump("k1")
    tap_text(n, "Bildirimler", "k1-tab") if any(x["desc"] == "Bildirimler" or x["text"] == "Bildirimler" for x in n) else None
    time.sleep(1.5)
    n = dump("k2")
    tap_text(n, "Ayarlar", "k2-ayarlar")
    time.sleep(1.5)
    n = dump("k3")
    tap_text(n, "Botlarım", "k3-botlarim")
    time.sleep(2)
    n = dump("k4")
    shot("11-kalici-liste")
    kartlar = [x["text"] for x in n if BOT_AD in x["text"] or "(kopya)" in x["text"]]
    print(f"  KALICI KARTLAR: {kartlar}")
    return n


def main():
    adim = sys.argv[1] if len(sys.argv) > 1 else "hepsi"
    n = None
    if adim in ("form", "hepsi"):
        print("== ADIM: form")
        n = step_form()
    if adim == "model":
        print("== ADIM: model seç")
        n = step_model()
    if adim in ("kaydet", "hepsi"):
        print("== ADIM: kaydet")
        n = step_save()
    if adim in ("pin", "hepsi"):
        print("== ADIM: çoğalt + sabitle")
        n = step_pin_dup(n)
    if adim in ("devam", "hepsi"):
        print("== ADIM: devam et")
        n = step_continue(n)
    if adim in ("kalici", "hepsi"):
        print("== ADIM: kalıcılık (yeniden başlat)")
        step_persist()
    print("BİTTİ")


if __name__ == "__main__":
    main()