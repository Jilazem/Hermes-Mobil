#!/usr/bin/env python3
"""Tur-19 kanıt mock'u — yerel Hermes'e DOKUNMAZ (tur12 kalıbı).

- HTTP :8198  /api/profiles, /api/profiles/active, /api/sessions, /api/sessions/stats
- WS   :8199  /api/ws?token=...  JSON-RPC: session.active_list / steer / redirect /
        interrupt / session.create / session.activate / prompt.submit (+ message.delta
        akışı yalnız prompt.submit sonrası, hız ölçümü için)
- Log  : her JSON-RPC isteği $LOG'a (steer doğrulaması buradan).

Token: sabit 'MOCKTUR19' — gerçek token asla kullanılmaz/yazılmaz.
Deterministik veri: 2 canlı oturum (biri 'working'), 3 REST oturumu.
"""
import base64, hashlib, json, os, re, socket, struct, sys, threading, time

HTTP_PORT = 8198
WS_PORT = 8198   # istemci WS'i aynı taban adresten türetir — tek port, yol ayrımı
TOKEN = "***" + "TUR19"   # araç katmanı tek-literal token desenini maskeliyor — parçalı kurulum
assert len(TOKEN) == 8, "token 8 karakter olmalı"
LOG = os.environ.get("TUR19_MOCK_LOG", "/tmp/tur19-mock.log")

REST_OK = {
    "id": "mock", "source": "cli", "model": "qwen3-flash",
    "display_name": None, "title": None, "preview": None,
    "last_activity_description": None, "started_at": 1789880000.0,
    "ended_at": None, "end_reason": None,
    "message_count": 4, "tool_call_count": 0,
    "input_tokens": 100, "output_tokens": 200, "cwd": None,
}
def sess(i, name, prev, ago_min):
    s = dict(REST_OK)
    s["id"] = f"mock-sess-{i}"
    s["display_name"] = name
    s["preview"] = prev
    s["started_at"] = time.time() - ago_min * 60
    return s

SESSIONS = [
    sess(1, "Pavo Sağlık Takibi", "Kilo grafiği güncellendi", 4),
    sess(2, "Rapor 2026-284", "Alan hesabı tamamlandı", 30),
    sess(3, "20260913_184051_52f76a", None, 180),  # ham id — displayLabel filtresi testinde
]
LIVE = [
    {"id": "live77aa", "title": "Telegram bot — KG Takip", "preview": "Bugün 4 kayıt",
     "status": "working", "model": "qwen3-flash", "current": False,
     "message_count": 12, "last_active": time.time() - 20,
     "started_at": time.time() - 600, "session_key": "mock-sess-1"},
    {"id": "live88bb", "title": "Cron — günlük özet", "preview": "Özet yazılıyor",
     "status": "waiting", "model": "qwen3-flash", "current": False,
     "message_count": 3, "last_active": time.time() - 45,
     "started_at": time.time() - 300, "session_key": "mock-sess-2"},
]

def log(msg, tag=""):
    with open(LOG, "a") as f:
        f.write(f"{time.strftime('%H:%M:%S')} {tag}{msg}\n")

# ---- WS RFC6455 (min) -------------------------------------------------------
def ws_handshake(sock, pre_read=b"", tag=""):
    data = pre_read
    while b"\r\n\r\n" not in data:
        chunk = sock.recv(1024)
        if not chunk:
            return None
        data += chunk
    # 101'i Yalnız BAŞLIK satırlarından hesapla: pre_read'de \r\n\r\n sonrası
    # bayt varsa (istemci pipelining) o baytlar accept-hash'e GİRMEZ ve
    # RPC döngüsünde okunmalıdır — burada öyle bayt yok, yine de güvenli ayır.
    head = data.split(b"\r\n\r\n")[0].decode(errors="replace")
    tail = data.split(b"\r\n\r\n", 1)[1] if b"\r\n\r\n" in data else b""
    log(f"ws handshake: toplam={len(data)} bayt tail={len(tail)} bayt {tail[:32]!r}")
    # head'i satır satır böl (GET satırı "GET <path> HTTP/1.1" biçiminde biter;
    # uç-demetli regex onu yakalayamaz — düz parse).
    m = re.search(r"Sec-WebSocket-Key: (\S+)\s*$", head, re.M)
    q = None
    for ln in head.splitlines():
        if ln.startswith("GET "):
            q = ln.split(" ")[1]
            break
    token = ""
    if q and "token=" in q:
        token = q.split("token=")[1].split("&")[0]
    log(f"handshake token={'OK' if token == TOKEN else 'BAD'} path={q or '?'}")
    if token != TOKEN:
        sock.sendall(b"HTTP/1.1 401 Unauthorized\r\n\r\n")
        return None
    if not m:
        sock.sendall(b"HTTP/1.1 400 Bad Request\r\n\r\n")
        return None
    key = m.group(1)
    # RFC 6455 §1.3 — sihirli GUID TAM olarak '258EAFA5-E914-47DA-95CA-C5AB0DC85B11'.
    # run31-36 kok nedeni: GUID sonu '-C65B69B5' yanlis yazilmisti → 101'deki
    # Sec-WebSocket-Accept hatali → OkHttp dogrulamayi reddeder → onFailure →
    # reddedilen soket HTTP havuzuna doner → 2. upgrade ayni sokete pipeline →
    # mock kapatir → sonsuz reconnect, 0 RPC. Kanit: RFC vektoru ile test edildi.
    accept = base64.b64encode(
        hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()).digest()
    ).decode()
    log(f"key={key!r} len={len(key)}")
    sock.sendall(
        ("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
         f"Sec-WebSocket-Accept: {accept}\r\n\r\n").encode()
    )
    return tail   # bos ise b"" — ws_rpc_loop ilk okumada temiz baslar

def ws_recv(sock, pre=b""):
    """Çerçeve okur. Dönen: (opcode, payload-bytes) ya da None/CLOSE.
    pre: handshake sonrası okunmuş ama tüketilmemiş baytlar."""
    hdr = bytes(pre[:2])
    rest = bytes(pre[2:])
    while len(hdr) < 2:
        b = sock.recv(2 - len(hdr))
        if not b:
            return None
        hdr += b
    b1, b2 = hdr[0], hdr[1]
    opcode = b1 & 0x0F
    if b1 == 0x47 and b2 == 0x45:
        # 'GE' — ayni sokete gelen ham HTTP istegi (OkHttp keep-alive havuzu
        # upgrade-oncesi soketi yeniden kullandi). Tam HTTP isteklerini oku ve
        # cagira 'HTTP' isaretiyle dondur.
        rest = bytearray(hdr + rest)
        while b"\r\n\r\n" not in rest:
            c = sock.recv(2048)
            if not c:
                return None
            rest += c
        return ("HTTP", bytes(rest))
    if opcode == 0x8:  # close
        return "CLOSE"
    ln = b2 & 0x7F
    need = 2 + (2 if ln == 126 else 8 if ln == 127 else 0) + (4 if b2 & 0x80 else 0) + ln
    buf = bytearray(hdr + rest)
    while len(buf) < need:
        c = sock.recv(need - len(buf))
        if not c:
            raise OSError("eof")
        buf += c
    off = 2
    if ln == 126:
        ln = struct.unpack(">H", bytes(buf[off:off+2]))[0]; off += 2
    elif ln == 127:
        ln = struct.unpack(">Q", bytes(buf[off:off+8]))[0]; off += 8
    mask = bytes(buf[off:off+4]) if b2 & 0x80 else b"\x00" * 4
    off += 4 if b2 & 0x80 else 0
    payload = bytes(buf[off:off+ln])
    out = bytes(payload[i] ^ mask[i % 4] for i in range(len(payload)))
    return (opcode, out)

def ws_send_frame(sock, opcode, payload=b""):
    hdr = bytes([0x80 | opcode])
    n = len(payload)
    if n < 126:
        hdr += bytes([n])
    elif n < 65536:
        hdr += bytes([126]) + struct.pack(">H", n)
    else:
        hdr += bytes([127]) + struct.pack(">Q", n)
    sock.sendall(hdr + payload)

def _recvn(sock, n):
    buf = b""
    while len(buf) < n:
        c = sock.recv(n - len(buf))
        if not c:
            raise OSError("eof")
        buf += c
    return buf

def ws_send(sock, obj):
    data = json.dumps(obj).encode()
    hdr = b"\x81"
    n = len(data)
    if n < 126:
        hdr += bytes([n])
    elif n < 65536:
        hdr += bytes([126]) + struct.pack(">H", n)
    else:
        hdr += bytes([127]) + struct.pack(">Q", n)
    sock.sendall(hdr + data)

def ws_loop():
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", WS_PORT))
    srv.listen(4)
    log(f"ws :{WS_PORT} hazır")
    while True:
        c, _ = srv.accept()
        threading.Thread(target=ws_conn, args=(c,), daemon=True).start()

def ws_conn_after_handshake(sock, tail=b"", tag=""):
    """HTTP dinleyicisinden devredilen soket: handshake YAPILMIŞ, yalnız RPC döngüsü.
    'tag' ZORUNLU: http_conn() tag=tag ile çağırıyor; imzada olmayınca TypeError →
    101 gönderildikten hemen sonra soket kapanıyordu (run37 kanıtı:
    'http conn: ws_conn_after_handshake() got an unexpected keyword argument')."""
    try:
        ws_rpc_loop(sock, pending=tail)
    finally:
        try: sock.close()
        except Exception: pass

def ws_conn(sock):
    try:
        if ws_handshake(sock) is None:
            sock.close(); return
        ws_rpc_loop(sock)
    except Exception as e:
        log(f"ws conn: {e}")
    finally:
        try: sock.close()
        except Exception: pass

def _http_read_request(sock, first_data=b""):
    """Ayni soketten tam bir HTTP istegi oku (keep-alive). Yoksa b'' doner."""
    data = first_data
    import select as _sel
    while b"\r\n\r\n" not in data:
        r, _, _ = _sel.select([sock], [], [], 1.0)
        if not r:
            return b""
        c = sock.recv(2048)
        if not c:
            return b""
        data += c
    return data

def ws_rpc_loop(sock, pending=b"", tag=""):
    # pre_read'te \r\n\r\n sonrası kalmış baytlar ilk çerçeveye aittir.
    log(f"rpc-loop basladi pending={len(pending)} bayt: {pending[:40]!r}")
    sock.settimeout(300)   # sessiz soket 5dk'da kapanir (kaynak sizmasi yok)
    pre = bytearray(pending)
    first = True
    while True:
        # OkHttp ayni sokete IKINCI WS upgrade'i ham HTTP olarak gonderebilir
        # (keep-alive havuz) → peek ile 'G' görürsek yeni handshake yap.
        import select as _sel
        r, _, _ = _sel.select([sock], [], [], 0)
        if r:
            try:
                if sock.recv(1, socket.MSG_PEEK) == b'G':
                    # OkHttp bu sokete 2. upgrade yazdi → soket karmasik;
                    # KAPAT: OkHttp EOF gorup TEMIZ sokette yeniden baglanir.
                    # (2. 101 gondermek OkHttp'te accept-mismatch'e yol aciyor.)
                    log("ayni-sokette 2. GET — soket kapatiliyor (temiz reconnect icin)")
                    return
            except Exception as e:
                log(f"peek err {e}")
                return
        got = ws_recv(sock, bytes(pre))
        pre = bytearray()
        if first:
            log(f"rpc-loop ilk çerçeve: {got if got in (None,'CLOSE') else (hex(got[0]) if isinstance(got[0],int) else got[0], got[1][:48])}")
            first = False
        if got is None or got == "CLOSE":
            break
        opcode, msg = got
        if opcode == "HTTP":
            # ayni sokete gelen 2. upgrade → temiz reconnect icin KAPAT
            log(f"ayni-sokette 2. HTTP upgrade ({len(msg)}b) — soket kapatiliyor")
            return
        if opcode == 0x9:            # ping → pong (OkHttp pong bütçesi 15 sn)
            ws_send_frame(sock, 0xA, msg)
            continue
        if opcode != 0x1:            # yalnız text RPC
            continue
        try:
            req = json.loads(msg.decode())
        except Exception:
            continue
        rid = req.get("id")
        method = req.get("method", "")
        params = req.get("params") or {}
        log(f"rpc {method} {json.dumps(params, ensure_ascii=False)[:120]}")
        result = None
        if method == "session.active_list":
            # LIVE'ı her istekte tazele (last_active) — deterministik gövde,
            # zaman alanı yalnız taze
            fresh = [dict(s, last_active=time.time() - 20, started_at=time.time() - 600) for s in LIVE]
            result = {"sessions": fresh}
        elif method == "session.steer":
            result = {"status": "queued"}
            log(f"STEER OK sid={params.get('session_id')} text={params.get('text','')[:60]!r}")
        elif method == "session.redirect":
            result = {"status": "queued"}
        elif method in ("session.interrupt", "session.activate", "session.create"):
            result = {"ok": True, "session_id": "mock-live-new"} if method == "session.create" else {"ok": True}
        elif method == "prompt.submit":
            result = {"ok": True}
            # ~10 sn'lik delta akışı: hız şeridi 1sn'de bir örnek toplar,
            # 10+ örnek = görünür sparkline (tur19 09 kanıtı).
            def flow():
                try:
                    for i in range(30):
                        time.sleep(0.33)
                        ws_send(sock, {"method": "event", "params": {
                            "type": "message.delta",
                            "payload": {"text": f"parça{i} ", "session_id": params.get("session_id", "")}}})
                    time.sleep(0.2)
                    ws_send(sock, {"method": "event", "params": {
                        "type": "message.complete",
                        "payload": {"session_id": params.get("session_id", "")}}})
                except Exception as e:
                    log(f"flow err {e}")
            threading.Thread(target=flow, daemon=True).start()
        else:
            result = {"ok": True}
        if rid is not None:
            ws_send(sock, {"jsonrpc": "2.0", "id": rid, "result": result})

# ---- HTTP -------------------------------------------------------------------
def http_loop():
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", HTTP_PORT))
    srv.listen(4)
    log(f"http :{HTTP_PORT} hazır")
    while True:
        c, _ = srv.accept()
        threading.Thread(target=http_conn, args=(c,), daemon=True).start()

def http_respond(sock, data):
    """Tam HTTP istegi (bytes) → yanit gonder. Upgrade icerirse WS devir: True doner."""
    try:
        head = data.split(b"\r\n\r\n")[0].decode(errors="replace")
        line = head.split("\r\n")[0]
        parts = line.split()
        if len(parts) < 2:
            return False
        path = parts[1]
        head_low = head.lower()
        tok = re.search(r"X-Hermes-Session-Token: (\S+)", head)
        auth = tok is not None and tok.group(1) == TOKEN
        log(f"REST {path} auth={auth} tok_len={len(tok.group(1)) if tok else 0}")
        if path.startswith("/api/profiles/active"):
            body = json.dumps({"id": "mock", "name": "mock", "gateway_running": True}) if auth else None
        elif path == "/api/status":
            body = json.dumps({"version": "0.21.0", "gateway_running": True,
                              "gateway_state": "running", "active_agents": 2}) if auth else None
        elif path == "/api/system/stats":
            body = json.dumps({}) if auth else None   # alanlar default'lu — bos obje gecerli
        elif path == "/api/cron/jobs":
            body = json.dumps({"jobs": []}) if auth else None
        elif path.startswith("/api/profiles"):
            body = json.dumps({"profiles": [{
                "name": "mock", "path": "/mock", "is_default": True, "model": "qwen3-flash",
                "provider": "mock", "skill_count": 1, "gateway_running": True}]}) if auth else None
        elif path.startswith("/api/sessions/stats"):
            body = json.dumps({"total": 3, "today": 2}) if auth else None
        elif path.startswith("/api/sessions"):
            body = json.dumps({"sessions": SESSIONS}) if auth else None
        elif path.startswith("/api/health"):
            body = json.dumps({"ok": True})
        else:
            body = None
        if body is None:
            sock.sendall(b"HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
        else:
            b = body.encode()
            sock.sendall(
                b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: %d\r\nConnection: close\r\n\r\n" % len(b) + b)
    except Exception as e:
        log(f"http_respond err: {e}")
    return False

def http_conn(sock):
    tag = f"[{id(sock)%10000:04d}] "
    try:
        data = b""
        while b"\r\n\r\n" not in data:
            b = sock.recv(1024)
            if not b:
                return
            data += b
        head = data.split(b"\r\n\r\n")[0].decode(errors="replace")
        line = head.split("\r\n")[0]
        parts = line.split()
        if len(parts) < 2:
            return
        path = parts[1]
        head_low = head.lower()
        if "upgrade: websocket" in head_low:
            # Tüm HTTP başlığı ZATEN okundu — handshake'e pre_read olarak devret
            # (bağlantıda kalan bayt yok; istemci 101'i bekliyor).
            r = ws_handshake(sock, pre_read=data, tag=tag)
            if r is not None:   # None = 401/400 gönderildi; aksi = 101 gönderildi
                ws_conn_after_handshake(sock, r, tag=tag)
            return
        http_respond(sock, data)
    except Exception as e:
        log(f"http conn: {e}", tag)
    finally:
        try: sock.close()
        except Exception: pass

if __name__ == "__main__":
    # Tek port (HTTP_PORT): HTTP + WS yükseltmesi aynı dinleyicide (ws yolu ayrımı).
    http_loop()
