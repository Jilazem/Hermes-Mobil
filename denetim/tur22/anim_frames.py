#!/usr/bin/env python3
# Tur22 animasyon-kare kanıtı: tap'i ATEŞLE (non-blocking) ve 100ms arayla
# 4 kare yakala. Farklı sha256 = animasyon ilerliyor kanıtı (kopya kare YOK).
import hashlib
import subprocess
import time

ADB = ["/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb",
       "-s", "emulator-5554"]
OUT = "/Users/gokhanuzman/hermes-workspace/wt-t22/denetim/tur22/kanit"


def fire_tap(x, y):
    # non-blocking: tap süreci kendi hızında işler, anında döner
    subprocess.Popen(ADB + ["shell", "input", "tap", str(x), str(y)],
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def shot(name):
    p = f"{OUT}/{name}"
    subprocess.run(ADB + ["exec-out", "screencap", "-p"], stdout=open(p, "wb"), timeout=30)
    h = hashlib.sha256(open(p, "rb").read()).hexdigest()[:12]
    print(f"  📸 {name}  {h}")
    return h


def burst(prefix, tap_xy=None, n=4, gap=0.12, pre=0.0):
    hashes = []
    if tap_xy:
        fire_tap(*tap_xy)
    time.sleep(pre)
    for i in range(n):
        hashes.append(shot(f"{prefix}-{i+1}.png"))
        time.sleep(gap)
    uniq = len(set(hashes))
    print(f"== {prefix}: {uniq}/{n} farklı kare {'OK' if uniq > 1 else 'KOPYA!'}")
    return uniq


def sh(args):
    return subprocess.run(ADB + args, capture_output=True, timeout=30).stdout.decode(errors="replace")


print("== 1) Çekmece spring açılış — 4 ardışık kare ==")
burst("anim-cekmece-acilis", tap_xy=(105, 215), n=4, gap=0.15)

print("== 2) Çekmece KAPANIŞ kareleri ==")
burst("anim-cekmece-kapanis", tap_xy=(900, 700), n=2, gap=0.15)

print("== 3) İskelet → dolu (6s mock penceresi — 4 kare çekmece açıkken) ==")
sh(["shell", "am", "force-stop", "com.hermes.mobile.v2"])
time.sleep(1)
sh(["shell", "am", "start", "-n", "com.hermes.mobile.v2/com.hermes.mobile.MainActivity"])
time.sleep(5)
fire_tap(105, 215)               # çekmece anında aç
time.sleep(0.6)
burst("anim-iskelet", n=3, gap=0.4)   # polling 6sn penceresi
time.sleep(6.5)
shot("anim-dolu-liste.png")

print("BİTTİ")
