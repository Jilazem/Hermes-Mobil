"""Bot tanımı doğrulama testleri (fail-closed + sır yasağı)."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from bot_framework.models import (  # noqa: E402
    SCHEMA_VERSION,
    TOOL_CATALOG,
    Bot,
    BotValidationError,
)


class BotValidationTests(unittest.TestCase):
    def test_valid_bot_roundtrip(self):
        bot = Bot.create(
            name="  Deneme  ",
            persona="Kısa yanıt ver.",
            emoji="🧪",
            tools=["web_search"],
            model="local",
            provider="node-local-models",
            think_mode="low",
            welcome_message="Merhaba",
            profile="cd-coder",
            now=1000,
        )
        self.assertEqual(bot.name, "Deneme")
        self.assertEqual(bot.created_at, 1000)
        self.assertEqual(bot.updated_at, 1000)
        data = bot.to_dict()
        again = Bot.from_dict(data)
        self.assertEqual(again.to_dict(), data)
        self.assertEqual(again.schema_version, SCHEMA_VERSION)

    def test_blank_name_rejected(self):
        with self.assertRaises(BotValidationError) as ctx:
            Bot.create(name="   ", persona="x")
        self.assertEqual(ctx.exception.code, "name_blank")

    def test_long_name_rejected(self):
        with self.assertRaises(BotValidationError) as ctx:
            Bot.create(name="a" * 49, persona="x")
        self.assertEqual(ctx.exception.code, "name_too_long")

    def test_blank_persona_rejected(self):
        with self.assertRaises(BotValidationError) as ctx:
            Bot.create(name="Bot", persona="  ")
        self.assertEqual(ctx.exception.code, "persona_blank")

    def test_long_persona_rejected(self):
        with self.assertRaises(BotValidationError) as ctx:
            Bot.create(name="Bot", persona="p" * 8001)
        self.assertEqual(ctx.exception.code, "persona_too_long")

    def test_unknown_tool_rejected(self):
        with self.assertRaises(BotValidationError) as ctx:
            Bot.create(name="Bot", persona="x", tools=["rm_rf"])
        self.assertEqual(ctx.exception.code, "tools_unknown")

    def test_duplicate_tools_rejected(self):
        with self.assertRaises(BotValidationError) as ctx:
            Bot.create(name="Bot", persona="x", tools=["web_search", "web_search"])
        self.assertEqual(ctx.exception.code, "tools_duplicate")

    def test_invalid_think_mode_rejected(self):
        with self.assertRaises(BotValidationError) as ctx:
            Bot.create(name="Bot", persona="x", think_mode="ultra")
        self.assertEqual(ctx.exception.code, "think_mode_invalid")

    def test_provider_requires_model(self):
        with self.assertRaises(BotValidationError) as ctx:
            Bot.create(name="Bot", persona="x", provider="node-local-models")
        self.assertEqual(ctx.exception.code, "model_required")

    def test_provider_must_be_a_safe_slug(self):
        with self.assertRaises(BotValidationError) as ctx:
            Bot.create(name="Bot", persona="x", model="m", provider="https://remote.local")
        self.assertEqual(ctx.exception.code, "provider_invalid")

    def test_legacy_bot_without_provider_stays_valid(self):
        bot = Bot.from_dict({"name": "Eski", "persona": "x", "model": "m"})
        self.assertEqual("", bot.provider)

    def test_tool_catalog_nonempty(self):
        self.assertGreaterEqual(len(TOOL_CATALOG), 5)
        for key in TOOL_CATALOG:
            self.assertIsInstance(key, str)

    def test_secret_field_rejected(self):
        for bad in ("token", "api_key", "geminiApiKey", "password", "Authorization"):
            with self.assertRaises(BotValidationError) as ctx:
                Bot.from_dict({"name": "Bot", "persona": "x", bad: "abc"})
            self.assertEqual(ctx.exception.code, "secret_not_allowed")

    def test_unknown_field_rejected(self):
        with self.assertRaises(BotValidationError) as ctx:
            Bot.from_dict({"name": "Bot", "persona": "x", "color": "red"})
        self.assertEqual(ctx.exception.code, "unknown_field")

    def test_future_schema_rejected(self):
        with self.assertRaises(BotValidationError) as ctx:
            Bot.from_dict({"name": "Bot", "persona": "x", "schema_version": 99})
        self.assertEqual(ctx.exception.code, "schema_unsupported")

    def test_export_carries_no_device_id(self):
        bot = Bot.create(name="Bot", persona="x")
        export = bot.to_export()
        self.assertEqual(export["format"], "hermes-bot")
        self.assertEqual(export["version"], SCHEMA_VERSION)
        self.assertNotIn("id", export["bot"])
        text = str(export)
        self.assertNotIn("token", text.lower())
        self.assertNotIn("api_key", text.lower())


if __name__ == "__main__":
    unittest.main()
