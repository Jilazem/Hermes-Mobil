"""Persona enjeksiyonu + sohbet motoru testleri."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from bot_framework.chat import ChatEngine, ChatError, LocalResponder  # noqa: E402
from bot_framework.models import Bot  # noqa: E402
from bot_framework.persona import build_system_prompt, system_message  # noqa: E402
from bot_framework.store import BotStore  # noqa: E402
from bot_framework.templates import bot_from_template, get_template, list_templates  # noqa: E402


class PersonaTests(unittest.TestCase):
    def test_prompt_contains_name_persona_tools(self):
        bot = Bot.create(
            name="Kod Uzmanı",
            persona="Test/build olmadan tamam deme.",
            tools=["read_file", "terminal"],
            model="local-x",
            think_mode="medium",
        )
        prompt = build_system_prompt(bot)
        self.assertIn("Kod Uzmanı", prompt)
        self.assertIn("Test/build olmadan tamam deme.", prompt)
        self.assertIn("read_file", prompt)
        self.assertIn("terminal", prompt)
        self.assertIn("local-x", prompt)
        self.assertIn("Think modu: medium", prompt)
        self.assertIn("Token, şifre", prompt)

    def test_prompt_without_tools_says_none(self):
        bot = Bot.create(name="Sade", persona="Sade bot")
        prompt = build_system_prompt(bot)
        self.assertIn("Araç çağırmadan yanıtla", prompt)

    def test_system_message_role(self):
        msg = system_message(Bot.create(name="A", persona="p"))
        self.assertEqual(msg["role"], "system")
        self.assertIn("A", msg["content"])

    def test_prompt_deterministic(self):
        bot = Bot.create(name="A", persona="p", now=1)
        self.assertEqual(build_system_prompt(bot), build_system_prompt(bot))


class TemplatesTests(unittest.TestCase):
    def test_at_least_three_templates(self):
        templates = list_templates()
        self.assertGreaterEqual(len(templates), 3)
        keys = {t["key"] for t in templates}
        self.assertTrue({"asistan", "kod", "arastirma"}.issubset(keys))
        for tpl in templates:
            for field in ("key", "name", "emoji", "summary", "persona", "tools", "think_mode"):
                self.assertIn(field, tpl)

    def test_from_template_creates_valid_bot(self):
        bot = bot_from_template("kod")
        self.assertEqual(bot.name, "Kod Uzmanı")
        self.assertIn("terminal", bot.tools)

    def test_from_template_override(self):
        bot = bot_from_template("asistan", overrides={"name": "Benim Asistanım", "think_mode": "high"})
        self.assertEqual(bot.name, "Benim Asistanım")
        self.assertEqual(bot.think_mode, "high")

    def test_unknown_template(self):
        with self.assertRaises(Exception) as ctx:
            bot_from_template("yok")
        self.assertEqual(getattr(ctx.exception, "code", ""), "template_unknown")

    def test_template_list_is_copy(self):
        first = list_templates()
        first[0]["name"] = "BOZUK"
        self.assertNotEqual(list_templates()[0]["name"], "BOZUK")
        self.assertIsNotNone(get_template("asistan"))


class ChatTests(unittest.TestCase):
    def setUp(self):
        self.store = BotStore(":memory:")
        self.bot = self.store.save_bot(
            Bot.create(
                name="Deneme Botu",
                persona="Yalnız kısa yanıt ver.",
                tools=["web_search"],
                welcome_message="Merhaba, ben Deneme Botu.",
                now=100,
            )
        )
        self.engine = ChatEngine(self.store, LocalResponder())

    def tearDown(self):
        self.store.close()

    def test_open_session_injects_persona_as_first_message(self):
        session = self.engine.open_session(self.bot, now=100)
        self.assertEqual(session["messages"][0]["role"], "system")
        self.assertIn("Yalnız kısa yanıt ver.", session["messages"][0]["content"])
        self.assertIn("[BOT PERSONA]", session["system_prompt"])
        # karşılama mesajı ikinci tur
        self.assertEqual(session["messages"][1], {"role": "assistant", "content": "Merhaba, ben Deneme Botu."})

    def test_send_appends_turns_and_reply_uses_persona(self):
        session = self.engine.open_session(self.bot, now=100)
        result = self.engine.send(session["id"], "naber", now=101)
        self.assertIn("Deneme Botu", result["reply"])
        self.assertIn("naber", result["reply"])
        roles = [m["role"] for m in result["messages"]]
        self.assertEqual(roles, ["system", "assistant", "user", "assistant"])

        reloaded = self.store.get_session(session["id"])
        assert reloaded is not None
        self.assertEqual(len(reloaded["messages"]), 4)
        self.assertEqual(reloaded["updated_at"], 101)

    def test_send_blank_message_rejected(self):
        session = self.engine.open_session(self.bot)
        with self.assertRaises(ChatError) as ctx:
            self.engine.send(session["id"], "   ")
        self.assertEqual(ctx.exception.code, "message_blank")

    def test_send_unknown_session(self):
        with self.assertRaises(ChatError) as ctx:
            self.engine.send("yok", "selam")
        self.assertEqual(ctx.exception.code, "session_not_found")

    def test_send_when_bot_deleted(self):
        # Sözleşme (store.delete_bot docstring + test_delete_removes_bot_and_sessions):
        # bot silinince oturumları da silinir; bu yüzden hata session_not_found'dır.
        session = self.engine.open_session(self.bot)
        self.store.delete_bot(self.bot.id)
        with self.assertRaises(ChatError) as ctx:
            self.engine.send(session["id"], "selam")
        self.assertEqual(ctx.exception.code, "session_not_found")

    def test_message_cap_keeps_persona(self):
        engine = ChatEngine(self.store, LocalResponder())
        engine.MAX_MESSAGES = 6
        session = engine.open_session(self.bot)
        for i in range(6):
            engine.send(session["id"], "tur %d" % i)
        stored = self.store.get_session(session["id"])
        assert stored is not None
        messages = stored["messages"]
        self.assertLessEqual(len(messages), 6)
        self.assertEqual(messages[0]["role"], "system")
        self.assertIn("[BOT PERSONA]", messages[0]["content"])


if __name__ == "__main__":
    unittest.main()
