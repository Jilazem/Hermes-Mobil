"""Röle kimlik kanalı testleri (iş #1021 B2).

Gemini Live rölesinin `_authorized` sözleşmesi:
 1) `Authorization: Bearer *** başlığı kabul edilir,
 2) geriye uyum için `?token=` sorgusu da kabul edilir,
 3) yanlış/eksik kimlik reddedilir.

Run:  ~/.hermes/hermes-agent/.venv/bin/python3 -m pytest relay/test_gemini_live_relay_auth.py
"""

from __future__ import annotations

import importlib.util
import os
import sys
from pathlib import Path

import pytest

_RELAY = Path(__file__).with_name("gemini_live_relay.py")


def _load_relay(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("HERMES_DASHBOARD_SESSION_TOKEN", "hs_b1_test_token_517e")
    spec = importlib.util.spec_from_file_location("gemini_live_relay_test", _RELAY)
    assert spec is not None and spec.loader is not None, f"röle yüklenemedi: {_RELAY}"
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class _Headers:
    """websockets `client.request.headers` dublürü."""

    def __init__(self, headers: dict[str, str]):
        self._headers = headers

    def get(self, key: str, default: str = "") -> str:
        for k, v in self._headers.items():
            if k.lower() == key.lower():
                return v
        return default

    def items(self):
        return self._headers.items()

    def __iter__(self):
        # websockets Headers gibi (k, v) çiftleriyle yinelenebilir.
        return iter(self._headers.items())


class _Client:
    """`client.request.headers` / `client.request.path` dublürü."""

    def __init__(self, path: str, headers: dict[str, str]):
        class _Req:
            pass

        self.request = _Req()
        self.request.path = path
        self.request.headers = _Headers(headers)


TOKEN = "hs_b1_test_token_517e"


def test_baslikta_dogru_token_kabul(monkeypatch):
    relay = _load_relay(monkeypatch)
    client = _Client("/live?model=x", {"Authorization": f"Bearer {TOKEN}"})
    assert relay._authorized({}, relay._headers_raw(client)) is True


def test_sorguda_token_geriye_uyum_olarak_kabul(monkeypatch):
    relay = _load_relay(monkeypatch)
    client = _Client(f"/live?token={TOKEN}", {})
    assert relay._authorized({"token": [TOKEN]}, relay._headers_raw(client)) is True


def test_baslikta_yanlis_token_red(monkeypatch):
    relay = _load_relay(monkeypatch)
    client = _Client("/live", {"Authorization": "Bearer yanlis-token"})
    assert relay._authorized({"token": ["yanlis-token"]}, relay._headers_raw(client)) is False


def test_kimliksiz_basvuru_red(monkeypatch):
    relay = _load_relay(monkeypatch)
    client = _Client("/live", {})
    assert relay._authorized({}, relay._headers_raw(client)) is False


def test_baslik_tercih_edilir_sorgu_yoksayilir(monkeypatch):
    # Başlık yanlış ama sorgu doğruysa: başlık önceliklidir → RED.
    relay = _load_relay(monkeypatch)
    client = _Client(f"/live?token={TOKEN}", {"Authorization": "Bearer yanlis-token"})
    assert relay._authorized({"token": [TOKEN]}, relay._headers_raw(client)) is False
