"""V5-5 — yerel depo: tek SQLite dosyası (botlar + sohbet oturumları).

Neden SQLite: tek dosya, sorgu var (bot_id ile oturum listesi), eşzamanlı
okuma/yazma güvenli, ek bağımlılık yok (stdlib). JSON dosya deposu onlarca
kayıtta yeterli olurdu; oturum geçmişi büyüdüğü için DB seçildi.

Yazmalar tek bağlantı + kilit ile serileştirilir; bozuk DB dosyası
açılışta fail-closed hata verir (sessiz sıfırlama YOK — veri kaybı gizlenmez).
"""
from __future__ import annotations

import json
import sqlite3
import threading
from typing import Any, Dict, List, Optional

from .models import Bot, BotValidationError

_SCHEMA = """
CREATE TABLE IF NOT EXISTS bots (
    id            TEXT PRIMARY KEY,
    name          TEXT NOT NULL,
    emoji         TEXT NOT NULL DEFAULT '🤖',
    persona       TEXT NOT NULL,
    tools         TEXT NOT NULL DEFAULT '[]',
    model         TEXT NOT NULL DEFAULT '',
    think_mode    TEXT NOT NULL DEFAULT 'off',
    welcome       TEXT NOT NULL DEFAULT '',
    profile       TEXT NOT NULL DEFAULT '',
    created_at    INTEGER NOT NULL,
    updated_at    INTEGER NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE IF NOT EXISTS sessions (
    id            TEXT PRIMARY KEY,
    bot_id        TEXT NOT NULL,
    bot_name      TEXT NOT NULL,
    system_prompt TEXT NOT NULL,
    messages      TEXT NOT NULL DEFAULT '[]',
    created_at    INTEGER NOT NULL,
    updated_at    INTEGER NOT NULL,
    FOREIGN KEY (bot_id) REFERENCES bots(id)
);
CREATE INDEX IF NOT EXISTS idx_sessions_bot ON sessions(bot_id);
"""


class StoreError(Exception):
    """Depo hatası (ör. açılamayan/bozuk DB)."""


class BotStore:
    """Bot + oturum deposu. `path=":memory:"` testte kullanılır."""

    def __init__(self, path: str):
        self.path = path
        self._lock = threading.RLock()
        try:
            self._db = sqlite3.connect(path, check_same_thread=False)
            self._db.row_factory = sqlite3.Row
            with self._lock:
                self._db.executescript(_SCHEMA)
                self._db.commit()
        except sqlite3.Error as exc:  # pragma: no cover - ortam hatası
            raise StoreError("Veritabanı açılamadı: %s" % exc)

    # ---- botlar --------------------------------------------------------
    def _row_to_bot(self, row: sqlite3.Row) -> Bot:
        return Bot.from_dict(
            {
                "id": row["id"],
                "name": row["name"],
                "emoji": row["emoji"],
                "persona": row["persona"],
                "tools": json.loads(row["tools"]),
                "model": row["model"],
                "think_mode": row["think_mode"],
                "welcome_message": row["welcome"],
                "profile": row["profile"],
                "created_at": row["created_at"],
                "updated_at": row["updated_at"],
                "schema_version": row["schema_version"],
            }
        )

    def list_bots(self) -> List[Bot]:
        with self._lock:
            rows = self._db.execute(
                "SELECT * FROM bots ORDER BY created_at ASC, name ASC"
            ).fetchall()
        return [self._row_to_bot(r) for r in rows]

    def get_bot(self, bot_id: str) -> Optional[Bot]:
        with self._lock:
            row = self._db.execute("SELECT * FROM bots WHERE id = ?", (bot_id,)).fetchone()
        return self._row_to_bot(row) if row else None

    def save_bot(self, bot: Bot, now: Optional[int] = None) -> Bot:
        """Yeni bot ekler ya da var olanı günceller. Doğrulama burada da yapılır."""
        bot.validate()
        import time as _time

        ts = int(now if now is not None else _time.time())
        existing = self.get_bot(bot.id)
        bot.updated_at = ts
        if existing is None:
            bot.created_at = bot.created_at or ts
            with self._lock:
                self._db.execute(
                    "INSERT INTO bots (id,name,emoji,persona,tools,model,think_mode,welcome,"
                    "profile,created_at,updated_at,schema_version) "
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    (
                        bot.id,
                        bot.name.strip(),
                        bot.emoji,
                        bot.persona,
                        json.dumps(bot.tools, ensure_ascii=False),
                        bot.model,
                        bot.think_mode,
                        bot.welcome_message,
                        bot.profile,
                        bot.created_at,
                        bot.updated_at,
                        bot.schema_version,
                    ),
                )
                self._db.commit()
        else:
            bot.created_at = existing.created_at
            with self._lock:
                self._db.execute(
                    "UPDATE bots SET name=?,emoji=?,persona=?,tools=?,model=?,think_mode=?,"
                    "welcome=?,profile=?,updated_at=?,schema_version=? WHERE id=?",
                    (
                        bot.name.strip(),
                        bot.emoji,
                        bot.persona,
                        json.dumps(bot.tools, ensure_ascii=False),
                        bot.model,
                        bot.think_mode,
                        bot.welcome_message,
                        bot.profile,
                        bot.updated_at,
                        bot.schema_version,
                        bot.id,
                    ),
                )
                self._db.commit()
        return bot

    def delete_bot(self, bot_id: str) -> bool:
        """Botu ve ona bağlı oturumları siler. Yoksa False."""
        with self._lock:
            cur = self._db.execute("DELETE FROM bots WHERE id = ?", (bot_id,))
            self._db.execute("DELETE FROM sessions WHERE bot_id = ?", (bot_id,))
            self._db.commit()
            return cur.rowcount > 0

    # ---- oturumlar -----------------------------------------------------
    def save_session(self, session: Dict[str, Any]) -> None:
        with self._lock:
            self._db.execute(
                "INSERT OR REPLACE INTO sessions (id,bot_id,bot_name,system_prompt,messages,"
                "created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
                (
                    session["id"],
                    session["bot_id"],
                    session["bot_name"],
                    session["system_prompt"],
                    json.dumps(session["messages"], ensure_ascii=False),
                    session["created_at"],
                    session["updated_at"],
                ),
            )
            self._db.commit()

    def get_session(self, session_id: str) -> Optional[Dict[str, Any]]:
        with self._lock:
            row = self._db.execute(
                "SELECT * FROM sessions WHERE id = ?", (session_id,)
            ).fetchone()
        if row is None:
            return None
        return {
            "id": row["id"],
            "bot_id": row["bot_id"],
            "bot_name": row["bot_name"],
            "system_prompt": row["system_prompt"],
            "messages": json.loads(row["messages"]),
            "created_at": row["created_at"],
            "updated_at": row["updated_at"],
        }

    def list_sessions(self, bot_id: Optional[str] = None) -> List[Dict[str, Any]]:
        with self._lock:
            if bot_id:
                rows = self._db.execute(
                    "SELECT id,bot_id,bot_name,created_at,updated_at FROM sessions "
                    "WHERE bot_id = ? ORDER BY updated_at DESC",
                    (bot_id,),
                ).fetchall()
            else:
                rows = self._db.execute(
                    "SELECT id,bot_id,bot_name,created_at,updated_at FROM sessions "
                    "ORDER BY updated_at DESC"
                ).fetchall()
        return [dict(r) for r in rows]

    def close(self) -> None:
        with self._lock:
            self._db.close()


def import_payload(payload: Any) -> Bot:
    """Dışa aktarma paketini çözer (fail-closed)."""
    if not isinstance(payload, dict):
        raise BotValidationError("invalid_payload", "İçe aktarma nesne olmalı")
    if payload.get("format") != "hermes-bot":
        raise BotValidationError("format_unknown", "Bu bir Hermes bot paketi değil")
    version = payload.get("version")
    if not isinstance(version, int) or version < 1:
        raise BotValidationError("version_invalid", "Paket sürümü geçersiz")
    bot_data = payload.get("bot")
    if not isinstance(bot_data, dict):
        raise BotValidationError("invalid_payload", "Pakette 'bot' nesnesi yok")
    # İçe aktarmada kimlik YENİDEN üretilir: aynı paket iki kez içe alınabilir.
    bot_data = dict(bot_data)
    bot_data.pop("id", None)
    bot_data.pop("created_at", None)
    bot_data.pop("updated_at", None)
    return Bot.from_dict(bot_data)
