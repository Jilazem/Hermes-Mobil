"""Depo testleri: kalıcılık, güncelleme, silme, içe/dışa aktarma."""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from bot_framework.models import Bot, BotValidationError  # noqa: E402
from bot_framework.store import BotStore, import_payload  # noqa: E402


class BotStoreTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = os.path.join(self.tmp.name, "bots.db")
        self.store = BotStore(self.path)

    def tearDown(self):
        self.store.close()
        self.tmp.cleanup()

    def test_save_and_reload_persists(self):
        bot = Bot.create(name="Kalıcı", persona="Kalıcı persona", tools=["terminal"], now=10)
        self.store.save_bot(bot, now=10)
        self.store.close()

        reopened = BotStore(self.path)
        try:
            bots = reopened.list_bots()
            self.assertEqual(len(bots), 1)
            self.assertEqual(bots[0].name, "Kalıcı")
            self.assertEqual(bots[0].persona, "Kalıcı persona")
            self.assertEqual(bots[0].tools, ["terminal"])
            self.assertEqual(bots[0].id, bot.id)
        finally:
            reopened.close()
        self.store = BotStore(self.path)  # tearDown için yeniden aç

    def test_update_keeps_created_at(self):
        bot = Bot.create(name="A", persona="p", now=100)
        self.store.save_bot(bot, now=100)
        bot.name = "B"
        updated = self.store.save_bot(bot, now=200)
        self.assertEqual(updated.created_at, 100)
        self.assertEqual(updated.updated_at, 200)
        fetched = self.store.get_bot(bot.id)
        assert fetched is not None
        self.assertEqual(fetched.name, "B")
        self.assertEqual(len(self.store.list_bots()), 1)

    def test_invalid_bot_not_written(self):
        bot = Bot.create(name="A", persona="p")
        bot.name = ""
        with self.assertRaises(BotValidationError):
            self.store.save_bot(bot)
        self.assertEqual(self.store.list_bots(), [])

    def test_delete_removes_bot_and_sessions(self):
        bot = Bot.create(name="A", persona="p", welcome_message="selam")
        self.store.save_bot(bot)
        self.store.save_session(
            {
                "id": "s1",
                "bot_id": bot.id,
                "bot_name": bot.name,
                "system_prompt": "sys",
                "messages": [],
                "created_at": 1,
                "updated_at": 1,
            }
        )
        self.assertTrue(self.store.delete_bot(bot.id))
        self.assertIsNone(self.store.get_bot(bot.id))
        self.assertIsNone(self.store.get_session("s1"))
        self.assertFalse(self.store.delete_bot(bot.id))

    def test_export_import_roundtrip_new_id(self):
        bot = Bot.create(name="Taşınan", persona="taşınan persona", tools=["vision"], now=5)
        self.store.save_bot(bot, now=5)
        export = bot.to_export()

        imported = self.store.save_bot(import_payload(export), now=6)
        self.assertNotEqual(imported.id, bot.id)
        self.assertEqual(imported.name, "Taşınan")
        self.assertEqual(imported.tools, ["vision"])
        self.assertEqual(len(self.store.list_bots()), 2)

    def test_import_rejects_foreign_format(self):
        with self.assertRaises(BotValidationError) as ctx:
            import_payload({"format": "openai-gpt", "version": 1, "bot": {}})
        self.assertEqual(ctx.exception.code, "format_unknown")

    def test_import_rejects_secret(self):
        export = Bot.create(name="A", persona="p").to_export()
        export["bot"]["api_key"] = "sk-123"
        with self.assertRaises(BotValidationError) as ctx:
            import_payload(export)
        self.assertEqual(ctx.exception.code, "secret_not_allowed")

    def test_sessions_list_by_bot(self):
        a = self.store.save_bot(Bot.create(name="A", persona="p"))
        b = self.store.save_bot(Bot.create(name="B", persona="p"))
        for sid, bot in (("s1", a), ("s2", b), ("s3", a)):
            self.store.save_session(
                {
                    "id": sid,
                    "bot_id": bot.id,
                    "bot_name": bot.name,
                    "system_prompt": "sys",
                    "messages": [],
                    "created_at": 1,
                    "updated_at": 1,
                }
            )
        self.assertEqual(len(self.store.list_sessions(a.id)), 2)
        self.assertEqual(len(self.store.list_sessions()), 3)


if __name__ == "__main__":
    unittest.main()
