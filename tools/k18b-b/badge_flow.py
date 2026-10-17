#!/usr/bin/env python3
"""K18B-B emülatör kanıt akışı — sohbette bot kimliği rozeti (ad+emoji+model).

NEDEN betik: klavye açıldığında dialog düzeni kayıyor (D-02 sınıfı); sabit
koordinat bayatlıyor. roster_flow.py'deki (kendi işimiz) desen: her dokunuştan
ÖNCE taze uiautomator dump alınır, hedef metinden/sınırdan bulunur, merkezine
dokunulur. Her adım ekran görüntüsü + dump bırakır; stdout akışın kanıtıdır.

Senaryo:
  seed   — sahte gateway (mock_gateway.py, port 9199) + sunucu profili +
           G1 roster kartı fikstürü (run-as ile cihaz JSON'u — FİKSTÜR, UI
           akışı değil; kartın göründüğü Botlarım ekranı ayrıca kanıtlanır)
  roster — Botlarım: kart G1 BotRoster verisinde görünür
  devam  — "Bu botla devam et" → sohbet (aynı profil+model oturumu)
  sohbet — 2 tur mesaj: KULLANICI mesajı (rozetsiz) + bot cevabı (rozetli)
  model  — model değiştir → afiş güncellenir (ad kalır, model etiketi yenilenir)

Kullanım: python3 badge_flow.py <adim|hepsi>
ÖNCE: python3 tools/k18b-g1/mock_gateway.py 9199  (arka planda açık olmalı)
"""
import json
import re
import subprocess
import sys
import time
from pathlib import Path

ADB = ["adb", "-s", "emulator-5554"]
PKG = "com.hermes.mobile.v3"
OUT = Path(__file__).resolve().parents[2] / "denetim" / "k18b-b"
OUT.mkdir(parents=True, exist_ok=True)

BOT_AD = "Arastirmaci"
BOT_EMOJI = "🔍"  # 🔍
BOT_PROFIL = "arastirma"           # createSession(profile) argümanı = p.name
BOT_PROFIL_ETIKET = "Arastirma Ekibi"  # ekranda görünen display_name
BOT_MODEL = "deepseek-ai/DeepSeek-V4.1-Flash"
IKINCI_MODEL = "Hermes-4-405B"

ROSTER_JSON = json.dumps({
    "bots": [{
        "id": "k18b-b-bot-1",
        "name": BOT_AD,
        "emoji": BOT_EMOJI,
        "personality": "Merakli ve detayci bir arastirmaci",
        "profile": BOT_PROFIL,
        "provider": "nous",
        "model": BOT_MODEL,
        "pinned": False,
        "createdAt": 1792210000000,
        "updatedAt": 1792210000000,
    }]
}, ensure_ascii=False)


def sh(*args, timeout=90, stdin_text=None):
    r = subprocess.run(ADB + list(args), capture_output=True, text=True,
                       timeout=timeout, input=stdin_text)
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
        x1, y1, x2, y2 = (int(b.group(i)) for i in range(1, 5))
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


def set_field(nd, text, tag):
    tap(nd, tag)
    sh("shell", "input", "keyevent", "123")  # MOVE_END
    sh("shell", "input", "keyevent", *(["67"] * 80))  # DEL x80
    time.sleep(0.5)
    sh("shell", "input", "text", text.replace(" ", "%s"))
    time.sleep(1)


def run_as(cmd, stdin_text=None):
    """run-as ile uygulama veri dizininde komut.

    DİKKAT (D-02 sınıfı): adb shell argv'leri BOŞLUKLA birleştirip uzak
    kabuğa yollar — `sh -c` parçası ayrı argv geçilirse komut değil yalnız
    ilk sözcük `-c`'nin hedefi olur ve artı komutlar run-as DIŞINDA koşar.
    Bu yüzden tüm uzak komut TEK dizi olarak, tırnaklarıyla geçilir.
    """
    return sh("shell", f"run-as {PKG} sh -c '{cmd}'", stdin_text=stdin_text)


def step_seed():
    # 1) Sunucu profili: token fikstürü (run-as, debug APK) + Tur6SeedActivity.
    print("  token: " + run_as("mkdir -p files && printf mock-token > files/tur6_token.txt").strip())
    sh("shell", "am", "start", "-n", f"{PKG}/com.hermes.mobile.ui.Tur6SeedActivity",
       "--es", "url", "http://10.0.2.2:9199", "--es", "name", "K18BB",
       "--ez", "agent", "false", "--ez", "full", "true", "--ez", "readonly", "false")
    time.sleep(3)
    # 2) G1 roster kartı fikstürü — cihaz JSON'u (BotRosterStore tek dosyası).
    run_as("mkdir -p files/cache && cat > files/cache/bot-roster.json", stdin_text=ROSTER_JSON)
    time.sleep(1)
    back = run_as("cat files/cache/bot-roster.json")
    print(f"  roster json geri okundu: {back.strip()[:120]}")
    proof = run_as("cat files/hermes-setup-proof.json")
    print(f"  setup proof: {proof.strip()[:200]}")


def step_roster():
    """Ana ekran → Bildirimler → Ayarlar → Botlarım → kart görünür."""
    n = dump("r0")
    if not any("Botlarım" in x["text"] for x in n):
        if not any(x["text"] == "Ayarlar" or x["desc"] == "Ayarlar" for x in n):
            tap_text(n, "Bildirimler", "r0-bildirim")
            time.sleep(1.5)
            n = dump("r1")
        tap_text(n, "Ayarlar", "r1-ayarlar")
        time.sleep(1.5)
        n = dump("r2")
        tap_text(n, "Botlarım", "r2-botlarim")
        time.sleep(2)
    n = dump("r3")
    shot("01-botlarim-kart")
    kart = any(BOT_AD in x["text"] for x in n)
    print(f"  ROSTER KARTI GÖRÜNÜR: {kart}")
    return n


def step_devam(n=None):
    if n is None:
        n = dump("d0")
    tap_text(n, "Bu botla devam et", "d1-devam")
    time.sleep(3)
    n = dump("d2")
    shot("02-sohbet-acildi")
    prof = any(BOT_PROFIL_ETIKET in x["text"] for x in n)
    print(f"  SOHBETTE PROFİL ÇİPİ: {prof}")
    return n


def step_sohbet(mesaj, tag, n=None):
    """Mesaj yaz → gönder → cevap gelince ekran görüntüsü."""
    if n is None:
        n = dump(f"{tag}-giris")
    eds = [x for x in n if "EditText" in x["cls"]]
    if not eds:
        raise SystemExit(f"SOHBET GİRDİSİ YOK ({tag})")
    set_field(eds[-1], mesaj, f"{tag}-yaz")
    n = dump(f"{tag}-yazildi")
    # Gönder düğmesi: content-desc 'Gönder' ya da en sağ alttaki aksiyon ikonu.
    gonder = [x for x in n if "Gönder" in x["desc"] or "Send" in x["desc"]]
    if gonder:
        tap(gonder[0], f"{tag}-gonder")
    else:
        sh("shell", "input", "keyevent", "66")  # ENTER
        print("  (gönder düğmesi bulunamadı — ENTER ile gönderildi)")
    time.sleep(4)  # mock yanıt 0.3 sn'de gelir; akış tamamlansın
    n = dump(f"{tag}-cevap")
    shot(f"{tag}-cevap")
    return n


def step_model(n=None):
    """⋯ (Menü) → 'Model ve profiller' → ikinci model → afiş güncellenir.

    Gerçek yol (emülatörde 2026-10-17 ölçüldü): başlıktaki üç-nokta ikonunun
    content-desc'u 'Menü'; açılan listede 'Model ve profiller'; sheet'te
    'Şu an: <model>' + model satırları. Seçimden sonra rozetin model etiketi
    yenilenir, bot adı kalır (BotBadgeLogicTest'teki sözleşmenin canlı kanıtı).
    """
    if n is None:
        n = dump("m0")
    more = [x for x in n if "Menü" in x["desc"] or "More" in x["desc"]]
    if more:
        tap(more[0], "m0-more")
    time.sleep(2)
    n = dump("m1")
    shot("05-model-menu")
    tap_text(n, "Model ve profiller", "m1-modelprofiller")
    time.sleep(3)
    n = dump("m2")
    shot("06-model-secici")
    try:
        tap_text(n, IKINCI_MODEL, "m2-model-sec")
    except SystemExit:
        sh("shell", "input", "swipe", "540", "2100", "540", "1000", "500")
        time.sleep(1.5)
        n = dump("m2b")
        tap_text(n, IKINCI_MODEL, "m2b-model-sec")
    time.sleep(3)
    n = dump("m3")
    shot("07-afis-guncellendi")
    ok = any(IKINCI_MODEL in x["text"] for x in n)
    rozet = any(IKINCI_MODEL in x["desc"] for x in n)
    print(f"  AFİŞ YENİ MODELİ GÖSTERİYOR: {ok}  (semantik rozet: {rozet})")
    return n


def main():
    adim = sys.argv[1] if len(sys.argv) > 1 else "hepsi"
    n = None
    if adim in ("seed", "hepsi"):
        print("== ADIM: seed (fikstür)")
        step_seed()
    if adim in ("roster", "hepsi"):
        print("== ADIM: roster (Botlarım)")
        n = step_roster()
    if adim in ("devam", "hepsi"):
        print("== ADIM: devam (bu botla devam et)")
        n = step_devam(n)
    if adim in ("sohbet", "hepsi"):
        print("== ADIM: sohbet (bot'lu + botsuz mesaj)")
        n = step_sohbet("Merhaba! Bana kisa bir cumle yaz.", "03-tur1", n)
        n = step_sohbet("Tesekkurler, bu yeterli.", "04-tur2", n)
    if adim in ("model", "hepsi"):
        print("== ADIM: model degisimi (afis guncellenir)")
        step_model(n)
    print("BİTTİ")


if __name__ == "__main__":
    main()