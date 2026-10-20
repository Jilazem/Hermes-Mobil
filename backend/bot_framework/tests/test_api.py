"""HTTP API testleri — gerçek sunucu, gerçek soket (urllib), uçtan uca akış."""
import json
import os
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from typing import Any, Tuple

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from bot_framework.api import make_server  # noqa: E402
from bot_framework.store import BotStore  # noqa: E402


class HttpCase(unittest.TestCase):
    token = None

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.db_path = os.path.join(self.tmp.name, "bots.db")
        self.store = BotStore(self.db_path)
        self.httpd = make_server(self.store, host="127.0.0.1", port=0, token=self.token)
        self.base = "http://127.0.0.1:%d" % self.httpd.server_address[1]
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.httpd.shutdown()
        self.httpd.server_close()
        self.thread.join(timeout=5)
        self.store.close()
        self.tmp.cleanup()

    def call(self, method: str, path: str, payload: Any = None, token: Any = None) -> Tuple[int, Any]:
        data = None
        headers = {}
        if payload is not None:
            data = json.dumps(payload).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if token:
            headers["Authorization"] = "Bearer %s" % token
        req = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                raw = resp.read().decode("utf-8")
                return resp.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as exc:
            raw = exc.read().decode("utf-8")
            return exc.code, (json.loads(raw) if raw else None)


class ApiTests(HttpCase):
    def test_health(self):
        status, body = self.call("GET", "/health")
        self.assertEqual(status, 200)
        self.assertTrue(body["ok"])
        self.assertEqual(body["bots"], 0)

    def test_templates_listed(self):
        status, body = self.call("GET", "/api/templates")
        self.assertEqual(status, 200)
        self.assertGreaterEqual(len(body["templates"]), 3)

    def test_tools_catalog_listed(self):
        status, body = self.call("GET", "/api/tools")
        self.assertEqual(status, 200)
        keys = {t["key"] for t in body["tools"]}
        self.assertIn("terminal", keys)
        self.assertIn("web_search", keys)
        for tool in body["tools"]:
            self.assertTrue(tool["label"])

    def test_create_list_get_patch_delete(self):
        status, body = self.call(
            "POST",
            "/api/bots",
            {
                "name": "HTTP Bot",
                "persona": "HTTP üzerinden oluşturuldu.",
                "emoji": "🌐",
                "tools": ["web_search"],
                "think_mode": "low",
            },
        )
        self.assertEqual(status, 201)
        bot_id = body["bot"]["id"]
        self.assertEqual(body["bot"]["name"], "HTTP Bot")

        status, body = self.call("GET", "/api/bots")
        self.assertEqual(status, 200)
        self.assertEqual(len(body["bots"]), 1)

        status, body = self.call("GET", "/api/bots/%s" % bot_id)
        self.assertEqual(status, 200)
        self.assertEqual(body["bot"]["persona"], "HTTP üzerinden oluşturuldu.")

        status, body = self.call("PATCH", "/api/bots/%s" % bot_id, {"name": "Yeni Ad"})
        self.assertEqual(status, 200)
        self.assertEqual(body["bot"]["name"], "Yeni Ad")
        self.assertEqual(body["bot"]["persona"], "HTTP üzerinden oluşturuldu.")

        status, body = self.call("DELETE", "/api/bots/%s" % bot_id)
        self.assertEqual(status, 204)
        status, body = self.call("GET", "/api/bots/%s" % bot_id)
        self.assertEqual(status, 404)
        self.assertEqual(body["error"], "bot_not_found")

    def test_create_validation_error(self):
        status, body = self.call("POST", "/api/bots", {"name": "", "persona": "x"})
        self.assertEqual(status, 400)
        self.assertEqual(body["error"], "name_blank")

    def test_create_with_secret_rejected(self):
        status, body = self.call(
            "POST", "/api/bots", {"name": "B", "persona": "p", "api_key": "sk-1"}
        )
        self.assertEqual(status, 400)
        self.assertEqual(body["error"], "secret_not_allowed")

    def test_bad_json(self):
        req = urllib.request.Request(
            self.base + "/api/bots",
            data=b"{bozuk",
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            urllib.request.urlopen(req, timeout=10)
            self.fail("bozuk JSON kabul edildi")
        except urllib.error.HTTPError as exc:
            self.assertEqual(exc.code, 400)
            body = json.loads(exc.read().decode("utf-8"))
            self.assertEqual(body["error"], "invalid_json")

    def test_from_template(self):
        status, body = self.call(
            "POST", "/api/bots/from-template", {"key": "arastirma", "overrides": {"name": "Araştırma 2"}}
        )
        self.assertEqual(status, 201)
        self.assertEqual(body["bot"]["name"], "Araştırma 2")
        self.assertIn("web_search", body["bot"]["tools"])

    def test_unknown_template(self):
        status, body = self.call("POST", "/api/bots/from-template", {"key": "yok"})
        self.assertEqual(status, 400)
        self.assertEqual(body["error"], "template_unknown")

    def test_export_import_over_http(self):
        _, created = self.call(
            "POST", "/api/bots", {"name": "Taşınan", "persona": "taşınan", "tools": ["vision"]}
        )
        bot_id = created["bot"]["id"]

        status, export = self.call("GET", "/api/bots/%s/export" % bot_id)
        self.assertEqual(status, 200)
        self.assertEqual(export["format"], "hermes-bot")
        self.assertNotIn("id", export["bot"])

        status, imported = self.call("POST", "/api/bots/import", export)
        self.assertEqual(status, 201)
        self.assertNotEqual(imported["bot"]["id"], bot_id)
        self.assertEqual(imported["bot"]["name"], "Taşınan")

    def test_import_rejects_secret(self):
        _, created = self.call("POST", "/api/bots", {"name": "A", "persona": "p"})
        _, export = self.call("GET", "/api/bots/%s/export" % created["bot"]["id"])
        export["bot"]["token"] = "abc"
        status, body = self.call("POST", "/api/bots/import", export)
        self.assertEqual(status, 400)
        self.assertEqual(body["error"], "secret_not_allowed")

    def test_chat_e2e_persona_visible(self):
        """KABUL: oluştur → sohbet → persona görünür."""
        persona = "Sen bir test botusun; yanıtların kısa ve ölçülü olsun."
        status, created = self.call(
            "POST",
            "/api/bots",
            {
                "name": "E2E Botu",
                "emoji": "🧪",
                "persona": persona,
                "tools": ["web_search", "hesap"],
                "model": "local-e2e",
                "think_mode": "high",
                "welcome_message": "E2E karşılama",
            },
        )
        self.assertEqual(status, 201)
        bot_id = created["bot"]["id"]

        status, chat = self.call(
            "POST", "/api/bots/%s/chat" % bot_id, {"message": "Merhaba bot"}
        )
        self.assertEqual(status, 200)
        self.assertIn(persona, chat["system_prompt"])
        self.assertIn("E2E Botu", chat["reply"])
        self.assertIn("Merhaba bot", chat["reply"])
        self.assertEqual(chat["messages"][0]["role"], "system")
        self.assertIn(persona, chat["messages"][0]["content"])
        session_id = chat["session_id"]

        status, session = self.call("GET", "/api/sessions/%s" % session_id)
        self.assertEqual(status, 200)
        self.assertEqual(session["session"]["bot_id"], bot_id)
        self.assertIn(persona, session["session"]["system_prompt"])
        self.assertEqual(session["session"]["messages"][0]["role"], "system")

        # aynı oturumda ikinci tur
        status, chat2 = self.call(
            "POST",
            "/api/bots/%s/chat" % bot_id,
            {"message": "İkinci tur", "session_id": session_id},
        )
        self.assertEqual(status, 200)
        self.assertEqual(chat2["session_id"], session_id)
        self.assertGreater(len(chat2["messages"]), len(chat["messages"]))

        status, sessions = self.call("GET", "/api/sessions?bot_id=%s" % bot_id)
        self.assertEqual(status, 200)
        self.assertEqual(len(sessions["sessions"]), 1)

    def test_chat_unknown_bot(self):
        status, body = self.call("POST", "/api/bots/yok/chat", {"message": "selam"})
        self.assertEqual(status, 404)
        self.assertEqual(body["error"], "bot_not_found")

    def test_chat_blank_message(self):
        _, created = self.call("POST", "/api/bots", {"name": "B", "persona": "p"})
        status, body = self.call(
            "POST", "/api/bots/%s/chat" % created["bot"]["id"], {"message": "  "}
        )
        self.assertEqual(status, 400)
        self.assertEqual(body["error"], "message_blank")

    def test_chat_session_mismatch(self):
        _, a = self.call("POST", "/api/bots", {"name": "A", "persona": "p"})
        _, b = self.call("POST", "/api/bots", {"name": "B", "persona": "p"})
        _, chat = self.call("POST", "/api/bots/%s/chat" % a["bot"]["id"], {"message": "x"})
        status, body = self.call(
            "POST",
            "/api/bots/%s/chat" % b["bot"]["id"],
            {"message": "x", "session_id": chat["session_id"]},
        )
        self.assertEqual(status, 400)
        self.assertEqual(body["error"], "session_mismatch")

    def test_unknown_path(self):
        status, body = self.call("GET", "/api/yok")
        self.assertEqual(status, 404)
        self.assertEqual(body["error"], "not_found")


class TokenTests(HttpCase):
    token = "test-token-123"

    def test_unauthorized_without_token(self):
        status, body = self.call("GET", "/api/bots")
        self.assertEqual(status, 401)
        self.assertEqual(body["error"], "unauthorized")

    def test_authorized_with_token(self):
        status, body = self.call("GET", "/api/bots", token=self.token)
        self.assertEqual(status, 200)

    def test_wrong_token(self):
        status, _ = self.call("GET", "/api/bots", token="yanlis")
        self.assertEqual(status, 401)


if __name__ == "__main__":
    unittest.main()
