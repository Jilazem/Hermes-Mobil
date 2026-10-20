"""V5-5 — botla sohbet: oturum açma + persona enjeksiyonu + yanıt.

Yanıtlayıcı (responder) katmanı takılabilir:
  * LocalResponder — çevrimdışı, deterministik; persona enjeksiyonunu kanıtlar
    (testler ve e2e bunu kullanır).
  * OpenAIResponder — OpenAI uyumlu yerel sunucu (ör. node-local-models :8888).

Oturum kaydı persona'yı `system_prompt` alanında saklar; `messages` dizisinin
ilk elemanı da `role=system` olur. Böylece persona hem modele hem UI'ya görünür.
"""
from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
import uuid
from typing import Any, Dict, List, Optional, Protocol

from .models import Bot
from .persona import build_system_prompt
from .store import BotStore


class Responder(Protocol):  # pragma: no cover - tip sözleşmesi
    def reply(self, bot: Bot, messages: List[Dict[str, str]]) -> str:
        ...


class LocalResponder:
    """Çevrimdışı yanıtlayıcı — persona'nın gerçekten enjekte edildiğini kanıtlar."""

    def reply(self, bot: Bot, messages: List[Dict[str, str]]) -> str:
        last_user = ""
        for msg in reversed(messages):
            if msg["role"] == "user":
                last_user = msg["content"]
                break
        tools = ", ".join(bot.tools) if bot.tools else "araç yok"
        return (
            "«%s» %s olarak yanıtlıyorum (model=%s, think=%s, araçlar=%s). "
            "İsteğin: %s"
            % (
                bot.name,
                bot.emoji,
                bot.model or "oturum modeli",
                bot.think_mode,
                tools,
                last_user,
            )
        )


class OpenAIResponder:
    """OpenAI uyumlu yerel uç (ör. http://127.0.0.1:8888/v1/chat/completions)."""

    def __init__(self, base_url: str, api_key: str = "", timeout: float = 120.0):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key  # yalnız bellekte; bot tanımına/diske yazılmaz
        self.timeout = timeout

    def reply(self, bot: Bot, messages: List[Dict[str, str]]) -> str:
        model = bot.model or "local"
        body = json.dumps(
            {
                "model": model,
                "messages": messages,
                "temperature": {"off": 0.2, "low": 0.3, "medium": 0.5, "high": 0.8}[
                    bot.think_mode
                ],
                "stream": False,
            }
        ).encode("utf-8")
        req = urllib.request.Request(
            self.base_url + "/chat/completions",
            data=body,
            headers={
                "Content-Type": "application/json",
                "Authorization": "Bearer %s" % (self.api_key or "local"),
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                data = json.loads(resp.read().decode("utf-8"))
        except urllib.error.URLError as exc:
            raise ChatError("model_unreachable", "Model ucu yanıt vermedi: %s" % exc)
        try:
            return data["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError):
            raise ChatError("model_bad_response", "Model yanıtı beklenen biçimde değil")


class ChatError(Exception):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message

    def to_dict(self) -> Dict[str, str]:
        return {"error": self.code, "message": self.message}


class ChatEngine:
    """Bot oturumu açar, persona'yı enjekte eder, tur ekler."""

    MAX_MESSAGES: int = 400

    def __init__(self, store: BotStore, responder: Optional[Responder] = None):
        self.store = store
        self.responder = responder or LocalResponder()

    def open_session(self, bot: Bot, now: Optional[int] = None) -> Dict[str, Any]:
        ts = int(now if now is not None else time.time())
        prompt = build_system_prompt(bot)
        session = {
            "id": str(uuid.uuid4()),
            "bot_id": bot.id,
            "bot_name": bot.name,
            "system_prompt": prompt,
            "messages": [{"role": "system", "content": prompt}],
            "created_at": ts,
            "updated_at": ts,
        }
        if bot.welcome_message.strip():
            session["messages"].append({"role": "assistant", "content": bot.welcome_message})
        self.store.save_session(session)
        return session

    def send(
        self,
        session_id: str,
        text: str,
        now: Optional[int] = None,
    ) -> Dict[str, Any]:
        """Oturuma kullanıcı turu ekler, yanıt üretir, ikisini de kaydeder."""
        if not isinstance(text, str) or not text.strip():
            raise ChatError("message_blank", "Mesaj boş olamaz")
        session = self.store.get_session(session_id)
        if session is None:
            raise ChatError("session_not_found", "Oturum bulunamadı")
        bot = self.store.get_bot(session["bot_id"])
        if bot is None:
            raise ChatError("bot_not_found", "Oturumun botu silinmiş")

        messages = list(session["messages"])
        messages.append({"role": "user", "content": text})

        answer = self.responder.reply(bot, messages)
        messages.append({"role": "assistant", "content": answer})

        # Sınır, yanıt eklendikten SONRA uygulanır; aksi halde son tur sınırı aşar.
        # Persona (system) her zaman ilk elemanda kalır, en eski turlar düşer.
        if len(messages) > self.MAX_MESSAGES >= 2:
            head = messages[:1]  # system (+ varsa karşılama tail'in başında kalır)
            tail = messages[-(self.MAX_MESSAGES - 1):]
            messages = head + tail

        ts = int(now if now is not None else time.time())
        session["messages"] = messages
        session["updated_at"] = ts
        self.store.save_session(session)
        return {
            "session_id": session["id"],
            "bot_id": bot.id,
            "bot_name": bot.name,
            "reply": answer,
            "system_prompt": session["system_prompt"],
            "messages": messages,
        }

    def get_session(self, session_id: str) -> Optional[Dict[str, Any]]:
        return self.store.get_session(session_id)
