#!/usr/bin/env python3
# Tur-3 kanıt vekili: emülatör uygulama -> 10.0.2.2:9199 -> gerçek 127.0.0.1:9120.
# Uygulamanın gönderdiği her X-Hermes-Session-Token değerini GERÇEK sunucu
# token'ıyla değiştirir (token emülatöre/asla sızmaz). Gerçek 2xx + sunucuda
# inen dosya kanıtı böylece gerçek sunucuya karşı üretilir.
import os, re, sys, urllib.request, urllib.error
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

def server_token():
    for line in open(os.path.expanduser('~/.hermes/.env')):
        m = re.match(r'HERMES_DASHBOARD_SESSION_TOKEN=(.+)', line.strip())
        if m:
            return m.group(1)
    sys.exit('TOKEN_NOT_FOUND')

REAL_TOKEN = server_token()
# 9150 = gateway (dashboard 9120 /api/ws ve /api/files/* sunmuyor, 404/401
# dönüyordu — 260913 emülatör kanıtı). Uygulamanın tüm uçları 9150'de.
UPSTREAM = 'http://127.0.0.1:9150'

class Proxy(BaseHTTPRequestHandler):
    def _ws_bridge(self):
        # WebSocket upgrade: urllib köprü yapamaz — ham socket tüneli. URL ve
        # header'lardaki SAHTE token gerçek sunucu token'ıyla değiştirilir
        # (uygulama ws URL'ini ?token=<profile.token> ile kuruyor).
        import re as _re
        import socket as _sock
        import threading as _th
        path = _re.sub(r'token=[^&]*', 'token=' + REAL_TOKEN, self.path)
        up = _sock.create_connection(('127.0.0.1', 9150), timeout=15)
        head = f"{self.command} {path} HTTP/1.1\r\n".encode()
        for k, v in self.headers.items():
            if k.lower() in ('content-length', 'connection'):
                continue
            if k.lower() == 'host':
                v = '127.0.0.1:9150'
            if k.lower() == 'x-hermes-session-token':
                v = REAL_TOKEN
            head += f"{k}: {v}\r\n".encode()
        head += b"Connection: Upgrade\r\n\r\n"
        up.sendall(head)
        client = self.connection

        def pipe(a, b):
            try:
                while True:
                    d = a.recv(65536)
                    if not d:
                        break
                    b.sendall(d)
            except OSError:
                pass
            finally:
                try:
                    b.shutdown(_sock.SHUT_WR)
                except OSError:
                    pass

        t = _th.Thread(target=pipe, args=(client, up), daemon=True)
        t.start()
        try:
            while True:
                d = up.recv(65536)
                if not d:
                    break
                client.sendall(d)
        except OSError:
            pass
        self.close_connection = True
        log('WS bridge kapandi (upgrade gecirildi)')

    def _relay(self):
        if self.path.startswith('/api/ws'):
            self._ws_bridge()
            return
        body = self.rfile.read(int(self.headers.get('Content-Length', 0) or 0)) \
            if self.headers.get('Content-Length') else None
        req = urllib.request.Request(UPSTREAM + self.path, data=body, method=self.command)
        for k, v in self.headers.items():
            if k.lower() in ('host', 'content-length', 'connection'):
                continue
            if k.lower() == 'x-hermes-session-token':
                v = REAL_TOKEN  # sahte → gerçek
            req.add_header(k, v)
        if 'x-hermes-session-token' not in {k.lower() for k in self.headers}:
            req.add_header('X-Hermes-Session-Token', REAL_TOKEN)
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                data = resp.read()
                self.send_response(resp.status)
                for k, v in resp.getheaders():
                    if k.lower() not in ('transfer-encoding', 'connection', 'content-length'):
                        self.send_header(k, v)
                self.send_header('Content-Length', str(len(data)))
                self.end_headers()
                self.wfile.write(data)
                log(f'{self.command} {self.path.split("?")[0]} -> {resp.status}')
        except urllib.error.HTTPError as e:
            data = e.read()
            self.send_response(e.code)
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            log(f'{self.command} {self.path.split("?")[0]} -> {e.code}')
        except Exception as e:
            self.send_response(502); self.end_headers()
            log(f'{self.command} {self.path} -> ERR {e}')

    do_GET = do_POST = do_PUT = do_DELETE = _relay
    def log_message(self, *a): pass

if __name__ == '__main__':
    # Log hem stdout'a hem /tmp/proxy.log'a (kanıt için; tek satır log).
    _log = open('/tmp/proxy.log', 'a', buffering=1)

    def log(msg):
        print(msg, flush=True)
        _log.write(msg + '\n')

    globals()['log'] = log
    log('proxy 127.0.0.1:9199 -> 9120 (token swap) STARTED')
    ThreadingHTTPServer(('0.0.0.0', 9199), Proxy).serve_forever()
