"""V4 e2e mock gateway — yalnız stdlib (ders b77de80d: serve yerine stdlib mock).

9171'de dinler (emülatörde 10.0.2.2:9171). Yalnız birleştirme e2e'si için
gerekli uçlar: GET /api/sessions, POST /api/czip/merge. Diğer uçlar permissif
boş JSON döner. 30 dk sonra kendini kapatır (sonsuz bekleme yok).
"""
import base64
import hashlib
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BITIS = time.time() + 1800

MERGED_ID = "birlesik-v4-e2e"

SESSIONS = {
    "sessions": [
        {"id": "k21-a", "title": "K21 Oturum A final", "source": "gateway",
         "preview": "İskelet sonra doluyor mu?"},
        {"id": "k21-b", "title": "K21 Oturum B final", "source": "gateway",
         "preview": "gönder butonu durumu"},
        {"id": MERGED_ID, "title": "V4 birlesik", "source": "gateway",
         "preview": "birleşik oturum (mock e2e)"},
    ]
}

MERGE_YANIT = {
    "session_id": MERGED_ID,
    "title": "V4 birlesik",
    "sources": [
        {"sid": "k21-a", "baslik": "K21 Oturum A final", "ilet": 3},
        {"sid": "k21-b", "baslik": "K21 Oturum B final", "ilet": 2},
    ],
    "source_message_count": 5,
    "merged_message_count": 5,
    "duplicates_dropped": 0,
}


class Mock(BaseHTTPRequestHandler):
    def _json(self, code, obj):
        govde = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(govde)))
        self.end_headers()
        self.wfile.write(govde)

    def do_GET(self):
        print(f"[mock] GET {self.path}", flush=True)
        if self.path.startswith("/api/ws") or self.path.startswith("/ws"):
            self._ws_upgrade()
            return
        if self.path.startswith("/api/sessions/stats") or self.path.startswith("/api/status"):
            self._json(200, {})
        elif self.path.startswith("/api/sessions"):
            self._json(200, SESSIONS)
        else:
            self._json(200, {})

    # ── Minimal WebSocket (RFC 6455): upgrade + ping/pong; gelen çerçeveler yok sayılır.
    def _ws_upgrade(self):
        key = self.headers.get("Sec-WebSocket-Key", "")
        if not key:
            self.send_error(400)
            return
        accept = base64.b64encode(
            hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()).digest()
        ).decode()
        self.send_response(101, "Switching Protocols")
        self.send_header("Upgrade", "websocket")
        self.send_header("Connection", "Upgrade")
        self.send_header("Sec-WebSocket-Accept", accept)
        self.end_headers()
        self.connection.settimeout(30)
        try:
            while time.time() < BITIS:
                baslik = self.connection.recv(2)
                if len(baslik) < 2:
                    break
                op = baslik[0] & 0x0F
                maske = baslik[1] & 0x80
                uzunluk = baslik[1] & 0x7F
                if uzunluk == 126:
                    uzunluk = int.from_bytes(self.connection.recv(2), "big")
                elif uzunluk == 127:
                    uzunluk = int.from_bytes(self.connection.recv(8), "big")
                anahtar = self.connection.recv(4) if maske else b""
                self.connection.recv(uzunluk)
                if op == 0x9:  # ping → pong
                    self.connection.sendall(bytes([0x8A, 0x80]) + anahtar)
                elif op == 0x8:  # close
                    break
        except OSError:
            pass
        self.close_connection = True

    def do_POST(self):
        uzunluk = int(self.headers.get("Content-Length", 0))
        govde = self.rfile.read(uzunluk).decode(errors="replace") if uzunluk else ""
        print(f"[mock] POST {self.path} {govde}", flush=True)
        if self.path.startswith("/api/czip/merge"):
            self._json(200, MERGE_YANIT)
        else:
            self._json(200, {})

    def log_message(self, *a):
        pass


sunucu = ThreadingHTTPServer(("127.0.0.1", 9171), Mock)
threading.Thread(target=lambda: (time.sleep(1800), sunucu.shutdown()), daemon=True).start()
print("[mock] 9171 hazir (1800 sn)", flush=True)
sunucu.serve_forever()
