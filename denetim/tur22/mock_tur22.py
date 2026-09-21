#!/usr/bin/env python3
# Tur22 kanıt mock'u — tur18 mock'unun genişletilmiş hâli (yalnız debug kanıtı).
#   /api/sessions  : 2.5 sn GEÇ yanıt (iskelet kanıtı) + 2 oturum
#   /api/empty=1   : boş oturum listesi (oturum-yok boş durumu)
#   /api/sessions/<id>/messages : boş / dolu mesaj
import base64
import hashlib
import json
import os
import re
import socket
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

WS_KEY_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"


def ws_accept(key: str) -> str:
    return base64.b64encode(hashlib.sha1((key + WS_KEY_GUID).encode()).digest()).decode()

MODE = {"empty": False}

STATUS = {
    "version": "0.21.0", "release_date": None,
    "gateway_running": True, "gateway_state": "running",
    "gateway_busy": False, "active_agents": 1, "can_update_hermes": False,
}
STATS = {"total": 2, "archived": 1, "messages": 6, "by_source": {"cli": 2}}
SESSIONS = {"sessions": [
    {"id": "tur22-d1", "source": "cli", "model": "qwen-test",
     "display_name": "Tur22 kare testi", "title": "Tur22 kare testi",
     "preview": "İskelet sonra doluyor mu?", "message_count": 3,
     "tool_call_count": 0, "input_tokens": 10, "output_tokens": 30},
    {"id": "tur22-d2", "source": "cli", "model": "qwen-test",
     "display_name": "İkinci oturum", "title": "İkinci oturum",
     "preview": "gönder butonu durumu", "message_count": 2,
     "tool_call_count": 0, "input_tokens": 5, "output_tokens": 12},
]}
MSGS = {"session_id": "tur22-d1", "messages": [
    {"role": "user", "content": "İskelet sonra doluyor mu?"},
    {"role": "assistant", "content": "Evet — skeleton alfası 0.35-0.75 nabız, veri gelince 180ms fade."},
    {"role": "user", "content": "Mesaj girişi?"},
    {"role": "assistant", "content": "Fade + 20dp slide, graphicsLayer, topuklama yok."},
]}
PROFILES = {"profiles": []}


class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _send(self, code, obj):
        b = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def do_GET(self):
        p = self.path.split("?")[0]
        # WS upgrade — 101 handshake, sonra sakin dur (mesajsız boş akış).
        if p in ("/ws", "/api/ws") and (self.headers.get("Upgrade") or "").lower() == "websocket":
            key = self.headers.get("Sec-WebSocket-Key", "")
            accept = ws_accept(key)
            resp = (
                "HTTP/1.1 101 Switching Protocols\r\n"
                "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                f"Sec-WebSocket-Accept: {accept}\r\n\r\n"
            )
            self.wfile.write(resp.encode())
            self.wfile.flush()
            # Bağlı kal: soketi kapatma, okuma döngüsü istemci close'una kadar.
            try:
                self.connection.settimeout(None)
                while True:
                    data = self.connection.recv(4096)
                    if not data:
                        break
            except Exception:
                pass
            return
        if p == "/mode/empty-on":
            MODE["empty"] = True
            return self._send(200, {"mode": "empty"})
        if p == "/mode/empty-off":
            MODE["empty"] = False
            return self._send(200, {"mode": "full"})
        if p == "/api/status":
            return self._send(200, STATUS)
        if p == "/api/sessions/stats":
            return self._send(200, STATS)
        if p == "/api/sessions":
            # İSKELET KANITI: 6 sn yavaşlat — çekmece açılıp iskelet karesi
            # alınabilmesi için yükleme penceresi geniş olmalı.
            # 480-kare nabız sayımı için SLOW_SEC env ile genişletilebilir.
            import os
            time.sleep(float(os.environ.get("SLOW_SEC", "6")))
            return self._send(200, {"sessions": []} if MODE["empty"] else SESSIONS)
        if p.startswith("/api/sessions/") and p.endswith("/messages"):
            # Sohbet geçmişi iskeleti penceresi — 1.5 sn yavaş.
            time.sleep(1.5)
            return self._send(200, MSGS)
        if p == "/api/profiles":
            return self._send(200, PROFILES)
        if p == "/api/system/stats":
            return self._send(200, {
                "os": "macOS", "hostname": "tur22-mock", "arch": "arm64",
                "hermes_version": "0.21.0", "cpu_count": 10, "cpu_percent": 12.5,
                "memory": {"total_gb": 32.0, "used_gb": 8.0, "available_gb": 24.0},
                "disk": {"total_gb": 500.0, "used_gb": 200.0, "free_gb": 300.0},
                "uptime_seconds": 12345.0,
            })
        if p == "/api/cron/jobs":
            return self._send(200, [])
        return self._send(404, {"error": "no such route (mock tur22)"})

    def do_POST(self):
        self._send(200, {"ok": True})

    def log_message(self, format, *args):  # noqa: A002 — imza base ile birebir
        import sys
        print(f"{self.address_string()} {format % args}", file=sys.stderr, flush=True)


if __name__ == "__main__":
    # ThreadingHTTPServer: WS bağlantıları (6sn REST yavaşlatmasını bloklar).
    port = int(os.environ.get("PORT", "9151"))
    ThreadingHTTPServer.allow_reuse_address = True
    ThreadingHTTPServer(("0.0.0.0", port), H).serve_forever()
