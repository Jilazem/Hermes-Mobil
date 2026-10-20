"""V5-5 BOT ÇERÇEVESİ — Hermes bot tanımı, depo, persona enjeksiyonu ve yerel API."""
from .chat import ChatEngine, ChatError, LocalResponder, OpenAIResponder
from .models import SCHEMA_VERSION, TOOL_CATALOG, Bot, BotValidationError
from .persona import build_system_prompt, system_message
from .store import BotStore, StoreError, import_payload
from .templates import bot_from_template, list_templates

__all__ = [
    "Bot",
    "BotValidationError",
    "TOOL_CATALOG",
    "SCHEMA_VERSION",
    "BotStore",
    "StoreError",
    "import_payload",
    "ChatEngine",
    "ChatError",
    "LocalResponder",
    "OpenAIResponder",
    "build_system_prompt",
    "system_message",
    "list_templates",
    "bot_from_template",
]
