#!/usr/bin/env python3
"""Tur-12 — CANLI /synthesize ölçümü (B-1) + motor durumu kanıtı.

Amaç: tur-11'de "canlı sentez yapılmadı" diye açık kalan maddeyi kapatmak.
Motorlar tembel: ilk sentez motoru yükler (ses ucu ekibinin ölçümü: 173-187 sn).
Bu koşumda Kahya **sıcak** (:8172). Ölçüm:

  1. GET  /health            (token)  -> öncesi motor tablosu
  2. POST /synthesize        (token)  -> kısa cümle, engine=kahya, SÜRE ölçülür
     - dönen ses diske yazılır (ogg imzası + sha256 + bayt)
  3. POST /synthesize (aynı) (token)  -> sıcak/ikinci çağrı süresi
  4. GET  /health            (token)  -> sonrası motor tablosu (motor açık kaldı mı)
  5. GET  /health            tokensiz  -> 403 fail-closed teyidi

Kısıt: motor YÜKLEME dışında hiçbir şey değiştirilmez; **Kahya açık bırakılır**
(kullanıcı bu akşam telefondan test edecek). chatterbox/kadın yüklenmez
(tek motor adı gönderilir).

Token ~/.hermes/.env'den ADIYLA okunur, ekrana basılmaz.
Sonuç: denetim/tur12/live-synth.json
"""
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

BASE = "http://192.168.1.101:8174"
ENV = os.path.expanduser("~/.hermes/.env")
OUT = "/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur12/live-synth.json"
OGG_OUT = "/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur12/live-kahya.ogg"
OGG_OUT2 = "/Users/gokhanuzman/hermes-workspace/wt-android-uzman/denetim/tur12/live-kahya-2.ogg"
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
        with urllib.request.urlopen(req, timeout=timeout) as res:
            body = res.read()
            return {
                "code": res.status,
                "ms": int((time.time() - t0) * 1000),
                "content_type": res.headers.get("Content-Type", ""),
                "bytes": len(body),
                "head": body[:32].hex(),
                "text": body[:300].decode("utf-8", "replace") if "json" in res.headers.get("Content-Type", "") else "",
                "_bytes": body,
            }
    except urllib.error.HTTPError as e:
        return {"code": e.code, "ms": int((time.time() - t0) * 1000), "text": e.read()[:300].decode("utf-8", "replace")}
    except Exception as e:  # noqa: BLE001
        return {"code": -1, "ms": int((time.time() - t0) * 1000), "text": f"{type(e).__name__}: {e}"}


def strip_raw(d):
    return {k: v for k, v in d.items() if k != "_bytes"}


def synthesize(tok: str, text: str, engine: str):
    body = json.dumps({"text": text, "engine": engine}).encode("utf-8")
    return call(
        "/synthesize",
        data=body,
        headers={"X-Hermes-Session-Token": tok, "Content-Type": "application/json; charset=utf-8"},
        timeout=300,
    )


def main() -> int:
    tok = token()
    out = {"base": BASE, "token_present": bool(tok), "sentence": SENTENCE, "engine": "kahya"}

    h0 = call("/health", headers={"X-Hermes-Session-Token": tok}, timeout=30)
    out["health_once"] = strip_raw(h0)

    s1 = synthesize(tok, SENTENCE, "kahya")
    out["synth_1"] = strip_raw(s1)
    if s1.get("_bytes"):
        with open(OGG_OUT, "wb") as fh:
            fh.write(s1["_bytes"])
        out["synth_1"]["sha256"] = hashlib.sha256(s1["_bytes"]).hexdigest()
        out["synth_1"]["ogg_imza"] = s1["_bytes"][:4] == b"OggS"
        out["synth_1"]["kayit"] = OGG_OUT

    s2 = synthesize(tok, SENTENCE, "kahya")
    out["synth_2_sicak"] = strip_raw(s2)
    if s2.get("_bytes"):
        with open(OGG_OUT2, "wb") as fh:
            fh.write(s2["_bytes"])
        out["synth_2_sicak"]["sha256"] = hashlib.sha256(s2["_bytes"]).hexdigest()

    h1 = call("/health", headers={"X-Hermes-Session-Token": tok}, timeout=30)
    out["health_sonra"] = strip_raw(h1)
    out["health_tokensiz"] = call("/health", timeout=30)

    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump(out, fh, ensure_ascii=False, indent=2)
    print(json.dumps(out, ensure_ascii=False, indent=2)[:4000])
    return 0


if __name__ == "__main__":
    sys.exit(main())
