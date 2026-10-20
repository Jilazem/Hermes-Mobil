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
- **HTTP API** (`api.py`, `server.py`): 13 uç; varsayılan 127.0.0.1:8765,
  LAN'a açarken `--token` zorunlu; bot tanımına sır yazılmaz.
- **UI şartnamesi** (`UI-SPEC.md`): cd-android için ekranlar, alan sınırları,
  uçlar, hata kodları, kabul ölçütleri.
- **E2E kanıt** (`e2e_demo.py`): gerçek HTTP + geçici SQLite ile tam yaşam döngüsü.

## Doğrulama (gerçek çıktı)

```
$ python3 -m pytest backend/bot_framework/tests -q
58 passed in 11.83s

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

## Commit

`feat/v5-czip` dalında `feat(v5-5): bot cercevesi — persona+yetenek+sablon+yerel
disa/ice aktarim API'si (58 test yesil, e2e kanit)` — tam hash `git log -1`.
