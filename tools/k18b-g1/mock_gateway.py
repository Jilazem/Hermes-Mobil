#!/usr/bin/env python3
# K18B-G1 emülatör akış kanıtı için YEREL sahte Hermes gateway'i.
#
# NEDEN: "bot oluştur → seç → devam et (aynı profil+model)" akışını gerçek
# cihazda doğrulamak için sunucudan profil listesi + model listesi gerekir.
# Sahte gateway GERÇEK gateway protokolünü taklit eder (tui_gateway JSON-RPC +
# REST yüzeyi, bkz. GatewayWsClient.kt / HermesClient.kt sözleşmeleri):
# uygulama gerçek APK ile emülatörde koşar, yalnız sunucu bu makinedir.
# KANIT: rpc günlük satırları (session.create params.profile = bot kartının
# profili) + ekran görüntüleri. K18B-G3 mock_gateway.py temeli alınmıştır
# (kendi işimiz); sözleşmede değişiklik yoktur.
#
# Kullanım: python3 mock_gateway.py [port]   (varsayılan 9199)
# Emülatörden adres: http://10.0.2.2:<port>
import base64
import hashlib
import json
import struct
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOG_PATH = "/tmp/k18b-g1-mock.log"
WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
SESSION_SEQ = 0

# K18B-G1 senaryo verisi: 4 profil (görünen adlar insan adı — iç ad sızıntısı
# testi için) + 2 sağlayıcı/3 model.
PROFILES = [
    {"name": "arastirma", "display_name": "Arastirma Ekibi", "is_default": False,
     "model": None, "provider": None, "skill_count": 3, "gateway_running": True,
     "description": "Mock profil"},
    {"name": "yedek", "display_name": "Yedek Bot", "is_default": False,
     "model": None, "provider": None, "skill_count": 1, "gateway_running": True,
     "description": "Mock profil"},
    {"name": "cevirmen", "display_name": "Cevirmen Ada", "is_default": False,
     "model": None, "provider": None, "skill_count": 2, "gateway_running": True,
     "description": "Mock profil"},
    {"name": "kodcu", "display_name": "Kodcu Mert", "is_default": False,
     "model": None, "provider": None, "skill_count": 5, "gateway_running": True,
     "description": "Mock profil"},
]

MODEL_OPTIONS = {
    "providers": [
        {
            "slug": "nous", "name": "Nous", "is_current": True,
            "models": ["deepseek-ai/DeepSeek-V4.1-Flash", "Hermes-4-405B"],
            "unavailable_models": [],
            "pricing": {"deepseek-ai/DeepSeek-V4.1-Flash": {"free": True},
                        "Hermes-4-405B": {"input": "$1.00", "output": "$3.00"}},
        },
        {
            "slug": "evren", "name": "Evren", "is_current": False,
            "models": ["mimo-v2.6-pro"],
            "unavailable_models": [],
            "pricing": {"mimo-v2.6-pro": {"free": True}},
        },
    ]
}


def log(kind, **obj):
    line = json.dumps({"t": time.time(), "kind": kind, **obj}, ensure_ascii=False)
    with open(LOG_PATH, "a") as f:
        f.write(line + "\n")
    print(line, flush=True)


class WsConn:
    """RFC6455 sunucu tarafı — yalnız ihtiyacımız olan kare türleri."""

    def __init__(self, sockfile, wfile, sock):
        self.rfile = sockfile
        self.wfile = wfile
        self.sock = sock
        self.lock = threading.Lock()

    def _read_exact(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.rfile.read(n - len(buf))
            if not chunk:
                return None
            buf += chunk
        return buf

    def send_text(self, text):
        data = text.encode()
        hdr = bytearray([0x81])
        n = len(data)
        if n < 126:
            hdr.append(n)
        elif n < 65536:
            hdr.append(126)
            hdr += struct.pack("!H", n)
        else:
            hdr.append(127)
            hdr += struct.pack("!Q", n)
        with self.lock:
            self.wfile.write(bytes(hdr) + data)
            self.wfile.flush()

    def frames(self):
        while True:
            h = self._read_exact(2)
            if h is None:
                return
            opcode = h[0] & 0x0F
            masked = (h[1] & 0x80) != 0
            ln = h[1] & 0x7F
            if ln == 126:
                ext = self._read_exact(2)
                if ext is None:
                    return
                ln = struct.unpack("!H", ext)[0]
            elif ln == 127:
                ext = self._read_exact(8)
                if ext is None:
                    return
                ln = struct.unpack("!Q", ext)[0]
            mask = self._read_exact(4) if masked else None
            payload = self._read_exact(ln) if ln else b""
            if payload is None:
                return
            if mask:
                payload = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
            if opcode == 8:
                return
            if opcode == 9:
                with self.lock:
                    self.wfile.write(bytes([0x8A, len(payload)]) + payload)
                    self.wfile.flush()
                continue
            if opcode in (0, 1):
                yield payload.decode(errors="replace")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass

    def _json(self, obj, code=200):
        body = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
        log("http", method="GET" if self.command == "GET" else self.command, path=self.path)

    # ── REST (uygulama açılışı + profil/model listeleri) ──────────────
    def do_GET(self):
        if self.path.startswith("/api/ws"):
            return self._ws()
        path = self.path.split("?")[0]
        if path == "/api/sessions":
            return self._json({"sessions": []})
        if path == "/api/profiles":
            return self._json({"profiles": PROFILES})
        if path == "/api/profiles/active":
            return self._json({"active": "arastirma", "current": "arastirma"})
        if path == "/api/model/options":
            return self._json(MODEL_OPTIONS)
        if path == "/api/model/info":
            return self._json({"provider": "nous", "model": "deepseek-ai/DeepSeek-V4.1-Flash"})
        if path in ("/api/status", "/api/system/stats", "/api/sessions/stats"):
            return self._json({})
        return self._json({})

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else b""
        log("http", method="POST", path=self.path, body=body.decode(errors="replace")[:200])
        return self._json({"ok": True})

    def do_DELETE(self):
        return self._json({"ok": True})

    def do_PUT(self):
        return self._json({"ok": True})

    # ── WebSocket: tui_gateway JSON-RPC ───────────────────────────────
    def _ws(self):
        key = self.headers.get("Sec-WebSocket-Key")
        if not key:
            return self._json({"error": "ws key yok"}, 400)
        accept = base64.b64encode(hashlib.sha1((key + WS_GUID).encode()).digest()).decode()
        self.wfile.write(
            (
                "HTTP/1.1 101 Switching Protocols\r\n"
                "Upgrade: websocket\r\n"
                "Connection: Upgrade\r\n"
                f"Sec-WebSocket-Accept: {accept}\r\n\r\n"
            ).encode()
        )
        self.wfile.flush()
        conn = WsConn(self.rfile, self.wfile, self.connection)
        log("ws_open")
        for raw in conn.frames():
            try:
                msg = json.loads(raw)
            except ValueError:
                continue
            self._rpc(conn, msg)
        log("ws_close")

    def _event(self, conn, etype, sid, payload):
        conn.send_text(
            json.dumps(
                {"method": "event", "params": {"type": etype, "session_id": sid, "payload": payload}},
                ensure_ascii=False,
            )
        )
        log("event", type=etype, session_id=sid, payload=payload)

    def _rpc(self, conn, msg):
        global SESSION_SEQ
        method = msg.get("method")
        mid = msg.get("id")
        params = msg.get("params") or {}
        log("rpc", method=method, params=params)
        if mid is None:
            return

        if method == "session.create":
            SESSION_SEQ += 1
            sid = f"mock-s{SESSION_SEQ}"
            # SessionBinding.parse: result.session_id ZORUNLU.
            result = {"session_id": sid, "session_key": f"mock-k{SESSION_SEQ}"}
        elif method == "prompt.submit":
            sid = params.get("session_id") or f"mock-s{SESSION_SEQ}"
            text = params.get("text") or ""
            result = {"session_id": sid}
            reply = f"Mock yanit ({sid}): \"{text}\" istegi alindi."
            threading.Timer(
                0.3, self._send_reply, (conn, sid, reply)
            ).start()
        elif method == "slash.exec":
            command = (params.get("command") or "").strip()
            # Gerçek gateway protokolü: uygulama `modelSwitchAccepted` ile
            # çıktıyı doğrular (içinde "switched" ya da "✓" OLMALI) — bkz.
            # ModelChoiceLogic.kt. Taklit gerçeğe sadık olmalı.
            if command.startswith("/model"):
                result = {"output": f"✓ switched ({command})"}
            else:
                result = {"output": f"mock: {command} alindi"}
        elif method == "session.active_list":
            result = {"sessions": []}
        elif method == "session.history":
            result = {"messages": []}
        elif method in ("session.activate", "session.resume", "session.interrupt",
                        "session.steer", "session.redirect"):
            sid = params.get("session_id") or params.get("session_key") or f"mock-s{SESSION_SEQ}"
            result = {"session_id": sid, "session_key": params.get("session_key") or sid}
        else:
            result = {"status": "ok"}
        conn.send_text(json.dumps({"id": mid, "result": result}, ensure_ascii=False))

    def _send_reply(self, conn, sid, text):
        self._event(conn, "message.delta", sid, {"text": text})
        self._event(conn, "message.complete", sid, {"text": text})


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9199
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    log("start", port=port)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()