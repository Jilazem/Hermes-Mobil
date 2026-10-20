# V5-5 BOT ÇERÇEVESİ — backend

Grok "Bot" / GPT "Custom GPT" / Gemini "Gem" karşılığı: **özel bot** tanımı
(persona + yetenekler + şablon + yerel dışa/içe aktarma) ve botla sohbet.

- Dil/bağımlılık: **Python 3.9+, yalnız stdlib** (SQLite + http.server).
- Depo: tek SQLite dosyası (varsayılan `~/.hermes/bots.db`).
- Ağ: varsayılan **127.0.0.1:8765**; LAN'a açmak için `--token` zorunlu.
- Genel paylaşım / bulut YOK; dışa aktarma yerel JSON dosyasıdır.
- Bot tanımı **sır taşımaz**: `token`, `api_key`, `password`, `secret` vb. alanlar
  hem oluşturmada hem içe aktarmada reddedilir (`secret_not_allowed`).

## Çalıştırma

```bash
python3 backend/bot_framework/server.py --db ~/.hermes/bots.db --port 8765
# LAN (token zorunlu):
HERMES_BOT_API_TOKEN=... python3 backend/bot_framework/server.py --host 0.0.0.0 --port 8765
```

## Testler

```bash
cd backend
python3 -m pytest bot_framework/tests -q
# veya: python3 -m unittest discover -s bot_framework/tests -t .
```

Kapsam: model doğrulama/şema, depo kalıcılığı ve içe/dışa aktarma, persona
enjeksiyonu, sohbet turları ve mesaj tavanı, gerçek HTTP API, uçtan uca yaşam
döngüsü (şablon → oluştur → sohbet → persona görünür → export/import →
yeniden başlatma → sil).

## Modüller

| Dosya | Görev |
|---|---|
| `models.py` | `Bot` dataclass, doğrulama, araç kataloğu, sır yasağı, export zarfı |
| `store.py` | SQLite depo: botlar + oturumlar; bozuk DB'de fail-closed |
| `persona.py` | `[BOT PERSONA]` bloklu sistem istemi üretimi |
| `chat.py` | `ChatEngine` (oturum, persona enjeksiyonu, mesaj tavanı), `LocalResponder`, `OpenAIResponder` |
| `templates.py` | 4 hazır şablon: asistan, kod, araştırma, bilirkişi |
| `api.py` | stdlib HTTP API + `make_server` / `serve` |
| `server.py` | CLI giriş noktası |
| `UI-SPEC.md` | Android (cd-android) için ekran/uç sözleşmesi |

## Akış

1. `GET /api/templates` → şablon seç.
2. `POST /api/bots/from-template {key, overrides}` → bot kaydı.
3. `POST /api/bots/{id}/chat {message}` → oturum açılır, persona `system`
   mesajı olarak enjekte edilir, yanıt `messages` ile döner.
4. Sonraki turlar `session_id` ile aynı oturuma eklenir.
5. `GET /api/bots/{id}/export` → `.hermes-bot.json`; `POST /api/bots/import`
   aynı paketi **yeni kimlikle** içe alır.

Yanıtlayıcı katmanı takılabilir: testler ve e2e deterministik `LocalResponder`
kullanır; gerçek model için `OpenAIResponder(base_url)` (OpenAI uyumlu yerel uç,
ör. `http://127.0.0.1:8888/v1`). API anahtarı yalnız bellekte tutulur.

## Sınırlar (bilinçli)

- Eşzamanlı çok süreçli yazma için tek süreç varsayılır (SQLite + süreç içi kilit).
- Oturum mesaj tavanı varsayılan 400; persona her zaman ilk elemanda kalır.
- Bot silindiğinde oturumları da silinir (bkz. `BotStore.delete_bot`).
- HTTP gövde sınırı 1 MB. Sınır aşımında sunucu gövdeyi en çok 8 MB'a kadar okuyup
  atar ve `400 body_too_large` + `Connection: close` döner; 8 MB üstü gövdede
  bağlantı bilinçli olarak kapatılır (bellek/DoS koruması).
