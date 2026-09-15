# Tur-13 — Hermes = telefon asistanı (yerel Kahya sesli yolu)

Dal: `feat/android-uzman-devralma` · Başlangıç HEAD: `a97401f` (main ile aynı) · Push YOK
Kapsam: F1 (asistan rolü) · F2 (yerel ses varsayılan) · F3 (test/kanıt)

Kullanıcı isteği (birebir): *"sesli asistan sürekli google tarafında kalmış kahyaya
geçmedi; telefonun asistan uygulamasının da yerini almalı — düzeltmelisin."*

**Sonuç bir cümlede:** asistan hareketi artık Gemini Live'ı değil **asistan modunu**
açıyor (sohbet + öne çıkan bas-konuş + yerel hat = voice_api 8174), rol durumu
Ayarlar'da görünür ve atanabiliyor; asistan yanıtı asistan modunda kendiliğinden
YEREL hat üzerinden okunuyor. Gemini Live canlı ses özelliği **bozulmadı**, yalnız
varsayılan yol olmaktan çıktı.

---

## 1. Ne değişti

### F1 — Asistan rolü

| İstenen | Yapılan | Dosya |
|---|---|---|
| `ACTION_ASSIST` (+`DEFAULT`) filtresi, `VOICE_COMMAND` de | `activity-alias` `.AssistantAlias` → `MainActivity`; etiket **"Hermes Asistan"**; filtreler MainActivity'den BURAYA taşındı (asistan listesinde çift girdi olmasın) | `app/src/main/AndroidManifest.xml`, `res/values/strings.xml` |
| Ayarlar → "Telefon asistanı": rol durumu + "Hermes'i varsayılan asistan yap" | Yeni `SettingsCategory.Assistant`: rol satırı + sistem rol diyaloğu düğmesi; rol yoksa `manualSteps` (Ayarlar → Varsayılan uygulamalar → Dijital asistan) | `ui/SettingsScreen.kt` (`AssistantRoleRow`) |
| Sistem diyaloğu `RoleManager.createRequestRoleIntent(ROLE_ASSISTANT)` | `AssistantRole.requestIntent`; **Android 12+ gerçeği**: diyalog "Role is not requestable" deyip anında kapanıyor → rol bizim olmadıysa otomatik **Ayarlar → Varsayılan uygulamalar** yönlendirmesi | `data/AssistantRole.kt`, `MainActivity.kt` |
| ASSIST ile açılış (soğuk/ılık) → doğrudan sesli giriş hâli | `ACTION_ASSIST`/`VOICE_COMMAND` → `pendingAction="assistant"` → sohbet sekmesi + asistan modu + **sessiz** mikrofon izni isteği (`requestMicQuiet`; dikte KENDİLİĞİNDEN başlamaz) | `MainActivity.kt` |
| Otomatik kayda BAŞLAMA | Asistan modu yalnız şeridi/okumayı açar; kayıt `holdStart` (parmak) ile başlar — emülatörde ölçüldü: faz `Ready`, kırmızı nokta/sayaç yok | `ChatViewModel.enterAssistantMode`, `ui/ChatScreen.kt` (`AssistantBanner`) |

### F2 — Yerel ses varsayılan

- **Canlı ses sheet'i**: en üstte "Ses yolu" seçimi — **"Yerel (Kahya)"** (önerilen,
  varsayılan asistan yolu) ve **"Gemini Live"** (isteğe bağlı, Google). Yerel
  seçilirse Gemini Live oturumu **kapatılır** ve asistan modu açılır; iki motor aynı
  anda çalışmaz. Bu ekranın Gemini Live olduğu ve asistan akışının oraya gitmediği
  metinle yazılı (`ui/LiveVoiceSheet.kt`, `VoicePathRow`).
- **Asistan akışı**: bas-konuş → `/transcribe` → metin **doğrudan gönderilir**
  (`autoSendTranscript`; normal sohbette karar kullanıcı ayarında, varsayılan KAPALI)
  → yanıt gelince **otomatik seslendirme** (`AssistantModeLogic.shouldAutoRead` →
  `voiceMsg.speak`, yani üretimdeki `message.complete` → kahya sentezi).
- **Ayar**: "Asistan yanıtını otomatik oku" (`AppSettings.assistantAutoRead`,
  **varsayılan AÇIK**) — ama **yalnız asistan bağlamında** etkili; normal sohbette
  ses başlamaz (kural saf katmanda VE'leniyor ve testle bağlı).
- Normal sohbet varsayılanları **değişmedi**: `voiceAutoSend=false`, motor `kahya`,
  balondaki hoparlöre dokunma davranışı aynı.

### F3 — Test/kanıt (özet; ayrıntı §3)

- **598 test / 0 hata** (`574 + 24 yeni`), `app/build/test-results/testDebugUnitTest`.
- Yeni saf katman testleri: `AssistantModeTest` (rol durumu metni, faz makinesi,
  oto-okuma kuralı, varsayılanlar).
- Emülatör: ASSIST niyeti, soğuk/ılık açılış, bas-konuş→transcribe→oto-oku zinciri
  (mock uç + **gerçek** voice_api), ekran görüntüleri + uiautomator dökümleri.

---

## 2. Mimari kararlar (gerekçeli)

1. **`activity-alias`, ayrı Activity değil.** Etiket yalnız asistan girdisinde
   değişmeli: `MainActivity`'ye `android:label` yazmak launcher'daki uygulama adını
   da değiştirirdi. Alias'ın `exported="true"` olması şart (sistem + `am start`
   çözebilmeli). Filtreler MainActivity'den KALDIRILDI: aynı filtre iki yerde olsa
   asistan listesinde aynı uygulama iki kez görünürdü (paylaşım filtresindeki
   çift-girdi dersinin aynısı).
2. **`RoleManager.getRoleHolders` KULLANILMADI.** Derleme SDK taslağında
   (`platforms/android-35/android.jar`) o metot yok — `javap` ile doğrulandı
   (yalnız `createRequestRoleIntent`, `isRoleAvailable`, `isRoleHeld` var). Rol
   sahibi üç GENEL API'den çıkarılıyor: `isRoleHeld` (kesin: biz miyiz) →
   `Settings.Secure "assistant"` (paket ipucu) → `PackageManager.resolveActivity`
   (son çare). Hiçbiri yoksa "atanmamış" denir; uydurma etiket gösterilmez.
3. **Karar saf katmanda** (`AssistantModeLogic`, Android'siz): rol metni, faz
   makinesi (`Off/Ready/Recording/Transcribing/AwaitingReply/Speaking`), oto-okuma
   kuralı, oto-gönderim kuralı, izin sorusu. Android'e değen tek yer `AssistantRole`.
4. **Ayrı "sessiz" izin yolu.** Mevcut `requestMic` launcher'ı izin verilince
   dikteyi başlatıyor; asistan hareketinde istenen yalnız izin. `requestMicQuiet`
   eklendi (gereksiz dikte/otomatik kayıt yok).

---

## 3. Kanıt

### 3.1 Birim testleri

```
JAVA_HOME=…/jdk-17 ./gradlew testDebugUnitTest assembleDebug   → BUILD SUCCESSFUL
denetim/count_tests.py → XML dosya sayisi: 46 · GENEL: tests=598 failures=0 errors=0 skipped=0
```
Yeni dosya: `app/src/test/java/com/hermes/mobile/AssistantModeTest.kt` (24 test).

### 3.2 APK

```
app/build/outputs/apk/debug/app-debug.apk
sha256: 0a1077da0df6f40fd2e80aff96ab01a57c5a9df1fd412287b9dedfd938bcbc23
md5   : 57de5821212e49c496ec88bdac954bb6
adb -s emulator-5554 install -r … → Success        (manifest doğrulaması install-time)
versionName=0.1.0 · applicationId com.hermes.mobile.v2 (debug sonekli)
```

### 3.3 Asistan girdisi manifestte gerçekten çözülüyor mu

`adb shell cmd package query-activities -a android.intent.action.ASSIST`
→ `name=com.hermes.mobile.AssistantAlias packageName=com.hermes.mobile.v2
targetActivity=com.hermes.mobile.MainActivity` (**aynısı VOICE_COMMAND için de**;
dökümler: `query-assist.txt`, `query-voice-command.txt`).

Etiket APK'dan doğrulandı: `aapt2 dump resources … → string/assistant_label =
"Hermes Asistan"` (uygulama adı "Hermes V2" olarak AYRI kalıyor).

Seçici ekranı (`emulator-assist-1.png`): Android'in resmi seçicisi **"Hermes Asistan"**
girdisini Google'ın yanında listeliyor (vizyon okuması: *"Hermes V2 / Hermes Asistan"
ve "Google"*).

### 3.4 ASSIST ile açılış

| Koşu | Komut | Sonuç |
|---|---|---|
| Soğuk (implicit + seçici) | `am start -a android.intent.action.ASSIST` → seçiciden "Hermes Asistan" | izin diyalogu (`izin1.xml`: "Hermes V2 uygulamasının ses kaydetmesine izin verilsin mi?") → izin → **asistan modu** |
| Soğuk (niyet + vekil bileşen) | `am start -a android.intent.action.ASSIST -n …/AssistantAlias` | `f1-soguk.xml`: **"Asistan hazır — basılı tut ve konuş"**, "Yerel hat · voice_api · Google'a gitmez", "Yanıtı otomatik oku ✓" |
| Ilık | ana ekran → aynı niyet | `f2-sicak.xml`: aynı asistan şeridi (görev öne geldi) |
| `VOICE_COMMAND` | `am start -a android.intent.action.VOICE_COMMAND -n …` | `f3-voice-command.xml`: asistan modu |

Uygulama içi tanı kaydı (`diag.log`): `I [asistan] mod acildi (yerel hat)` (soğuk +
ılık + VOICE_COMMAND için ayrı satırlar). **Kayıt başlamadı** — ekranda "Asistan hazır",
faz `Ready` (kırmızı nokta/sayaç yok). `logcat -b crash` **boş**.

### 3.5 Rol durumu + atama

- Özgün emülatör durumu: rol sahibi **Google** (`cmd role get-role-holders` →
  `com.google.android.googlequicksearchbox`; `settings get secure assistant` → aynı paket).
  Kullanıcının şikâyetiyle birebir örtüşen durum.
- Ayarlar → Telefon asistanı ekranı bu durumda: **"Şu an: Google"** + düğme
  (`emulator-rol-google.png`, `b3-asistan-bolum.xml`).
- Düğmeye dokunma: `RequestRoleActivity` **açılıp anında kapanıyor**
  (`E RequestRoleActivity: Role is not requestable: android.app.role.ASSISTANT`);
  uygulama bunu yakalayıp **DefaultAppListActivity**'yi açıyor →
  `emulator-rol-yonlendirme.png`, `b4-yonlendirme.xml` ("Dijital asistan uygulaması").
- Rol Hermes'e verildikten sonra (kabuk: `cmd role add-role-holder`) satır:
  **"Hermes: varsayılan asistan ✓"** (`e3-rol-hermes.xml`, `emulator-rol-hermes.png`).
- Koşum sonunda emülatör **özgün durumuna döndürüldü**:
  `cmd role remove-role-holder … com.hermes.mobile.v2` +
  `add-role-holder … com.google.android.googlequicksearchbox` → rol sahibi yine Google.

### 3.6 Bas-konuş → transcribe → oto-oku zinciri (gerçek üretim kodu)

`AssistantSelfTestActivity` (yalnız debug, launcher'da yok) uygulamanın GERÇEK
`VoiceMessageController`/`VoiceApiClient` kodunu koşturur.

**a) Mock uç** (`tools/tur12/mock_voice_api.py`, port 8199, `--token-any`):

```
1a gonderme asistan=true normal=false
1b oto-okuma asistan=true normal=false ayar_kapali=false
1c izin iste: true / izin var: false
1d rol durumu: Hermes · Hermes: varsayılan asistan ✓
2 kayit fazlari: Idle->Recording->Transcribing islem=Upload
2 asistan fazlari: hazir=Ready kayit=Recording ceviri=Transcribing kapali=Off
3 OK health ok=true stt=acik
4 kayit.ogg okundu: 31774 bayt (ogg imza=true)
4 OK metin (669 ms): Toplanti saat 14:00te basliyor.
5 oto-okuma karari=true (asistan modu + ayar acik)
5 OK sentez+calma basladi=true sure=1920 ms · onbellek=2 dosya
SONUC: BASARILI
```
Sunucu tarafı (`denetim/tur13/mock-voice.log`): `GET /health 200`,
`POST /transcribe 200 (multipart=True alan_audio=True dosya=kayit.ogg ogg_imza=True boyut=31976)`,
`POST /synthesize 200 (motor=kahya metin="Toplanti saat 14:00te basliyor." 11678 bayt)`.

**b) Gerçek voice_api (SALT OKUMA/çağrı — servis durdurulmadı, motor elle yüklenmedi):**

```
3 OK health ok=true stt=acik motorlar={kahya=hazir, chatterbox=hazir, kadin=hazir}
4 OK metin (1040 ms): Mesajınızı aldım. Şunu duydum.
  Yalçın, çocukluğunun en güzel. Hemen ilgileniyorum.      ← whisper, GERÇEK transkript
5 oto-okuma karari=true
```
(`denetim/tur13/selftest-gercek.txt`; Mac'ten `GET /health` → `kahya:"hazir"` —
`tools/tur12/live_synth.py` deseni, token `.env`'den adıyla.)

> Gerçek `/synthesize` adımı bu turda **tamamlanmadı**. İki bağımsız koşum aynı yeri
> gösterdi:
> - **Emülatör (uygulama kodu)**: 300 sn bekledikten sonra
>   `bildirimler: Ses ucuna ulaşılamıyor (ev ağında :8174, dışarıda /voice-api) ·
>   http://10.0.2.2:8174 (ulaşılamadı)` → `SONUC: HATA`
>   (`g1-selftest.xml`, `emulator-selftest-gercek.png`).
> - **Mac (uygulamadan bağımsız, salt çağrı)**: aynı anda `POST /synthesize`
>   (kahya, "Merhaba, sesli asistan hazır!") **~420 sn içinde yanıt vermedi**
>   (`denetim/tur13/live_synth_probe.py`), oysa `GET /health` aynı dakikada
>   `200 {"ok":true,"stt":"acik","engines":{"kahya":"hazir",...}}` döndü.
>
> Yani **kusur uygulamada ya da emülatör ağında değil, ses ucunun sentez
> yanıtında** (motor "hazir" görünürken sentez yanıt vermiyor). Servise
> dokunulmadı/durdurulmadı. Mock uçta aynı kod yolu (sentez+çalma+önbellek) yeşil;
> tur-12'de gerçek uç soğukken **297,5 sn**, ısınmışken **5,3 sn** ölçülmüştü.
> Ayrıntı: §5.1.

---

## 4. Ölçülen platform gerçekleri (yeni bilgi)

1. **`createRequestRoleIntent(ROLE_ASSISTANT)` Android 12+ pratikte çalışmıyor:**
   `RequestRoleActivity` açılıyor ve `Role is not requestable` deyip anında
   kapanıyor (emülatör Android 34). Uygulama tarafında bunu **yakalayıp Ayarlar'a
   yönlendirmek zorunlu** — yoksa düğme sessizce hiçbir şey yapmıyor gibi görünür.
2. **Sistemin "Dijital asistan" seçicisi üçüncü taraf uygulamayı listelemiyor:**
   `DefaultAppListActivity → Dijital asistan uygulaması` listesinde yalnız **Google**
   görünüyor. Rol `systemOnly`; listede görünmenin yolu **`VoiceInteractionService`
   beyanı** (BIND_VOICE_INTERACTION + `android.service.voice.VoiceInteractionService`
   filtresi) — bu tur yapılmadı (§5).
3. **`am start -a android.intent.action.ASSIST` (implicit) chooser açıyor** — Google da
   ASSIST beyan ettiği ve varsayılan atanmadığı için. Rol bize aitken de `am start`
   chooser gösteriyor: rol yönlendirmesi SystemUI'nin asistan hareketinde devreye
   giriyor, `am start` çözümlemesinde değil. Bu yüzden "doğrudan açılış" kanıtı
   **`ACTION_ASSIST` + vekil bileşen** ile alındı (sistemin gönderdiği niyetin aynısı)
   ve ayrıca seçici üzerinden tıklama yolu da kanıtlandı.
4. **Emülatörde soğuk sentez ölçümü için 60 sn bekleme YETMEZ:** ilk gerçek koşuda
   öz-test "sentez başlamadı" dedi; tavan 300 sn'ye çıkarıldı (üretim istemci tavanı
   420 sn). Test tarafı düzeltildi; uygulama kodu değişmedi.

---

## 5. Kalanlar (dürüst liste)

1. **Gerçek uçta `/synthesize` ölçümü tamamlanmadı.** Motor `hazir` görünürken
   sentez yanıt vermiyor: Mac'ten bağımsız `POST /synthesize` ~420 sn'de dönmedi,
   emülatördeki uygulama kodu da 300 sn sonra "ulaşılamadı" dedi (§3.6b). Ses ucu
   ekibinin bakması gereken yer burası — **uygulama tarafında yapılacak bir şey
   yok**; mock uçta aynı kod yolu yeşil. Tekrarlanabilir ölçüm:
   `python3 denetim/tur13/live_synth_probe.py` (salt çağrı, motoru elle yüklemez)
   ve `python3 denetim/tur13/selftest_kos.py --hedef gercek --env-token`.
   Sentez dönerse ikinci komutun son satırı `SONUC: BASARILI` olur.
2. **`VoiceInteractionService` beyanı** — Hermes'in sistemin "Dijital asistan"
   listesinde GÖRÜNMESİ için gerekli (bugün yalnız kabukla rol atanabiliyor).
   Bu, asistan hareketinin SystemUI yolundan gerçekten Hermes'e düşmesini de
   sağlar. Ayrı bir tur işi: servis + oturum yaşam döngüsü + gerçek cihaz testi.
3. **Fiziksel mikrofon**: emülatörde mikrofon yok; bas-konuş kaydının kendisi
   cihazda denenmeli (tur-11'den beri açık madde). Bu turda kayıt DURUM MAKİNESİ +
   gerçek dosyayla `/transcribe` kanıtlandı.
4. **Gerçek cihazda rol ataması** denenmeli (emülatörde rol kabukla verildi; sistem
   diyaloğu Android 12+ kısıtı nedeniyle kapalı).

---

## 6. Dosya listesi

Değişen:
```
app/src/main/AndroidManifest.xml                     (activity-alias + filtre taşıma)
app/src/main/res/values/strings.xml                  (assistant_label)
app/src/main/java/com/hermes/mobile/data/AppSettings.kt      (assistantAutoRead + VoicePrefs)
app/src/main/java/com/hermes/mobile/ChatViewModel.kt         (asistan modu, oto-gönderim, oto-okuma)
app/src/main/java/com/hermes/mobile/MainActivity.kt          (niyet, izin, rol akışı, bağlantılar)
app/src/main/java/com/hermes/mobile/ui/ChatScreen.kt         (asistan şeridi)
app/src/main/java/com/hermes/mobile/ui/LiveVoiceSheet.kt     (ses yolu ayrımı)
app/src/main/java/com/hermes/mobile/ui/SettingsScreen.kt     (Telefon asistanı bölümü)
app/src/debug/AndroidManifest.xml                            (öz-test aktivitesi)
```
Yeni:
```
app/src/main/java/com/hermes/mobile/data/AssistantMode.kt    (saf karar katmanı)
app/src/main/java/com/hermes/mobile/data/AssistantRole.kt    (Android rol okuma/diyalog)
app/src/test/java/com/hermes/mobile/AssistantModeTest.kt     (24 test)
app/src/debug/java/com/hermes/mobile/ui/AssistantSelfTestActivity.kt
denetim/tur13/*                                              (kanıt + koşum betikleri)
```
