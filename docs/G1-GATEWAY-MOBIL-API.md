# G1 — Mobil ↔ Gateway API (dar kapsam: G1b oturum ucu + token başlığı)

Durum: **G1b kapandı** — oturum ucu ve token başlığı gerçek curl testiyle doğrulandı.
Kapsam dışı: kimlik doğrulama modu değişikliği, port/Caddy değişikliği, uygulama kodu.
**Bu işte hiçbir ayar/dosya değişikliği yapılmadı** (config.yaml, Caddyfile, .env dokunulmadı).

## 1. Uç nokta zinciri (gerçek durum)

    Telefon  →  http://192.168.1.99:9150   (Caddy, LAN)
             →  127.0.0.1:9120             (hermes dashboard / gateway web API)
             →  /api/sessions, /api/ws, /api/files/*

- Caddy `:9150` bloğu varsayılan olarak her şeyi `127.0.0.1:9120`'ye aktarır
  (`flush_interval -1` → WebSocket akışı açık kalır; `response_header_timeout 0`).
- `9120` yalnız loopback dinler; dışarıdan erişim YALNIZ Caddy `:9150` üzerinden.
- Gateway sürecinin `127.0.0.1:8646` portu **OpenAI-uyumlu API** ucudur; oturum ucu DEĞİL.
  Mobil istemci oraya gitmez.

## 2. Token başlığı

- REST: `X-Hermes-Session-Token: <token>` (tek ve birincil yol).
  Eski istemciler için `Authorization: Bearer <token>` de kabul edilir.
  Kaynak: `hermes_cli/web_server.py` → `_has_valid_session_token()` (sabit-zamanlı `hmac.compare_digest`).
- WS: `ws(s)://…/api/ws?token=<token>` — **sorgu parametresi**, başlık değil
  (`_ws_auth_reason()`, loopback modu; gated modda `?ticket=` / `?internal=`).
  Tarayıcı WS API'si başlık set edemediği için bu yol zorunlu.
- Token değeri: ortam değişkeni `HERMES_DASHBOARD_SESSION_TOKEN` (profil `.env`).
  Bu dosyada token **yazılmaz**; belgeye kopyalanmaz.
- İstisna: `?token=` sorgu parametresi yalnız `/api/files/download` ve
  `/api/ema/model/…` yollarında geçerli; diğer REST uçlarında başlık şart.

## 3. Kimlik doğrulama gerektirmeyen (public) /api yolları

`/api/health`, `/api/status`, `/api/config/defaults`, `/api/config/schema`,
`/api/model/info`, `/api/dashboard/themes`, `/api/dashboard/plugins`, `/api/cron/fire`.
Bunların dışındaki her `/api/*` token ister → `/api/sessions` **korumalıdır**.
(Kaynak: `hermes_cli/dashboard_auth/public_paths.py`, iki kapı da aynı listeyi kullanır.)

## 4. Oturum uçları

Okuma (mobil için yeterli):

| Yöntem | Yol | Not |
|---|---|---|
| GET | `/api/sessions` | liste; `limit` 0–100 (varsayılan 20), `offset`, `archived=exclude\|only\|include`, `order=created\|recent`, `source`/`sources`/`exclude_sources`, `cwd_prefix`, `min_messages`, `full=1`, `profile` |
| GET | `/api/sessions/{id}` | tek oturum |
| GET | `/api/sessions/{id}/messages` | mesajlar |
| GET | `/api/sessions/{id}/timeline` | zaman çizelgesi |
| GET | `/api/sessions/{id}/messages/around` | bağlam penceresi |
| GET | `/api/sessions/stats`, `/api/sessions/empty/count` | sayaçlar |

Yazma: `DELETE /api/sessions/{id}`, `POST /api/sessions/bulk-delete`,
`POST /api/sessions/import`, `POST /api/sessions/prune`, `POST /api/sessions/owner-backfill`.

**Önemli boşluk:** HTTP üzerinde "yeni oturum aç" ucu YOK. Yeni sohbet
`/api/ws?token=<token>&fresh=1` (isteğe bağlı `&profile=<ad>&cwd=<yol>`) ile açılır;
devam eden oturum `&resume=<session_id>` ile bağlanır. Mobil "yeni sohbet" düğmesi
bu yüzden WS kurmalıdır — REST ile oturum oluşturulamaz.

`limit` davranışı: sabitlenmiş (pinned) oturumlar limitten bağımsız olarak listeye eklenir;
yani `limit=3` iken 3 normal + N pinned satır dönebilir (testte 20 satır: 3 + 17 pinned).

## 5. Kanıt — tek curl testi (gerçek çıktı)

    TOKEN=$(ps eww -p <dashboard-pid> | ... HERMES_DASHBOARD_SESSION_TOKEN)
    curl -sS -o g1b_sessions.json -w 'HTTP=%{http_code} bytes=%{size_download}\n' \
         -H "X-Hermes-Session-Token: $TOKEN" \
         "http://127.0.0.1:9120/api/sessions?limit=3"

Sonuç (22.10.2026):

    HTTP=200 bytes=37200
    limit=3 offset=0 total=339 n=20 pinned=17

- Token başlığı kabul edildi → 200. Başlıksız çağrının 401 döneceği kodla sabittir
  (`/api/sessions` public listesinde değil, `auth_middleware` token ister); bu işte
  ikinci bir curl yapılmadığı için ayrıca test edilmedi.
- Gövde anahtarları: `limit, offset, sessions, total, storage` → beklenen şema.
- Oturum satırı alanları gerçek veri içeriyor (`id`, `title`, `archived`, `pinned`, maliyet/token alanları).
- Ham gövde: `~/.hermes/profiles/hermes-ustasi/cache/scratch/g1b_sessions.json` (token içermez).

Not: bu tek istek loopback'e (`127.0.0.1:9120`) yapıldı; LAN yolu (`:9150` → Caddy) aynı
arka uca gider, ancak telefon üzerinden ayrı bir test yapılmadı — bu belge onu iddia etmez.

## 6. Geri alma

Değişiklik yok → geri alma yok. Yalnız bu belge ve scratch JSON üretildi.
Bu iş kapsamında hiçbir servis yeniden başlatılmadı, hiçbir yapılandırma yazılmadı.

## 7. Mobil tarafı eşleşmesi (kod kanıtı)

- `app/src/main/java/com/hermes/mobile/data/HermesClient.kt:581` → `SESSION_HEADER = "X-Hermes-Session-Token"` (satır 69 ile her isteğe ekleniyor).
- `ServerProfile.kt:24` → taban adres örneği `http://192.168.140.20:9150`.
- WS bağlantısı `?token=` sorgusuyla kuruluyor (commit `98d93c8` "WS baglanti tek kaynak + ws->http sema + ?token= sorgu").
