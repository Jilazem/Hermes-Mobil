#!/usr/bin/env python3
# Tur22 kanıt sürücüsü v4 — D-02: her adım dump ile DOĞRULANARAK yürür.
# v3 düzeltmeleri:
#  - Compose'ta tıklanabilir View boş metinli; ET parent-map ile TextView'den
#    tıklanabilir ATAYA yürünür (v2/v3 'YOK: Sunucu ekle' burada takıldı).
#  - uiautomator dump animasyon sırasında boş dönebilir → 3× retry.
#  - Ekran video + ffmpeg 25fps kare çıkarımı (screencap ~300ms gecikmesi
#    900ms nabzı ve 280px'lik mikro animasyonu çözemiyor).
#  - İskelet nabzı: kare başına parlaklık ölçümü → tepe sayacı (D-10).
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = ["/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb",
       "-s", "emulator-5554"]
FF = "/Users/gokhanuzman/Library/Python/3.9/lib/python/site-packages/imageio_ffmpeg/binaries/ffmpeg-macos-aarch64-v7.1"
PKG = "com.hermes.mobile.v2"
OUT = "/Users/gokhanuzman/hermes-workspace/wt-t22/denetim/tur22/kanit"
VID = f"{OUT}/video-kare"
os.makedirs(VID, exist_ok=True)
os.makedirs(OUT, exist_ok=True)


def sh(args, binout=None, t=40):
    if binout:
        with open(binout, "wb") as fh:
            return subprocess.run(args, stdout=fh, timeout=t)
    return subprocess.run(args, capture_output=True, timeout=t).stdout.decode(errors="replace")


def dump(retry=3):
    for i in range(retry):
        sh(ADB + ["shell", "uiautomator", "dump", "/sdcard/ui.xml"])
        x = sh(ADB + ["shell", "cat", "/sdcard/ui.xml"])
        if "<hierarchy" in x:
            return ET.fromstring(x[x.index("<hierarchy"):])
        time.sleep(0.7)
    raise RuntimeError("uiautomator dump alınamadı")


def all_nodes():
    r = dump()
    elem_parent = {c: p for p in r.iter("node") for c in p}
    info = {}
    out = []
    for n in r.iter("node"):
        d = {"t": n.get("text") or "", "cd": n.get("content-desc") or "",
             "cls": n.get("class") or "", "b": n.get("bounds") or "",
             "clk": n.get("clickable") == "true", "_el": n}
        info[id(n)] = d
        out.append(d)
    # tıklanabilir ata yürüyüşü için element-id üzerinden bağla
    for n in r.iter("node"):
        p = elem_parent.get(n)
        info[id(n)]["_p"] = info.get(id(p)) if p is not None else None
    return out


def ctr(b):
    m = list(map(int, re.findall(r"-?\d+", b)))
    return (m[0] + m[2]) // 2, (m[1] + m[3]) // 2


def find_clickable_for(want):
    """İstenen metni/CD'yi BULAN, tıklanabilir kendisi ya da en yakın tıklanabilir atası."""
    for n in all_nodes():
        if (n["t"] == want or n["cd"] == want) and not n["t"].startswith("Sunucu bağlı değil"):
            node = n
            for _ in range(6):
                if node is None:
                    break
                if node["clk"]:
                    return node["b"]
                node = node["_p"]
    return None


def tap_text(want, timeout=10):
    end = time.time() + timeout
    while time.time() < end:
        b = find_clickable_for(want)
        if b:
            x, y = ctr(b)
            sh(ADB + ["shell", "input", "tap", str(x), str(y)])
            print(f"  tap '{want}' ({x},{y})")
            return True
        time.sleep(0.5)
    print(f"  YOK: {want}")
    return False


def wait_text(want, timeout=20, neg=False):
    end = time.time() + timeout
    while time.time() < end:
        found = any(want in n["t"] or want in n["cd"] for n in all_nodes())
        if neg and not found:
            return True
        if found and not neg:
            return True
        time.sleep(0.6)
    return False


def edit(i, text):
    eds = [n for n in all_nodes() if "EditText" in n["cls"]]
    if i >= len(eds):
        print(f"  EditText #{i} YOK")
        return False
    x, y = ctr(eds[i]["b"])
    sh(ADB + ["shell", "input", "tap", str(x), str(y)])
    time.sleep(0.4)
    sh(ADB + ["shell", "input", "keyevent", "KEYCODE_MOVE_END"])
    for _ in range(40):
        sh(ADB + ["shell", "input", "keyevent", "KEYCODE_DEL"])
    sh(ADB + ["shell", "input", "text", text.replace(" ", "%s")])
    print(f"  edit[{i}] = {text}")
    time.sleep(0.3)
    return True


def shot(n):
    sh(ADB + ["exec-out", "screencap", "-p"], binout=f"{OUT}/{n}")
    h = hashlib.sha256(open(f"{OUT}/{n}", "rb").read()).hexdigest()[:12]
    print(f"  📸 {n}  {h}")
    return h


def dump_symbols(tag):
    lines = []
    for n in all_nodes():
        if n["t"] or n["cd"] or n["clk"]:
            lines.append(f"{n['cls'].split('.')[-1]}{' clk' if n['clk'] else ''} | "
                         f"{n['t'][:70]!r} | cd={n['cd'][:50]!r} | {n['b']}")
    p = f"{OUT}/dogrulama-{tag}.txt"
    open(p, "w").write("\n".join(lines) + "\n")
    print(f"  ♿ sembol dökümü -> dogrulama-{tag}.txt ({len(lines)})")


def measure_tap(want):
    density = float(sh(ADB + ["shell", "wm", "density"]).split(":")[-1])
    b = find_clickable_for(want)
    if not b:
        print(f"  48dp: düğüm yok: {want}")
        return None
    m = list(map(int, re.findall(r"-?\d+", b)))
    hdp = (m[3] - m[1]) / (density / 160.0)
    wdp = (m[2] - m[0]) / (density / 160.0)
    print(f"  48dp '{want}': {wdp:.0f}x{hdp:.0f}dp  {'OK' if hdp >= 48 else '<48 !!!'}")
    return hdp


def relaunch():
    sh(ADB + ["shell", "am", "force-stop", PKG])
    time.sleep(1)
    sh(ADB + ["shell", "am", "start", "-n", f"{PKG}/com.hermes.mobile.MainActivity"])
    time.sleep(5)


def video(name, seconds, action):
    """Ekranı kaydet, aksiyonu ateşle, mp4 çek (screencap gecikmesi çözümü)."""
    dev = f"/sdcard/{name}.mp4"
    sh(ADB + ["shell", "rm", dev])
    p = subprocess.Popen(ADB + ["shell", "screenrecord", "--time-limit", str(seconds),
                                "--bit-rate", "8000000", dev],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(2.5)
    action()
    p.wait(timeout=seconds + 15)
    sh(ADB + ["pull", dev, f"{OUT}/{name}.mp4"], t=60)
    return f"{OUT}/{name}.mp4"


def extract_frames(mp4, prefix, fps=25, n=None):
    d = f"{VID}/{prefix}"
    os.makedirs(d, exist_ok=True)
    for f in os.listdir(d):
        os.remove(os.path.join(d, f))
    cmd = [FF, "-y", "-i", mp4]
    if fps != 25:
        cmd += ["-vf", f"fps={fps}"]
    cmd += [os.path.join(d, "f%04d.png")]
    subprocess.run(cmd, capture_output=True, timeout=120)
    frames = sorted(os.listdir(d))
    print(f"  🎞 {prefix}: {len(frames)} kare @{fps}fps")
    return [os.path.join(d, f) for f in frames]


def brightness_series(paths, box):
    from PIL import Image
    vals = []
    for p in paths:
        im = Image.open(p).convert("L").crop(box)
        px = list(im.getdata())
        vals.append(sum(px) / len(px))
    return vals


def pulse_report(vals, name):
    """Yerel maksimum sayacı + min/max — D-10 nabız kanıtı."""
    peaks = 0
    for i in range(1, len(vals) - 1):
        if vals[i] > vals[i-1] and vals[i] >= vals[i+1] and vals[i] > vals[i-1] + 0.2:
            peaks += 1
    print(f"  pulse[{name}]: n={len(vals)} min={min(vals):.1f} max={max(vals):.1f} "
          f"tepe={peaks}  → {'HAREKETLİ' if max(vals)-min(vals) > 2 else 'SABİT'}")
    json.dump(vals, open(f"{OUT}/nabiz-{name}.json", "w"))
    return peaks


MODE = sys.argv[1] if len(sys.argv) > 1 else "main"
DUR = f"{OUT}/durum.json"

if MODE == "main":
    print("== A) bağlantı-yok boş durumu + 48dp + sembol ==")
    sh(ADB + ["shell", "pm", "clear", PKG]); time.sleep(1)
    relaunch()
    shot("01-baglantiyok-bosdurum.png")
    dump_symbols("01-bosluk")
    measure_tap("Sunucu ekle")

    print("== B) form + kaydet (D-02: EditText=3 doğrulanır) ==")
    tap_text("Sunucu ekle")
    time.sleep(1.2)
    n_edit = sum(1 for n in all_nodes() if "EditText" in n["cls"])
    print(f"  EditText sayısı = {n_edit}")
    edit(0, "tur22-mock"); edit(1, "http://127.0.0.1:9151"); edit(2, "demo-token")
    shot("02-sunucu-formu.png")
    tap_text("Kaydet")
    ok = wait_text("Sunucu bağlı değil", neg=True, timeout=20)
    print("  bağlantı kuruldu mu:", ok)
    shot("03-baglanildi.png")
    dump_symbols("03-baglanti")

    print("== C) Çekmece + iskelet 60sn video (8fps≈480 kare, D-10) ==")
    # bağlantıdan hemen sonra pencere canlı olabilir; 60sn video 15sn pencereyi yakalar
    tap_text("Oturumlar")
    def fire_drawer():
        pass
    video("v02-iskelet-60sn", 60, fire_drawer)

    print("== D) Çekmece açık: arama boş sonucu + CTA + sembol ==")
    # arama alanı = drawer'daki EditText (placeholder 'Oturum ara')
    eds = [n for n in all_nodes() if "EditText" in n["cls"]]
    print("  drawer EditText:", len(eds))
    if eds:
        x, y = ctr(eds[0]["b"])
        sh(ADB + ["shell", "input", "tap", str(x), str(y)]); time.sleep(0.4)
        sh(ADB + ["shell", "input", "text", "zzz-yok-boyle"])
    time.sleep(1.2)
    shot("30-arama-bos-sonuc.png"); dump_symbols("30-arama-bos")
    measure_tap("Aramayı temizle")
    tap_text("Aramayı temizle"); time.sleep(0.8)

    print("== E) ARŞİV boş sekmesi (sol üst 3. sekme, spec 4) ==")
    # 'Arşiv' sekmesi — metni tıklanabilir atasıyla
    tap_text("Arşiv"); time.sleep(1.2)
    shot("31-arsiv-bos-durum.png"); dump_symbols("31-arsiv")
    # geri al
    b = find_clickable_for("Oturumlar")
    if b:
        x, y = ctr(b); sh(ADB + ["shell", "input", "tap", str(x), str(y)])
    time.sleep(1.5)

    print("== F) (60sn video C'de çekildi) ==")
    sh(ADB + ["shell", "input", "keyevent", "KEYCODE_BACK"])

    print("== G) 48dp gönder anı videosu (sohbette) ==")
    # sohbet sekmesine dön (alt bar Sohbet = index 0)
    # oturum aç: sohbet ekranına, mock mesaj 1.5sn
    def fire_send():
        eds = [n for n in all_nodes() if "EditText" in n["cls"]]
        if not eds:
            print("  composer yok"); return
        x, y = ctr(eds[-1]["b"])
        sh(ADB + ["shell", "input", "tap", str(x), str(y)]); time.sleep(0.4)
        sh(ADB + ["shell", "input", "text", "tur22-giris-testi"]); time.sleep(0.5)
        gb = find_clickable_for("Gönder")
        if gb:
            gx, gy = ctr(gb)
            print("  gönder basılıyor", (gx, gy))
            subprocess.Popen(ADB + ["shell", "input", "tap", str(gx), str(gy)],
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        else:
            print("  !Gönder YOK")
    # sohbet sekmesine geç: alt bar 'Sohbet'
    tap_text("Sohbet"); time.sleep(1.5)
    shot("44-oncesi.png")
    video("v03-gonder-anlari", 6, fire_send)
    time.sleep(2)
    shot("46-mesaj-dolu.png"); dump_symbols("46-mesaj-dolu")

    log = sh(ADB + ["shell", "logcat", "-d", "-b", "crash"])
    open(f"{OUT}/logcat-crash.txt", "w").write(log)
    hermes_fatal = [l for l in log.splitlines() if "hermes" in l.lower() and "FATAL" in l]
    print("CRASH (hermes+FATAL):", len(hermes_fatal))
    print("BİTTİ")

elif MODE == "frames":
    print("== kare analizi: 60sn iskelet videosu (8fps ~480 kare) ==")
    frames = extract_frames(f"{OUT}/v02-iskelet-60sn.mp4", "iskelet", fps=8)
    # iskelet satırı: sol 0..1100, ~70px satırlar, 60..400 arası 4 kart
    vals = brightness_series(frames, (60, 300, 1020, 780))
    pulse_report(vals, "iskelet")
    # benzersizlik
    hs = [hashlib.sha256(open(p, "rb").read()).hexdigest() for p in frames]
    print(f"  sha256: {len(set(hs))}/{len(hs)} farklı kare")

    for pref, box in [("cekmece", None), ("gonder", None)]:
        pass

    # çekmece açılış + gonder videolarından 100ms aralıklı seçme kareler
    for name, fps in [("v01-sohbet-gecis-ve-mesaj", 25), ("v03-gonder-anlari", 25)]:
        fs = extract_frames(f"{OUT}/{name}.mp4", name.replace("v0", "kare"), fps=fps)
        sel = fs[::3][:8]
        uniq = {hashlib.sha256(open(p, "rb").read()).hexdigest()[:12] for p in sel}
        print(f"  {name}: 8 seçme kare → {len(uniq)} benzersiz (benzersizlik) OK={len(uniq)>1}")
        b = brightness_series(sel, (60, 300, 1020, 780))
        print(f"   parlaklık: {[round(v) for v in b]}")
