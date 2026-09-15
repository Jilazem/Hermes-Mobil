# TUR-12 — "Ses durumu CANLI + Isıt butonu" (küçük UI turu) — RAPOR

Tarih: 2026-09-15 · Repo: `/Users/gokhanuzman/hermes-workspace/wt-android-uzman`
Dal: `feat/android-uzman-devralma` · Başlangıç HEAD: `558547b` (tur-11 tepesi) · **Push YOK**.
Sözleşme: `docs/ses-api-sozlesmesi.md` (tur-12'de repoya alındı — B-4; içerik `000-TEMP` kopyasıyla AYNI).

## 1. Özet (kanıtlı)

| İş | Durum | Kanıt |
|---|---|---|
| 1. Canlı durum: bölüm görünürken `/health` **4,5 sn**'de bir + "Yenile" | **YAPILDI** | `VoiceStatusLogic.REFRESH_MS = 4_500L`; mock günlüğü 22:35:13→18→22→27→31→36→41 sn; kart açıkken 11 sn'de **3 istek**, bölümden çıkınca **0** (`faz5`) |
| 1b. Yapılandırılmış + renkli satır | **YAPILDI** | UI dökümü: `Metinleştirme: açık · Kahya: açık · Kadın: kapalı · Chatterbox: kapalı`; açık=yeşil `HermesColors.Online`, kapalı=gri `HermesColors.Offline` |
| 2. "Isıt" butonu (kapalıysa görünür → sabit cümle → `/synthesize` → yükleme) | **YAPILDI** | `ui-f2-kapali.xml` (Isıt görünür) → `ui-f2-isitiliyor.xml` ("Isıtılıyor… (~2-3 dk)" + spinner) → `ui-f2-hazir.xml`/`emulator-hazir-3.png` ("Hazır ✓") |
| 2b. Çift-tık koruması | **YAPILDI** | Isıt'a **iki kez** basıldı → mock günlüğünde **tek** `POST /synthesize` (`motin="Merhaba, sesli asistan hazır!"`) + birim testi (`isitma surerken ikinci tik ikinci istek uretmez`) |
| 2c. Tavan 300 sn + net hata | **YAPILDI** | `WARM_TIMEOUT_MS = VoiceApiEndpoints.SYNTH_TIMEOUT_MS = 300_000L`; `withTimeout` + `WarmPhase.Failed` + `warmTimeoutMsg` ("Isıtma 300 sn'de tamamlanmadı…") — testli |
| 3. "Şimdi dene": "deneniyor…" → çözümlenmiş motorlar + kapalı motor ipucu | **YAPILDI** | `ui-f3-deniyor.xml`, `emulator-kapali-ipucu.png`: "Motor kapalı — 'Isıt' ile ön-yüklersen ilk çağrı hızlı olur" |
| 4. Testler: 543 → **574** (+31), 0 hata | **YAPILDI** | `denetim/tur12/test5.log` + XML sayımı: 45 dosya, tests=574 failures=0 errors=0 skipped=0 |
| 5. Emülatör kanıtı + gerçek `/health` + **canlı `/synthesize`** | **YAPILDI** | `emulator-*.png`, `ui-*.xml`, `mock.log`, `live-synth.json` (gerçek makine) |
| 6. Rapor + ayrık commit + push yok | YAPILDI | bu dosya; `feat(tur-12)` + `docs(tur-12)` |

Kısıtlar korundu: canlı relay/gateway/caddy **değiştirilmedi** (yalnız salt-okunur `/health` ve
istenen **canlı `/synthesize`** ölçümü), telefona dokunulmadı, **push yok**.
**Kahya ölçüm sonrası AÇIK BIRAKILDI** (`health_sonra` → `{"kahya":"hazir"}`); chatterbox/kadın
gerçek motorda **yüklenmedi**.

## 2. Kapıdan gelen ekler (tur-11 verdict B-1…B-4) — kapanış

### B-1 (MEDIUM) — Canlı `/synthesize` ölçümü **YAPILDI** (`denetim/tur12/live_synth.py` → `live-synth.json`)

`http://192.168.1.101:8174` (token `~/.hermes/.env`ten adıyla; ekrana basılmadı):

| Çağrı | Sonuç |
|---|---|
| `GET /health` | **200**, 105 ms → `{"ok":true,"stt":"acik","engines":{"kahya":"hazir","chatterbox":"kapali","kadin":"kapali"}}` |
| `POST /synthesize` #1 (kahya, "Merhaba, sesli asistan hazır!") | **200**, **297.461 ms (297,5 sn)**, `audio/ogg`, **11.678 bayt**, `OggS` imzası ✓, sha256 `18f0682c…7f744b` → `denetim/tur12/live-kahya.ogg` |
| `POST /synthesize` #2 (aynı cümle, motor ısınmış) | **200**, **5.316 ms**, 11.678 bayt, sha256 `7624c313…546e` → `live-kahya-2.ogg` |
| `GET /health` (sonra) | **200**, 6 ms → `kahya:"hazir"` → **motor açık bırakıldı** |
| `GET /health` (tokensiz) | **403** `{"error":"yetkisiz"}` → fail-closed teyit |

**Bulgu (yeni risk):** gerçek soğuk ilk sentez **297,5 sn** sürdü — ses ucu ekibinin 173-187 sn
ölçümünün **üzerinde** ve istemcinin 300 sn tavanına **2,5 sn kala** tamamlandı. Yani tavan
"yeterli" değil, **kıl payı**: motor bir tık yavaş yüklenirse istemci zaman aşımına düşer.
Öneri (tur-13): `SYNTH_TIMEOUT_MS`/`WARM_TIMEOUT_MS` → **420 sn** ya da zaman aşımında
"motor arkada yükleniyor" mesajıyla otomatik **tek yeniden deneme** (ısınmış ikinci çağrı 5,3 sn).

### B-2 (LOW) — Emülatör öz-test transkripti HAM dosya olarak arşivde

`denetim/tur12/voice-selftest.txt` (tur-12 APK'sıyla **yeniden koşuldu**, `denetim/tur12/selftest_arsiv.sh`):

```
sesli mesaj oz-testi
uc: http://10.0.2.2:8199
token: var (16 karakter)
motor: kadin
1 OK health ok=true stt=acik motorlar={kahya=acik, chatterbox=kapali, kadin=kapali}
2 kayit.ogg okundu: 31774 bayt
2 OK metin (66 ms): Bu bir sesli mesaj denemesidir.
3 OK ogg indirildi: tts-kadin-803004573904755-31.ogg 31774 bayt (13 ms)
4 OYNATMA basladi=true sure=5600 ms
4 OK calma tamam (5800 ms izlendi, hala caliyor=false)
SONUC: BASARILI
```

Cihazdaki önceki kopya da ham olarak duruyor (`voice-selftest-cihazdaki.txt`), ayrıca
`diag-tur12.log`, `emulator-selftest.png`; `logcat -b crash` **boş**.

### B-3 (LOW) — Dış `/voice-api` rotası **uygulamadan** denendi: **200**

- Ayar alanına harici adres yazıldı (uygulamanın profilinden türetilen aday):
  `Çalışan adres: https://hermes.winterfell07.keenetic.pro/voice-api` (`ui-f1b-durum1.xml`,
  `emulator-canli-durum-1.png`) — durum satırı **gerçek makinenin** motor tablosunu gösterdi:
  `Metinleştirme: açık · Kahya: açık · Kadın: kapalı · Chatterbox: kapalı`
  (Kahya "açık" olması B-1'de motoru ısıtmamızla tutarlı → **gerçek sunucu, gerçek yanıt**).
- İstemci tanısı: `[voice] calisan ses ucu: https://hermes.winterfell07.keenetic.pro/voice-api`
  (22:28:21 ve 22:32:17, `diag-tur12.log`).
- Mac tarafından aynı rota (tokensiz, salt GET): **403** `{"error":"yetkisiz"}` (`ext-route.log`)
  → token kapısı dış rotada da fail-closed.
- Ortam notu: bu Mac'te `hermes.winterfell07.keenetic.pro` → `198.51.100.11` (2001:2::c633:640b)
  çözülüyor (sandbox TLS vekili); buna rağmen istek gerçek servise ulaştı (motor tablosu gerçek).

### B-4 (LOW) — Sözleşme repoya alındı

`docs/ses-api-sozlesmesi.md`, sha256 **`e5b3626e21cf11c426f5ec4ec562f8298fa918001a149567c012eb288346b16f`**
= `000-TEMP/ses-api-sozlesmesi.md` ile **AYNI**. Atıflar güncellendi:
`VoiceApiEndpoints.kt` (başlık) + `VoiceApiEndpointsTest.kt` → `docs/ses-api-sozlesmesi.md`.

## 3. Ne değişti (davranış)

### 3a. Canlı durum
- Yeni saf katman `data/VoiceStatusLogic.kt`: `/health` gövdesi → **yapılandırılmış** parçalar
  (`Chip(label, on, known)`), sıra `Metinleştirme · Kahya · Kadın · Chatterbox`, değerler
  `açık / kapalı / bilinmiyor`.
- **Eksik/boş motor bilgisi fail-closed "kapalı"** sayılır → 'Isıt' görünür.
- Türkçe harf tuzağı kapatıldı: `"AÇIK".lowercase()` yerel ayara göre `"açik"` döndüğü için
  karşılaştırma `ascii()` sadeleştirmesinden geçiyor (testli).
- `VoiceMessageController.probe()` **hata fırlatmaz**: `Probe(health, base, error, atMs)` döner;
  canlı döngü 403/ulaşılamaz durumunda düşmez, hata satırı kırmızı gösterilir.
- Kart `LaunchedEffect` ile 4,5 sn'de bir yeniler; **kart ekrandan düşünce döngü iptal edilir**
  (ölçüm: kart açıkken 11 sn'de 3 istek, çıkıştan sonra 12 sn'de **0**).
- Satır tek `AnnotatedString` olarak çizilir (etiket gri, açık yeşil, kapalı gri); "Yenile" ve
  "Şimdi dene" aynı yenileme yolunu kullanır (`inFlight` kilidi üst üste isteği engeller).

### 3b. Isıt
- `VoiceMessageController.warmEngine(engine)`: kısa sabit cümle (`WARM_SENTENCE`) →
  `transport.synthesize(...)`, `withTimeout(300 sn)`.
- Durum makinesi `VoiceStatusLogic.WarmState`: `Isıt → Isıtılıyor… (~2-3 dk) → Hazır ✓`
  (başarıda süre satırı "Motor ısıtıldı (25 sn) — ilk ses artık hızlı"), hatada
  `Yeniden dene` + kırmızı mesaj; tavan aşımında "Isıtma 300 sn'de tamamlanmadı…".
- **Çift-tık koruması iki katmanlı**: durum makinesi (`warmStart` aynı nesneyi döner) + UI'da
  ısıtma sürerken düğme yerine spinner çizilir (ikinci dokunuş düğmeye gelemez).
- Isıtma **denetleyicide** koşar (`viewModelScope`): kullanıcı bölümden çıksa da yükleme sürer,
  dönünce "Hazır ✓" görünür. Motor değişince eski işaret sıfırlanır (ısıtma sürerken sıfırlanmaz).
- Isıtma bitince durum satırı **hemen** tazelenir (`Kahya: kapalı → açık`).

### 3c. Şimdi dene
- İstek sürerken düğme "Deneniyor…", "Yenile" → "Yenileniyor…".
- Sonuç çözümlenmiş tablo; motor kapalıysa sarı ipucu: "Motor kapalı — 'Isıt' ile
  ön-yüklersen ilk çağrı hızlı olur".

## 4. Emülatör kanıtı (gerçek istemci kodu + gerçek HTTP)

Sürücü: `denetim/tur12/ui_warm.py` (uiautomator dökümü → bounds merkezine dokunuş; tahmin
koordinat YOK). İki uç kullanıldı:
1. **Gerçek makine** (uygulamanın profil adayı): `https://…/voice-api` → gerçek motor tablosu.
2. **Sözleşme mock'u** `tools/tur12/mock_voice_api.py` (tembel motorlar; ilk sentezde 25 sn
   "soğuk" taklidi, motoru `acik` yapar) — `10.0.2.2:8199`.

| Adım | Döküm/ekran | Ne kanıtlıyor |
|---|---|---|
| Kart, tüm motorlar kapalı | `ui-f2-kapali.xml` | `Metinleştirme: açık · Kahya: kapalı · …` + **`Isıt`** görünür |
| Isıt'a **iki kez** basış | `ui-f2-isitiliyor.xml` + `mock.log` | "Isıtılıyor… (~2-3 dk)" + spinner; mock'ta **tek** `POST /synthesize` (`metin="Merhaba, sesli asistan hazır!"`) |
| Isıtma bitti | `ui-f2-hazir.xml`, `emulator-hazir-3.png` | **`Hazır ✓`** + "Motor ısıtıldı (25 sn)"; canlı satır `Kahya: açık` |
| Şimdi dene + kapalı motor | `ui-f3-sonuc.xml`, `emulator-kapali-ipucu.png` | ipucu satırı + Kadın için `Isıt` düğmesi |
| Canlı yenileme | `mock.log` (22:35:13/18/22/27/31/36/41), dökümlerde sayaç `1 → 3 → 17 → 52 → 69. yoklama` | 4,5 sn aralık |
| Bölümden çıkış | `faz5` çıktısı | kart açıkken 11 sn'de **3 istek**; çıkıştan sonra 12 sn'de **0** |

Crash: `logcat -d -b crash` **boş** (hem öz-test hem UI koşularından sonra). Dökümlerdeki
"⚠ Önceki açılış bir çökmeyle kapandı… ForegroundServiceDidNotStartInTime" şeridi **tur-10
öncesi eski kayıt** (tur-11 RAPOR §4b'de de belgelendi).

## 5. Testler

`bash denetim/tur12/derle.sh testDebugUnitTest assembleDebug --rerun-tasks` → `test5.log`:

```
XML dosya sayisi: 45
GENEL: tests=574 failures=0 errors=0 skipped=0
BUILD SUCCESSFUL · TEST-EXIT=0
```

Taban (tur-11): 543 → **574** (**+31**):

| Test dosyası | tur-11 | tur-12 | Yeni kapsam |
|---|---|---|---|
| `VoiceStatusLogicTest` (yeni) | — | **25** | durum satırı biçimi/sırası, açık-kapalı-bilinmiyor, Türkçe harf sadeleştirme, `Isıt` görünürlüğü, ısıtma durum makinesi (çift tık, hata→yeniden dene, süre satırı, motor değişimi), tavan/aralık sabitleri |
| `VoiceMessageFlowTest` | 21 | **27** | `probe()` yapılandırılmış dönüş + hata yutmama, ısıtma uçtan uca (sabit cümle+motor), **çift tık koruması (kapılı sahte taşıyıcı)**, hata→bildirim→yeniden dene, motor değişiminde sıfırlama |

## 6. Teslim edilen APK

`app/build/outputs/apk/debug/app-debug.apk` — 24.303.218 bayt,
sha256 `7ada12efe64e146ef4cdb6018e9952abb052e2047a53ed90591e4cd1a531b111`.
`adb -s emulator-5554 install -r` → **Success**; yeniden kurulum sonrası duman testi
(`faz6`, `emulator-tur12-final.png`) kartı çizdi, çökme yok.
(*UI kanıtının çoğu aynı kaynak ağacının bir önceki derlemesiyle alındı; sonraki derleme yalnız
yorum satırı — sözleşme atfı — değiştiriyor, bayt boyutu aynı: 24.303.218.*)

## 7. Değişen/eklenen dosyalar

**Yeni (üretim):** `data/VoiceStatusLogic.kt` (durum satırı + ısıtma durum makinesi, saf)

**Değişen (üretim):**
- `data/VoiceMessageController.kt` — `probe()`, `warmEngine()`, `resetWarm()`, `warm` akışı; sözleşme atfı
- `ChatViewModel.kt` — `voiceProbe()`, `voiceWarm()`, `voiceWarmReset()`, `voiceWarmState`
- `ui/SettingsScreen.kt` — `VoiceProbeRow` → **`VoiceStatusCard`** (canlı 4,5 sn, renkli yapılandırılmış
  satır, "Yenile"/"Şimdi dene"/"Isıt", spinner, "Hazır ✓", ipucu); motor seçiminde ısıtma sıfırlama
- `MainActivity.kt` — yapılandırılmış probe + ısıtma kablolaması
- `data/VoiceApiEndpoints.kt`, `test/…/VoiceApiEndpointsTest.kt` — sözleşme atfı `docs/…`

**Yeni (test):** `app/src/test/java/com/hermes/mobile/VoiceStatusLogicTest.kt` (+`VoiceMessageFlowTest` genişledi)

**Kanıt/araç:** `denetim/tur12/{RAPOR.md,derle.sh,test5.log,mock_baslat.sh,mock.log,ui_warm.py,
live_synth.py,live-synth.json,live-kahya.ogg,live-kahya-2.ogg,ext_route_probe.sh,ext-route.log,
selftest_arsiv.sh,voice-selftest.txt,voice-selftest-cihazdaki.txt,diag-tur12.log,
emulator-*.png,ui-*.xml,boot_emulator.sh,pull_prefs*.sh}`, `tools/tur12/mock_voice_api.py`,
`docs/ses-api-sozlesmesi.md`

## 8. Kalan riskler / yapılmayanlar (dürüst liste)

1. **300 sn tavanı kıl payı** (ölçüm 297,5 sn). Gerçek motorla "Isıt" bu turda **denenmedi**
   (kısıt: motor ısıtma; canlı sentez ölçümü ayrı yapıldı). Öneri §B-1'de.
2. **"Yenile" düğmesi** UI'da ayrıca basılmadı; "Şimdi dene" ile **aynı** yenileme yolunu
   kullanır (otomatik döngü + "Şimdi dene" kanıtlı).
3. Kart `LazyColumn` öğesi olduğu için **ekrandan düşünce** de döngü durur (istenen davranış:
   görünmeyen bölüm için boşuna soket yok); kullanıcı kartı aşağı kaydırıp gizlerse yenileme de durur.
4. Canlı durum kartı, uç **403/ulaşılamaz** ise satır yerine hata metni gösterir — "kapalı motor"
   ile "uç yok" ayrımı korunur ama ikisinin renk kodu aynı değil (hata kırmızı).
5. Emülatörde mikrofon/telefon yok: bas-konuş hâlâ fiziksel cihaz maddesi (tur-11'den devir).
6. `diag.log`'da URL yazılırken oluşan ara istekler (`http://10.0.2.2:`) görünüyor — kullanıcı
   yazarken canlı döngü yarım adresi deniyor; zararsız ama döngü, alan **odaktayken** duraklatılabilir.

## 9. Tur-13 için öneri

- Tavan 420 sn + zaman aşımında otomatik tek yeniden deneme (ısınmış çağrı 5,3 sn ölçüldü).
- Fiziksel telefonda bas-konuş + ogg/opus gerçek kayıt kanıtı (izin akışı dahil).
- Sesli okuma kuyruğu, hız/ton ayarı; "Isıt" sonrası kısa bir deneme cümlesi çalma (kullanıcı
  motorun gerçekten konuştuğunu duysun).

---

# TUR-12b — "sentez tavanına güvenlik payı" (mini düzeltme) — RAPOR

Tarih: 2026-09-15 · Repo: `/Users/gokhanuzman/hermes-workspace/wt-android-uzman`
Dal: `feat/android-uzman-devralma` · Taban HEAD: **`c6dff3d`** (tur-12 tepesi) · **Push YOK**.

## 1. Neden (tur-12 §B-1 canlı ölçümünün doğrudan sonucu)

| Ölçüm (tur-12, gerçek makine) | Değer |
|---|---|
| Soğuk ilk `POST /synthesize` (kahya) | **297,5 sn** |
| Isınmış ikinci çağrı | **5,3 sn** |
| O günkü istemci tavanı | **300 sn** → yalnız **~2,5 sn** kil payı |

Soğuk yol **motor değişiminde / ilk kullanımda** gerçekleşir → risk gerçek, kabul edilemez
kil payı. tur-12 §9 öneri #1 (tavan 420 sn) bu turda uygulandı.

## 2. Ne değişti

| # | Değişiklik | Dosya / kanıt |
|---|---|---|
| 1 | `SYNTH_TIMEOUT_MS` **300_000 → 420_000** (=420 sn) | `data/VoiceApiEndpoints.kt:45` (tek sabit; `VoiceApiClient` okuma yolu bu sabiti kullanır → read 420 sn, call 450 sn = `+30 sn`) |
| 2 | Isıtma tavanı sabiti **takma ad** olduğu için otomatik 420 sn | `data/VoiceStatusLogic.kt:40 WARM_TIMEOUT_MS = VoiceApiEndpoints.SYNTH_TIMEOUT_MS`; `warmTimeoutMsg` metni sabitten üretilir → "Isıtma **420** sn'de tamamlanmadı…" |
| 3 | **Kayıt ve transcribe süreleri DEĞİŞMEDİ** | `MAX_RECORD_MS = 60_000L` (sözleşme <=60 sn) aynen; yükleme istemcisi `write 60 sn / call 90 sn` aynen. Test: `VoiceApiClientTest` içinde `MAX_RECORD_MS == 60_000` teyidi |
| 4 | UI bilgisi tutarlı: **"ilk yanıt 2-3 dk sürebilir (bazen 5 dk'ya kadar)"** (TR+EN) | **Isıt ipucu** `VoiceStatusLogic.coldHint`; **soğuk başlangıç uyarısı** `VoiceSpeakLogic.statusLine` (8 sn sonra); motor ipucu `VoiceSpeakLogic.engineHint(KAHYA)`. Düğme etiketi `Isıtılıyor… (~2-3 dk)` ve durumlar değişmedi |
| 5 | Yorum/Kdoc atıfları tazelendi (300→420; "tur-12 canlı ölçümü 297,5 sn") | `VoiceApiClient`, `VoiceMessageController`, `SettingsScreen`, `VoiceApiEndpoints`, `VoiceSpeakLogic`, `VoiceStatusLogic` — **davranış değişikliği yok** (yalnız yorum) |

**Başka davranış değişikliği yok:** sentez yolu dışında hiçbir sabit/mantık değişmedi;
`relay/`, `gateway`, `caddy` **hiç dokunulmadı** (bu turda okunmadı bile).

## 3. Kanıt

- **Koşum:** `denetim/tur12b/derle.sh` → `denetim/tur12b/test.log`
  (`JAVA_HOME` jdk-17 + gradle 8.9, `testDebugUnitTest assembleDebug --rerun-tasks`):
  **`BUILD SUCCESSFUL`, `GRADLE-EXIT=0`**, 41 görev — hepsi `executed` (cache yanılsaması yok).
- **Testler:** `denetim/tur12b/sayi.sh` → 45 XML · **tests=574 failures=0 errors=0 skipped=0**
  (tur-12 ile **aynı** sayı; yeni test eklenmedi, sabite bağlı testler güncellendi):
  - `VoiceApiClientTest."sentez zaman asimi guvenlik payiyla 420 sn"` → `SYNTH_TIMEOUT_MS == 420_000` + `MAX_RECORD_MS == 60_000`
  - `VoiceStatusLogicTest."isitma tavani sentez zaman asimiyla ayni 420 sn"` → `WARM_TIMEOUT_MS == SYNTH_TIMEOUT_MS == 420_000`
  - `VoiceStatusLogicTest."zaman asimi satiri tavani soyler"` → mesaj "420 sn" içerir
  - `VoiceApiEndpointsTest."sozlesme tavanlari sabit"` → `SYNTH_TIMEOUT_MS >= 420_000`, kayıt tavanı 60 sn sabit
  - UI tutarlılığı **testle bağlandı**: `coldHint` ve soğuk başlangıç uyarısı ikisi de `2-3 dk` + `5 dk` içeriyor
- **APK:** `app/build/outputs/apk/debug/app-debug.apk` — **24.303.218 bayt**,
  sha256 `fb8f52b6269901cf47ba22c372ef1e293a115a063c71d27321ac0d1afa70a2db` (`denetim/tur12b/kanit.txt`).
- **APK içeriği (uçtan uca teyit):** `denetim/tur12b/dex_teyit.sh` → `dex_teyit.txt`:
  `classes3.dex` içinde yeni metin **`bazen 5 dk` 3 kez** (coldHint + soğuk başlangıç uyarısı +
  Kahya motor ipucu) ve `sn'de tamamlanmad` (ısıtma tavanı mesajı) **var**; eski metin
  `3 dakikaya kadar` **0** → derlenen APK gerçekten yeni kopyayı taşıyor ("kaynakta değişti, APK'da yok" riski elendi).
- **Kahya servisi dokunulmadan AÇIK:** `denetim/tur12b/health.py` → `denetim/tur12b/health.json`:
  bu turda **0** `/synthesize` çağrısı; `GET /health` **200** →
  `{"ok":true,"stt":"acik","engines":{"kahya":"hazir","chatterbox":"kapali","kadin":"kapali"}}`
  (tur-12 sonundaki hâl **aynı**); tokensiz `GET /health` **403** `yetkisiz` (fail-closed teyidi).
- **Araç/kanıt dosyaları:** `denetim/tur12b/{derle.sh,test.log,sayi.sh,kanit.sh,kanit.txt,health.py,health.json,health.txt}`
  (rapor metni bu dosyada, tur-12 RAPOR.md içinde).

## 4. Kalan risk (dürüst)

1. Gerçek soğuk ölçüm (297,5 sn) bu turda **tekrarlanmadı** (kısıt: motor ısıtma) → 420 sn tavanının
   yeterliliği ölçüme göre ~**120 sn** pay ile **gerekçeli**, yeniden ölçümle teyit değil.
2. Zaman aşımında otomatik **tek yeniden deneme** (ısınmış çağrı 5,3 sn) hâlâ açık öneri (tur-13).
3. Emülatör/cihaz koşumu bu turda yapılmadı — metin/etiket değişiklikleri **saf fonksiyon** +
   birim testle bağlı (`coldHint`, `statusLine`, `engineHint`), UI dökümü kanıtı tur-12'ninkidir.

