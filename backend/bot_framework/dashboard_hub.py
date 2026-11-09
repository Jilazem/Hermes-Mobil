"""Loopback-only Bot API adapter for the existing Hermes Dashboard.

The Dashboard token stays in its existing env file. No provider configuration,
Hermes runtime installation or model selection is changed by this service.
"""
from __future__ import annotations
import argparse
import hmac
import json
import os
from pathlib import Path
import sqlite3
import threading
import time
import urllib.parse
from .mobile_assets import read_configured_models, serve_ema
import urllib.request
import urllib.error
import urllib.parse
import uuid
from http.server import ThreadingHTTPServer
from .api import Api, Handler
from .chat import ChatEngine, ChatError
from .store import BotStore


def dashboard_token(path: Path) -> str:
    for line in path.read_text().splitlines():
        key, separator, value = line.strip().removeprefix('export ').partition('=')
        if separator and key.strip() == 'HERMES_DASHBOARD_SESSION_TOKEN':
            return value.strip().strip('\"\'')
    raise RuntimeError('Dashboard session token is not configured')


class DashboardRPC:
    def __init__(self, url: str, token: str):
        import websocket
        # The deployed Dashboard accepts WebSocket auth in its token query parameter.
        # This connection stays on loopback; this adapter never logs the URL or headers.
        url = url + ('&' if '?' in url else '?') + urllib.parse.urlencode({'token':token})
        self.socket = websocket.create_connection(url, timeout=12, suppress_origin=True,
            header=['X-Hermes-Session-Token: ' + token], enable_multithread=True)
        self.events = []

    def call(self, method, params):
        request_id = uuid.uuid4().hex
        self.socket.send(json.dumps(dict(jsonrpc='2.0', id=request_id, method=method, params=params)))
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            frame = json.loads(self.socket.recv())
            if frame.get('id') == request_id:
                if frame.get('error'):
                    raise RuntimeError('Dashboard rejected ' + method)
                return frame.get('result') or {}
            if frame.get('method') == 'event': self.events.append(frame.get('params', {}))
        raise TimeoutError('Dashboard request timeout')

    def answer(self, runtime):
        deadline = time.monotonic() + 120
        text = []
        while time.monotonic() < deadline:
            event = self.events.pop(0) if self.events else json.loads(self.socket.recv()).get('params', {})
            if event.get('session_id') != runtime: continue
            payload = event.get('payload') or {}
            if event.get('type') == 'message.delta': text.append(payload.get('text') or payload.get('delta') or '')
            if event.get('type') == 'message.complete':
                result = payload.get('text') or payload.get('content') or ''.join(text)
                if not isinstance(result, str) or not result.strip(): raise RuntimeError('Empty Dashboard answer')
                return result
            if event.get('type') in ('error', 'turn.error'): raise RuntimeError('Dashboard generation failed')
        raise TimeoutError('Dashboard answer timeout; the original task may still run')

    def close(self): self.socket.close()


class DashboardEngine(ChatEngine):
    """Keeps the canonical gateway conversation and never replays an uncertain turn."""
    def __init__(self, store, token_file, ws_url, rpc_factory=DashboardRPC):
        super().__init__(store, responder=self)
        self.token_file, self.ws_url, self.rpc_factory = token_file, ws_url, rpc_factory
        self.local = threading.local()
        self.locks = {}
        self.lock = threading.Lock()
        with store._lock:
            store._db.execute('CREATE TABLE IF NOT EXISTS mobile_gateway_sessions (id TEXT PRIMARY KEY, stored TEXT NOT NULL, state TEXT NOT NULL)')
            store._db.commit()

    def send(self, session_id, text, now=None):
        with self.lock: lock = self.locks.setdefault(session_id, threading.Lock())
        with lock:
            self.local.session_id = session_id
            return super().send(session_id, text, now)

    def binding(self, session_id):
        with self.store._lock:
            return self.store._db.execute('SELECT stored,state FROM mobile_gateway_sessions WHERE id=?', (session_id,)).fetchone()

    def save_binding(self, session_id, stored, state):
        with self.store._lock:
            self.store._db.execute('INSERT OR REPLACE INTO mobile_gateway_sessions VALUES (?,?,?)', (session_id,stored,state))
            self.store._db.commit()

    def reply(self, bot, messages):
        session_id = self.local.session_id
        binding = self.binding(session_id)
        if binding and binding['state'] != 'ready':
            raise ChatError('outcome_unknown', 'Önceki tur Dashboard üzerinde kontrol edilmeli; yeniden gönderilmedi.')
        rpc = self.rpc_factory(self.ws_url, getattr(self.local, 'auth_token', None) or dashboard_token(self.token_file))
        try:
            params = {'session_id': binding['stored'], 'omit_messages': True} if binding else {'title': 'Mobil · ' + bot.name}
            if bot.profile: params['profile'] = bot.profile
            result = rpc.call('session.resume' if binding else 'session.create', params)
            runtime = result.get('session_id') or result.get('id')
            stored = result.get('session_key') or result.get('stored_session_id') or result.get('resumed') or (binding['stored'] if binding else runtime)
            if not isinstance(runtime, str) or not runtime or not isinstance(stored, str) or not stored:
                raise RuntimeError('Dashboard session binding not confirmed')
            prompt = messages[-1]['content']
            if not binding:
                prompt = ('Bu görüşmedeki rol ve üslup talimatları:\n' + messages[0]['content'] +
                          '\nTelefon işlemlerinde mevcut cihaz izinlerine uy. Yapmadığın eylemi yapılmış gösterme.\n\nKullanıcının isteği:\n' + prompt)
            self.save_binding(session_id, stored, 'inflight')
            rpc.socket.settimeout(130)
            rpc.call('prompt.submit', {'session_id': runtime, 'text': prompt})
            answer = rpc.answer(runtime)
            self.save_binding(session_id, stored, 'ready')
            return answer
        finally: rpc.close()


def verify_with_dashboard(base, supplied):
    if not supplied or any(ord(c)<32 for c in supplied): return False
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs): return None
    opener=urllib.request.build_opener(NoRedirect)
    url=base.rstrip('/')+'/api/sessions'
    # HEAD crosses the same authentication gate without running the expensive
    # conversation-list handler. A 405 is expected after successful auth.
    def head(headers):
        try:
            with opener.open(urllib.request.Request(url,method='HEAD',headers=headers),timeout=5) as response:
                return response.status
        except urllib.error.HTTPError as error:return error.code
    if head({}) not in (401,403): raise RuntimeError('Dashboard authentication gate was not confirmed')
    return head({'X-Hermes-Session-Token':supplied}) in (200,204,405)


def make_hub(store, token_file, ws_url, port=19760, rpc_factory=DashboardRPC, auth_check=None, model_dir=None, models_loader=None):
    engine = DashboardEngine(store, token_file, ws_url, rpc_factory)
    class HubHandler(Handler):
        def _authorized(self):
            supplied = self.headers.get('X-Hermes-Session-Token', '')
            if auth_check is not None: return auth_check(supplied)
            try: expected = dashboard_token(token_file)
            except (OSError, RuntimeError): return False
            return bool(expected) and hmac.compare_digest(supplied.encode('utf-8'), expected.encode('utf-8'))

        def _route(self, method):
            try: authorized=self._authorized()
            except Exception:
                self._error(503, 'dashboard_unavailable', 'Dashboard erişimi doğrulanamadı.'); return
            if not authorized:
                self._error(401, 'unauthorized', 'Dashboard tokenı gerekli.'); return
            self.api.engine.local.auth_token = self.headers.get('X-Hermes-Session-Token', '')
            if method == 'GET' and self.path == '/mobile-api/models':
                try:
                    origin=urllib.parse.urlsplit(ws_url)
                    if models_loader is not None: result=models_loader()
                    else:
                        if origin.hostname not in ('127.0.0.1','localhost','::1'): raise ValueError('Local Dashboard required')
                        url=urllib.parse.urlunsplit(('https' if origin.scheme=='wss' else 'http',origin.netloc,'/api/config','',''))
                        result=read_configured_models(url,self.api.engine.local.auth_token)
                    self._send(200,result)
                except Exception:self._error(503,'inventory_unavailable','Kayıtlı model listesi okunamadı.')
                return
            prefix_ema='/mobile-api/ema/model/'
            if method == 'GET' and self.path.startswith(prefix_ema):
                serve_ema(self,model_dir,self.path[len(prefix_ema):]); return
            if method == 'GET' and self.path == '/mobile-api/capabilities':
                self._send(200, dict(api_version=1, bots=True, devices=True, phone='native', mola='local')); return
            if method == 'GET' and self.path in ('/mobile-api/devices', '/mobile-api/phone/status'):
                try:
                    with urllib.request.urlopen('http://127.0.0.1:9181/status', timeout=3) as response:
                        state = json.load(response)
                    self._send(200, dict(devices=[], phone=state)); return
                except Exception:
                    self._send(200, dict(devices=[], phone=dict(connected=False))); return
            prefix = '/mobile-api/bot'
            if self.path.startswith(prefix + '/api/'):
                self.path = self.path[len(prefix):]
                return super()._route(method)
            self._error(404, 'not_found', 'Mobil çalışma alanı yolu bulunamadı.')
    HubHandler.api = Api(store, engine)
    return ThreadingHTTPServer(('127.0.0.1', port), HubHandler)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--db', required=True)
    parser.add_argument('--token-file', type=Path, default=Path.home()/'.hermes/.env')
    parser.add_argument('--dashboard-ws', default='ws://127.0.0.1:9120/api/ws')
    parser.add_argument('--port', type=int, default=19760)
    parser.add_argument('--dashboard-http', default='')
    parser.add_argument('--ema-model-dir', type=Path, default=Path.home()/'hermes-local/mobile-workspace/ema-model')
    args = parser.parse_args()
    os.umask(0o077)
    if not args.dashboard_http: dashboard_token(args.token_file)
    store = BotStore(args.db)
    checker=(lambda supplied: verify_with_dashboard(args.dashboard_http,supplied)) if args.dashboard_http else None
    try: make_hub(store, args.token_file, args.dashboard_ws, args.port, auth_check=checker, model_dir=args.ema_model_dir).serve_forever()
    finally: store.close()


if __name__ == '__main__': main()
