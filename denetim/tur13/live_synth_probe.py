#!/usr/bin/env python3
"""Tur-13 — canli /synthesize yoklamasi (SALT OKUMA/cagri; motor elle yuklenmez).

Amac: emulatordeki "ulasilamadi" hatasinin ucta mi yoksa emulator aginda mi
oldugunu ayirt etmek. Kahya zaten ACIK; bu kosum onu yalniz cagirir.

Token ~/.hermes/.env'den ADIYLA okunur, ekrana basilmaz.
Cikti: denetim/tur13/live-synth.json + live-synth-probe.ogg
"""
import hashlib
import json
import os
import re
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8174"
ENV = os.path.expanduser("~/.hermes/.env")
OUT = "/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur13"
SENTENCE = "Merhaba, sesli asistan hazır!"


def token() -> str:
    with open(ENV, encoding="utf-8") as fh:
        for line in fh:
            m = re.match(r"\s*HERMES_DASHBOARD_SESSION_TOKEN\s*=\s*(.+)\s*$", line)
            if m:
                return m.group(1).strip().strip('"').strip("'")
    return ""


def call(path, data=None, headers=None, timeout=300):
    req = urllib.request.Request(BASE + path, data=data, headers=headers or {})
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            body = r.read()
            return r.status, body, time.time() - t0
    except urllib.error.HTTPError as e:
        return e.code, e.read(), time.time() - t0


def main() -> int:
    tok = token()
    h = {"X-Hermes-Session-Token": tok}
    result = {}

    code, body, took = call("/health", headers=h, timeout=30)
    result["health_once"] = {
        "code": code, "took_s": round(took, 1),
        "body": json.loads(body.decode()) if code == 200 else body[:200].decode(errors="replace"),
    }
    print("health:", json.dumps(result["health_once"], ensure_ascii=False)[:300])

    payload = json.dumps({"text": SENTENCE, "engine": "kahya"}).encode()
    hh = dict(h)
    hh["Content-Type"] = "application/json"
    code, body, took = call("/synthesize", data=payload, headers=hh, timeout=420)
    result["synthesize"] = {
        "code": code, "took_s": round(took, 1), "bytes": len(body),
        "ogg": body[:4] == b"OggS",
        "sha256_8": hashlib.sha256(body).hexdigest()[:8] if body else "-",
    }
    print("synthesize:", json.dumps(result["synthesize"], ensure_ascii=False))
    if body and body[:4] == b"OggS":
        with open(os.path.join(OUT, "live-synth-probe.ogg"), "wb") as fh:
            fh.write(body)

    code2, body2, took2 = call("/health", headers=h, timeout=30)
    result["health_after"] = {
        "code": code2, "took_s": round(took2, 1),
        "body": json.loads(body2.decode()) if code2 == 200 else body2[:200].decode(errors="replace"),
    }
    print("health(sonra):", json.dumps(result["health_after"], ensure_ascii=False)[:300])

    with open(os.path.join(OUT, "live-synth.json"), "w", encoding="utf-8") as fh:
        json.dump(result, fh, ensure_ascii=False, indent=2)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
