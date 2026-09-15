# TUR-11 — Sesli mesaj v1 (uygulama içi ses girişi + sesli okuma) — RAPOR

Tarih: 2026-09-15 · Repo: `/Users/gokhanuzman/hermes-workspace/wt-android-uzman`
Dal: `feat/android-uzman-devralma` · Başlangıç HEAD: `21ba0a3` (main ile aynı) · Push YOK.
Sözleşme: `000-TEMP/ses-api-sozlesmesi.md` (uygulama ↔ Mac ses hattı, v1).

## 1. Özet (kanıtlı)

| İş | Durum | Kanıt |
|---|---|---|
| 1. Bas-konuş mikrofon (izin akışı; MediaRecorder → ogg/opus 16k mono) → `/transcribe` → metin sohbet girdisine | **YAPILDI** | `Composer.kt` (HoldToTalkButton), `VoiceAudioPorts.kt`, emülatör koşusu 2. adım |
| 1b. Ayar "otomatik gönder" (varsayılan **KAPALI**) | YAPILDI | `AppSettings.voiceAutoSend=false`, `VoicePrefsTest` |
| 2. Sesli okuma: asistan balonuna uzun bas **veya** hoparlör ikonu → `/synthesize` → indir → MediaPlayer çal/durdur | YAPILDI | `ChatScreen.kt` (combinedClickable + VolumeUp/Stop), emülatör koşusu 3-4. adım |
| 2b. Ayar motor `kahya\|chatterbox\|kadin` (varsayılan **kahya**) | YAPILDI | `AppSettings.voiceEngine="kahya"`, Ayarlar → Canlı ses → Sesli mesaj |
| 3. Uç adresi SparkEndpoints deseni (LAN :8174 / dış `/voice-api`), çalışanı hatırla, kimlik = oturum tokeni | YAPILDI | `VoiceApiEndpoints.kt`, `VoiceApiClient.kt` (AddressHealth entegre), `live-probe.json` (canlı uç 200 / tokensiz 403) |
| 4. Testler: birim + emülatör (mock python fixture, kayıt dosyası asset'ten) | YAPILDI | 543 test / 0 hata (taban 442 → **+101**), `emulator_selftest.sh` çıktısı |
| 5. Kanıt: `denetim/tur11/RAPOR.md` + loglar | YAPILDI | bu dosya + `denetim/tur11/*` |

Kısıtlar korundu: canlı relay/gateway/telefon **dokunulmadı**, push **yok**.
Canlı ses hattına yalnız **salt-okunur** iki çağrı yapıldı (`GET /health`, `POST /transcribe`).

## 2. EKLER'in (ses ucu ekibi, 15.09 akşam) uygulanışı

| Ek | Nerede uygulandı |
|---|---|
| İlk sentez SOĞUKKEN 173-187 sn → istemci timeout ≥300 sn | `VoiceApiEndpoints.SYNTH_TIMEOUT_MS = 300_000`; sentez yolu ayrı OkHttp istemcisi (read 300 sn, call 330 sn), yükleme yolu 60/90 sn. Test: `VoiceApiClientTest."sentez zaman asimi sozlesme geregi 300 sn"` |
| UI'da "ilk yanıt uzun sürebilir" yükleme durumu | `VoiceSpeakLogic.statusLine` — indirme 8 sn'yi geçince "İlk yanıt uzun sürebilir: ses motoru şimdi açılıyor (soğukken 3 dakikaya kadar)"; ayrıca balonda döner gösterge. Test: `VoiceSpeakLogicTest."soğuk motor uyarisi 8 sn sonra cikar"` |
| /health motoru ısıtmaz | `/health` ayrı yoldan çağrılıyor, motor durumu yalnız gösteriliyor; ısıtma amaçlı çağrı YOK |
| Ses yükleme <=60 sn → kayıt 60 sn'de otomatik dursun | `VoiceRecordLogic.MAX_RECORD_MS=60_000` + tik döngüsü tavanı görünce `holdRelease()` çağırarak parmak basılıyken bile yükler. Test: `VoiceRecordLogicTest."60 sn dolunca kendiliginden durur"`, `VoiceMessageFlowTest."60 sn tavani dolunca kayit kendiliginden yuklenir"` |
| Motorlar tembel (kahya :8172 / chatterbox :8173), kapalıysa servis açar, ilk çağrı yavaş | İstemci "kapalı motor" durumunu hata saymıyor; sentez doğrudan `/synthesize`e gidiyor ve bekleme satırı gösteriliyor |
| Üretim önerisi: ana motor kahya, kadın için kadin, chatterbox referanssız kararsız | Varsayılan `kahya`; `kadin` ikinci sırada "üretimde kadın ses için önerilen"; `chatterbox` etiketi **"(deneysel)"** + ipucu "referanssızken kararsız (bant geziyor)" |

## 3. Sözleşme uyumu (AYNEN uygulandı)

| Sözleşme maddesi | Uygulama |
|---|---|
| Yerel `http://<host>:8174` · dış `https://<host>/voice-api` | `VoiceApiEndpoints.candidates()` (özel adres → `:8174`, genel adres → `/voice-api`; sıra: son çalışan → profil adresleri) |
| Kimlik `X-Hermes-Session-Token`, yanlış/eksikse 403 | Her istekte başlık (`HermesClient.SESSION_HEADER`); 403/401 → "token eksik/yanlış, Ayarlar → Sunucular'dan tazele" mesajı |
| `GET /health` → `{ok, stt, engines{kahya,chatterbox,kadin}}` | `VoiceHealth` + `VoiceApiEndpoints.healthLine()` (Ayarlar → "Şimdi dene" satırı) |
| `POST /transcribe` multipart **`audio`**, ogg/opus, ≤60 sn → `{text, lang}` | `VoiceApiClient.transcribe` (`addFormDataPart("audio", "kayit-<ts>.ogg", …)`); mock sunucu günlüğü: `alan_audio=True dosya=kayit.ogg ogg_imza=True` |
| `POST /synthesize` JSON `{text, engine}` → gövde `audio/ogg` baytları | `VoiceApiClient.synthesize`; mock günlüğü: `motor=kadin metin_uzunluk=31`; emülatörde 31774 bayt ogg indirildi ve MediaPlayer ile çalındı |
| Hatalar: `{"error": "..."}` + kod | `VoiceApiException` (kod + yol + kullanıcıya gösterilen tek cümle) |

## 4. Emülatör kanıtı (mock fixture ile uçtan uca)

Mock: `tools/tur11/mock_voice_api.py` (stdlib; sözleşmeyi birebir uygular, token kapısı ve
"soğuk ilk sentez" gecikmesi taklit edilir). Uygulama tarafında **gerçek istemci kodu**
(`VoiceApiClient` + `VoiceMessageController` + `AndroidVoicePlayer`) koştu; kayıt dosyası
asset'ten geldi (`app/src/debug/assets/sesli/kayit.ogg`, 31774 bayt, gerçek Opus/OGG).

Koşum: `bash denetim/tur11/emulator_selftest.sh` (emulator-5554, `com.hermes.mobile.v2`) —
çıktı (`files/voice-selftest.txt`):

```
sesli mesaj oz-testi
uc: http://10.0.2.2:8199
token: var (16 karakter)
motor: kadin
1 OK health ok=true stt=acik motorlar={kahya=kapali, chatterbox=kapali, kadin=kapali}
2 kayit.ogg okundu: 31774 bayt
2 OK metin (33 ms): Bu bir sesli mesaj denemesidir
3 OK ogg indirildi: tts-kadin-803004573904755-31.ogg 31774 bayt (3505 ms)
4 OYNATMA basladi=true sure=5600 ms
4 OK calma tamam (5800 ms izlendi, hala caliyor=false)
SONUC: BASARILI
```

Sunucu tarafı günlüğü (`denetim/tur11/mock.log`):

```
21:39:39 200 POST /transcribe · multipart=True alan_audio=True dosya=kayit.ogg ogg_imza=True boyut=31976
21:39:40 SOGUK sentez: 3.0 sn motor isitma taklidi (motor=kadin)
21:39:43 200 POST /synthesize · motor=kadin metin_uzunluk=31 oggg_bayt=31774
```

Ek: `denetim/tur11/emulator-sesli-mesaj.png` (ekran görüntüsü), crash tamponu **boş**
(`adb logcat -d -b crash` boş), uygulama tanısında `calisan ses ucu: http://10.0.2.2:8199`.

## 5. Canlı uca salt-okunur rozet (sözleşme gerçek sunucuda doğrulandı)

`python3 denetim/tur11/live_probe.py` → `denetim/tur11/live-probe.json`
(192.168.1.101:8174 koşuyor; token `~/.hermes/.env`ten adıyla okundu, ekrana basılmadı):

| Çağrı | Sonuç |
|---|---|
| `GET /health` tokensiz | **403** `{"error":"yetkisiz"}` → fail-closed doğrulandı |
| `GET /health` tokenli | **200** `{"ok":true,"stt":"acik","engines":{…}}` (32 ms) → istemcinin beklediği şema |
| `POST /transcribe` tokenli (aynı ogg) | **200** gerçek whisper metni (`"Mesajınızı aldım. Şunu duydum…"`, 3025 ms) |

Canlı `/synthesize` **çağrılmadı**: motorlar kapalı ve soğuk ilk sentez 173-187 sn
(ses ucu ekibinin ölçümü) — görev kısıtı gereği canlı motorda ısıtma yapılmadı,
sentez yolu mock ile uçtan uca kanıtlandı.

## 6. Testler

Koşum: `bash denetim/tur11/derle.sh testDebugUnitTest assembleDebug --rerun-tasks`
(gradle logu: `denetim/tur11/test4.log`)

```
XML dosya sayisi: 44
GENEL: tests=543 failures=0 errors=0 skipped=0
BUILD SUCCESSFUL
```

Taban (21ba0a3): **442** → yeni **543** (+101). Yeni dosyalar:

| Test dosyası | # | Kapsam |
|---|---|---|
| `VoiceApiEndpointsTest` | 23 | aday sıralaması, elle adres, hatırlanan adres normalizasyonu (çift `/voice-api` yok), URL kurulumu, 403/404/erişilemez mesajları, tavan sabitleri |
| `VoiceRecordLogicTest` | 20 | bas-konuş durum makinesi: yarışlar, 0,8 sn atma, 60 sn otomatik durma, etiket/ilerleme |
| `VoiceSpeakLogicTest` | 20 | motor çözümü/varsayılan, markdown sadeleştirme, kırpma, önbellek adı, aynı balona ikinci dokunuş, soğuk uyarısı |
| `VoiceMessageFlowTest` | 21 | uçtan uca akış (sahte portlar): kayıt→metin, 60 sn tavanı, kısa basış, hata yolları, sentez→dosya→çalma, önbellek |
| `VoiceApiClientTest` | 13 | **gerçek soket**: `/health`, multipart alan adı+mime, ogg baytları bozulmadan, JSON gövdesi `engine`, 403/404 eşlemesi, iki adresli düşüş |
| `VoicePrefsTest` | 4 | varsayılanlar (otomatik gönder kapalı, motor kahya) ve ayar→tercih çözümü |

## 7. Değişen/eklenen dosyalar

**Yeni (üretim):**
- `data/VoiceApiEndpoints.kt` — adaylar, URL'ler, hata metinleri (saf)
- `data/VoiceApiClient.kt` — OkHttp istemcisi (300 sn sentez / 60 sn yükleme), `VoiceTransport` arayüzü
- `data/VoiceRecordLogic.kt` — kayıt durum makinesi (saf)
- `data/VoiceSpeakLogic.kt` — motor/soğuk-uyarı/önbellek/markdown (saf)
- `data/VoiceMessageController.kt` — akış orkestrasyonu (port tabanlı, JVM'de test edilebilir)
- `data/VoiceAudioPorts.kt` — `AndroidVoiceRecorder` (ogg/opus 16 kHz mono; API<29'da m4a/AAC'ye düşer), `AndroidVoicePlayer`

**Değişen:**
- `data/AppSettings.kt` — `voiceAutoSend`, `voiceEngine`, `voiceUrl`, `voiceLastOk` + `VoicePrefs`/`toVoicePrefs()`
- `ChatViewModel.kt` — `voiceMsg` denetleyicisi, `voicePrefill` akışı, bas-konuş/seslendirme uçları, teardown'da ses durdurma
- `MainActivity.kt` — durum toplama, ayar→tercih, çalışan adresi hatırlama, ChatScreen/SettingsScreen bağlantıları
- `ui/Composer.kt` — bas-konuş düğmesi + kayıt satırı (kırmızı nokta, sayaç, ipucu); "Canlı ses" ek menüsüne taşındı
- `ui/ChatScreen.kt` — asistan balonunda uzun basma + hoparlör ikonu, seslendirme durum satırı, taslak ön-doldurma
- `ui/SettingsScreen.kt` — "Sesli mesaj (uygulama içi)" bölümü: motor, otomatik gönder, uç adresi, "Şimdi dene"
- `app/src/debug/AndroidManifest.xml` + `ui/VoiceSelfTestActivity.kt` + `app/src/debug/assets/sesli/kayit.ogg` (yalnız debug)

**Kanıt/araç:** `tools/tur11/mock_voice_api.py`, `denetim/tur11/{derle.sh,emulator_selftest.sh,live_probe.py,mock.log,live-probe.json,emulator-sesli-mesaj.png,test4.log}`

## 8. Kalan riskler / yapılmayanlar (dürüst liste)

1. **Canlı `/synthesize` denenmedi** (motorlar kapalı + soğuk 173-187 sn). Sentez yolu mock'ta
   uçtan uca, istemci tarafı gerçek soket testiyle doğrulandı; canlı motor ilk çağrısının
   gerçek süresi bu turda ölçülmedi.
2. **Dış `/voice-api` rotası** uygulamadan denenmedi (Caddy token kapısı + dış ağ); aday
   üretimi testlerle, LAN yolu canlı probe ile doğrulandı.
3. **Gerçek mikrofon kaydı** emülatörde koşulamaz (emülatörde mikrofon yok) — kayıt yolu
   `MediaRecorder` sarmalayıcısı + durum makinesi testleriyle ve asset dosyasıyla dolaylı
   doğrulandı; fiziksel telefonda bas-konuş denenmeli (tur-12).
4. **API<29 cihazlarda** ogg/opus yazılamıyor → m4a/AAC'ye düşülüyor (mime `audio/mp4`).
   Sunucu whisper'ı AAC çözer; yine de telefonda teyit edilmeli.
5. "Canlı sesli sohbet" (Gemini Live) girişi Composer mikrofonundan **ek menüsüne** taşındı;
   başlıktaki kulaklık ikonu da aynı sayfayı açıyor (erişim kaybı yok, ama keşfedilebilirlik azaldı).
6. Otomatik gönder **kapalı** olduğundan sesli mesaj metni taslağa yazılır; taslak doluyken
   metin altına eklenir (üstüne yazmaz) — kullanıcı bunu beklemeyebilir.

## 9. Tur-12 için öneri

- Fiziksel telefonda bas-konuş + ogg/opus gerçek kayıt kanıtı (izin akışı dahil).
- Canlı `/synthesize` ölçümü: soğuk ilk çağrı süresi + 300 sn tavanının yeterliliği.
- Dış rota (`/voice-api`) uygulamadan uçtan uca; 403 mesajının sahadaki karşılığı.
- Sesli okuma kuyruğu (birden fazla mesajı sırayla okuma) ve hız/ton ayarı.
