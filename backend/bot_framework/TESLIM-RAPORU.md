# V5-5 BOT ÇERÇEVESİ — teslim raporu

Tarih: 2026-10-20 · Profil: cd-coder · Dal: feat/v5-czip

## Ne yapıldı

Grok Bot / GPT Custom GPT karşılığı özel bot çerçevesi, stdlib Python:

- **Bot tanımı** (`models.py`): ad, emoji, persona (≤8000), yetenekler
  (9 araçlık katalog, ≤12), model, think modu (off/low/medium/high),
  karşılama mesajı; katı şema + sır yasağı (`secret_not_allowed`).
- **Depo** (`store.py`): tek SQLite dosyası, botlar + oturumlar; bozuk DB'de
  fail-closed; dışa/içe aktarma zarfı (`hermes-bot` v1) — içe aktarmada yeni kimlik.
- **Persona enjeksiyonu** (`persona.py`): `[BOT PERSONA]` bloklu sistem istemi;
  oturumun ilk mesajı `role=system` olarak saklanır (UI ve model görür).
- **Sohbet** (`chat.py`): `ChatEngine` oturum açar/turları ekler; mesaj tavanı
  persona'yı korur; `LocalResponder` (deterministik) ve `OpenAIResponder`
  (OpenAI uyumlu yerel uç) takılabilir.
- **Şablonlar** (`templates.py`): asistan, kod, araştırma, bilirkişi.
- **HTTP API** (`api.py`, `server.py`): 14 uç; varsayılan 127.0.0.1:8765,
  LAN'a açarken `--token` zorunlu (kütüphane düzeyinde de); bot tanımına sır yazılmaz.
- **UI şartnamesi** (`UI-SPEC.md`): cd-android için ekranlar, alan sınırları,
  uçlar, hata kodları, kabul ölçütleri.
- **E2E kanıt** (`e2e_demo.py`): gerçek HTTP + geçici SQLite ile tam yaşam döngüsü.

## Doğrulama (gerçek çıktı)

```
$ python3 -m pytest backend/bot_framework/tests -q
64 passed in 14.41s

$ python3 backend/bot_framework/e2e_demo.py
EXIT=0  →  "ok": true
adımlar: templates(200,4) · create_bot(201) · chat_first(200, persona_visible=true,
ilk mesaj role=system) · chat_second(200, same_session=true, 6 mesaj) ·
session_persisted(200, persona_visible=true) · export_import(200/201,
format=hermes-bot, id_changed=true, same_persona=true) · delete_bot(204)
```

## Bu turda düzeltilenler

1. **Mesaj tavanı taşması** (kırmızı test): tavan, yanıt eklenmeden önce
   uygulanıyordu → MAX=6 iken 7 mesaj kalıyordu. Tavan artık yanıt eklendikten
   sonra uygulanıyor; persona ilk elemanda kalıyor. `chat.py:146-160`.
2. **Bot silinince sohbet hatası** (kırmızı test): `delete_bot` oturumları da
   siliyor (depo sözleşmesi + `test_delete_removes_bot_and_sessions`); bu yüzden
   hata `bot_not_found` değil `session_not_found`. Test sözleşmeye hizalandı
   (`tests/test_persona_chat.py:129-136`).
3. **`GET /api/tools`** eklendi: UI yetenek listesini backend kataloğundan alır
   (şartnamede referans verilen uç gerçekten var).

## Atlanan / kapsam dışı

- Android UI kodu (cd-android'in işi) — yalnız şartname teslim edildi.
- Gerçek modelle canlı sohbet (yerel uç ayakta olmayabilir); `OpenAIResponder`
  hazır, e2e deterministik `LocalResponder` ile koşuyor.
- Çok süreçli yazma (tek süreç varsayımı README'de yazılı).

## Açık riskler

- `OpenAIResponder` yalnız URL hatası ve bozuk gövdeyi ayıklıyor; HTTP 429/5xx
  ayrımı eklenmedi (model ucu katmanına bırakıldı).
- SQLite tek dosya; yedekleme stratejisi uygulama dışında (K22 yedek akışıyla
  birlikte değerlendirilmeli).

## Bağımsız denetim (cd-denetmen, 1. ve 2. tur)

1. tur verdict: **GECTI** (`denetim/verdict-v5-5-bot-cercevesi.json`), 3 koşul + 2 ORTA
bulgu. 2. tur verdict: **GECTI**, 3/3 koşul KAPANDI
(`denetim/verdict-v5-5-bot-cercevesi-t2.json`; 63→64 test). Bulgu ve düzeltmeler:

| Bulgu | Düzeltme |
|---|---|
| ORTA-1: `from-template` override yolu sır/şema kapısını atlıyordu (500 + TypeError) | `templates.bot_from_template` artık `Bot.from_dict` kapısından geçiyor → `secret_not_allowed` / `unknown_field` 400 |
| ORTA-2: 1 MB üzeri gövdede istemci 400'ü göremiyordu (broken pipe) | `_read_json` gövdeyi okuyup atıyor; gerekirse `close_connection` → 400 `body_too_large` istemciye ulaşıyor |
| DUSUK-3: sayısal olmayan `Content-Length` 500 + istisna metni sızdırıyordu | try/except → 400 `invalid_content_length`; 500 gövdesi artık genel mesaj |
| DUSUK-4: token karşılaştırması sabit zamanlı değil | `hmac.compare_digest` |
| DUSUK-5: metin içine gömülü sır taranmıyor | Alan düzeyi sözleşme olarak bırakıldı (README'de yazılı) |
| DUSUK-6/7: uç sayısı dokümanda yanlış; LAN kapısı yalnız CLI'da | Docstring 14 uç; `make_server` yerel olmayan bağ + token yoksa `ValueError` |
| DUSUK-8 (2. tur): ~1,07 MB üstü gövdede hâlâ broken pipe | Drain üst sınırı 8 MB'a çıkarıldı; `body_too_large` yanıtı `Connection: close` ile dönüyor; 2 MB gövde testi eklendi. 8 MB üstü gövde bilinçli olarak bağlantıyı kapatır (DoS odunleşimi) |

Her düzeltme için yeni test eklendi (6 test); toplam 64.

## Commit

`feat/v5-czip` dalında `feat(v5-5): bot cercevesi — persona+yetenek+sablon+yerel
disa/ice aktarim API'si (58 test yesil, e2e kanit)` — tam hash `git log -1`.
