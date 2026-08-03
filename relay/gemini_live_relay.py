#!/usr/bin/env python3
"""Gemini Live API rölesi — telefon ile Google arasında the server üzerinde durur.

Neden röle?
-----------
Gemini Live API doğrudan telefondan da çağrılabilir, ama o zaman API anahtarının
cihazda durması gerekir. Telefon kaybolursa/ele geçerse anahtar da gider ve tek
tek iptal edilemez. Röle ile anahtar sunucuda `~/.hermes/.env` içinde kalır;
telefon yalnızca zaten sahip olduğu Hermes oturum tokenini kullanır.

Akış
----
    Telefon ──WS──► :9170/live ──WSS──► generativelanguage.googleapis.com
              (X-Hermes token)          (GOOGLE_API_KEY, sunucuda)

Röle çerçeveleri iki yönde de olduğu gibi geçirir; tek müdahalesi ilk `setup`
çerçevesini kendisinin üretmesidir — böylece telefon model adını, anahtarı ya da
protokol ayrıntısını bilmek zorunda kalmaz.

Kimlik doğrulama
----------------
`?token=` sorgu parametresi `HERMES_DASHBOARD_SESSION_TOKEN` ile sabit-zamanlı
karşılaştırılır. Yanlışsa bağlantı 4401 ile kapatılır. Bu, Hermes dashboard'ının
WS ucuyla aynı sözleşme — telefonda zaten kayıtlı olan token yeniden kullanılır.

Kullanıcının kendi anahtarı
---------------------------
Telefon `?api_key=` gönderirse sunucununki yerine o kullanılır. Böylece kendi
kotasını kullanmak isteyen ya da röleye anahtar emanet etmek istemeyen kullanıcı
kendi anahtarını girebilir.
"""

from __future__ import annotations

import asyncio
import hmac
import json
import logging
import os
import ssl
from pathlib import Path
from typing import Any, Optional
from urllib.parse import parse_qs, urlparse

import websockets
from websockets.asyncio.server import serve

LOG = logging.getLogger("gemini-live-relay")

HOST = os.environ.get("RELAY_HOST", "0.0.0.0")
PORT = int(os.environ.get("RELAY_PORT", "9170"))

DEFAULT_MODEL = os.environ.get(
    "LIVE_MODEL", "models/gemini-2.5-flash-native-audio-preview-09-2025"
)
UPSTREAM = (
    "wss://generativelanguage.googleapis.com/ws/"
    "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
)

DEFAULT_SYSTEM_INSTRUCTION = (
    "Sen Hermes'sin — kullanıcının kişisel yapay zekâ asistanı. Türkçe konuş. "
    "Sesli sohbette kısa ve doğal cümleler kur; madde madde uzun listeler okuma. "
    "Kamera görüntüsü geldiğinde ne gördüğünü kısaca betimle, sonra sorusunu yanıtla. "
    "Emin olmadığın şeyi uydurma, bilmiyorsan bilmediğini söyle.\n"
    "Kullanıcının dosyaları, kayıtları, cron görevleri, hesaplamaları ya da "
    "sunucudaki herhangi bir bilgi söz konusuysa MUTLAKA hermes_ask aracını kullan — "
    "bu bilgiler yalnızca Hermes'te var, senin eğitim verinde yok. "
    "Aracı çağırmadan önce 'bakıyorum' gibi kısa bir şey söyle ki kullanıcı beklediğini bilsin."
)

# ── Hermes araç köprüsü ───────────────────────────────────────────────
#
# Gemini'ye tek bir araç tanıtılıyor: hermes_ask. Bunun arkasında Hermes
# ajanının tamamı var — dosya erişimi, cron görevleri, oturum geçmişi,
# hesap motoru, tüm MCP sunucuları. Her MCP aracını tek tek Gemini'ye
# tanıtmak yerine tek kapı bırakmak hem sözleşmeyi sabit tutuyor hem de
# Hermes'e yeni araç eklendiğinde röleyi değiştirmeyi gereksiz kılıyor.

HERMES_WS = os.environ.get("HERMES_WS", "ws://127.0.0.1:9150/api/ws")
HERMES_ASK_TIMEOUT = float(os.environ.get("HERMES_ASK_TIMEOUT", "120"))

# Telefon araçları — röle çalıştırmaz, yalnız Gemini'ye tanıtır. `toolCall`
# çerçevesi telefona da iletildiği için uygulama bunları kendisi yürütüp
# `toolResponse` gönderir. Röle yalnız `hermes_ask`'i üstlenir.
# Shizuku (kabuk yetkisi) gerektiren araçlar.
#
# Bunlar intent'le yapılamıyor: Android uygulamalara Wi-Fi'ı programatik olarak
# açtırmıyor (yalnız ayar sayfası açılabiliyor). Shizuku `svc`/`cmd` komutlarını
# kabuk kimliğiyle çalıştırıyor. Telefon tarafı hazır değilse bu liste hiç
# gönderilmiyor.
SHIZUKU_TOOL_DECLARATIONS = [
    {
        "name": "phone_wifi",
        "description": "Telefonun Wi-Fi'ını açar ya da kapatır.",
        "parameters": {
            "type": "OBJECT",
            "properties": {"state": {"type": "STRING", "description": "on veya off"}},
            "required": ["state"],
        },
    },
    {
        "name": "phone_bluetooth",
        "description": "Telefonun Bluetooth'unu açar ya da kapatır.",
        "parameters": {
            "type": "OBJECT",
            "properties": {"state": {"type": "STRING", "description": "on veya off"}},
            "required": ["state"],
        },
    },
    {
        "name": "phone_dnd",
        "description": "Rahatsız etme kipini açar ya da kapatır.",
        "parameters": {
            "type": "OBJECT",
            "properties": {"state": {"type": "STRING", "description": "on veya off"}},
            "required": ["state"],
        },
    },
    {
        "name": "phone_shell",
        "description": (
            "Telefonda kabuk komutu çalıştırır ve çıktısını döner. Yalnız "
            "başka araç yetmediğinde kullan; komutu kullanıcıya açıkla."
        ),
        "parameters": {
            "type": "OBJECT",
            "properties": {"command": {"type": "STRING"}},
            "required": ["command"],
        },
    },
]

PHONE_TOOL_DECLARATIONS = [
    {
        "name": "phone_open_app",
        "description": "Telefonda bir uygulamayı açar. Uygulama adı ya da paket adı ver.",
        "parameters": {"type": "OBJECT", "properties": {
            "app": {"type": "STRING", "description": "Uygulama adı, örn. WhatsApp"}},
            "required": ["app"]},
    },
    {
        "name": "phone_dial",
        "description": (
            "Numarayi ya da REHBERDEKI BIR ISMI cevirici ekranina yazar; isim "
            "verilirse rehberden cozulur. Aramayi kullanici baslatir -- sen baslatamazsin."
        ),
        "parameters": {"type": "OBJECT", "properties": {
            "number": {"type": "STRING", "description": "Numara ya da rehberdeki isim"}},
            "required": ["number"]},
    },
    {
        "name": "phone_sms_draft",
        "description": "SMS taslağı hazırlar. Göndermez — kullanıcı onaylar.",
        "parameters": {"type": "OBJECT", "properties": {
            "number": {"type": "STRING"}, "text": {"type": "STRING"}},
            "required": ["text"]},
    },
    {
        "name": "phone_navigate",
        "description": "Haritada bir hedefe yol tarifi başlatır.",
        "parameters": {"type": "OBJECT", "properties": {
            "destination": {"type": "STRING"}}, "required": ["destination"]},
    },
    {
        "name": "phone_set_alarm",
        "description": "Alarm kurar. Saat 'HH:MM' biçiminde.",
        "parameters": {"type": "OBJECT", "properties": {
            "time": {"type": "STRING"}, "label": {"type": "STRING"}},
            "required": ["time"]},
    },
    {
        "name": "phone_add_event",
        "description": "Takvime kayıt ekranı açar.",
        "parameters": {"type": "OBJECT", "properties": {
            "title": {"type": "STRING"}, "when": {"type": "STRING"}},
            "required": ["title"]},
    },
    {
        "name": "phone_web_search",
        "description": "Telefonda web araması açar.",
        "parameters": {"type": "OBJECT", "properties": {
            "query": {"type": "STRING"}}, "required": ["query"]},
    },
    {
        "name": "phone_tasker_task",
        "description": "Kullanıcının Tasker'da tanımlı bir görevini tetikler.",
        "parameters": {"type": "OBJECT", "properties": {
            "task": {"type": "STRING", "description": "Tasker görev adı"},
            "parameter": {"type": "STRING"}}, "required": ["task"]},
    },
    {
        "name": "phone_macrodroid_webhook",
        "description": "MacroDroid webhook adresini çağırarak makro tetikler.",
        "parameters": {"type": "OBJECT", "properties": {
            "url": {"type": "STRING"}}, "required": ["url"]},
    },
    {
        "name": "phone_contacts",
        "description": (
            "Rehberde isme gore kisi arar, ad ve numara doner. Kullanici bir kisiyi "
            "adiyla andiginda once bunu cagir."
        ),
        "parameters": {"type": "OBJECT", "properties": {
            "name": {"type": "STRING", "description": "Aranacak isim, orn. Ahmet"}},
            "required": ["name"]},
    },
    {
        "name": "phone_notifications",
        "description": (
            "Telefonda su an duran bildirimleri ozetler. \"Bugun ne kacirdim\", "
            "\"yeni mesaj var mi\" gibi sorularin karsiligi. Surekli bildirimler "
            "(muzik calar, VPN) haric tutulur."
        ),
        "parameters": {"type": "OBJECT", "properties": {}, "required": []},
    },
    {
        "name": "phone_calendar",
        "description": (
            "Onumuzdeki saatlerdeki takvim etkinliklerini listeler. "
            "\"Bugun ne var\", \"yarin programim ne\" sorulari icin."
        ),
        "parameters": {"type": "OBJECT", "properties": {
            "hours": {"type": "STRING", "description": "Kac saat ileri bakilacak, varsayilan 24"}},
            "required": []},
    },
    {
        "name": "phone_location",
        "description": (
            "Telefonun son bilinen konumunu adres olarak doner. \"Neredeyim\" "
            "sorusu ve yol tarifi hesabi icin. Sonuc konumun kac dakika onceye "
            "ait oldugunu da soyler."
        ),
        "parameters": {"type": "OBJECT", "properties": {}, "required": []},
    },
    {
        "name": "phone_clipboard_read",
        "description": "Telefonun panosundaki metni okur.",
        "parameters": {"type": "OBJECT", "properties": {}, "required": []},
    },
    {
        "name": "phone_clipboard_write",
        "description": "Verilen metni telefonun panosuna kopyalar.",
        "parameters": {"type": "OBJECT", "properties": {
            "text": {"type": "STRING"}}, "required": ["text"]},
    },
    {
        "name": "phone_settings",
        "description": "Telefon ayarlarında bir bölümü açar (wifi, bluetooth, ses, ekran, pil, konum).",
        "parameters": {"type": "OBJECT", "properties": {
            "section": {"type": "STRING"}}, "required": []},
    },
]

TOOL_DECLARATIONS = [
    {
        "functionDeclarations": [
            {
                "name": "hermes_ask",
                "description": (
                    "Hermes ajanına soru sorar ve yanıtını döner. Hermes'in kullanıcıya ait "
                    "tüm araçları vardır: dosya okuma/yazma, alan bilgisi sorgulama, "
                    "değerleme, hesap motoru, dosya okuma/yazma, cron görevleri, oturum "
                    "geçmişi, MCP sunucuları. Kullanıcının kişisel verisi, dosyaları ya da "
                    "sunucudaki durum hakkındaki HER soru bu araçla yanıtlanmalı."
                ),
                "parameters": {
                    "type": "OBJECT",
                    "properties": {
                        "question": {
                            "type": "STRING",
                            "description": (
                                "Hermes'e iletilecek soru ya da görev. Açık ve eksiksiz yaz; "
                                "Hermes sesli sohbetin bağlamını görmüyor."
                            ),
                        }
                    },
                    "required": ["question"],
                },
            }
        ]
    }
]


def _read_env_file(path: Path) -> dict[str, str]:
    """`~/.hermes/.env` dosyasını okur. python-dotenv bağımlılığı istemiyoruz."""
    values: dict[str, str] = {}
    try:
        for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            values[key.strip()] = value.strip().strip('"').strip("'")
    except OSError as exc:
        LOG.warning("env dosyası okunamadı (%s): %s", path, exc)
    return values


ENV = _read_env_file(Path.home() / ".hermes" / ".env")
SERVER_API_KEY = (
    os.environ.get("GOOGLE_GEMINI_API_KEY")
    or os.environ.get("GOOGLE_API_KEY")
    or ENV.get("GOOGLE_GEMINI_API_KEY")
    or ENV.get("GOOGLE_API_KEY")
    or ""
)
HERMES_TOKEN = (
    os.environ.get("HERMES_DASHBOARD_SESSION_TOKEN")
    or ENV.get("HERMES_DASHBOARD_SESSION_TOKEN")
    or ""
)


def _query(path: str) -> dict[str, list[str]]:
    return parse_qs(urlparse(path).query)


def _authorized(params: dict[str, list[str]]) -> bool:
    if not HERMES_TOKEN:
        LOG.error("HERMES_DASHBOARD_SESSION_TOKEN bulunamadı — tüm bağlantılar reddedilir")
        return False
    supplied = (params.get("token") or [""])[0]
    return hmac.compare_digest(supplied, HERMES_TOKEN)


def _build_setup(params: dict[str, list[str]]) -> dict[str, Any]:
    """İlk `setup` çerçevesini röle üretir; telefon protokolü bilmek zorunda değil."""
    model = (params.get("model") or [DEFAULT_MODEL])[0]
    if not model.startswith("models/"):
        model = f"models/{model}"

    # Ses mi metin mi? Live API tek modalite kabul ediyor.
    modality = (params.get("modality") or ["AUDIO"])[0].upper()
    if modality not in {"AUDIO", "TEXT"}:
        modality = "AUDIO"

    instruction = (params.get("system") or [DEFAULT_SYSTEM_INSTRUCTION])[0]

    setup: dict[str, Any] = {
        "model": model,
        "generationConfig": {"responseModalities": [modality]},
        "systemInstruction": {"parts": [{"text": instruction}]},
    }

    # Konuşmanın yazıya dökümü — uygulamada altyazı göstermek için.
    if modality == "AUDIO":
        setup["outputAudioTranscription"] = {}
        setup["inputAudioTranscription"] = {}

    # `?tools=off` ile kapatılabilir (saf sohbet için).
    if (params.get("tools") or ["on"])[0].lower() != "off":
        decls = list(TOOL_DECLARATIONS[0]["functionDeclarations"])
        # Telefon araçları yalnız istemci istediğinde tanıtılır: uygulama
        # dışından bağlanan bir istemci bunları çalıştıramaz, tanıtmak da
        # modeli boşuna yanıltır.
        if (params.get("phone_tools") or ["0"])[0] in ("1", "true", "on"):
            decls += PHONE_TOOL_DECLARATIONS
            # Shizuku hazırsa derin araçlar da tanıtılıyor. Hazır değilken
            # tanıtmak modeli boşa denemeye ve "yapamıyorum" demeye itiyordu.
            if (params.get("shizuku") or ["0"])[0] in ("1", "true", "on"):
                decls += SHIZUKU_TOOL_DECLARATIONS
        setup["tools"] = [{"functionDeclarations": decls}]

    return {"setup": setup}


class HermesBridge:
    """Gemini'nin `hermes_ask` çağrılarını Hermes gateway'ine iletir.

    Röle bağlantısı başına tek bir Hermes oturumu açılır ve korunur; böylece
    ardışık sorular birbirinin bağlamını görür ("o kaydın ayrıntılarını da getir"
    gibi takip soruları çalışır).
    """

    def __init__(self, token: str) -> None:
        self._token = token
        self._ws: Optional[Any] = None
        self._session_id: Optional[str] = None
        self._req = 0
        self._lock = asyncio.Lock()

    async def _ensure(self) -> None:
        if self._ws is not None and self._session_id:
            return
        self._ws = await websockets.connect(
            f"{HERMES_WS}?token={self._token}", max_size=None
        )
        self._req += 1
        rid = f"r{self._req}"
        await self._ws.send(
            json.dumps({"jsonrpc": "2.0", "id": rid, "method": "session.create", "params": {}})
        )
        while True:
            frame = json.loads(await asyncio.wait_for(self._ws.recv(), timeout=30))
            if frame.get("id") == rid:
                self._session_id = (frame.get("result") or {}).get("session_id")
                break
        LOG.info("Hermes oturumu açıldı: %s", self._session_id)

    async def ask(self, question: str) -> str:
        """Soruyu Hermes'e sorar, tam yanıtı döner."""
        async with self._lock:
            try:
                await self._ensure()
            except Exception as exc:  # noqa: BLE001
                return f"Hermes'e bağlanılamadı: {exc}"

            ws, sid = self._ws, self._session_id
            if ws is None or not sid:
                return "Hermes oturumu kurulamadı."

            self._req += 1
            rid = f"r{self._req}"
            try:
                await ws.send(json.dumps({
                    "jsonrpc": "2.0", "id": rid, "method": "prompt.submit",
                    "params": {"session_id": sid, "text": question},
                }))
            except Exception as exc:  # noqa: BLE001
                self._ws = None
                self._session_id = None
                return f"Soru iletilemedi: {exc}"

            # Yanıt `message.delta` olaylarıyla akar, `message.complete` ile biter.
            text_parts: list[str] = []
            deadline = asyncio.get_event_loop().time() + HERMES_ASK_TIMEOUT
            try:
                while asyncio.get_event_loop().time() < deadline:
                    remaining = deadline - asyncio.get_event_loop().time()
                    frame = json.loads(
                        await asyncio.wait_for(ws.recv(), timeout=max(1.0, remaining))
                    )
                    if frame.get("method") != "event":
                        continue
                    params = frame.get("params") or {}
                    etype = params.get("type")
                    payload = params.get("payload") or {}
                    if etype in ("message.delta",):
                        text_parts.append(str(payload.get("text") or ""))
                    elif etype == "message.complete":
                        if not text_parts and payload.get("text"):
                            text_parts.append(str(payload["text"]))
                        break
                    elif etype == "error":
                        return f"Hermes hatası: {payload.get('text') or 'bilinmeyen'}"
            except asyncio.TimeoutError:
                partial = "".join(text_parts).strip()
                return partial or (
                    "Hermes hâlâ çalışıyor, yanıt gecikti. Kullanıcıya beklemesini söyle."
                )
            except Exception as exc:  # noqa: BLE001
                self._ws = None
                self._session_id = None
                return f"Hermes yanıtı okunamadı: {exc}"

            answer = "".join(text_parts).strip()
            return answer or "Hermes boş yanıt döndü."

    async def close(self) -> None:
        if self._ws is not None:
            try:
                await self._ws.close()
            except Exception:  # noqa: BLE001
                pass
        self._ws = None
        self._session_id = None


async def _pump(src, dst, label: str) -> None:
    """Bir yönü aktarır; kapanışta sessizce biter."""
    try:
        async for frame in src:
            await dst.send(frame)
    except websockets.ConnectionClosed:
        pass
    except Exception as exc:  # noqa: BLE001 - röle asla çökmemeli
        LOG.warning("%s aktarımı hata ile bitti: %s", label, exc)


async def _pump_downstream(upstream, client, bridge: Optional[HermesBridge]) -> None:
    """Gemini→telefon yönü; `toolCall` çerçevelerini yakalayıp Hermes'e sorar.

    Çerçeve telefona da iletilir — uygulama "araç çalışıyor" göstergesi
    çizebilsin diye. Yanıt asenkron üretilir; Hermes yavaş olsa bile ses akışı
    tıkanmaz.
    """
    pending: set[asyncio.Task] = set()
    try:
        async for frame in upstream:
            await client.send(frame)

            if bridge is None:
                continue

            text = frame.decode() if isinstance(frame, (bytes, bytearray)) else frame
            if '"toolCall"' not in text:
                continue

            try:
                calls = (json.loads(text).get("toolCall") or {}).get("functionCalls") or []
            except Exception:  # noqa: BLE001
                continue

            for call in calls:
                # `phone_*` çağrılarını telefon yürütür; röle karışmaz.
                if str(call.get("name") or "").startswith("phone_"):
                    continue
                task = asyncio.create_task(_run_tool_call(upstream, bridge, call))
                pending.add(task)
                task.add_done_callback(pending.discard)
    except websockets.ConnectionClosed:
        pass
    except Exception as exc:  # noqa: BLE001
        LOG.warning("gemini→telefon aktarımı hata ile bitti: %s", exc)
    finally:
        for task in list(pending):
            task.cancel()


async def _run_tool_call(upstream, bridge: HermesBridge, call: dict) -> None:
    """Tek bir `hermes_ask` çağrısını yürütüp `toolResponse` gönderir."""
    name = call.get("name") or ""
    call_id = call.get("id") or ""
    args = call.get("args") or {}

    if name != "hermes_ask":
        answer = f"Bilinmeyen araç: {name}"
    else:
        question = str(args.get("question") or "").strip()
        LOG.info("hermes_ask → %s", question[:120])
        answer = await bridge.ask(question) if question else "Soru boş geldi."
        LOG.info("hermes_ask ← %s", answer[:120].replace("\n", " "))

    try:
        await upstream.send(json.dumps({
            "toolResponse": {
                "functionResponses": [
                    {"id": call_id, "name": name, "response": {"result": answer}}
                ]
            }
        }))
    except Exception as exc:  # noqa: BLE001
        LOG.warning("toolResponse gönderilemedi: %s", exc)


async def handle(client) -> None:
    params = _query(client.request.path)
    peer = getattr(client, "remote_address", ("?", 0))[0]

    if not _authorized(params):
        LOG.warning("yetkisiz bağlantı reddedildi: %s", peer)
        await client.close(code=4401, reason="unauthorized")
        return

    # Telefon kendi anahtarını gönderdiyse onu kullan (kullanıcı tercihi),
    # yoksa sunucudaki anahtar. Anahtar hiçbir zaman loglanmaz.
    api_key = (params.get("api_key") or [""])[0] or SERVER_API_KEY
    if not api_key:
        LOG.error("API anahtarı yok — ne sunucuda ne istemcide")
        await client.close(code=4402, reason="no api key")
        return

    key_source = "istemci" if (params.get("api_key") or [""])[0] else "sunucu"
    LOG.info("bağlantı açıldı: %s (anahtar: %s)", peer, key_source)

    ssl_ctx = ssl.create_default_context()
    try:
        async with websockets.connect(
            f"{UPSTREAM}?key={api_key}", ssl=ssl_ctx, max_size=None
        ) as upstream:
            LOG.info("upstream açıldı, setup gönderiliyor")
            await upstream.send(json.dumps(_build_setup(params)))

            # İlk yanıt setupComplete olmalı; hata varsa istemciye ilet.
            first = await asyncio.wait_for(upstream.recv(), timeout=30)
            LOG.info("upstream ilk yanıt: %s", str(first)[:120])
            await client.send(first)
            LOG.info("istemciye iletildi")

            # Araçlar açıksa Gemini'nin `hermes_ask` çağrılarını karşılayacak
            # köprü; kapalıysa düz aktarım.
            tools_on = (params.get("tools") or ["on"])[0].lower() != "off"
            bridge = HermesBridge(HERMES_TOKEN) if tools_on else None

            try:
                await asyncio.gather(
                    _pump(client, upstream, "telefon→gemini"),
                    _pump_downstream(upstream, client, bridge),
                )
            finally:
                if bridge is not None:
                    await bridge.close()
    except asyncio.TimeoutError:
        LOG.error("Gemini setup zaman aşımı")
        await client.close(code=4504, reason="upstream timeout")
    except websockets.InvalidStatus as exc:
        # 401/403 → anahtar geçersiz; istemci bunu ayırt edebilmeli.
        LOG.error("Gemini bağlantısı reddetti: %s", exc)
        await client.close(code=4403, reason="upstream rejected")
    except Exception as exc:  # noqa: BLE001
        LOG.exception("röle hatası: %s", exc)
        await client.close(code=1011, reason="relay error")
    finally:
        LOG.info("bağlantı kapandı: %s", peer)


async def main() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
    )
    if not SERVER_API_KEY:
        LOG.warning("Sunucuda GOOGLE_API_KEY yok — yalnız kendi anahtarını gönderen istemciler çalışır")
    if not HERMES_TOKEN:
        LOG.error("HERMES_DASHBOARD_SESSION_TOKEN yok — röle hiçbir bağlantıyı kabul etmeyecek")

    LOG.info("Gemini Live rölesi %s:%s dinliyor (model: %s)", HOST, PORT, DEFAULT_MODEL)
        # `ping_timeout` açıkça yükseltiliyor: varsayılan 20 sn, ama `hermes_ask`
    # 60 saniye sürebiliyor ve o sırada telefonun pong'u ses kareleri arasında
    # sıkışıyor. 20/20 çifti bağlantıyı boşuna öldürüyordu.
    async with serve(
        handle, HOST, PORT, max_size=None, ping_interval=20, ping_timeout=60
    ):
        await asyncio.Future()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
