"""V5-5 — yerel HTTP API (stdlib only, dış bağımlılık yok).

Varsayılan olarak YALNIZ 127.0.0.1'e bağlanır (genel paylaşım yok). LAN'a
açılacaksa `--host 0.0.0.0 --token <değer>` verilir; token ortamdan/CLI'dan
gelir, bot tanımına ASLA yazılmaz.

Yollar:
  GET    /health
  GET    /api/templates
  GET    /api/tools                       yetenek kataloğu
  GET    /api/bots
  POST   /api/bots                       {name, persona, emoji?, tools?, model?,
                                          think_mode?, welcome_message?, profile?}
  POST   /api/bots/from-template         {key, overrides?}
  POST   /api/bots/import                {format:"hermes-bot", version, bot:{...}}
  GET    /api/bots/{id}
  PATCH  /api/bots/{id}                  kısmi güncelleme
  DELETE /api/bots/{id}
  GET    /api/bots/{id}/export           taşınabilir JSON paketi
  POST   /api/bots/{id}/chat             {message, session_id?}
  GET    /api/sessions/{id}
  GET    /api/sessions?bot_id={id}
(14 uç; yöntem+yol çifti sayısı)
"""
from __future__ import annotations

import hmac
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Dict, Optional, Tuple
from urllib.parse import parse_qs, urlparse

from .chat import ChatEngine, ChatError
from .models import Bot, BotValidationError, TOOL_CATALOG
from .store import BotStore, import_payload
from .templates import bot_from_template, list_templates

MAX_BODY = 1_000_000  # 1 MB — bot tanımı ve sohbet turu için fazlasıyla yeterli


class Api:
    """Yönlendirme + iş kuralları. HTTP'den bağımsız (testte doğrudan çağrılabilir)."""

    def __init__(self, store: BotStore, engine: Optional[ChatEngine] = None):
        self.store = store
        self.engine = engine or ChatEngine(store)

    # ---- botlar --------------------------------------------------------
    def create_bot(self, payload: Dict[str, Any], now: Optional[int] = None) -> Bot:
        if not isinstance(payload, dict):
            raise BotValidationError("invalid_payload", "Gövde nesne olmalı")
        data = dict(payload)
        data.pop("id", None)
        bot = Bot.from_dict(data, now=now)
        return self.store.save_bot(bot, now=now)

    def create_from_template(
        self, key: str, overrides: Optional[Dict[str, Any]] = None, now: Optional[int] = None
    ) -> Bot:
        bot = bot_from_template(key, overrides=overrides, now=now)
        return self.store.save_bot(bot, now=now)

    def patch_bot(self, bot_id: str, payload: Dict[str, Any], now: Optional[int] = None) -> Bot:
        current = self.store.get_bot(bot_id)
        if current is None:
            raise ChatError("bot_not_found", "Bot bulunamadı")
        if not isinstance(payload, dict):
            raise BotValidationError("invalid_payload", "Gövde nesne olmalı")
        merged = current.to_dict()
        for key, value in payload.items():
            if key in ("id", "created_at", "updated_at", "schema_version"):
                raise BotValidationError("readonly_field", "Salt okunur alan: %s" % key)
            merged[key] = value
        updated = Bot.from_dict(merged, now=now)
        updated.id = bot_id
        return self.store.save_bot(updated, now=now)

    def export_bot(self, bot_id: str) -> Dict[str, Any]:
        bot = self.store.get_bot(bot_id)
        if bot is None:
            raise ChatError("bot_not_found", "Bot bulunamadı")
        return bot.to_export()

    def import_bot(self, payload: Any, now: Optional[int] = None) -> Bot:
        bot = import_payload(payload)
        return self.store.save_bot(bot, now=now)

    # ---- sohbet --------------------------------------------------------
    def chat(
        self,
        bot_id: str,
        message: str,
        session_id: Optional[str] = None,
        now: Optional[int] = None,
    ) -> Dict[str, Any]:
        bot = self.store.get_bot(bot_id)
        if bot is None:
            raise ChatError("bot_not_found", "Bot bulunamadı")
        if session_id:
            session = self.store.get_session(session_id)
            if session is None:
                raise ChatError("session_not_found", "Oturum bulunamadı")
            if session["bot_id"] != bot_id:
                raise ChatError("session_mismatch", "Oturum başka bir bota ait")
        else:
            session = self.engine.open_session(bot, now=now)
        result = self.engine.send(session["id"], message, now=now)
        result["welcome_message"] = bot.welcome_message
        return result


class Handler(BaseHTTPRequestHandler):
    server_version = "HermesBotFramework/1.0"
    api: Api
    token: Optional[str] = None

    # ---- yardımcılar ---------------------------------------------------
    def log_message(self, format: str, *args: Any) -> None:  # sessiz: journal'a yazılmaz
        return

    def _send(self, status: int, payload: Optional[Any] = None) -> None:
        body = b""
        if payload is not None:
            body = json.dumps(payload, ensure_ascii=False, indent=None).encode("utf-8")
        self.send_response(status)
        if payload is not None:
            self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        if body:
            self.wfile.write(body)

    def _error(self, status: int, code: str, message: str) -> None:
        self._send(status, {"error": code, "message": message})

    def _read_json(self) -> Any:
        raw_length = self.headers.get("Content-Length") or 0
        try:
            length = int(raw_length)
        except (TypeError, ValueError):
            raise BotValidationError("invalid_content_length", "Content-Length sayı olmalı")
        if length <= 0:
            return None
        if length > MAX_BODY:
            # Gövdeyi okuyup at ki istemci 400'ü alabilsin (okunmamış gövde RST üretir).
            drained = self.rfile.read(min(length, MAX_BODY + 65536))
            if len(drained) < length:
                self.close_connection = True
            raise BotValidationError("body_too_large", "Gövde 1 MB sınırını aşıyor")
        raw = self.rfile.read(length)
        try:
            return json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            raise BotValidationError("invalid_json", "Gövde geçerli JSON değil")

    def _authorized(self) -> bool:
        if not self.token:
            return True
        header = self.headers.get("Authorization") or ""
        return hmac.compare_digest(header, "Bearer %s" % self.token)

    def _route(self, method: str) -> None:
        if not self._authorized():
            self._error(401, "unauthorized", "Yetkisiz")
            return
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"
        query = parse_qs(parsed.query)
        parts = [p for p in path.split("/") if p]

        try:
            # GET /health
            if method == "GET" and path == "/health":
                self._send(200, {"ok": True, "bots": len(self.api.store.list_bots())})
                return

            # GET /api/templates
            if method == "GET" and path == "/api/templates":
                self._send(200, {"templates": list_templates()})
                return

            # GET /api/tools — yetenek kataloğu (UI çoklu seçim için)
            if method == "GET" and path == "/api/tools":
                tools = [
                    {"key": key, "label": label} for key, label in TOOL_CATALOG.items()
                ]
                self._send(200, {"tools": tools})
                return

            # GET/POST /api/bots
            if path == "/api/bots":
                if method == "GET":
                    self._send(
                        200,
                        {"bots": [b.public_view() for b in self.api.store.list_bots()]},
                    )
                    return
                if method == "POST":
                    bot = self.api.create_bot(self._read_json() or {})
                    self._send(201, {"bot": bot.public_view()})
                    return
                self._error(405, "method_not_allowed", "Yöntem desteklenmiyor")
                return

            # POST /api/bots/import
            if path == "/api/bots/import" and method == "POST":
                bot = self.api.import_bot(self._read_json())
                self._send(201, {"bot": bot.public_view()})
                return

            # POST /api/bots/from-template
            if path == "/api/bots/from-template" and method == "POST":
                body = self._read_json() or {}
                key = body.get("key")
                if not isinstance(key, str):
                    raise BotValidationError("template_unknown", "Şablon anahtarı gerekli")
                overrides = body.get("overrides") or {}
                bot = self.api.create_from_template(key, overrides)
                self._send(201, {"bot": bot.public_view()})
                return

            # /api/bots/{id}[/export|/chat]
            if len(parts) >= 3 and parts[0] == "api" and parts[1] == "bots":
                bot_id = parts[2]
                if len(parts) == 3:
                    if method == "GET":
                        bot = self.api.store.get_bot(bot_id)
                        if bot is None:
                            self._error(404, "bot_not_found", "Bot bulunamadı")
                            return
                        self._send(200, {"bot": bot.public_view()})
                        return
                    if method == "PATCH":
                        bot = self.api.patch_bot(bot_id, self._read_json() or {})
                        self._send(200, {"bot": bot.public_view()})
                        return
                    if method == "DELETE":
                        if not self.api.store.delete_bot(bot_id):
                            self._error(404, "bot_not_found", "Bot bulunamadı")
                            return
                        self._send(204)
                        return
                if len(parts) == 4 and parts[3] == "export" and method == "GET":
                    self._send(200, self.api.export_bot(bot_id))
                    return
                if len(parts) == 4 and parts[3] == "chat" and method == "POST":
                    body = self._read_json() or {}
                    message = body.get("message")
                    session_id = body.get("session_id")
                    if not isinstance(message, str):
                        raise BotValidationError("invalid_type", "message metin olmalı")
                    if session_id is not None and not isinstance(session_id, str):
                        raise BotValidationError("invalid_type", "session_id metin olmalı")
                    result = self.api.chat(bot_id, message, session_id)
                    self._send(200, result)
                    return
                self._error(405, "method_not_allowed", "Yöntem desteklenmiyor")
                return

            # /api/sessions[/{id}]
            if len(parts) >= 2 and parts[0] == "api" and parts[1] == "sessions":
                if len(parts) == 2 and method == "GET":
                    bot_id = (query.get("bot_id") or [None])[0]
                    self._send(200, {"sessions": self.api.store.list_sessions(bot_id)})
                    return
                if len(parts) == 3 and method == "GET":
                    session = self.api.store.get_session(parts[2])
                    if session is None:
                        self._error(404, "session_not_found", "Oturum bulunamadı")
                        return
                    self._send(200, {"session": session})
                    return
                self._error(405, "method_not_allowed", "Yöntem desteklenmiyor")
                return

            self._error(404, "not_found", "Yol bulunamadı: %s" % path)
        except BotValidationError as exc:
            self._error(400, exc.code, exc.message)
        except ChatError as exc:
            status = 404 if exc.code.endswith("_not_found") else 400
            self._error(status, exc.code, exc.message)
        except Exception:  # pragma: no cover - beklenmedik; istisna metni DIŞARI VERİLMEZ
            self._error(500, "internal_error", "Sunucu hatası")

    def do_GET(self) -> None:
        self._route("GET")

    def do_POST(self) -> None:
        self._route("POST")

    def do_PATCH(self) -> None:
        self._route("PATCH")

    def do_DELETE(self) -> None:
        self._route("DELETE")


def make_server(
    store: BotStore,
    host: str = "127.0.0.1",
    port: int = 8765,
    token: Optional[str] = None,
    engine: Optional[ChatEngine] = None,
) -> ThreadingHTTPServer:
    # Kütüphane düzeyinde de aynı kapı: yerel olmayan bağ + token yok → açma.
    if host not in ("127.0.0.1", "localhost", "::1") and not token:
        raise ValueError("LAN'a açarken token zorunlu (genel paylaşım yok)")
    handler = type(
        "BoundHandler",
        (Handler,),
        {"api": Api(store, engine=engine), "token": token},
    )
    httpd = ThreadingHTTPServer((host, port), handler)
    httpd.daemon_threads = True
    return httpd


def serve(
    db_path: str,
    host: str = "127.0.0.1",
    port: int = 8765,
    token: Optional[str] = None,
) -> None:
    store = BotStore(db_path)
    httpd = make_server(store, host=host, port=port, token=token)
    print(
        "[bot-framework] %s:%d — db=%s — %d bot"
        % (host, httpd.server_address[1], db_path, len(store.list_bots()))
    )
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        httpd.shutdown()
        store.close()
