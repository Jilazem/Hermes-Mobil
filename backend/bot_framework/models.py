"""V5-5 BOT ÇERÇEVESİ — bot tanımı, doğrulama ve şema.

Grok "Bot" / GPT "Custom GPT" / Gemini "Gem" üçlüsünün Hermes karşılığı:
ad + emoji + persona (sistem talimatı) + araç seti + varsayılan model/think
modu + karşılama mesajı.

İlkeler:
  * fail-closed: geçersiz tanım diske YAZILMAZ, doğrulama hatası döner.
  * sır yasağı: token/şifre/anahtar alanı bot tanımında yaşamaz; dışa/içe
    aktarmada sır görünümlü anahtar REDDEDİLİR.
  * şema sürümü: ileride alan eklenirse eski kayıtlar okunabilir kalsın.
"""
from __future__ import annotations

import re
import time
import uuid
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

SCHEMA_VERSION = 1

MAX_NAME = 48
MAX_EMOJI = 8
MAX_PERSONA = 8000
MAX_WELCOME = 500
MAX_TOOLS = 12
MAX_MODEL = 120
MAX_PROVIDER = 80
MAX_PROFILE = 64

THINK_MODES = ("off", "low", "medium", "high")

# Sır görünümlü anahtar adları — bot tanımına ve içe aktarmaya giremez.
SECRET_KEY_MARKERS = (
    "token",
    "api_key",
    "apikey",
    "password",
    "passwd",
    "secret",
    "credential",
    "authorization",
    "cookie",
)

# Yetenek (araç) kataloğu: bot yalnız buradan seçebilir. Serbest metin araç
# adı kabul edilmez (fail-closed) — UI bu listeyi gösterir.
TOOL_CATALOG: Dict[str, str] = {
    "web_search": "Web araması ve sayfa okuma",
    "read_file": "Yerel dosya okuma (izinli klasörler)",
    "write_file": "Yerel dosya yazma (izinli klasörler)",
    "terminal": "Kabuk komutu (zaman aşımlı)",
    "vision": "Görsel/ekran analizi",
    "gorev_hatti": "Görev Hattı ile uzmana iş verme",
    "kod_kutuphanesi": "Kod kütüphanesinde arama/kayıt",
    "hesap": "Hesap ve matematik denetimi",
    "rapor": "Rapor/Word üretimi",
}

# İzinli alan kümesi — içe aktarmada bilinmeyen alan REDDEDİLİR.
ALLOWED_FIELDS = {
    "id",
    "name",
    "emoji",
    "persona",
    "tools",
    "model",
    "provider",
    "think_mode",
    "welcome_message",
    "profile",
    "created_at",
    "updated_at",
    "schema_version",
}


class BotValidationError(Exception):
    """Doğrulama hatası — kod ve Türkçe mesaj taşır (API 400 döner)."""

    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message

    def to_dict(self) -> Dict[str, str]:
        return {"error": self.code, "message": self.message}


def _looks_secret(key: str) -> bool:
    low = key.lower()
    return any(marker in low for marker in SECRET_KEY_MARKERS)


def _as_text(value: Any, field_name: str) -> str:
    if value is None:
        return ""
    if not isinstance(value, str):
        raise BotValidationError("invalid_type", "%s metin olmalı" % field_name)
    return value


def _as_int(value: Any, field_name: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise BotValidationError("invalid_type", "%s tam sayı olmalı" % field_name)
    return value


@dataclass
class Bot:
    """Kalıcı bot tanımı. `id` cihaz/sunucu içi kimliktir, UI'da gösterilmez."""

    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    name: str = ""
    emoji: str = "🤖"
    persona: str = ""
    tools: List[str] = field(default_factory=list)
    model: str = ""
    provider: str = ""
    think_mode: str = "off"
    welcome_message: str = ""
    profile: str = ""
    created_at: int = 0
    updated_at: int = 0
    schema_version: int = SCHEMA_VERSION

    # ---- doğrulama -----------------------------------------------------
    def validate(self) -> None:
        """Geçersizse BotValidationError fırlatır; geçerliyse None."""
        name = self.name.strip()
        if not name:
            raise BotValidationError("name_blank", "Bot adı boş olamaz")
        if len(name) > MAX_NAME:
            raise BotValidationError(
                "name_too_long", "Bot adı en çok %d karakter olabilir" % MAX_NAME
            )
        if len(self.emoji) > MAX_EMOJI:
            raise BotValidationError(
                "emoji_too_long", "Emoji en çok %d karakter olabilir" % MAX_EMOJI
            )
        if not self.persona.strip():
            raise BotValidationError("persona_blank", "Persona (sistem talimatı) boş olamaz")
        if len(self.persona) > MAX_PERSONA:
            raise BotValidationError(
                "persona_too_long", "Persona en çok %d karakter olabilir" % MAX_PERSONA
            )
        if len(self.tools) > MAX_TOOLS:
            raise BotValidationError(
                "tools_too_many", "En çok %d araç seçilebilir" % MAX_TOOLS
            )
        unknown = [t for t in self.tools if t not in TOOL_CATALOG]
        if unknown:
            raise BotValidationError(
                "tools_unknown", "Bilinmeyen araç: %s" % ", ".join(sorted(unknown))
            )
        if len(set(self.tools)) != len(self.tools):
            raise BotValidationError("tools_duplicate", "Araç listesinde yinelenen ad var")
        if len(self.model) > MAX_MODEL:
            raise BotValidationError(
                "model_too_long", "Model adı en çok %d karakter olabilir" % MAX_MODEL
            )
        if len(self.provider) > MAX_PROVIDER or (self.provider and not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.:-]*", self.provider)):
            raise BotValidationError(
                "provider_invalid", "Model sağlayıcısı geçersiz veya en çok %d karakter olmalı" % MAX_PROVIDER
            )
        if self.provider and not self.model.strip():
            raise BotValidationError(
                "model_required", "Sağlayıcı seçildiyse bot modeli de seçilmeli"
            )
        if self.think_mode not in THINK_MODES:
            raise BotValidationError(
                "think_mode_invalid", "think_mode şunlardan biri olmalı: %s" % ", ".join(THINK_MODES)
            )
        if len(self.welcome_message) > MAX_WELCOME:
            raise BotValidationError(
                "welcome_too_long", "Karşılama mesajı en çok %d karakter olabilir" % MAX_WELCOME
            )
        if len(self.profile) > MAX_PROFILE:
            raise BotValidationError(
                "profile_too_long", "Profil adı en çok %d karakter olabilir" % MAX_PROFILE
            )

    # ---- kurucular -----------------------------------------------------
    @classmethod
    def create(
        cls,
        name: str,
        persona: str,
        emoji: str = "🤖",
        tools: Optional[List[str]] = None,
        model: str = "",
        think_mode: str = "off",
        welcome_message: str = "",
        profile: str = "",
        provider: str = "",
        now: Optional[int] = None,
    ) -> "Bot":
        """Yeni bot üretir ve doğrular; geçersizse BotValidationError."""
        ts = int(now if now is not None else time.time())
        bot = cls(
            id=str(uuid.uuid4()),
            name=name.strip(),
            emoji=emoji or "🤖",
            persona=persona,
            tools=list(tools or []),
            model=model.strip(),
            provider=provider.strip(),
            think_mode=think_mode,
            welcome_message=welcome_message,
            profile=profile.strip(),
            created_at=ts,
            updated_at=ts,
        )
        bot.validate()
        return bot

    @classmethod
    def from_dict(cls, data: Dict[str, Any], now: Optional[int] = None) -> "Bot":
        """İçe aktarma/depo okuması: katı şema, sır yasağı, doğrulama."""
        if not isinstance(data, dict):
            raise BotValidationError("invalid_payload", "Bot tanımı nesne (JSON object) olmalı")
        for key in data:
            if _looks_secret(key):
                raise BotValidationError(
                    "secret_not_allowed",
                    "Bot tanımı sır taşıyamaz (yasak alan: %s)" % key,
                )
            if key not in ALLOWED_FIELDS:
                raise BotValidationError("unknown_field", "Bilinmeyen alan: %s" % key)

        ts = int(now if now is not None else time.time())
        tools = data.get("tools") or []
        if not isinstance(tools, list) or any(not isinstance(t, str) for t in tools):
            raise BotValidationError("invalid_type", "tools metin listesi olmalı")

        bot = cls(
            id=_as_text(data.get("id"), "id").strip() or str(uuid.uuid4()),
            name=_as_text(data.get("name"), "name"),
            emoji=_as_text(data.get("emoji"), "emoji") or "🤖",
            persona=_as_text(data.get("persona"), "persona"),
            tools=list(tools),
            model=_as_text(data.get("model"), "model"),
            provider=_as_text(data.get("provider"), "provider"),
            think_mode=_as_text(data.get("think_mode"), "think_mode") or "off",
            welcome_message=_as_text(data.get("welcome_message"), "welcome_message"),
            profile=_as_text(data.get("profile"), "profile"),
            created_at=_as_int(data.get("created_at", ts), "created_at"),
            updated_at=_as_int(data.get("updated_at", ts), "updated_at"),
            schema_version=_as_int(data.get("schema_version", SCHEMA_VERSION), "schema_version"),
        )
        if bot.schema_version > SCHEMA_VERSION:
            raise BotValidationError(
                "schema_unsupported",
                "Bu tanım daha yeni bir şema sürümü taşıyor (%d > %d)"
                % (bot.schema_version, SCHEMA_VERSION),
            )
        bot.validate()
        return bot

    # ---- çıktı ---------------------------------------------------------
    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "name": self.name,
            "emoji": self.emoji,
            "persona": self.persona,
            "tools": list(self.tools),
            "model": self.model,
            "provider": self.provider,
            "think_mode": self.think_mode,
            "welcome_message": self.welcome_message,
            "profile": self.profile,
            "created_at": self.created_at,
            "updated_at": self.updated_at,
            "schema_version": self.schema_version,
        }

    def to_export(self) -> Dict[str, Any]:
        """Dışa aktarma paketi: tanım + taşıma zarfı (cihaz kimliği taşınmaz)."""
        payload = self.to_dict()
        payload.pop("id", None)
        return {
            "format": "hermes-bot",
            "version": SCHEMA_VERSION,
            "bot": payload,
        }

    def public_view(self) -> Dict[str, Any]:
        """UI için görünüm — iç kimlik dahil (düzenleme/silme için gerekir)."""
        return self.to_dict()
