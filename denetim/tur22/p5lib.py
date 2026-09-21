# Tur22 kanıt helper'ı (proof_v5'ten türedi) — import p5lib olarak kullanılır.
import hashlib
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = ["/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb",
       "-s", "emulator-5554"]
FFMPEG = "/Users/gokhanuzman/Library/Python/3.9/lib/python/site-packages/imageio_ffmpeg/binaries/ffmpeg-macos-aarch64-v7.1"
PKG = "com.hermes.mobile.v2"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "kanit")


def sh(args, timeout=60, **kw):
    return subprocess.run(args, capture_output=True, text=True, timeout=timeout, **kw).stdout


def dump():
    last = ""
    for attempt in range(3):
        sh(ADB + ["shell", "uiautomator", "dump", "/sdcard/ui.xml"])
        xml = sh(ADB + ["shell", "cat", "/sdcard/ui.xml"])
        if "<hierarchy" in xml:
            return ET.fromstring(xml[xml.index("<hierarchy"):])
        last = xml[:200]
        time.sleep(0.8)
    raise RuntimeError("dump alınamadı: " + last)


def nodes():
    r = dump()
    return [{"t": n.get("text") or "", "cd": n.get("content-desc") or "",
             "cls": n.get("class") or "", "b": n.get("bounds") or "",
             "clk": n.get("clickable") == "true"} for n in r.iter("node")]


def clickable_parent_of(pred):
    r = dump()
    par = {c: p for p in r.iter("node") for c in p}
    for n in r.iter("node"):
        if not (n.get("text") or n.get("content-desc")):
            continue
        if not (pred({"t": n.get("text") or "", "cd": n.get("content-desc") or ""})):
            continue
        cur = n
        for _ in range(5):
            if cur.get("clickable") == "true":
                return cur.get("bounds") or ""
            cur = par.get(cur)
            if cur is None:
                break
    return None


def ctr(bounds):
    m = list(map(int, re.findall(r"-?\d+", bounds)))
    return (m[0] + m[2]) // 2, (m[1] + m[3]) // 2


def tap(want, settle=1.0):
    b = clickable_parent_of(lambda n: n["t"] == want or n["cd"] == want)
    if not b:
        print("!YOK:", repr(want))
        return False
    x, y = ctr(b)
    sh(ADB + ["shell", "input", "tap", str(x), str(y)])
    time.sleep(settle)
    return True


def edit(idx, value):
    eds = [n for n in nodes() if "EditText" in n["cls"]]
    if idx >= len(eds):
        print("edit YOK", idx)
        return False
    x, y = ctr(eds[idx]["b"])
    sh(ADB + ["shell", "input", "tap", str(x), str(y)])
    time.sleep(0.5)
    for i in range(40):
        sh(ADB + ["shell", "input", "keyevent", "KEYCODE_DEL"])
    sh(ADB + ["shell", "input", "text", value.replace(" ", "%s")])
    time.sleep(0.4)
    sh(ADB + ["shell", "input", "keyevent", "4"])
    time.sleep(0.4)
    return True


def shot(name):
    p = f"{OUT}/{name}"
    subprocess.run(ADB + ["exec-out", "screencap", "-p"], stdout=open(p, "wb"), timeout=60)
    h = hashlib.sha256(open(p, "rb").read()).hexdigest()[:12]
    print(f"📸 {name} {h}")
    return p


def dump_syms(tag):
    lines = [f"{n['cls'].split('.')[-1]}{' clk' if n['clk'] else ''} | "
             f"{n['t'][:60]!r} | cd={n['cd'][:24]!r} | {n['b']}" for n in nodes() if n["t"] or n["cd"]]
    fn = f"{OUT}/dogrulama-{tag}.txt"
    with open(fn, "w") as f:
        f.write("\n".join(lines) + "\n")
    print(f"♿ dogrulama-{tag}.txt ({len(lines)})")


def pull(dev, dest):
    sh(ADB + ["pull", dev, dest], timeout=240)


def frames(video_src, outdir, fps=8):
    os.makedirs(outdir, exist_ok=True)
    for f in os.listdir(outdir):
        os.remove(os.path.join(outdir, f))
    subprocess.run([FFMPEG, "-hide_banner", "-loglevel", "error", "-i", video_src,
                    "-vf", f"fps={fps}", os.path.join(outdir, "f%04d.png")], check=True)
    n = len([f for f in os.listdir(outdir) if f.endswith(".png")])
    print(f"kare: {n}")
    return n


def pulse_trace(outdir, region, a, b, fps=8):
    from PIL import Image
    import statistics
    fr = sorted(os.listdir(outdir))
    vals = []
    for f in fr[a:b]:
        im = Image.open(os.path.join(outdir, f)).convert("L")
        vals.append(round(statistics.fmean(list(im.crop(region).getdata())), 2))
    return vals
