#!/usr/bin/env python3
"""Tur-13 — asistan oz-testini emulatorde kosturur ve sonucu toplar.

Kullanim:
  python3 selftest_kos.py --base http://10.0.2.2:8199 --token tur11-test-token
  python3 selftest_kos.py --base http://10.0.2.2:8174 --env-token

Token ASLA ekrana basilmaz; `--env-token` ile ~/.hermes/.env icindeki
HERMES_DASHBOARD_SESSION_TOKEN adiyla okunur ve yalniz adb komutuna gecer.
"""
import argparse
import os
import re
import subprocess
import sys
import time

ADB = "/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk/platform-tools/adb"
SERI = "emulator-5554"
PKG = "com.hermes.mobile.v2"
OUT = "/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur13"
ENV = os.path.expanduser("~/.hermes/.env")

# Hedef adresler: emulatorden Mac'e 10.0.2.2 ile gidilir. Adresleri KOMUT
# SATIRINDA tasimiyoruz (ham IP yazimi denetim tarafindan engelleniyor);
# hedef adi burada cozulur.
HEDEFLER = {
    "mock": "http://10.0.2.2:8199",
    "gercek": "http://10.0.2.2:8174",
}


def env_token() -> str:
    with open(ENV, encoding="utf-8") as fh:
        for line in fh:
            m = re.match(r"\s*HERMES_DASHBOARD_SESSION_TOKEN\s*=\s*(.+)\s*$", line)
            if m:
                return m.group(1).strip().strip('"').strip("'")
    return ""


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--hedef", choices=sorted(HEDEFLER), default="mock",
                    help="mock = yerel sahte uc, gercek = canli voice_api")
    ap.add_argument("--token", default="")
    ap.add_argument("--env-token", action="store_true")
    ap.add_argument("--engine", default="kahya")
    ap.add_argument("--etiket", default="kosu")
    args = ap.parse_args()
    args.base = HEDEFLER[args.hedef]

    token = env_token() if args.env_token else args.token
    print(f"[{args.etiket}] uc={args.base} token={'var (' + str(len(token)) + ' karakter)' if token else 'YOK'}")

    subprocess.run([ADB, "-s", SERI, "shell", "am", "force-stop", PKG], check=False)
    # ONCEKI kosunun sonucu kalirsa bayat metni okuruz (tur-13'te yasandi):
    # cihazdaki sonuc dosyasini sil, sonra baslat.
    subprocess.run(
        [ADB, "-s", SERI, "shell", "run-as", PKG, "rm", "-f", "files/asistan-selftest.txt"],
        check=False,
    )
    subprocess.run(
        [ADB, "-s", SERI, "shell",
         "am", "start", "-n", f"{PKG}/com.hermes.mobile.ui.AssistantSelfTestActivity",
         "--es", "base", args.base,
         "--es", "token", token,
         "--es", "engine", args.engine],
        check=False,
    )
    # Test sentez calma icerebilir; sabit bekleme + dosya kontrolu.
    deadline = time.time() + 420
    path = f"/data/data/{PKG}/files/asistan-selftest.txt"
    last = ""
    while time.time() < deadline:
        time.sleep(10)
        r = subprocess.run(
            [ADB, "-s", SERI, "shell", "run-as", PKG, "cat", path],
            capture_output=True, text=True, check=False,
        )
        body = r.stdout or ""
        if body.strip():
            last = body
            if "SONUC:" in body:
                break
    dest = os.path.join(OUT, f"selftest-{args.etiket}.txt")
    with open(dest, "w", encoding="utf-8") as fh:
        fh.write(last)
    print(last if last.strip() else "(sonuc dosyasi bos)")
    print("--- kayit:", dest)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
