import json
from pathlib import Path
import tempfile
import threading
import unittest
import urllib.request
import urllib.error
from types import SimpleNamespace
from bot_framework.dashboard_hub import DashboardEngine, dashboard_token, make_hub
from bot_framework.store import BotStore
from bot_framework.models import Bot
from bot_framework.chat import ChatError


class FakeRPC:
    calls = []
    fail = False
    def __init__(self, url, token): self.socket = SimpleNamespace(settimeout=lambda _: None)
    def call(self, method, params):
        self.calls.append((method, params))
        return {'session_id':'live-1', 'session_key':'stored-1'} if method.startswith('session.') else {}
    def answer(self, runtime):
        if self.fail: raise TimeoutError('uncertain')
        return 'Gerçek istemci sözleşmesi için kontrollü yanıt'
    def close(self): pass


class DashboardHubTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.token = Path(self.temp.name)/'env'
        self.token.write_text('OTHER_KEY=unrelated\nHERMES_DASHBOARD_SESSION_TOKEN="unit-token"\n')
        self.store = BotStore(':memory:')
        FakeRPC.calls = []; FakeRPC.fail = False
    def tearDown(self): self.store.close(); self.temp.cleanup()

    def test_token_reads_only_named_dashboard_key(self):
        self.assertEqual('unit-token', dashboard_token(self.token))

    def test_canonical_conversation_is_resumed_not_recreated(self):
        bot = self.store.save_bot(Bot(name='Kontrol', persona='Açık ve kısa yaz.'))
        engine = DashboardEngine(self.store, self.token, 'ws://unused', FakeRPC)
        sid = engine.open_session(bot)['id']
        engine.send(sid, 'ilk'); engine.send(sid, 'ikinci')
        self.assertEqual(['session.create','prompt.submit','session.resume','prompt.submit'], [x[0] for x in FakeRPC.calls])
        self.assertEqual('stored-1', FakeRPC.calls[2][1]['session_id'])
        self.assertEqual(5, len(self.store.get_session(sid)['messages']))

    def test_uncertain_submission_is_never_replayed(self):
        bot = self.store.save_bot(Bot(name='Kontrol', persona='Açık ve kısa yaz.'))
        engine = DashboardEngine(self.store, self.token, 'ws://unused', FakeRPC)
        sid = engine.open_session(bot)['id']; FakeRPC.fail = True
        with self.assertRaises(TimeoutError): engine.send(sid, 'bir kez')
        with self.assertRaises(ChatError): engine.send(sid, 'aynı işi tekrar etme')
        self.assertEqual(1, sum(m == 'prompt.submit' for m, _ in FakeRPC.calls))

    def test_http_requires_dashboard_header_and_preserves_bot_namespace(self):
        server = make_hub(self.store, self.token, 'ws://unused', 0, FakeRPC)
        thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start()
        base = 'http://127.0.0.1:' + str(server.server_port)
        try:
            for headers in ({}, {'Authorization':'Bearer unit-token'}, {'X-Hermes-Session-Token':'wrong'}):
                with self.assertRaises(urllib.error.HTTPError) as error:
                    urllib.request.urlopen(urllib.request.Request(base+'/mobile-api/bot/api/bots', headers=headers))
                self.assertEqual(401, error.exception.code)
            req = urllib.request.Request(base+'/mobile-api/bot/api/bots', headers={'X-Hermes-Session-Token':'unit-token'})
            with urllib.request.urlopen(req) as response: self.assertEqual([], json.load(response)['bots'])
            self.token.write_text('HERMES_DASHBOARD_SESSION_TOKEN=new-token\n')
            with self.assertRaises(urllib.error.HTTPError): urllib.request.urlopen(req)
        finally: server.shutdown(); thread.join(); server.server_close()
