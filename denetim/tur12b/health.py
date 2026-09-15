#!/usr/bin/env python3
"""Tur-12b — SALT-OKUNUR motor durumu teyidi (Kahya'ya DOKUNULMADI).

Bu turda hiç /synthesize çağrısı YAPILMADI (kısıt: motor ısıtma yok, Kahya açık
kalsın). Yalnız GET /health ile tur-12 sonundaki durumun değişmediği kanıtlanır.

Token ~/.hermes/.env'den ADIYLA okunur, ekrana basılmaz.
Sonuç: denetim/tur12b/health.json
"""
import json
import os
import re
import time
import urllib.error
import urllib.request

BASE = "http://192.168.1.101:8174"
ENV = os.path.expanduser("~/.hermes/.env")
OUT = "/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur12b/health.json"


def token() -> str:
    with open(ENV, encoding="utf-8") as fh:
        for line in fh:
            m = re.match(r"\s*HERMES_DASHBOARD_SESSION_TOKEN\s*=\s*(.+)\s*$", line)
            if m:
                return m.group(1).strip().strip('"').strip("'")
    return ""


def get(path, with_token=True, timeout=15):
    headers = {}
    tok = token()
    if with_token:
        headers["X-Hermes-Session-Token"] = tok
    req = urllib.request.Request(BASE + path, headers=headers)
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as res:
            body = res.read()
            return {
                "code": res.status,
                "ms": int((time.time() - t0) * 1000),
                "text": body[:400].decode("utf-8", "replace"),
            }
    except urllib.error.HTTPError as exc:  # 403 fail-closed teyidi
        return {
            "code": exc.code,
            "ms": int((time.time() - t0) * 1000),
            "text": exc.read()[:200].decode("utf-8", "replace"),
        }
    except Exception as exc:  # noqa: BLE001
        return {"code": -1, "ms": int((time.time() - t0) * 1000), "error": type(exc).__name__}


out = {
    "base": BASE,
    "token_present": bool(token()),
    "synthesize_calls": 0,
    "health_tokenli": get("/health"),
    "health_tokensiz": get("/health", with_token=False),
}
with open(OUT, "w", encoding="utf-8") as fh:
    json.dump(out, fh, ensure_ascii=False, indent=2)
print(json.dumps({k: v for k, v in out.items() if k != "base"}, ensure_ascii=False, indent=2))
