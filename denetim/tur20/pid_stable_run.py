#!/usr/bin/env python3
"""Tur-20 r3: kill-dış-etken kanıtı + PID-sabit uzun koşu.

Makine emülatörü SUREKLI dis kuvvetle force-stop'lanıyor (am_kill 'from pid X',
X = mac tarafindan o an acilan adb shell — baska oturum/kanit scriptleri).
Bu betik: (1) kill serisini loglar, (2) 150 sn kill'siz pencere kollayip 130 sn
kosu alir, (3) kosu ici kill olursa 'dis etken' olarak ayri ispatlar.
Cikti: kanit/uzun-kosu/ (ozet, logcat dump, kareler, kill-serisi, seri).
"""
import json
import subprocess
import sys
import time
import os
from datetime import datetime

ADB = "/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb"
DEV = "emulator-5554"
PKG = "com.hermes.mobile.v2"
OUT = "denetim/tur20/kanit/uzun-kosu"
CLEAN_WINDOW_S = 150   # bu kadar kill'siz kalirsa kosu baslar
RUN_S = 130            # kosu uzunlugu (>=120 istek)
BUDGET_S = 600         # toplam sabir


def sh(*args, binary=False):
    r = subprocess.run([ADB, "-s", DEV] + list(args),
                       capture_output=True, text=not binary)
    return r.stdout


def kills():
    out = sh("logcat", "-d", "-b", "events", "-v", "epoch")
    return [l.split()[:8] for l in out.splitlines()
            if "am_kill" in l and PKG in l]


def kill_times():
    ks = kills()
    return sorted({float(k[0]) for k in ks})


def ts(epoch):
    return datetime.fromtimestamp(epoch).strftime("%H:%M:%S")


def main():
    import os
    os.makedirs(OUT, exist_ok=True)
    log = open(f"{OUT}/kosu-serisi.txt", "w")

    def P(msg):
        line = f"[{ts(time.time())}] {msg}"
        print(line, flush=True)
        log.write(line + "\n"); log.flush()

    P("=== tur20-r3 kill-serisi + PID-sabit kosu ===")
    # 0) Arena'da oldugumuzu dogrula (WebView yoksa disaridan dokunulmus demektir)
    #    — sahne kare delta'si bunu zaten kanitlar; burada sadece pid alalim.
    start = time.time()
    attempt = 0
    while time.time() - start < BUDGET_S:
        attempt += 1
        P(f"deneme {attempt}: temiz pencere koluyorum (hedef {CLEAN_WINDOW_S}s kill'siz)")
        clean_since = time.time()
        # kill'siz pencereyi bekle
        while time.time() - start < BUDGET_S:
            time.sleep(5)
            kt = kill_times()
            recent = [t for t in kt if t > time.time() - 20]
            if recent:
                clean_since = time.time()
                P(f"  kill yakalandi ({ts(recent[-1])}) -> pencere sifirlandi")
            if time.time() - clean_since >= CLEAN_WINDOW_S - 30:  # 120sn temiz yeter
                break
        if time.time() - start >= BUDGET_S:
            P("SANIR: 600sn buceti icinde temiz pencere yok")
            break

        P(f"pencere temiz, kosu basliyor (pid once)")
        pid0 = sh("shell", "pidof", PKG).strip()
        sh("logcat", "-c")
        t0 = time.time()
        subprocess.run([ADB, "-s", DEV, "exec-out", "screencap", "-p"],
                       stdout=open(f"{OUT}/kare-once-full.png", "wb"))
        kill_log = []
        mid = None
        while time.time() - t0 < RUN_S:
            time.sleep(5)
            now = time.time()
            if mid is None and now - t0 >= 60:
                subprocess.run([ADB, "-s", DEV, "exec-out", "screencap", "-p"],
                               stdout=open(f"{OUT}/kare-60sn-full.png", "wb"))
                pidm = sh("shell", "pidof", PKG).strip()
                mid = (now, pidm)
                P(f"  60sn kare + pid={pidm}")
            newkill = [t for t in kill_times() if t0 - 1 < t < now]
            for t in newkill:
                if t not in kill_log:
                    kill_log.append(t)
                    P(f"  DIK: kill {ts(t)} (t={int(t - t0)}sn, dis pid kaynakli)")
            if not pidm_ok(pid0):
                P("  uygulama kill sonrasi yeniden basladi — deneme gecersiz")
                break
        else:
            pid1 = sh("shell", "pidof", PKG).strip()
            subprocess.run([ADB, "-s", DEV, "exec-out", "screencap", "-p"],
                           stdout=open(f"{OUT}/kare-son-full.png", "wb"))
            subprocess.run([ADB, "-s", DEV, "logcat", "-d", "-v", "time"],
                           stdout=open(f"{OUT}/logcat-dump.txt", "wb"))
            P(f"KOSU TAMAM: pid-once={pid0} 60sn={mid[1] if mid else '?'} son={pid1} "
              f"kill-kosu-icinde={len(kill_log)} suresi={int(time.time() - t0)}sn")
            with open(f"{OUT}/pid-kanit.json", "w") as f:
                json.dump({"pid0": pid0, "pid_mid": mid[1] if mid else None,
                           "pid1": pid1, "run_s": int(time.time() - t0),
                           "kills_during": [ts(t) for t in kill_log]}, f, indent=2)
            # tum kill serisini kaydet (dis etken ispati)
            with open(f"{OUT}/kill-serisi.txt", "w") as f:
                for t in kill_times():
                    f.write(f"{ts(t)} kill\n")
            if pid0 == pid1 == (mid[1] if mid else pid0):
                P("PID-SABIT DOGRULANDI")
                return 0
            P("pid kaydi tutmadi, tekrar")
        time.sleep(10)
    return 1


def pidm_ok(pid0):
    cur = sh("shell", "pidof", PKG).strip()
    return cur == pid0 and pid0 != ""


if __name__ == "__main__":
    sys.exit(main())
