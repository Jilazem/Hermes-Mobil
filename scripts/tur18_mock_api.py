#!/usr/bin/env python3
# Tur18 FR-010 14-b4-balon-prose kaniti icin GELECEKTE KULLANILACAK yerel mock
# Hermes API'si (yalniz debug kanitindan ibaret; urun kodu degil, temizlenebilir).
from http.server import BaseHTTPRequestHandler, HTTPServer
import json

STATUS = {
    "version": "0.21.0", "release_date": None,
    "gateway_running": True, "gateway_state": "running",
    "gateway_busy": False, "active_agents": 1, "can_update_hermes": False,
}
STATS = {"total": 1, "archived": 0, "messages": 2, "by_source": {"cli": 1}}
SESSIONS = {"sessions": [{
    "id": "tur18-demo", "source": "cli", "model": "qwen-test",
    "display_name": "Tur18 balon demosu", "title": "Tur18 balon demosu",
    "preview": "Merhaba Hermes, B3 prose balonunu kontrol ediyorum.",
    "message_count": 2, "tool_call_count": 0,
    "input_tokens": 21, "output_tokens": 64,
}]}
MSGS = {"session_id": "tur18-demo", "messages": [
    {"role": "user", "content": "Merhaba Hermes, B3 prose balonunu kontrol ediyorum. 15sp govde 22sp satir arasi gorunecek mi?"},
    {"role": "assistant", "content": "Merhaba! Tur18 B3 + B4 balon duzeni:\n\n- Govde **prose 15sp**, satir araligi 1.45 (≈22sp) — tur3 standart.\n- Kullanici balonu radius-md (10dp) + ici 12/10 bosluk.\n- Kod blogu artik ntr Skeleton yuzeyi, aksan degil (FR-009):\n\n```kotlin\nval bodyLine = fontSize * 1.45f\n```\n\nFont olcegi %115 iken tum roller 15 x 1.15 = 17.25sp ile cizilir."},
]}
PROFILES = {"profiles": []}

class H(BaseHTTPRequestHandler):
    def _send(self, code, obj):
        b = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def do_GET(self):
        p = self.path.split("?")[0]
        if p == "/api/status":
            self._send(200, STATUS)
        elif p == "/api/sessions/stats":
            self._send(200, STATS)
        elif p == "/api/sessions":
            self._send(200, SESSIONS)
        elif p.startswith("/api/sessions/") and p.endswith("/messages"):
            self._send(200, MSGS)
        elif p == "/api/profiles":
            self._send(200, PROFILES)
        else:
            self._send(404, {"error": "no such route (mock)"})

    def do_POST(self):
        self._send(200, {"ok": True})

    def log_message(self, *a):
        pass

if __name__ == "__main__":
    HTTPServer(("127.0.0.1", 9151), H).serve_forever()
