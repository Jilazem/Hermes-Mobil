#!/usr/bin/env python3
"""Tur-11 — CANLI ses hattına salt-okunur rozet: sözleşme uyumu ölçümü.

Gerçek `voice_api` (192.168.1.101:8174) koşarken:
  - GET /health  (X-Hermes-Session-Token)  -> motor durumu
  - POST /transcribe (asset'teki gerçek ogg) -> whisper metni
  - tokensiz istek -> 403 (fail-closed) doğrulaması

Yalnız OKUMA çağrılarıdır; canlı sunucuda hiçbir şey değiştirilmez. Token
~/.hermes/.env'den ADIYLA okunur, ekrana basılmaz.

Sonuç: denetim/tur11/live-probe.json
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

BASE = "http://192.168.1.101:8174"
ENV = os.path.expanduser("~/.hermes/.env")
OGG = "/Users/gokhanuzman/007-HERMES/000-TEMP/sesli-hat/uctan-uca-2/yanit.ogg"
OUT = "/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur11/live-probe.json"


def token() -> str:
    with open(ENV, encoding="utf-8") as fh:
        for line in fh:
            m = re.match(r"\s*HERMES_DASHBOARD_SESSION_TOKEN\s*=\s*(.+)\s*$", line)
            if m:
                return m.group(1).strip().strip('"').strip("'")
    return ""


def call(path, data=None, headers=None, timeout=60):
    req = urllib.request.Request(BASE + path, data=data, headers=headers or {})
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as res:
            body = res.read()
            return {
                "code": res.status,
                "ms": int((time.time() - t0) * 1000),
                "content_type": res.headers.get("Content-Type", ""),
                "bytes": len(body),
                "body": body[:400].decode("utf-8", "replace"),
            }
    except urllib.error.HTTPError as e:
        return {"code": e.code, "ms": int((time.time() - t0) * 1000), "body": e.read()[:200].decode("utf-8", "replace")}
    except Exception as e:  # noqa: BLE001
        return {"code": -1, "ms": int((time.time() - t0) * 1000), "body": f"{type(e).__name__}: {e}"}


def multipart(audio: bytes, filename: str):
    boundary = "----tur11boundary"
    head = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="audio"; filename="{filename}"\r\n'
        "Content-Type: audio/ogg\r\n\r\n"
    ).encode("utf-8")
    tail = f"\r\n--{boundary}--\r\n".encode("utf-8")
    body = head + audio + tail
    return body, f"multipart/form-data; boundary={boundary}"


def main() -> int:
    tok = token()
    out = {"base": BASE, "token_present": bool(tok)}
    out["health_tokensiz"] = call("/health")
    out["health_tokenli"] = call("/health", headers={"X-Hermes-Session-Token": tok})
    with open(OGG, "rb") as fh:
        audio = fh.read()
    body, ctype = multipart(audio, "kayit.ogg")
    out["transcribe_tokenli"] = call(
        "/transcribe",
        data=body,
        headers={"X-Hermes-Session-Token": tok, "Content-Type": ctype},
        timeout=120,
    )
    out["ogg_bytes"] = len(audio)
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump(out, fh, ensure_ascii=False, indent=2)
    print(json.dumps(out, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
