#!/usr/bin/env python3
"""Tur-11 mock voice_api — sözleşmenin (000-TEMP/ses-api-sozlesmesi.md) birebir taklidi.

Emülatör akış kanıtı için: uygulamanın GERÇEK istemci kodu (VoiceApiClient)
bu sahte uca konuşur; böylece "/transcribe'a yükleme -> metin" ve
"/synthesize -> ogg indirme -> çalma" akışları canlı ses hattına dokunmadan
uçtan uca koşar.

Uçlar (sözleşme):
  GET  /health      -> {"ok":true,"stt":"acik","engines":{...}}
  POST /transcribe  -> multipart alan `audio` (ogg/opus) -> {"text","lang"}
  POST /synthesize  -> JSON {"text","engine"} -> audio/ogg (Opus) baytları
Kimlik: `X-Hermes-Session-Token` — yanlış/eksikse 403 (fail-closed).

Kullanım:
  python3 mock_voice_api.py --port 8199 --token <beklenen-token> \
      --ogg <gercek-ogg-dosyasi> --log mock.log [--cold-seconds 3]

Notlar:
  - `--cold-seconds`: İLK sentez çağrısında uyku (gerçek uçta SOĞUKKEN 173-187 sn).
    Varsayılan 3 sn: uygulamanın "ilk yanıt uzun sürebilir" yükleme durumunu
    görebilmek için yeterli, kanıt koşusunu bekletmeyecek kadar kısa.
  - Motorlar tembel: `engines` yalnız çağrıldıktan sonra "acik" olur (gerçek
    davranış: /health motoru ısıtmaz).
"""
import argparse
import json
import re
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

STATE = {
    "cold_done": False,
    "transcribe_calls": 0,
    "synthesize_calls": 0,
    "engines": {"kahya": "kapali", "chatterbox": "kapali", "kadin": "kapali"},
}
LOCK = threading.Lock()
ARGS = None
OGG = b""


def log(line: str) -> None:
    stamp = time.strftime("%Y-%m-%d %H:%M:%S")
    with open(ARGS.log, "a", encoding="utf-8") as fh:
        fh.write(f"{stamp} {line}\n")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # gürültüyü kendi log dosyama yazıyorum
        log("http " + (fmt % args))

    # ── yardımcılar ──────────────────────────────────────────────
    def _token_ok(self) -> bool:
        got = self.headers.get("X-Hermes-Session-Token") or ""
        ok = bool(ARGS.token) and got == ARGS.token
        if not ok:
            log(f"403 {self.command} {self.path} · token={'var' if got else 'yok'} (beklenenle uyusmadi)")
        return ok

    def _json(self, code: int, obj: dict) -> None:
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _bytes(self, code: int, data: bytes, mime: str) -> None:
        self.send_response(code)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _read_body(self) -> bytes:
        length = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(length) if length else b""

    # ── uçlar ────────────────────────────────────────────────────
    def do_GET(self):
        if self.path.split("?")[0] != "/health":
            self._json(404, {"error": "not found"})
            return
        if not self._token_ok():
            self._json(403, {"error": "forbidden"})
            return
        with LOCK:
            payload = {"ok": True, "stt": "acik", "engines": dict(STATE["engines"])}
        log(f"200 GET /health -> {payload}")
        self._json(200, payload)

    def do_POST(self):
        path = self.path.split("?")[0]
        body = self._read_body()
        if not self._token_ok():
            self._json(403, {"error": "forbidden"})
            return
        if path == "/transcribe":
            self._transcribe(body)
        elif path == "/synthesize":
            self._synthesize(body)
        else:
            self._json(404, {"error": "not found"})

    def _transcribe(self, body: bytes) -> None:
        ctype = self.headers.get("Content-Type") or ""
        has_field = b'name="audio"' in body
        fname = re.search(rb'filename="([^"]*)"', body)
        fname = fname.group(1).decode("utf-8", "replace") if fname else "?"
        is_ogg = b"OggS" in body[:4096]
        audio_bytes = len(body)
        with LOCK:
            STATE["transcribe_calls"] += 1
            STATE["engines"]["kahya"] = "acik"
        log(
            f"200 POST /transcribe · multipart={ctype.startswith('multipart/form-data')} "
            f"alan_audio={has_field} dosya={fname} ogg_imza={is_ogg} boyut={audio_bytes}"
        )
        if not has_field:
            self._json(400, {"error": "audio alanı yok"})
            return
        self._json(
            200,
            {"text": ARGS.text, "lang": "tr"},
        )

    def _synthesize(self, body: bytes) -> None:
        try:
            req = json.loads(body.decode("utf-8"))
        except Exception:
            self._json(400, {"error": "bozuk JSON"})
            return
        text = str(req.get("text", ""))
        engine = str(req.get("engine", "")) or "kahya"
        if engine not in STATE["engines"]:
            log(f"400 POST /synthesize · bilinmeyen motor={engine}")
            self._json(400, {"error": f"bilinmeyen motor: {engine}"})
            return
        with LOCK:
            first = not STATE["cold_done"]
            STATE["cold_done"] = True
            STATE["synthesize_calls"] += 1
            STATE["engines"][engine] = "acik"
        if first and ARGS.cold_seconds > 0:
            log(f"SOGUK sentez: {ARGS.cold_seconds} sn motor isitma taklidi (motor={engine})")
            time.sleep(ARGS.cold_seconds)
        log(
            f"200 POST /synthesize · motor={engine} metin_uzunluk={len(text)} "
            f"oggg_bayt={len(OGG)}"
        )
        self._bytes(200, OGG, "audio/ogg")


def main() -> int:
    global ARGS, OGG
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8199)
    ap.add_argument("--token", default="tur11-test-token")
    ap.add_argument("--ogg", required=True)
    ap.add_argument("--log", default="mock.log")
    ap.add_argument("--text", default="Bu bir sesli mesaj denemesidir.")
    ap.add_argument("--cold-seconds", type=float, default=3.0)
    ARGS = ap.parse_args()
    with open(ARGS.ogg, "rb") as fh:
        OGG = fh.read()
    if not OGG.startswith(b"OggS"):
        print(f"UYARI: {ARGS.ogg} ogg değil gibi (OggS imzası yok)", file=sys.stderr)
    srv = ThreadingHTTPServer(("0.0.0.0", ARGS.port), Handler)
    log(f"mock voice_api basladi · port={ARGS.port} ogg={len(OGG)} bayt")
    print(f"mock voice_api http://0.0.0.0:{ARGS.port} (ogg {len(OGG)} bayt)", flush=True)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        srv.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
