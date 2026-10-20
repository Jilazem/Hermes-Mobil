"""V5-5 — uçtan uca kabul testi: şablon → bot oluştur → sohbet → persona görünür
→ dışa aktar → içe aktar → yeniden başlat sonrası kalıcılık → sil.

Gerçek HTTP sunucusu + gerçek SQLite dosyası kullanır (mock yok). Bu, kartın
"en az 1 bot e2e (oluştur → sohbet → persona görünür)" kabul ölçütünü karşılar.
"""
import json
import os
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from bot_framework.api import make_server  # noqa: E402
from bot_framework.store import BotStore  # noqa: E402


class E2ECase(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.db_path = os.path.join(self.tmp.name, "bots.db")
        self._start()

    def _start(self):
        self.store = BotStore(self.db_path)
        self.httpd = make_server(self.store, host="127.0.0.1", port=0)
        self.base = "http://127.0.0.1:%d" % self.httpd.server_address[1]
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()

    def _stop(self):
        self.httpd.shutdown()
        self.httpd.server_close()
        self.thread.join(timeout=5)
        self.store.close()

    def tearDown(self):
        try:
            self._stop()
        finally:
            self.tmp.cleanup()

    def call(self, method, path, payload=None):
        data = json.dumps(payload).encode("utf-8") if payload is not None else None
        headers = {"Content-Type": "application/json"} if data else {}
        req = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                raw = resp.read().decode("utf-8")
                return resp.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as exc:
            raw = exc.read().decode("utf-8")
            return exc.code, (json.loads(raw) if raw else None)

    def test_full_bot_lifecycle(self):
        # 1) Şablon listesi
        status, body = self.call("GET", "/api/templates")
        self.assertEqual(status, 200)
        self.assertIn("kod", {t["key"] for t in body["templates"]})

        # 2) Şablondan bot oluştur (ad ezilir)
        status, body = self.call(
            "POST", "/api/bots/from-template", {"key": "kod", "overrides": {"name": "E2E Kod Botu"}}
        )
        self.assertEqual(status, 201)
        bot = body["bot"]
        bot_id = bot["id"]
        self.assertEqual(bot["name"], "E2E Kod Botu")
        self.assertIn("terminal", bot["tools"])

        # 3) Botla sohbet — ilk tur oturumu açar, persona enjekte edilir
        status, body = self.call("POST", "/api/bots/%s/chat" % bot_id, {"message": "Merhaba bot"})
        self.assertEqual(status, 200)
        session_id = body["session_id"]
        self.assertIn("[BOT PERSONA]", body["system_prompt"])
        self.assertIn("E2E Kod Botu", body["system_prompt"])
        self.assertEqual(body["messages"][0]["role"], "system")
        self.assertIn("kıdemli bir yazılım mühendisi", body["messages"][0]["content"])
        self.assertEqual(body["messages"][1]["role"], "assistant")  # karşılama
        self.assertEqual(body["messages"][-2], {"role": "user", "content": "Merhaba bot"})
        self.assertEqual(body["messages"][-1]["role"], "assistant")

        # 4) Aynı oturumda ikinci tur — geçmiş korunur
        status, body = self.call(
            "POST",
            "/api/bots/%s/chat" % bot_id,
            {"message": "İkinci tur", "session_id": session_id},
        )
        self.assertEqual(status, 200)
        self.assertEqual(body["session_id"], session_id)
        self.assertEqual(len(body["messages"]), 6)  # system, welcome, u1,a1, u2,a2

        # 5) Sunucudan oturumu oku — persona diske de yazılmış
        status, body = self.call("GET", "/api/sessions/%s" % session_id)
        self.assertEqual(status, 200)
        stored = body["session"]
        self.assertIn("[BOT PERSONA]", stored["system_prompt"])
        self.assertEqual(stored["messages"][0]["role"], "system")
        self.assertEqual(len(stored["messages"]), 6)

        # 6) Dışa aktar → içe aktar (yeni kimlik, aynı persona/araçlar)
        status, export = self.call("GET", "/api/bots/%s/export" % bot_id)
        self.assertEqual(status, 200)
        self.assertEqual(export["format"], "hermes-bot")
        self.assertNotIn("id", export["bot"])
        self.assertNotIn("api_key", json.dumps(export))
        status, body = self.call("POST", "/api/bots/import", export)
        self.assertEqual(status, 201)
        imported = body["bot"]
        self.assertNotEqual(imported["id"], bot_id)
        self.assertEqual(imported["name"], "E2E Kod Botu")
        self.assertEqual(imported["tools"], bot["tools"])
        self.assertEqual(imported["persona"], bot["persona"])

        # 7) Yeniden başlatma benzeri: sunucuyu kapat, aynı DB ile aç, veri duruyor
        self._stop()
        self._start()
        status, body = self.call("GET", "/api/bots")
        self.assertEqual(status, 200)
        names = [b["name"] for b in body["bots"]]
        self.assertEqual(names.count("E2E Kod Botu"), 2)
        status, body = self.call("GET", "/api/sessions/%s" % session_id)
        self.assertEqual(status, 200)
        self.assertEqual(len(body["session"]["messages"]), 6)

        # 8) Sil — bot ve oturumları gider
        status, _ = self.call("DELETE", "/api/bots/%s" % bot_id)
        self.assertEqual(status, 204)
        status, body = self.call("GET", "/api/bots/%s" % bot_id)
        self.assertEqual(status, 404)
        status, body = self.call("GET", "/api/sessions/%s" % session_id)
        self.assertEqual(status, 404)

    def test_persona_isolation_between_bots(self):
        """Aynı mesaj, iki farklı persona → iki farklı sistem istemi."""
        ids = []
        for key in ("asistan", "arastirma"):
            status, body = self.call("POST", "/api/bots/from-template", {"key": key})
            self.assertEqual(status, 201)
            ids.append(body["bot"]["id"])
        prompts = []
        for bot_id in ids:
            status, body = self.call("POST", "/api/bots/%s/chat" % bot_id, {"message": "test"})
            self.assertEqual(status, 200)
            prompts.append(body["system_prompt"])
        self.assertNotEqual(prompts[0], prompts[1])
        self.assertIn("Genel Asistan", prompts[0])
        self.assertIn("Araştırmacı", prompts[1])

    def test_secret_never_accepted(self):
        status, body = self.call(
            "POST",
            "/api/bots",
            {"name": "Sır Botu", "persona": "p", "api_key": "sk-test"},
        )
        self.assertEqual(status, 400)
        self.assertEqual(body["error"], "secret_not_allowed")


if __name__ == "__main__":
    unittest.main()
