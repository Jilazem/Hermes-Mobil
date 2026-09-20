# JEV Backend Sözleşme Notu — Tur-21

Tarih: 2026-09-20 · Yazar: android botu (t_56056a61)

## Durum (kontrol edildi, 20.09.2026)

Masaüstü JEV eklentileri (`20-HERMES/plugins/jev_gate`, `jev_guard`) her
turda yerel JSON-log bırakıyor (`logs/jev_gate.log`, `logs/jev_guard.log`):

```
jev_gate:  {"ts":..., "oturum":"...", "profil":"android", "niyet":"...", "guven":1.0, "acil":0.67, "mesaj":"..."}
jev_guard: {"ts":..., "karar":"gec", "geri":0.03, "zarar":0.003, "ms":700, "komut":"..."}
```

Mobil tarafta bu veri **YOK**:
* `GET /api/sessions` yanıtında `jev` alanı yok (Models.kt şeması 20.09'da
  tek tek doğrulandı),
* gateway WS olaylarında (`GatewayWsClient`) jev alanı/olayı yok,
* log dosyaları gateway'e taşınmıyor.

## İstek (backend sözleşmesi)

**Önerilen: opsiyonel `jev` alanı — `GET /api/sessions` her oturum
kaydına eklesin** (yalnız masaüstünde jev olayı EŞLEŞEN oturumlar için):

```json
{ "id": "...", "jev": "gec" }        // gate'ten geçen tur → YEŞİL
{ "id": "...", "jev": "gozlem" }     // log-only gözlem  → SARI
{ "id": "...", "jev": "iade" }       // gate'ten dönen/iade → KIRMIZI
```

* Alan yoksa / `null` / boş → mobil rozet **çizmez** (tarafımız hazır:
  `JevBadgeLogic` boş-safe, şeması kilitli — uydurma renk testle engelli).
* Eşleştirme: `oturum` alanı ile session id (veya dbId) — masaüstü
  tarafı eşleştirme anahtarını kendi log sözlüğüyle uyumlu seçer.
* WS canlı akış opsiyonel ikinci adım: mevcut `session.*` olaylarına aynı
  değer kümesiyle `jev` eklenebilir; mobil taraf aynı parser'ı kullanır.

## Mobil taraf hazır (bu turda yapılan)

* `HermesSession.jev: String?` alanı (Models.kt) — JSON'da yoksa null.
* `JevBadgeLogic.parse` — sözlük: gec/gecis/ok/pass/passed→yeşil,
  gozlem/log/log-only/logonly/observe→sarı, iade/reddedildi/fail/failed/
  blocked→kırmızı, **bilinmeyen→yok** (fail-safe, uydurma yok).
* Çekmece satırında 7dp nokta (yeşil/sarı/kırmızı), boşken yer tutucu yok.
* Testler: `JevBadgeTest` (boş-durum + sözlük + DrawerRow taşıma +
  istatistik şeridi "veri yoksa boş satır").

Backend alanı yayınlandığında mobilde KOD DEĞİŞİKLİĞİ GEREKMEZ — veri
geldiği an rozetler kendiliğnden görünür.
