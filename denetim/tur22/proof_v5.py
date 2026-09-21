#!/usr/bin/env python3
# Tur22 v5 hedefli-adım sürücüsü — her çağrı TEK adım, dump doğrulamalı (D-02).
import hashlib, os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

ADB = ["/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb", "-s", "emulator-5554"]
FF = "/Users/gokhanuzman/Library/Python/3.9/lib/python/site-packages/imageio_ffmpeg/binaries/ffmpeg-macos-aarch64-v7.1"
PKG = "com.hermes.mobile.v2"
OUT = "/Users/gokhanuzman/hermes-workspace/wt-t22/denetim/tur22/kanit"
VID = f"{OUT}/video-kare"; os.makedirs(VID, exist_ok=True)


def sh(args, binout=None, t=40):
    if binout:
        with open(binout, "wb") as fh:
            return subprocess.run(args, stdout=fh, timeout=t)
    return subprocess.run(args, capture_output=True, timeout=t).stdout.decode(errors="replace")


def dump(retry=3):
    for _ in range(retry):
        sh(ADB + ["shell", "uiautomator", "dump", "/sdcard/ui.xml"])
        x = sh(ADB + ["shell", "cat", "/sdcard/ui.xml"])
        if "<hierarchy" in x:
            return ET.fromstring(x[x.index("<hierarchy"):])
        time.sleep(0.7)
    raise RuntimeError("dump alınamadı")


def nodes():
    r = dump()
    ep = {c: p for p in r.iter("node") for c in p}
    info = {}
    out = []
    for n in r.iter("node"):
        d = {"t": n.get("text") or "", "cd": n.get("content-desc") or "",
             "cls": n.get("class") or "", "b": n.get("bounds") or "",
             "clk": n.get("clickable") == "true", "_el": n}
        info[id(n)] = d; out.append(d)
    for n in r.iter("node"):
        p = ep.get(n)
        info[id(n)]["_p"] = info.get(id(p)) if p is not None else None
    return out


def ctr(b):
    m = list(map(int, re.findall(r"-?\d+", b)))
    return (m[0] + m[2]) // 2, (m[1] + m[3]) // 2


def clickable_parent_of(pred):
    for n in nodes():
        if pred(n):
            node = n
            for _ in range(8):
                if node is None: break
                if node["clk"]:
                    return node["b"]
                node = node["_p"]
    return None


def hit(want):
    # birebir eşleşme: uzun mesaj metinleri (içeren) CTA sayılmaz
    return clickable_parent_of(lambda n: n["t"] == want or n["cd"] == want)


def tap(want, settle=1.0):
    b = hit(want)
    if not b:
        print(f"YOK: {want}"); sys.exit(1)
    x, y = ctr(b)
    sh(ADB + ["shell", "input", "tap", str(x), str(y)])
    print(f"tap '{want}' ({x},{y})")
    time.sleep(settle)


def edit(i, text):
    eds = [n for n in nodes() if "EditText" in n["cls"]]
    if i >= len(eds):
        print("EditText YOK"); sys.exit(1)
    x, y = ctr(eds[i]["b"])
    sh(ADB + ["shell", "input", "tap", str(x), str(y)]); time.sleep(0.4)
    sh(ADB + ["shell", "input", "keyevent", "KEYCODE_MOVE_END"])
    for _ in range(60):
        sh(ADB + ["shell", "input", "keyevent", "KEYCODE_DEL"])
    sh(ADB + ["shell", "input", "text", text.replace(" ", "%s")])
    print(f"edit[{i}] ok")


def state():
    for n in nodes():
        if n["t"] or n["cd"]:
            print(f"{n['cls'].split('.')[-1]}{' clk' if n['clk'] else ''} {n['t'][:60]!r} cd={n['cd'][:40]!r} {n['b']}")


def shot(n):
    sh(ADB + ["exec-out", "screencap", "-p"], binout=f"{OUT}/{n}")
    print("📸", n, hashlib.md5(open(f"{OUT}/{n}", "rb").read()).hexdigest()[:12])


def dump_syms(tag):
    lines = [f"{n['cls'].split('.')[-1]}{' clk' if n['clk'] else ''} | {n['t'][:70]!r} | cd={n['cd'][:50]!r} | {n['b']}"
             for n in nodes() if n["t"] or n["cd"] or n["clk"]]
    open(f"{OUT}/dogrulama-{tag}.txt", "w").write("\n".join(lines) + "\n")
    print(f"♿ dogrulama-{tag}.txt ({len(lines)})")


def dp48(want):
    b = hit(want)
    d = float(sh(ADB + ["shell", "wm", "density"]).split(":")[-1])
    m = list(map(int, re.findall(r"-?\d+", b)))
    print(f"48dp '{want}': {(m[2]-m[0])/ (d/160):.0f}x{(m[3]-m[1])/(d/160):.0f}dp")


cmd = sys.argv[1]

if cmd == "state":
    state()

elif cmd == "clear-relaunch":
    sh(ADB + ["shell", "pm", "clear", PKG]); time.sleep(1)
    sh(ADB + ["shell", "am", "start", "-n", f"{PKG}/com.hermes.mobile.MainActivity"])
    time.sleep(6)
    shot("01-baglantiyok-bosdurum.png"); dump_syms("01-bosluk"); dp48("Sunucu ekle")

elif cmd == "add-server":
    tap("Sunucu ekle", 1.5)
    n = sum(1 for x in nodes() if "EditText" in x["cls"])
    print(f"form EditText = {n} (beklenen >=3)")
    edit(0, "tur22-mock"); edit(1, "http://127.0.0.1:9151"); edit(2, "demo-token")
    shot("02-sunucu-formu.png")
    tap("Kaydet", 3.0)
    txts = "|".join(x["t"] for x in nodes())
    print("durum:", "hâlâ 'Sunucu bağlı değil' YOK" if "Sunucu bağlı değil" not in txts else "bağlantı YOK")
    shot("03-baglanildi.png"); dump_syms("03-baglanti")

elif cmd == "video-iskelet":
    # 60 sn video; 10. sn'de ☰ (tıklanabilir, cd='Oturumlar' metinli) açılır
    dev = "/sdcard/v02.mp4"; sh(ADB + ["shell", "rm", dev])
    p = subprocess.Popen(ADB + ["shell", "screenrecord", "--time-limit", "60",
                                "--bit-rate", "8000000", dev],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(2)
    # ☰ = üst bar ilk tıklanabilir (43..169)
    b = clickable_parent_of(lambda n: n["cd"] == "Oturumlar")
    x, y = ctr(b)
    subprocess.Popen(ADB + ["shell", "input", "tap", str(x), str(y)],
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print("☰ ateşlendi", (x, y))
    p.wait(timeout=90)
    sh(ADB + ["pull", dev, f"{OUT}/v02-iskelet-60sn.mp4"], t=60)
    print("video çekildi")

elif cmd == "frames":
    d = f"{VID}/iskelet"; os.makedirs(d, exist_ok=True)
    for f in os.listdir(d): os.remove(os.path.join(d, f))
    subprocess.run([FF, "-y", "-i", f"{OUT}/v02-iskelet-60sn.mp4", "-vf", "fps=8",
                    os.path.join(d, "f%04d.png")], capture_output=True, timeout=180)
    frames = sorted(os.path.join(d, f) for f in os.listdir(d))
    print(f"kare: {len(frames)}")
    from PIL import Image
    vals = []
    for p in frames:
        im = Image.open(p).convert("L").crop((60, 280, 1020, 800))
        vals.append(sum(im.getdata()) / (im.size[0] * im.size[1]))
    peaks = sum(1 for i in range(1, len(vals)-1) if vals[i] > vals[i-1] and vals[i] >= vals[i+1] and vals[i] - vals[i-1] > 0.2)
    print(f"nabız: n={len(vals)} min={min(vals):.1f} max={max(vals):.1f} tepe={peaks} hareketli={'EVET' if max(vals)-min(vals)>2 else 'HAYIR'}")
    import json; json.dump(vals, open(f"{OUT}/nabiz-iskelet.json", "w"))

elif cmd == "drawer-empty":
    # arama boş sonucu
    eds = [n for n in nodes() if "EditText" in n["cls"]]
    print("EditText (arama) sayisi:", len(eds))
    if not eds:
        print("cekmece kapalimi? state ver"); sys.exit(1)
    x, y = ctr(eds[0]["b"])
    sh(ADB + ["shell", "input", "tap", str(x), str(y)]); time.sleep(0.5)
    sh(ADB + ["shell", "input", "text", "zzz-yok-boyle"]); time.sleep(1.2)
    shot("30-arama-bos-sonuc.png"); dump_syms("30-arama-bos"); dp48("Aramayı temizle")

elif cmd == "clear-search":
    tap("Aramayı temizle", 1.0)

elif cmd == "archive-tab":
    # 3. sekme = 'Arşiv'
    b = None
    for n in nodes():
        if n["t"] == "Arşiv" or n["cd"] == "Arşiv":
            node = n
            for _ in range(8):
                if node is None: break
                if node["clk"]:
                    b = node["b"]; break
                node = node["_p"]
        if n["cd"] == "Arşiv":
            node = n
            for _ in range(8):
                if node is None: break
                if node["clk"]:
                    b = node["b"]; break
                node = node["_p"]
    if not b:
        print("Arşiv sekmesi YOK"); sys.exit(1)
    x, y = ctr(b)
    sh(ADB + ["shell", "input", "tap", str(x), str(y)]); time.sleep(1.2)
    shot("31-arsiv-bos-durum.png"); dump_syms("31-arsiv")

elif cmd == "video-gonder":
    # sohbet ekranında taslak yaz, video çek, gönder'e bas
    dev = "/sdcard/v03.mp4"; sh(ADB + ["shell", "rm", dev])
    # taslak yaz (composer = son EditText, boş olan)
    eds = [n for n in nodes() if "EditText" in n["cls"] and not n["t"]]
    if not eds:
        print("composer EditText bulunamadı"); sys.exit(1)
    x, y = ctr(eds[-1]["b"])
    sh(ADB + ["shell", "input", "tap", str(x), str(y)]); time.sleep(0.4)
    sh(ADB + ["shell", "input", "text", "tur22-giris-testi"]); time.sleep(0.6)
    shot("44-oncesi.png")
    gb = clickable_parent_of(lambda n: n["cd"] == "Gönder")
    print("Gönder bounds:", gb)
    p = subprocess.Popen(ADB + ["shell", "screenrecord", "--time-limit", "6",
                                "--bit-rate", "8000000", dev],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(2)
    gx, gy = ctr(gb)
    subprocess.Popen(ADB + ["shell", "input", "tap", str(gx), str(gy)],
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print("Gönder basıldı", (gx, gy))
    p.wait(timeout=40)
    sh(ADB + ["pull", dev, f"{OUT}/v03-gonder-anlari.mp4"], t=60)
    time.sleep(1)
    shot("46-mesaj-dolu.png"); dump_syms("46-mesaj-dolu")

elif cmd == "frames-gonder":
    d = f"{VID}/gonder"; os.makedirs(d, exist_ok=True)
    for f in os.listdir(d): os.remove(os.path.join(d, f))
    subprocess.run([FF, "-y", "-i", f"{OUT}/v03-gonder-anlari.mp4",
                    os.path.join(d, "f%04d.png")], capture_output=True, timeout=180)
    frames = sorted(os.path.join(d, f) for f in os.listdir(d))
    # 100ms aralık = her 3. kare (30fps)
    sel = frames[::3]
    hs = [hashlib.sha256(open(p, "rb").read()).hexdigest()[:12] for p in sel]
    print(f"gonder kareleri: 100ms aralik {len(sel)} kare, {len(set(hs))} benzersiz")
    # burst'ın ilk 6 karesi (basış anı)
    # video 2sn sonra basıldı; basış = ~60. kare
    base = frames[60:84]
    hs2 = [hashlib.sha256(open(p, "rb").read()).hexdigest()[:12] for p in base]
    print(f"basış penceresi 24 kare: {len(set(hs2))} benzersiz")
    print("hash ornek:", hs2[:8])

else:
    print("bilinmeyen komut:", cmd)
