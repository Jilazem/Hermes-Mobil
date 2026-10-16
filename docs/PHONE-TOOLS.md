# Telefon köprüsü araçları — referans

Köprü (`PhoneBridgeService`) ajan çağrılarını `{"id","tool","args"}` karesiyle
alır; araçlar `FullControl.advertise()` (ekran araçları) ve `PhoneTools`
(mesaj/araç kutusu) tarafından sağlanır. Aşağıda **tam kontrol** ekran
araçları — V3 mesaj araçları (`phone_messages`, `phone_reply`, `phone_task`)
için bkz. `PhoneToolSpecs.kt` ve docs/TELEFON-KONTROL.md.

## Ekran araçları (tam kontrol)

| Araç | Ne yapar | Argümanlar | Kip |
|---|---|---|---|
| `screen.dump` | Budanmış pencere ağacı + özet (JSON) | — | okuma |
| `screenshot` | JPEG+base64 kare, `width/height/scale` | — | okuma |
| `tap` / `long_press` | Koordinat, `text` ya da `id` ile dokunma | `x,y` \| `text` \| `id` | eylem |
| `swipe` | Parmağın yönüyle jest (flick dâhil) | `direction` \| `x1,y1,x2,y2` + `duration` | eylem |
| `screen.wait` | **Bekle-doğrula**: koşul sağlanana kadar süre sınırlı yoklama | `text` (görünen etiket), `app` (öndeki paket), `stable` ("true" = içerik dursun), `timeout` (saniye, varsayılan 10, tavan 30) | okuma |
| `screen.scroll_step` | Momentum'suz **içerik** kaydırma + bitiş algılama | `direction` (`down` = içerikte aşağısı = parmak yukarı; `up` = tersi), `step` (0.1–0.9, varsayılan 0.6) | eylem |
| `type` / `key` | Yazı alanı doldurma, enter/backspace/space/tab/escape | `text`, `mode=append`; `key` | eylem |
| `global` | geri / home / recents / bildirimler / hizliayarlar / lock | `action` | eylem |
| `apps.list` / `app.start` / `app.stop` | Başlatılabilir uygulamalar / aç / durdur | `app` | okuma / eylem |

## Sözleşmeler

- **`screen.wait` bloke değil:** her çağrının bir deadline'ı var; süre dolunca
  `ok=false` + o ana kadarki görünür etiketler/öndeki paket döner (`reason:
  timeout`). `stable` için ardışık 3 değişmeyen yoklama gerekir.
- **`scroll_step` ≠ `swipe`:** `scroll_step` "neyi GÖRMEK istiyorum"u söyler
  (içerik yönü), `swipe` parmak yönü ve jest niyeti taşır. `scroll_step` sabit
  adım + yavaş (450 ms) çizimle momentum üretmez; `at_end=true` = içerik
  kıpırdamadı (liste sonu).
- **Koruma:** `screen.wait` salt-okunur kipte çalışır; `screen.scroll_step` bir
  jest olduğu için salt-okunur kipte reddedilir (`guardReason` → `denied:`).
- Her iki araç da `screen.dump` gibi JSON döner; `ok` alanı "yaptım" ile
  "yapamadım"ı ayırır.