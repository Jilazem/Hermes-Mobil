# V5-5 — Botlar (custom bot) UI şartnamesi — cd-android için

Bu belge Hermes Mobil "Botlar" alanının Android tarafı sözleşmesidir.
Backend `backend/bot_framework/` hazırdır (stdlib, dış bağımlılık yok).

## 0. İlke

Bot = ad + emoji + persona (sistem talimatı) + yetenekler (araç seti) +
varsayılan model/think modu + karşılama mesajı. Bot tanımı **sır taşımaz**
(token/şifre/API anahtarı alanı yoktur; backend `secret_not_allowed` döner).
Genel paylaşım YOKTUR: dışa/içe aktarma yalnız yerel dosya (JSON) iledir.

## 1. Ekranlar

### 1.1 Botlar listesi (BotRosterScreen)
- Üstte "＋ Yeni bot" düğmesi.
- Kart: emoji · ad · kısa açıklama (persona ilk 80 karakter) · araç rozetleri.
- Boş durum: "Henüz bot yok — şablondan başla" + 3 şablon çipi.
- Uzun basış / sağ üst menü: Düzenle · Dışa aktar · Sil (onay dialogu).
- `GET /api/bots` → `{bots:[Bot]}`

### 1.2 Bot oluştur/düzenle (BotEditScreen)
Alanlar ve sınırlar (backend doğrular, UI aynı sınırı gösterir):

| Alan | Zorunlu | Sınır |
|---|---|---|
| Ad | evet | 1–48 karakter |
| Emoji | hayır (varsayılan 🤖) | ≤ 8 karakter |
| Persona (sistem talimatı) | evet | 1–8000 karakter, sayaç göster |
| Yetenekler | hayır | katalogdan çoklu seçim, en çok 12 |
| Model | hayır | boş = oturum modeli, ≤ 120 |
| Think modu | hayır | off / low / medium / high (varsayılan off) |
| Karşılama mesajı | hayır | ≤ 500 |

- Yetenek listesi `GET /api/templates` yerine sabit katalogdan değil,
  backend'in `TOOL_CATALOG` çıktısından gelir (uç: `GET /api/tools` — aşağıya bak).
- Kaydet: yeni → `POST /api/bots`, var olan → `PATCH /api/bots/{id}`.
- 400 gövdesi `{error, message}`: `message` alanı doğrudan alan altında gösterilir
  (ör. `secret_not_allowed`, `tools_unknown`, `persona_blank`).
- Şablondan başlat: `POST /api/bots/from-template {key, overrides}`.

### 1.3 Botla sohbet
- Bot kartından "Sohbet" → `POST /api/bots/{id}/chat {message}` (ilk tur, yanıt
  içindeki `session_id` saklanır).
- Sonraki turlar: `POST /api/bots/{id}/chat {message, session_id}`.
- Yanıt: `{session_id, bot_id, bot_name, reply, system_prompt, messages, welcome_message}`.
- UI `messages` dizisini olduğu gibi çizer; `role=system` balonu **gösterilmez**
  ama "Persona" bilgi sayfasında `system_prompt` okunabilir olmalı (kabul kanıtı:
  persona sohbete gerçekten enjekte edildi).
- Karşılama mesajı ilk `assistant` turu olarak gelir; ayrıca gösterilmez.
- Oturum geçmişi: `GET /api/sessions?bot_id={id}`, `GET /api/sessions/{id}`.

### 1.4 Dışa/içe aktarma
- Dışa: `GET /api/bots/{id}/export` → `{format:"hermes-bot", version:1, bot:{...}}`
  → Android Sharesheet / SAF ile `.hermes-bot.json` olarak kaydet.
- İçe: dosya seç → JSON oku → `POST /api/bots/import` (gövde = dosyanın kendisi).
  Yeni kimlik üretilir; aynı paket iki kez içe alınabilir.
- Hata kodları: `format_unknown`, `version_invalid`, `schema_unsupported`,
  `secret_not_allowed`, `unknown_field` → kullanıcıya Türkçe mesaj.

## 2. Uç noktalar (özet)

```
GET    /health
GET    /api/templates
GET    /api/tools                       # araç kataloğu (ad → açıklama)
GET    /api/bots                        # liste
POST   /api/bots                        # oluştur
POST   /api/bots/from-template          # şablondan oluştur
GET    /api/bots/{id}
PATCH  /api/bots/{id}                   # kısmi güncelleme
DELETE /api/bots/{id}                   # bot + oturumları (204)
GET    /api/bots/{id}/export
POST   /api/bots/import
POST   /api/bots/{id}/chat              # {message, session_id?}
GET    /api/sessions?bot_id={id}
GET    /api/sessions/{id}
```

Hata gövdesi her yerde: `{"error": "<kod>", "message": "<Türkçe metin>"}`.
HTTP: 400 doğrulama, 401 yetkisiz, 404 bulunamadı, 405 yöntem, 500 iç hata.

## 3. Ağ / güvenlik
- Varsayılan bağ `127.0.0.1:8765`; LAN'a açılacaksa `--token` zorunludur ve
  istekler `Authorization: Bearer <token>` taşır. Token cihaz ayarında
  (Keystore/EncryptedSharedPreferences) tutulur, bot tanımına yazılmaz.
- Android emülatörde ana makine: `http://10.0.2.2:8765`; gerçek cihazda LAN IP.

## 4. Kabul ölçütleri (Android)
1. Şablondan bot oluşturma; listede görünme.
2. Botla ilk mesaj → persona'lı yanıt; ikinci mesaj aynı oturumda.
3. Düzenleme sonrası sohbetin yeni persona ile açılması.
4. Dışa aktar → dosya; içe aktar → yeni bot (kimlik farklı).
5. Silme onayı sonrası bot ve oturumlarının listeden gitmesi.
6. Ekran görüntüleri: liste, düzenle, sohbet, export dosyası.
