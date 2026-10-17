#!/usr/bin/env python3
# K18B-G3 emülatör akış kanıtı için YEREL sahte Hermes gateway'i.
#
# NEDEN: "onayla → eylem devam / reddet → iptal" akışını gerçek cihazda
# doğrulamak için ajanın bir approval.request üretmesi gerekir; bu rastgele
# bir olay. Sahte gateway, GERÇEK gateway protokolünü (tui_gateway JSON-RPC
# + olay akışı, bkz. GatewayWsClient.kt dokümanı) taklit eder: uygulama
# gerçek APK ile emülatörde koşar, yalnız sunucu bu makaledir. KANIT:
# slash.exec günlük satırı (onayla→/approve, reddet→/deny) + ekran
# görüntüleri. Gerçek gateway ile aynı sözleşmedir; kod kopyalaması yoktur.
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

LOG_PATH = "/tmp/k18b-g3-mock.log"
WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
GEN = 1
APPROVAL_SEQ = 0
SESSION_SEQ = 0
LAST_PROMPT = {}  # session_id -> prompt_id


def log(kind, **obj):
    line = json.dumps({"t": time.time(), "kind": kind, **obj}, ensure_ascii=False)
    with open(LOG_PATH, "a") as f:
        f.write(line + "\n")
    print(line, flush=True)


class WsConn:
    """RFC6455 sunucu tarafı — yalnız ihtiyaacımız olan kare türleri."""

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
        """İstemci karelerini (metin) üretir; bağlantı kapanınca biter."""
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
            if opcode == 8:  # close
                return
            if opcode == 9:  # ping -> pong
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

    # ── REST (uygulama açılışı için asgari yüzey) ─────────────────────
    def do_GET(self):
        if self.path.startswith("/api/ws"):
            return self._ws()
        path = self.path.split("?")[0]
        if path == "/api/sessions":
            return self._json({"sessions": []})
        if path == "/api/profiles":
            return self._json({"profiles": []})
        if path in ("/api/status", "/api/system/stats", "/api/sessions/stats",
                    "/api/model/options", "/api/model/info", "/api/profiles/active"):
            return self._json({})
        return self._json({})

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length:
            self.rfile.read(length)
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
        global APPROVAL_SEQ, SESSION_SEQ, GEN
        method = msg.get("method")
        mid = msg.get("id")
        params = msg.get("params") or {}
        log("rpc", method=method, params=params)
        if mid is None:
            return

        if method == "session.create":
            SESSION_SEQ += 1
            sid = f"mock-s{SESSION_SEQ}"
            result = {"session_id": sid, "session_key": f"mock-k{SESSION_SEQ}"}
        elif method == "prompt.submit":
            result = {"session_id": params.get("session_id") or f"mock-s{SESSION_SEQ + 1}"}
            threading.Timer(0.3, self._send_approvals, (conn, params)).start()
        elif method == "slash.exec":
            command = (params.get("command") or "").strip()
            sid = params.get("session_id")
            result = {"output": f"mock: {command} alındı"}
            threading.Timer(0.2, self._post_decision, (conn, sid, command)).start()
        elif method == "session.active_list":
            result = {"sessions": []}
        elif method == "session.history":
            result = {"messages": []}
        elif method in ("session.activate", "session.resume", "session.interrupt",
                        "session.steer", "session.redirect"):
            # SessionBinding.parse: result.session_id ZORUNLU.
            sid = params.get("session_id") or params.get("session_key") or f"mock-s{SESSION_SEQ}"
            result = {"session_id": sid, "session_key": params.get("session_key") or sid}
        else:
            result = {"status": "ok"}
        conn.send_text(json.dumps({"id": mid, "result": result}, ensure_ascii=False))

    def _send_approvals(self, conn, params):
        """prompt.submit sonrası: ajan 'riskli iş' için onay ister (senaryo)."""
        global APPROVAL_SEQ, GEN
        sid = params.get("session_id") or "mock-s1"
        text = (params.get("text") or "").lower()
        count = 2 if "iki" in text else 1
        for i in range(count):
            APPROVAL_SEQ += 1
            pid = f"p-{APPROVAL_SEQ}"
            LAST_PROMPT[sid] = pid
            if i == 0:
                desc = "Mock: /tmp/ornek klasörünü silmek istiyor"
                cmd = "rm -rf /tmp/ornek"
            else:
                desc = "Mock: paketi yeniden imzalamak istiyor"
                cmd = "apksigner sign app.apk"
            self._event(
                conn,
                "approval.request",
                sid,
                {
                    "prompt_id": pid,
                    "execution_generation": GEN,
                    "description": desc,
                    "command": cmd,
                    "allow_permanent": False,
                },
            )
            GEN += 1

    def _post_decision(self, conn, sid, command):
        """Onayla → eylem devam; reddet → iptal. Gerçek gateway davranışı."""
        if command.startswith("/approve"):
            self._event(conn, "message.delta", sid, {"text": "✓ Onaylandı — eylem devam ediyor."})
            self._event(conn, "message.complete", sid, {"text": "✓ Onaylandı — eylem devam ediyor."})
        elif command.startswith("/deny"):
            reason = command[len("/deny"):].strip()
            tail = f" (sebep: {reason})" if reason else ""
            self._event(conn, "message.delta", sid, {"text": f"✗ Reddedildi — eylem iptal edildi{tail}."})
            self._event(conn, "message.complete", sid, {"text": f"✗ Reddedildi — eylem iptal edildi{tail}."})
        pid = LAST_PROMPT.get(sid)
        if pid:
            self._event(conn, "approval.settled", sid, {"prompt_id": pid, "execution_generation": GEN})


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